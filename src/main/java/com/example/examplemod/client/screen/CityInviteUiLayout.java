package com.example.examplemod.client.screen;

/**
 * Geometry for the invite and members screens, kept pure so the headless check can prove that what
 * is painted is exactly what is clickable.
 *
 * <p>The previous invite screen hard-coded every row's position inline in both {@code render} and
 * {@code mouseClicked}, which is how a panel ends up with rows you can see but not click. Every
 * rectangle below is computed once and reused by both paths.
 */
final class CityInviteUiLayout {
    private CityInviteUiLayout() {
    }

    static final int MICRO = 4;
    static final int NORMAL = 8;
    static final int SECTION = 12;
    static final int EDGE = 12;

    static final int ROW_HEIGHT = 26;
    static final int HEADER_HEIGHT = 34;
    static final int FEEDBACK_HEIGHT = 16;

    record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        boolean overlaps(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }
    }

    /** Panel width: comfortable on a desktop screen, never wider than a phone-sized one. */
    static int panelWidth(int screenWidth) {
        return Math.min(340, Math.max(240, screenWidth - EDGE * 2));
    }

    static int panelLeft(int screenWidth) {
        return (screenWidth - panelWidth(screenWidth)) / 2;
    }

    /** Search field across the panel, always at least as wide as the panel allows. */
    static Rect searchField(int screenWidth, int top) {
        int left = panelLeft(screenWidth);
        return new Rect(left + EDGE, top + HEADER_HEIGHT, panelWidth(screenWidth) - EDGE * 2, 18);
    }

    /** First row under the search field. */
    static int listTop(int screenWidth, int top, boolean feedbackVisible) {
        return searchField(screenWidth, top).bottom() + NORMAL + (feedbackVisible ? FEEDBACK_HEIGHT : 0);
    }

    /** Rows are clipped to the space below them, so a long result set scrolls instead of overflowing. */
    static int visibleRows(int screenHeight, int top, boolean feedbackVisible) {
        return Math.max(1, (screenHeight - EDGE - listTop(0, top, feedbackVisible)) / ROW_HEIGHT);
    }

    static Rect row(int screenWidth, int top, boolean feedbackVisible, int index, int rows) {
        int left = panelLeft(screenWidth);
        int width = panelWidth(screenWidth);
        int capacity = Math.max(1, Math.min(rows, 12));
        int y = listTop(screenWidth, top, feedbackVisible) + index * ROW_HEIGHT;
        return new Rect(left + EDGE, y, width - EDGE * 2, ROW_HEIGHT - MICRO);
    }

    /** Primary action at the right of a row. Only AVAILABLE rows get one. */
    static Rect rowAction(int screenWidth, int top, boolean feedbackVisible, int index, int rows) {
        Rect bounds = row(screenWidth, top, feedbackVisible, index, rows);
        int width = Math.min(72, Math.max(56, bounds.width() / 3));
        return new Rect(bounds.right() - width - MICRO, bounds.y() + 2, width, bounds.height() - 4);
    }

    /** How much of the nickname column is left once the action column is reserved. */
    static int nameColumnWidth(int screenWidth, boolean hasAction) {
        Rect bounds = row(screenWidth, 0, false, 0, 1);
        Rect action = rowAction(screenWidth, 0, false, 0, 1);
        int reserved = hasAction ? bounds.width() - (action.x() - bounds.x()) + MICRO : 0;
        return Math.max(24, bounds.width() - reserved);
    }

    /** The single invite CTA under a members list. */
    static Rect inviteButton(int screenWidth, int screenHeight) {
        int left = panelLeft(screenWidth);
        int width = panelWidth(screenWidth);
        return new Rect(left + EDGE, screenHeight - EDGE - 24, width - EDGE * 2, 24);
    }

    /** Inline success or error line directly under the search field. */
    static Rect feedback(int screenWidth, int top) {
        Rect field = searchField(screenWidth, top);
        int left = panelLeft(screenWidth);
        return new Rect(left + EDGE, field.bottom() + MICRO, panelWidth(screenWidth) - EDGE * 2, FEEDBACK_HEIGHT);
    }

    /** Members rows use the same rhythm as search rows so the two screens read as one family. */
    static Rect memberRow(int screenWidth, int top, int index, int count) {
        int left = panelLeft(screenWidth);
        int width = panelWidth(screenWidth);
        return new Rect(left + EDGE, top + index * ROW_HEIGHT, width - EDGE * 2, ROW_HEIGHT - MICRO);
    }
}