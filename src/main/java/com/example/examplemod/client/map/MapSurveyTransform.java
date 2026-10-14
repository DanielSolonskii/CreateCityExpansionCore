package com.example.examplemod.client.map;

/** Natural terrain colour and optional legacy contour treatment. UI chrome is styled separately. */
final class MapSurveyTransform {

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

        return argb;
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
     * Gentle close-range relief, preserving the underlying material. Far LOD omits contours in
     * TerrainTileTexture instead of covering entire averaged cells with opaque dark ink.
     */
    static int surveyInk(int paper) {
        int alpha = MapPalette.alpha(paper);
        return MapPalette.argb(contourChannel(MapPalette.red(paper),MapPalette.red(MapPalette.INK))<<16
                |contourChannel(MapPalette.green(paper),MapPalette.green(MapPalette.INK))<<8
                |contourChannel(MapPalette.blue(paper),MapPalette.blue(MapPalette.INK)),alpha);
    }
    private static int contourChannel(int source,int ink) {
        return MapPalette.mix(source,Math.min(ink,source/2),.18);
    }
}
