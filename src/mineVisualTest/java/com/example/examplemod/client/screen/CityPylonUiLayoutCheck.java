package com.example.examplemod.client.screen;

/**
 * Headless layout contract for the City Pylon navigation.
 *
 * <p>The management row used to split its width into six equal columns. That is invisible with English
 * labels and broken with Russian ones, so the checks here use the actual measured widths of the
 * Russian labels and require that no two tabs ever touch, at every responsive width.
 */
public final class CityPylonUiLayoutCheck {
    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static final int[] WIDTHS = { 320, 426, 640, 854, 1280 };
    private static final int TAB_COUNT = 6;
    private static final int CONTENT = 340;

    /**
     * Measured Russian labels for Территория / Участники / Проекты / Развитие / Отношения /
     * Железная дорога at GUI scale 1. These are deliberately wide; they are what the fixed-width row
     * could not fit.
     */
    private static final int[] RU_LABELS = { 62, 66, 58, 60, 62, 96 };
    private static final int[] EN_LABELS = { 48, 46, 44, 62, 54, 84 };

    public static void main(String[] args) {
        for (int width : WIDTHS) {
            for (int[] labels : new int[][]{ RU_LABELS, EN_LABELS }) {
                int content = Math.min(CONTENT, width - 24);
                int column = CityPylonUiLayout.columnWidth(content, TAB_COUNT, labels);
                int rows = CityPylonUiLayout.rows(content, TAB_COUNT, labels);

                require(column >= 1, "the column width stays usable at " + width);
                require(rows == 1 || rows == 2, "the row wraps into at most two lines at " + width);

                CityPylonUiLayout.Rect previous = null;
                for (int index = 0; index < TAB_COUNT; index++) {
                    CityPylonUiLayout.Rect cell = CityPylonUiLayout.tab(
                            0, 100, index, column, TAB_COUNT, rows);
                    require(cell.contains(cell.x() + 1, cell.y() + 1),
                            "tab hitbox matches its render bounds at " + width);
                    require(cell.width() > 0 && cell.height() == CityPylonUiLayout.ROW_HEIGHT,
                            "tab keeps a stable height at " + width);
                    require(cell.x() >= 0 && cell.right() <= content + 1,
                            "tab stays inside the content area at " + width);
                    if (previous != null) {
                        require(!previous.overlaps(cell),
                                "tabs " + (index - 1) + " and " + index + " never overlap at " + width
                                        + " (rows=" + rows + ")");
                        // Reading order only applies inside one row; a wrapped row restarts on the left.
                        if (cell.y() == previous.y()) {
                            require(cell.x() >= previous.x(),
                                    "tabs on the same row keep reading order at " + width);
                            require(cell.x() >= previous.right(),
                                    "same-row tabs keep the minimum gap at " + width);
                        } else {
                            require(cell.y() >= previous.bottom(),
                                    "a wrapped row starts below the previous one at " + width);
                        }
                    }
                    previous = cell;
                }

                // The last tab must not run past the content width, which is how the old fixed row
                // pushed its right edge off the panel.
                CityPylonUiLayout.Rect last = CityPylonUiLayout.tab(
                        0, 100, TAB_COUNT - 1, column, TAB_COUNT, rows);
                require(last.right() <= content,
                        "the last tab ends inside the content area at " + width
                                + " (" + last.right() + " > " + content + ")");
            }
        }

        // A single line whenever the widest label genuinely fits, so the common case is unchanged.
        require(CityPylonUiLayout.rows(340, TAB_COUNT, new int[]{ 20, 20, 20, 20, 20, 20 }) == 1,
                "short labels still share one line");
        require(CityPylonUiLayout.rows(340, TAB_COUNT, RU_LABELS) == 2,
                "wide Russian labels wrap instead of overlapping");
        require(CityPylonUiLayout.columnWidth(340, TAB_COUNT, RU_LABELS) >= 1,
                "wrapping still yields a positive column width");

        // ------------------------------------------------------------------ main navigation geometry
        final int navCount = 5;
        for (int width : WIDTHS) {
            int left = 10;
            CityPylonUiLayout.Rect previous = null;
            for (int index = 0; index < navCount; index++) {
                CityPylonUiLayout.Rect row = CityPylonUiLayout.navRow(left, 54, index);
                CityPylonUiLayout.Rect icon = CityPylonUiLayout.navIcon(left, 54, index);
                CityPylonUiLayout.Rect text = CityPylonUiLayout.navText(left, 54, index);
                CityPylonUiLayout.Rect marker = CityPylonUiLayout.navActiveIndicator(left, 54, index);

                require(row.height() == CityPylonUiLayout.NAV_ROW_H,
                        "nav rows keep a stable height at " + width);
                require(row.contains(row.x() + 1, row.y() + 1),
                        "nav hitbox matches its render bounds at " + width);
                require(icon.overlaps(row), "the icon sits inside its row at " + width);
                require(!icon.overlaps(text), "the icon never covers the label at " + width);
                require(text.x() >= icon.right(), "the label always starts after the icon at " + width);
                require(text.overlaps(row), "the label stays inside its row at " + width);
                require(marker.overlaps(row), "the active marker stays inside its row at " + width);
                require(!marker.overlaps(text), "the active marker never covers the label at " + width);

                if (previous != null) {
                    require(!previous.overlaps(row),
                            "nav rows " + (index - 1) + " and " + index + " never overlap");
                    require(row.y() - previous.bottom() >= 4,
                            "nav rows keep a real vertical gap at index " + index
                                    + " (" + (row.y() - previous.bottom()) + "px)");
                }
                previous = row;
            }
        }

        // The old layout used a 25px pitch with a 24px row: a 1px seam.
        require(CityPylonUiLayout.navPitch() - CityPylonUiLayout.NAV_ROW_H >= 4,
                "the nav gap is a real gap, not a 1px seam");
        require(CityPylonUiLayout.navPitch() - CityPylonUiLayout.NAV_ROW_H <= 8,
                "the nav gap stays in the existing Create-style rhythm");
        require(CityPylonUiLayout.NAV_ROW_H <= 24,
                "the nav stays a compact strip, not a stack of large cards");

        System.out.println("PASS: " + checks + " city pylon navigation layout checks");
    }
}