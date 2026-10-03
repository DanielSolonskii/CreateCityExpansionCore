package com.example.examplemod.client.screen;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Headless-проверки UI-контракта магнитного анализатора.
 *
 * Minecraft не запускается, поэтому проверяется то, что
 * реально можно проверить без клиента:
 *
 *  - наличие и непустота каждого lang-ключа, который экран
 *    выводит, в обоих языках;
 *  - структурное отсутствие вывода сырых ключей;
 *  - полнота покрытия трёх режимов анализа;
 *  - геометрия компоновки на разных размерах окна.
 *
 * Чего чек НЕ делает: он не рендерит текст. В headless-окружении
 * Minecraft не загружает Language, и Component.translatable()
 * возвращает сам ключ, поэтому «посмотреть на строку» здесь
 * невозможно. Поэтому факт перевода доказывается по lang-файлам,
 * а факт использования перевода — по исходнику экрана.
 */
public final class MagneticAnalyzerUiCheck {

    private static final String PREFIX =
            "gui.createcityexpansioncore.magnetic_analyzer.";

    /** Все суффиксы ключей, которые экран выводит. */
    private static final List<String> RENDERED_SUFFIXES = List.of(
            "title", "subtitle", "sector", "signal", "uses",
            "complete", "scanning", "reading",
            "mode.inside", "mode.outside", "mode.none",
            "resource", "direction", "distance", "size",
            "local_potential", "peak_potential", "remaining",
            "lifetime", "lifetime_hours",
            "no_anomaly", "no_anomaly_hint",
            "no_core_hint", "search_hint",
            "direction.east", "direction.east_northeast",
            "direction.north", "direction.north_northwest",
            "direction.west", "direction.west_southwest",
            "direction.south", "direction.south_southeast",
            "direction.unknown",
            "distance.very_close", "distance.close",
            "distance.medium", "distance.far",
            "size_class.small", "size_class.normal",
            "size_class.large", "size_class.giant"
    );

    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws IOException {
        everyRenderedKeyExistsInBothLanguages();
        everyRenderedKeyHasSubstantext();
        screenNeverPrintsRawKeys();
        allThreeModesAreCovered();
        fallbackNeverLeaksAKey();
        layoutIsSelfConsistent();

        System.out.println("PASS: " + checks + " magnetic analyzer UI checks");
    }

    /**
     * Каждый ключ, который экран может вывести, обязан
     * существовать и в EN, и в RU.
     *
     * Отсутствие ключа означало бы текст вида
     * "gui.createcityexpansioncore...." — ровно тот дефект,
     * который был виден в runtime.
     */
    private static void everyRenderedKeyExistsInBothLanguages() throws IOException {
        Map<String, String> en = lang("en_us.json");
        Map<String, String> ru = lang("ru_ru.json");

        require(!RENDERED_SUFFIXES.isEmpty(),
                "the check actually enumerates keys");
        require(en.size() > 1000, "en lang file parsed (" + en.size() + " keys)");
        require(ru.size() > 1000, "ru lang file parsed (" + ru.size() + " keys)");

        for (String suffix : RENDERED_SUFFIXES) {
            String key = PREFIX + suffix;
            require(en.containsKey(key), "en_us.json is missing " + key);
            require(ru.containsKey(key), "ru_ru.json is missing " + key);
        }

        /*
         * Режимы — самые заметные подписи, они обязаны
         * различаться в каждом языке.
         */
        require(!en.get(PREFIX + "mode.none").equals(en.get(PREFIX + "mode.outside")),
                "EN NO_ANOMALY and NEAREST labels differ");
        require(!en.get(PREFIX + "mode.outside").equals(en.get(PREFIX + "mode.inside")),
                "EN NEAREST and INSIDE labels differ");
        require(!ru.get(PREFIX + "mode.none").equals(ru.get(PREFIX + "mode.outside")),
                "RU NO_ANOMALY and NEAREST labels differ");
        require(!ru.get(PREFIX + "mode.outside").equals(ru.get(PREFIX + "mode.inside")),
                "RU NEAREST and INSIDE labels differ");
    }

    /**
     * Значение ключа не должно совпадать с самим ключом и не
     * должно быть пустым.
     */
    private static void everyRenderedKeyHasSubstantext() throws IOException {
        for (String fileName : List.of("en_us.json", "ru_ru.json")) {
            Map<String, String> values = lang(fileName);
            for (String suffix : RENDERED_SUFFIXES) {
                String key = PREFIX + suffix;
                String value = values.get(key);
                require(value != null && !value.isBlank(),
                        fileName + " has no text for " + key);
                require(!value.equals(key),
                        fileName + " value for " + key + " echoes the key");
                require(!value.contains("createcityexpansioncore"),
                        fileName + " value for " + key + " leaks the namespace");
            }
        }
    }

