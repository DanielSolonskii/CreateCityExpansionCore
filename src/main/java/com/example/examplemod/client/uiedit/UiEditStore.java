package com.example.examplemod.client.uiedit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Файловое хранилище правок: {@code config/createcityexpansioncore/ui-edit/<Экран>.json}.
 *
 * <p>Только java.nio + Gson, без Minecraft — проверяется headless-задачей
 * {@code checkUiEdit}. Экспорт пишет рядом человеческий файл {@code export/<Экран>.txt}:
 * JSON живёт для игры, экспорт — для переноса значений в код вручную.
 */
public final class UiEditStore {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path root;

    public UiEditStore(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    /** Имя файла экрана. Имя класса может содержать только безопасные символы. */
    public static String safeName(String screen) {
        if (screen == null || screen.isBlank()) {
            return "Screen";
        }
        StringBuilder out = new StringBuilder(screen.length());
        for (int i = 0; i < screen.length(); i++) {
            char c = screen.charAt(i);
            out.append(Character.isLetterOrDigit(c) || c == '_' || c == '$' ? c : '_');
        }
        return out.toString();
    }

    public Path fileFor(String screen) {
        return root.resolve(safeName(screen) + ".json");
    }

    /** Пусто при отсутствии файла; битый файл — пусто с флагом {@code broken}. */
    public UiEditModel.ScreenOverrides load(String screen) {
        Path file = fileFor(screen);
        if (!Files.isRegularFile(file)) {
            return new UiEditModel.ScreenOverrides(screen);
        }
        try {
            return UiEditModel.ScreenOverrides.fromJson(screen, Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            UiEditModel.ScreenOverrides broken = new UiEditModel.ScreenOverrides(screen);
            broken.markBroken();
            return broken;
        }
    }

    /**
     * Запись на диск. Пустой набор удаляет файл — экран возвращается к ванильной
     * раскладке, и в конфиге не копятся пустышки.
     *
     * @return фактически записанный файл
     */
    public Path save(UiEditModel.ScreenOverrides overrides) throws IOException {
        Path file = fileFor(overrides.screen());
        if (overrides.broken()) {
            throw new IOException("refusing to overwrite unreadable file: " + file);
        }
        if (overrides.isEmpty()) {
            Files.deleteIfExists(file);
            return file;
        }
        Files.createDirectories(root);
        Files.writeString(file, overrides.toJson(), StandardCharsets.UTF_8);
        return file;
    }

    /** Строка экспорта: сдвиги, размеры и тексты каждой правки. */
    public static String exportText(String screen, String guiSize, List<ExportRow> rows) {
        StringBuilder out = new StringBuilder();
        out.append("=== UI-редактор: экспорт ").append(screen)
                .append(" (окно ").append(guiSize)
                .append(", ").append(ZonedDateTime.now().format(STAMP)).append(") ===\n");
        if (rows.isEmpty()) {
            out.append("Правок нет.\n");
            return out.toString();
        }
        out.append("Формат: сдвиг dx,dy и размер dw,dh от исходной позиции виджета.\n\n");
        for (ExportRow row : rows) {
            UiEditModel.WidgetOverride override = row.override();
            out.append(row.key()).append('\n');
            out.append("    сдвиг ").append(fmt(override.dx())).append(',').append(fmt(override.dy()))
                    .append("  размер ").append(fmt(override.dw())).append(',').append(fmt(override.dh())).append('\n');
            out.append("    было:  ").append(describe(row.original().bounds()))
                    .append("  → стало: ").append(describe(row.current())).append('\n');
            if (override.hasText()) {
                out.append("    текст: \"").append(row.original().text())
                        .append("\" → \"").append(override.text()).append("\"\n");
            }
            out.append('\n');
        }
        out.append("В JSON это сохранено автоматически:\n    ")
                .append("config/createcityexpansioncore/ui-edit/").append(safeName(screen)).append(".json\n");
        return out.toString();
    }

    /** Запись экспорта в {@code ui-edit/export/<Экран>.txt}. */
    public Path writeExport(String screen, String content) throws IOException {
        Path dir = root.resolve("export");
        Files.createDirectories(dir);
        Path file = dir.resolve(safeName(screen) + ".txt");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    /** Строка экспорта для одной правки. */
    public record ExportRow(
            String key,
            UiEditModel.Original original,
            UiEditModel.Bounds current,
            UiEditModel.WidgetOverride override) {

        public static ExportRow of(
                String key,
                UiEditModel.Original original,
                UiEditModel.Bounds current,
                UiEditModel.WidgetOverride override) {
            return new ExportRow(key, original, current, override);
        }
    }

    /** Сбор строк экспорта по списку ключей в порядке чтения. Без Minecraft. */
    public static List<ExportRow> rowsFor(
            UiEditModel.ScreenOverrides overrides,
            List<UiEditModel.Original> originals,
            List<UiEditModel.Bounds> currentBounds,
            List<String> classNames) {
        List<ExportRow> rows = new ArrayList<>();
        for (var entry : overrides.widgets().entrySet()) {
            int index = UiEditModel.keyIndex(entry.getKey());
            if (index < 0 || index >= originals.size() || index >= currentBounds.size()) {
                continue;
            }
            if (originals.get(index) == null || currentBounds.get(index) == null) {
                continue;
            }
            String expected = UiEditModel.widgetKey(index, classNames.get(index));
            if (!expected.equals(entry.getKey())) {
                continue;
            }
            rows.add(ExportRow.of(entry.getKey(), originals.get(index), currentBounds.get(index), entry.getValue()));
        }
        return rows;
    }

    private static String describe(UiEditModel.Bounds b) {
        return "x=" + b.x() + " y=" + b.y() + " " + b.w() + "\u00d7" + b.h();
    }

    private static String fmt(int value) {
        return value >= 0 ? "+" + value : String.valueOf(value);
    }
}
