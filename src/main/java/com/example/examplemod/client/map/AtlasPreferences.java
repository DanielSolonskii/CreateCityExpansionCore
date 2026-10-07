package com.example.examplemod.client.map;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** Local, world-scoped presentation settings. Pins never send gameplay commands. */
public final class AtlasPreferences {
    public record Pin(int x, int z) {}
    public double centerX, centerZ, zoom = 1.25;
    public int mode;
    public boolean positioned, grid, minimap;
    public final int[] overlays = {-1, -1, -1, -1};
    public final List<Pin> pins = new ArrayList<>();
    public static final int MAX_PINS = 64;

    public static AtlasPreferences load(Path path, String world) {
        AtlasPreferences result = new AtlasPreferences();
        Properties values = read(path);
        String prefix = world + ".";
        try {
            result.centerX = Double.parseDouble(values.getProperty(prefix + "x", "0"));
            result.centerZ = Double.parseDouble(values.getProperty(prefix + "z", "0"));
            result.zoom = Double.parseDouble(values.getProperty(prefix + "zoom", "1.25"));
            result.positioned = Boolean.parseBoolean(values.getProperty(prefix + "positioned", "false"));
            result.mode = Math.max(0, Math.min(2, Integer.parseInt(values.getProperty(prefix + "mode", "0"))));
            result.grid = Boolean.parseBoolean(values.getProperty(prefix + "grid", "false"));
            result.minimap = Boolean.parseBoolean(values.getProperty(prefix + "minimap", "false"));
            for (int i = 0; i < result.overlays.length; i++) result.overlays[i] = Integer.parseInt(values.getProperty(prefix + "layers." + i, "-1"));
            if (!Double.isFinite(result.centerX) || !Double.isFinite(result.centerZ)
                    || !Double.isFinite(result.zoom)) result.positioned = false;
            for (String pin : values.getProperty(prefix + "pins", "").split(";")) {
                if (pin.isBlank() || result.pins.size() >= MAX_PINS) continue;
                String[] coords = pin.split(",");
                if (coords.length == 2) result.pins.add(new Pin(Integer.parseInt(coords[0]), Integer.parseInt(coords[1])));
            }
        } catch (IllegalArgumentException ignored) {
            // Corrupt local settings must not prevent opening the map.
            result.positioned = false;
        }
        return result;
    }

    public void save(Path path, String world) throws IOException {
        Properties values = read(path);
        String prefix = world + ".";
        values.setProperty(prefix + "x", Double.toString(centerX));
        values.setProperty(prefix + "z", Double.toString(centerZ));
        values.setProperty(prefix + "zoom", Double.toString(zoom));
        values.setProperty(prefix + "positioned", Boolean.toString(positioned));
        values.setProperty(prefix + "mode", Integer.toString(mode));
        values.setProperty(prefix + "grid", Boolean.toString(grid));
        values.setProperty(prefix + "minimap", Boolean.toString(minimap));
        for (int i = 0; i < overlays.length; i++) values.setProperty(prefix + "layers." + i, Integer.toString(overlays[i]));
        StringBuilder coordinates = new StringBuilder();
        for (Pin pin : pins.stream().limit(MAX_PINS).toList()) coordinates.append(pin.x()).append(',').append(pin.z()).append(';');
        values.setProperty(prefix + "pins", coordinates.toString());
        Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (var writer = Files.newBufferedWriter(temporary)) { values.store(writer, "CCEC local atlas view"); }
        Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private static Properties read(Path path) {
        Properties result = new Properties();
        if (Files.isRegularFile(path)) try (var reader = Files.newBufferedReader(path)) {
            result.load(reader);
        } catch (IOException | IllegalArgumentException ignored) { }
        return result;
    }
}
