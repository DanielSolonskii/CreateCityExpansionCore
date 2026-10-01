package com.example.examplemod.client.screen;

/**
 * Pure geometry for the City Pylon navigation.
 *
 * <p>The secondary management row used to divide its width into six equal columns and hope each
 * label fitted. Russian labels do not: "Развитие" and "Железная дорога" are far wider than their
 * English originals, and a fixed column simply truncates or overlaps them. Here the columns are
 * measured instead, and when the measured labels cannot share one line the row wraps into two.
 *
 * <p>Kept free of client state so the headless layout check can prove that no two tabs ever touch,
 * including for the longest Russian labels.
 */
final class CityPylonUiLayout {
    private CityPylonUiLayout() {
    }

    static final int GAP = 4;
    static final int ROW_HEIGHT = 18;
    static final int ROW_GAP = 3;

    // ------------------------------------------------------------------ main navigation
    /** Row height is unchanged; only the gap grows, so nothing else in the panel moves. */
    static final int NAV_ROW_H = 24;
    /** The rows used to sit 25px apart with a 24px button, which left a 1px seam. */
    static final int NAV_GAP = 5;
    static final int NAV_WIDTH = 150;
    static final int NAV_ICON = 16;
    static final int NAV_ICON_PAD = 5;
    static final int NAV_INDICATOR = 3;

    /** Row-to-row pitch. Row bounds and hitboxes both come from here. */
    static int navPitch() { return NAV_ROW_H + NAV_GAP; }

    static Rect navRow(int left, int top, int index) {
        return new Rect(left, top + index * navPitch(), NAV_WIDTH, NAV_ROW_H);
    }

    /** The icon never leaves its row and never touches the text. */
    static Rect navIcon(int left, int top, int index) {
        Rect row = navRow(left, top, index);
        return new Rect(row.x() + NAV_ICON_PAD, row.y() + (row.height() - NAV_ICON) / 2, NAV_ICON, NAV_ICON);
    }

    /** Text starts after the icon and runs to the row's right margin. */
    static Rect navText(int left, int top, int index) {
        Rect row = navRow(left, top, index);
        int x = row.x() + NAV_ICON_PAD + NAV_ICON + 4;
        return new Rect(x, row.y() + 6, Math.max(1, row.right() - x - 6), 12);
    }

    /** Quiet Create-style accent bar marking the active section; no glow, no heavy fill. */
    static Rect navActiveIndicator(int left, int top, int index) {
        Rect row = navRow(left, top, index);
        return new Rect(row.x() + 1, row.y() + 2, NAV_INDICATOR, row.height() - 4);
    }

    record Rect(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
        boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
        boolean overlaps(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }
    }

    /**
     * Splits {@code availableWidth} between {@code count} tabs, giving every tab at least the width
     * its own label needs and handing any leftover space to the widest ones. Tabs therefore never
     * overlap, and a long label is never squeezed into a neighbour's column.
     *
     * @param labelWidths measured pixel width of each label, in tab order
     * @return one column width shared by the whole row
     */
    static int columnWidth(int availableWidth, int count, int[] labelWidths) {
        if (count <= 0) {
            return Math.max(1, availableWidth);
        }
        int padding = 12;
        int needed = 0;
        int widest = 0;
        for (int index = 0; index < count; index++) {
            int want = (index < labelWidths.length ? labelWidths[index] : 0) + padding;
            needed += want;
            widest = Math.max(widest, want);
        }
        int shared = (availableWidth - GAP * (count - 1)) / count;
        if (shared >= widest) {
            return shared;
        }
        // Not enough room for the widest label: the row wraps instead of overlapping.
        return Math.max(1, shared);
    }

    /**
 * Vertical rhythm of one management section, in the order it is painted:
 * title, divider, navigation, then the body. The body top is derived from how many rows the
 * navigation actually needs, so nothing has to guess whether the tabs wrapped.
 */
static final int SECTION_TITLE_H = 12;
    static final int SECTION_GAP = 6;
    static final int DIVIDER_H = 2;
    static final int BODY_GAP = 8;

    static Rect sectionTitle(int left, int contentTop, int width) {
        return new Rect(left, contentTop, width, SECTION_TITLE_H);
    }

    /** First navigation row; strictly below the title, so the two can never share a line. */
    static int navTop(int contentTop) {
        return contentTop + SECTION_TITLE_H + SECTION_GAP;
    }

    static Rect divider(int left, int contentTop, int width) {
        return new Rect(left, navTop(contentTop) - 3, width, DIVIDER_H);
    }

    /** Height the navigation occupies for a given number of wrapped rows. */
    static int navHeight(int rows) {
        return rows <= 0 ? 0 : rows * ROW_HEIGHT + (rows - 1) * ROW_GAP;
    }

    /**
     * Where the body starts. This is the single value every management body must use: it rises again
     * automatically when the tabs fit on one row and drops when they wrap to two.
     */
    static int bodyTop(int contentTop, int rows) {
        return navTop(contentTop) + navHeight(rows) + BODY_GAP;
    }

    /** One tab's rectangle: exactly one column wide, so a neighbour can never be covered. */
    static Rect tab(int x, int top, int index, int columnWidth, int count, int rows) {
        int perRow = rows == 1 ? count : (count + 1) / 2;
        int rowIndex = index / perRow;
        int column = index % perRow;
        return new Rect(x + column * (columnWidth + GAP),
                top + rowIndex * (ROW_HEIGHT + ROW_GAP),
                columnWidth,
                ROW_HEIGHT);
    }

    /** How many rows the measured labels need at {@code availableWidth}. */
    static int rows(int availableWidth, int count, int[] labelWidths) {
        return columnWidth(availableWidth, count, labelWidths) >= widestNeed(count, labelWidths) ? 1 : 2;
    }

    private static int widestNeed(int count, int[] labelWidths) {
        int widest = 0;
        for (int index = 0; index < count; index++) {
            widest = Math.max(widest, (index < labelWidths.length ? labelWidths[index] : 0) + 12);
        }
        return widest;
    }
}