    /**
     * Структурная проверка исходника экрана: сырые ключи не
     * должны попадать в drawString.
     *
     * Старый экран делал ровно это:
     *   graphics.drawString(font, modeKey(), ...)
     * где modeKey() возвращала строку-ключ. Теперь любой
     * текст обязан идти через tr(), который возвращает уже
     * переводённую строку.
     */
    private static void screenNeverPrintsRawKeys() throws IOException {
        String source = readScreenSource();

        require(source.contains("private static String tr("),
                "screen has a single translation entry point tr()");

        /*
         * Запрещённая форма: drawString получает вызов,
         * возвращающий ключ, напрямую.
         */
        require(!source.contains("drawString(font, modeKey()"),
                "screen must not print modeKey() directly");
        require(!source.contains("drawString(font, resourceName(),"),
                "screen must not print an untranslated mode/key string directly");
        require(!source.contains("drawString(font, key"),
                "screen must not print a raw key variable");

        /*
         * Все три режима обязаны проходить через tr():
         * иначе на экране снова появится сырой ключ.
         */
        for (String mode : List.of("mode.outside", "mode.inside", "mode.none")) {
            require(source.contains("\"" + mode + "\""),
                    "mode " + mode + " is handled by the screen");
            require(source.contains("tr(modeKey())"),
                    "mode label is rendered through tr()");
        }

        /*
         * Fallback обязателен: если ключ всё же исчезнет,
         * на экране будет короткая подпись, а не ключ.
         */
        require(source.contains("fallbackFor("),
                "translation has a human-readable fallback");
    }

    /**
     * Все три режима покрыты в правой панели: у каждого есть
     * свой набор строк данных.
     */
    private static void allThreeModesAreCovered() throws IOException {
        String source = readScreenSource();

        for (String method : List.of(
                "drawOutsideData", "drawInsideData"
        )) {
            require(source.contains("private void " + method),
                    "screen has a dedicated renderer for " + method);
        }

        require(source.contains("case NO_ANOMALY ->"),
                "NO_ANOMALY has its own branch");
        require(source.contains("case OUTSIDE -> drawOutsideData"),
                "OUTSIDE routes to drawOutsideData");
        require(source.contains("case INSIDE -> drawInsideData"),
                "INSIDE routes to drawInsideData");

        Map<String, String> en = lang("en_us.json");
        for (String suffix : List.of(
                "resource", "direction", "distance", "size",
                "local_potential", "peak_potential", "remaining", "lifetime"
        )) {
            require(!en.get(PREFIX + suffix).isBlank(),
                    "data row label " + suffix + " has text");
        }
    }

    /**
     * Заглушка перевода обязана быть короткой
     * человекочитаемой строкой, а не ключом.
     */
    private static void fallbackNeverLeaksAKey() {
        for (String suffix : List.of(
                "inside", "outside", "none", "not_a_real_key_xyz"
        )) {
            String fallback = MagneticAnalyzerScreen.fallbackForTest(suffix);
            require(!fallback.startsWith(PREFIX),
                    "fallback for " + suffix + " does not leak a key");
            require(!fallback.contains("gui."),
                    "fallback for " + suffix + " contains no dotted key");
            require(!fallback.contains("createcityexpansioncore"),
                    "fallback for " + suffix + " leaks no namespace");
            require(fallback.length() <= 24,
                    "fallback for " + suffix + " stays short (got " + fallback + ")");
        }
    }

