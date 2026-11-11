package com.example.examplemod.cutscene;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class CutsceneStore {
    private static final Pattern SCENE_ID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,63}");
    private static final Pattern RESERVED_FILE_NAME = Pattern.compile("con|prn|aux|nul|com[1-9]|lpt[1-9]");
    private static final String JSON_EXTENSION = ".json";

    private CutsceneStore() {
    }

    public static String validateId(String id) {
        if (!validId(id)) {
            throw new IllegalArgumentException("Use a scene ID such as intro or chapter_2 (lowercase ASCII, 1–64 characters)");
        }
        return id;
    }

    public static Cutscene load(MinecraftServer server, String id) throws IOException {
        String fileName = validateId(id) + JSON_EXTENSION;
        Path directory = directory(server, false);
        if (directory == null) {
            return null;
        }
        Path file = directory.resolve(fileName);
        try {
            if (!attributes(file).isRegularFile()) {
                throw new IOException("Cutscene path is not a regular file: " + fileName);
            }
            try (InputStream input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                byte[] bytes = input.readNBytes(CutsceneJson.MAX_JSON_BYTES + 1);
                if (bytes.length > CutsceneJson.MAX_JSON_BYTES) {
                    throw new IOException("Cutscene file exceeds " + CutsceneJson.MAX_JSON_BYTES + " UTF-8 bytes: " + fileName);
                }
                String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                return CutsceneJson.decode(json);
            }
        } catch (NoSuchFileException exception) {
            return null;
        }
    }

    public static void save(MinecraftServer server, String id, Cutscene scene) throws IOException {
        String fileName = validateId(id) + JSON_EXTENSION;
        String json = CutsceneJson.encode(scene);
        Path directory = directory(server, true);
        Path temporaryFile = Files.createTempFile(directory, "." + id + "-", ".tmp");
        try {
            Files.writeString(temporaryFile, json, StandardCharsets.UTF_8);
            Files.move(temporaryFile, directory.resolve(fileName), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    public static List<String> list(MinecraftServer server) throws IOException {
        Path directory = directory(server, false);
        if (directory == null) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(JSON_EXTENSION))
                    .map(name -> name.substring(0, name.length() - JSON_EXTENSION.length()))
                    .filter(CutsceneStore::validId)
                    .sorted()
                    .toList();
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private static boolean validId(String id) {
        return id != null && SCENE_ID.matcher(id).matches() && !RESERVED_FILE_NAME.matcher(id).matches();
    }

    private static Path directory(MinecraftServer server, boolean create) throws IOException {
        Path path = server.getWorldPath(LevelResource.ROOT);
        for (String segment : List.of("ccec", "cutscenes")) {
            path = path.resolve(segment);
            if (create) {
                try {
                    Files.createDirectory(path);
                } catch (FileAlreadyExistsException ignored) {
                    // The existing path is verified below before following it.
                }
            }
            try {
                if (!attributes(path).isDirectory()) {
                    throw new IOException("Cutscene storage path is not a directory: " + path);
                }
            } catch (NoSuchFileException exception) {
                if (create) {
                    throw exception;
                }
                return null;
            }
        }
        return path;
    }

    private static BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }
}
