package com.example.examplemod.client.screen;

/**
 * Single geometry source for the Members body of the City Pylon panel.
 *
 * <p>Everything that is painted in the Members tab — the toolbar with its label and the invite
 * button, the clipped row list, each row's avatar / nickname / metadata / gear, the scrollbar and
 * every mouse hitbox — is derived from one {@link MembersLayout} value. There is deliberately no
 * second arithmetic: the old breakdown (a fixed invite Y set once in {@code init()}, a toolbar
 * painted on top of the first row, and rows measured against the screen-centred invite panel
 * instead of the panel's content column) is exactly why the painted UI and the click targets
 * disagreed on screen.
 *
 * <p>Kept pure, like {@link CityPylonUiLayout} and {@link CityInviteUiLayout}, so the headless
 * check can prove the ordering and containment for the Russian two-row tab layout.
 */
final class CityPylonMembersLayout {
    private CityPylonMembersLayout() {
    }

    static record Rect(int x, int y, int width, int height) {
        int right() {
            return x + width;
        }

        int bottom() {
            return y + height;
        }

        boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }
    }

    /*
     * Rhythm of the body, top to bottom:
     * last management tab row -> BODY_GAP (inside CityPylonUiLayout.bodyTop)
     * -> toolbar (label + invite button, one line)
     * -> LIST_GAP -> first member row -> visibleRows * rowHeight.
     */
    static final int TOOLBAR_HEIGHT = 24;
    static final int LIST_GAP = 6;

    /** Invite button inside the toolbar; vertically centred by INVITE_BUTTON_HEIGHT. */
    static final int INVITE_BUTTON_WIDTH = 110;
    static final int INVITE_BUTTON_HEIGHT = 20;

    /*
     * A row carries two real text lines (nickname, then status + role). The row height is measured
     * from the caller's real font.lineHeight instead of a frozen constant, and only then clamped
     * into the requested 26-32px band, so three lines can never be squeezed into 22px.
     */
    static final int ROW_MIN_HEIGHT = 26;
    static final int ROW_MAX_HEIGHT = 32;
    static final int NICKNAME_TOP = 4;
    static final int METADATA_GAP = 2;
    static final int ROW_BOTTOM_PAD = 2;

    /*
     * The avatar is measured from the row itself and can never exceed the row minus 4px, so it is
     * not a second source of vertical geometry.
     */
    static final int AVATAR_PAD = 6;
    static final int AVATAR_MAX_INSET = 4;
    static final int TEXT_GAP = 4;

    /** Gear control on the right of a row. */
    static final int GEAR_SIZE = 12;
    static final int GEAR_RIGHT_MARGIN = 6;

    /** Scrollbar to the right of the clipped list. */
    static final int SCROLLBAR_GAP = 8;
    static final int SCROLLBAR_WIDTH = 4;

    /**
     * Row height for a given real font line height. This is the only place a row height is
     * decided: {@code NICKNAME_TOP + 2 lines + METADATA_GAP + ROW_BOTTOM_PAD}.
     *
     * <p>The 26-32px band is a design preference, not a hard limit. The content rule wins: if an
     * unusually tall font genuinely needs more than 32px, the row grows rather than letting the
     * nickname or the metadata spill past the row's bottom edge. At the vanilla
     * {@code Font.lineHeight} of 9 this lands on 26px, inside the band.
     */
    static int rowHeightFor(int fontLineHeight) {
        int line = Math.max(1, fontLineHeight);
        int needed = NICKNAME_TOP + 2 * line + METADATA_GAP + ROW_BOTTOM_PAD;
        return Math.max(ROW_MIN_HEIGHT, Math.max(needed, Math.min(ROW_MAX_HEIGHT, needed)));
    }

    record MembersLayout(
            int contentLeft,
            int contentRight,
            int bodyTop,
            int firstRowTop,
            int visibleRows,
            int lastRowBottom,
            int rowHeight,
            int fontLineHeight
    ) {
        Rect toolbar() {
            return new Rect(contentLeft, bodyTop, contentRight - contentLeft, TOOLBAR_HEIGHT);
        }

        /** The toolbar label baseline; one real font line centred inside the toolbar. */
        int toolbarLabelY() {
            return bodyTop + Math.max(0, (TOOLBAR_HEIGHT - fontLineHeight) / 2);
        }

        /** The single invite entry point; the real widget is positioned from this. */
        Rect invite() {
            int x = contentRight - INVITE_BUTTON_WIDTH;
            int y = bodyTop + (TOOLBAR_HEIGHT - INVITE_BUTTON_HEIGHT) / 2;
            return new Rect(x, y, INVITE_BUTTON_WIDTH, INVITE_BUTTON_HEIGHT);
        }

        /** The clipped window of the list; wheel, clamp, scrollbar and painting all read it. */
        Rect listClip() {
            return new Rect(contentLeft, firstRowTop, contentRight - contentLeft,
                    visibleRows * rowHeight);
        }

        /** One visible row; index is relative to the first visible row. */
        Rect row(int visibleIndex) {
            return new Rect(contentLeft, firstRowTop + visibleIndex * rowHeight,
                    contentRight - contentLeft, rowHeight);
        }

        /**
         * The player head: sized from the row (never taller than row.height - 4), anchored by the
         * row's left padding and centred vertically inside it.
         */
        Rect avatar(int visibleIndex) {
            Rect r = row(visibleIndex);
            // Avatar is a function of the row only: never taller than row - 2*AVATAR_MAX_INSET,
            // hence never taller than row - 4, and vertically centred in the row.
            int size = Math.max(4, r.height() - 2 * AVATAR_MAX_INSET);
            return new Rect(r.x() + AVATAR_PAD, r.y() + (r.height() - size) / 2, size, size);
        }

        int nicknameY(int visibleIndex) {
            return row(visibleIndex).y() + NICKNAME_TOP;
        }

        int metadataY(int visibleIndex) {
            return nicknameY(visibleIndex) + fontLineHeight + METADATA_GAP;
        }

        /** Left edge of the text column: after the avatar and a real gap. */
        int textX(int visibleIndex) {
            return avatar(visibleIndex).right() + TEXT_GAP;
        }

        /** Right edge of the text column: before the gear and a real gap. */
        int textRight(int visibleIndex) {
            return gear(visibleIndex).x() - TEXT_GAP;
        }

        /** The row's management control, centred vertically, inside the row's right margin. */
        Rect gear(int visibleIndex) {
            Rect r = row(visibleIndex);
            int x = r.right() - GEAR_RIGHT_MARGIN - GEAR_SIZE;
            int y = r.y() + Math.max(0, (r.height() - GEAR_SIZE) / 2);
            return new Rect(x, y, GEAR_SIZE, GEAR_SIZE);
        }

        int clampScroll(int scroll, int total) {
            return Math.max(0, Math.min(scroll, Math.max(0, total - visibleRows)));
        }

        /** The scrollbar track, derived from the clip so painting and reach never drift. */
        Rect scrollTrack() {
            Rect clip = listClip();
            return new Rect(clip.right() + SCROLLBAR_GAP - SCROLLBAR_WIDTH / 2,
                    clip.y(), SCROLLBAR_WIDTH, clip.height());
        }

        Rect scrollThumb(int total, int scroll) {
            Rect track = scrollTrack();
            if (total <= visibleRows) {
                return new Rect(track.x(), track.y(), track.width(), track.height());
            }
            int thumbHeight = Math.max(12, track.height() * visibleRows / total);
            int available = track.height() - thumbHeight;
            int offset = clampScroll(scroll, total) * available / Math.max(1, total - visibleRows);
            return new Rect(track.x(), track.y() + offset, track.width(), thumbHeight);
        }
    }

    /**
     * Computes the members body from the content bounds, the management row count and the real font
     * line height. The body top comes from {@link CityPylonUiLayout#bodyTop} so a wrapped Russian
     * tab row pushes the whole body (toolbar, invite button and list) down with it.
     */
    static MembersLayout compute(
            int contentLeft,
            int contentTop,
            int contentWidth,
            int bodyBottom,
            int managementTabRows,
            int fontLineHeight
    ) {
        int contentRight = contentLeft + contentWidth;
        int bodyTop = CityPylonUiLayout.bodyTop(contentTop, managementTabRows);
        int rowHeight = rowHeightFor(fontLineHeight);
        int firstRowTop = bodyTop + TOOLBAR_HEIGHT + LIST_GAP;
        int available = Math.max(rowHeight, bodyBottom - firstRowTop);
        int visibleRows = Math.max(1, available / rowHeight);
        int lastRowBottom = firstRowTop + visibleRows * rowHeight;
        return new MembersLayout(contentLeft, contentRight, bodyTop, firstRowTop,
                visibleRows, lastRowBottom, rowHeight, Math.max(1, fontLineHeight));
    }
}