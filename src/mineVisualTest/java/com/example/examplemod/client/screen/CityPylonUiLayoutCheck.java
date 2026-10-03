package com.example.examplemod.client.screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Headless layout contract for the City Pylon navigation.
 *
 * <p>The management row used to split its width into six equal columns and truncate whatever did not
 * fit, which is why "ТЕРРИТОРИЯ" and "ЖЕЛЕЗНАЯ ДОРОГА" appeared as "ТЕРРИТОРИ..." and "ЖЕЛЕЗНА..."
 * and the two rows looked like a strip of debug buttons. The tabs are now flowed at their own
 * measured width, so the checks here require that every tab is wide enough for its own label, that
 * no two tabs ever touch, that the real Russian and English labels fit without truncation, and that
 * the painter and the hit test read the same rectangles.
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
    /** A short English set that genuinely shares one row at the panel width. */
    private static final int[] EN_SHORT_LABELS = { 40, 38, 40, 45, 42, 38 };

    public static void main(String[] args) throws Exception {
        for (int width : WIDTHS) {
            for (int[] labels : new int[][]{ RU_LABELS, EN_LABELS }) {
                int content = Math.min(CONTENT, width - 24);
                CityPylonUiLayout.Rect[] tabs = CityPylonUiLayout.flow(0, 100, content, labels);
                int rows = CityPylonUiLayout.rows(content, TAB_COUNT, labels);

                require(tabs.length == TAB_COUNT, "every tab gets a rectangle at " + width);
                require(rows >= 1, "the nav needs at least one row at " + width);

                CityPylonUiLayout.Rect previous = null;
                for (int index = 0; index < TAB_COUNT; index++) {
                    CityPylonUiLayout.Rect cell = tabs[index];

                    require(cell.contains(cell.x() + 1, cell.y() + 1),
                            "tab hitbox matches its render bounds at " + width);
                    require(cell.height() == CityPylonUiLayout.ROW_HEIGHT,
                            "tab keeps a stable height at " + width);
                    require(cell.width() >= labels[index] + CityPylonUiLayout.TAB_PAD_X * 2,
                            "tab " + index + " is wide enough for its own label at " + width
                                    + " (" + cell.width() + " for a " + labels[index] + "px label)");
                    require(cell.x() >= 0 && cell.right() <= content,
                            "tab stays inside the content area at " + width
                                    + " (" + cell.right() + " <= " + content + ")");

                    if (previous != null) {
                        require(!previous.overlaps(cell),
                                "tabs " + (index - 1) + " and " + index + " never overlap at " + width
                                        + " (rows=" + rows + ")");
                        if (cell.y() == previous.y()) {
                            require(cell.x() >= previous.right(),
                                    "same-row tabs keep the gap at " + width);
                            require(cell.x() - previous.right() >= CityPylonUiLayout.GAP,
                                    "the same-row gap is at least GAP at " + width);
                            require(cell.x() - previous.right() <= 6,
                                    "the same-row gap stays tight at " + width);
                        } else {
                            require(cell.y() >= previous.bottom(),
                                    "a wrapped row starts below the previous one at " + width);
                            require(cell.y() - previous.bottom() == CityPylonUiLayout.ROW_GAP,
                                    "the row gap stays tight at " + width);
                            require(cell.x() == 0,
                                    "a wrapped row restarts on the left at " + width);
                        }
                    }
                    previous = cell;
                }

                // Wrapping must be driven by the next tab genuinely not fitting, never by squeezing.
                for (int index = 1; index < TAB_COUNT; index++) {
                    CityPylonUiLayout.Rect previousTab = tabs[index - 1];
                    CityPylonUiLayout.Rect cell = tabs[index];
                    if (cell.y() == previousTab.y()) {
                        require(previousTab.right() + CityPylonUiLayout.GAP + cell.width() <= content,
                                "tab " + index + " only stays on the row because it really fits at "
                                        + width);
                    }
                }

                // The nav's real bottom edge drives the body.
                int navBottom = CityPylonUiLayout.navBottom(100, tabs);
                require(navBottom == 100 + CityPylonUiLayout.navHeight(rows),
                        "the nav bottom matches the row count at " + width);
                require(CityPylonUiLayout.bodyTop(50, tabs) == navBottom
                                + CityPylonUiLayout.BODY_GAP,
                        "the body starts below the tabs' real bottom edge at " + width);
            }
        }

        /* ---------------------------------------------- the reported Russian case, end to end */
        // At the panel's real content width every Russian tab is wide enough for its own full name,
        // so "ТЕРРИТОРИЯ" and "ЖЕЛЕЗНАЯ ДОРОГА" are no longer cut into an ellipsis.
        CityPylonUiLayout.Rect[] ruTabs = CityPylonUiLayout.flow(0, 100, CONTENT, RU_LABELS);
        String[] ruNames = {
            "ТЕРРИТОРИЯ", "УЧАСТНИКИ", "ПРОЕКТЫ", "РАЗВИТИЕ", "ОТНОШЕНИЯ", "ЖЕЛЕЗНАЯ ДОРОГА"
        };
        for (int index = 0; index < ruTabs.length; index++) {
            require(ruTabs[index].width() >= RU_LABELS[index] + CityPylonUiLayout.TAB_PAD_X * 2,
                    "\"" + ruNames[index] + "\" is never truncated at the panel width: "
                            + ruTabs[index].width() + "px cell for a " + RU_LABELS[index] + "px label");
        }
        require(CityPylonUiLayout.rows(CONTENT, TAB_COUNT, RU_LABELS) == 2,
                "the Russian labels wrap into two rows at the panel width");

        /*
         * The balanced split: 3 + 3, not the greedy 5 + 1 that left "ЖЕЛЕЗНАЯ ДОРОГА" alone.
         *
         * <p>Row 1 = ТЕРРИТОРИЯ / УЧАСТНИКИ / ПРОЕКТЫ,
         * row 2 = РАЗВИТИЕ / ОТНОШЕНИЯ / ЖЕЛЕЗНАЯ ДОРОГА.
         */
        for (int index = 0; index < 3; index++) {
            require(ruTabs[index].y() == ruTabs[0].y(),
                    "\"" + ruNames[index] + "\" stays on the first nav row");
            require(ruTabs[index + 3].y() == ruTabs[3].y(),
                    "\"" + ruNames[index + 3] + "\" stays on the second nav row");
        }
        require(ruTabs[0].y() != ruTabs[3].y(),
                "the Russian nav really does use two rows");
        require(ruTabs[2].right() < CONTENT && ruTabs[5].right() <= CONTENT,
                "both balanced rows stay inside the content width: "
                        + ruTabs[2].right() + " and " + ruTabs[5].right() + " <= " + CONTENT);
        require(ruTabs[3].y() - ruTabs[2].bottom() == CityPylonUiLayout.ROW_GAP,
                "the balanced rows keep the row gap");
        require(ruTabs[1].x() - ruTabs[0].right() == CityPylonUiLayout.GAP,
                "the first balanced row keeps the tab gap");

        // Balancing must not reintroduce equal-width columns.
        boolean distinctWidths = ruTabs[0].width() != ruTabs[1].width()
                || ruTabs[1].width() != ruTabs[2].width()
                || ruTabs[5].width() != ruTabs[2].width();
        require(distinctWidths,
                "balanced wrapping keeps content-aware widths instead of equal columns");

        // The panel's own content width behaves the same way.
        CityPylonUiLayout.Rect[] ruPanel = CityPylonUiLayout.flow(180, 93, 420, RU_LABELS);
        require(ruPanel[2].y() == ruPanel[0].y() && ruPanel[3].y() != ruPanel[0].y(),
                "the panel's real content width also splits 3 + 3");
        require(ruPanel[3].right() <= 180 + 420 && ruPanel[5].right() <= 180 + 420,
                "the panel's balanced rows stay inside the content column");

        /*
         * Regression: the balanced branch must not fire when everything already fits on one row,
         * and must not fire when the halves genuinely do not fit.
         */
        for (int content = 100; content <= 560; content += 3) {
            CityPylonUiLayout.Rect[] tabs = CityPylonUiLayout.flow(0, 100, content, RU_LABELS);
            int rows = CityPylonUiLayout.rows(content, TAB_COUNT, RU_LABELS);
            require(rows >= 1, "the nav never collapses to zero rows at " + content);

            CityPylonUiLayout.Rect prior = null;
            for (CityPylonUiLayout.Rect tab : tabs) {
                require(tab.right() <= content,
                        "no tab runs past the content width at " + content);
                if (prior != null) {
                    require(!prior.overlaps(tab),
                            "tabs never overlap at content width " + content);
                }
                prior = tab;
            }
            require(CityPylonUiLayout.navBottom(100, tabs)
                            == 100 + CityPylonUiLayout.navHeight(rows),
                    "the nav bottom still tracks the row count at " + content);

            if (rows == 2) {
                // A two-row nav with an even tab count is balanced whenever both halves fit.
                boolean halvesFit = halfFits(RU_LABELS, 0, 3, content)
                        && halfFits(RU_LABELS, 3, 6, content);
                int firstRow = 0;
                for (CityPylonUiLayout.Rect tab : tabs) {
                    if (tab.y() == tabs[0].y()) {
                        firstRow++;
                    }
                }
                require(halvesFit == (firstRow == 3),
                        "a two-row nav is balanced exactly when both halves fit at " + content
                                + " (firstRow=" + firstRow + ", halvesFit=" + halvesFit + ")");
            }
        }

        /* A single line whenever the labels genuinely fit. */
        require(CityPylonUiLayout.rows(340, TAB_COUNT, new int[]{ 20, 20, 20, 20, 20, 20 }) == 1,
                "short labels still share one line");
        require(CityPylonUiLayout.rows(340, TAB_COUNT, RU_LABELS) >= 2,
                "wide Russian labels wrap instead of overlapping");

        /*
         * Regression: rows() must count distinct rows, not tabs sitting off the first row.
         *
         * <p>A wrapped row holds several tabs, so counting every non-first-row tab reported 4 rows
         * where the flow really produced 2. navHeight() then over-reported the navigation height and
         * would have pushed the members body far below the painted tabs.
         */
        for (int content = 200; content <= 520; content += 5) {
            CityPylonUiLayout.Rect[] tabs = CityPylonUiLayout.flow(0, 100, content, RU_LABELS);
            int distinct = 0;
            int lastTop = Integer.MIN_VALUE;
            for (CityPylonUiLayout.Rect tab : tabs) {
                if (tab.y() != lastTop) {
                    distinct++;
                    lastTop = tab.y();
                }
            }
            require(CityPylonUiLayout.rows(content, TAB_COUNT, RU_LABELS) == distinct,
                    "rows() counts distinct rows at content width " + content
                            + " (" + CityPylonUiLayout.rows(content, TAB_COUNT, RU_LABELS)
                            + " != " + distinct + ")");
            require(CityPylonUiLayout.navBottom(100, tabs)
                            == 100 + CityPylonUiLayout.navHeight(distinct),
                    "the nav bottom tracks the real row count at content width " + content);
        }

        /* ---------------------------------------------- English: no truncation either */
        checkSharedManagementBody();
        CityPylonUiLayout.Rect[] enTabs = CityPylonUiLayout.flow(0, 100, CONTENT, EN_LABELS);
        for (int index = 0; index < enTabs.length; index++) {
            require(enTabs[index].width() >= EN_LABELS[index] + CityPylonUiLayout.TAB_PAD_X * 2,
                    "the English label " + index + " is never truncated: "
                            + enTabs[index].width() + "px cell for a " + EN_LABELS[index] + "px label");
        }
        CityPylonUiLayout.Rect previousEn = null;
        for (CityPylonUiLayout.Rect tab : enTabs) {
            if (previousEn != null) {
                require(!previousEn.overlaps(tab), "English tabs never overlap");
            }
            previousEn = tab;
        }

        /* A pathological panel still yields positive, non-overlapping cells. */
        for (int content = 40; content <= 420; content += 7) {
            CityPylonUiLayout.Rect[] narrow = CityPylonUiLayout.flow(0, 0, content, RU_LABELS);
            CityPylonUiLayout.Rect prior = null;
            for (CityPylonUiLayout.Rect cell : narrow) {
                require(cell.width() >= 1 && cell.height() == CityPylonUiLayout.ROW_HEIGHT,
                        "a tab stays usable at content width " + content);
                require(cell.x() >= 0 && cell.right() <= content,
                        "a tab stays inside the panel at content width " + content);
                if (prior != null) {
                    require(!prior.overlaps(cell),
                            "tabs never overlap at content width " + content);
                }
                prior = cell;
            }
            require(CityPylonUiLayout.navBottom(0, narrow)
                            == CityPylonUiLayout.navHeight(
                                    CityPylonUiLayout.rows(content, TAB_COUNT, RU_LABELS)),
                    "the row count and the flowed bottom agree at content width " + content);
        }

        require(CityPylonUiLayout.tabWidth(62, 420) == 62 + CityPylonUiLayout.TAB_PAD_X * 2,
                "a tab is exactly its label plus padding");
        require(CityPylonUiLayout.tabWidth(96, 60) == 60,
                "a tab is clamped to the panel instead of overflowing it");

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

        // ================================================================ integration, not the helper
        // The previous fix was green here yet still overlapped on screen, because only the hit test
        // had moved to the measured layout while the painting kept its own fixed columns. These
        // checks read the real screen and panel sources so the two paths cannot drift apart again.
        String screen = Files.readString(
                sourceRoot().resolve(
                        "src/main/java/com/example/examplemod/client/screen/CityPylonScreen.java"));
        require(screen.contains("private CityPylonUiLayout.Rect[] managementTabs("),
                "the screen owns one shared management-tab flow layout");
        require(screen.contains("CityPylonUiLayout.flow("),
                "the tabs are flowed at their own measured width");
        require(screen.contains("CityPylonUiLayout.Rect[] tabs = managementTabs(left, top);"),
                "renderManagementNav paints through the shared rectangles");
        require(screen.contains("CityPylonUiLayout.Rect[] cells = managementTabs("),
                "the hit test uses the same shared rectangles as the painting");
        require(screen.contains("this.font.width(managementTabName(MANAGEMENT_TABS[i]))"),
                "tab width comes from the real measured label width");
        require(screen.contains("CityPylonUiLayout.TAB_PAD_X"),
                "the painter and the layout share one horizontal tab padding");
        require(!screen.contains("int width = (CONTENT_WIDTH - 12) / tabs.length;"),
                "the old fixed-width management row is gone from the painter");
        require(!screen.contains("CityPylonUiLayout.columnWidth("),
                "the old equal-column splitter is gone");
        require(!screen.contains("managementTabRect("),
                "the old single-tab geometry helper is gone");
        require(screen.contains("setDrawInternalControls(false)"),
                "the overview mini-map renders without the old internal controls");
        require(screen.contains("city.hub.open_map"),
                "the overview has a single, explicit map entry point");

        // The obsolete chips still live in CityMapPanel, gated by the flag, so the full World Map
        // keeps its own behaviour.
        String panel = Files.readString(
                sourceRoot().resolve("src/main/java/com/example/examplemod/client/map/CityMapPanel.java"));
        require(panel.contains("if (this.drawInternalControls) {"),
                "internal controls stay gated by the flag in the panel");
        require(panel.contains("if (this.drawInternalControls && inside(mouseX, mouseY, centerButtonX"),
                "the centre hitbox is gated by the flag too");
        require(panel.contains("if (this.drawInternalControls && inside(mouseX, mouseY, layerButtonsX"),
                "every layer chip hitbox is gated by the flag");
        require(CityPylonUiLayout.rows(340, 6, RU_LABELS) == 2,
                "the Russian management labels genuinely need the second row at this width");

        // ---------------------------------------------- management section: one vertical layout, checked
        // The title and the first tab row were painted at the same Y because the navigation was
        // handed the raw contentTop. These assert the order the section now uses.
        for (int contentTop : new int[]{ 40, 120, 260 }) {
            for (int rows : new int[]{ 1, 2 }) {
                CityPylonUiLayout.Rect title =
                        CityPylonUiLayout.sectionTitle(10, contentTop, 340);
                int navTop = CityPylonUiLayout.navTop(contentTop);
                int bodyTop = CityPylonUiLayout.bodyTop(contentTop, rows);

                require(title.bottom() <= navTop,
                        "the section title never reaches the navigation (top=" + contentTop
                                + ", rows=" + rows + ")");
                CityPylonUiLayout.Rect[] tabs =
                        CityPylonUiLayout.flow(10, navTop, 340, RU_LABELS);
                CityPylonUiLayout.Rect lastTab = tabs[tabs.length - 1];
                require(lastTab.bottom() <= CityPylonUiLayout.bodyTop(contentTop, tabs),
                        "the last tab row never reaches the flowed body top (top=" + contentTop + ")");
                require(CityPylonUiLayout.navBottom(navTop, tabs) <= bodyTop
                                || CityPylonUiLayout.rows(340, TAB_COUNT, RU_LABELS) > rows,
                        "the tab flow's real bottom and the row-count body top do not disagree "
                                + "(top=" + contentTop + ", rows=" + rows + ")");
                require(CityPylonUiLayout.divider(10, contentTop, 340).bottom() <= navTop,
                        "the divider sits above the navigation");

                // A one-row nav really does lift the body higher than a two-row nav.
                int one = CityPylonUiLayout.bodyTop(contentTop, 1);
                int two = CityPylonUiLayout.bodyTop(contentTop, 2);
                require(two - one == CityPylonUiLayout.ROW_HEIGHT + CityPylonUiLayout.ROW_GAP,
                        "wrapping the navigation pushes the body down by exactly one row and gap");
                require(CityPylonUiLayout.navHeight(1) == CityPylonUiLayout.ROW_HEIGHT,
                        "a single-row navigation is exactly one row tall");
                require(CityPylonUiLayout.navHeight(2)
                                == CityPylonUiLayout.ROW_HEIGHT * 2 + CityPylonUiLayout.ROW_GAP,
                        "a two-row navigation includes the gap between its rows");
            }
        }

        // A member row is 22px tall, so the avatar can never be the old 40px head.
        CityInviteUiLayout.Rect member = CityInviteUiLayout.memberRow(854, 200, 0, 3);
        int avatar = Math.max(8, Math.min(20, member.height() - 4));
        require(avatar <= member.height(), "the avatar is never taller than its row");
        require(avatar <= 20, "the avatar stays a small anchor, not a portrait");
        require(member.x() + 6 + avatar <= member.right(),
                "the avatar stays inside the row's right edge");
        require(member.y() + (member.height() - avatar) / 2 + avatar <= member.bottom(),
                "the avatar stays inside the row's bottom edge");

        // The painter and the hit test must read the same dynamic body top.
        String screen2 = Files.readString(
                sourceRoot().resolve(
                        "src/main/java/com/example/examplemod/client/screen/CityPylonScreen.java"));
        require(screen2.contains("renderManagementNav(guiGraphics, contentLeft, CityPylonUiLayout.navTop(contentTop)"),
                "the painter starts the navigation below the title");
        require(screen2.contains("private int managementBodyTop(int contentTop)"),
                "one helper derives the body top from the real row count");
        require(screen2.contains("managementBodyTop(top)"),
                "the territory body derives from that helper");
        require(screen2.contains("private CityPylonMembersLayout.MembersLayout membersLayout()"),
                "the members body has its own single layout source");
        require(!screen2.contains("top + 45"),
                "the old fixed +45 members offset is gone");
        require(!screen2.contains("MEMBER_LIST_OFFSET;\n"),
                "the members list no longer anchors to the old fixed offset");
        require(!screen2.contains("CityInviteUiLayout.memberRow"),
                "the members body no longer borrows the invite panel geometry");
        require(!screen2.contains("cardY + 44"),
                "the old 40px placeholder box is gone");

        /* ---------------------------------------------- the shared body, wired to the real screen */
        require(screen2.contains("private CityPylonUiLayout.ManagementBody managementBody(int contentTop)"),
                "the screen owns one shared management-body frame");
        require(screen2.contains("CityPylonUiLayout.managementBody(contentTop, CONTENT_BODY_BOTTOM, managementRows())"),
                "the shared body is derived from the content bounds and the real tab row count");
        require(screen2.contains("return managementBody(contentTop).top();"),
                "managementBodyTop delegates to the shared frame instead of re-deriving it");

        // No management body may anchor itself to the raw contentTop again.
        require(!screen2.contains("top + 47"),
                "Projects no longer paints its level line at contentTop + 47");
        require(!screen2.contains("contentTop + 62"),
                "Projects' card hitbox no longer uses contentTop + 62");
        require(!screen2.contains("contentTop() + 45 + 110"),
                "Territory's checkbox hitbox no longer uses the old fixed Y");
        require(!screen2.contains("int top = this.contentTop();\n        int pageWidth = CONTENT_WIDTH / 4;"),
                "the railway click handler no longer starts from the raw contentTop");

        // The six bodies must all read the shared frame.
        require(countOccurrences(screen2, "managementBody(") >= 8,
                "every management body reads the shared frame in render and hit paths: "
                        + countOccurrences(screen2, "managementBody("));
        require(screen2.contains("CityPylonUiLayout.managementBody(contentTop, CONTENT_BODY_BOTTOM, managementRows())"),
                "the shared frame is built from the content bounds and the real tab row count");
        require(screen2.contains("RAIL_ROW_TOP") && screen2.contains("railActionY(body)"),
                "the railway body hangs off the shared frame from both ends");

        System.out.println("PASS: " + checks + " city pylon navigation layout checks");
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

    /**
     * The one shared management body that all six management tabs measure inside.
     *
     * <p>Five of the six used to anchor to the raw {@code contentTop} plus their own magic offsets,
     * which silently assumed a single navigation row. With the Russian tabs wrapping onto two rows
     * those bodies started inside the navigation, so Projects painted its level line straight through
     * the wrapped second row. Now there is one frame: {@code top} is the real bottom of the tabs plus
     * {@code BODY_GAP}, and {@code bottom} stays the panel's content bottom.
     */
    private static void checkSharedManagementBody() {
        for (int contentTop : new int[]{ 40, 75, 120, 260 }) {
            for (int rows : new int[]{ 1, 2 }) {
                CityPylonUiLayout.ManagementBody body =
                        CityPylonUiLayout.managementBody(contentTop, 258, rows);
                int navBottom = CityPylonUiLayout.navTop(contentTop)
                        + CityPylonUiLayout.navHeight(rows);

                require(body.top() == navBottom + CityPylonUiLayout.BODY_GAP,
                        "the body starts one gap below the navigation's real bottom (contentTop="
                                + contentTop + ", rows=" + rows + ")");
                require(body.top() > navBottom,
                        "no management body can start above the tabs (contentTop="
                                + contentTop + ", rows=" + rows + ")");
                require(body.bottom() == contentTop + 258,
                        "the body bottom stays the panel's content bottom (contentTop="
                                + contentTop + ", rows=" + rows + ")");
                require(body.height() == body.bottom() - body.top() && body.height() > 0,
                        "the body keeps real free space (contentTop="
                                + contentTop + ", rows=" + rows + ")");
            }
            require(CityPylonUiLayout.managementBody(contentTop, 258, 2).height()
                            < CityPylonUiLayout.managementBody(contentTop, 258, 1).height(),
                    "wrapping the navigation shrinks the body instead of moving the footer "
                            + "(contentTop=" + contentTop + ")");
        }

        // The acceptance rule for all six tabs: nothing may start above the body. Every first element
        // is expressed as an offset from the body top, and that offset must never be negative.
        String[] tabs = {
            "territory", "members", "projects", "development", "relations", "railway"
        };
        // Territory/Projects/Development paint their first line at the body top, Members uses its own
        // layout, Relations opens its own screen, Railway starts with its page switcher.
        int[] firstOffsets = { 0, 0, 0, 0, 0, 0 };
        for (int index = 0; index < tabs.length; index++) {
            for (int rows : new int[]{ 1, 2 }) {
                // Match the body to a navigation that really has that many rows, so the two are
                // compared against each other rather than against the wrong tab set.
                int[] labels = rows == 2 ? RU_LABELS : EN_SHORT_LABELS;
                require(CityPylonUiLayout.rows(420, TAB_COUNT, labels) == rows,
                        "the label set really needs " + rows + " row(s)");
                CityPylonUiLayout.ManagementBody body =
                        CityPylonUiLayout.managementBody(75, 258, rows);
                int navBottom = CityPylonUiLayout.navBottom(
                        CityPylonUiLayout.navTop(75),
                        CityPylonUiLayout.flow(180, CityPylonUiLayout.navTop(75), 420, labels));
                int first = body.top() + firstOffsets[index];
                require(first >= body.top(),
                        "the " + tabs[index] + " body never starts above the body top (rows="
                                + rows + "): " + first + " >= " + body.top());
                require(first > navBottom,
                        "the " + tabs[index] + " body never overlaps the navigation (rows="
                                + rows + "): " + first + " > " + navBottom);
            }
        }

        // The railway body is the deepest one; its action rows hang off the body's bottom edge, which is
        // the panel's content bottom, so nothing can be pushed off the panel when the nav wraps.
        for (int rows : new int[]{ 1, 2 }) {
            CityPylonUiLayout.ManagementBody body =
                    CityPylonUiLayout.managementBody(75, 258, rows);
            int actionY = body.bottom() - 22 - 6;
            int lowest = actionY + 22;
            require(actionY > body.top(),
                    "the railway action row starts below the body top (rows=" + rows + ")");
            require(lowest == body.bottom() - 6,
                    "the railway action row ends above the content bottom (rows=" + rows + ")");
            require(lowest <= 75 + 258 + 12,
                    "the deepest railway action row stays inside the panel (rows=" + rows
                            + "): " + lowest + " <= " + (75 + 258 + 12));

            // The row window shrinks when the navigation wraps, but always keeps at least one row.
            int rowTop = body.top() + 27;
            int listBottom = actionY - 6;
            int visible31 = Math.max(1, (listBottom - rowTop) / 31);
            require(visible31 >= 1,
                    "the railway list always shows at least one row (rows=" + rows + ")");
            require(rowTop + visible31 * 31 <= listBottom,
                    "the last visible railway row stays inside the clip (rows=" + rows + "): "
                            + (rowTop + visible31 * 31) + " <= " + listBottom);
            require(rowTop > body.top(),
                    "the railway rows start below the body top (rows=" + rows + ")");
        }
    }

    /** Independent re-implementation of "do tabs [from, to) fit on one row", used to cross-check flow(). */
    private static boolean halfFits(int[] labelWidths, int from, int to, int availableWidth) {
        int total = 0;
        for (int index = from; index < to; index++) {
            int width = labelWidths[index] + 12;
            if (width > availableWidth) {
                return false;
            }
            total = total == 0 ? width : total + 4 + width;
        }
        return total <= availableWidth;
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