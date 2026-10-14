package com.example.examplemod.client.map;

import net.minecraft.util.FastColor;

/**
 * Headless regression for the terrain texture pixel packing.
 *
 * <p>This is the client half of the chain that produced an orange and magenta map while the
 * server-side colours were provably correct:
 * {@code CityMapSavedData} RGB → payload → tile cache → {@link TerrainTileTexture} →
 * {@code NativeImage.setPixelRGBA} → {@code DynamicTexture} → blit. The colours themselves never
 * changed; the channels were rotated on the way into the texture.
 *
 * <p>The contract is not guessable from the method name, so it is asserted from the byte layout
 * instead. {@code setPixelRGBA} is a raw little-endian {@code memPutInt}, and OpenGL reads those four
 * bytes as R,G,B,A — so the packed int must carry red in its <em>low</em> byte
 * ({@code 0xAABBGGRR}), which is {@link FastColor#ABGR32}'s layout. Every assertion below reads the
 * bytes exactly as the GPU would.
 */
public final class TerrainPixelPackingCheck {
    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    /** Byte {@code index} of the value as little-endian memory holds it — what the GPU reads. */
    private static int byteAt(int packed, int index) {
        return (packed >>> (index * 8)) & 0xFF;
    }

    /** Asserts that a semantic colour survives the trip into the native image unchanged. */
    private static void assertRgbSurvives(int argb, String name) {
        int packed = TerrainTileTexture.toNativeImageColor(argb);
        int red = argb >> 16 & 0xFF;
        int green = argb >> 8 & 0xFF;
        int blue = argb & 0xFF;
        int alpha = argb >>> 24;
        require(byteAt(packed, 0) == red,
                name + ": byte 0 must be R=" + red + " but is " + byteAt(packed, 0));
        require(byteAt(packed, 1) == green,
                name + ": byte 1 must be G=" + green + " but is " + byteAt(packed, 1));
        require(byteAt(packed, 2) == blue,
                name + ": byte 2 must be B=" + blue + " but is " + byteAt(packed, 2));
        require(byteAt(packed, 3) == alpha,
                name + ": byte 3 must be A=" + alpha + " but is " + byteAt(packed, 3));
    }

    public static void main(String[] args) {
        // ---------------------------------------- the four semantic primaries, straight through
        System.out.println("semantic        native     R    G    B    A   (bytes as OpenGL reads them)");
        System.out.println("---------------------------------------------------------------------");
        int[][] samples = {
                { 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFFFF },
        };
        String[] names = { "RED #FF0000", "GREEN #00FF00", "BLUE #0000FF", "WHITE #FFFFFF" };
        for (int[] group : samples) {
            for (int index = 0; index < group.length; index++) {
                int argb = group[index];
                int packed = TerrainTileTexture.toNativeImageColor(argb);
                System.out.printf("%-14s  #%08X  %3d  %3d  %3d  %3d%n", names[index], packed,
                        byteAt(packed, 0), byteAt(packed, 1), byteAt(packed, 2), byteAt(packed, 3));
                assertRgbSurvives(argb, names[index]);
            }
        }

        // ----------------------------------------------- the colours the server actually produced
        assertRgbSurvives(0xFF689739, "grass #689739");
        assertRgbSurvives(0xFF373A97, "water #373A97");
        assertRgbSurvives(0xFFDAD09F, "sand #DAD09F");
        assertRgbSurvives(0xFF707070, "stone #707070");

        int grass = TerrainTileTexture.toNativeImageColor(0xFF689739);
        int water = TerrainTileTexture.toNativeImageColor(0xFF373A97);
        int sand = TerrainTileTexture.toNativeImageColor(0xFFDAD09F);
        System.out.printf("%-14s  #%08X  %3d  %3d  %3d  %3d%n", "grass #689739", grass,
                byteAt(grass, 0), byteAt(grass, 1), byteAt(grass, 2), byteAt(grass, 3));
        System.out.printf("%-14s  #%08X  %3d  %3d  %3d  %3d%n", "water #373A97", water,
                byteAt(water, 0), byteAt(water, 1), byteAt(water, 2), byteAt(water, 3));
        System.out.printf("%-14s  #%08X  %3d  %3d  %3d  %3d%n", "sand #DAD09F", sand,
                byteAt(sand, 0), byteAt(sand, 1), byteAt(sand, 2), byteAt(sand, 3));

        // ---------------------------------------- the exact regressions that were on screen
        // Grass must keep green in the middle byte, not the 0xFF that the rotated packing produced.
        require(byteAt(grass, 1) == 0x97,
                "grass keeps G=0x97 in the green byte instead of showing #FF3997 magenta");
        require(byteAt(grass, 0) != 0xFF,
                "grass does not leak full alpha into the red byte");
        require(byteAt(grass, 2) == 0x39, "grass keeps B=0x39 in the blue byte");
        // Water must stay blue-dominant, not become #FF973A orange.
        require(byteAt(water, 2) == 0x97 && byteAt(water, 2) > byteAt(water, 0),
                "water stays blue-dominant instead of turning #FF973A orange");
        require(byteAt(water, 0) == 0x37, "water keeps R=0x37 in the red byte");
        require(byteAt(sand, 0) == 0xDA && byteAt(sand, 1) == 0xD0,
                "sand keeps its warm red and green bytes");
        require(FastColor.ABGR32.red(grass) == 0x68 && FastColor.ABGR32.green(grass) == 0x97
                        && FastColor.ABGR32.blue(grass) == 0x39,
                "the packed value satisfies vanilla's own ABGR32 accessors");

        // ------------------------------------------------- agreement with the vanilla helper
        for (int argb : new int[]{ 0xFF689739, 0xFF373A97, 0xFFDAD09F, 0xFFFFFFFF, 0xFF000000 }) {
            require(TerrainTileTexture.toNativeImageColor(argb) == FastColor.ABGR32.fromArgb32(argb),
                    "our helper matches FastColor.ABGR32.fromArgb32 for #"
                            + Integer.toHexString(argb));
        }

        // ------------------------------------------------------------- the packing is not a no-op
        require(TerrainTileTexture.toNativeImageColor(0xFF689739) != 0xFF689739,
                "an ARGB colour is genuinely repacked, so this is not accidentally passing through");
        require((TerrainTileTexture.toNativeImageColor(0xFF689739) & 0xFF) == 0x68,
                "red ends up in the low byte, which is the first byte OpenGL reads");

        // ------------------------------------------------ the faulty packing would fail these
        int wrong = (0x68 << 24) | (0x97 << 16) | (0x39 << 8) | 0xFF;   // 0xRRGGBBAA
        require(byteAt(wrong, 0) == 0xFF && byteAt(wrong, 1) == 0x39,
                "the old 0xRRGGBBAA packing really would put alpha in the red byte — documented here so "
                        + "the regression cannot come back unnoticed");
        require(wrong != TerrainTileTexture.toNativeImageColor(0xFF689739),
                "the helper no longer produces the rotated packing");

        System.out.println();
        System.out.println("PASS: " + checks + " terrain texture pixel packing checks");
    }
}