package com.example.examplemod.knowledge;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Общая работа с каталогом базы знаний.
 *
 * <p>Каталог лежит в game dir и называется {@code CCEC - knowledge base} (двоеточие
 * запрещено Windows). Один и тот же ход по каталогу нужен и серверу — чтобы выдать
 * {@code default: visible} файлы при входе игрока, — и клиенту — чтобы показать
 * список, содержимое и картинки. Часть логики (доступ) сведена в
 * {@link KnowledgeAccess}.
 */
public final class KnowledgeStorage {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean folderWarned;

    private KnowledgeStorage() {
    }

    /** Путь к каталогу базы знаний (создаётся по запросу, не в конструторе). */
    public static Path folder() {
        return FMLPaths.GAMEDIR.get().resolve(KnowledgeFileMeta.FOLDER_NAME);
    }

    /** Создаёт папку, если её нет. Клиент вызывает при первом открытии Интерфейса. */
    public static void ensureFolder() {
        try {
            Files.createDirectories(folder());
        } catch (IOException e) {
            LOGGER.warn("Не удалось создать папку базы знаний", e);
        }
    }

    /**
     * Отдаёт относительные пути всех .md файлов (обход рекурсивный, сортировка
     * алфавитная). Каталог обязан существовать, иначе пусто.
     */
    public static List<String> markdownFiles() {
        Path folder = folder();
        if (!Files.isDirectory(folder)) return List.of();
        List<String> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(folder)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".md"))
                    .map(p -> rel(folder, p))
                    .sorted()
                    .forEach(result::add);
        } catch (IOException e) {
            LOGGER.warn("Не удалось обойти базу знаний", e);
            return List.of();
        }
        return result;
    }

    /**
     * Общий обход: каждому файлу отдаём relative path и настоящий {@link Path}.
     * Так сервер и клиент не дублируют ход по каталогу.
     */
    public static void forEachMarkdown(Consumer<PathWithRel> visitor) {
        Path folder = folder();
        if (!Files.isDirectory(folder)) {
            if (!folderWarned) {
                folderWarned = true;
                LOGGER.info("База знаний: папка {} отсутствует, буду читать список только по доступу", folder);
            }
            return;
        }
        folderWarned = false;
        try (Stream<Path> stream = Files.walk(folder)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".md"))
                    .map(p -> new PathWithRel(folder.relativize(p).toString().replace('\\', '/'), p))
                    .sorted((a, b) -> a.rel().compareTo(b.rel()))
                    .forEach(visitor);
        } catch (IOException e) {
            LOGGER.warn("Не удалось обойти базу знаний", e);
        }
    }

    /** Читает первые строки файла — достаточно для переднего поля и заголовков. */
    public static List<String> firstLines(Path file, int limit) {
        try (Stream<String> lines = Files.lines(file, java.nio.charset.StandardCharsets.UTF_8)) {
            return lines.limit(limit).toList();
        } catch (IOException e) {
            LOGGER.warn("Не удалось прочитать {}", file, e);
            return List.of();
        }
    }

    public static String rel(Path folder, Path file) {
        return folder.relativize(file).toString().replace('\\', '/');
    }

    /** Относительный путь + путь на диске. */
    public record PathWithRel(String rel, Path file) {
    }
}
