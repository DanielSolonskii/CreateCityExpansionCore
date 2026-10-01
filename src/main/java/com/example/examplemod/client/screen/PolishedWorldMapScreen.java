package com.example.examplemod.client.screen;

import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.client.map.MapMarkerRenderer;
import com.example.examplemod.client.status.CcecProblemCenter;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.EconomyCommandPayload;
import com.example.examplemod.network.RailwayCommandPayload;
import com.example.examplemod.network.RailwayMapPayload;
import com.example.examplemod.railway.RailwayTypes;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import static com.example.examplemod.client.screen.WorldMapUiLayout.LARGE;
import static com.example.examplemod.client.screen.WorldMapUiLayout.MICRO;
import static com.example.examplemod.client.screen.WorldMapUiLayout.NORMAL;
import static com.example.examplemod.client.screen.WorldMapUiLayout.SECTION;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** Full-screen, map-first industrial atlas. */
public final class PolishedWorldMapScreen extends Screen {
    private static final int PANEL = 0xEC182023;
    private static final int PANEL_ALT = 0xF0222B2E;
    private static final int FRAME = 0xFF596064;
    private static final int ACCENT = 0xFFD09A3A;
    private static final int TEXT = 0xFFD8DEDF;
    private static final int MUTED = 0xFF98A3A7;
    private static final long TOOLTIP_DELAY_MILLIS = 350L;
    private static boolean tutorialSeen;

    private final CityPylonScreen parent;
    private final CityPylonMenu menu;
    private final CityMapPanel map;
    private final ItemStack titleIcon = item("createcityexpansioncore:city_pylon");
    private final ItemStack cityIcon = item("createcityexpansioncore:city_pylon");
    private final ItemStack residentsIcon = item("minecraft:player_head");
    private final ItemStack backIcon = item("minecraft:arrow");
    private final ItemStack forwardIcon = item("minecraft:arrow");
    private final ItemStack centerIcon = item("minecraft:compass");
    private final ItemStack searchIcon = item("minecraft:spyglass");
    private final ItemStack layersIcon = item("minecraft:map");

    /** Glyphs for the global actions; every one of them also carries a visible label when it fits. */
    private static final String[] GLOBAL_GLYPHS = { "‹", "›", "⌖", "⌕", "▤", "!", "?", "×" };
    private static final String[] GLOBAL_KEYS = {
            "gui.createcityexpansioncore.world_map.back",
            "gui.createcityexpansioncore.world_map.forward",
            "gui.createcityexpansioncore.world_map.center.city",
            "gui.createcityexpansioncore.world_map.search",
            "gui.createcityexpansioncore.world_map.layers",
            "gui.createcityexpansioncore.city.hub.problems",
            "gui.createcityexpansioncore.world_map.guide",
            "gui.createcityexpansioncore.world_map.close" };

    private Mode mode = Mode.CITIES;
    /** Measured once per layout pass so render and hit-testing share one geometry. */
    private final int[] modeLabelWidths = new int[WorldMapUiLayout.MODE_COUNT];
    private final int[] globalLabelWidths = new int[WorldMapUiLayout.GLOBAL_ACTIONS];
    private int refreshTicks;
    private int tutorialTicks;
    private boolean layersOpen;
    private boolean searchOpen;
    private EditBox searchBox;
    private List<CityMapPanel.Selection> searchResults = List.of();
    private Component hoveredTooltip;
    private Component tooltipCandidate;
    private long tooltipCandidateSince;
    private int cardX, cardY, cardWidth, cardHeight;
    /** Top pixel of the first action row inside the context card, -1 when there is no action. */
    private int cardActionY = -1;
    private int cardActionCount;
    private final List<Component> cardActions = new java.util.ArrayList<>();
    private final Deque<CityMapPanel.Selection> backHistory = new ArrayDeque<>();
    private final Deque<CityMapPanel.Selection> forwardHistory = new ArrayDeque<>();
    private boolean problemCenterOpen;
    private boolean guideOpen;

    public PolishedWorldMapScreen(CityPylonScreen parent) {
        super(Component.translatable("gui.createcityexpansioncore.world_map.title"));
        this.parent = parent;
        this.menu = parent.getMenu();
        this.map = parent.getMapPanel();
        this.map.setDrawInternalControls(false);
    }

