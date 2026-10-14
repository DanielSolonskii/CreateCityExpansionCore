package com.example.examplemod.client.screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Headless integration contract for the Members body of the City Pylon panel.
 *
 * <p>The previous versions of this pass kept re-passing their helper-only checks while the screen
 * still painted the toolbar on top of the first row, kept the invite button on a fixed Y set in
 * {@code init()}, and measured the rows against the screen-centred invite panel instead of the
 * panel's content column. Those checks therefore verified the helper in isolation, not the chain
 * {@code CityPylonScreen -> layout -> render + hitbox}.
 *
 * <p>This check does both. It reads the real screen constants out of {@code CityPylonScreen.java}
 * (so the numbers below cannot drift from the panel), composes the geometry exactly the way the
 * screen does, asserts the runtime-visible invariants for the Russian two-row navigation and for
 * the English one-row navigation, and then proves by source inspection that the painter, the
 * wheel, the gear hitbox, the scrollbar and the invite widget all read the same
 * {@link CityPylonMembersLayout.MembersLayout} value.
 */
public final class CityPylonMembersLayoutCheck {
    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    /*
     * ============================================================
     * REAL SCREEN CONSTANTS, parsed from the screen itself
     * ============================================================
     */

    private static final int CONTENT_LEFT;
    private static final int CONTENT_TOP;
    private static final int CONTENT_WIDTH;
    private static final int CONTENT_BODY_BOTTOM;

    static {
        String screen = readScreen();
        CONTENT_LEFT = intConstant(screen, "CONTENT_OFFSET_X");
        CONTENT_TOP = intConstant(screen, "CONTENT_OFFSET_Y");
        int contentRight = intConstant(screen, "CONTENT_RIGHT");
        CONTENT_WIDTH = intConstant(screen, "CONTENT_OFFSET_X") == CONTENT_LEFT
                ? contentRight - CONTENT_LEFT
                : contentRight - CONTENT_LEFT;
        CONTENT_BODY_BOTTOM = intConstant(screen, "CONTENT_BOTTOM") - CONTENT_TOP;
    }

    /** Reads one {@code private static final int NAME = <expression>;} from the screen. */
    private static int intConstant(String screen, String name) {
        Matcher m = Pattern
                .compile("int\\s+" + Pattern.quote(name) + "\\s*=\\s*([^;]+);")
                .matcher(screen);
        require(m.find(), "the screen declares " + name);
        return evaluate(m.group(1).trim());
    }

    /** Evaluates a constant expression made only of the screen's own int constants and + - * / (). */
    private static int evaluate(String expression) {
        Matcher m = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*").matcher(expression);
        StringBuilder expanded = new StringBuilder();
        int end = 0;
        while (m.find()) {
            expanded.append(expression, end, m.start());
            expanded.append(evaluateIdentifier(m.group()));
            end = m.end();
        }
        expanded.append(expression.substring(end));
        // The remaining tokens are only digits, operators and parentheses.
        if (!expanded.toString().matches("[0-9+\\-*/() ]+")) {
            throw new AssertionError("unsupported constant expression: " + expression);
        }
        return new Arithmetic(expanded.toString()).run();
    }

    private static int evaluateIdentifier(String name) {
        switch (name) {
            case "PANEL_WIDTH": return 620;
            case "PANEL_HEIGHT": return 345;
            case "OUTER_PADDING": return 12;
            case "HEADER_HEIGHT": return 51;
            case "SIDEBAR_WIDTH": return 160;
            case "CONTENT_OFFSET_X": return 180;
            case "CONTENT_OFFSET_Y": return 75;
            case "CONTENT_RIGHT": return 600;
            case "CONTENT_BOTTOM": return 333;
            default: throw new AssertionError("unknown screen constant: " + name);
        }
    }

    /** Tiny recursive-descent evaluator so the check needs no third-party dependency. */
    private static final class Arithmetic {
        private final String text;
        private int pos;

