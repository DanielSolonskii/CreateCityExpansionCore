package com.example.examplemod.client.screen;

/**
 * The single source of geometry for the World Map chrome.
 *
 * <p>Every rectangle the player can see or click is produced here, so rendering, hit-testing, hover
 * and tooltips can never disagree about where a control is â€” the previous layout computed some
 * coordinates inline in {@code render()} and separately in {@code mouseClicked()}, which is exactly
 * how a toolbar ends up with dead corners.
 *
 * <p>Pure integers on purpose: the headless layout check drives these functions at the responsive
 * widths the design has to survive (320/426/640/854/1280) without a Minecraft client. Measured label
 * widths are passed in because measuring glyphs needs a font; the geometry around them does not.
 *
 * <p><b>Controls are sized per item</b>, not to the widest label. A row of four equal tabs wastes the
 * difference between "Ð“Ð¾Ñ€Ð¾Ð´Ð°" and "Ð–ÐµÐ»ÐµÐ·Ð½Ñ‹Ðµ Ð´Ð¾Ñ€Ð¾Ð³Ð¸", and that wasted space is exactly what the global
 * toolbar needs in order to stay labelled instead of collapsing to icons.
 *
 * <p><b>Responsive ladder.</b> There is not room for labelled tabs and a labelled toolbar below
     * roughly 560 px; secondary utilities use compact icons before mode labels are sacrificed:
 * <ol>
 *   <li>mode tabs are labelled only on a wide screen â€” a mode you cannot read is the exact problem
 *       this pass exists to fix;</li>
 *   <li>the global toolbar is labelled only when the labelled toolbar fits beside the tabs that are
 *       actually shown; otherwise it keeps its glyphs and its tooltips;</li>
 *   <li>the gap between global actions shrinks before anything is dropped, because a toolbar that
 *       runs off the edge is worse than a slightly tighter one;</li>
 *   <li>if even the icon-only toolbar does not fit beside the tabs, it drops to a second row â€” it
 *       never overlaps them.</li>
 * </ol>
 */
final class WorldMapUiLayout {
    private WorldMapUiLayout() {}

    /** One spacing scale for the whole screen: micro / normal / section / large. */
    static final int MICRO = 4;
    static final int NORMAL = 8;
    static final int SECTION = 12;
    static final int LARGE = 16;

    static final int TAB_HEIGHT = 32;
    static final int BUTTON_HEIGHT = 26;
    static final int POPOVER_ROW = 20;
    static final int POPOVER_HEADER = 25;
    static final int EDGE = NORMAL;

    /** Icon slot plus the padding a labelled control needs around its text. */
    private static final int ICON = 16;
    private static final int LABEL_GAP = MICRO;
    private static final int LABEL_PADDING = 8;

    /** Global actions, in reading order: navigation, view, help, close. */
    static final int GLOBAL_BACK = 0;
    static final int GLOBAL_FORWARD = 1;
    static final int GLOBAL_CENTER_CITY = 2;
    static final int GLOBAL_SEARCH = 3;
    static final int GLOBAL_LAYERS = 4;
    static final int GLOBAL_PROBLEMS = 5;
    static final int GLOBAL_HELP = 6;
    static final int GLOBAL_CLOSE = 7;
    static final int GLOBAL_ACTIONS = 8;

    static final int MODE_CITIES = 0;
    static final int MODE_RAILWAY = 1;
    static final int MODE_LOGISTICS = 2;
    static final int MODE_ENERGY = 3;
    static final int MODE_COUNT = 4;

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

    // ------------------------------------------------------------ responsive decisions

    /** Mode names have priority over secondary utility labels. */
    static boolean modeTabsLabelled(int screenWidth) { return screenWidth >= 560; }

    /** A compact control is its glyph and nothing else â€” no padding spent on an absent label. */
    static int compactWidth() { return BUTTON_HEIGHT; }

    static int labelledWidth(int labelWidth) {
        return ICON + LABEL_GAP + Math.max(0, labelWidth) + LABEL_PADDING;
    }

    // ------------------------------------------------------------ identity plate (top-left)

    static int titleWidth(int screenWidth) { return screenWidth < 560 ? 32 : 122; }

