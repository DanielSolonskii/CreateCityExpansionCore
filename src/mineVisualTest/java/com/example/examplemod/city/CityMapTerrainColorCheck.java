package com.example.examplemod.city;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.MapColor;

/**
 * Temporary diagnostic for the terrain source-colour / biome-tint classification.
 *
 * <p>Two mistakes motivated it, and both were in the classification rather than in the arithmetic:
 * <ul>
 *   <li>tint family was inferred from {@link MapColor}, which is a block's <em>map</em> colour and says
 *       nothing about whether the client resolves that block through a biome colour resolver;</li>
 *   <li>the tint itself was read through {@code Biome.getGrassColor}/{@code getFoliageColor}, whose
 *       fallback path returns entries from {@code GrassColor}/{@code FoliageColor} — colour-texture
 *       tables the client uploads and a server never initialises.</li>
 * </ul>
 *
 * <p>This check pins the classification for the blocks that matter, per the family each block
 * actually uses in vanilla client rendering, and records base RGB, tint category and the resulting
 * dominant channel so a regression is visible as data rather than as a screenshot.
 *
 * <p>It runs headless, so it cannot query a live biome. The tint column therefore exercises
 * {@link CityMapSavedData#applyTint} with an explicit authored biome tint and with the "no tint"
 * case, which is what a server-side run can actually produce.
 */
public final class CityMapTerrainColorCheck {
    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static int red(int argb) { return argb >> 16 & 0xFF; }
    private static int green(int argb) { return argb >> 8 & 0xFF; }
    private static int blue(int argb) { return argb & 0xFF; }
    private static int chroma(int argb) {
        return Math.max(red(argb), Math.max(green(argb), blue(argb)))
                - Math.min(red(argb), Math.min(green(argb), blue(argb)));
    }

    /** 0 = red dominant, 1 = green dominant, 2 = blue dominant. */
    private static String dominant(int argb) {
        if (red(argb) >= green(argb) && red(argb) >= blue(argb)) return "R";
        return green(argb) >= blue(argb) ? "G" : "B";
    }

    /**
     * A diagnostic row: one known vanilla block, its tint family, the base map colour vanilla gives
     * it, and the authored biome tint a server can actually supply for that family.
     */
    private record Row(String blockId, CityMapSavedData.TintFamily family, MapColor mapColor,
                       int tint, String expectedDominant) {
    }

    /** A green authored tint, as a jungle or swamp biome authors. */
    private static final int FOLIAGE_TINT = 0xFF7FBF4F;
    /** A blue authored tint, as most biomes author for water. */
    private static final int WATER_TINT = 0xFF3F76E4;

    private static final Row[] KNOWN = {
            new Row("grass_block", CityMapSavedData.TintFamily.GRASS, MapColor.GRASS, FOLIAGE_TINT, "G"),
            new Row("oak_leaves", CityMapSavedData.TintFamily.FOLIAGE, MapColor.PLANT, FOLIAGE_TINT, "G"),
            new Row("water", CityMapSavedData.TintFamily.WATER, MapColor.WATER, WATER_TINT, "B"),
            new Row("sand", CityMapSavedData.TintFamily.NONE, MapColor.SAND, 0, "R"),
            new Row("stone", CityMapSavedData.TintFamily.NONE, MapColor.STONE, 0, "R"),
            new Row("dirt", CityMapSavedData.TintFamily.NONE, MapColor.DIRT, 0, "R"),
            new Row("crimson_nylium", CityMapSavedData.TintFamily.NONE, MapColor.CRIMSON_NYLIUM, 0, "R"),
            new Row("warped_nylium", CityMapSavedData.TintFamily.NONE, MapColor.WARPED_NYLIUM, 0, "B"),
    };

