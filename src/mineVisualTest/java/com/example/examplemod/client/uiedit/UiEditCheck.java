package com.example.examplemod.client.uiedit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Headless-проверка редактора UI: модель дельт, JSON, файловое хранилище,
 * экспорт и allowlist — без запуска игры.
 *
 * <p>Запускается задачей {@code checkUiEdit} и входит в {@code gradlew check}.
 * Живёт в том же пакете, что и инструмент, чтобы видеть package-private API.
 */
public final class UiEditCheck {

    private static int checks;

    private static void require(boolean condition, String reason) {
        checks++;
        if (!condition) {
            throw new AssertionError(reason);
        }
    }

    public static void main(String[] args) throws Exception {
        keysAndDeltas();
        jsonRoundTrip();
        brokenJson();
        storeFiles();
        exportText();
        allowlist();

        System.out.println("ok: ui edit " + checks + " checks");
    }

    // ------------------------------------------------------------------
    // Ключи и дельты
    // ------------------------------------------------------------------

    private static void keysAndDeltas() {
        String key = UiEditModel.widgetKey(4, "ChromeButton");
        require("4:ChromeButton".equals(key), "key format");
        require(UiEditModel.keyIndex(key) == 4, "key index");
        require("ChromeButton".equals(UiEditModel.keyClass(key)), "key class");
        require(UiEditModel.keyMatches(key, 4, "ChromeButton"), "matching key");
        require(!UiEditModel.keyMatches(key, 5, "ChromeButton"), "index shift is rejected");
        require(!UiEditModel.keyMatches(key, 4, "OtherButton"), "class mismatch is rejected");
        require(UiEditModel.keyIndex("garbage") == -1, "garbage index");
        require(UiEditModel.keyIndex(":NoIndex") == -1, "missing index");
        require(UiEditModel.keyClass("bad") .isEmpty(), "missing class");

        UiEditModel.Bounds original = new UiEditModel.Bounds(100, 50, 120, 20);
        UiEditModel.Bounds current = new UiEditModel.Bounds(104, 48, 130, 20);
        UiEditModel.WidgetOverride delta =
                UiEditModel.delta(original, current, "Сохранить", "Применить");
        require(delta.dx() == 4 && delta.dy() == -2, "delta position");
        require(delta.dw() == 10 && delta.dh() == 0, "delta size");
        require("Применить".equals(delta.text()), "delta text");
        require(delta.applyTo(original).equals(current), "delta round trip");

        UiEditModel.WidgetOverride same =
                UiEditModel.delta(original, original, "Same", "Same");
        require(same.isIdentity(), "unchanged widget is identity");
        require(same.text() == null, "unchanged text is null");

        UiEditModel.WidgetOverride textOnly =
                UiEditModel.delta(original, original, "Old", "Old");
        require(textOnly.isIdentity(), "same text stays identity");

        UiEditModel.Bounds shrunk =
                new UiEditModel.WidgetOverride(0, 0, -1000, -1000, null).applyTo(original);
        require(shrunk.w() == UiEditModel.MIN_SIZE && shrunk.h() == UiEditModel.MIN_SIZE,
                "minimum size clamp");
    }

    // ------------------------------------------------------------------
    // JSON
    // ------------------------------------------------------------------

