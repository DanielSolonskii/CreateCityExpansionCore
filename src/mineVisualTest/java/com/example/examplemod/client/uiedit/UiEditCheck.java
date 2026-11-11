package com.example.examplemod.client.uiedit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import com.example.examplemod.client.screen.CityPylonWorkspaceLayout;

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
        elementsJson();
        brokenJson();
        storeFiles();
        exportText();
        allowlist();
        layoutHooks();
        workspaceChrome();

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

    // ------------------------------------------------------------------
    // Элементы layout (фаза 2: ручные экраны)
    // ------------------------------------------------------------------

    private static void elementsJson() {
        UiEditModel.ScreenOverrides overrides = new UiEditModel.ScreenOverrides("PlayerProfile");
        overrides.putElement("close", new UiEditModel.WidgetOverride(4, 6, 0, 0, null));
        overrides.putElement("tab[0]", new UiEditModel.WidgetOverride(0, 0, 10, 2, null));
        require(overrides.elements().size() == 2, "elements stored");
        require(!overrides.isEmpty(), "element-only set is not empty");

        String json = overrides.toJson();
        require(json.contains("\"elements\""), "elements node in json");
        require(json.contains("tab[0]"), "element name survives json");

        UiEditModel.ScreenOverrides back =
                UiEditModel.ScreenOverrides.fromJson("PlayerProfile", json);
        require(!back.broken(), "elements json parses");
        require(back.size() == 0, "widgets stay empty");
        require(back.getElement("close") != null && back.getElement("close").dx() == 4,
                "element round trip");
        require(back.getElement("tab[0]").dw() == 10, "indexed element round trip");

        // Пустое имя игнорируется, пустая правка удаляется — файл не засоряется.
        overrides.putElement("   ", new UiEditModel.WidgetOverride(1, 0, 0, 0, null));
        require(overrides.elements().size() == 2, "blank element name ignored");
        overrides.putElement("close", new UiEditModel.WidgetOverride(0, 0, 0, 0, null));
        require(overrides.elements().size() == 1, "identity edit removes element");
        require(overrides.getElement("missing") == null, "unknown element is null");

        // Битый узел elements — битый файл, как и с widgets.
        UiEditModel.ScreenOverrides broken =
                UiEditModel.ScreenOverrides.fromJson("X", "{\"elements\": [1]}");
        require(broken.broken() && broken.isEmpty(), "array elements is broken");

        // Узел widgets отсутствует, elements есть — разбирается.
        UiEditModel.ScreenOverrides only =
                UiEditModel.ScreenOverrides.fromJson("X", "{\"elements\":{\"body\":{\"dx\":3}}}");
        require(!only.broken() && only.getElement("body").dx() == 3,
                "elements without widgets parse");

        // Чистый набор: clear убирает обе половины.
        only.clear();
        require(only.isEmpty() && only.elements().isEmpty(), "clear removes elements too");
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

    // ------------------------------------------------------------------
    // Хуки layout-классов (фаза 2)
    // ------------------------------------------------------------------

    private static void layoutHooks() throws Exception {
        Path root = Files.createTempDirectory("ui-edit-layout");
        UiEditLayout.installForTests(new UiEditStore(root), () -> true);

        // 0. Без активного экрана хук — чистая проходная функция и ничего не пишет
        // (так живут headless-проверки layout-классов без Minecraft).
        int[] inert = UiEditLayout.apply("close", 10, 20, 30, 40,
                2f, 100f, 50f, (x, y, w, h) -> new int[]{x, y, w, h});
        require(inert[0] == 10 && inert[1] == 20, "no active screen: pass-through");
        require(UiEditLayout.registeredNames("TestScreen").isEmpty(),
                "no active screen: nothing registered");

        // 1. Без правок хук отдаёт ровно те же координаты и регистрирует элемент
        // под ключом АКТИВНОГО экрана (то, что видит сессия редактора).
        UiEditLayout.activateForTests("TestScreen");
        int[] raw = UiEditLayout.apply("close", 10, 20, 30, 40,
                2f, 100f, 50f, (x, y, w, h) -> new int[]{x, y, w, h});
        require(raw[0] == 10 && raw[1] == 20 && raw[2] == 30 && raw[3] == 40,
                "hook is pass-through without edits");
        require(UiEditLayout.registeredFor("TestScreen").containsKey("close"),
                "hook registers element under active screen key");
        require(UiEditLayout.registeredFor("PlayerProfileScreen").isEmpty(),
                "registration never leaks to a guessed screen key");

        // 2. Экранные координаты: screen = origin + local * scale.
        UiEditModel.Bounds screen =
                UiEditLayout.elementToScreen("TestScreen", "close", new UiEditModel.Bounds(10, 20, 30, 40));
        require(screen.x() == 120 && screen.y() == 90 && screen.w() == 60 && screen.h() == 80,
                "local to screen transform");

        // 3. Правка применяется в логических единицах (переживает GUI scale).
        UiEditLayout.overridesFor("TestScreen")
                .putElement("close", new UiEditModel.WidgetOverride(5, -3, 0, 0, null));
        int[] moved = UiEditLayout.apply("close", 10, 20, 30, 40,
                2f, 100f, 50f, (x, y, w, h) -> new int[]{x, y, w, h});
        require(moved[0] == 15 && moved[1] == 17, "delta applied in layout units");

        // 4. Обратное преобразование — для коммита перетаскивания.
        UiEditModel.Bounds back = UiEditLayout.elementToLayout("TestScreen", "close",
                new UiEditModel.Bounds(130, 84, 60, 80));
        require(back.x() == 15 && back.y() == 17 && back.w() == 30 && back.h() == 40,
                "screen to layout transform");
        require(UiEditLayout.elementToScreenDelta("TestScreen", "close", 2) == 4, "layout delta to screen");
        UiEditLayout.apply("frame", 10, 20, 30, 40, UiEditModel.Bounds::new);
        UiEditLayout.apply("page.button", 10, 20, 30, 40, .5f, 200f, 100f, UiEditModel.Bounds::new);
        var baseBounds = new UiEditModel.Bounds(10, 20, 30, 40);
        require(UiEditLayout.elementToScreen("TestScreen", "frame", baseBounds).equals(baseBounds),
                "workspace frame keeps screen coordinates");
        require(UiEditLayout.elementToScreen("TestScreen", "page.button", baseBounds)
                .equals(new UiEditModel.Bounds(205, 110, 15, 20)), "workspace page has its own origin and scale");
        require(UiEditLayout.toScreen("TestScreen", baseBounds).equals(baseBounds),
                "layout registration does not alter widget coordinates");
        UiEditLayout.clearElements("page.");
        require(!UiEditLayout.registeredFor("TestScreen").containsKey("page.button")
                && UiEditLayout.registeredFor("TestScreen").containsKey("frame"),
                "page switch invalidates page controls only");

        // 5. Файл — общий источник: что сохранили, то видит хук после reload.
        UiEditModel.ScreenOverrides working = UiEditLayout.overridesFor("PlayerProfile");
        working.putElement("body", new UiEditModel.WidgetOverride(-8, 0, 0, 0, null));
        new UiEditStore(root).save(working);
        UiEditModel.ScreenOverrides reloaded = UiEditLayout.reload("PlayerProfile");
        require(reloaded.getElement("body").dx() == -8, "file round trip via hook");
        UiEditLayout.reload("TestScreen");
        int[] afterReload = UiEditLayout.apply("close", 10, 20, 30, 40,
                2f, 100f, 50f, (x, y, w, h) -> new int[]{x, y, w, h});
        require(afterReload[0] == 10, "reload discards unsaved in-memory edit");

        // 6. Экран без pose-масштаба — координаты не конвертируются.
        UiEditLayout.activateForTests("Plain");
        UiEditLayout.apply("body", 1, 2, 3, 4, (x, y, w, h) -> new int[]{x, y, w, h});
        UiEditModel.Bounds plain =
                UiEditLayout.toScreen("Plain", new UiEditModel.Bounds(1, 2, 3, 4));
        require(plain.x() == 1 && plain.w() == 3, "identity transform");
        require(!UiEditLayout.hasTransform("Plain"), "no transform for plain screen");

        // 7. Реестр чистится перед новым экраном.
        require(!UiEditLayout.registeredNames("TestScreen").isEmpty(), "registry has names");
        UiEditLayout.clearRegistry();
        require(UiEditLayout.registeredNames("TestScreen").isEmpty(), "clearRegistry empties");

        // 8. Доступ закрыт — правка в памяти есть, но не применяется.
        UiEditLayout.installForTests(new UiEditStore(root), () -> false);
        UiEditLayout.activateForTests("TestScreen");
        UiEditLayout.overridesFor("TestScreen")
                .putElement("close", new UiEditModel.WidgetOverride(5, 0, 0, 0, null));
        int[] gated = UiEditLayout.apply("close", 10, 20, 30, 40,
                1f, 0f, 0f, (x, y, w, h) -> new int[]{x, y, w, h});
        require(gated[0] == 10, "closed gate: override not applied");

        UiEditLayout.installForTests(new UiEditStore(root), () -> true);
        Files.deleteIfExists(root.resolve("PlayerProfile.json"));
        Files.deleteIfExists(root);
    }

    // ------------------------------------------------------------------
    // Хром пилона (CityPylonWorkspace)
    // ------------------------------------------------------------------

    private static void workspaceChrome() throws Exception {
        Path root = Files.createTempDirectory("ui-edit-pylon");
        UiEditLayout.installForTests(new UiEditStore(root), () -> true);
        UiEditLayout.activateForTests("CityPylonWorkspace");

        // 1. Без правок calculate отдаёт базовую геометрию и регистрирует хром.
        var base = CityPylonWorkspaceLayout.calculate(854, 480, false);
        var names = UiEditLayout.registeredNames("CityPylonWorkspace");
        require(names.contains("frame") && names.contains("content"), "pylon chrome registered");
        require(UiEditLayout.registeredFor("PlayerProfileScreen").isEmpty(),
                "pylon chrome does not leak to other screens");

        // 2. Рамка: дельта двигает всё, что из неё выводится (контент, pose-начало).
        UiEditLayout.overridesFor("CityPylonWorkspace")
                .putElement("frame", new UiEditModel.WidgetOverride(12, 6, 0, 0, null));
        var framed = CityPylonWorkspaceLayout.calculate(854, 480, false);
        require(framed.frameX() == base.frameX() + 12 && framed.frameY() == base.frameY() + 6,
                "frame delta moves the frame");
        require(framed.x() == base.x() + 12 && framed.originX() == base.originX() + 12,
                "content viewport follows the frame");
        require(framed.originY() == base.originY() + 6, "pose origin follows the frame");

        // 3. Контент: дельта двигает окно просмотра и pose-начало страницы.
        UiEditLayout.overridesFor("CityPylonWorkspace")
                .putElement("content", new UiEditModel.WidgetOverride(0, 9, 0, 0, null));
        var content = CityPylonWorkspaceLayout.calculate(854, 480, false);
        require(content.y() == framed.y() + 9 && content.originY() == framed.originY() + 9,
                "content delta moves viewport and pose origin");
        require(content.contains(content.x(), content.y()), "shifted viewport accepts input");

        // 4. Навигация: пункт несёт свою дельту (navY — int, доступен из другого
        // пакета; в игре тот же apply вызывает navRect).
        int navBase = content.frameY()
                + (content.frameHeight() < 240 ? 54 : content.frameHeight() < 300 ? 82 : 96);
        require(content.navY(0) == navBase, "nav rail sits below the title bar");
        UiEditLayout.overridesFor("CityPylonWorkspace")
                .putElement("nav[0]", new UiEditModel.WidgetOverride(0, 4, 0, 0, null));
        require(content.navY(0) == navBase + 4, "nav item delta applied");
        require(UiEditLayout.registeredNames("CityPylonWorkspace").contains("nav[0]"),
                "nav item registered");

        UiEditLayout.reload("CityPylonWorkspace");
        UiEditLayout.activateForTests(null);
        UiEditLayout.installForTests(new UiEditStore(root), () -> true);
        Files.deleteIfExists(root);
    }

    private UiEditCheck() {
    }
}
