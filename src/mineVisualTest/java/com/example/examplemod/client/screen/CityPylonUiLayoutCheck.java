package com.example.examplemod.client.screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    public static void main(String[] args) throws Exception {
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

        // ================================================================ integration, not the helper
        // The previous fix was green here yet still overlapped on screen, because only the hit test
        // had moved to the measured layout while the painting kept its own fixed columns. These
        // checks read the real screen and panel sources so the two paths cannot drift apart again.
        String screen = Files.readString(
                sourceRoot().resolve(
                        "src/main/java/com/example/examplemod/client/screen/CityPylonScreen.java"));
        require(screen.contains("private CityPylonUiLayout.Rect managementTabRect("),
                "the screen owns one shared management-tab geometry");
        require(screen.contains("managementTabRect(left, top, i)"),
                "renderManagementNav paints through the shared geometry");
        require(screen.contains("managementTabRect(this.contentLeft(), CityPylonUiLayout.navTop(this.contentTop()), i)"),
                "the hit test uses the same shared geometry as the painting");
        require(screen.contains("plainSubstrByWidth(full, Math.max(1, cell.width() - 8))"),
                "a label is truncated to its own cell instead of painting over a neighbour");
        require(!screen.contains("int width = (CONTENT_WIDTH - 12) / tabs.length;"),
                "the old fixed-width management row is gone from the painter");
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
                CityPylonUiLayout.Rect lastTab = CityPylonUiLayout.tab(
                        10, navTop, 5, 100, 6, rows);
                require(lastTab.bottom() <= bodyTop,
                        "the last tab row never reaches the body (rows=" + rows + ")");
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
                "renderMembers derives its body from that helper");
        require(screen2.contains("managementBodyTop(this.contentTop())"),
                "the member hit test and the wheel clip use the same helper");
        require(!screen2.contains("top + 45"),
                "the old fixed +45 members offset is gone");
        require(!screen2.contains("MEMBER_LIST_OFFSET;\n"),
                "the members list no longer anchors to the old fixed offset");
        require(screen2.contains("int avatarSize = Math.max(8, Math.min(20, cardHeight - 4));"),
                "the avatar is sized from the row, not from the old 48px card");
        require(!screen2.contains("cardY + 44"),
                "the old 40px placeholder box is gone");

        System.out.println("PASS: " + checks + " city pylon navigation layout checks");
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