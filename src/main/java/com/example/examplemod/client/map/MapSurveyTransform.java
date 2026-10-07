package com.example.examplemod.client.map;

/**
 * Перевод цвета местности в «чертёжный» вид.
 *
 * <p>Карта должна читаться как генплан, а не как фотография
 * сверху: линии территорий, маршрутов и знаки важнее
 * оттенка блока под ними. Преобразование поэтому не «стилизует»
 * картинку, а убирает из неё то, что с чертёжным листом
 * конкурирует, — и усиливает то, что помогает.
 *
 * <p>Порядок шагов фиксирован и каждый шаг линейный, поэтому
 * соседние сэмплы не могут разойтись по тону: прыжок цвета
 * на границе биома остаётся прыжком, просто меньшей величины.
 *
 * <p>Класс чистый: ни графики, ни Minecraft. Точное
 * поведение проверяется headless на синтетической шкале серого,
 * где ожидаемый результат известен заранее.
 */
final class MapSurveyTransform {

    /**
     * Точка, к которой стягивается цвет.
     *
     * <p>Не нейтральный серый, а холодный графит: в связке с
     * тёплыми светами это даёт разделённый тон, при котором
     * тень читается как тень, а не как «просто темнее».
     */
    private static final int NEUTRAL_ANCHOR = 0xFF6A7276;

    private MapSurveyTransform() {
    }

    /**
     * Один сэмпл местности -> цвет подложки.
     *
     * @param argb исходный цвет {@code 0xAARRGGBB}
     */
    static int survey(int argb) {
        int sourceAlpha = MapPalette.alpha(argb);
        if (sourceAlpha == 0) {
            return argb;
        }

        int red = MapPalette.red(argb);
        int green = MapPalette.green(argb);
        int blue = MapPalette.blue(argb);

        int redNeutral = MapPalette.red(NEUTRAL_ANCHOR);
        int greenNeutral = MapPalette.green(NEUTRAL_ANCHOR);
        int blueNeutral = MapPalette.blue(NEUTRAL_ANCHOR);

        // 1. Обесцвечивание к холодному графиту.
        double desaturation = MapPalette.SURVEY_DESATURATION;
        red = MapPalette.mix(red, redNeutral, desaturation);
        green = MapPalette.mix(green, greenNeutral, desaturation);
        blue = MapPalette.mix(blue, blueNeutral, desaturation);

        // 2. Контраст светлоты вокруг среднего тона.
        int luminance = MapPalette.luminance(
                (red << 16) | (green << 8) | blue
        );
        double contrast = MapPalette.SURVEY_CONTRAST;
        red = MapPalette.clamp(luminance + (red - luminance) * contrast);
        green = MapPalette.clamp(luminance + (green - luminance) * contrast);
        blue = MapPalette.clamp(luminance + (blue - luminance) * contrast);

        // 3. Разделение тона: тени уходят в холодный, света — в тёплый.
        double shade = 1.0 - luminance / 255.0;
        int shadow = MapPalette.mix(red, MapPalette.red(MapPalette.SURVEY_SHADOW_TINT), shade * 0.45);
        int shadowGreen = MapPalette.mix(green, MapPalette.green(MapPalette.SURVEY_SHADOW_TINT), shade * 0.45);
        int shadowBlue = MapPalette.mix(blue, MapPalette.blue(MapPalette.SURVEY_SHADOW_TINT), shade * 0.45);

        double light = luminance / 255.0;
        int highlight = MapPalette.mix(shadow, MapPalette.red(MapPalette.SURVEY_HIGHLIGHT_TINT), light * 0.40);
        int highlightGreen = MapPalette.mix(shadowGreen, MapPalette.green(MapPalette.SURVEY_HIGHLIGHT_TINT), light * 0.40);
        int highlightBlue = MapPalette.mix(shadowBlue, MapPalette.blue(MapPalette.SURVEY_HIGHLIGHT_TINT), light * 0.40);

        return MapPalette.argb(
                (MapPalette.clamp(highlight) << 16)
                        | (MapPalette.clamp(highlightGreen) << 8)
                        | MapPalette.clamp(highlightBlue),
                sourceAlpha
        );
    }

    /**
     * Бумага для ещё не изведанного тайла.
     *
     * <p>Не пустота: незаведённое должно читаться как
     * «здесь ещё не смотрели», а не как «сломалось». Поэтому это
     * тон подложки, а не чёрный.
     */
    static int unexploredPaper(int parity) {
        return parity == 0 ? 0xFF1A1E1F : 0xFF171A1B;
    }

    /**
     * Горизонталь поверх готовой бумаги.
     *
     * <p>Заменяет тон, а не подмешивает к нему. Смешивание дало бы
     * на отдалённом зуме серую кашу, а горизонталь должна
     * оставаться линией при любом увеличении — иначе она
     * исчезает ровно там, где игрок смотрит на рельеф издалека.
     *
     * <p>Тон чернильный и заметно темнее самой бумаги: на
     * светлом листе первая, самая слабая линия — та, которую
     * видно сразу.
     */
    static int surveyInk(int paper) {
        int alpha = MapPalette.alpha(paper);
        return MapPalette.argb(MapPalette.INK, alpha);
    }
}