package com.example.examplemod.client.map;

import com.example.examplemod.policy.AtlasTileAccess;
import java.nio.file.Files;
import java.util.HashSet;

/** Access denial, native silhouettes, corruption recovery and world isolation without a game client. */
public final class AtlasExperienceCheck {
    private static int checks;
    private static void require(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        for (boolean authorized : new boolean[]{false, true}) for (boolean anchor : new boolean[]{false, true}) for (boolean dimension : new boolean[]{false, true})
            require(AtlasTileAccess.allowed(authorized, anchor, dimension) == (authorized && anchor && dimension), "wrong anchor/dimension/nonmember denied");
        var signatures = new HashSet<String>();
        for (var glyph : AtlasGlyph.values()) {
            StringBuilder shape = new StringBuilder(); int count = 0;
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) { boolean pixel = glyph.pixel(x, y); shape.append(pixel ? '#' : '.'); if (pixel) count++; }
            require(count > 10 && count < 200, "legible native silhouette " + glyph);
            require(signatures.add(shape.toString()), "categories have distinct silhouettes " + glyph);
            require(!glyph.pixel(-1, 0) && !glyph.pixel(16, 0) && !glyph.pixel(0, 16), "no overflow pixels " + glyph);
        }
        var directory = Files.createTempDirectory("ccec-atlas-check-");
        var file = directory.resolve("settings.properties");
        var a = new AtlasPreferences(); a.mode = 2; a.positioned = true; a.centerX = -150; a.centerZ = 203.5; a.zoom = 0.5;
        a.grid = true; a.minimap = true; a.overlays[2] = 77; a.pins.add(new AtlasPreferences.Pin(-50, 81));
        a.save(file, "server-a:city-a");
        var b = new AtlasPreferences(); b.centerX = 999; b.save(file, "server-b:city-b");
        var loaded = AtlasPreferences.load(file, "server-a:city-a");
        require(loaded.positioned && loaded.centerX == -150 && loaded.centerZ == 203.5 && loaded.zoom == 0.5, "negative coordinates/view survive reopen");
        require(loaded.grid && loaded.minimap && loaded.mode == 2 && loaded.overlays[2] == 77, "mode and layers survive reopen");
        require(loaded.pins.equals(a.pins), "personal pins survive reopen");
        require(AtlasPreferences.load(file, "server-b:city-b").centerX == 999, "worlds do not share views");
        require(AtlasPreferences.load(file, "another-player").pins.isEmpty(), "players do not share pins");
        String world = "server:player:minecraft:overworld";
        a.save(file, world + ":42");
        var moved = new AtlasPreferences(); moved.pins.add(new AtlasPreferences.Pin(500, -600)); moved.save(file, world + ":99");
        var migrated = AtlasPreferences.loadWorld(file, world);
        require(migrated.pins.containsAll(a.pins) && migrated.pins.containsAll(moved.pins), "pins recover across old anchors");
        migrated.save(file, world);
        require(AtlasPreferences.loadWorld(file, world).pins.equals(migrated.pins), "reopening at a different anchor uses the same pins");
        migrated.pins.clear(); migrated.save(file, world);
        require(AtlasPreferences.loadWorld(file, world).pins.isEmpty(), "deleted pins are not resurrected from legacy anchors");
        require(AtlasPreferences.loadWorld(file, "server:other:minecraft:overworld").pins.isEmpty(), "migration cannot cross players");
        require(AtlasPreferences.loadWorld(file, "server:player:minecraft:the_nether").pins.isEmpty(), "migration cannot cross dimensions");
        for (int i = 0; i < 200; i++) a.pins.add(new AtlasPreferences.Pin(i, -i));
        a.save(file, "server-a:city-a");
        require(AtlasPreferences.load(file, "server-a:city-a").pins.size() == AtlasPreferences.MAX_PINS, "bounded pin storage");
        Files.writeString(file, "bad.positioned=true\nbad.x=NaN\nbad.zoom=Infinity\nbad.mode=900\n");
        require(!AtlasPreferences.load(file, "bad").positioned, "nonfinite local view cannot poison map transform");
        Files.writeString(file, "bad.x=garbage\n");
        require(!AtlasPreferences.load(file, "bad").positioned, "malformed settings fall back safely");
        Files.delete(file); Files.delete(directory);
        var cacheDirectory = Files.createTempDirectory("ccec-terrain-cache-check-");
        var lines = new java.util.BitSet(1024); lines.set(31); lines.set(1023);
        int[] colors = new int[1024]; java.util.Arrays.fill(colors, 0xff123456);
        var tile = new TerrainTileStore.Tile(colors, lines, true, 1000);
        var store = new TerrainTileStore(cacheDirectory, "server:a:overworld");
        TerrainTileStore.awaitIo(); store.put(-100, 205, tile); store.flush().get();
        var reopened = new TerrainTileStore(cacheDirectory, "server:a:overworld"); TerrainTileStore.awaitIo();
        reopened.get(-100,205);TerrainTileStore.awaitIo();
        var restored = reopened.get(-100, 205);
        require(restored != null && java.util.Arrays.equals(restored.colors(), colors) && restored.contours().equals(lines)
                && restored.complete(), "terrain colors, contours, completeness and negative coordinates survive restart");
        var other = new TerrainTileStore(cacheDirectory, "server:b:overworld"); TerrainTileStore.awaitIo();
        require(other.get(-100, 205) == null, "terrain cannot cross player scopes");
        var dimension = new TerrainTileStore(cacheDirectory, "server:a:nether"); TerrainTileStore.awaitIo();
        require(dimension.get(-100, 205) == null, "terrain cannot cross dimensions");
        var server = new TerrainTileStore(cacheDirectory, "other-server:a:overworld"); TerrainTileStore.awaitIo();
        require(server.get(-100, 205) == null, "terrain cannot cross servers");
        int[] changed = colors.clone(); changed[0] = 0xffeeeeee;
        var racing = new TerrainTileStore(cacheDirectory, "server:a:overworld");
        racing.put(-100, 205, new TerrainTileStore.Tile(changed, lines, false, 2000)); TerrainTileStore.awaitIo();
        require(racing.get(-100, 205).colors()[0] == changed[0] && !racing.get(-100, 205).complete(), "network update wins over disk load");
        racing.flush().get();
        for (int i = 0; i < TerrainTileStore.MAX_TILES; i++) other.put(i, 0, tile);
        other.get(0, 0); other.put(TerrainTileStore.MAX_TILES, 0, tile);
        require(other.size() == TerrainTileStore.MAX_TILES && other.get(0, 0) != null && other.get(1, 0) == null, "terrain memory LRU is bounded and retains recently viewed tiles");
        other.flush().get();
        try (var paths = Files.list(cacheDirectory)) {
            for (var path : paths.toList()) Files.writeString(path, "corrupt cache");
        }
        var corrupt = new TerrainTileStore(cacheDirectory, "server:a:overworld"); TerrainTileStore.awaitIo();
        require(corrupt.loaded() && corrupt.size() == 0, "corrupt terrain cache becomes a safe network miss");
        var unrelated = cacheDirectory.resolve("unrelated.txt"); Files.writeString(unrelated, "keep");
        TerrainTileStore.prune(cacheDirectory, 0);
        require(Files.exists(unrelated), "disk eviction touches only owned cache files");
        Files.delete(unrelated); Files.delete(cacheDirectory);
        var random = new java.util.Random(7341);
        int[] relief = new int[1024]; var contour = new java.util.BitSet(1024);
        for (int i = 0; i < relief.length; i++) { relief[i] = random.nextInt(); if (random.nextBoolean()) contour.set(i); }
        int[] filtered = TerrainSurfaceFilter.smooth(relief,32);
        for (int stride : new int[]{1, 2, 3, 4, 8, 16, 32}) {
            int[] raster = TerrainTileTexture.raster(relief, contour, stride);
            for (int z = 0; z < 32; z++) for (int x = 0; x < 32; x++) {
                int baseX = x / stride * stride, baseZ = z / stride * stride;
                long a0 = 0, r0 = 0, g0 = 0, b0 = 0; int count = 0, marked = 0;
                for (int z1 = baseZ; z1 < Math.min(32, baseZ + stride); z1++)
                    for (int x1 = baseX; x1 < Math.min(32, baseX + stride); x1++) {
                        int pixel = filtered[z1 * 32 + x1]; a0 += pixel >>> 24; r0 += pixel >> 16 & 255;
                        g0 += pixel >> 8 & 255; b0 += pixel & 255; count++; if (contour.get(z1 * 32 + x1)) marked++;
                    }
                int expected = MapSurveyTransform.survey((int) ((a0 / count) << 24 | (r0 / count) << 16 | (g0 / count) << 8 | b0 / count));
                if (stride < 8 && marked * 2 >= count) expected = MapSurveyTransform.surveyInk(expected);
                require(raster[z * 32 + x] == expected, "optimized LOD preserves exact terrain and contour pixels at stride " + stride);
            }
        }
        System.out.println("PASS: " + checks + " atlas experience checks");
    }
}