    static Rect title(int screenWidth) {
        return new Rect(EDGE, EDGE, titleWidth(screenWidth), TAB_HEIGHT);
    }

    // ------------------------------------------------------------ mode tabs

    static int modeTabsX(int screenWidth) {
        return EDGE + titleWidth(screenWidth) + NORMAL;
    }

    static int modeTabWidth(int screenWidth, int labelWidth) {
        return modeTabsLabelled(screenWidth) ? labelledWidth(labelWidth) + MICRO : TAB_HEIGHT;
    }

    static Rect modeTab(int screenWidth, int index, int[] labelWidths) {
        int width = modeTabWidth(screenWidth, labelWidths[index]);
        return new Rect(modeTabsX(screenWidth) + modeTabOffset(screenWidth, labelWidths, index),
                EDGE, width, TAB_HEIGHT);
    }

    /** Sum of the individual tab widths; each tab keeps only the space its own label needs. */
    static int modeTabsWidth(int screenWidth, int[] labelWidths) {
        int total = 0;
        for (int index = 0; index < MODE_COUNT; index++) total += modeTabWidth(screenWidth, labelWidths[index]) + (index == 0 ? 0 : MICRO);
        return total;
    }

    static Rect modeTabs(int screenWidth, int[] labelWidths) {
        return new Rect(modeTabsX(screenWidth), EDGE, modeTabsWidth(screenWidth, labelWidths), TAB_HEIGHT);
    }

    static int modeTabsRight(int screenWidth, int[] labelWidths) {
        return modeTabs(screenWidth, labelWidths).right();
    }

    /** Prefix sum: where tab {@code index} starts. */
    private static int modeTabOffset(int screenWidth, int[] labelWidths, int index) {
        int offset = 0;
        for (int i = 0; i < index; i++) offset += modeTabWidth(screenWidth, labelWidths[i]) + MICRO;
        return offset;
    }

    // ------------------------------------------------------------ global toolbar (top-right)

    static int globalButtonWidth(int screenWidth, int[] modeLabelWidths, int[] globalLabelWidths, int index) {
        boolean labelled = globalToolbarLabelled(screenWidth, modeLabelWidths, globalLabelWidths);
        return labelled ? labelledWidth(globalLabelWidths[index]) : compactWidth();
    }

    static int globalToolbarWidth(int screenWidth, int[] modeLabelWidths, int[] globalLabelWidths) {
        boolean labelled = globalToolbarLabelled(screenWidth, modeLabelWidths, globalLabelWidths);
        int total = 0;
        for (int index = 0; index < GLOBAL_ACTIONS; index++) {
            total += labelled ? labelledWidth(globalLabelWidths[index]) : compactWidth();
        }
        return total + globalToolbarGap(screenWidth, modeLabelWidths, globalLabelWidths) * (GLOBAL_ACTIONS - 1) + utilityGroupExtra(screenWidth) * 3;
    }

    /**
     * The gap shrinks before anything is dropped. At 320 px an icon row with the normal 8 px gap is
     * wider than the screen, and running off the edge is worse than sitting a little tighter.
     */
    static int globalToolbarGap(int screenWidth, int[] modeLabelWidths, int[] globalLabelWidths) {
        boolean labelled = globalToolbarLabelled(screenWidth, modeLabelWidths, globalLabelWidths);
        int each = 0;
        for (int index = 0; index < GLOBAL_ACTIONS; index++) {
            each += labelled ? labelledWidth(globalLabelWidths[index]) : compactWidth();
        }
        int spare = screenWidth - EDGE * 2 - each - utilityGroupExtra(screenWidth)*3;
        return Math.max(0, Math.min(NORMAL, spare / (GLOBAL_ACTIONS - 1)));
    }

    /**
     * Labelled only when the labelled toolbar fits beside the tabs that are actually shown: compact
     * tabs when the screen is narrow, labelled tabs when it is wide.
     */
    static boolean globalToolbarLabelled(int screenWidth, int[] modeLabelWidths, int[] globalLabelWidths) {
        int tabsRight = modeTabsRight(screenWidth, modeLabelWidths);
        int labelledTotal = 0;
        for (int index = 0; index < GLOBAL_ACTIONS; index++) labelledTotal += labelledWidth(globalLabelWidths[index]);
        labelledTotal += NORMAL * (GLOBAL_ACTIONS - 1) + utilityGroupExtra(screenWidth)*3;
        return tabsRight + NORMAL + labelledTotal <= screenWidth - EDGE;
    }

