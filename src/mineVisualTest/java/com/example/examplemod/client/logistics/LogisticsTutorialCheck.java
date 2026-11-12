package com.example.examplemod.client.logistics;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

/** Real Properties round trips. No Minecraft client, network or rendering bootstrap is required. */
public final class LogisticsTutorialCheck {
    private static int checks;
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void run() {
        try {
            var directory = Files.createTempDirectory("ccec-logistics-tutorial-");
            var path = directory.resolve("logistics.properties");
            try {
                var first = LogisticsTutorial.load(path, "first");
                require(first.shouldOpen() && first.step() == 0, "first visit opens the first guide page");
                LogisticsTutorial.save(path, "first", new LogisticsTutorial.Progress(3, false, false, false));
                var resumed = LogisticsTutorial.load(path, "first");
                require(resumed.shouldOpen() && resumed.step() == 3, "unfinished guide resumes after reloading preferences");
                require(LogisticsTutorial.load(path, "second").shouldOpen(), "other players or servers have their own first visit");

                LogisticsTutorial.save(path, "second", new LogisticsTutorial.Progress(2, false, true, true));
                var dismissed = LogisticsTutorial.load(path, "second");
                require(!dismissed.shouldOpen() && dismissed.dismissed() && dismissed.step() == 2, "explicit dismissal survives restart");
                require(LogisticsTutorial.load(path, "first").step() == 3, "saving another scope preserves earlier progress");
                require(LogisticsTutorial.load(path, "first").reducedMotion(), "motion preference is shared by local workspaces");

                LogisticsTutorial.save(path, "first", new LogisticsTutorial.Progress(5, true, false, false));
                var completed = LogisticsTutorial.load(path, "first");
                require(!completed.shouldOpen() && completed.completed(), "completed guide does not reopen automatically");
                require(!LogisticsTutorial.load(path, "second").shouldOpen(), "completion cannot clear another scope's dismissal");
                require(new LogisticsTutorial.Progress(-10, false, false, false).step() == 0, "negative page values are bounded");
                require(new LogisticsTutorial.Progress(100, false, false, false).step() == 5, "page values past the guide are bounded");

                Properties properties = new Properties();
                try (var reader = Files.newBufferedReader(path)) { properties.load(reader); }
                properties.setProperty("first.version", "0");
                properties.setProperty("reducedMotion", "true");
                try (var writer = Files.newBufferedWriter(path)) { properties.store(writer, "old guide"); }
                var revised = LogisticsTutorial.load(path, "first");
                require(revised.shouldOpen() && revised.step() == 0 && revised.reducedMotion(), "a revised tutorial restarts while preserving reduced motion");

                properties.setProperty("first.version", Integer.toString(LogisticsTutorial.VERSION));
                properties.setProperty("first.step", "invalid");
                properties.setProperty("first.completed", "false");
                try (var writer = Files.newBufferedWriter(path)) { properties.store(writer, "corrupt page"); }
                require(LogisticsTutorial.load(path, "first").step() == 0, "malformed page values recover to the beginning");
                Files.writeString(path, "broken=\\uTHIS");
                require(LogisticsTutorial.load(path, "first").shouldOpen(), "malformed Properties cannot prevent opening logistics");
            } finally {
                Files.deleteIfExists(path.resolveSibling(path.getFileName() + ".tmp"));
                Files.deleteIfExists(path);
                Files.deleteIfExists(directory);
            }
        } catch (IOException failure) { throw new AssertionError("tutorial preferences round trip", failure); }
        System.out.println("PASS: " + checks + " logistics tutorial persistence checks");
    }

    public static void main(String[] args) { run(); }
}
