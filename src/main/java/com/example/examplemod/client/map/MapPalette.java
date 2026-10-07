package com.example.examplemod.client.map;

/**
 * Единая палитра и шкала интервалов карты.
 *
 * <p>Раньше цвета и отступы карты жили по месту: рамка прибора,
 * цвета территорий, сетка, легенда и маркеры задавали числа
 * независимо друг от друга. Из-за этого один и тот же тон
 * назывался в трёх местах и в двух из них отличался на
 * единицу, а вертикальные отступы панелей не складывались.
 *
 * <p>Теперь это единственный источник. Всё чистое и
 * проверяется headless: ни графики, ни Minecraft.
 *
 * <p>Стилистика — «инженерный чертёж». Местность становится
 * бумагой, инфраструктура — чернилами, родной город — медью.
 * Приём не декоративный: обесцвеченная подложка перестаёт
 * конкурировать с линиями и знаками, поэтому границы
 * территорий и маршруты читаются на любом зуме, включая
 * отдалённый, где раньше всё сливалось в пятна.
 */
public final class MapPalette {

    private MapPalette() {
    }

    /* ------------------------------------------------------------- интервалы */

    /** Шкала отступов: микро / базовый / секция. */
    public static final int MICRO = 4;
    public static final int NORMAL = 8;
    public static final int SECTION = 12;

    /* ------------------------------------------------------------- бумага и чернила */

    /** Подложка корпуса прибора. */
    public static final int PAPER = 0xFF181E20;
    /** Верхний слой корпуса прибора. */
    public static final int PAPER_RAISED = 0xFF222A2C;
    /** Углублённая секция. */
    public static final int PAPER_SUNK = 0xFF12191B;
    /** Линия чертежа. */
    public static final int INK = 0xFF2C3133;
    /** Тонкая вспомогательная линия. */
    public static final int INK_SOFT = 0xFF394043;
    /** Сетка координат. */
    public static final int INK_GRID = 0x107FA0A8;

    /* ------------------------------------------------------------- текст */

    public static final int TEXT = 0xFFE7E8DB;
    public static final int TEXT_MUTED = 0xFFA6B3AD;
    public static final int TEXT_FAINT = 0xFF69716E;

    /* ------------------------------------------------------------- акценты */

    /** Родной город и его инфраструктура. */
    public static final int OWN = 0xFFD3A363;
    /** Нейтральная инфраструктура. */
    public static final int NEUTRAL = 0xFF6FA7A9;
    /** Ошибка и предупреждение. */
    public static final int ALERT = 0xFFD2705F;
    /** Хорошее состояние. */
    public static final int GOOD = 0xFF8FBF6A;
    /** Выделение выбранного объекта. */
    public static final int SELECTED = 0xFFFFD58A;

    /* ------------------------------------------------------------- территория */

    /**
     * Плотность заливки территории.
     *
     * <p>Сознательно меньше, чем раньше: заливка должна
     * обозначать границу, а не закрывать местность под собой.
     * Читаемость границы несёт контур, а не заливка.
     */
    public static final int OWN_FILL_ALPHA = 0x1E;
    public static final int PEER_FILL_ALPHA = 0x14;

    /* ------------------------------------------------------------- местность */

    /**
     * Насколько местность обесцвечивается в бумагу.
     *
     * <p>0 — исходный цвет блока, 1 — чистая бумага.
     * Значение намеренно оставлено неполным: полное
     * обесцвечивание убивает различие «вода / трава / камень»,
     * а именно оно объясняет игроку, где проходить рельс.
     */
    public static final double SURVEY_DESATURATION = 0.52;

    /**
     * Контраст светлоты после обесцвечивания.
     *
     * <p>Поднимает различимость рельефа: у чертёжного листа
     * светлота должна нести форму, а у фотографии — материал.
     */
    public static final double SURVEY_CONTRAST = 1.22;

    /** Куда уходят полутона: холодный графит. */
    public static final int SURVEY_SHADOW_TINT = 0xFF2E3A40;
    /** Куда уходят света: тёплая бумага. */
    public static final int SURVEY_HIGHLIGHT_TINT = 0xFFF2ECDC;

    /* ------------------------------------------------------------- инструмент */

    /**
     * Утилиты смешивания.
     *
     * <p>Все шаги идут через них, поэтому ни один этап
     * обработки цвета не может «перескочить» оттенок: между
     * соседними точками разница всегда считается одним и тем же
     * способом.
     */
    public static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    public static int clamp(double value) {
        return value < 0 ? 0 : value > 255 ? 255 : (int) Math.round(value);
    }

    public static int mix(int from, int to, double amount) {
        double clamped = amount < 0 ? 0 : amount > 1 ? 1 : amount;
        return clamp((int) Math.round(from + (to - from) * clamped));
    }

    public static int argb(int rgb, int alpha) {
        return (clamp(alpha) << 24) | (rgb & 0xFFFFFF);
    }

    public static int alpha(int color) {
        return color >>> 24 & 0xFF;
    }

    public static int red(int color) {
        return color >> 16 & 0xFF;
    }

    public static int green(int color) {
        return color >> 8 & 0xFF;
    }

    public static int blue(int color) {
        return color & 0xFF;
    }

    /** Смешивает два цвета попиксельно, сохраняя непрозрачность результата. */
    public static int overlay(int top, int bottom) {
        double a = alpha(top) / 255.0;
        return argb(
                mix(red(bottom), red(top), a) << 16
                        | mix(green(bottom), green(top), a) << 8
                        | mix(blue(bottom), blue(top), a),
                0xFF
        );
    }

    /** Воспринимаемая светлота в шкале 0..255. */
    public static int luminance(int color) {
        return (red(color) * 30 + green(color) * 59 + blue(color) * 11) / 100;
    }
}
