package com.example.examplemod.client.logistics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/** Local presentation preferences. Tutorial progress never sends a gameplay request. */
public final class LogisticsTutorial {
    public static final int VERSION = 1;
    public static final int STEPS = 6;
    private static Progress current;
    private static String loadedScope = "";
    private static String loadedServer;
    private static UUID loadedPlayer;

    /** Pure, bounded state also used by the headless persistence checks. */
    public record Progress(int step, boolean completed, boolean dismissed, boolean reducedMotion) {
        public Progress { step = Math.clamp(step, 0, STEPS - 1); }
        public boolean shouldOpen() { return !completed && !dismissed; }
    }

    private LogisticsTutorial() { }

    public static boolean shouldOpen() { return current().shouldOpen(); }
    public static int step() { return current().step(); }
    public static boolean reducedMotion() { return current().reducedMotion(); }

    public static void progress(int step) {
        Progress p = current();
        int next = Math.clamp(step, 0, STEPS - 1);
        if (p.step() == next) return;
        replace(new Progress(next, p.completed(), p.dismissed(), p.reducedMotion()));
    }

    public static void complete() {
        Progress p = current();
        replace(new Progress(STEPS - 1, true, false, p.reducedMotion()));
    }

    public static void dismiss() {
        Progress p = current();
        replace(new Progress(p.step(), p.completed(), true, p.reducedMotion()));
    }

    public static void setReducedMotion(boolean value) {
        Progress p = current();
        replace(new Progress(p.step(), p.completed(), p.dismissed(), value));
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config/ccec-logistics.properties");
    }

    private static Progress current() {
        Minecraft mc = Minecraft.getInstance();
        String server = mc.getCurrentServer() == null ? "local" : mc.getCurrentServer().ip;
        UUID player = mc.player == null ? null : mc.player.getUUID();
        if (current == null || !server.equals(loadedServer) || !java.util.Objects.equals(player, loadedPlayer)) {
            loadedServer = server;
            loadedPlayer = player;
            loadedScope = UUID.nameUUIDFromBytes((server + ":" + (player == null ? "" : player))
                    .getBytes(StandardCharsets.UTF_8)).toString();
            current = load(path(), loadedScope);
        }
        return current;
    }

    private static void replace(Progress value) {
        current = value;
        try { save(path(), loadedScope, value); }
        catch (IOException | IllegalArgumentException ignored) {
            // A read-only config directory must not prevent use of the logistics UI.
        }
    }

    public static Progress load(Path path, String scope) {
        Properties properties = read(path);
        String prefix = scope + ".";
        boolean reduced = Boolean.parseBoolean(properties.getProperty("reducedMotion", "false"));
        if (!Integer.toString(VERSION).equals(properties.getProperty(prefix + "version")))
            return new Progress(0, false, false, reduced);
        int step;
        try { step = Integer.parseInt(properties.getProperty(prefix + "step", "0")); }
        catch (NumberFormatException ignored) { step = 0; }
        return new Progress(step,
                Boolean.parseBoolean(properties.getProperty(prefix + "completed", "false")),
                Boolean.parseBoolean(properties.getProperty(prefix + "dismissed", "false")), reduced);
    }

    public static void save(Path path, String scope, Progress progress) throws IOException {
        Properties properties = read(path);
        String prefix = scope + ".";
        properties.setProperty(prefix + "version", Integer.toString(VERSION));
        properties.setProperty(prefix + "step", Integer.toString(progress.step()));
        properties.setProperty(prefix + "completed", Boolean.toString(progress.completed()));
        properties.setProperty(prefix + "dismissed", Boolean.toString(progress.dismissed()));
        properties.setProperty("reducedMotion", Boolean.toString(progress.reducedMotion()));
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try (var writer = Files.newBufferedWriter(temporary)) {
            properties.store(writer, "CCEC logistics presentation and tutorial");
        }
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
    }

    private static Properties read(Path path) {
        Properties result = new Properties();
        if (Files.isRegularFile(path)) try (var reader = Files.newBufferedReader(path)) {
            result.load(reader);
        } catch (IOException | IllegalArgumentException ignored) { }
        return result;
    }
}
