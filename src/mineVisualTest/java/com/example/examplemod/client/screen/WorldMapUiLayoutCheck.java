package com.example.examplemod.client.screen;

import com.example.examplemod.client.map.CityMapPanel;

/**
 * Headless layout contract for the World Map chrome.
 *
 * <p>The point of this check is that render bounds and click bounds cannot disagree: both come from
 * {@link WorldMapUiLayout}, so a control that is painted is by construction the control that is
 * clickable. Everything here is checked at the geometry level, across the responsive widths the
 * screen has to survive, without a client.
 */
public final class WorldMapUiLayoutCheck {
    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static final int SCREEN_HEIGHT = 380;

    /** Realistic measured widths: a short mode name and the longest global one, per language. */
    private static int[] modeLabels() { return new int[] { 38, 75, 50, 58 }; }
    private static int[] globalLabels() { return new int[] { 40, 44, 52, 50, 64, 72, 30, 50 }; }

    public static void main(String[] args) {
        require(CityMapClaimAccess.allowed(true,true,true,false,false,false),"root enables claim");
        require(CityMapClaimAccess.allowed(true,true,false,true,false,false),"workspace enables claim");
        require(CityMapClaimAccess.allowed(true,true,false,false,true,false),"own management atlas enables claim");
        require(!CityMapClaimAccess.allowed(true,true,false,false,true,true),"read-only atlas disables claim");
        require(!CityMapClaimAccess.allowed(true,true,false,false,false,false),"unrelated screen disables claim");
        require(!CityMapClaimAccess.allowed(true,false,false,true,false,false),"resident cannot claim");
        require(!CityMapClaimAccess.allowed(false,true,false,true,false,false),"unfounded city cannot claim");
        for (int width : new int[]{320, 426, 640, 960}) for (int height : new int[]{240, 360, 540}) {
            var founding = CityFoundingLayout.compute(width,height);
            for (var rect : new WorldMapUiLayout.Rect[]{founding.panel(),founding.map(),founding.name(),founding.back(),founding.submit()}) {
                require(WorldMapUiLayout.withinScreen(width,height,rect),"founding bounds stay on screen");
                require(rect.width()>0&&rect.height()>0,"founding has usable bounds");
            }
            require(!founding.map().overlaps(founding.name()),"map never covers the name field");
            require(!founding.back().overlaps(founding.submit()),"founding actions do not overlap");
            require(founding.map().bottom()+14<founding.panel().bottom()-51,"map caption clears footer");
            require(founding.name().bottom()+30<founding.panel().bottom()-51,"founding summary clears footer");
        }
        int[] modes = modeLabels();
        int[] globals = globalLabels();

        // ------------------------------------------------- mode tabs never overlap each other
        for (int width : WorldMapUiLayout.RESPONSIVE_WIDTHS) {
            WorldMapUiLayout.Rect previous = null;
            for (int index = 0; index < WorldMapUiLayout.MODE_COUNT; index++) {
                WorldMapUiLayout.Rect tab = WorldMapUiLayout.modeTab(width, index, modes);
                require(WorldMapUiLayout.withinScreen(width, SCREEN_HEIGHT, tab),
                        "mode tab " + index + " stays on screen at " + width);
                require(tab.contains(tab.x() + 1, tab.y() + 1),
                        "mode tab hitbox matches its render bounds at " + width);
                require(tab.height() == WorldMapUiLayout.TAB_HEIGHT,
                        "every mode tab shares one height at " + width);
                if (previous != null) {
                    require(!previous.overlaps(tab), "mode tabs do not overlap at " + width);
                    require(tab.x() >= previous.right(), "mode tabs are laid out in reading order at " + width);
                }
                previous = tab;
            }
            WorldMapUiLayout.Rect tabs = WorldMapUiLayout.modeTabs(width, modes);
            require(tabs.right() == previous.right(),
                    "the tabs plate ends exactly where the last tab ends at " + width);
            require(tabs.y() == WorldMapUiLayout.EDGE, "the mode tabs sit on the top edge at " + width);
        }

        // ------------------------------------------- global controls never overlap or collide
        for (int width : WorldMapUiLayout.RESPONSIVE_WIDTHS) {
            WorldMapUiLayout.Rect previous = null;
            for (int index = 0; index < WorldMapUiLayout.GLOBAL_ACTIONS; index++) {
                WorldMapUiLayout.Rect button = WorldMapUiLayout.globalButton(width, index, modes, globals);
                require(WorldMapUiLayout.withinScreen(width, SCREEN_HEIGHT, button),
                        "global action " + index + " stays on screen at " + width);
                require(button.contains(button.x() + 1, button.y() + 1),
                        "global action hitbox matches its render bounds at " + width);
                require(button.height() == WorldMapUiLayout.BUTTON_HEIGHT,
                        "every global action shares one height at " + width);
                if (previous != null) {
                    require(!previous.overlaps(button), "global actions do not overlap at " + width);
                    require(button.x() >= previous.right(), "global actions keep a gap at " + width);
                }
                previous = button;
            }
            WorldMapUiLayout.Rect plate = WorldMapUiLayout.globalToolbar(width, modes, globals);
            require(plate.right() == previous.right(),
                    "the toolbar plate ends where its last button ends at " + width
                            + " (plate " + plate + " vs last " + previous + ")");
            require(!WorldMapUiLayout.globalToolbarOverlapsTabs(width, modes, globals),
                    "the global toolbar never covers the mode tabs at " + width);
            require(!WorldMapUiLayout.globalToolbarOverlapsTabs(width, modes, new int[8]),
                    "a zero-width label set still clears the mode tabs at " + width);
        }

        // ------------------------------- the toolbar drops to a second row instead of colliding
        require(WorldMapUiLayout.globalToolbarSecondRow(320, modes, globals),
                "320 px cannot fit the toolbar beside the tabs, so it stacks");
        require(WorldMapUiLayout.globalToolbarY(320, modes, globals) > WorldMapUiLayout.EDGE,
                "the stacked toolbar leaves the tab row alone at 320");
        require(WorldMapUiLayout.globalToolbarY(1280, modes, globals) == WorldMapUiLayout.EDGE,
                "a wide screen keeps the toolbar in the header");
        require(WorldMapUiLayout.globalToolbarY(1280, modes, globals)
                        == WorldMapUiLayout.modeTabs(1280, modes).y(),
                "toolbar and tabs share the header row on a wide screen");
        require(WorldMapUiLayout.globalToolbarGap(1280, modes, globals) == WorldMapUiLayout.NORMAL,
                "a wide screen keeps the full spacing step");
        // Below the design widths the gap is what gives way, and the row still has to fit.
        int[] tiny = { 240, 260 };
        for (int width : tiny) {
            int gap = WorldMapUiLayout.globalToolbarGap(width, modes, globals);
            require(gap < WorldMapUiLayout.NORMAL,
                    "the gap tightens at " + width + " instead of running off the edge");
            require(WorldMapUiLayout.globalToolbar(width, modes, globals).right() <= width,
                    "the toolbar still fits at " + width);
            for (int index = 0; index < WorldMapUiLayout.GLOBAL_ACTIONS; index++) {
                require(WorldMapUiLayout.withinScreen(width, SCREEN_HEIGHT,
                                WorldMapUiLayout.globalButton(width, index, modes, globals)),
                        "global action " + index + " stays on screen even at " + width);
            }
        }
        for (int width : WorldMapUiLayout.RESPONSIVE_WIDTHS) {
            require(WorldMapUiLayout.globalToolbarGap(width, modes, globals) >= 0,
                    "the gap never goes negative at " + width);
        }

        // ------------------------------------------------- labels, and the disabled Electricity tab
        require(WorldMapUiLayout.modeTabsLabelled(1280), "a wide screen labels its mode tabs");
        require(WorldMapUiLayout.modeTabsLabelled(854),
                "854 px prioritises readable mode names over utility labels");
        require(WorldMapUiLayout.globalToolbarLabelled(1280, modes, globals),
                "a wide screen labels the global toolbar");
        require(!WorldMapUiLayout.globalToolbarLabelled(854, modes, globals),
                "a labelled toolbar is refused when it would not fit");
        require(WorldMapUiLayout.modeTabWidth(1280, 75) == WorldMapUiLayout.modeTabWidth(854, 75),
                "mode names stay readable at normal GUI sizes");
        require(WorldMapUiLayout.modeTabWidth(1280, 75) > 75,
                "a labelled mode tab actually reserves room for its label");
        require(WorldMapUiLayout.modeTabWidth(426, 75) == WorldMapUiLayout.TAB_HEIGHT,
                "a compact mode tab is exactly its icon, never a truncated label");
        // Per-item sizing: a short label does not inherit the widest label's padding.
        require(WorldMapUiLayout.modeTabWidth(1280, 38) < WorldMapUiLayout.modeTabWidth(1280, 75),
                "a short mode name gets a narrower tab than a long one at 1280");
        require(WorldMapUiLayout.globalButtonWidth(1280, modes, globals, WorldMapUiLayout.GLOBAL_HELP)
                        < WorldMapUiLayout.globalButtonWidth(1280, modes, globals, WorldMapUiLayout.GLOBAL_PROBLEMS),
                "a short global action is narrower than a long one at 1280");
        require(WorldMapUiLayout.globalButtonWidth(854, modes, globals, 0) == WorldMapUiLayout.compactWidth(),
                "a compact global action falls back to its glyph instead of truncating the label");
        require(WorldMapUiLayout.MODE_COUNT == 4, "all four modes exist, including the disabled one");
        require(WorldMapUiLayout.MODE_ENERGY == WorldMapUiLayout.MODE_COUNT - 1,
                "the disabled mode keeps its slot instead of vanishing");
        require(WorldMapUiLayout.GLOBAL_ACTIONS == 8,
                "the toolbar holds exactly the global actions, no mode-specific extras");
        require(WorldMapUiLayout.GLOBAL_CLOSE == WorldMapUiLayout.GLOBAL_ACTIONS - 1,
                "Close is the last global action");

        // ------------------------------------------------------ context panel is never off-screen
        for (int width : WorldMapUiLayout.RESPONSIVE_WIDTHS) {
            WorldMapUiLayout.Rect panel = WorldMapUiLayout.contextPanel(width, SCREEN_HEIGHT, modes, globals, 4000);
            require(WorldMapUiLayout.withinScreen(width, SCREEN_HEIGHT, panel),
                    "the context panel is clamped to the screen at " + width);
            require(panel.right() <= width - WorldMapUiLayout.EDGE,
                    "the context panel keeps its margin at " + width);
            require(panel.x() >= WorldMapUiLayout.contextPanelLeft(width) - 1,
                    "the panel column is the one the layout reports at " + width);
            require(!panel.overlaps(WorldMapUiLayout.modeTabs(width, modes)),
                    "the context panel never covers the mode tabs at " + width);
            require(!panel.overlaps(WorldMapUiLayout.globalToolbar(width, modes, globals)),
                    "the context panel never covers the global toolbar at " + width);
            require(panel.height() <= SCREEN_HEIGHT,
                    "an oversized panel is clamped instead of overflowing at " + width);
            require(WorldMapUiLayout.contextPanel(width, SCREEN_HEIGHT, modes, globals, 10).height() == 10,
                    "a short panel keeps its content height at " + width);
            require(WorldMapUiLayout.contextPanel(width, SCREEN_HEIGHT, modes, globals, 0).height() == 0,
                    "an absent panel collapses to nothing at " + width);
        }

        // ----------------------------------------------------- popovers stay under the toolbar
        for (int width : WorldMapUiLayout.RESPONSIVE_WIDTHS) {
            WorldMapUiLayout.Rect layers = WorldMapUiLayout.layersPopover(width, modes, globals, 8, 214);
            WorldMapUiLayout.Rect search = WorldMapUiLayout.searchPopover(width, modes, globals, 5, 228);
            require(WorldMapUiLayout.withinScreen(width, SCREEN_HEIGHT, layers),
                    "the layers popover stays on screen at " + width);
            require(WorldMapUiLayout.withinScreen(width, SCREEN_HEIGHT, search),
                    "the search popover stays on screen at " + width);
            require(layers.y() >= WorldMapUiLayout.popoverY(width, modes, globals),
                    "popovers hang below the toolbar at " + width);
            for (int row = 0; row < 8; row++) {
                WorldMapUiLayout.Rect bounds = WorldMapUiLayout.layersRow(width, modes, globals, row, 214);
                require(bounds.contains(bounds.x() + 1, bounds.y() + 1),
                        "a layer row hitbox matches its render bounds at " + width);
                require(bounds.bottom() <= layers.bottom(),
                        "a layer row stays inside the popover at " + width);
                require(!bounds.overlaps(WorldMapUiLayout.layersRow(width, modes, globals, row + 1, 214)),
                        "layer rows do not overlap each other at " + width);
            }
            for (int row = 0; row < 5; row++) {
                WorldMapUiLayout.Rect bounds = WorldMapUiLayout.searchRow(width, modes, globals, row, 228);
                require(bounds.contains(bounds.x() + 1, bounds.y() + 1),
                        "a search row hitbox matches its render bounds at " + width);
                require(bounds.bottom() <= search.bottom(),
                        "a search row stays inside the popover at " + width);
            }
        }

        // ---------------------------------------------------------------- modals are centred, clamped
        for (int width : WorldMapUiLayout.RESPONSIVE_WIDTHS) {
            WorldMapUiLayout.Rect problem = WorldMapUiLayout.problemModal(width, SCREEN_HEIGHT);
            WorldMapUiLayout.Rect guide = WorldMapUiLayout.guideModal(width, SCREEN_HEIGHT);
            require(WorldMapUiLayout.withinScreen(width, SCREEN_HEIGHT, problem),
                    "the problem card fits at " + width);
            require(WorldMapUiLayout.withinScreen(width, SCREEN_HEIGHT, guide),
                    "the guide card fits at " + width);
            require(problem.x() + problem.width() / 2 == width / 2,
                    "the problem card is centred at " + width);
            require(problem.bottom() <= SCREEN_HEIGHT, "the problem card clears the status strip at " + width);
            require(problem.height() < SCREEN_HEIGHT,
                    "the problem card is a card, not a full-screen takeover at " + width);
        }

        // ------------------------------------------------- status strip and spacing scale discipline
        for (int width : WorldMapUiLayout.RESPONSIVE_WIDTHS) {
            WorldMapUiLayout.Rect strip = WorldMapUiLayout.statusStrip(width, SCREEN_HEIGHT);
            require(strip.bottom() <= SCREEN_HEIGHT, "the status strip stays on screen at " + width);
            require(strip.x() >= WorldMapUiLayout.EDGE, "the status strip keeps its margin at " + width);
            require(WorldMapUiLayout.statusStrip(width, 0).bottom() <= SCREEN_HEIGHT,
                    "the status strip survives a zero-height screen at " + width);
        }
        require(WorldMapUiLayout.MICRO < WorldMapUiLayout.NORMAL
                        && WorldMapUiLayout.NORMAL < WorldMapUiLayout.SECTION
                        && WorldMapUiLayout.SECTION < WorldMapUiLayout.LARGE,
                "the spacing scale is strictly increasing");
        require(WorldMapUiLayout.EDGE == WorldMapUiLayout.NORMAL, "the screen edge uses the normal step");

        // ---------------------------------------- railway classes are distinguishable, not just coloured
        CityMapPanel.RailClass own = CityMapPanel.classifyRail(true, true, 0L, true);
        CityMapPanel.RailClass free = CityMapPanel.classifyRail(false, true, 0L, true);
        CityMapPanel.RailClass paid = CityMapPanel.classifyRail(false, true, 25L, true);
        CityMapPanel.RailClass priv = CityMapPanel.classifyRail(false, false, 0L, true);
        CityMapPanel.RailClass offline = CityMapPanel.classifyRail(true, true, 25L, false);
        require(own == CityMapPanel.RailClass.OWN, "our own network is recognised as ours");
        require(free == CityMapPanel.RailClass.PUBLIC_FREE, "a foreign network without a tariff is free public");
        require(paid == CityMapPanel.RailClass.PUBLIC_PAID, "a foreign network with a tariff is paid public");
        require(priv == CityMapPanel.RailClass.PRIVATE, "an inaccessible network stays private");
        require(offline == CityMapPanel.RailClass.OFFLINE,
                "an offline section is reported as offline whatever its access is");
        require(CityMapPanel.classifyRail(true, true, 99L, true) == CityMapPanel.RailClass.OWN,
                "our own network stays ours even if it charges a tariff");
        require(CityMapPanel.classifyRail(false, true, 1L, true) == CityMapPanel.RailClass.PUBLIC_PAID,
                "any positive tariff counts as paid");

        // Every class is separable by width as well as by colour, so the map survives greyscale.
        require(CityMapPanel.railInnerWidth(paid) > CityMapPanel.railInnerWidth(free),
                "paid track is heavier than free track");
        require(CityMapPanel.railInnerWidth(own) >= CityMapPanel.railInnerWidth(free),
                "our own track is at least as heavy as free public track");
        require(CityMapPanel.railInnerWidth(priv) < CityMapPanel.railInnerWidth(free),
                "private track is the lightest, never louder than a usable line");
        require(CityMapPanel.railInnerWidth(paid) > CityMapPanel.railInnerWidth(priv),
                "paid track is clearly heavier than private track");
        int[] colors = { CityMapPanel.railInnerColor(own), CityMapPanel.railInnerColor(free),
                CityMapPanel.railInnerColor(paid), CityMapPanel.railInnerColor(priv) };
        for (int index = 0; index < colors.length; index++) {
            for (int other = index + 1; other < colors.length; other++) {
                require(colors[index] != colors[other],
                        "rail classes " + index + " and " + other + " use different colours");
            }
            require((colors[index] >>> 24) == 0xFF, "rail class " + index + " is opaque so it reads on terrain");
        }
        for (CityMapPanel.RailClass kind : CityMapPanel.RailClass.values()) {
            require((CityMapPanel.railOutlineColor(kind) >>> 24) >= 0xE0,
                    "every rail class paints an opaque outline under the line");
            require(CityMapPanel.railInnerWidth(kind) >= 1, "every rail class keeps a visible inner line");
        }

        System.out.println("PASS: " + checks + " City hub / World Map responsive layout checks");
    }
}