    /**
     * Геометрия компоновки не должна вырождаться ни на одном
     * поддерживаемом размере окна.
     */
    private static void layoutIsSelfConsistent() {
        int[][] sizes = {
                {1920, 1080}, {1600, 900}, {1280, 720},
                {1024, 640}, {854, 480}, {640, 360},
                {480, 270}, {360, 240}, {320, 240}
        };

        for (int[] size : sizes) {
            String at = " at " + size[0] + "x" + size[1];
            MagneticAnalyzerScreen.LayoutResult layout =
                    MagneticAnalyzerScreen.LayoutResult.of(size[0], size[1]);

            require(layout.locatorWidth() > 0, "locator width is positive" + at);
            require(layout.locatorHeight() > 0, "locator height is positive" + at);
            require(layout.locatorWidth() == layout.locatorHeight(),
                    "locator stays square" + at + " (got "
                            + layout.locatorWidth() + "x" + layout.locatorHeight() + ")");
            require(layout.dataWidth() > 0, "data panel width is positive" + at);
            require(layout.dataHeight() > 0, "data panel height is positive" + at);

            require(layout.dataLeft() >= layout.locatorRight(),
                    "data panel does not overlap the locator" + at);
            require(layout.dataRight() <= layout.right(),
                    "data panel stays inside the shell" + at);
            require(layout.dataLeft() > layout.left(),
                    "data panel stays inside the shell on the left" + at);
            require(layout.locatorLeft() >= layout.left(),
                    "locator stays inside the shell" + at);
            require(layout.locatorBottom() <= layout.bottom(),
                    "locator stays inside the shell vertically" + at);

            require(layout.headerBottom() <= layout.bodyTop() + 1,
                    "header sits above the body" + at);
            require(layout.footerTop() > layout.bodyTop(),
                    "footer sits below the body" + at);
            require(layout.left() >= 0 && layout.top() >= 0,
                    "layout is on screen" + at);
            require(layout.right() <= size[0] && layout.bottom() <= size[1],
                    "layout fits the screen" + at);
        }

        /*
         * На большом разрешении прибор не растягивается
         * бесконечно.
         */
        MagneticAnalyzerScreen.LayoutResult large =
                MagneticAnalyzerScreen.LayoutResult.of(2560, 1440);
        require(large.right() - large.left() <= 520,
                "panel width is capped (got " + (large.right() - large.left()) + ")");
        require(large.bottom() - large.top() <= 300,
                "panel height is capped (got " + (large.bottom() - large.top()) + ")");

        /*
         * Компоновка детерминирована: один и тот же размер
         * окна всегда даёт одну и ту же геометрию.
         */
        MagneticAnalyzerScreen.LayoutResult first =
                MagneticAnalyzerScreen.LayoutResult.of(1280, 720);
        MagneticAnalyzerScreen.LayoutResult second =
                MagneticAnalyzerScreen.LayoutResult.of(1280, 720);
        require(first.equals(second),
                "layout is deterministic for the same screen size");
    }

    /* ---------------------------------------------------------------
     *  ЧТЕНИЕ РЕСУРСОВ
     * --------------------------------------------------------------- */

    /**
     * Разбирает lang-файл в карту ключ -> значение.
     *
     * Свой мини-парсер, а не com.google.gson: проверка должна
     * работать на classpath, где gson может быть недоступен
     * как прямой зависимость.
     */
    private static Map<String, String> lang(String fileName) throws IOException {
        String json = readResource(
                "assets/createcityexpansioncore/lang/" + fileName
        );
        Map<String, String> values = new LinkedHashMap<>();

        int index = 0;
        while (true) {
            int keyStart = json.indexOf('"', index);
            if (keyStart < 0) {
                break;
            }
            int keyEnd = keyEnd(json, keyStart);
            if (keyEnd < 0) {
                break;
            }
            String key = unescape(json.substring(keyStart + 1, keyEnd));

            int colon = json.indexOf(':', keyEnd + 1);
            if (colon < 0) {
                break;
            }
            int valueStart = json.indexOf('"', colon);
            if (valueStart < 0) {
                break;
            }
            int valueEnd = keyEnd(json, valueStart);
            if (valueEnd < 0) {
                break;
            }
            String value = unescape(json.substring(valueStart + 1, valueEnd));
            values.put(key, value);
            index = valueEnd + 1;
        }
        return values;
    }

    private static int keyEnd(String json, int startQuote) {
        int cursor = startQuote + 1;
        while (cursor < json.length()) {
            char c = json.charAt(cursor);
            if (c == '\\') {
                cursor += 2;
                continue;
            }
            if (c == '"') {
                return cursor;
            }
            cursor++;
        }
        return -1;
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n");
    }

    private static String readResource(String path) throws IOException {
        try (InputStream stream =
                     MagneticAnalyzerUiCheck.class.getClassLoader()
                             .getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("resource not found: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Исходник экрана для структурной проверки.
     *
     * Читается с диска, а не из classpath: в байт-коде строковые
     * литералы лежат в отдельном пуле констант, и проверка
     * «как именно вызывается drawString» по ним невозможна.
     */
    private static String readScreenSource() throws IOException {
        Path source = Path.of(
                "src", "main", "java", "com", "example", "examplemod",
                "client", "screen", "MagneticAnalyzerScreen.java"
        );
        require(Files.exists(source),
                "screen source is available for the structural check (" + source + ")");
        return Files.readString(source, StandardCharsets.UTF_8);
    }

    private MagneticAnalyzerUiCheck() {
    }
}