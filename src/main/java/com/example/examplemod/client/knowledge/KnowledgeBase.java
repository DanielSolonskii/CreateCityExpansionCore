package com.example.examplemod.client.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Локальные заметки .md из папки «knowledge base» игровой папки клиента.
 *
 * <p>Класс работает только с файловой системой игрока: файлы не читаются на
 * сервере и никуда не отправляются. Поэтому клавиша открытия экрана не требует
 * прав и не меняет состояние мира — открывается чисто локальный справочник.
 */
public final class KnowledgeBase {
    /** Папка создаётся внутри игровой папки (.minecraft у игрока, run/ в dev). */
    public static final String FOLDER_NAME = "knowledge base";
    /** Первая папка получает заметку-образец, чтобы экран не открывался пустым. */
    public static final String SAMPLE_NAME = "getting-started.md";

    /** Один файл заметки: путь на диске и отображаемое имя без расширения .md. */
    public record Note(Path path, String name) {}

    private KnowledgeBase() {}

    public static Path folder(Path gameDirectory) {
        return gameDirectory.resolve(FOLDER_NAME);
    }

    /** Создаёт папку при первом обращении; true — папки до этого не было. */
    public static boolean ensureFolder(Path folder) {
        try {
            if (Files.isDirectory(folder)) {
                return false;
            }
            Files.createDirectories(folder);
            return true;
        } catch (IOException | SecurityException failure) {
            return false;
        }
    }

    /** Записывает образец только если его ещё нет; отсутствие прав не ломает экран. */
    public static void writeSample(Path folder, boolean russian) {
        try {
            Path sample = folder.resolve(SAMPLE_NAME);
            if (!Files.exists(sample)) {
                Files.writeString(sample, russian ? SAMPLE_RU : SAMPLE_EN, StandardCharsets.UTF_8);
            }
        } catch (IOException | SecurityException failure) {
            // Нет прав на запись — игрок положит собственные файлы сам.
        }
    }

    /** Все *.md папки, отсортированные по имени без учёта регистра. */
    public static List<Note> list(Path folder) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            List<Note> notes = new ArrayList<>();
            for (Path path : stream) {
                String fileName = path.getFileName().toString();
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                if (!fileName.toLowerCase(Locale.ROOT).endsWith(".md")) {
                    continue;
                }
                notes.add(new Note(path, fileName.substring(0, fileName.length() - 3)));
            }
            notes.sort(Comparator.comparing(note -> note.name().toLowerCase(Locale.ROOT)));
            return notes;
        } catch (IOException | SecurityException failure) {
            return List.of();
        }
    }

    /** Некорректные байты заменяются, а § обезвреживается — это коды цвета чата. */
    public static String read(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return new String(bytes, StandardCharsets.UTF_8).replace('\u00A7', '&');
    }

    private static final String SAMPLE_RU = """
            # База знаний

            Это демонстрационная заметка. Свои файлы кладите рядом, в ту же папку.

            ## Как добавить заметку

            1. Создайте файл с расширением `.md`.
            2. Положите его в папку `knowledge base` внутри папки игры.
            3. Нажмите **K**, чтобы открыть базу знаний.

            ## Что поддерживается

            - заголовки: `#`, `##`, `###`
            - **жирный** текст, *курсив* и `код`
            - нумерованные списки, цитаты и блоки кода

            > Файлы хранятся только на вашем компьютере.

            ```
            // пример блока кода
            ```

            Файлы перечитываются при каждом открытии экрана.
            """;

    private static final String SAMPLE_EN = """
            # Knowledge Base

            This is a demo note. Put your own files next to it, in the same folder.

            ## Adding a note

            1. Create a file with the `.md` extension.
            2. Drop it into the `knowledge base` folder inside your game folder.
            3. Press **K** to open the knowledge base.

            ## What is supported

            - headings: `#`, `##`, `###`
            - **bold** text, *italic* and `code`
            - numbered lists, quotes and code blocks

            > Files stay on your computer only.

            ```
            // code block example
            ```

            Files are re-read every time the screen opens.
            """;
}