    @Override
    protected void init() {
        this.map.setGuiScale(1.0F);
        applyMode(Mode.CITIES);
        requestSnapshots(true);
        this.tutorialTicks = tutorialSeen ? 0 : 220;
        measureLabels();
        // The search field is positioned from the same popover the renderer uses, so the box and the
        // plate around it cannot drift apart at any width.
        WorldMapUiLayout.Rect search = WorldMapUiLayout.searchPopover(this.width, this.modeLabelWidths, this.globalLabelWidths, 0, 228);
        this.searchBox = new EditBox(this.font, search.x() + 11, search.y() + 6, 208, 14,
                Component.translatable("gui.createcityexpansioncore.world_map.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(Component.translatable("gui.createcityexpansioncore.world_map.search.hint"));
        this.searchBox.setVisible(false);
        this.searchBox.setResponder(query -> this.searchResults = this.map.search(query));
        addRenderableWidget(this.searchBox);
    }

    /**
     * Measures every label once per layout. The widths are the only font-dependent input to the
     * geometry, so passing them in is what keeps {@link WorldMapUiLayout} pure and headless-testable.
     */
    private void measureLabels() {
        Mode[] modes = Mode.values();
        for (int index = 0; index < modes.length; index++) {
            this.modeLabelWidths[index] = this.font.width(modes[index].label());
        }
        for (int index = 0; index < WorldMapUiLayout.GLOBAL_ACTIONS; index++) {
            this.globalLabelWidths[index] = this.font.width(Component.translatable(GLOBAL_KEYS[index]));
        }
    }

    @Override
    public void onClose() {
        this.map.setDrawInternalControls(true);
        // The terrain textures are derived from the cached samples and the panel is shared with the
        // pylon screen, so they are released here instead of lingering on the GPU between openings.
        this.map.releaseTerrainTextures();
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    @Override
    public void tick() {
        if (this.tutorialTicks > 0 && --this.tutorialTicks == 0) tutorialSeen = true;
        if (++this.refreshTicks >= 20) {
            this.refreshTicks = 0;
            requestSnapshots(false);
        }
    }

    private void requestSnapshots(boolean includeMap) {
        PacketDistributor.sendToServer(RailwayCommandPayload.simple(
                this.menu.getPylonPos(), RailwayCommandPayload.Action.REQUEST_STATE, null, null));
        PacketDistributor.sendToServer(EconomyCommandPayload.simple(
                this.menu.getPylonPos(), EconomyCommandPayload.Action.REQUEST_STATE, null));
        if (includeMap) PacketDistributor.sendToServer(RailwayCommandPayload.simple(
                this.menu.getPylonPos(), RailwayCommandPayload.Action.REQUEST_MAP, null, null));
    }

    private void syncMapData() {
        this.menu.drainMapTiles().forEach(this.map::receive);
        RailwayMapPayload railwayMap = this.menu.drainRailwayMap();
        if (railwayMap != null) this.map.receive(railwayMap);
        this.map.setProjects(this.menu.getProjects());
        this.map.setTerritorySize(this.menu.getTerritorySize());
        this.map.setRailwayData(this.menu.getRailwayStations(), this.menu.getRailwayTrains());
        this.map.setUnregisteredTrains(this.menu.getRailwayTrainCandidates());
        if (this.menu.getEconomyState() != null) {
            this.map.setEconomyInfrastructure(this.menu.getEconomyState().infrastructure());
            this.map.setFreightData(this.menu.getEconomyState().logistics());
        }
        if (this.menu.getRelationsState() != null) this.map.setRelations(this.menu.getRelationsState().cities());
        reconcileTrainSelection();
    }

    /**
     * A marker changes kind the moment a train is registered (TRAIN_UNREGISTERED disappears and a
     * TRAIN marker takes its place). Re-pin the same train so the player keeps context and the card
     * immediately switches from "Register" to the type/management actions.
     */
    private void reconcileTrainSelection() {
        CityMapPanel.Selection selected = this.map.getSelection();
        if (selected == null || selected.kind() != CityMapPanel.SelectionKind.TRAIN_UNREGISTERED) return;
        var now = this.menu.getRailwayTrains().stream()
                .filter(train -> selected.id().equals(train.createTrainId())).findFirst().orElse(null);
        if (now != null) {
            this.map.selectTrain(now);
        } else if (this.menu.getRailwayTrainCandidates().stream()
                .noneMatch(candidate -> selected.id().equals(candidate.createTrainId()))) {
            this.map.clearSelection();
        }
    }

    /**
     * The render pipeline, in one fixed order:
     * background, terrain and map geometry, markers, the chrome, the context column, the tutorial
     * strip, then any modal, then the tooltip. A modal is the last thing painted and the only thing
     * that takes input, so no marker can ever end up on top of it and no ordinary tooltip can leak
     * through a dim overlay.
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        syncMapData();
        this.hoveredTooltip = null;
        boolean modal = this.guideOpen || this.problemCenterOpen || this.tutorialTicks > 0;
        // 1-2: background and terrain/geometry, drawn by the panel itself.
        graphics.fill(0, 0, this.width, this.height, 0xFF101719);
        this.map.render(graphics, 0, 0, this.width, this.height, mouseX, mouseY);
        // 3-4: markers are inside the panel render above; 5-6: chrome and the context column.
        renderTitle(graphics);
        renderModeToolbar(graphics, mouseX, mouseY);
        renderActionToolbar(graphics, mouseX, mouseY);
        renderContextCard(graphics);
        renderStatus(graphics, mouseX, mouseY);
        if (!modal) {
            if (this.layersOpen) renderLayers(graphics, mouseX, mouseY);
            if (this.searchOpen) renderSearchPanel(graphics, mouseX, mouseY);
        }
        // 7-10: dim, tutorial target, modal surface and its widgets, then the tooltip last.
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.tutorialTicks > 0) renderTutorial(graphics);
        if (this.problemCenterOpen) renderProblemCenter(graphics);
        if (this.guideOpen) renderGuide(graphics);
        if (!modal && this.hoveredTooltip != null) renderTooltip(graphics, this.hoveredTooltip, mouseX, mouseY);
    }

    private void renderTitle(GuiGraphics graphics) {
        WorldMapUiLayout.Rect plate = WorldMapUiLayout.title(this.width);
        plate(graphics, plate.x(), plate.y(), plate.width(), plate.height());
        graphics.renderItem(this.titleIcon, plate.x() + 6, plate.y() + 5);
        if (plate.width() <= 32) return;
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.world_map.identity"),
                plate.x() + 28, plate.y() + 4, 0xFFFFD59A, false);
        graphics.drawString(this.font, Component.literal(this.menu.getDimensionId().getPath()),
                plate.x() + 28, plate.y() + 15, MUTED, false);
    }

    /**
     * Mode tabs. The label is part of the tab whenever there is room for it, so what the map is
     * showing is readable without hovering anything; the selected tab is filled and outlined rather
     * than merely tinted. Electricity stays visible but disabled, so the player can see it is coming
     * without the map pretending to have power data.
     */
    private void renderModeToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        WorldMapUiLayout.Rect tabs = WorldMapUiLayout.modeTabs(this.width, this.modeLabelWidths);
        plate(graphics, tabs.x() - MICRO, tabs.y() - MICRO, tabs.width() + MICRO * 2, tabs.height() + MICRO * 2);
        Mode[] modes = Mode.values();
        for (int index = 0; index < modes.length; index++) {
            Mode value = modes[index];
            WorldMapUiLayout.Rect bounds = WorldMapUiLayout.modeTab(this.width, index, this.modeLabelWidths);
            boolean selected = value == this.mode;
            boolean enabled = value.available();
            boolean hover = enabled && bounds.contains(mouseX, mouseY);
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                    selected ? 0xFF4A391E : hover ? 0xFF303A3D : enabled ? PANEL_ALT : 0xFF1B2124);
            if (selected) graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), ACCENT);
            else if (hover) graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), FRAME);
            graphics.renderItem(value.icon, bounds.x() + MICRO, bounds.y() + MICRO);
            if (WorldMapUiLayout.modeTabsLabelled(this.width)) {
                int labelColour = !enabled ? 0xFF6E787C : selected ? 0xFFFFD59A : TEXT;
                graphics.drawString(this.font, value.label(), bounds.x() + MICRO + 16 + MICRO, bounds.y() + NORMAL + 1,
                        labelColour, false);
            }
            Component hint = !enabled
                    ? Component.translatable("gui.createcityexpansioncore.world_map.mode.energy.soon")
                    : value.label();
            offerTooltip(hint, hover || (!enabled && bounds.contains(mouseX, mouseY)));
        }
    }

    /**
     * Global actions only: navigation, view, help, close. Mode-specific actions live in the context
     * panel of the selected object, so nothing here changes meaning when the mode changes.
     */
    private void renderActionToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        WorldMapUiLayout.Rect bar = WorldMapUiLayout.globalToolbar(this.width, this.modeLabelWidths, this.globalLabelWidths);
        plate(graphics, bar.x() - MICRO, bar.y() - MICRO, bar.width() + MICRO * 2, bar.height() + MICRO * 2);
        int problems = problemCount();
        for (int index = 0; index < WorldMapUiLayout.GLOBAL_ACTIONS; index++) {
            WorldMapUiLayout.Rect bounds = WorldMapUiLayout.globalButton(this.width, index, this.modeLabelWidths, this.globalLabelWidths);
            boolean active = (index == WorldMapUiLayout.GLOBAL_SEARCH && this.searchOpen)
                    || (index == WorldMapUiLayout.GLOBAL_LAYERS && this.layersOpen)
                    || (index == WorldMapUiLayout.GLOBAL_PROBLEMS && this.problemCenterOpen);
            boolean hover = bounds.contains(mouseX, mouseY);
            boolean enabled = !canGoBack() || index == WorldMapUiLayout.GLOBAL_BACK ? true
                    : !(index == WorldMapUiLayout.GLOBAL_FORWARD && !canGoForward());
            graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(),
                    active ? 0xFF4A391E : hover ? 0xFF303A3D : PANEL_ALT);
            graphics.renderOutline(bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                    active ? ACCENT : hover ? FRAME : 0xFF394245);
            // The glyph occupies the reserved icon slot: centred in it when a label follows, centred
            // in the whole button when it does not, so both forms look centred.
            String glyph = GLOBAL_GLYPHS[index];
            boolean labelled = WorldMapUiLayout.globalToolbarLabelled(this.width,
                    this.modeLabelWidths, this.globalLabelWidths);
            int glyphX = labelled
                    ? bounds.x() + MICRO + (16 - this.font.width(glyph)) / 2
                    : bounds.x() + (bounds.width() - this.font.width(glyph)) / 2;
            graphics.drawString(this.font, glyph, glyphX, bounds.y() + NORMAL + 1,
                    enabled ? TEXT : 0xFF5E686C, false);
            Component label = Component.translatable(GLOBAL_KEYS[index]);
            if (labelled) {
                graphics.drawString(this.font, label, bounds.x() + MICRO + 16 + MICRO, bounds.y() + NORMAL + 1,
                        enabled ? TEXT : MUTED, false);
            }
            if (index == WorldMapUiLayout.GLOBAL_PROBLEMS && problems > 0) {
                graphics.drawString(this.font, Integer.toString(problems), bounds.right() - 12,
                        bounds.y() + NORMAL + 1, 0xFFFFA7A0, false);
            }
            offerTooltip(label, hover);
        }
    }

    private boolean canGoBack() { return !this.backHistory.isEmpty(); }
    private boolean canGoForward() { return !this.forwardHistory.isEmpty(); }

    /**
     * The layers popover only offers what the current mode actually draws. Listing every overlay in
     * every mode is how a "layers" panel turns into a debug dump, and a toggle the mode ignores is a
     * lie about what the map is showing.
     */
    private List<CityMapPanel.Overlay> activeOverlays() {
        return switch (this.mode) {
            case CITIES -> List.of(CityMapPanel.Overlay.CITIES, CityMapPanel.Overlay.TERRITORIES,
                    CityMapPanel.Overlay.RELATIONS, CityMapPanel.Overlay.PROJECTS);
            case RAILWAY -> List.of(CityMapPanel.Overlay.TRACKS, CityMapPanel.Overlay.STATIONS,
                    CityMapPanel.Overlay.TRAINS, CityMapPanel.Overlay.CITIES);
            case LOGISTICS -> List.of(CityMapPanel.Overlay.TRADE, CityMapPanel.Overlay.TRAINS,
                    CityMapPanel.Overlay.CITIES);
            case ENERGY -> List.of();
        };
    }

    /** Railway classes in legend order; the swatches come from the same policy the map draws with. */
    private static List<CityMapPanel.RailClass> legendClasses() {
        return List.of(CityMapPanel.RailClass.OWN, CityMapPanel.RailClass.PUBLIC_FREE,
                CityMapPanel.RailClass.PUBLIC_PAID, CityMapPanel.RailClass.PRIVATE);
    }

    private int layersRowCount() {
        return activeOverlays().size() + (this.mode == Mode.RAILWAY ? legendClasses().size() + 1 : 0);
    }

    private void renderLayers(GuiGraphics graphics, int mouseX, int mouseY) {
        int contentWidth = this.mode == Mode.RAILWAY ? 214 : 178;
        int rows = layersRowCount();
        WorldMapUiLayout.Rect panel = WorldMapUiLayout.layersPopover(this.width, this.modeLabelWidths, this.globalLabelWidths, rows, contentWidth);
        plate(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.layers"),
                panel.x() + WorldMapUiLayout.SECTION, panel.y() + WorldMapUiLayout.NORMAL - 2, 0xFFFFD59A, false);
        int row = 0;
        for (CityMapPanel.Overlay overlay : activeOverlays()) {
            WorldMapUiLayout.Rect bounds = WorldMapUiLayout.layersRow(this.width, this.modeLabelWidths, this.globalLabelWidths, row++, contentWidth);
            boolean enabled = this.map.isOverlayEnabled(overlay);
            boolean hover = bounds.contains(mouseX, mouseY);
            if (hover) graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xFF303A3D);
            graphics.drawString(this.font, enabled ? "◆" : "◇", bounds.x() + WorldMapUiLayout.MICRO, bounds.y() + 5,
                    enabled ? 0xFFFFD59A : 0xFF788589, false);
            graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.overlay."
                    + overlay.name().toLowerCase(Locale.ROOT)), bounds.x() + 14, bounds.y() + 5,
                    enabled ? TEXT : MUTED, false);
        }
        if (this.mode != Mode.RAILWAY) return;
        // In railway mode the legend is the whole point: a line must be identifiable without hovering it.
        WorldMapUiLayout.Rect header = WorldMapUiLayout.layersRow(this.width, this.modeLabelWidths, this.globalLabelWidths, row++, contentWidth);
        graphics.hLine(header.x(), header.right(), header.y() + WorldMapUiLayout.POPOVER_ROW / 2, 0xFF6F542C);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.legend.title"),
                header.x() + WorldMapUiLayout.MICRO, header.y() + 5, 0xFFFFD59A, false);
        for (CityMapPanel.RailClass kind : legendClasses()) {
            WorldMapUiLayout.Rect bounds = WorldMapUiLayout.layersRow(this.width, this.modeLabelWidths, this.globalLabelWidths, row++, contentWidth);
            int inner = CityMapPanel.railInnerWidth(kind);
            // Drawn exactly the way the map draws it: outline underneath, semantic colour on top.
            graphics.fill(bounds.x() + WorldMapUiLayout.MICRO, bounds.y() + 5,
                    bounds.x() + 8 + WorldMapUiLayout.MICRO, bounds.y() + 6 + inner,
                    CityMapPanel.railOutlineColor(kind));
            graphics.fill(bounds.x() + WorldMapUiLayout.MICRO, bounds.y() + 6,
                    bounds.x() + 8 + WorldMapUiLayout.MICRO, bounds.y() + 6 + inner,
                    CityMapPanel.railInnerColor(kind));
            graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.legend."
                    + kind.name().toLowerCase(Locale.ROOT)), bounds.x() + 22, bounds.y() + 5, TEXT, false);
        }
    }

    private void renderSearchPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int contentWidth = 228;
        WorldMapUiLayout.Rect panel = WorldMapUiLayout.searchPopover(this.width, this.modeLabelWidths, this.globalLabelWidths,
                this.searchResults.size(), contentWidth);
        plate(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        graphics.fill(panel.x() + 7, panel.y() + 5, panel.right() - 7, panel.y() + 25, 0xFF111719);
        graphics.renderOutline(panel.x() + 7, panel.y() + 5, panel.width() - 14, 20, ACCENT);
        if (this.searchResults.isEmpty() && !this.searchBox.getValue().isBlank()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.createcityexpansioncore.world_map.search.empty"),
                    panel.x() + 10, panel.y() + 36, MUTED, false);
        }
        for (int index = 0; index < this.searchResults.size(); index++) {
            CityMapPanel.Selection result = this.searchResults.get(index);
            WorldMapUiLayout.Rect row = WorldMapUiLayout.searchRow(this.width, this.modeLabelWidths, this.globalLabelWidths, index, contentWidth);
            boolean hover = row.contains(mouseX, mouseY);
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(), hover ? 0xFF303A3D : PANEL_ALT);
            graphics.renderItem(result.icon(), row.x() + 3, row.y() + 2);
            graphics.drawString(this.font, result.title(), row.x() + 24, row.y() + 3, TEXT, false);
            graphics.drawString(this.font, selectionType(result), row.x() + 24, row.y() + 13, MUTED, false);
        }
    }

    /**
     * The right-hand column. With nothing selected it is an operational overview rather than an
     * empty box, because opening the map should answer "what is going on", not "here is a list of
     * errors". A pinned selection always wins over the overview.
     */
    private void renderContextCard(GuiGraphics graphics) {
        CityMapPanel.Selection value = this.map.getSelection();
        boolean pinned = value != null;
        if (!pinned) value = this.map.getHoveredSelection();
        if (value == null) {
            this.cardActions.clear();
            this.cardActionCount = 0;
            this.cardActionY = -1;
            renderOverview(graphics);
            return;
        }
        renderSelectionPanel(graphics, value, pinned);
    }

    /** "Сейчас важно": counts the player actually acts on, and a calm state when nothing is wrong. */
    private void renderOverview(GuiGraphics graphics) {
        int width = WorldMapUiLayout.contextPanelWidth(this.width);
        int trains = this.menu.getRailwayTrains().size();
        int freightPoints = this.map.freightPointCount();
        int discoveries = this.map.freightCandidateCount();
        int problems = problemCount();
        List<Component> facts = new ArrayList<>();
        facts.add(Component.translatable("gui.createcityexpansioncore.world_map.overview.trains", trains));
        facts.add(Component.translatable("gui.createcityexpansioncore.world_map.overview.freight_points", freightPoints));
        if (discoveries > 0) facts.add(Component.translatable("gui.createcityexpansioncore.world_map.overview.discoveries", discoveries));
        facts.add(problems > 0
                ? Component.translatable("gui.createcityexpansioncore.world_map.overview.problems", problems)
                : Component.translatable("gui.createcityexpansioncore.world_map.overview.calm"));
        int factsHeight = 0;
        for (Component fact : facts) factsHeight += this.font.wordWrapHeight(fact, width - LARGE) + MICRO;
        int height = 46 + factsHeight + 8;
        WorldMapUiLayout.Rect panel = WorldMapUiLayout.contextPanel(this.width, this.height, this.modeLabelWidths, this.globalLabelWidths, height);
        this.cardX = panel.x(); this.cardY = panel.y();
        this.cardWidth = panel.width(); this.cardHeight = panel.height();
        plate(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(), FRAME);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.overview.title"),
                panel.x() + LARGE, panel.y() + NORMAL, 0xFFFFD59A, false);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.overview.hint"),
                panel.x() + LARGE, panel.y() + NORMAL + 12, MUTED, false);
        int cursor = panel.y() + 46;
        for (Component fact : facts) {
            graphics.drawWordWrap(this.font, fact, panel.x() + LARGE, cursor, width - LARGE, TEXT);
            cursor += this.font.wordWrapHeight(fact, width - LARGE) + MICRO;
        }
    }

    /**
     * One structure for every kind of object: icon and title, kind/owner subtitle, status, the few
     * facts that matter, and then at most one primary action plus its siblings. Raw ids and
     * coordinates are deliberately not part of the normal card.
     */
    private void renderSelectionPanel(GuiGraphics graphics, CityMapPanel.Selection value, boolean pinned) {
        this.cardActions.clear();
        if (pinned) this.cardActions.addAll(contextActions(value));
        int actionCount = this.cardActions.size();
        int width = WorldMapUiLayout.contextPanelWidth(this.width);
        int detailCount = pinned ? Math.min(6, value.details().size()) : Math.min(2, value.details().size());
        int detailsHeight = 0;
        for (int index = 0; index < detailCount; index++) {
            detailsHeight += this.font.wordWrapHeight(value.details().get(index), width - LARGE) + MICRO;
        }
        int height = 46 + detailsHeight + (pinned ? 14 : 0)
                + (actionCount > 0 ? MICRO * 2 + actionCount * 20 : 0);
        WorldMapUiLayout.Rect panel = WorldMapUiLayout.contextPanel(this.width, this.height,
                this.modeLabelWidths, this.globalLabelWidths, height);
        int x = panel.x(), y = panel.y();
        this.cardX = x; this.cardY = y; this.cardWidth = panel.width(); this.cardHeight = panel.height();
        plate(graphics, x, y, panel.width(), panel.height());
        graphics.renderOutline(x, y, panel.width(), panel.height(), pinned ? ACCENT : FRAME);
        MapMarkerRenderer.renderCardIcon(graphics, x + LARGE, y + LARGE - 2, value.icon(), value.own(), value.warning());
        graphics.drawString(this.font, value.title(), x + 16 + LARGE, y + NORMAL, 0xFFFFFFFF, false);
        graphics.drawString(this.font, selectionType(value), x + 16 + LARGE, y + NORMAL + 12,
                value.own() ? 0xFFFFD59A : MUTED, false);
        if (pinned) {
            // Coordinates are a last-resort detail for a pinned selection only, never for hover.
            graphics.drawString(this.font,
                    Component.translatable("gui.createcityexpansioncore.world_map.context.coordinates",
                            value.pos().getX(), value.pos().getZ()),
                    x + 16 + LARGE, y + NORMAL + 23, MUTED, false);
        }
        int cursor = y + (pinned ? 46 : 38);
        if (pinned) {
            // Status band, derived from server-authoritative fields the selection already carries:
            // a warning is the only thing that can override ownership.
            boolean attention = value.warning();
            String key = attention ? "attention" : value.own() ? "own" : "foreign";
            int colour = attention ? 0xFFFFA7A0 : value.own() ? 0xFF9FE1C0 : MUTED;
            graphics.fill(x + LARGE, cursor, x + panel.width() - LARGE, cursor + 12,
                    attention ? 0xFF4A2422 : value.own() ? 0xFF23382F : 0xFF2A3033);
            graphics.drawString(this.font,
                    Component.translatable("gui.createcityexpansioncore.world_map.context.status." + key),
                    x + LARGE + MICRO, cursor + 3, colour, false);
            cursor += 14;
        }
        for (int index = 0; index < detailCount; index++) {
            Component detail = value.details().get(index);
            graphics.drawWordWrap(this.font, detail, x + LARGE, cursor, width - LARGE, TEXT);
            cursor += this.font.wordWrapHeight(detail, width - LARGE) + MICRO;
        }
        this.cardActionY = -1;
        this.cardActionCount = 0;
        if (actionCount == 0) {
            if (!pinned) graphics.drawString(this.font,
                    Component.translatable("gui.createcityexpansioncore.world_map.hover.details"),
                    x + LARGE, cursor, 0xFFB89A68, false);
            return;
        }
        graphics.hLine(x + LARGE, x + panel.width() - LARGE, cursor + 1, 0xFF6F542C);
        int rowY = cursor + MICRO + 1;
        this.cardActionY = rowY;
        this.cardActionCount = actionCount;
        for (int index = 0; index < actionCount; index++) {
            int actionY = rowY + index * 20;
            // The first action is the primary one, so it gets the accent instead of a flat row.
            graphics.fill(x + LARGE, actionY, x + panel.width() - LARGE, actionY + 18,
                    index == 0 ? 0xFF5A4522 : 0xFF3A4145);
            graphics.drawCenteredString(this.font, this.cardActions.get(index),
                    x + panel.width() / 2, actionY + 5, 0xFFFFFFFF);
        }
    }

    /**
     * Action rows of the pinned context card. Trains and unregistered Create trains get a small
     * management list instead of the single generic action so registration, type changes and route
     * selection are reachable straight from the World Map; everything else keeps one action.
     * An empty list means "read-only card": the viewer has no rights on that selection.
     */
    private List<Component> contextActions(CityMapPanel.Selection value) {
        if (value == null) return List.of();
        if (value.kind() == CityMapPanel.SelectionKind.TRAIN) {
            var train = this.menu.getRailwayTrains().stream()
                    .filter(registered -> registered.fleetTrainId().equals(value.id())).findFirst().orElse(null);
            if (train == null || !train.canManage()) return List.of();
            return List.of(
                    Component.translatable("gui.createcityexpansioncore.world_map.action.change_type"),
                    Component.translatable("gui.createcityexpansioncore.world_map.action.manage_railway"),
                    Component.translatable("gui.createcityexpansioncore.world_map.action.select_route"));
        }
        if (value.kind() == CityMapPanel.SelectionKind.TRAIN_UNREGISTERED) {
            var candidate = this.menu.getRailwayTrainCandidates().stream()
                    .filter(pending -> pending.createTrainId().equals(value.id())).findFirst().orElse(null);
            if (candidate == null || !candidate.canManage()) return List.of();
            return List.of(
                    Component.translatable("gui.createcityexpansioncore.world_map.action.register_train"),
                    Component.translatable("gui.createcityexpansioncore.world_map.action.manage_railway"));
        }
        if (!value.own()) return List.of();
        return List.of(contextAction(value));
    }

    private void handleContextAction(CityMapPanel.Selection value, int index) {
        if (value.kind() == CityMapPanel.SelectionKind.TRAIN) {
            var train = this.menu.getRailwayTrains().stream()
                    .filter(registered -> registered.fleetTrainId().equals(value.id())).findFirst().orElse(null);
            if (train == null || !train.canManage()) return;
            if (index == 0) {
                RailwayTypes.TrainType next = RailwayTypes.TrainType.values()[
                        (train.type().ordinal() + 1) % RailwayTypes.TrainType.values().length];
                PacketDistributor.sendToServer(new RailwayCommandPayload(this.menu.getPylonPos(),
                        RailwayCommandPayload.Action.SET_TRAIN_TYPE, train.fleetTrainId(), null,
                        "", next.ordinal(), true, List.of()));
            } else if (index == 1) {
                this.parent.openTrainManagement(train.fleetTrainId());
                if (this.minecraft != null) this.minecraft.setScreen(this.parent);
            } else if (index == 2) {
                this.parent.openTrainManagement(train.fleetTrainId());
                PacketDistributor.sendToServer(RailwayCommandPayload.simple(this.menu.getPylonPos(),
                        RailwayCommandPayload.Action.REQUEST_ROUTES, train.fleetTrainId(), null));
                if (this.minecraft != null) this.minecraft.setScreen(this.parent);
            }
            return;
        }
        if (value.kind() == CityMapPanel.SelectionKind.TRAIN_UNREGISTERED) {
            var candidate = this.menu.getRailwayTrainCandidates().stream()
                    .filter(pending -> pending.createTrainId().equals(value.id())).findFirst().orElse(null);
            if (candidate == null || !candidate.canManage()) return;
            if (index == 0) {
                // Server replies with a fresh state; reconcileTrainSelection() re-pins the new
                // TRAIN marker and the card immediately offers the type action.
                PacketDistributor.sendToServer(RailwayCommandPayload.simple(this.menu.getPylonPos(),
                        RailwayCommandPayload.Action.REGISTER_TRAIN, candidate.createTrainId(), null));
            } else {
                this.parent.openTrainManagement();
                if (this.minecraft != null) this.minecraft.setScreen(this.parent);
            }
            return;
        }
        if (index == 0) handleContextAction(value);
    }

    private Component contextAction(CityMapPanel.Selection value) {
        return Component.translatable(switch (value.kind()) {
            case FREIGHT_POINT, TRAIN -> "gui.createcityexpansioncore.world_map.action.open_logistics";
            case DISCOVERY -> "gui.createcityexpansioncore.world_map.action.setup_freight";
            case CITY -> "gui.createcityexpansioncore.world_map.action.open_relations";
            case RAILWAY_SECTION -> "gui.createcityexpansioncore.world_map.action.manage_railway";
            case ROUTE, STATION -> "gui.createcityexpansioncore.world_map.action.railway_focus";
            default -> "gui.createcityexpansioncore.world_map.action.center";
        });
    }

    private int problemCount() {
        return CcecProblemCenter.count(this.menu.getEconomyState(), this.menu.getRailwayOwnership(),
                this.menu.getRailwayTrains());
    }

    /**
     * A summary card, not a screen takeover: the map stays visible behind a light dim so the player
     * keeps their orientation while reading what needs attention.
     */
    private void renderProblemCenter(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0x66101517);
        WorldMapUiLayout.Rect panel = WorldMapUiLayout.problemModal(this.width, this.height);
        plate(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(), ACCENT);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.problems"),
                panel.x() + WorldMapUiLayout.SECTION, panel.y() + WorldMapUiLayout.NORMAL, 0xFFFFD59A, false);
        int count = problemCount();
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.problem_count", count),
                panel.x() + WorldMapUiLayout.SECTION, panel.y() + WorldMapUiLayout.NORMAL + 16, count > 0 ? 0xFFFFA7A0 : 0xFF9FE1C0, false);
        graphics.drawWordWrap(this.font,
                Component.translatable(CcecProblemCenter.nextStepKey(this.menu.getEconomyState(), count)),
                panel.x() + WorldMapUiLayout.SECTION, panel.y() + WorldMapUiLayout.NORMAL + 32,
                panel.width() - WorldMapUiLayout.SECTION * 2, TEXT);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.createcityexpansioncore.city.hub.open_logistics"),
                panel.x() + panel.width() / 2, panel.bottom() - 22, 0xFFFFD59A);
    }

    private void renderGuide(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0x66101517);
        WorldMapUiLayout.Rect panel = WorldMapUiLayout.guideModal(this.width, this.height);
        plate(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(), ACCENT);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.guide.title"),
                panel.x() + WorldMapUiLayout.LARGE, panel.y() + WorldMapUiLayout.SECTION, 0xFFFFD59A, false);
        graphics.drawWordWrap(this.font, Component.translatable("gui.createcityexpansioncore.world_map.guide.text"),
                panel.x() + WorldMapUiLayout.LARGE, panel.y() + WorldMapUiLayout.NORMAL + 14,
                panel.width() - WorldMapUiLayout.LARGE * 2, TEXT);
        graphics.drawCenteredString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.guide.dismiss"),
                panel.x() + panel.width() / 2, panel.bottom() - 20, MUTED);
    }

    private void renderStatus(GuiGraphics graphics, int mouseX, int mouseY) {
        Component coordinates = this.map.coordinateStatus(mouseX, mouseY);
        int coordinateWidth = this.font.width(coordinates) + 16;
        int rightX = this.width - coordinateWidth - WorldMapUiLayout.EDGE;
        int residentsWidth = this.font.width(Integer.toString(this.menu.getMemberCount())) + 32;
        int residentsX = Math.max(WorldMapUiLayout.EDGE, rightX - residentsWidth - 6);
        int cityWidth = Math.max(42, residentsX - 14);
        cityWidth = Math.min(cityWidth, Math.min(190, this.font.width(this.menu.getCityName()) + 30));
        WorldMapUiLayout.Rect strip = WorldMapUiLayout.statusStrip(this.width, this.height);
        int y = strip.y();
        statusChip(graphics, WorldMapUiLayout.EDGE, y, cityWidth);
        graphics.renderItem(this.cityIcon, WorldMapUiLayout.EDGE + 5, y + 1);
        Component cityName = Component.literal(this.font.plainSubstrByWidth(
                this.menu.getCityName(), cityWidth - 29));
        graphics.drawString(this.font, cityName, WorldMapUiLayout.EDGE + 26, y + 5, TEXT, false);
        statusChip(graphics, residentsX, y, residentsWidth);
        graphics.renderItem(this.residentsIcon, residentsX + 5, y + 1);
        graphics.drawString(this.font, Integer.toString(this.menu.getMemberCount()),
                residentsX + 24, y + 5, MUTED, false);
        statusChip(graphics, rightX, y, coordinateWidth);
        graphics.drawString(this.font, coordinates, rightX + 8, y + 5, MUTED, false);
    }

    private void renderTutorial(GuiGraphics graphics) {
        if (this.tutorialTicks <= 0) return;
        graphics.fill(0, 0, this.width, this.height, 0x660B1012);
        Component text = Component.translatable("gui.createcityexpansioncore.world_map.tutorial");
        WorldMapUiLayout.Rect strip = WorldMapUiLayout.tutorialStrip(this.width, this.height, this.font.width(text));
        plate(graphics, strip.x(), strip.y(), strip.width(), strip.height());
        graphics.drawCenteredString(this.font, text, this.width / 2, strip.y() + 5, TEXT);
    }

    private void offerTooltip(Component tooltip, boolean hovered) {
        if (!hovered) {
            if (tooltip.equals(this.tooltipCandidate)) this.tooltipCandidate = null;
            return;
        }
        long now = System.currentTimeMillis();
        if (!tooltip.equals(this.tooltipCandidate)) {
            this.tooltipCandidate = tooltip;
            this.tooltipCandidateSince = now;
        } else if (now - this.tooltipCandidateSince >= TOOLTIP_DELAY_MILLIS) {
            this.hoveredTooltip = tooltip;
        }
    }

    private void renderTooltip(GuiGraphics graphics, Component value, int mouseX, int mouseY) {
        int width = this.font.width(value) + 12;
        int x = Math.max(4, Math.min(this.width - width - 4, mouseX + 10));
        int y = Math.max(4, Math.min(this.height - 18, mouseY + 12));
        graphics.fill(x, y, x + width, y + 16, 0xF0101517);
        graphics.renderOutline(x, y, width, 16, FRAME);
        graphics.drawString(this.font, value, x + 6, y + 5, TEXT, false);
    }

    private static void plate(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.renderOutline(x, y, width, height, FRAME);
    }

    private static void statusChip(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 18, 0xD5182023);
        graphics.fill(x, y, x + 2, y + 18, 0xA9596064);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);
        // A modal owns the click: the map must not pan, select or dismiss anything underneath it.
        if (this.problemCenterOpen || this.guideOpen) return handleModalClick(mouseX, mouseY);
        Mode[] modes = Mode.values();
        for (int index = 0; index < modes.length; index++) {
            if (!WorldMapUiLayout.modeTab(this.width, index, this.modeLabelWidths).contains(mouseX, mouseY)) continue;
            // A disabled mode is inert: it never switches focus and never pretends to have data.
            if (modes[index].available()) applyMode(modes[index]);
            dismissTutorial();
            return true;
        }
        for (int index = 0; index < WorldMapUiLayout.GLOBAL_ACTIONS; index++) {
            if (!WorldMapUiLayout.globalButton(this.width, index, this.modeLabelWidths, this.globalLabelWidths).contains(mouseX, mouseY)) continue;
            handleGlobalAction(index);
            return true;
        }
        if (this.searchOpen && this.searchBox.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.layersOpen && handleLayerClick(mouseX, mouseY)) return true;
        if (this.searchOpen && handleSearchClick(mouseX, mouseY)) return true;
        CityMapPanel.Selection selected = this.map.getSelection();
        if (selected != null && this.cardActionY >= 0) {
            for (int index = 0; index < this.cardActionCount; index++) {
                int rowY = this.cardActionY + index * 20;
                if (inside(mouseX, mouseY, this.cardX + WorldMapUiLayout.LARGE, rowY,
                        this.cardWidth - WorldMapUiLayout.LARGE * 2, 18)) {
                    handleContextAction(selected, index);
                    return true;
                }
            }
        }
        if (selected != null && this.cardWidth > 0
                && inside(mouseX, mouseY, this.cardX, this.cardY, this.cardWidth, this.cardHeight)) return true;
        this.map.clearSelection();
        dismissTutorial();
        CityMapPanel.Selection before = selected;
        boolean handled = this.map.mouseClicked(mouseX, mouseY, button);
        CityMapPanel.Selection after = this.map.getSelection();
        if (handled && after != null && !after.equals(before)) {
            if (before != null) this.backHistory.push(before);
            this.forwardHistory.clear();
        }
        return handled || super.mouseClicked(mouseX, mouseY, button);
    }

    /** Any click inside an open modal closes it; the problem card's own button opens Logistics. */
    private boolean handleModalClick(double mouseX, double mouseY) {
        if (this.problemCenterOpen) {
            WorldMapUiLayout.Rect panel = WorldMapUiLayout.problemModal(this.width, this.height);
            boolean open = inside(mouseX, mouseY, panel.x() + WorldMapUiLayout.SECTION, panel.bottom() - 30,
                    panel.width() - WorldMapUiLayout.SECTION * 2, 24);
            this.problemCenterOpen = false;
            if (open && this.minecraft != null) this.minecraft.setScreen(new LogisticsScreen(this.parent));
            return true;
        }
        this.guideOpen = false;
        tutorialSeen = true;
        return true;
    }

    private void handleGlobalAction(int index) {
        switch (index) {
            case WorldMapUiLayout.GLOBAL_BACK -> { navigateBack(); dismissTutorial(); }
            case WorldMapUiLayout.GLOBAL_FORWARD -> { navigateForward(); dismissTutorial(); }
            case WorldMapUiLayout.GLOBAL_CENTER_CITY -> { this.map.centerOn(this.menu.getPylonPos()); dismissTutorial(); }
            case WorldMapUiLayout.GLOBAL_SEARCH -> {
                this.searchOpen = !this.searchOpen;
                this.layersOpen = false;
                this.searchBox.setVisible(this.searchOpen);
                setFocused(this.searchOpen ? this.searchBox : null);
            }
            case WorldMapUiLayout.GLOBAL_LAYERS -> {
                this.layersOpen = !this.layersOpen;
                this.searchOpen = false;
                this.searchBox.setVisible(false);
            }
            case WorldMapUiLayout.GLOBAL_PROBLEMS -> this.problemCenterOpen = true;
            case WorldMapUiLayout.GLOBAL_HELP -> this.guideOpen = true;
            case WorldMapUiLayout.GLOBAL_CLOSE -> onClose();
            default -> { }
        }
    }

    private void navigateBack() {
        if (this.backHistory.isEmpty()) return;
        CityMapPanel.Selection current=this.map.getSelection();if(current!=null)this.forwardHistory.push(current);
        this.map.selectAndCenter(this.backHistory.pop());
    }

    private void navigateForward() {
        if (this.forwardHistory.isEmpty()) return;
        CityMapPanel.Selection current=this.map.getSelection();if(current!=null)this.backHistory.push(current);
        this.map.selectAndCenter(this.forwardHistory.pop());
    }

    private void handleContextAction(CityMapPanel.Selection value) {
        switch (value.kind()) {
            case FREIGHT_POINT, TRAIN, DISCOVERY -> { if(this.minecraft!=null)this.minecraft.setScreen(new LogisticsScreen(this.parent)); }
            case CITY -> { if(this.minecraft!=null)this.minecraft.setScreen(new CityRelationsScreen(this.parent)); }
            case RAILWAY_SECTION -> { this.parent.openRailwayManagement(); if(this.minecraft!=null)this.minecraft.setScreen(this.parent); }
            case ROUTE, STATION -> applyMode(Mode.RAILWAY);
            default -> this.map.centerOn(value.pos());
        }
    }

    /** Only the overlays the current mode owns are clickable; a row that is not drawn is not a row. */
    private boolean handleLayerClick(double mouseX, double mouseY) {
        int contentWidth = this.mode == Mode.RAILWAY ? 214 : 178;
        int row = 0;
        for (CityMapPanel.Overlay overlay : activeOverlays()) {
            if (WorldMapUiLayout.layersRow(this.width, this.modeLabelWidths, this.globalLabelWidths, row++, contentWidth).contains(mouseX, mouseY)) {
                this.map.setOverlay(overlay, !this.map.isOverlayEnabled(overlay));
                return true;
            }
        }
        return layersPopoverBounds().contains(mouseX, mouseY);
    }

    private WorldMapUiLayout.Rect layersPopoverBounds() {
        return WorldMapUiLayout.layersPopover(this.width, this.modeLabelWidths, this.globalLabelWidths, layersRowCount(),
                this.mode == Mode.RAILWAY ? 214 : 178);
    }

    private boolean handleSearchClick(double mouseX, double mouseY) {
        int contentWidth = 228;
        for (int index = 0; index < this.searchResults.size(); index++) {
            if (WorldMapUiLayout.searchRow(this.width, this.modeLabelWidths, this.globalLabelWidths, index, contentWidth).contains(mouseX, mouseY)) {
                this.map.selectAndCenter(this.searchResults.get(index));
                this.searchOpen = false;
                this.searchBox.setVisible(false);
                setFocused(null);
                return true;
            }
        }
        return WorldMapUiLayout.searchPopover(this.width, this.modeLabelWidths, this.globalLabelWidths, this.searchResults.size(), contentWidth)
                .contains(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        dismissTutorial();
        return this.map.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.map.mouseReleased(mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        dismissTutorial();
        return this.map.mouseScrolled(mouseX, mouseY, scrollY)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.map.getSelection() != null) { this.map.clearSelection(); return true; }
            if (this.searchOpen) {
                this.searchOpen = false; this.searchBox.setVisible(false); setFocused(null); return true;
            }
            if (this.layersOpen) { this.layersOpen = false; return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void dismissTutorial() {
        this.tutorialTicks = 0;
        tutorialSeen = true;
    }

    /**
     * A mode owns a set of overlays: switching mode is what makes the map legible, so each one shows
     * only what it is about. Electricity has no overlays because it has no data to draw.
     */
    private void applyMode(Mode mode) {
        this.mode = mode;
        this.map.setFocus(mode.focus);
        this.map.setOverlay(CityMapPanel.Overlay.CITIES, true);
        switch (mode) {
            case CITIES -> setOverlays(true, true, false, false, false, false);
            case RAILWAY -> setOverlays(false, false, true, true, true, false);
            case LOGISTICS -> setOverlays(false, false, false, true, true, true);
            case ENERGY -> setOverlays(false, false, false, false, false, false);
        }
    }

    private void setOverlays(boolean territories, boolean projects, boolean tracks,
                             boolean stations, boolean trains, boolean trade) {
        this.map.setOverlay(CityMapPanel.Overlay.TERRITORIES, territories);
        this.map.setOverlay(CityMapPanel.Overlay.PROJECTS, projects);
        this.map.setOverlay(CityMapPanel.Overlay.TRACKS, tracks);
        this.map.setOverlay(CityMapPanel.Overlay.STATIONS, stations);
        this.map.setOverlay(CityMapPanel.Overlay.TRAINS, trains);
        this.map.setOverlay(CityMapPanel.Overlay.TRADE, trade);
    }

    private static Component selectionType(CityMapPanel.Selection value) {
        return Component.translatable("gui.createcityexpansioncore.world_map.selection."
                + value.kind().name().toLowerCase(Locale.ROOT));
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    /** Popovers hang under the global toolbar, wherever that toolbar ended up this layout. */
    private int popoverY() {
        return WorldMapUiLayout.popoverY(this.width, this.modeLabelWidths, this.globalLabelWidths);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private enum Mode {
        CITIES("minecraft:compass", "gui.createcityexpansioncore.world_map.mode.cities", CityMapPanel.Focus.WORLD, true),
        RAILWAY("create:track", "gui.createcityexpansioncore.world_map.mode.railway", CityMapPanel.Focus.RAILWAY, true),
        // The shipment seal, not an emerald: emerald reads as the vanilla villager currency, and a
        // freight mode has nothing to do with it.
        LOGISTICS("createcityexpansioncore:shipment_seal", "gui.createcityexpansioncore.world_map.mode.logistics", CityMapPanel.Focus.TRADE, true),
        ENERGY("minecraft:redstone", "gui.createcityexpansioncore.world_map.mode.energy", CityMapPanel.Focus.ENERGY, false);

        private final ItemStack icon;
        private final String labelKey;
        private final CityMapPanel.Focus focus;
        private final boolean available;

        Mode(String iconId, String labelKey, CityMapPanel.Focus focus, boolean available) {
            this.icon = item(iconId);
            this.labelKey = labelKey;
            this.focus = focus;
            this.available = available;
        }

        Component label() { return Component.translatable(this.labelKey); }

        /** A mode that is not shipped yet stays visible and disabled instead of hiding or faking data. */
        boolean available() { return this.available; }
    }
}