    /** True when even the icon-only toolbar cannot share the tab row, so it drops below it. */
    static boolean globalToolbarSecondRow(int screenWidth, int[] modeLabelWidths, int[] globalLabelWidths) {
        return modeTabsRight(screenWidth, modeLabelWidths) + NORMAL
                + globalToolbarWidth(screenWidth, modeLabelWidths, globalLabelWidths) > screenWidth - EDGE;
    }

    static int globalToolbarY(int screenWidth, int[] modeLabelWidths, int[] globalLabelWidths) {
        return globalToolbarSecondRow(screenWidth, modeLabelWidths, globalLabelWidths) ? TAB_HEIGHT + EDGE + MICRO : EDGE;
    }

    static Rect globalButton(int screenWidth, int index, int[] modeLabelWidths, int[] globalLabelWidths) {
        int width = globalButtonWidth(screenWidth, modeLabelWidths, globalLabelWidths, index);
        int gap = globalToolbarGap(screenWidth, modeLabelWidths, globalLabelWidths);
        int offset = 0;
        for (int i = 0; i < index; i++) {
            offset += globalButtonWidth(screenWidth, modeLabelWidths, globalLabelWidths, i) + gap;
        }
        offset += utilityGroupExtra(screenWidth) * ((index >= 2 ? 1 : 0) + (index >= 5 ? 1 : 0) + (index >= 7 ? 1 : 0));
        int total = globalToolbarWidth(screenWidth, modeLabelWidths, globalLabelWidths);
        return new Rect(Math.max(EDGE, screenWidth - total - EDGE) + offset,
                globalToolbarY(screenWidth, modeLabelWidths, globalLabelWidths), width, BUTTON_HEIGHT);
    }

    static Rect globalToolbar(int screenWidth, int[] modeLabelWidths, int[] globalLabelWidths) {
        int width = globalToolbarWidth(screenWidth, modeLabelWidths, globalLabelWidths);
        return new Rect(Math.max(EDGE, screenWidth - width - EDGE),
                globalToolbarY(screenWidth, modeLabelWidths, globalLabelWidths), width, BUTTON_HEIGHT);
    }

