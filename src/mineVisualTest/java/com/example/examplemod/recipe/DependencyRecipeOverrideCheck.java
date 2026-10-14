package com.example.examplemod.recipe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Checks that the dependency-recipe override pack and the list documenting it agree.
 *
 * <p>{@link DependencyRecipeOverride#OVERRIDDEN_RECIPES} is a hand-written list of the recipes this
 * project intends to replace, and it is also the only description of what the pack does. Nothing
 * verified it against the pack's actual contents, so two silent failures were possible: a recipe
 * overridden on disk but absent from the list, which hides a balance change from review, and a name in
 * the list with no file behind it, which reads as a removal that never happens.
 *
 * <p>The check also reads every override as JSON, because a malformed override does not fail the build
 * otherwise — it fails at datapack load, or worse, silently loses to the original recipe.
 */
public final class DependencyRecipeOverrideCheck {
    private static int checks;

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Path packRoot = locatePackRoot();

        documentedIdsMatchPackContents(packRoot);
        everyOverrideIsValidJson(packRoot);
        catenaryHolderUsesIndustrialInputs(packRoot);
        overriddenRecipesOnlyUseRealItems(packRoot);

        System.out.println("PASS: " + checks + " dependency recipe override checks");
    }

    /** Every documented id has a file, and every file in the pack is documented. */
    private static void documentedIdsMatchPackContents(Path packRoot) {
        Set<String> onDisk = recipeFiles(packRoot);
        Set<String> documented = new LinkedHashSet<>(List.of(DependencyRecipeOverride.OVERRIDDEN_RECIPES));

        require(!documented.isEmpty(), "the override list must not be empty");

        for (String id : documented) {
            require(onDisk.contains(id), id + " is documented as overridden but has no file in the pack");
        }
        for (String id : onDisk) {
            require(documented.contains(id),
                    id + " overrides a dependency recipe but is missing from OVERRIDDEN_RECIPES");
        }

        // The pack must stay limited to intentional replacements; the mod's own recipes live elsewhere.
        require(onDisk.size() == documented.size(),
                "the pack holds " + onDisk.size() + " recipes but " + documented.size() + " are documented");
    }

    private static void everyOverrideIsValidJson(Path packRoot) {
        for (String id : recipeFiles(packRoot)) {
            Path file = resolveRecipe(packRoot, id);
            JsonObject root;
            try {
                root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException ex) {
                throw new AssertionError("cannot read " + file + ": " + ex.getMessage());
            } catch (RuntimeException ex) {
                throw new AssertionError(file.getFileName() + " is not valid JSON: " + ex.getMessage());
            }

            require(root.has("type"), id + " must declare a recipe type");
            require(root.has("result"), id + " must declare a result");

            JsonObject result = root.getAsJsonObject("result");
            require(result.has("id"), id + " must declare a result id");
            require(result.has("count") && result.get("count").getAsInt() > 0,
                    id + " must produce at least one item");
        }
    }

    /**
     * The catenary holder is meant to hang off Create: City Expansion industrial goods rather than raw
     * iron, so its override is pinned ingredient by ingredient. This is the requirement that would be
     * easiest to lose to an accidental revert.
     */
    private static void catenaryHolderUsesIndustrialInputs(Path packRoot) {
        JsonObject recipe = readJson(resolveRecipe(packRoot, "electroenergetics:crafting/catenary_holder"));

        require("minecraft:crafting_shaped".equals(recipe.get("type").getAsString()),
                "catenary_holder must stay a shaped recipe");
        require("electroenergetics:catenary_holder".equals(
                        recipe.getAsJsonObject("result").get("id").getAsString()),
                "catenary_holder must still produce the catenary holder itself");

        JsonObject key = recipe.getAsJsonObject("key");
        Set<String> items = new LinkedHashSet<>();
        key.asMap().forEach((symbol, ingredient) ->
                items.add(ingredient.getAsJsonObject().get("item").getAsString()));

        require(items.contains("createcityexpansioncore:structural_beam"),
                "catenary_holder must use a Create: City Expansion structural beam");
        require(items.contains("createcityexpansioncore:industrial_fastener_pack"),
                "catenary_holder must use a Create: City Expansion industrial fastener pack");
        require(items.size() == 2, "catenary_holder must only use the two industrial inputs, saw " + items);
        require(!items.contains("minecraft:iron_ingot") && !items.contains("minecraft:iron_nugget"),
                "catenary_holder must no longer be raw iron");

        // The pattern has to match the declared key symbols, or the recipe book shows a blank grid.
        List<String> pattern = recipe.getAsJsonArray("pattern").asList().stream()
                .map(element -> element.getAsString()).toList();
        Set<String> used = new LinkedHashSet<>();
        for (String row : pattern) {
            for (char symbol : row.toCharArray()) {
                if (symbol != ' ') {
                    used.add(String.valueOf(symbol));
                }
            }
        }
        require(used.equals(key.asMap().keySet()),
                "catenary_holder pattern uses " + used + " but declares " + key.asMap().keySet());
        require(pattern.size() == 3 && pattern.stream().allMatch(row -> row.length() == 3),
                "catenary_holder must use a 3x3 grid");
    }

    /**
     * Every {@code createcityexpansioncore:} item an override uses must be a real item of this mod, and
     * it must be named in both shipped languages.
     *
     * <p>An override naming an item that does not exist is dropped at datapack load, which would hand the
     * players back the cheap original recipe without any error surfacing in the build. The registry
     * cannot be booted headless here, so this leans on the lang files instead: a lang entry is written
     * for every item the mod registers, so a missing entry is a reliable signal of a wrong item id.
     */
    private static void overriddenRecipesOnlyUseRealItems(Path packRoot) {
        Pattern itemId = Pattern.compile("\"createcityexpansioncore:([a-z0-9_]+)\"");
        Set<String> referenced = new LinkedHashSet<>();

        for (String id : recipeFiles(packRoot)) {
            Matcher matcher = itemId.matcher(readText(resolveRecipe(packRoot, id)));
            while (matcher.find()) {
                referenced.add(matcher.group(1));
            }
        }

        require(!referenced.isEmpty(),
                "the override pack must reference at least one Create: City Expansion item");

        for (String item : referenced) {
            for (String locale : List.of("en_us", "ru_ru")) {
                String key = "item.createcityexpansioncore." + item;
                String lang = readText(langFile(locale));
                require(lang.contains("\"" + key + "\""),
                        locale + ".json is missing " + key
                                + ", which an override in this pack already uses as an ingredient");
            }
        }
    }

    private static Path langFile(String locale) {
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 8 && root != null; depth++) {
            Path candidate = root.resolve("src/main/resources/assets/createcityexpansioncore/lang")
                    .resolve(locale + ".json");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            root = root.getParent();
        }
        throw new AssertionError("cannot locate " + locale + ".json");
    }

    // -------------------------------------------------------------------------- helpers

    private static Set<String> recipeFiles(Path packRoot) {
        Set<String> found = new LinkedHashSet<>();
        for (String namespace : List.of("create", "electroenergetics")) {
            Path recipes = packRoot.resolve("data").resolve(namespace).resolve("recipe");
            if (!Files.isDirectory(recipes)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(recipes)) {
                walk.filter(Files::isRegularFile).forEach(file -> {
                    String relative = recipes.relativize(file).toString().replace('\\', '/');
                    found.add(namespace + ":" + relative.substring(0, relative.length() - ".json".length()));
                });
            } catch (IOException ex) {
                throw new AssertionError("cannot walk " + recipes + ": " + ex.getMessage());
            }
        }
        return found;
    }

    private static Path resolveRecipe(Path packRoot, String id) {
        int colon = id.indexOf(':');
        String namespace = id.substring(0, colon);
        String path = id.substring(colon + 1);
        return packRoot.resolve("data").resolve(namespace).resolve("recipe").resolve(path + ".json");
    }

    private static JsonObject readJson(Path file) {
        try {
            return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException ex) {
            throw new AssertionError("cannot read " + file + ": " + ex.getMessage());
        } catch (RuntimeException ex) {
            throw new AssertionError(file.getFileName() + " is not valid JSON: " + ex.getMessage());
        }
    }

    private static String readText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new AssertionError("cannot read " + file + ": " + ex.getMessage());
        }
    }

    private static Path locatePackRoot() {
        List<String> missing = new ArrayList<>();
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 8 && root != null; depth++) {
            Path candidate = root.resolve(
                    "src/main/resources/datapacks/dependency_recipe_overrides");
            if (Files.isDirectory(candidate.resolve("data"))) {
                return candidate;
            }
            root = root.getParent();
        }
        throw new AssertionError("cannot locate the override pack from "
                + Paths.get("").toAbsolutePath() + ", missing " + missing);
    }
}