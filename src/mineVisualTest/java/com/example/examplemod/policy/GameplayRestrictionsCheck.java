package com.example.examplemod.policy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Contract checks for {@link GameplayRestrictions}.
 *
 * <p>Deliberately registry-free. {@code Bootstrap.bootStrap()} needs {@code LoadingModList}, which only
 * exists inside a real mod loading pass, and in 1.21.1 even a plain
 * {@code BuiltInRegistries.BLOCK.getOptional(...)} throws {@code "Not bootstrapped"} before that. Every
 * check in this project works on plain data for the same reason, and so does this one: it pins the
 * policy's decisions on registry ids, which is the form the policy itself is written in.
 *
 * <p>What that costs is stated plainly rather than hidden. The ids below are vanilla's, written out in
 * full, so a dropped colour is caught here. What is <em>not</em> proven here is that a given id resolves
 * to the expected registry object, nor that the vanilla {@code BlockTags#SHULKER_BOXES} lookup returns
 * true, since neither can run without a bootstrapped registry.
 *
 * <p>The wiring is not provable here either: that cancelling an {@code EntityTickEvent.Pre} really
 * freezes a boat or a carriage, that a cancelled {@code BlockEvent.EntityPlaceEvent} leaves the world
 * untouched, and that an elytra cannot slip one tick of glide between the flag being set and cleared.
 * Those need a running game and are on the manual checklist.
 */
public final class GameplayRestrictionsCheck {
    private static int checks;

    /**
     * Every refusal phrase this policy can send. Listed here so the policy and its translations cannot
     * drift: a key added in Java but missing from a lang file would show the player a raw translation
     * key instead of a reason.
     */
    private static final String[] RESTRICTION_KEYS = {
            "message.createcityexpansioncore.restriction.shulker_open",
            "message.createcityexpansioncore.restriction.shulker_place",
            "message.createcityexpansioncore.restriction.shulker_crafted",
            "message.createcityexpansioncore.restriction.elytra",
            "message.createcityexpansioncore.restriction.ice_boat",
            "message.createcityexpansioncore.restriction.nether_train",
            "message.createcityexpansioncore.restriction.nether_track",
    };

    /**
     * The vanilla recipes this mod removes, by id. They are shipped as {@code neoforge:conditions}
     * overrides in this mod's own resources, which is how a recipe is disabled without touching the
     * immutable {@code RecipeManager}.
     *
     * <p>These two are the whole vanilla shulker surface: {@code shulker_box} makes the box and
     * {@code shulker_box_coloring} dyes it, and all seventeen colours are the same item. Vanilla 1.21.1
     * ships no other shulker recipe — verified against the vanilla resource jar.
     */
    private static final String[] DISABLED_RECIPE_IDS = {
            "minecraft:shulker_box",
            "minecraft:shulker_box_coloring",
    };

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        shulkerRecipesAreDisabledByOverride();
        shulkerBoxesCoverEveryVanillaVariant();
        shulkerRuleStaysInsideShulkerBoxes();
        elytraIsRefusedButOtherChestArmorIsNot();
        iceSurfacesAreRefusedAndOrdinaryGroundIsNot();
        hullLayerSeparatesIceFromWater();
        iceBoatSpeedIsClampedNotFrozen();
        netherRefusesTrainsOnlyInTheNether();
        trainsMidPortalAreNotStopped();
        notificationCooldownSuppressesSpam();
        restrictionPhrasesAreShippedInBothLanguages();

        System.out.println("PASS: " + checks + " gameplay restriction policy checks");
    }

    /**
     * The refusal phrases are parsed as real JSON rather than scanned for keys.
     *
     * <p>The neighbouring lang check extracts keys with a regular expression, which cannot tell a
     * valid file from a broken one. A single missing comma in a lang file does not fail the build
     * there — it fails silently at runtime and the whole locale stops resolving. That happened while
     * this policy was being written, so the syntax is pinned here.
     */
    private static void restrictionPhrasesAreShippedInBothLanguages() {
        Path langDir = locateLangDir();
        for (String locale : new String[]{"en_us", "ru_ru"}) {
            Path file = langDir.resolve(locale + ".json");
            require(Files.isRegularFile(file), locale + ".json must exist");

            JsonObject root;
            try {
                root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException ex) {
                throw new AssertionError(locale + ".json cannot be read: " + ex.getMessage());
            } catch (RuntimeException ex) {
                throw new AssertionError(locale + ".json is not valid JSON: " + ex.getMessage());
            }

            for (String key : RESTRICTION_KEYS) {
                JsonElement value = root.get(key);
                require(value != null && value.isJsonPrimitive(),
                        locale + ".json is missing the translation key " + key);
                require(!value.getAsString().isBlank(),
                        locale + ".json has a blank translation for " + key);
            }
        }
    }

    private static Path locateLangDir() {
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 8 && root != null; depth++) {
            Path candidate = root.resolve("src/main/resources/assets/createcityexpansioncore/lang");
            if (Files.isRegularFile(candidate.resolve("en_us.json"))) {
                return candidate;
            }
            root = root.getParent();
        }
        throw new AssertionError("cannot locate the lang directory from " + Paths.get("").toAbsolutePath());
    }

    /**
     * The recipe overrides that actually remove crafting must exist and must be shaped so NeoForge skips
     * them at datapack load.
     *
     * <p>The shape matters and is easy to get wrong: {@code ConditionalOps} evaluates
     * {@code neoforge:conditions} <em>before</em> it decodes the recipe body, so a failing condition means
     * the recipe never reaches its own codec. That is what lets the override be a condition and nothing
     * else — no {@code type}, no {@code result}, no ingredients — and it is also why a wrong condition type
     * would silently fall through to "decode this as a real recipe" and fail the pack load instead.
     */
    private static void shulkerRecipesAreDisabledByOverride() {
        for (String recipeId : DISABLED_RECIPE_IDS) {
            Path file = locateModData()
                    .resolve("minecraft/recipe/" + recipeId.substring("minecraft:".length()) + ".json");
            require(Files.isRegularFile(file), "override for " + recipeId + " must exist at " + file);

            JsonObject root;
            try {
                root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (IOException ex) {
                throw new AssertionError("cannot read " + file + ": " + ex.getMessage());
            } catch (RuntimeException ex) {
                throw new AssertionError(file.getFileName() + " is not valid JSON: " + ex.getMessage());
            }

            JsonElement conditions = root.get("neoforge:conditions");
            require(conditions != null && conditions.isJsonArray(),
                    file.getFileName() + " must carry a neoforge:conditions array");
            JsonArray list = conditions.getAsJsonArray();
            require(list.size() == 1, file.getFileName() + " needs exactly one condition");
            JsonObject condition = list.get(0).getAsJsonObject();
            require("neoforge:false".equals(condition.get("type").getAsString()),
                    file.getFileName() + " must use neoforge:false, otherwise the recipe still loads");
        }

        // Vanilla 1.21.1 ships no elytra recipe, so there is deliberately no override for it. If a future
        // version adds one, this is where it will be noticed.
        Path elytra = locateModData().resolve("minecraft/recipe/elytra.json");
        require(!Files.isRegularFile(elytra),
                "no elytra recipe override is expected while vanilla 1.21.1 ships no elytra recipe");
    }

    private static Path locateModData() {
        Path root = Paths.get("").toAbsolutePath();
        for (int depth = 0; depth < 8 && root != null; depth++) {
            Path candidate = root.resolve("src/main/resources/data");
            if (Files.isDirectory(candidate.resolve("minecraft/recipe"))) {
                return candidate;
            }
            root = root.getParent();
        }
        throw new AssertionError("cannot locate src/main/resources/data from " + Paths.get("").toAbsolutePath());
    }

    // ------------------------------------------------------------------------ shulker

    private static void shulkerBoxesCoverEveryVanillaVariant() {
        // Every vanilla shulker colour plus the undyed one, seventeen in total. Written out in full so
        // a new vanilla colour, or a dropped one, shows up as a failure rather than as a leak in game.
        String[] coloured = {
                "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
                "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black",
        };
        int seen = 0;
        for (String colour : coloured) {
            String id = "minecraft:" + colour + "_shulker_box";
            require(GameplayRestrictions.shulkerBoxId(id), id + " must be refused");
            seen++;
        }
        require(GameplayRestrictions.shulkerBoxId("minecraft:undyed_shulker_box"),
                "the undyed shulker box must be refused");
        seen++;
        require(seen == 17, "all seventeen vanilla shulker boxes must be covered, saw " + seen);
    }

    private static void shulkerRuleStaysInsideShulkerBoxes() {
        // The failure mode of a policy like this is a ban that quietly spreads, so the whole nearby
        // container family is pinned as allowed. Anything here turning true is a server-wide regression.
        String[] allowed = {
                "minecraft:ender_chest", "minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel",
                "minecraft:hopper", "minecraft:dispenser", "minecraft:dropper", "minecraft:furnace",
                "minecraft:blast_furnace", "minecraft:smoker", "minecraft:chiseled_bookshelf",
                "minecraft:bundle", "minecraft:crafter", "minecraft:decorated_pot",
        };
        for (String id : allowed) {
            require(!GameplayRestrictions.shulkerBoxId(id), id + " must stay allowed");
        }

        // Only vanilla's own namespace may be refused by id, so a modded lookalike is not silently
        // swallowed by a naming accident.
        require(!GameplayRestrictions.shulkerBoxId("someothermod:undyed_shulker_box"),
                "a modded block that reuses a vanilla id shape must not be refused by id");

        // Null and malformed ids must be refused rather than throwing, because a block can be
        // unregistered during a reload.
        require(!GameplayRestrictions.shulkerBoxId((String) null), "a null id is not a shulker box");
        require(!GameplayRestrictions.shulkerBoxId("not a resource location"),
                "a malformed id is not a shulker box");
    }

    // ------------------------------------------------------------------------ elytra

    private static void elytraIsRefusedButOtherChestArmorIsNot() {
        require(GameplayRestrictions.elytraId("minecraft:elytra"), "the elytra must be refused");

        // The elytra is refused because it flies, not because it sits in the chest slot. Every other
        // chest-slot item has to keep working or the rule became "no armour in the chest slot".
        String[] allowed = {
                "minecraft:diamond_chestplate", "minecraft:netherite_chestplate", "minecraft:iron_chestplate",
                "minecraft:leather_chestplate", "minecraft:golden_chestplate", "minecraft:carved_pumpkin",
                "minecraft:elytra_firework_rocket", "minecraft:shield", "minecraft:turtle_helmet",
        };
        for (String id : allowed) {
            require(!GameplayRestrictions.elytraId(id), id + " must stay allowed");
        }
    }

    // ------------------------------------------------------------------- boats on ice

    private static void iceSurfacesAreRefusedAndOrdinaryGroundIsNot() {
        // The bypass is friction-driven, so the three vanilla ice blocks are pinned by id and by the
        // friction they actually carry.
        require(GameplayRestrictions.isIceBoatSurface("minecraft:ice", 0.98F),
                "ice must be a refused surface");
        require(GameplayRestrictions.isIceBoatSurface("minecraft:packed_ice", 0.98F),
                "packed ice must be a refused surface");
        require(GameplayRestrictions.isIceBoatSurface("minecraft:blue_ice", 0.98F),
                "blue ice must be a refused surface");

        String[] allowed = {
                "minecraft:water", "minecraft:stone", "minecraft:grass_block", "minecraft:sand",
                "minecraft:oak_planks", "minecraft:cobblestone", "minecraft:gravel", "minecraft:clay",
        };
        for (String id : allowed) {
            require(!GameplayRestrictions.isIceBoatSurface(id, 0.6F), id + " must stay allowed");
        }

        // A modded ice that copies vanilla's friction carries the same bypass, and the threshold is
        // what catches it without the policy needing to know the mod exists.
        require(GameplayRestrictions.isIceBoatSurface("someothermod:cold_ice", 0.98F),
                "a high-friction modded surface must be refused like ice");

        // A modded ice that keeps ordinary friction is harmless, so the threshold must not refuse it.
        require(!GameplayRestrictions.isIceBoatSurface("someothermod:soft_snow", 0.6F),
                "a low-friction modded surface must stay allowed");

        // The threshold has to sit between ordinary ground and vanilla ice, or one of the two breaks.
        require(GameplayRestrictions.MIN_ICE_BOAT_FRICTION > 0.6F,
                "the threshold must stay above ordinary block friction, or ordinary ground gets refused");
        require(GameplayRestrictions.MIN_ICE_BOAT_FRICTION <= 0.98F,
                "the threshold must stay at or below vanilla ice friction, or ice is missed");

        require(!GameplayRestrictions.isIceBoatSurface((String) null, 0.6F),
                "an unknown block id must not be a refused surface");
    }

    /**
     * The hull scan reads one layer under the boat, which is the whole reason a boat on water is left
     * alone while a boat on ice is not. Getting this layer wrong is exactly how a rule like this either
     * does nothing or freezes every boat, so the arithmetic is pinned on real box shapes.
     */
    private static void hullLayerSeparatesIceFromWater() {
        // A hull resting on an ice layer at y=63 has its bottom at 64, so the surface under it is 63.
        AABB onIce = new AABB(10.0D, 64.0D, 10.0D, 11.2D, 64.625D, 11.2D);
        require(GameplayRestrictions.iceSurfaceLayer(onIce) == 63,
                "a hull sitting at y=64 must read the layer below it, y=63");

        // A hull floating on a water surface at y=63 has its bottom at 63, so the layer under it is
        // 62 — the water block, never the surface itself and never the lake floor.
        AABB onWater = new AABB(10.0D, 63.0D, 10.0D, 11.2D, 63.625D, 11.2D);
        require(GameplayRestrictions.iceSurfaceLayer(onWater) == 62,
                "a hull floating at y=63 must read y=62, which is water rather than ice");

        // Negative coordinates must floor downwards, not towards zero, or a boat in the far corner of
        // the world reads the wrong layer entirely.
        AABB negative = new AABB(-11.2D, 64.0D, -11.2D, -10.0D, 64.625D, -10.0D);
        require(GameplayRestrictions.iceSurfaceLayer(negative) == 63,
                "the surface layer must floor downwards for negative coordinates");

        // A boat that rowed out of the water onto an ice track arrives with its hull at the higher
        // layer, which is the same expression reading a different block. This is why the rule is
        // position-based rather than placement-based.
        AABB arrivedOnIce = new AABB(10.0D, 65.0D, 120.0D, 11.2D, 65.625D, 121.2D);
        require(GameplayRestrictions.iceSurfaceLayer(arrivedOnIce) == 64,
                "a hull arriving on an ice track at y=65 must read the ice layer below it");
    }

    /**
     * The ice rule is a speed cap, not a stopped boat, so the clamp itself is pinned here. A cap that
     * accidentally left a component unclamped, or that touched the vertical axis, would either not stop
     * the bypass at all or would make boats on ice behave unlike boats on water.
     */
    private static void iceBoatSpeedIsClampedNotFrozen() {
        double max = GameplayRestrictions.MAX_ICE_BOAT_SPEED;
        require(max > 0.0D, "the ice boat speed cap must be positive");

        // A fast diagonal run is pulled down to exactly the cap, keeping its 3:4 direction.
        Vec3 fast = GameplayRestrictions.clampIceBoatSpeed(new Vec3(3.0D, 0.0D, 4.0D), max);
        double horizontal = Math.sqrt(fast.x * fast.x + fast.z * fast.z);
        require(Math.abs(horizontal - max) < 1.0E-9D,
                "a fast boat must be clamped to the cap, was " + horizontal);

        // Direction must survive the clamp, otherwise steering on ice would break. Both components stay
        // positive and the 3:4 ratio is preserved, so a clamped boat still points and slides where it did.
        require(fast.x > 0.0D && fast.z > 0.0D, "clamping must not flip the boat's direction");
        require(Math.abs(fast.x / fast.z - 0.75D) < 1.0E-9D,
                "clamping must preserve the direction the boat was already travelling in");

        // The vertical axis is never clamped, so landing and falling still work.
        Vec3 falling = GameplayRestrictions.clampIceBoatSpeed(new Vec3(5.0D, -0.8D, 0.0D), max);
        require(Math.abs(falling.y + 0.8D) < 1.0E-9D, "the vertical component must be left alone");
        require(Math.abs(falling.x - max) < 1.0E-9D, "the horizontal component must still be capped");

        // Motion already under the cap is returned untouched, which is what keeps water behaviour and
        // slow paddling unchanged.
        Vec3 slow = new Vec3(0.1D, 0.2D, -0.15D);
        require(GameplayRestrictions.clampIceBoatSpeed(slow, max).equals(slow),
                "movement below the cap must not be modified at all");

        Vec3 still = Vec3.ZERO;
        require(GameplayRestrictions.clampIceBoatSpeed(still, max).equals(still),
                "a boat at rest must not be divided by zero or nudged");

        // A purely vertical drop has no horizontal component and must pass through unchanged.
        Vec3 dropping = new Vec3(0.0D, -0.5D, 0.0D);
        require(GameplayRestrictions.clampIceBoatSpeed(dropping, max).equals(dropping),
                "a boat falling straight down must be untouched");
    }

    // ------------------------------------------------------------------ nether trains

    private static void netherRefusesTrainsOnlyInTheNether() {
        require(!GameplayRestrictions.trainsAllowedIn(Level.NETHER),
                "Create trains must be refused in the nether");
        require(GameplayRestrictions.trainsAllowedIn(Level.OVERWORLD),
                "Create trains must be allowed in the overworld");
        require(GameplayRestrictions.trainsAllowedIn(Level.END),
                "the rule must not touch any dimension other than the nether");

        // The rule is equality against the nether's dimension key, not a name match, so a custom
        // dimension that merely reads like the nether keeps working.
        var lookalike = ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.parse("someothermod:the_nether"));
        require(GameplayRestrictions.trainsAllowedIn(lookalike),
                "a custom dimension that merely looks like the nether must stay allowed");

        // The real nether key is "minecraft:the_nether", not "minecraft:nether". Pinning it means a
        // future edit that switches to a substring or name check is caught here.
        require(!GameplayRestrictions.trainsAllowedIn(ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse("minecraft:the_nether"))),
                "the real nether dimension key must be refused");

        // Neighbouring vanilla dimensions must stay allowed.
        require(GameplayRestrictions.trainsAllowedIn(ResourceKey.create(Registries.DIMENSION,
                        ResourceLocation.parse("minecraft:the_end"))),
                "the end must stay allowed");
    }

    /**
     * The "fully inside" gate is what keeps a train that straddles a portal moving, so it is pinned
     * directly. Create reports such a train as present in more than one dimension at once, and a rule
     * that stopped it there would strand it across a portal boundary with its chunk loading split.
     */
    private static void trainsMidPortalAreNotStopped() {
        var nether = Level.NETHER;
        var overworld = Level.OVERWORLD;

        require(GameplayRestrictions.trainFullyInside(List.of(nether), nether),
                "a train with every carriage in the nether is fully inside");

        require(!GameplayRestrictions.trainFullyInside(List.of(nether, overworld), nether),
                "a train straddling a portal must not count as fully inside");
        require(!GameplayRestrictions.trainFullyInside(List.of(overworld, nether), nether),
                "the order of dimensions must not change the decision");
        require(!GameplayRestrictions.trainFullyInside(List.of(overworld), nether),
                "a train entirely in the overworld is not fully inside the nether");
        require(!GameplayRestrictions.trainFullyInside(List.of(overworld, Level.END), Level.END),
                "the same rule must hold for any dimension, not just the nether");
        require(GameplayRestrictions.trainFullyInside(List.of(overworld), overworld),
                "a train entirely in one non-nether dimension is fully inside it");

        require(!GameplayRestrictions.trainFullyInside(List.of(), nether),
                "a train with no carriage present cannot be reasoned about and must be left alone");
    }

    // --------------------------------------------------------------------- messaging

    private static void notificationCooldownSuppressesSpam() {
        // The boat and elytra rules run every tick, so an unthrottled refusal is a chat flood.
        GameplayRestrictions.resetNotifications();
        UUID player = UUID.randomUUID();

        require(GameplayRestrictions.shouldNotify(player, "a"), "the first refusal must speak");
        require(!GameplayRestrictions.shouldNotify(player, "a"),
                "an immediate repeat of the same refusal must be suppressed");

        // A different reason is a different message and must not be swallowed by the first cooldown.
        require(GameplayRestrictions.shouldNotify(player, "b"),
                "a different refusal must speak even inside another one's cooldown");

        // Players must not share one cooldown, or one player can silence another.
        require(GameplayRestrictions.shouldNotify(UUID.randomUUID(), "a"),
                "a different player must not inherit somebody else's cooldown");

        require(!GameplayRestrictions.shouldNotify(null, "a"), "there is nobody to notify");
        GameplayRestrictions.resetNotifications();
    }
}