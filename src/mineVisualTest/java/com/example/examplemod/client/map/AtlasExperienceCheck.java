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
        for (int i = 0; i < 200; i++) a.pins.add(new AtlasPreferences.Pin(i, -i));
        a.save(file, "server-a:city-a");
        require(AtlasPreferences.load(file, "server-a:city-a").pins.size() == AtlasPreferences.MAX_PINS, "bounded pin storage");
        Files.writeString(file, "bad.positioned=true\nbad.x=NaN\nbad.zoom=Infinity\nbad.mode=900\n");
        require(!AtlasPreferences.load(file, "bad").positioned, "nonfinite local view cannot poison map transform");
        Files.writeString(file, "bad.x=garbage\n");
        require(!AtlasPreferences.load(file, "bad").positioned, "malformed settings fall back safely");
        Files.delete(file); Files.delete(directory);
        System.out.println("PASS: " + checks + " atlas experience checks");
    }
}
