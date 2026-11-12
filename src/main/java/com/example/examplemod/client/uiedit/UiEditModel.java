package com.example.examplemod.client.uiedit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Данные правок интерфейса: смещения, размеры и тексты виджетов одного экрана.
 *
 * <p>Класс намеренно не зависит от Minecraft — на нём строится headless-проверка
 * {@code UiEditCheck}, которая гоняет модель, JSON и математику дельт без запуска игры.
 *
 * <p><b>Почему дельты, а не абсолютные координаты.</b> Экраны мода пересчитывают
 * раскладку под размер окна и GUI scale ({@code Layout.of(width, height)}). Если
 * хранить абсолютные x/y, сохранённая правка «уедет» при другом разрешении. Дельта
 * от исходной позиции переживает любую перекладку: сдвиг остаётся тем же,
 * а база каждый раз берётся из свежего {@code init()}.
 *
 * <p><b>Почему ключ — индекс плюс имя класса.</b> Виджеты создаются анонимно и
 * стабильного id у них нет. Индекс в {@code children()} + имя класса дают ключ,
 * который сам себя защищает: сдвинулась структура экрана — имя под индексом
 * не совпало и правка молча не применяется, вместо сдвига чужой кнопки.
 */
public final class UiEditModel {

    /** Версия формата JSON-файла. Несовместимая смена — только с новым номером. */
    public static final int VERSION = 1;

    /** Минимальный размер виджета после правки — нулевой кнопки не бывает. */
    public static final int MIN_SIZE = 8;

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private UiEditModel() {
    }

    /** Прямоугольник в координатах GUI (после масштабирования). */
    public record Bounds(int x, int y, int w, int h) {

        public int right() {
            return x + w;
        }