    /** The invariant the old layout kept breaking: global actions never sit on top of the mode tabs. */
    static boolean globalToolbarOverlapsTabs(int screenWidth, int[] modeLabelWidths, int[] globalLabelWidths) {
        Rect tabs = modeTabs(screenWidth, modeLabelWidths);
        for (int index = 0; index < GLOBAL_ACTIONS; index++) {
            if (globalButton(screenWidth, index, modeLabelWidths, globalLabelWidths).overlaps(tabs)) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ context panel

    static int contextPanelWidth(int screenWidth) {
        if (screenWidth < 420) return Math.max(136, screenWidth * 2 / 5);
        return screenWidth < 560 ? Math.max(136, screenWidth * 2 / 5) : Math.min(288, Math.max(216, screenWidth / 3));
    }

    /**
     * A docked right-hand column: it can never grow past the screen edge and never covers the mode
     * tabs. Its height is content-driven and clamped, so a long list runs out of panel rather than
     * off the screen.
     */
    static Rect contextPanel(int screenWidth, int screenHeight, int[] modeLabelWidths, int[] globalLabelWidths,
                             int panelHeight) {
        int width = contextPanelWidth(screenWidth);
        int x = Math.max(EDGE, screenWidth - width - EDGE);
        int y = headerBottom(screenWidth,modeLabelWidths,globalLabelWidths) + NORMAL;
        int available = Math.max(0, legend(screenWidth,screenHeight).y() - NORMAL - y);
        return new Rect(x, y, width, Math.min(Math.max(0, panelHeight), available));
    }

    /** The column the context panel occupies, so the map can be told to avoid it. */
    static int contextPanelLeft(int screenWidth) {
        return Math.max(EDGE, screenWidth - contextPanelWidth(screenWidth) - EDGE);
    }

    // ------------------------------------------------------------ popovers

    static int popoverWidth(int screenWidth, int contentWidth) {
        return Math.min(Math.max(contentWidth, 180), Math.max(160, screenWidth - EDGE * 2));
    }

    static int popoverY(int screenWidth, int[] modeLabelWidths, int[] globalLabelWidths) {
        return headerBottom(screenWidth,modeLabelWidths,globalLabelWidths) + MICRO;
    }
    static int headerBottom(int width,int[] modes,int[] utilities) {
        return Math.max(EDGE+TAB_HEIGHT,globalToolbarY(width,modes,utilities)+BUTTON_HEIGHT);
    }

    static Rect layersPopover(int screenWidth, int[] modeLabelWidths, int[] globalLabelWidths, int rows, int contentWidth) {
        return new Rect(Math.max(EDGE, screenWidth - popoverWidth(screenWidth, contentWidth) - EDGE),
                popoverY(screenWidth, modeLabelWidths, globalLabelWidths), popoverWidth(screenWidth, contentWidth),
                POPOVER_HEADER + rows * POPOVER_ROW);
    }

    static Rect layersRow(int screenWidth, int[] modeLabelWidths, int[] globalLabelWidths, int index, int contentWidth) {
        Rect panel = layersPopover(screenWidth, modeLabelWidths, globalLabelWidths, 99, contentWidth);
        return new Rect(panel.x() + MICRO, panel.y() + POPOVER_HEADER + index * POPOVER_ROW,
                panel.width() - NORMAL, POPOVER_ROW - MICRO);
    }

    static Rect searchPopover(int screenWidth, int[] modeLabelWidths, int[] globalLabelWidths, int rows, int contentWidth) {
        return new Rect(Math.max(EDGE, screenWidth - popoverWidth(screenWidth, contentWidth) - EDGE),
                popoverY(screenWidth, modeLabelWidths, globalLabelWidths), popoverWidth(screenWidth, contentWidth),
                68 + Math.max(1, rows) * 25 + 20);
    }

    static Rect searchRow(int screenWidth, int[] modeLabelWidths, int[] globalLabelWidths, int index, int contentWidth) {
        Rect panel = searchPopover(screenWidth, modeLabelWidths, globalLabelWidths, 99, contentWidth);
        return new Rect(panel.x() + MICRO, panel.y() + 68 + index * 25, panel.width() - NORMAL, 23);
    }

    // ------------------------------------------------------------ status strip (bottom)

    static Rect statusStrip(int screenWidth, int screenHeight) {
        return new Rect(EDGE, Math.max(EDGE, screenHeight - 23), Math.max(0, screenWidth - EDGE * 2), 18);
    }

    // ------------------------------------------------------------ modal surfaces

    /** Modals are centred and clamped so they never exceed the screen. */
    static Rect modal(int screenWidth, int screenHeight, int contentWidth, int contentHeight) {
        int width = Math.min(contentWidth, screenWidth - EDGE * 2);
        int height = Math.min(contentHeight, screenHeight - EDGE * 2);
        return new Rect((screenWidth - width) / 2, Math.max(EDGE, (screenHeight - height) / 2), width, height);
    }

    static Rect problemModal(int screenWidth, int screenHeight) {
        return modal(screenWidth, screenHeight, 360, 340);
    }

    static Rect guideModal(int screenWidth, int screenHeight) {
        return modal(screenWidth, screenHeight, 420, 260);
    }

    static Rect tutorialStrip(int screenWidth, int screenHeight, int textWidth) {
        int width = Math.min(screenWidth - EDGE * 2, textWidth + LABEL_PADDING * 2);
        return new Rect((screenWidth - width) / 2, Math.max(EDGE, screenHeight - 48), width, 18);
    }

    static final int CARD_PADDING=12, CARD_HEADER=58, STATUS_HEIGHT=22, FACT_GAP=8;
    static final int SUMMARY_ROW_HEIGHT=32, SUMMARY_ROW_PITCH=40;
    static final int ACTION_HEIGHT=24, ACTION_PITCH=30, LEGEND_ROW=18, LEGEND_HEADER=30;
    static final int COMPACT_LEGEND_HEADER=22;
    static int utilityGroupExtra(int width){return width>=320?MICRO:0;}
    static Rect legend(int width,int height) {
        int minimum=COMPACT_LEGEND_HEADER+LEGEND_ROW;
        int w=contextPanelWidth(width),h=Math.min(LEGEND_HEADER+6*LEGEND_ROW,Math.max(minimum,height/3));
        // Leave at least one complete action visible in a short, two-row-header GUI.
        int contextTop=EDGE+TAB_HEIGHT+MICRO+BUTTON_HEIGHT+NORMAL;
        int maximum=statusStrip(width,height).y()-NORMAL*2-contextTop-CARD_HEADER-ACTION_HEIGHT-MICRO;
        h=Math.min(h,Math.max(minimum,maximum));
        return new Rect(contextPanelLeft(width),Math.max(EDGE,statusStrip(width,height).y()-NORMAL-h),w,h);
    }
    static int legendHeader(Rect panel) { return panel.height()<LEGEND_HEADER+LEGEND_ROW?COMPACT_LEGEND_HEADER:LEGEND_HEADER; }
    static Rect legendRow(Rect legend,int row,int scroll) {
        return new Rect(legend.x()+CARD_PADDING,legend.y()+legendHeader(legend)+row*LEGEND_ROW-scroll,
                legend.width()-2*CARD_PADDING,LEGEND_ROW);
    }
    static Rect cardScrollTrack(Rect panel) {
        int top=Math.min(panel.bottom(),panel.y()+CARD_HEADER);
        return new Rect(panel.right()-5,top,2,Math.max(0,panel.bottom()-CARD_PADDING-top));
    }
    static Rect cardBody(Rect panel) {
        int bottom=Math.max(panel.y(),panel.bottom()-1);
        int top=Math.min(bottom,panel.y()+CARD_HEADER-3);
        return new Rect(panel.x()+1,top,Math.max(0,panel.width()-2),bottom-top);
    }
    static Rect cardScrollThumb(Rect panel,int scroll,int maxScroll) {
        var track=cardScrollTrack(panel);int h=Math.min(track.height(),Math.max(12,track.height()*track.height()/Math.max(1,track.height()+maxScroll)));
        return new Rect(track.x(),track.y()+(track.height()-h)*Math.max(0,Math.min(scroll,maxScroll))/Math.max(1,maxScroll),track.width(),h);
    }
    static Rect cardAction(Rect panel,int top,int index) {
        return new Rect(panel.x()+CARD_PADDING,top+index*ACTION_PITCH,panel.width()-2*CARD_PADDING,ACTION_HEIGHT);
    }
    static java.util.List<Rect> viewButtons(int width,int startY,int[] labels) {
        var result=new java.util.ArrayList<Rect>();int x=EDGE,y=startY;
        for(int label:labels){int w=Math.min(width,viewButtonsCompact(width)?BUTTON_HEIGHT:label+SECTION);
            if(x>EDGE&&x+w>EDGE+width){x=EDGE;y+=BUTTON_HEIGHT+MICRO;}
            result.add(new Rect(x,y,w,BUTTON_HEIGHT));x+=w+MICRO;
        }return result;
    }
    static boolean viewButtonsCompact(int width) { return width < 240; }

    static final int TOOLTIP_PADDING=6;
    static Rect tooltip(int width,int height,int mouseX,int mouseY,int textWidth,int textHeight) {
        int w=Math.min(Math.max(1,width-EDGE*2),textWidth+TOOLTIP_PADDING*2);
        int h=Math.min(Math.max(1,height-EDGE*2),textHeight+TOOLTIP_PADDING*2);
        int x=Math.max(EDGE,Math.min(width-EDGE-w,mouseX+SECTION));
        int y=Math.max(EDGE,Math.min(height-EDGE-h,mouseY+SECTION));
        return new Rect(x,y,w,h);
    }

    // ------------------------------------------------------------ responsive contract

    /** The widths the layout is checked against. */
    static final int[] RESPONSIVE_WIDTHS = { 320, 426, 640, 854, 1280 };

    static boolean withinScreen(int screenWidth, int screenHeight, Rect rect) {
        return rect.x() >= 0 && rect.y() >= 0
                && rect.right() <= screenWidth && rect.bottom() <= screenHeight;
    }
}