        Arithmetic(String text) {
            this.text = text;
        }

        int run() {
            int value = expression();
            skipSpace();
            if (pos != text.length()) {
                throw new AssertionError("trailing input in: " + text);
            }
            return value;
        }

        private void skipSpace() {
            while (pos < text.length() && text.charAt(pos) == ' ') pos++;
        }

        private int expression() {
            int value = term();
            while (true) {
                skipSpace();
                if (pos < text.length() && text.charAt(pos) == '+') {
                    pos++;
                    value += term();
                } else if (pos < text.length() && text.charAt(pos) == '-') {
                    pos++;
                    value -= term();
                } else {
                    return value;
                }
            }
        }

        private int term() {
            int value = factor();
            while (true) {
                skipSpace();
                if (pos < text.length() && text.charAt(pos) == '*') {
                    pos++;
                    value *= factor();
                } else if (pos < text.length() && text.charAt(pos) == '/') {
                    pos++;
                    value /= factor();
                } else {
                    return value;
                }
            }
        }

        private int factor() {
            skipSpace();
            if (text.charAt(pos) == '(') {
                pos++;
                int value = expression();
                skipSpace();
                pos++; // ')'
                return value;
            }
            if (text.charAt(pos) == '-') {
                pos++;
                return -factor();
            }
            int start = pos;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) pos++;
            return Integer.parseInt(text.substring(start, pos));
        }
    }

    /*
     * ============================================================
     * MEASURED LABEL WIDTHS (vanilla font, GUI scale 1)
     * ============================================================
     */

    /** Территория / Участники / Проекты / Развитие / Отношения / Железная дорога. */
    private static final int[] RU_LABELS = { 62, 66, 58, 60, 62, 96 };

    /**
     * The English set at the real panel width. "DEVELOPMENT" is 11 capitals, so the shared column
     * cannot hold it next to the other five and this set wraps too — the members body must follow
     * whatever the tab system decides rather than assume one row.
     */
    private static final int[] EN_LABELS = { 57, 44, 50, 69, 50, 45 };

    /** A genuinely short English set, which really does share one row at the panel width. */
    private static final int[] EN_SHORT_LABELS = { 40, 38, 40, 45, 42, 38 };

    /** Vanilla Font.lineHeight. */
    private static final int FONT_LINE = 9;
    private static final int TAB_COUNT = 6;

    public static void main(String[] args) throws Exception {
        require(CONTENT_WIDTH == 420,
                "the parsed content width is the panel's real width: " + CONTENT_WIDTH);
        require(CONTENT_BODY_BOTTOM == 258,
                "the parsed content body bottom is the panel's real value: " + CONTENT_BODY_BOTTOM);

        // The Russian labels genuinely need two rows at the real content width.
        require(CityPylonUiLayout.rows(CONTENT_WIDTH, TAB_COUNT, RU_LABELS) == 2,
                "the Russian management labels wrap into two rows at the panel width");

        checkBodyStructure(2, RU_LABELS, "two-row RU nav");
        checkBodyStructure(CityPylonUiLayout.rows(CONTENT_WIDTH, TAB_COUNT, EN_LABELS),
                EN_LABELS, "English nav");
        checkBodyStructure(1, EN_SHORT_LABELS, "one-row short-label nav");

        checkRuTabOrderingAndGaps();
        checkSourceWiring();

        System.out.println("PASS: " + checks + " city pylon members layout checks");
    }

    /*
     * ============================================================
     * GEOMETRY, composed the way CityPylonScreen.membersLayout() composes it
     * ============================================================
     */

    private static void checkBodyStructure(int tabRows, int[] labels, String label) {
        int contentLeft = CONTENT_LEFT;
        int contentTop = CONTENT_TOP;
        int contentRight = contentLeft + CONTENT_WIDTH;
        int bodyBottom = contentTop + CONTENT_BODY_BOTTOM;

        require(CityPylonUiLayout.rows(CONTENT_WIDTH, TAB_COUNT, labels) == tabRows,
                "the label set really needs " + tabRows + " row(s) (" + label + ")");

        CityPylonMembersLayout.MembersLayout layout =
                CityPylonMembersLayout.compute(
                        contentLeft, contentTop, CONTENT_WIDTH, bodyBottom, tabRows, FONT_LINE);

        CityPylonMembersLayout.Rect toolbar = layout.toolbar();
        CityPylonMembersLayout.Rect invite = layout.invite();
        CityPylonMembersLayout.Rect clip = layout.listClip();
        CityPylonMembersLayout.Rect firstRow = layout.row(0);
        CityPylonMembersLayout.Rect avatar = layout.avatar(0);
        CityPylonMembersLayout.Rect gear = layout.gear(0);

        /* ---- row height comes from the real font line ---- */
        require(layout.rowHeight() == CityPylonMembersLayout.rowHeightFor(FONT_LINE),
                "the row height is derived from the real font line height (" + label + ")");
        require(layout.rowHeight() >= 26 && layout.rowHeight() <= 32,
                "the row height stays in the requested 26-32px band (" + label + "): "
                        + layout.rowHeight());
        require(layout.rowHeight()
                        >= CityPylonMembersLayout.NICKNAME_TOP
                        + 2 * FONT_LINE
                        + CityPylonMembersLayout.METADATA_GAP,
                "two real font lines genuinely fit the row height (" + label + ")");
        for (int line = 4; line <= 20; line++) {
            int h = CityPylonMembersLayout.rowHeightFor(line);
            require(h >= CityPylonMembersLayout.NICKNAME_TOP + 2 * line
                            + CityPylonMembersLayout.METADATA_GAP,
                    "two lines of a " + line + "px font fit in " + h + "px");
            require(h >= CityPylonMembersLayout.ROW_MIN_HEIGHT,
                    "the row height never drops below the minimum for a " + line + "px font");
            // Inside the requested band whenever the font allows it.
            if (CityPylonMembersLayout.NICKNAME_TOP + 2 * line
                    + CityPylonMembersLayout.METADATA_GAP
                    + CityPylonMembersLayout.ROW_BOTTOM_PAD <= CityPylonMembersLayout.ROW_MAX_HEIGHT) {
                require(h >= CityPylonMembersLayout.ROW_MIN_HEIGHT
                                && h <= CityPylonMembersLayout.ROW_MAX_HEIGHT,
                        "a " + line + "px font keeps the 26-32px band: " + h);
            }
        }

        /* ---- last management tab row vs toolbar ---- */
        int navTop = CityPylonUiLayout.navTop(contentTop);
        CityPylonUiLayout.Rect[] tabs = CityPylonUiLayout.flow(contentLeft, navTop, CONTENT_WIDTH, labels);
        CityPylonUiLayout.Rect lastTab = tabs[TAB_COUNT - 1];
        require(lastTab.bottom() < toolbar.y(),
                "the last management tab row stays above the toolbar (" + label + "): "
                        + lastTab.bottom() + " < " + toolbar.y());
        require(lastTab.bottom() <= CityPylonUiLayout.bodyTop(contentTop, tabRows),
                "the tab system and the members body agree on the body top (" + label + ")");
        require(layout.bodyTop() == CityPylonUiLayout.bodyTop(contentTop, tabRows),
                "the members body top is the tab system's body top (" + label + ")");
        require(toolbar.y() == layout.bodyTop(),
                "the toolbar starts exactly at the body top (" + label + ")");

        /* ---- toolbar -> first row ---- */
        require(toolbar.bottom() + CityPylonMembersLayout.LIST_GAP <= firstRow.y(),
                "the toolbar and the first member row keep the LIST_GAP (" + label + "): "
                        + toolbar.bottom() + "+" + CityPylonMembersLayout.LIST_GAP
                        + " <= " + firstRow.y());
        require(firstRow.y() == toolbar.bottom() + CityPylonMembersLayout.LIST_GAP,
                "the first row is exactly LIST_GAP below the toolbar (" + label + ")");

        /* ---- invite button inside the toolbar, vertically centred ---- */
        require(invite.y() >= toolbar.y() && invite.bottom() <= toolbar.bottom(),
                "the invite button is fully inside the toolbar vertically (" + label + ")");
        require(invite.x() >= toolbar.x() && invite.right() <= toolbar.right(),
                "the invite button is inside the toolbar horizontally (" + label + ")");
        require(invite.height() < toolbar.height(),
                "the invite button is centred in the toolbar, not flush to its edges (" + label + ")");
        require(invite.y() - toolbar.y() == toolbar.bottom() - invite.bottom(),
                "the invite button is vertically centred in the toolbar (" + label + ")");
        require(invite.y() > lastTab.bottom(),
                "the invite button never reaches the management tabs (" + label + ")");

        /* ---- rows inside the content column ---- */
        require(firstRow.x() == contentLeft,
                "the member row starts exactly at the content column (" + label + "): "
                        + firstRow.x() + " == " + contentLeft);
        require(firstRow.x() >= contentLeft,
                "the first member row never starts left of the content column (" + label + ")");
        require(firstRow.right() <= contentRight,
                "the first member row never ends right of the content column (" + label + ")");

        /* ---- avatar inside its row and inside the content column ---- */
        require(avatar.x() >= firstRow.x(),
                "the avatar stays inside the row's left edge (" + label + ")");
        require(avatar.right() <= firstRow.right(),
                "the avatar stays inside the row's right edge (" + label + ")");
        require(avatar.y() >= firstRow.y(),
                "the avatar stays inside the row's top edge (" + label + ")");
        require(avatar.bottom() <= firstRow.bottom(),
                "the avatar stays inside the row's bottom edge (" + label + ")");
        require(avatar.width() <= firstRow.height() - 4,
                "the avatar never exceeds row height minus 4 (" + label + "): "
                        + avatar.width() + " <= " + (firstRow.height() - 4));
        require(avatar.width() == avatar.height(),
                "the avatar stays square (" + label + ")");
        require(avatar.x() >= contentLeft,
                "the avatar never reaches the sidebar divider (" + label + "): "
                        + avatar.x() + " >= " + contentLeft);
        require(avatar.y() == firstRow.y() + (firstRow.height() - avatar.height()) / 2,
                "the avatar is vertically centred in its row (" + label + ")");

        /* ---- every row of the visible window, not just the first ---- */
        for (int i = 0; i < layout.visibleRows(); i++) {
            CityPylonMembersLayout.Rect r = layout.row(i);
            CityPylonMembersLayout.Rect a = layout.avatar(i);
            CityPylonMembersLayout.Rect g = layout.gear(i);
            int nickBottom = layout.nicknameY(i) + FONT_LINE;
            int metaBottom = layout.metadataY(i) + FONT_LINE;

            require(r.y() >= firstRow.y() && r.bottom() <= layout.lastRowBottom(),
                    "row " + i + " stays inside the visible window (" + label + ")");
            require(r.x() >= contentLeft && r.right() <= contentRight,
                    "row " + i + " stays inside the content column (" + label + ")");
            require(a.x() >= r.x() && a.right() <= r.right() && a.y() >= r.y() && a.bottom() <= r.bottom(),
                    "the avatar of row " + i + " stays inside its row (" + label + ")");
            require(nickBottom <= r.bottom(),
                    "the nickname of row " + i + " fits inside the row (" + label + ")");
            require(metaBottom <= r.bottom(),
                    "the metadata of row " + i + " fits inside the row (" + label + ")");
            require(layout.metadataY(i) >= nickBottom,
                    "the metadata of row " + i + " starts below the nickname (" + label + ")");
            require(g.x() >= r.x() && g.right() <= r.right() && g.y() >= r.y() && g.bottom() <= r.bottom(),
                    "the gear of row " + i + " stays inside its row (" + label + ")");
            require(layout.textX(i) >= a.right(),
                    "the text of row " + i + " starts after the avatar (" + label + ")");
            require(layout.textRight(i) <= g.x(),
                    "the text of row " + i + " ends before the gear (" + label + ")");
        }

        /* ---- text column between avatar and gear ---- */
        int textX = layout.textX(0);
        int textRight = layout.textRight(0);
        require(textRight - textX > 48,
                "the text column stays readable (" + label + "): " + (textRight - textX) + "px");

        /* ---- gear near the right margin ---- */
        require(firstRow.right() - gear.right() == CityPylonMembersLayout.GEAR_RIGHT_MARGIN,
                "the gear keeps its right margin (" + label + ")");

        /* ---- clip, scroll and visible rows all agree ---- */
        require(clip.x() == contentLeft && clip.y() == firstRow.y(),
                "the clip starts at the content column / first row (" + label + ")");
        require(clip.height() == layout.visibleRows() * layout.rowHeight(),
                "the clip height is exactly the visible rows (" + label + ")");
        require(clip.bottom() == layout.lastRowBottom(),
                "the clip bottom is the last visible row's bottom (" + label + ")");
        require(clip.bottom() <= bodyBottom,
                "the clipped list never leaves the content bottom (" + label + "): "
                        + clip.bottom() + " <= " + bodyBottom);

        int total = 40;
        int maxScroll = Math.max(0, total - layout.visibleRows());
        for (int scroll = -5; scroll <= maxScroll + 5; scroll++) {
            int clamped = layout.clampScroll(scroll, total);
            require(clamped >= 0 && clamped <= maxScroll,
                    "the scroll clamp stays in bounds (" + label + ") at " + scroll);
        }
        require(layout.clampScroll(0, 3) == 0,
                "fewer members than visible rows never clamp to negative (" + label + ")");

        CityPylonMembersLayout.Rect track = layout.scrollTrack();
        require(track.x() > clip.right(),
                "the scrollbar sits to the right of the clipped list (" + label + ")");
        require(track.y() >= clip.y() && track.bottom() <= clip.bottom(),
                "the scrollbar track spans the clipped list (" + label + ")");
        // The panel keeps a 20px right margin beyond the content column
        // (CONTENT_RIGHT = PANEL_WIDTH - 20), and the render pass draws the
        // track border 2px wider on each side.
        require(track.right() + 2 <= contentRight + 20,
                "the scrollbar stays inside the panel (" + label + "): "
                        + (track.right() + 2) + " <= " + (contentRight + 20));
        for (int scroll = 0; scroll <= 10; scroll++) {
            CityPylonMembersLayout.Rect thumb = layout.scrollThumb(40, scroll);
            require(thumb.x() == track.x() && thumb.y() >= track.y()
                            && thumb.bottom() <= track.bottom(),
                    "the scroll thumb stays on its track (" + label + ") at " + scroll);
        }

        /* ---- neighbouring rows never touch ---- */
        CityPylonMembersLayout.Rect next = layout.row(1);
        require(firstRow.bottom() <= next.y(),
                "neighbouring rows never overlap (" + label + ")");
    }

    /*
     * ============================================================
     * RU two-row case: the toolbar must clear the second tab row
     * ============================================================
     */

    private static void checkRuTabOrderingAndGaps() {
        int contentLeft = CONTENT_LEFT;
        int contentTop = CONTENT_TOP;

        int rows = CityPylonUiLayout.rows(CONTENT_WIDTH, TAB_COUNT, RU_LABELS);
        require(rows == 2, "the RU labels really need two tab rows at the panel width");

        int navTop = CityPylonUiLayout.navTop(contentTop);
        CityPylonUiLayout.Rect[] tabs =
                CityPylonUiLayout.flow(contentLeft, navTop, CONTENT_WIDTH, RU_LABELS);

        // The second row is the one that actually holds the wrap; find it from the flowed rects.
        int firstTabOfSecondRow = -1;
        for (int i = 1; i < tabs.length; i++) {
            if (tabs[i].y() != tabs[0].y()) {
                firstTabOfSecondRow = i;
                break;
            }
        }
        require(firstTabOfSecondRow >= 0, "the Russian nav really does wrap onto a second row");
        CityPylonUiLayout.Rect secondRowFirstTab = tabs[firstTabOfSecondRow];
        CityPylonUiLayout.Rect secondRowLastTab = tabs[TAB_COUNT - 1];
        require(secondRowLastTab.y() == secondRowFirstTab.y(),
                "the wrapped tabs share the second row");

        int bodyTop = CityPylonUiLayout.bodyTop(contentTop, 2);
        require(secondRowFirstTab.bottom() <= bodyTop,
                "the second tab row ends before the body");
        require(secondRowLastTab.bottom() < bodyTop,
                "BODY_GAP separates the second tab row from the members toolbar");
        require(CityPylonUiLayout.bodyTop(contentTop, tabs) == bodyTop,
                "the body top derived from the flowed rects equals the row-count body top");

        CityPylonMembersLayout.MembersLayout layout =
                CityPylonMembersLayout.compute(
                        contentLeft, contentTop, CONTENT_WIDTH,
                        contentTop + CONTENT_BODY_BOTTOM, 2, FONT_LINE);
        require(layout.toolbar().y() == bodyTop,
                "the members toolbar starts exactly at the body top");
        require(layout.invite().y() > secondRowLastTab.bottom(),
                "the invite widget sits below the second tab row");
    }

    /*
     * ============================================================
     * SOURCE WIRING: the real screen must consume one layout value
     * ============================================================
     */

    private static void checkSourceWiring() {
        String screen = readScreen();

        /* Single layout source, fed the real font line height. */
        require(screen.contains("private CityPylonMembersLayout.MembersLayout membersLayout()"),
                "the screen owns one members-layout source");
        require(screen.contains("CityPylonMembersLayout.compute("),
                "the members layout is derived from the content bounds and the tab rows");
        require(screen.contains("this.font.lineHeight"),
                "the members layout is fed the real font line height");

        /* Render path. */
        require(screen.contains("layout.toolbarLabelY()"),
                "the toolbar label paints from the layout");
        require(screen.contains("layout.listClip()"),
                "renderMembers clips from the layout");
        require(screen.contains("layout.scrollThumb("),
                "the scrollbar paints from the layout");
        require(screen.contains("layout.clampScroll("),
                "the render path clamps the scroll from the layout");
        require(screen.contains("layout.avatar(visibleIndex)"),
                "the avatar paints from the layout");
        require(screen.contains("layout.nicknameY(visibleIndex)"),
                "the nickname line paints from the layout");
        require(screen.contains("layout.metadataY(visibleIndex)"),
                "the metadata line paints from the layout");
        require(screen.contains("layout.gear(visibleIndex)"),
                "the gear paints from the layout");
        require(screen.contains("layout.textX(visibleIndex)")
                        && screen.contains("layout.textRight(visibleIndex)"),
                "the text column is bounded by the layout");
        require(screen.contains("if (maxWidth <= 0) return Component.literal(\"\");"),
                "ellipsize always bounds its result instead of passing narrow budgets through");
        require(!screen.contains("if (maxWidth <= 16) return component;"),
                "the old unbounded ellipsize shortcut is gone");
        require(screen.contains("if (roleWidth >= 8) {"),
                "the role is dropped rather than painted past the gear when the budget is tiny");
        require(screen.contains("CityPylonMembersLayout.SCROLLBAR_GAP"),
                "the wheel reach and the scrollbar come from one layout constant");

        /* Real invite widget takes its bounds from the same layout. */
        require(screen.contains("this.membersLayout().invite()"),
                "the invite widget bounds come from the members layout");
        require(screen.contains("this.inviteButton.setX(")
                        && screen.contains("this.inviteButton.setY(")
                        && screen.contains("this.inviteButton.setWidth(")
                        && screen.contains("this.inviteButton.setHeight("),
                "the invite widget is really repositioned, not just repainted");

        /* Hit paths share the layout. */
        require(screen.contains("CityPylonMembersLayout.MembersLayout layout =")
                        && screen.contains("this.membersLayout();"),
                "the wheel and the gear hit paths read the members layout");
        require(screen.contains("gear.contains(mouseX, mouseY)"),
                "the gear hitbox is the painted gear rectangle");
        require(screen.contains("int visibleIndex,\n            int mouseX,\n            int mouseY\n    ) {"),
                "renderMemberCard receives the interfaceScale-corrected mouse position");
        require(countOccurrences(screen, "renderMemberCard(") == 2,
                "renderMemberCard is declared and called exactly once, and the call passes the mouse");

        /* No second source of geometry left behind. */
        require(!screen.contains("MEMBER_LIST_OFFSET"),
                "the old fixed members offset is gone");
        require(!screen.contains("MEMBER_ROWS"),
                "the old fixed visible-row constant is gone");
        require(!screen.contains("MEMBER_CARD"),
                "the old 48px member-card constants are gone");
        require(!screen.contains("CityInviteUiLayout.memberRow"),
                "the members body no longer borrows the invite panel geometry");
        require(!screen.contains("CityInviteUiLayout.memberClip"),
                "the members clip no longer comes from the invite panel geometry");
        require(!screen.contains("CONTENT_OFFSET_Y + 44"),
                "the old fixed invite Y is gone");
        require(!screen.contains("cardX + 58"),
                "the old fixed member text column is gone");
        require(!screen.contains("MemberCardGeometry"),
                "the old per-card geometry type is gone");
        require(!screen.contains("mouseOverGear"),
                "the old interfaceScale-blind gear hover helper is gone");
        require(!screen.contains("mouseHandler.xpos()"),
                "the members body no longer re-derives the mouse position by hand");

        /*
         * Only one invite CTA can ever be on screen.
         *
         * <p>The screen still contains the retired inline player list ({@code renderInvitePlayers}),
         * which paints its own "ПРИГЛАСИТЬ" per row. It is unreachable: {@code showInvitePlayers} is
         * only ever assigned {@code false} (on tab switch and on the back button), so the overlay
         * can never be shown while the real invite widget lives in the toolbar. The assertion proves
         * that unreachability rather than pretending the old painter is gone.
         */
        require(!Pattern.compile("showInvitePlayers\\s*=\\s*true").matcher(screen).find(),
                "the legacy inline invite overlay can never be opened");
        require(countOccurrences(screen, "this.showInvitePlayers =") == 2,
                "showInvitePlayers is only written where the overlay is being closed: "
                        + countOccurrences(screen, "this.showInvitePlayers ="));
        int inviteCtas = countOccurrences(screen,
                "\"gui.createcityexpansioncore.city.invite\"");
        require(inviteCtas == 2,
                "exactly the real widget and the unreachable legacy overlay mention the CTA key: "
                        + inviteCtas);
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int index = haystack.indexOf(needle);
        while (index >= 0) {
            count++;
            index = haystack.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static String readScreen() {
        try {
            String raw = Files.readString(sourceRoot().resolve(
                    "src/main/java/com/example/examplemod/client/screen/CityPylonScreen.java"));
            // The working copy is CRLF; the wiring assertions are written in LF.
            return raw.replace("\r\n", "\n");
        } catch (Exception e) {
            throw new AssertionError("cannot read CityPylonScreen.java", e);
        }
    }

    /** Tests run with a build/checks working directory, so the source root has to be found by walking up. */
    private static Path sourceRoot() {
        Path root = Paths.get("").toAbsolutePath();
        while (root != null && !Files.isDirectory(root.resolve("src/main/java"))) {
            root = root.getParent();
        }
        if (root == null) {
            throw new AssertionError("cannot locate source tree");
        }
        return root;
    }
}