        public int bottom() {
            return y + h;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    /**
     * Правка одного виджета: дельты к исходной геометрии и, если менялся,
     * переопределённый текст.
     */
    public record WidgetOverride(int dx, int dy, int dw, int dh, String text) {

        public boolean hasText() {
            return text != null;
        }

        /** Пустая правка — её не нужно хранить в JSON. */
        public boolean isIdentity() {
            return dx == 0 && dy == 0 && dw == 0 && dh == 0 && text == null;
        }

        /** Границы, полученные из исходных. */
        public Bounds applyTo(Bounds original) {
            return new Bounds(
                    original.x() + dx,
                    original.y() + dy,
                    Math.max(MIN_SIZE, original.w() + dw),
                    Math.max(MIN_SIZE, original.h() + dh));
        }
    }

    /** Исходное состояние виджета до правок: геометрия и исходный текст. */
    public record Original(Bounds bounds, String text) {
    }

    /** Ключ виджета внутри экрана: {@code индекс:ИмяКласса}. */
    public static String widgetKey(int index, String className) {
        return index + ":" + className;
    }

    /** Индекс из ключа; {@code -1}, если ключ битый. */
    public static int keyIndex(String key) {
        int colon = key.indexOf(':');
        if (colon <= 0) {
            return -1;
        }
        try {
            return Integer.parseInt(key.substring(0, colon));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Имя класса из ключа; пустая строка, если ключ битый. */
    public static String keyClass(String key) {
        int colon = key.indexOf(':');
        return colon < 0 ? "" : key.substring(colon + 1);
    }

    /**
     * Правка относится к этому виджету? Ключ должен совпасть и по индексу,
     * и по классу — защита от сдвига структуры экрана.
     */
    public static boolean keyMatches(String key, int index, String className) {
        return keyIndex(key) == index && keyClass(key).equals(className);
    }

    /** Дельта «было → стало». Совпавший текст остаётся {@code null} — не храним лишнего. */
    public static WidgetOverride delta(Bounds original, Bounds current, String originalText, String currentText) {
        String text = currentText == null || currentText.equals(originalText) ? null : currentText;
        return new WidgetOverride(
                current.x() - original.x(),
                current.y() - original.y(),
                current.w() - original.w(),
                current.h() - original.h(),
                text);
    }

    /** Все правки одного экрана. Эквивалент одному JSON-файлу. */
    public static final class ScreenOverrides {

        private final String screen;
        private final LinkedHashMap<String, WidgetOverride> widgets = new LinkedHashMap<>();
        /** Именованные элементы layout-классов (ручные экраны): имя → дельта. */
        private final LinkedHashMap<String, WidgetOverride> elements = new LinkedHashMap<>();
        private boolean broken;

        public ScreenOverrides(String screen) {
            this.screen = screen;
        }

        public String screen() {
            return screen;
        }

        /** Правки в порядке чтения файла; ключ — {@link #widgetKey(int, String)}. */
        public Map<String, WidgetOverride> widgets() {
            return Collections.unmodifiableMap(widgets);
        }

        /** Файл экрана не удалось разобрать: сохранять поверх него нельзя молча. */
        public boolean broken() {
            return broken;
        }

        public void markBroken() {
            broken = true;
        }

        public WidgetOverride get(String key) {
            return widgets.get(key);
        }

        /** Пустая правка удаляется — файл не засоряется нулями. */
        public void put(String key, WidgetOverride override) {
            if (keyIndex(key) < 0) {
                return;
            }
            if (override == null || override.isIdentity()) {
                widgets.remove(key);
            } else {
                widgets.put(key, override);
            }
        }

        public void remove(String key) {
            widgets.remove(key);
        }

        /** Именованные элементы layout (фаза 2): видят оверлей и хуки layout-классов. */
        public Map<String, WidgetOverride> elements() {
            return Collections.unmodifiableMap(elements);
        }

        public WidgetOverride getElement(String name) {
            return name == null ? null : elements.get(name);
        }

        /** Пустая правка удаляется; пустое имя игнорируется. */
        public void putElement(String name, WidgetOverride override) {
            if (name == null || name.isBlank()) {
                return;
            }
            if (override == null || override.isIdentity()) {
                elements.remove(name);
            } else {
                elements.put(name, override);
            }
        }

        public void removeElement(String name) {
            if (name != null) {
                elements.remove(name);
            }
        }

        public void clear() {
            widgets.clear();
            elements.clear();
        }

        public boolean isEmpty() {
            return widgets.isEmpty() && elements.isEmpty();
        }

        public int size() {
            return widgets.size();
        }

        public String toJson() {
            JsonObject root = new JsonObject();
            root.addProperty("version", VERSION);
            root.addProperty("screen", screen);
            root.add("widgets", overridesToJson(widgets));
            if (!elements.isEmpty()) {
                root.add("elements", overridesToJson(elements));
            }
            return GSON.toJson(root);
        }

        private static JsonObject overridesToJson(Map<String, WidgetOverride> source) {
            JsonObject body = new JsonObject();
            for (Map.Entry<String, WidgetOverride> entry : source.entrySet()) {
                WidgetOverride override = entry.getValue();
                JsonObject one = new JsonObject();
                one.addProperty("dx", override.dx());
                one.addProperty("dy", override.dy());
                one.addProperty("dw", override.dw());
                one.addProperty("dh", override.dh());
                if (override.hasText()) {
                    one.addProperty("text", override.text());
                }
                body.add(entry.getKey(), one);
            }
            return body;
        }

        /**
         * Разбор JSON без исключений наружу. Битый файл даёт пустые правки
         * с {@link #broken()} — старые данные не накладываются на новые молча,
         * а сохранение поверх битого файла блокируется вызывающим кодом.
         */
        public static ScreenOverrides fromJson(String screen, String json) {
            ScreenOverrides result = new ScreenOverrides(screen);
            if (json == null || json.isBlank()) {
                return result;
            }
            try {
                JsonElement parsed = JsonParser.parseString(json);
                if (!parsed.isJsonObject()) {
                    result.broken = true;
                    return result;
                }
                JsonObject rootObject = parsed.getAsJsonObject();
                JsonElement body = rootObject.get("widgets");
                if (body != null) {
                    if (!body.isJsonObject()) {
                        result.broken = true;
                        return result;
                    }
                    parseOverrides(result, body.getAsJsonObject(), false);
                }
                JsonElement elementsNode = rootObject.get("elements");
                if (elementsNode != null) {
                    if (!elementsNode.isJsonObject()) {
                        result.broken = true;
                        return result;
                    }
                    parseOverrides(result, elementsNode.getAsJsonObject(), true);
                }
            } catch (RuntimeException e) {
                result.broken = true;
            }
            return result;
        }

        /**
         * Разбор одной карты правок. Виджеты ключуются {@code индекс:Класс}
         * ({@link #keyIndex}, битые ключи пропускаются), элементы layout —
         * простым непустым именем.
         */
        private static void parseOverrides(ScreenOverrides result, JsonObject body, boolean elementKeys) {
            for (Map.Entry<String, JsonElement> entry : body.entrySet()) {
                String key = entry.getKey();
                if (key.isBlank() || !entry.getValue().isJsonObject()) {
                    continue;
                }
                if (!elementKeys && keyIndex(key) < 0) {
                    continue;
                }
                JsonObject one = entry.getValue().getAsJsonObject();
                WidgetOverride override = new WidgetOverride(
                        intField(one, "dx"),
                        intField(one, "dy"),
                        intField(one, "dw"),
                        intField(one, "dh"),
                        stringField(one, "text"));
                if (override.isIdentity()) {
                    continue;
                }
                if (elementKeys) {
                    result.elements.put(key, override);
                } else {
                    result.widgets.put(key, override);
                }
            }
        }

        private static int intField(JsonObject one, String name) {
            JsonElement value = one.get(name);
            if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                return 0;
            }
            return value.getAsInt();
        }

        private static String stringField(JsonObject one, String name) {
            JsonElement value = one.get(name);
            if (value == null || !value.isJsonPrimitive()) {
                return null;
            }
            return value.getAsString();
        }
    }
}