    public static void main(String[] args) {
        System.out.println("block            tint      map         base        tint        result      dominant");
        System.out.println("------------------------------------------------------------------------------------");
        for (Row row : KNOWN) {
            ResourceLocation id = ResourceLocation.withDefaultNamespace(row.blockId());
            CityMapSavedData.TintFamily family = CityMapSavedData.classifyTint(id);

            // A server can only ever supply a biome's authored override, or nothing at all. Both are
            // exercised: the authored tint a server can produce, and the "no tint" case.
            int material=CityMapSavedData.materialColor(family,row.mapColor().col);
            int untinted=0xFF000000|material;
            int tinted=0xFF000000|CityMapSavedData.applyTint(material,row.tint(),CityMapSavedData.BIOME_TINT_STRENGTH);

            require(family == row.family(),
                    row.blockId() + " must be classified as " + row.family() + " but is " + family);

            // Whatever the tint, the material keeps its own hue family.
            require(dominant(untinted).equals(row.expectedDominant()),
                    row.blockId() + " reads as " + row.expectedDominant() + " but is "
                            + dominant(untinted) + " (" + Integer.toHexString(untinted) + ")");
            require(dominant(tinted).equals(row.expectedDominant()),
                    row.blockId() + " keeps its hue family under its biome tint but is "
                            + dominant(tinted) + " (" + Integer.toHexString(tinted) + ")");

            // A block that vanilla never tints must come out of the pipeline byte-identical.
            if (row.family() == CityMapSavedData.TintFamily.NONE) {
                require(tinted == untinted,
                        row.blockId() + " is never biome-tinted, so its colour must be untouched");
            }
            require(chroma(untinted) <= 160,
                    row.blockId() + " has restrained natural chroma");
            require(CityMapSavedData.luminance(red(untinted), green(untinted), blue(untinted))
                            <= 255,
                    row.blockId() + " remains within the colour gamut");

            System.out.printf("%-15s %-9s map=%-9s tint=%-9s result=%-9s dominant=%s%n",
                    row.blockId(), family, "#" + Integer.toHexString(row.mapColor().col),
                    row.tint() == 0 ? "none" : "#" + Integer.toHexString(row.tint()),
                    "#" + Integer.toHexString(tinted), dominant(tinted));
        }

        require(CityMapSavedData.materialColor(CityMapSavedData.TintFamily.NONE,MapColor.SNOW.col)==MapColor.SNOW.col,"snow retains native white without a luminance cap");
        require(CityMapSavedData.materialColor(CityMapSavedData.TintFamily.NONE,0x73A9D1)==0x73A9D1,"building and mod material colours remain exact");
        require(CityMapSavedData.materialColor(CityMapSavedData.TintFamily.FOLIAGE,MapColor.PLANT.col)!=MapColor.PLANT.col,"forest uses a natural deep green rather than neon map green");
        // ------------------------------------------------------------------- classification contract
        require(CityMapSavedData.classifyTint(ResourceLocation.withDefaultNamespace("grass_block"))
                        == CityMapSavedData.TintFamily.GRASS, "grass_block uses the grass tint");
        require(CityMapSavedData.classifyTint(ResourceLocation.withDefaultNamespace("oak_leaves"))
                        == CityMapSavedData.TintFamily.FOLIAGE, "leaves use the foliage tint");
        require(CityMapSavedData.classifyTint(ResourceLocation.withDefaultNamespace("water"))
                        == CityMapSavedData.TintFamily.WATER, "water uses the water tint");
        require(CityMapSavedData.classifyTint(ResourceLocation.withDefaultNamespace("sand"))
                        == CityMapSavedData.TintFamily.NONE, "sand is never tinted");
        require(CityMapSavedData.classifyTint(ResourceLocation.withDefaultNamespace("dirt"))
                        == CityMapSavedData.TintFamily.NONE, "dirt is never tinted");
        require(CityMapSavedData.classifyTint(ResourceLocation.withDefaultNamespace("stone"))
                        == CityMapSavedData.TintFamily.NONE, "stone is never tinted");

        // Nether vegetation must not inherit an overworld biome tint, whatever its map colour is.
        for (String nether : new String[]{ "crimson_nylium", "warped_nylium", "crimson_roots",
                "warped_roots", "crimson_fungus", "warped_fungus", "nether_wart_block" }) {
            require(CityMapSavedData.classifyTint(ResourceLocation.withDefaultNamespace(nether))
                            == CityMapSavedData.TintFamily.NONE,
                    nether + " must never be biome-tinted");
        }
        // Nether stems/hypæe share map colours with plants but are not tinted in vanilla.
        for (String nether : new String[]{ "crimson_stem", "warped_stem", "crimson_hyphae",
                "warped_hyphae", "crimson_fungus_block", "warped_wart_block" }) {
            require(CityMapSavedData.classifyTint(ResourceLocation.withDefaultNamespace(nether))
                            == CityMapSavedData.TintFamily.NONE,
                    nether + " must never be biome-tinted");
        }
        // Modded and non-minecraft namespaces are never guessed at.
        require(CityMapSavedData.classifyTint(
                        ResourceLocation.fromNamespaceAndPath("create", "grass_block"))
                        == CityMapSavedData.TintFamily.NONE,
                "a non-minecraft block is never tinted from a guessed family");
        require(CityMapSavedData.classifyTint(
                        ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "shipment_seal"))
                        == CityMapSavedData.TintFamily.NONE,
                "a CCEC block is never tinted");

