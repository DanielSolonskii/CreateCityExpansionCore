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

    /** Horizontal padding inside one tab, so the label is never flush against its border. */
    static final int TAB_PAD_X = 6;

    /**
     * The width one tab needs for its own label, never wider than {@code availableWidth}.
     *
     * <p>This is the heart of the change: a tab is as wide as its text plus padding, so
     * "ТЕРРИТОРИЯ" and "ЖЕЛЕЗНАЯ ДОРОГА" get their full names instead of sharing a narrow column
     * with "ПРОЕКТЫ" and being cut off.
     */
    static int tabWidth(int labelWidth, int availableWidth) {
        return Math.max(1, Math.min(labelWidth + TAB_PAD_X * 2, Math.max(1, availableWidth)));
    }

    /**
     * Content-aware flow layout for the secondary management tabs.
     *
     * <p>Tabs are placed left to right at their own measured width. When the next tab would not fit
     * in the remaining space it moves to a new row instead of being squeezed, so tabs never
     * overlap and a long label is never truncated by a neighbour.
     *
     * <p>A plain greedy wrap is not enough here: with six Russian tabs it filled the first row with
     * five and left "ЖЕЛЕЗНАЯ ДОРОГА" alone on the second. When greedy needs exactly two rows and the
     * tabs can be split into two halves that both genuinely fit, the split is balanced instead. Every
     * tab keeps its own measured width, so this is a better break point, never a return to equal
     * columns.
     *
     * @param labelWidths measured pixel width of each label, in tab order
     * @return one rectangle per tab, in reading order; the same value paints and hit-tests
     */
    static Rect[] flow(int left, int top, int availableWidth, int[] labelWidths) {
        if (labelWidths == null || labelWidths.length == 0) {
            return new Rect[0];
        }
        int width = Math.max(1, availableWidth);

        // Greedy first: when everything fits on one row this is the answer, and when the balanced
        // split would not fit it is the fallback.
        int[] greedyEnds = greedyRowEnds(labelWidths, width);
        int[] rowEnds = greedyEnds;

        int greedyRows = greedyEnds.length;
        if (greedyRows == 2 && labelWidths.length % 2 == 0) {
            int half = labelWidths.length / 2;
            if (fitsRow(labelWidths, 0, half, width) && fitsRow(labelWidths, half, labelWidths.length, width)) {
                rowEnds = new int[]{ half, labelWidths.length };
            }
        }

        return place(left, top, width, labelWidths, rowEnds);
    }

    /** Exclusive end index of each greedy row, i.e. where the single-pass wrap broke the line. */
    private static int[] greedyRowEnds(int[] labelWidths, int availableWidth) {
        int[] ends = new int[labelWidths.length + 1];
        int rowStart = 0;
        int used = 0;
        int rows = 0;

        for (int index = 0; index < labelWidths.length; index++) {
            int tab = tabWidth(labelWidths[index], availableWidth);
            int candidate = used == 0 ? tab : used + GAP + tab;
            if (index > rowStart && candidate > availableWidth) {
                // The row ends at the tab that would not fit, which starts the next one.
                ends[rows++] = index;
                rowStart = index;
                used = tab;
            } else {
                used = candidate;
            }
        }
        ends[rows++] = labelWidths.length;

        int[] result = new int[rows];
        System.arraycopy(ends, 0, result, 0, rows);
        return result;
    }

    /** Whether tabs {@code [from, to)} genuinely fit on one row, without any truncation. */
    private static boolean fitsRow(int[] labelWidths, int from, int to, int availableWidth) {
        if (to <= from) {
            return false;
        }
        int total = 0;
        for (int index = from; index < to; index++) {
            int tab = tabWidth(labelWidths[index], availableWidth);
            // A tab that had to be clamped is already truncated, so this row is not a clean fit.
            if (tab != labelWidths[index] + TAB_PAD_X * 2) {
                return false;
            }
            total = total == 0 ? tab : total + GAP + tab;
        }
        return total <= availableWidth;
    }

    /** Places the tabs onto the rows described by {@code rowEnds}. */
    private static Rect[] place(int left, int top, int availableWidth, int[] labelWidths, int[] rowEnds) {
        Rect[] tabs = new Rect[labelWidths.length];
        int index = 0;
        int rowTop = top;

        for (int end : rowEnds) {
            int cursorX = left;
            while (index < end && index < labelWidths.length) {
                int tab = tabWidth(labelWidths[index], availableWidth);
                tabs[index] = new Rect(cursorX, rowTop, tab, ROW_HEIGHT);
                cursorX += tab + GAP;
                index++;
            }
            rowTop += ROW_HEIGHT + ROW_GAP;
        }
        return tabs;
    }

    /**
     * How many rows the measured labels really need at {@code availableWidth}.
     *
     * <p>Derived from {@link #flow}, so the row count and the painted rectangles can never disagree
     * — which is exactly what {@link #bodyTop} needs to stay correct.
     */
    static int rows(int availableWidth, int count, int[] labelWidths) {
        int length = Math.min(count, labelWidths == null ? 0 : labelWidths.length);
        if (length <= 0) {
            return 0;
        }
        Rect[] tabs = flow(0, 0, availableWidth, java.util.Arrays.copyOf(labelWidths, length));

        // Count distinct rows, not tabs off the first row: a wrapped row can hold several tabs, so
        // counting every non-first-row tab over-reports the height and would push the body too far
        // down.
        int rows = 1;
        int previousTop = tabs[0].y();
        for (int index = 1; index < tabs.length; index++) {
            if (tabs[index].y() != previousTop) {
                rows++;
                previousTop = tabs[index].y();
            }
        }
        return rows;
    }

    /** Bottom edge of the navigation, measured from the tabs themselves rather than a row count. */
    static int navBottom(int top, Rect[] tabs) {
        if (tabs == null || tabs.length == 0) {
            return top;
        }
        int bottom = tabs[0].bottom();
        for (Rect tab : tabs) {
            bottom = Math.max(bottom, tab.bottom());
        }
        return bottom;
    }

    /**
     * Where the body starts for tabs that were actually flowed.
     *
     * <p>The same value as {@link #bodyTop}, but derived from the painted rectangles' real bottom
     * edge instead of a row count, so a wrapped tab can never be painted over by the body.
     */
    static int bodyTop(int contentTop, Rect[] tabs) {
        return navBottom(navTop(contentTop), tabs) + BODY_GAP;
    }

    /**
     * The one vertical frame every management body shares.
     *
     * <p>Five of the six management tabs anchored themselves to the raw {@code contentTop} plus their
     * own magic offsets, which silently assumed a single navigation row. When the Russian tabs wrap
     * onto two rows those bodies started inside the navigation and painted over it. Now every body is
     * measured inside this frame: {@link #top()} is the real bottom of the tabs plus
     * {@link #BODY_GAP}, while {@link #bottom()} stays the panel's content bottom, so a taller
     * navigation shrinks the body instead of pushing its footer off the panel.
     */
    record ManagementBody(int top, int bottom) {
        int height() {
            return bottom - top;
        }
    }

    static ManagementBody managementBody(int contentTop, int contentHeight, int managementTabRows) {
        int top = bodyTop(contentTop, managementTabRows);
        return new ManagementBody(top, Math.max(top + 1, contentTop + contentHeight));
    }
}