    private static void jsonRoundTrip() {
        UiEditModel.ScreenOverrides overrides = new UiEditModel.ScreenOverrides("CityPylonScreen");
        overrides.put(UiEditModel.widgetKey(0, "ChromeButton"),
                new UiEditModel.WidgetOverride(4, -2, 10, 0, "Применить → сохранить"));
        overrides.put(UiEditModel.widgetKey(7, "EditBox"),
                new UiEditModel.WidgetOverride(0, 0, 0, 0, "Текст"));
        overrides.put(UiEditModel.widgetKey(1, "Button"),
                new UiEditModel.WidgetOverride(2, 0, 0, 0, null));
        require(overrides.size() == 3, "stored overrides");

        String json = overrides.toJson();
        require(json.contains("\"version\": 1"), "version in json");
        require(json.contains("Применить → сохранить"), "unicode survives json");

        UiEditModel.ScreenOverrides back =
                UiEditModel.ScreenOverrides.fromJson("CityPylonScreen", json);
        require(!back.broken(), "valid json is not broken");
        require(back.size() == 3, "json round trip size");
        UiEditModel.WidgetOverride first = back.get(UiEditModel.widgetKey(0, "ChromeButton"));
        require(first != null && first.dx() == 4 && first.dy() == -2 && first.dw() == 10,
                "json round trip deltas");
        require("Применить → сохранить".equals(first.text()), "json round trip unicode text");
        require(back.get(UiEditModel.widgetKey(1, "Button")).text() == null,
                "json round trip null text");

        // Неизвестные и битые поля не роняют разбор.
        UiEditModel.ScreenOverrides partial = UiEditModel.ScreenOverrides.fromJson("X",
                "{\"widgets\":{\"0:A\":{\"dx\":\"oops\",\"text\":123},\"bad\":{},\"2:B\":{}}}");
        require(!partial.broken(), "odd fields still parse");
        require(partial.get(UiEditModel.widgetKey(0, "A")) != null
                        && partial.get(UiEditModel.widgetKey(0, "A")).dx() == 0,
                "non-numeric field falls back to zero");

        // Правка с пустым содержимым исчезает, а не копится в файле.
        overrides.put(UiEditModel.widgetKey(7, "EditBox"),
                new UiEditModel.WidgetOverride(0, 0, 0, 0, null));
        require(overrides.size() == 2, "clearing text removes override");
    }

    private static void brokenJson() {
        UiEditModel.ScreenOverrides broken =
                UiEditModel.ScreenOverrides.fromJson("X", "{\"widgets\": [1,2,3]}");
        require(broken.broken(), "array widgets is broken");
        require(broken.isEmpty(), "broken json gives no overrides");

        UiEditModel.ScreenOverrides garbage =
                UiEditModel.ScreenOverrides.fromJson("X", "not json at all {");
        require(garbage.broken(), "garbage is broken");
        require(garbage.isEmpty(), "garbage gives no overrides");

        UiEditModel.ScreenOverrides absent =
                UiEditModel.ScreenOverrides.fromJson("X", "");
        require(!absent.broken() && absent.isEmpty(), "empty file is empty, not broken");
    }

    // ------------------------------------------------------------------
    // Файлы
    // ------------------------------------------------------------------

    private static void storeFiles() throws Exception {
        Path root = Files.createTempDirectory("ui-edit-check");
        UiEditStore store = new UiEditStore(root);

        require("CityPylonScreen".equals(UiEditStore.safeName("CityPylonScreen")), "safe name pass");
        require("Foo$1".equals(UiEditStore.safeName("Foo$1")), "safe name inner class");
        require(!UiEditStore.safeName("../evil").contains("/"), "safe name strips traversal");
        require("Screen".equals(UiEditStore.safeName(" ")), "safe name blank");

        UiEditModel.ScreenOverrides missing = store.load("NeverSaved");
        require(missing.isEmpty() && !missing.broken(), "missing file is empty");

        UiEditModel.ScreenOverrides overrides = new UiEditModel.ScreenOverrides("Pylon");
        overrides.put(UiEditModel.widgetKey(2, "ChromeButton"),
                new UiEditModel.WidgetOverride(6, 0, 0, 0, null));
        Path written = store.save(overrides);
        require(Files.isRegularFile(written), "save writes file");

        UiEditModel.ScreenOverrides loaded = store.load("Pylon");
        require(loaded.size() == 1 && !loaded.broken(), "load reads file back");
        require(loaded.get(UiEditModel.widgetKey(2, "ChromeButton")).dx() == 6, "file keeps delta");

        // Пустой набор удаляет файл — экран возвращается к ванили.
        loaded.clear();
        store.save(loaded);
        require(!Files.isRegularFile(written), "empty save deletes file");

        // Битый файл нельзя перезаписать молча.
        Files.writeString(root.resolve("Broken.json"), "trash {", StandardCharsets.UTF_8);
        UiEditModel.ScreenOverrides broken = store.load("Broken");
        require(broken.broken(), "broken file detected");
        boolean refused = false;
        try {
            store.save(broken);
        } catch (Exception e) {
            refused = true;
        }
        require(refused, "refuses to overwrite broken file");
        require("trash {".equals(Files.readString(root.resolve("Broken.json"), StandardCharsets.UTF_8)),
                "broken file untouched");

        Files.deleteIfExists(root.resolve("Broken.json"));
        Files.deleteIfExists(root);
    }