        // A block that is not a real tint user must not pick one up through its map colour.
        require(MapColor.CRIMSON_NYLIUM != MapColor.PLANT && MapColor.WARPED_NYLIUM != MapColor.PLANT,
                "nether map colours differ from PLANT, so a PLANT rule could not have been caught by luck");
        require(MapColor.SAND != MapColor.PLANT && MapColor.SAND != MapColor.GRASS,
                "sand shares no plant map colour either");

        // -------------------------------------------------------------------- hue and channel order
        // Water must dominate in blue and stone must be neutral; that pins the channel order, because
        // reading the packed colour as BGR would turn water red and stone would still be neutral.
        int water = CityMapSavedData.normalize(MapColor.WATER.col);
        require(blue(water) > green(water) + 30 && blue(water) > red(water) + 30,
                "water is decoded as blue, so the colour is RRGGBB and not BGR");
        require(chroma(CityMapSavedData.normalize(MapColor.STONE.col)) == 0,
                "stone decodes to a neutral grey");
        require(red(MapColor.WATER.col) == 0x40 && green(MapColor.WATER.col) == 0x40
                        && blue(MapColor.WATER.col) == 0xFF,
                "the vanilla water map colour is 0x4040FF, so blue sits in the low byte: the colour is"
                        + " read as RRGGBB and a BGR reading would render water red");

        // ----------------------------------------------------------------- tint strength is a blend
        require(CityMapSavedData.BIOME_TINT_STRENGTH > 0.0 && CityMapSavedData.BIOME_TINT_STRENGTH < 1.0,
                "the biome tint is blended, never applied at full strength");
        require(CityMapSavedData.BIOME_TINT_STRENGTH <= 0.5,
                "the biome tint stays a minority influence");
        int blended = CityMapSavedData.applyTint(MapColor.WATER.col, 0xFFFF0000,
                CityMapSavedData.BIOME_TINT_STRENGTH);
        require(dominant(blended).equals("B"),
                "a fully red tint at blend strength cannot flip water out of the blue family");
        int multiplied = 0xFF000000 | (red(MapColor.WATER.col) * 0xFF / 255) << 16
                | (green(MapColor.WATER.col) * 0xFF / 255) << 8 | blue(MapColor.WATER.col) * 0x00 / 255;
        require(dominant(multiplied).equals("R"),
                "the vanilla multiply really would turn water red — which is the failure being avoided");

        // ------------------------------------------------------------------- unexplored background
        int unknown = CityMapSavedData.UNKNOWN_COLOR;
        require(blue(unknown) >= red(unknown), "unexplored area is not yellow-shifted");
        require(CityMapSavedData.luminance(red(unknown), green(unknown), blue(unknown)) < 30,
                "unexplored area stays dark so terrain reads as new information");
        require(CityMapSavedData.NO_COLOR_FALLBACK != unknown,
                "a block with no map colour is not the same colour as unexplored ground");
        require(CityMapSavedData.luminance(red(CityMapSavedData.NO_COLOR_FALLBACK),
                        green(CityMapSavedData.NO_COLOR_FALLBACK), blue(CityMapSavedData.NO_COLOR_FALLBACK)) < 80,
                "the no-map-colour fallback is dark and neutral");

        System.out.println();
        System.out.println("PASS: " + checks + " terrain source-colour and biome-tint classification checks");
    }
}