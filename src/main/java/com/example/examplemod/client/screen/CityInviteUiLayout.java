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

    /** The single management control of a member row; its hitbox can never reach a neighbouring row. */
    static Rect memberAction(int screenWidth, int top, int index, int count) {
        Rect row = memberRow(screenWidth, top, index, count);
        return new Rect(row.right() - ROW_HEIGHT + 4, row.y() + 3, ROW_HEIGHT - 8, row.height() - 6);
    }

    /** The visible window of the member list; anything outside is clipped rather than drawn. */
    static Rect memberClip(int screenWidth, int top, int rows, int count) {
        Rect first = memberRow(screenWidth, top, 0, count);
        return new Rect(first.x(), first.y(), first.width(), Math.max(ROW_HEIGHT, rows * ROW_HEIGHT));
    }

    /** How many whole rows fit between {@code top} and {@code bottom}. */
    static int memberVisibleRows(int top, int bottom) {
        return Math.max(1, (bottom - top) / ROW_HEIGHT);
    }

    /** One clamp, shared by the wheel, the keyboard and the render pass so they cannot disagree. */
    static int clampMemberScroll(int scroll, int total, int rows) {
        return Math.max(0, Math.min(scroll, Math.max(0, total - rows)));
    }

    /** Scrollbar thumb, derived so the render and the hit-test share one computation. */
    static Rect memberScrollThumb(int screenWidth, int top, int rows, int total, int scroll) {
        Rect clip = memberClip(screenWidth, top, rows, total);
        int x = clip.right() + NORMAL;
        if (total <= rows) {
            return new Rect(x, clip.y(), 4, clip.height());
        }
        int thumbHeight = Math.max(12, clip.height() * rows / total);
        int available = clip.height() - thumbHeight;
        int offset = clampMemberScroll(scroll, total, rows) * available
                / Math.max(1, total - rows);
        return new Rect(x, clip.y() + offset, 4, thumbHeight);
    }

    /**
     * The member list keeps the pre-existing invite button widget: it already carries the permission
     * check and the on-screen placement, so there is deliberately no second invite geometry here and
     * no second clickable zone.
     */

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