    // ------------------------------------------------------------------
    // Экспорт
    // ------------------------------------------------------------------

    private static void exportText() {
        UiEditModel.ScreenOverrides overrides = new UiEditModel.ScreenOverrides("MarketScreen");
        String key = UiEditModel.widgetKey(3, "ChromeButton");
        overrides.put(key, new UiEditModel.WidgetOverride(4, -2, 10, 0, "Купить"));

        UiEditModel.Original original =
                new UiEditModel.Original(new UiEditModel.Bounds(100, 50, 120, 20), "Покупка");
        List<UiEditStore.ExportRow> rows = UiEditStore.rowsFor(
                overrides,
                java.util.Arrays.asList(null, null, null, original),
                java.util.Arrays.asList(null, null, null, new UiEditModel.Bounds(104, 48, 130, 20)),
                List.of("A", "B", "C", "ChromeButton"));
        require(rows.size() == 1, "export rows for known widgets");

        String text = UiEditStore.exportText("MarketScreen", "427×240", rows);
        require(text.contains("MarketScreen"), "export names screen");
        require(text.contains(key), "export names key");
        require(text.contains("+4,-2"), "export shows delta");
        require(text.contains("ui-edit/MarketScreen.json"), "export points at json");
        require(text.contains("x=100 y=50 120×20"), "export shows before bounds");

        // Индекс вне диапазона и рассинхрон класса не должны ронять экспорт.
        UiEditModel.ScreenOverrides stale = new UiEditModel.ScreenOverrides("S");
        stale.put(UiEditModel.widgetKey(99, "Ghost"), new UiEditModel.WidgetOverride(1, 0, 0, 0, null));
        stale.put(UiEditModel.widgetKey(0, "Renamed"), new UiEditModel.WidgetOverride(1, 0, 0, 0, null));
        List<UiEditStore.ExportRow> staleRows = UiEditStore.rowsFor(
                stale,
                List.of(original),
                List.of(original.bounds()),
                List.of("ChromeButton"));
        require(staleRows.isEmpty(), "stale keys are skipped");

        String empty = UiEditStore.exportText("E", "1×1", List.of());
        require(empty.contains("Правок нет"), "empty export says so");
    }

    // ------------------------------------------------------------------
    // Allowlist
    // ------------------------------------------------------------------

    private static void allowlist() {
        Set<String> entries = UiEditAccess.parse(List.of(
                "# комментарий",
                "",
                "  goddzerzh  ",
                "\uFEFFGODDZERZH2",
                "ДругойИгрок"));
        require(entries.size() == 3, "parse skips comments and blanks");
        require(entries.contains("goddzerzh"), "parse trims");
        require(entries.contains("goddzerzh2"), "parse strips BOM");
        require(UiEditAccess.allows(entries, "GoddZerzh", "any-uuid"), "case-insensitive name");
        require(UiEditAccess.allows(entries, "someone", "другойигрок"), "uuid match");
        require(!UiEditAccess.allows(entries, "stranger", "uuid"), "stranger denied");
        require(!UiEditAccess.allows(Set.of(), "goddzerzh", "uuid"), "empty list denies everyone");

        String defaults = UiEditAccess.defaultFileText();
        require(defaults.contains("goddzerzh"), "default file contains owner nick");
        require(defaults.contains("drannch1k"), "default file contains lead developer nick");
        require(defaults.contains("#"), "default file has instructions");
    }

    private UiEditCheck() {
    }
}
