package com.example.examplemod.client.screen;

import com.example.examplemod.client.logistics.AtlasTradeDirectory;
import com.example.examplemod.city.CityPublicProfile;
import com.example.examplemod.citymap.CityMapFeature;

import com.example.examplemod.client.CityWorldMapSession;
import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.client.logistics.LogisticsMapOverlay;
import com.example.examplemod.client.journey.JourneyClient;
import com.example.examplemod.network.RequestCityWorldMapPayload;
import com.example.examplemod.client.WorldMapDataSource;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.client.map.CityMapFeatures;
import com.example.examplemod.client.map.PassengerMapLayer;
import com.example.examplemod.client.map.MapMarkerRenderer;
import com.example.examplemod.client.map.AtlasPlaceIcon;
import com.example.examplemod.client.map.MapPalette;
import com.example.examplemod.client.map.AtlasGlyph;
import com.example.examplemod.client.map.DispatchPalette;
import com.example.examplemod.client.map.AtlasPreferences;
import com.example.examplemod.client.map.DispatchMotion;
import com.example.examplemod.client.map.MapPresentationDraw;
import java.nio.file.Path;
import java.util.UUID;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.RequestCityTerritoriesPayload;
import com.example.examplemod.client.uiedit.UiEditLayout;
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
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** Full-screen, map-first industrial atlas. */
public final class PolishedWorldMapScreen extends Screen {
    /* Dispatcher surfaces share one palette. City ownership and alerts keep their map semantics. */
    private static final int PANEL_ALT = WorldMapChrome.RAISED;
    private static final int FRAME = WorldMapChrome.EDGE;
    private static final int ACCENT = WorldMapChrome.ACCENT;
    private static final int TEXT = WorldMapChrome.TEXT;
    private static final int MUTED = WorldMapChrome.MUTED;
    private static final long TOOLTIP_DELAY_MILLIS = 350L;
    private static boolean tutorialSeen;

    /**
     * Экран пилона, из которого открыт атлас, или {@code null} в режиме «только чтение».
     *
     * <p>Нужен для возврата и для переходов в управление поездами и рельсами. В режиме
     * чтения переходов нет, поэтому экрана пилона за картой может не быть.
     */
    private final CityPylonScreen parent;
    private final WorldMapDataSource data;
    /**
     * Панель карты.
     *
     * <p>Не final и создаётся не сразу: в режиме чтения пилон города определяет сервер,
     * и до его ответа создать панель не из чего — она требует и пилон, и измерение. До
     * готовности рисуется экран загрузки.
     */
    private CityMapPanel map;
    private static final AtlasViewContext VIEWS = new AtlasViewContext();
    private final java.lang.ref.WeakReference<Object> connection =
            new java.lang.ref.WeakReference<>(net.minecraft.client.Minecraft.getInstance() == null ? null
                    : net.minecraft.client.Minecraft.getInstance().getConnection());
    private boolean finished, initialized;
    private AtlasViewContext.State journeyReturn;
    private AtlasViewContext.Target pendingSelection;
    private int pendingCardScroll;
    private String pendingCardKey="";
    private String restoredQuery = "";
    private UUID preferenceWorldId;
    private UUID directoryView = com.example.examplemod.client.CityPublicDirectory.viewId();
    private ResourceLocation returnDimension;
    private static java.lang.ref.WeakReference<PolishedWorldMapScreen> workspaceOwner =
            new java.lang.ref.WeakReference<>(null);
    public static PolishedWorldMapScreen activeView() {
        var mc=net.minecraft.client.Minecraft.getInstance();
        if (mc.screen instanceof PolishedWorldMapScreen atlas) return atlas.validReturn()?atlas:null;
        var owner=workspaceOwner.get();
        return owner!=null && owner.journeyWorkspaceOpen && owner.validReturn()?owner:null;
    }
    private boolean journeyWorkspaceOpen;
    /** Transfer the live map to its marker workspace without tearing down the shared session. */
    public void openJourneyWorkspace(String marker) {
        if (this.map == null) return;
        journeyReturn = captureView();
        journeyWorkspaceOpen = true;
        workspaceOwner = new java.lang.ref.WeakReference<>(this);
        minecraft.setScreen(new com.example.examplemod.client.journey.JourneyAtlasScreen(this, this.map, marker));
    }
    public void tickJourneyWorkspace() {
        if (this.map != null) this.data.drainMapTiles().forEach(this.map::receive);
    }
    public boolean ownsJourneyPanel(CityMapPanel shown) {
        return journeyWorkspaceOpen && !finished && shown == map && validReturn()
                && (!readOnly || java.util.Objects.equals(directoryView,com.example.examplemod.client.CityPublicDirectory.viewId()));
    }
    public static void disconnected() {
        lastDrawingCity = null;
        AtlasPlaces.clear();
        workspaceOwner.clear();
        VIEWS.clear();
    }
    public void finishJourneyWorkspace(boolean returning) {
        journeyWorkspaceOpen = false;
        workspaceOwner.clear();
        if (returning && journeyReturn != null && validReturn()) restoreView(journeyReturn);
        journeyReturn = null;
        if (!returning) removed();
    }
    /** Legacy map management stays read-only by key; projects use their own membership-checked commands. */
    private final boolean readOnly;
    private final ProjectPanel projectWorkspace = new ProjectPanel();
    private final ItemStack titleIcon = item("createcityexpansioncore:city_pylon");
    private final ItemStack cityIcon = item("createcityexpansioncore:city_pylon");
    private final ItemStack residentsIcon = item("minecraft:player_head");
    private final ItemStack backIcon = item("minecraft:arrow");
    private final ItemStack forwardIcon = item("minecraft:arrow");
    private final ItemStack centerIcon = item("minecraft:compass");
    private final ItemStack searchIcon = item("minecraft:spyglass");
    private final ItemStack layersIcon = item("minecraft:map");

    /** Glyphs for the global actions; every one of them also carries a visible label when it fits. */
    private static final com.example.examplemod.client.map.ToolbarGlyph[] GLOBAL_GLYPHS = com.example.examplemod.client.map.ToolbarGlyph.values();
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
    private List<AtlasSearch.Entry> searchResults = List.of(), searchCatalog = List.of();
    private AtlasSearch.Tab searchTab = AtlasSearch.Tab.ALL;
    private int searchFocus = -1, searchCursor, searchTicks;
    private String searchMessage = "", queryBeforeNaming = "", queryBeforeViews = "";
    private enum ViewEdit { NONE, CREATE, RENAME, DELETE }
    private ViewEdit viewEdit = ViewEdit.NONE;
    private UUID selectedView;
    private AtlasWorkspace library;
    private AtlasPlanEditor planEditor;
    private static final int PLAN_TOOL = 13;
    private static final int PLACE_TOOL=14, RECALCULATE_TOOL=15, PASSENGER_TOOL=16, CARTOGRAPHY_TOOL=17;
    private UUID drawingCity;
    private record DrawingCity(AtlasWorkspace.Area area, UUID id) {}
    private static DrawingCity lastDrawingCity;
    private boolean recalculateToolVisible;
    private Screen atlasTool;
    private UUID toolWorld;
    private ResourceLocation toolDimension;
    private long toolEpoch;
    public void openAtlasTool(Screen tool) {
        if(map==null || !planningAvailable() || tool==null)return;
        closeAtlasTool();
        map.cancelPersonalTools();map.interruptCamera();LogisticsMapOverlay.cancelAutomaticFocus();
        toolDimension=map.dimensionId();toolWorld=CityPublicDirectory.worldId(toolDimension);toolEpoch=CityPublicDirectory.accessEpoch();
        atlasTool=tool;tool.init(minecraft,width,height);
    }
    public boolean ownsAtlasTool(Screen tool){return tool!=null && atlasTool==tool && !finished;}
    public void closeAtlasTool() {
        var previous=atlasTool;atlasTool=null;toolWorld=null;toolDimension=null;
        if(previous!=null)previous.removed();
    }
    private boolean planningAvailable() {
        return library != null && data != null && library.area.dimension().equals(data.getDimensionId().toString())
                && library.area.world().equals(com.example.examplemod.client.CityPublicDirectory.worldId(data.getDimensionId()));
    }
    private void exitPlanEditor() {
        planEditor=null;
        if(map!=null)map.setPrecisionInput(false);
    }
    private void openPlanEditor() {
        if(!planningAvailable() || map==null)return;
        map.cancelPersonalTools();map.interruptCamera();map.setPrecisionInput(true);
        LogisticsMapOverlay.cancelAutomaticFocus();
        planEditor=new AtlasPlanEditor(map,library.engineeringDrafts(),this::exitPlanEditor);
        planEditor.init(minecraft,width,height);
    }
    private final java.util.Map<AtlasWorkspace.Area, AtlasWorkspace> viewLibraries = new java.util.LinkedHashMap<>();
    private static java.lang.ref.WeakReference<Object> lastLibraryConnection = new java.lang.ref.WeakReference<>(null);
    private static Path lastLibraryDirectory;
    private static AtlasWorkspace.Area lastLibraryArea;
    private record ViewRow(AtlasWorkspace library, AtlasWorkspace.View view) {}
    private List<ViewRow> visibleViews = List.of();
    private record SearchJump(UUID view, ResourceLocation dimension, String objectKey, AtlasWorkspace.Area area, UUID savedView) {}
    private SearchJump searchJump;
    private long searchJumpDeadline;
    private boolean jumpDirectoryReady;
    private final AtlasHoverDelay hoverDelay = new AtlasHoverDelay(TOOLTIP_DELAY_MILLIS);
    private int cardX, cardY, cardWidth, cardHeight;
    /** Top pixel of the first action row inside the context card, -1 when there is no action. */
    private int cardActionY = -1;
    private int cardActionCount;
    private List<AtlasObjectCard.Row> cardActions = List.of();
    private AtlasDirections directions;
    private String cardNotice = "";
    private int noticeTicks;
    private final AtlasFocus keyboard = new AtlasFocus();
    private Component tooltipOffered;
    private String focusSurface="";
    private final Deque<AtlasViewContext.Target> backHistory = new ArrayDeque<>();
    private final Deque<AtlasViewContext.Target> forwardHistory = new ArrayDeque<>();
    private boolean problemCenterOpen;
    private boolean guideOpen;
    private int searchOffset, searchType, searchOwner;
    private boolean searchProblems;
    private int problemOffset;
    private int cardScroll, cardMaxScroll;
    private int guideScroll, guideMaxScroll;
    private int layerScroll,legendScroll,mouseXNow,mouseYNow;
    /**
     * The logistics center owns its map and always needs the rail layer, but that must not rewrite
     * the atlas preference of a player who turned railways off on the normal world map. When the
     * session forces the layer on, the saved mask keeps its previous bit.
     */
    private boolean sessionForcedTracks;
    private String cardKey = "";

private AtlasPreferences preferences;
    private String preferenceWorld;
    private Runnable pendingMutation;
    private Component mutationTitle;
    private final List<WorldMapUiLayout.Rect> viewButtons = new ArrayList<>();
    private boolean trainActionVisible;
    private static final String[] VIEW_KEYS = {"player", "fit", "follow", "pin", "measure", "overview", "grid", "retry", "player_profile", "reduce_motion", "inertia", "terrain_filter", "context_hints"};
    private final DispatchMotion.Transition[] surfaceMotion = {
            new DispatchMotion.Transition(0), new DispatchMotion.Transition(0), new DispatchMotion.Transition(0),
            new DispatchMotion.Transition(0), new DispatchMotion.Transition(0)};
    private final double[] surfaceAlpha = new double[5];
    private final java.util.Map<String,DispatchMotion.Change> dispatchChanges = new java.util.HashMap<>();

    private DispatchMotion.Transition deliveredBar = new DispatchMotion.Transition(0), inFlightBar = new DispatchMotion.Transition(0);

    /** Атлас из вкладки «Карта»: панель и данные — из открытого пилона. */
    public PolishedWorldMapScreen(CityPylonScreen parent) {
        this(parent, parent.getMenu(), parent.getMapPanel(), false);
    }

    /**
     * Атлас по клавише: только чтение, без открытого пилона.
     *
     * <p>Панели здесь ещё нет: сервер определяет пилон города по членству игрока, и
     * до его ответа {@code CityMapPanel} создать не из чего. Экран рисует загрузку и
     * создаёт панель в первом тике, в котором данные уже пришли.
     */
    public PolishedWorldMapScreen(WorldMapDataSource data) {
        this(null, data, null, true);
    }

    private PolishedWorldMapScreen(
            CityPylonScreen parent,
            WorldMapDataSource data,
            CityMapPanel map,
            boolean readOnly
    ) {
        super(Component.translatable("gui.createcityexpansioncore.world_map.title"));
        this.parent = parent;
        this.data = data;
        this.map = map;
        this.readOnly = readOnly;
        if (this.map != null) {
            this.map.setDrawInternalControls(false);
        }
    }

    /**
     * Создаёт панель, когда сервер уже назвал пилон города.
     *
     * <p>Отдельным шагом потому, что пилон известен только из снимка: создать панель
     * раньше нельзя, а рисовать нужно сразу.
     */
    private void ensurePanel() {
        if (this.map != null) return;
        if (this.data.getPylonPos() == null) return;
        CityMapPanel panel = new CityMapPanel(
                this.data.getPylonPos(), this.data.getDimensionId(), this.data.getProjects());
        panel.setDrawInternalControls(false);
        this.map = panel;
        com.example.examplemod.client.map.MapDiagnostics.created();
        panel.receivePublicCities(com.example.examplemod.client.CityPublicDirectory.profiles(this.data.getDimensionId()));
        /*
         * Новая панель поднимается сразу в том режиме, который уже выбран.
         *
         * <p>Раньше вызывающий обязан был повторить applyMode(CITIES) после
         * ensurePanel(). Из init() это сделать нельзя: там панели ещё нет, и
         * повторный вызов падал. Приходящий пилон появляется позже, в tick(),
         * и режим, выбранный игроком до этого, должен был бы уцелеть — а вместо
         * этого всегда сбрасывался в «Города».
         */
        applyMode(this.mode);
        loadPreferences();
    }

    /**
     * Экран пилона, из которого открыт атлас.
     *
     * <p>Нужен обработчику снимка территорий: панель у атласа и у вкладки
     * «Карта» одна и та же, но кеш участков и граница в мире живут в экране
     * пилона. Без этого снимок, пришедший при открытом атласе, обновлял
     * карту и оставлял границу старой.
     */
    public CityPylonScreen getParentScreen() {
        return this.parent;
    }

    /** Меню пилона, к которому относится этот атлас. */
    public CityPylonMenu getMenu() {
        return this.data instanceof CityPylonMenu pylon ? pylon : null;
    }

    /** Открыта ли карта только для чтения. */
    public boolean isReadOnly() {
        return this.readOnly;
    }

    private net.minecraft.resources.ResourceLocation requestedDimension;
    public boolean acceptsDirectory(com.example.examplemod.network.CityDirectoryPayload payload) {
        if (requestedDimension != null) {
            if (!requestedDimension.equals(payload.dimension())) return false;
            if (payload.offset() == 0 && this.readOnly) CityWorldMapSession.INSTANCE.begin(payload.anchor(), payload.dimension());
            return true;
        }
        boolean accepted = this.data.getPylonPos() == null || payload.dimension().equals(this.data.getDimensionId());
        if (accepted && this.readOnly && payload.offset() == 0 && this.data.getPylonPos() != null && !payload.anchor().equals(this.data.getPylonPos())) {
            savePreferences(); discardOwnedPanel(); preferences = null; sessionForcedTracks = false;
            CityWorldMapSession.INSTANCE.begin(payload.anchor(), payload.dimension());
        }
        return accepted;
    }
    public void directoryReady(com.example.examplemod.network.CityDirectoryPayload payload) {
        requestedDimension = null;
        ensurePanel();
        if (map != null) map.receivePublicCities(com.example.examplemod.client.CityPublicDirectory.profiles(this.data.getDimensionId()));
        loadPreferences();
        resolvePending(true);
        rebuildSearchCatalog();
        jumpDirectoryReady=true;
        completeSearchJump();
    }
    public void acceptTerritories(com.example.examplemod.network.CityMapPeersPayload payload) {
        if (!payload.dimensionId().equals(data.getDimensionId())) return;
        ensurePanel();
        if (map != null) map.receivePeers(payload.peers());
    }

    @Override
    protected void init() {
        if (finished && validReturn()) {
            if (readOnly) {
                com.example.examplemod.client.CityPublicDirectory.open(returnDimension);
                directoryView=com.example.examplemod.client.CityPublicDirectory.viewId();
                map=null;preferences=null;preferenceWorld=null;
            }
            finished=false;initialized=false;
        }
        if (!initialized) com.example.examplemod.client.map.MapDiagnostics.opened();
        String searchText=this.searchBox==null?null:this.searchBox.getValue();
        if (!initialized) com.example.examplemod.client.ProjectClientState.begin();
        ensurePanel();
        loadPreferences();
        if (this.map != null) { this.map.setMotion(true,com.example.examplemod.client.map.MinimapClient.settings().reducedMotion); this.map.setGuiScale(1.0F); this.map.setDrawInternalControls(false); }
        applyMode(this.mode);
        if (!initialized) requestSnapshots(true);
        initialized = true;
        this.tutorialTicks = 0; // Persistent contextual onboarding replaces the session-only intro banner.
        measureLabels();
        // The search field is positioned from the same popover the renderer uses, so the box and the
        // plate around it cannot drift apart at any width.
        WorldMapUiLayout.Rect search = WorldMapUiLayout.searchPopover(this.width, this.modeLabelWidths, this.globalLabelWidths, 0, 228);
        this.searchBox = new EditBox(this.font, search.x() + 11, search.y() + 6, Math.max(8, search.width() - 22), 14,
                Component.translatable("gui.createcityexpansioncore.world_map.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(Component.translatable("gui.createcityexpansioncore.world_map.search.hint"));
        this.searchBox.setVisible(this.searchOpen);
        /*
         * Ответчик вызывается позже, когда панель уже есть, но поле может быть и
         * раньше: без проверки первый же символ до загрузки ронял бы тик.
         */
        this.searchBox.setResponder(query -> { searchOffset = searchCursor = 0;searchMessage="";updateSearchResults(); });
        int retainedOffset = this.searchOffset;
        this.searchBox.setValue(searchText == null ? restoredQuery : searchText);
        this.searchOffset = retainedOffset;
        addWidget(this.searchBox); // Painted with its popover so opacity and content stay together.
        if(this.searchOpen)setFocused(this.searchBox);
        if(planEditor!=null)planEditor.init(minecraft,width,height);
        if(atlasTool!=null)atlasTool.init(minecraft,width,height);
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

    private Screen communityReturn;
    private com.example.examplemod.client.logistics.LogisticsCenterScreen.View centerReturn;
    public com.example.examplemod.client.logistics.LogisticsCenterScreen.View logisticsOrigin(){return centerReturn;}
    private boolean planningSession;

    public void returnToCommunity(Screen screen){
        if (screen instanceof com.example.examplemod.client.logistics.LogisticsCenterScreen center) {
            centerReturn=center.view(); communityReturn=null;
        } else communityReturn=screen;
    }
    public void planningDimension(ResourceLocation dimension){requestedDimension=dimension;mode=Mode.CITIES;planningSession=true;}

    @Override
    public void onClose() {
        if(atlasTool!=null){atlasTool.onClose();return;}
        if(planEditor!=null){planEditor.onClose();return;}
        if (this.minecraft != null) closeTo(this.minecraft::setScreen);
    }
    void closeTo(java.util.function.Consumer<Screen> navigation) {
        Screen target = null;
        if (validReturn()) target=communityReturn!=null?communityReturn:centerReturn!=null?centerReturn.restore():parent;
        navigation.accept(target);
    }

    private java.util.UUID framedAlliance;
    public boolean validReturn() {
        if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null
                || minecraft.getConnection() != connection.get()) return false;
        return AtlasViewContext.canReturn(minecraft.player.containerMenu, parent == null ? null : parent.getMenu(),
                parent == null || minecraft.level != null && minecraft.level.dimension().location().equals(parent.getMenu().getDimensionId()));
    }
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {

        if (!validReturn()) { minecraft.setScreen(null); return; }
        if(planEditor!=null){
            if(planEditor.map!=map || !planningAvailable()) {planEditor.detach();exitPlanEditor();}
            else planEditor.tick();
        }
        com.example.examplemod.client.AllianceClient.ensure();
        if(this.map!=null&&com.example.examplemod.client.AllianceClient.focusedAlliance!=null&&!com.example.examplemod.client.AllianceClient.focusedAlliance.equals(framedAlliance)&&this.map.focusAlliance(com.example.examplemod.client.AllianceClient.focusedCities))framedAlliance=com.example.examplemod.client.AllianceClient.focusedAlliance;
        if (!this.readOnly && this.parent != null) this.parent.refreshMapClaimAccess();
        com.example.examplemod.client.ProjectClientState.tick();
        if(map!=null)CityMapFeatures.refresh(map.dimensionId());
        if (searchOpen && (++searchTicks % 10 == 0 || map!=null &&
                listedPlaces!=AtlasPlaces.selections(map.dimensionId()))) rebuildSearchCatalog();
        if (searchJump!=null && jumpDirectoryReady) completeSearchJump();
        if (this.tutorialTicks > 0 && --this.tutorialTicks == 0) tutorialSeen = true;
        if (this.map == null) {
            /*
             * Панель появляется на первом тике, в котором сервер уже назвал пилон.
             * До этого рисуется загрузка, а не пустой экран: игрок нажал клавишу и
             * должен видеть, что запрос ушёл, иначе карта выглядит сломанной.
             */
            ensurePanel();
            if (this.map == null) {
                return;
            }
            /*
             * Режим не задаётся здесь: ensurePanel() применяет уже выбранный.
             * Прежний applyMode(Mode.CITIES) после появления пилона глушил вкладку,
             * которую игрок успел выбрать, пока карта грузилась.
             */
            this.map.setGuiScale(1.0F);
            requestSnapshots(false);
        }
        if (noticeTicks > 0) noticeTicks--;
        if (directions != null && !directions.matches(map.getSelection(), map.dimensionId())) directions = null;
        if (directions != null || searchOpen || pendingSelection != null && pendingSelection.kind()==CityMapPanel.SelectionKind.LOGISTICS) {
            LogisticsMapOverlay.attach(map.dimensionId(),true);
        }
        var pinned=map.getSelection();
        AtlasPlaces.tick(map.dimensionId(),pinned);
        if(atlasTool!=null){
            if(!map.dimensionId().equals(toolDimension) || !java.util.Objects.equals(toolWorld,CityPublicDirectory.worldId(toolDimension))
                    || toolEpoch!=CityPublicDirectory.accessEpoch())closeAtlasTool();
            else atlasTool.tick();
        }
        AtlasTradeDirectory.tick(map.dimensionId(),searchOpen && searchTab!=AtlasSearch.Tab.VIEWS,
                searchBox==null?"":searchBox.getValue(),pinned==null?null:pinned.id(),
                pendingSelection!=null && pendingSelection.kind()==CityMapPanel.SelectionKind.LOGISTICS ? pendingSelection.id() : null);
        LogisticsMapOverlay.maintainInspection(pinned);
        LogisticsMapOverlay.maintainDirectory(directions != null || searchOpen || pinned!=null && pinned.kind()==CityMapPanel.SelectionKind.LOGISTICS
                || pendingSelection!=null && pendingSelection.kind()==CityMapPanel.SelectionKind.LOGISTICS);
        resolvePending(false);
        var selected=map.getSelection();
        if (selected!=null) {
            var updated=map.resolveSelection(selected.kind(),selected.id(),selected.pos());
            boolean waitForSnapshot=switch(selected.kind()) {
                case LOGISTICS -> true;
                case PROJECT -> !com.example.examplemod.client.ProjectClientState.hasMapSnapshot();
                case PLACE -> !AtlasPlaces.ready(map.dimensionId());
                case CARTOGRAPHY -> !CityMapFeatures.ready(map.dimensionId()) && CityMapFeatures.supported();
                case PASSENGER_LINE, PASSENGER_TRAIN -> !CityMapFeatures.railReady(map.dimensionId());
                default -> false;
            };
            if (updated==null && waitForSnapshot) {
                pendingSelection=AtlasViewContext.Target.from(selected);
                pendingCardScroll=cardScroll;
                pendingCardKey=cardKey;
            } else if (updated==null) showCardNotice("unavailable");
            map.setSelection(updated);
        }
        JourneyClient.refreshNavigation(map, data.getDimensionId());
        if (directions != null && directions.matches(map.getSelection(), map.dimensionId())) {
            var origin = minecraft.player.level().dimension().location().equals(map.dimensionId()) ? minecraft.player.blockPosition() : null;
            directions.update(origin, map.getSelection().pos(), net.minecraft.Util.getMillis());
        }
        if (++this.refreshTicks >= (this.readOnly ? 100 : 20)) {
            this.refreshTicks = 0;
            requestSnapshots(false);
        }
        if (this.map != null && this.mode == Mode.CITIES
                && this.map.isOverlayEnabled(CityMapPanel.Overlay.TRACKS)) {
            com.example.examplemod.client.logistics.LogisticsMapOverlay.attach(this.data.getDimensionId());
        }
    }

    private void requestSnapshots(boolean includeMap) {
        com.example.examplemod.client.CityPublicDirectory.refresh();
        requestVisibleTerritories();
    }

    private void requestVisibleTerritories() {
        if(this.map==null||this.data.getPylonPos()==null)return;
        int range=(int)Math.ceil(Math.max(this.width,this.height)/Math.max(.0625,this.map.zoom())/2)+100;
        if (this.readOnly) {
            com.example.examplemod.client.CityPublicDirectory.requestTerritories(
                    new net.minecraft.core.BlockPos((int)this.map.centerX(),0,(int)this.map.centerZ()),range);
            return;
        }
        PacketDistributor.sendToServer(new RequestCityTerritoriesPayload(this.data.getPylonPos(),
                new net.minecraft.core.BlockPos((int)this.map.centerX(),0,(int)this.map.centerZ()),range));
    }

    private long privateAccessEpoch = com.example.examplemod.client.CityPublicDirectory.accessEpoch();
    public void expireMapAccess() {
        privateAccessEpoch = com.example.examplemod.client.CityPublicDirectory.accessEpoch();
        if (!readOnly || map == null) return;
        if (pendingSelection == null) pendingSelection=AtlasViewContext.Target.from(map.getSelection());
        map.setProjects(List.of());
        map.setRelations(List.of());
        map.receivePeers(List.of());
        map.receivePublicCities(com.example.examplemod.client.CityPublicDirectory.profiles(data.getDimensionId()));
        map.clearSelection();
        pendingMutation = null;
        rebuildSearchCatalog();
    }

    private void syncMapData() {
        if (this.map == null) return;
        this.data.drainMapTiles().forEach(this.map::receive);

this.map.setProjects(this.data.getProjects());

if (this.data.getRelationsState() != null) this.map.setRelations(this.data.getRelationsState().cities());
else if (this.readOnly) this.map.setRelations(List.of());
if (this.readOnly && privateAccessEpoch != com.example.examplemod.client.CityPublicDirectory.accessEpoch()) {
    privateAccessEpoch = com.example.examplemod.client.CityPublicDirectory.accessEpoch();
    if (!com.example.examplemod.client.CityPublicDirectory.hasPrivateAccess()) expireMapAccess();
}

    }

    /**
     * Экран ожидания ответа сервера.
     *
     * <p>Показан ровно один вход: пилон города определяет сервер, и панель не может
     * существовать раньше его ответа. Молчаливый пустой экран читался бы как
     * «карта сломалась», поэтому здесь видно, что запрос ушёл.
     *
     * <p>Про отказ сервера. Раньше здесь было сказано, что при отказе экран «висит
     * до закрытия», и это было верным замыслом: сообщение «города нет» постороннему
     * перечислило бы города сервера. Но реализация оказалась хуже замысла — закрыть
     * такой экран было нельзя, потому что onClose() в режиме чтения не вызывал
     * setScreen. Игрок оказывался заперт на надписи «загрузка».
     *
     * <p>Теперь отказ различается от загрузки и закрывается. Формулировка «вы не
     * участник города» по-прежнему ничего не выдаёт: она не называет ни одного города
     * и не утверждает, что города вообще есть.
     */
    private void renderLoading(GuiGraphics graphics) {
        boolean denied = isDenied();
        graphics.fill(0, 0, this.width, this.height, 0xFF101719);
        int w = 320;
        int h = denied ? 92 : 74;
        int x = (this.width - w) / 2;
        int y = (this.height - h) / 2;
        plate(graphics,x,y,w,h);
        graphics.drawCenteredString(this.font,
                Component.translatable("gui.createcityexpansioncore.world_map.title"),
                this.width / 2, y + 14, ACCENT);
        if (denied) {
            /*
             * Отказ — не загрузка. Надпись «загрузка территорий» здесь была бы
             * ложью: запрос уже обработан и ответа с городом не будет.
             */
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.createcityexpansioncore.world_map.no_city"),
                    this.width / 2, y + 34, TEXT);
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.createcityexpansioncore.world_map.no_city_hint"),
                    this.width / 2, y + 52, MUTED);
        } else if (com.example.examplemod.client.CityPublicDirectory.failed()) {
            graphics.drawCenteredString(this.font,Component.translatable("gui.createcityexpansioncore.world_map.terrain.failed"),
                    this.width/2, y+32,TEXT);
            var retry = directoryRetryBounds();
            paintControl(graphics,retry,ControlGroup.STATUS,1,retry.contains(mouseXNow,mouseYNow),true,false,false,false,false);
            graphics.drawCenteredString(this.font,Component.translatable("gui.createcityexpansioncore.world_map.view.retry"),
                    this.width/2,retry.y()+4,TEXT);
        } else {
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.createcityexpansioncore.map.territory_pending"),
                    this.width / 2, y + 34, TEXT);
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.createcityexpansioncore.world_map.read_only"),
                    this.width / 2, y + 52, MUTED);
        }
    }

    /**
     * Отказал ли сервер.
     *
     * <p>Ответ приходит в CityWorldMapSession, а не в экран: экран создаётся раньше
     * ответа, и сессия — единственное, что живёт дольше одного открытия.
     */
    private WorldMapUiLayout.Rect directoryRetryBounds() {
        return new WorldMapUiLayout.Rect(width/2-42,(height-74)/2+48,84,20);
    }

    private boolean isDenied() {
        return this.data instanceof CityWorldMapSession session && session.isDenied();
    }

    /**
     * A marker changes kind the moment a train is registered (TRAIN_UNREGISTERED disappears and a
     * TRAIN marker takes its place). Re-pin the same train so the player keeps context and the card
     * immediately switches from "Register" to the type/management actions.
     */

/**
     * The render pipeline, in one fixed order:
     * background, terrain and map geometry, markers, the chrome, the context column, the tutorial
     * strip, then any modal, then the tooltip. A modal is the last thing painted and the only thing
     * that takes input, so no marker can ever end up on top of it and no ordinary tooltip can leak
     * through a dim overlay.
     */
    private WorldMapUiLayout.Rect editControl(String key, WorldMapUiLayout.Rect bounds) {
        return UiEditLayout.apply("map." + key, bounds.x(), bounds.y(), bounds.width(), bounds.height(),
                WorldMapUiLayout.Rect::new);
    }

    private WorldMapUiLayout.Rect modeControl(int index) {
        return editControl("mode[" + index + "]", WorldMapUiLayout.modeTab(this.width, index, this.modeLabelWidths));
    }

    private WorldMapUiLayout.Rect globalControl(int index) {
        return editControl("toolbar[" + index + "]",
                WorldMapUiLayout.globalButton(this.width, index, this.modeLabelWidths, this.globalLabelWidths));
    }

    private WorldMapUiLayout.Rect searchResultControl(int index) {
        return editControl("searchResult[" + index + "]",
                WorldMapUiLayout.searchRow(this.width, this.modeLabelWidths, this.globalLabelWidths, index, 228));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!validReturn()) return;
        UiEditLayout.clearElements("map.");
        this.mouseXNow=mouseX;this.mouseYNow=mouseY;
        controlFrame = System.nanoTime();
        pointerDown = GLFW.glfwGetMouseButton(minecraft.getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
                == GLFW.GLFW_PRESS;
        syncMapData();
        if(atlasTool!=null){atlasTool.render(graphics,mouseX,mouseY,partialTick);return;}
        if(planEditor!=null) {
            if(planEditor.map==map && planningAvailable()){planEditor.render(graphics,mouseX,mouseY,partialTick);return;}
            planEditor.detach();exitPlanEditor();
        }
        if (this.map == null) {
            renderLoading(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        tooltipOffered=null;
        keyboard.begin();
        if (!focusSurface.equals(focusSurface())) { keyboard.reset(); focusSurface=focusSurface(); }
        boolean modalOpen = this.guideOpen || this.problemCenterOpen || this.pendingMutation != null || this.tutorialTicks > 0;
        long now=System.nanoTime();
        boolean[] surfaces={this.layersOpen&&!modalOpen,this.searchOpen&&!modalOpen,this.problemCenterOpen,this.guideOpen,this.pendingMutation!=null};
        for(int i=0;i<surfaces.length;i++)surfaceAlpha[i]=surfaceMotion[i].value(surfaces[i]?1:0,now,this.map.reducedMotion(),140);
        boolean modal = modalOpen || closingModal();
        int toolsBottom = layoutViewButtons();
        int hintHeight = 0;
        Component toolHint = null;
        if (map.pinToolActive() || map.measureToolActive()) {
            toolHint = Component.translatable("gui.createcityexpansioncore.world_map.tool." + (map.pinToolActive() ? "pin" : "measure"));
            int hintWidth = Math.max(1,WorldMapUiLayout.contextPanelLeft(width) - 2 * NORMAL - 2 * MICRO);
            hintHeight = font.wordWrapHeight(toolHint,hintWidth) + 2 * MICRO;
        }
        workspace = WorldMapUiLayout.workspace(width,height,toolsBottom,hintHeight,map.getSelection()!=null);
        var viewport = workspace.map();
        this.map.setChromeAvoidRight(workspace.inspector().x());
        graphics.fill(0, 0, this.width, this.height, MapPalette.PAPER);
        boolean pointerOnMap = !modal && !overActivePopover(mouseX,mouseY) && !closingPopover(mouseX,mouseY);
        int mapMouseX = pointerOnMap ? mouseX : -1, mapMouseY = pointerOnMap ? mouseY : -1;
        projectWorkspace.prepareMap(this.map,this.data.getDimensionId(),viewport,mapMouseX,mapMouseY);
        this.map.setProjectFocus(projectWorkspace.open() && this.mode==Mode.CITIES,projectWorkspace.territoryPicking() && this.mode==Mode.CITIES);
        this.map.setPrecisionInput(false);
        if (this.mode==Mode.CITIES && !modal) com.example.examplemod.client.logistics.LogisticsMapOverlay.updateFollow(this.map);
        this.map.setVisualOverlay(this.mode==Mode.CITIES && projectWorkspace.mapLayerVisible() && !modal ? projectWorkspace::renderMapLayer : null);
        this.map.setRailOverlay(this.mode==Mode.CITIES && this.map.isOverlayEnabled(CityMapPanel.Overlay.TRACKS)
                ? com.example.examplemod.client.logistics.LogisticsMapOverlay::renderRails : null);
        this.map.setTrackCount(this.mode==Mode.CITIES
                ? com.example.examplemod.client.logistics.LogisticsMapOverlay.railCount(this.data.getDimensionId()) : 0);
        if (this.mode == Mode.CITIES) com.example.examplemod.client.logistics.LogisticsMapOverlay.prepareView(this.map,this.data.getDimensionId(),viewport.width(),viewport.height());
        this.map.deferLabels();
        this.map.render(graphics,viewport.x(),viewport.y(),viewport.width(),viewport.height(),mapMouseX,mapMouseY);
        boolean overlaysInteractive = mode==Mode.CITIES && !modal && !layersOpen && !searchOpen && !projectWorkspace.open();
        ProjectWorkspaceClient.render(graphics,map,data.getDimensionId(),viewport,mapMouseX,mapMouseY,overlaysInteractive);
        com.example.examplemod.client.logistics.LogisticsMapOverlay.render(graphics,map,data.getDimensionId(),viewport.x(),viewport.y(),viewport.width(),viewport.height(),mapMouseX,mapMouseY,overlaysInteractive);
        com.example.examplemod.client.map.GeologyWideScanOverlay.render(graphics,map,data.getDimensionId(),viewport.x(),viewport.y(),viewport.width(),viewport.height(),mapMouseX,mapMouseY,overlaysInteractive);
        if (surfaceAlpha[0]>0) reserveCaptionSpace(layersPopoverBounds());
        if (surfaceAlpha[1]>0) reserveCaptionSpace(WorldMapUiLayout.searchPopover(
                width,modeLabelWidths,globalLabelWidths,visibleSearchResults().size(),228));
        this.map.drawLabels(graphics);
        // 3-4: markers are inside the panel render above; 5-6: chrome and the context column.
        renderTitle(graphics);
        renderModeToolbar(graphics, mouseX, mouseY);
        renderActionToolbar(graphics, mouseX, mouseY);
        renderViewButtons(graphics, mouseX, mouseY);
        int hintTop = workspace.inspector().y();
        if (toolHint!=null && viewport.y()>hintTop) {
            WorldMapChrome.rounded(graphics,viewport.x(),hintTop,viewport.width(),viewport.y()-hintTop,4,WorldMapChrome.INSET);
            graphics.enableScissor(viewport.x(),hintTop,viewport.right(),viewport.y());
            try {
                graphics.drawWordWrap(font,toolHint,viewport.x()+MICRO,hintTop+MICRO,
                        Math.max(1,viewport.width()-2*MICRO),MUTED);
            } finally { graphics.disableScissor(); }
        }
        if (!projectWorkspace.open() || this.mode != Mode.CITIES) {
            renderContextCard(graphics);
            if (workspace.legend()!=null) renderModeLegend(graphics);
        } else {
            cardWidth=cardHeight=cardActionCount=0;
        }
        renderStatus(graphics, mouseX, mouseY);
        if(surfaceAlpha[0]>0)MapPresentationDraw.foreground(graphics,200,surfaceAlpha[0],()->renderLayers(graphics,mouseX,mouseY));
        if(surfaceAlpha[1]>0)MapPresentationDraw.foreground(graphics,200,surfaceAlpha[1],()->renderSearchPanel(graphics,mouseX,mouseY));
        // 7-10: dim, tutorial target, modal surface and its widgets, then the tooltip last.
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.tutorialTicks > 0) renderTutorial(graphics);
        if(surfaceAlpha[2]>0)MapPresentationDraw.foreground(graphics,300,surfaceAlpha[2],()->renderProblemCenter(graphics));
        if(surfaceAlpha[3]>0)MapPresentationDraw.foreground(graphics,300,surfaceAlpha[3],()->renderGuide(graphics));
        if(surfaceAlpha[4]>0)MapPresentationDraw.foreground(graphics,300,surfaceAlpha[4],()->renderMutation(graphics));
        var focus=keyboard.current();
        if (focus!=null) {
            var r=visibleFocusBounds(focus);
            if (r.width()>0 && r.height()>0) MapPresentationDraw.foreground(graphics,340,1,
                    ()->graphics.renderOutline(r.x(),r.y(),r.width(),r.height(),WorldMapChrome.TEXT));
        }
        Component tooltip=hoverDelay.samplePointer(map.isMapDragging() || closingModal() ? null : tooltipOffered,
                net.minecraft.Util.getMillis(),mouseX,mouseY);
        if (tooltip!=null) MapPresentationDraw.foreground(graphics,350,1,()->renderTooltip(graphics,tooltip,mouseX,mouseY));
    }

    private void renderTitle(GuiGraphics graphics) {
        WorldMapUiLayout.Rect plate = WorldMapUiLayout.title(this.width);
        plate(graphics, plate.x(), plate.y(), plate.width(), plate.height());
        MapMarkerRenderer.drawGlyph(graphics, AtlasGlyph.CITY, plate.x() + MICRO, plate.y() + MICRO, ACCENT);
        Component dimension=Component.literal(this.data.getDimensionId().toString());
        offerTooltip(dispatchText("dimension",dimension),plate.contains(this.mouseXNow,this.mouseYNow));
        if (plate.width() <= 32) {
            if(this.readOnly&&com.example.examplemod.client.CityPublicDirectory.dimensions.size()>1)
                graphics.drawCenteredString(this.font,"↔",plate.x()+plate.width()/2,plate.bottom()-10,ACCENT);
            return;
        }
        graphics.drawString(this.font,
                WorldMapChrome.fit(this.font,dispatchText("title"),plate.width()-34),
                plate.x() + 28, plate.y() + 4, ACCENT, false);
        WorldMapChrome.rounded(graphics,plate.x()+25,plate.y()+14,plate.width()-29,14,3,PANEL_ALT);
        graphics.drawString(this.font,WorldMapChrome.fit(this.font,Component.literal(this.data.getDimensionId().getPath()),plate.width()-45),
                plate.x() + 29, plate.y() + 17, TEXT, false);
        if (this.readOnly && com.example.examplemod.client.CityPublicDirectory.dimensions.size() > 1)
            graphics.drawString(this.font, "↔", plate.right() - 12, plate.y() + 17, ACCENT, false);
    }

    /**
     * Mode tabs. The label is part of the tab whenever there is room for it, so what the map is
     * showing is readable without hovering anything; the selected tab is filled and outlined rather
     * than merely tinted. Electricity stays visible but disabled, so the player can see it is coming
     * without the map pretending to have power data.
     */
    private final ControlMotion.TabIndicator modeIndicator = new ControlMotion.TabIndicator();
    private final ControlMotion.TabIndicator searchIndicator = new ControlMotion.TabIndicator();
    private enum ControlGroup { MODE, GLOBAL, VIEW, LAYER, SEARCH, RESULT, CARD, MODAL, STATUS }
    private static final int MOTION_SLOTS = 32;
    private final ControlMotion[][] controls = new ControlMotion[ControlGroup.values().length][MOTION_SLOTS];
    private long controlFrame;
    private double pressX=Double.NaN, pressY=Double.NaN;
    private boolean pointerDown;

    private void paintControl(GuiGraphics g,WorldMapUiLayout.Rect bounds,ControlGroup group,int index,
                              boolean hover,boolean enabled,boolean selected,boolean primary,
                              boolean danger,boolean quiet) {
        ControlMotion motion = null;
        if (index < MOTION_SLOTS) {
            var groupControls = controls[group.ordinal()];
            motion = groupControls[index];
            if (motion == null) groupControls[index] = motion = new ControlMotion();
            boolean down = pointerDown && bounds.contains(pressX,pressY);
            motion.update(controlFrame,map == null || map.reducedMotion(),enabled && hover,
                    enabled && hover && down,selected);
        }
        WorldMapChrome.atlasControl(g,bounds,enabled,primary,danger,quiet,motion,hover,selected);
    }
    private void renderModeToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        var activeTab=modeControl(this.mode.ordinal());
        WorldMapUiLayout.Rect tabs = WorldMapUiLayout.modeTabs(this.width, this.modeLabelWidths);
        plate(graphics, tabs.x() - MICRO, tabs.y() - MICRO, tabs.width() + MICRO * 2, tabs.height() + MICRO * 2);
        Mode[] modes = Mode.values();
        for (int index = 0; index < modes.length; index++) {
            Mode value = modes[index];
            WorldMapUiLayout.Rect bounds = modeControl(index);
            boolean selected = value == this.mode;
            boolean enabled = value.available();
            boolean hover = enabled && bounds.contains(mouseX, mouseY);
            paintControl(graphics,bounds,ControlGroup.MODE,index,hover && mainControls(),enabled,selected,false,false,false);

            MapMarkerRenderer.drawGlyph(graphics, switch (value) { case CITIES -> AtlasGlyph.CITY; case ENERGY -> AtlasGlyph.ENERGY; }, bounds.x() + MICRO, bounds.y() + (bounds.height()-AtlasGlyph.SIZE)/2, !enabled ? MUTED : selected ? ACCENT : TEXT);
            if (WorldMapUiLayout.modeTabsLabelled(this.width)) {
                int labelColour = !enabled ? MUTED : selected ? ACCENT : TEXT;
                graphics.drawString(this.font, value.label(), bounds.x() + MICRO + 16 + MICRO, bounds.y() + (bounds.height()-8)/2,
                        labelColour, false);
            }
            Component hint = !enabled
                    ? Component.translatable("gui.createcityexpansioncore.world_map.mode.energy.soon")
                    : value.label();
            if (enabled) hint=atlasText("mode");
            if (mainControls()) {
                keyboard.add("mode."+index,value.label(),hint,bounds,()->{if(value.available())applyMode(value);});
                offerTooltip(hint,bounds.contains(mouseX,mouseY));
            }
        }
        modeIndicator.paint(graphics,tabs,activeTab.x()+NORMAL,activeTab.right()-NORMAL,activeTab.bottom()-1,
                controlFrame,map.reducedMotion());
    }

    /**
     * Global actions only: navigation, view, help, close. Mode-specific actions live in the context
     * panel of the selected object, so nothing here changes meaning when the mode changes.
     */
    private void renderActionToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        WorldMapUiLayout.Rect bar = WorldMapUiLayout.globalToolbar(this.width, this.modeLabelWidths, this.globalLabelWidths);
        plate(graphics, bar.x() - MICRO, bar.y() - MICRO, bar.width() + MICRO * 2, bar.height() + MICRO * 2);
        int problems = problemCount();
        boolean toolbarHover = !guideOpen && !problemCenterOpen && pendingMutation==null && !closingModal()
                && !map.hasPendingClaim() && tutorialTicks==0;
        for (int index = 0; index < WorldMapUiLayout.GLOBAL_ACTIONS; index++) {
            WorldMapUiLayout.Rect bounds = globalControl(index);
            boolean active = (index == WorldMapUiLayout.GLOBAL_SEARCH && this.searchOpen)
                    || (index == WorldMapUiLayout.GLOBAL_LAYERS && this.layersOpen)
                    || (index == WorldMapUiLayout.GLOBAL_PROBLEMS && this.problemCenterOpen)
                    || (index == WorldMapUiLayout.GLOBAL_HELP && this.guideOpen);
            boolean hover = toolbarHover && bounds.contains(mouseX, mouseY);
            boolean enabled = (index != WorldMapUiLayout.GLOBAL_BACK || canGoBack())
                    && (index != WorldMapUiLayout.GLOBAL_FORWARD || canGoForward());
            paintControl(graphics,bounds,ControlGroup.GLOBAL,index,hover,enabled,active,false,false,false);
            if(index==2||index==5||index==7)graphics.vLine(bounds.x()-MICRO-1,bounds.y()+6,bounds.bottom()-6,FRAME);
            boolean labelled = WorldMapUiLayout.globalToolbarLabelled(this.width,
                    this.modeLabelWidths, this.globalLabelWidths);
            int glyphX = labelled ? bounds.x() + MICRO : bounds.x() + (bounds.width() - 16) / 2;
            int glyphY = bounds.y() + (bounds.height() - 16) / 2;
            int color = !enabled ? MUTED : active || hover ? ACCENT : TEXT;
            var sharedIcon = switch (index) {
                case WorldMapUiLayout.GLOBAL_BACK -> SocialHubIcons.Icon.CHAT_BACK;
                case WorldMapUiLayout.GLOBAL_FORWARD -> SocialHubIcons.Icon.NEXT;
                case WorldMapUiLayout.GLOBAL_SEARCH -> SocialHubIcons.Icon.SEARCH;
                case WorldMapUiLayout.GLOBAL_CLOSE -> SocialHubIcons.Icon.CLOSE;
                default -> null;
            };
            if (sharedIcon != null && enabled) {
                sharedIcon.draw(graphics,glyphX,glyphY,active || hover,0);
            } else {
                var glyph = GLOBAL_GLYPHS[index];
                for (int y = 0; y < SocialHubIcons.SIZE; y++) for (int x = 0; x < SocialHubIcons.SIZE; x++)
                    if (glyph.pixel(x,y)) graphics.fill(glyphX+x,glyphY+y,glyphX+x+1,glyphY+y+1,color);
            }
            Component label = Component.translatable(GLOBAL_KEYS[index]);
            if (labelled) {
                graphics.drawString(this.font, label, bounds.x() + MICRO + 16 + MICRO, bounds.y() + NORMAL + 1,
                        enabled ? TEXT : MUTED, false);
            }
            if (index == WorldMapUiLayout.GLOBAL_PROBLEMS && problems > 0) {
                graphics.fill(bounds.right() - 7, bounds.y() + 2, bounds.right() - 1, bounds.y() + 8, 0xFF171D20);
                graphics.fill(bounds.right() - 6, bounds.y() + 3, bounds.right() - 2, bounds.y() + 7, WorldMapChrome.WARNING);
                label = label.copy().append(": " + problems);
            }
            final int action=index;
            Component help=atlasText(!enabled ? "history_empty" : "global."+index);
            if(mainControls()) {
                keyboard.add("global."+index,label,help,bounds,()->{if(enabled) handleGlobalAction(action);});
                offerTooltip(help,hover);
            }

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
                    CityMapPanel.Overlay.TRACKS, CityMapPanel.Overlay.RELATIONS, CityMapPanel.Overlay.PROJECTS);

case ENERGY -> List.of();
        };
    }

private int layersRowCount() {
        return activeOverlays().size() + 2 + (VIEW_KEYS.length - 5);
    }

    private void reserveCaptionSpace(WorldMapUiLayout.Rect rect) {
        map.labels().reserve(rect.x(),rect.y(),rect.width(),rect.height());
    }

    private void renderLayers(GuiGraphics graphics, int mouseX, int mouseY) {
        WorldMapUiLayout.Rect panel = layersPopoverBounds();
        WorldMapChrome.popover(graphics,panel.x(),panel.y(),panel.width(),panel.height());
        boolean overBody = layersOpen && "layers".equals(focusSurface())
                && panel.contains(mouseX,mouseY) && mouseY >= panel.y()+WorldMapUiLayout.POPOVER_HEADER;
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.layers"),
                panel.x() + WorldMapUiLayout.SECTION, panel.y() + WorldMapUiLayout.NORMAL - 2, ACCENT, false);
        graphics.enableScissor(panel.x(), panel.y() + WorldMapUiLayout.POPOVER_HEADER, panel.right(), panel.bottom());
        try {
        int row = 0;
        for (CityMapPanel.Overlay overlay : activeOverlays()) {
            WorldMapUiLayout.Rect bounds = layerRow(row++);
            boolean enabled = this.map.isOverlayEnabled(overlay);
            boolean hover = overBody && bounds.contains(mouseX,mouseY);
            paintControl(graphics,bounds,ControlGroup.LAYER,row-1,hover,true,enabled,false,false,true);
            int toggleAccent = overlay==CityMapPanel.Overlay.TRACKS ? DispatchPalette.ACCENT : ACCENT;
            WorldMapChrome.toggleMark(graphics,bounds.x()+MICRO,bounds.y()+5,enabled,toggleAccent);
            String count = Integer.toString(this.map.overlayCount(overlay));
            Component label = Component.translatable("gui.createcityexpansioncore.world_map.overlay." + overlay.name().toLowerCase(Locale.ROOT));
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label.getString(), bounds.width() - this.font.width(count) - 24), bounds.x() + 14, bounds.y() + 5, enabled ? TEXT : MUTED, false);
            if (layersOpen && "layers".equals(focusSurface())) {
                var help=atlasText("toggle_layer",label);
                keyboard.add("layer."+row,label,help,bounds,()->toggleLayer(overlay));
                offerTooltip(help, hover && panel.contains(mouseX,mouseY));
            }
            graphics.drawString(this.font, count, bounds.right() - this.font.width(count) - MICRO, bounds.y() + 5, MUTED, false);
        }
        for(int option=0;option<2;option++) {
            var bounds=layerRow(row++);boolean hover=overBody && bounds.contains(mouseX,mouseY);
            paintControl(graphics,bounds,ControlGroup.LAYER,row-1,hover,true,option==1 && map.plotGrid(),false,false,true);
            Component label=Component.translatable("gui.createcityexpansioncore.world_map."+(option==0?"territory_mode."+this.map.territoryMode():"plot_grid."+this.map.plotGrid()));
            graphics.drawString(this.font,WorldMapChrome.fit(this.font,label,bounds.width()-12),bounds.x()+6,bounds.y()+5,TEXT,false);
            final int presentation=option;
            if ("layers".equals(focusSurface())) {
                var hint=atlasText("presentation."+option);
                keyboard.add("layer."+row,label,hint,bounds,()->toggleTerritoryPresentation(presentation==0));
                offerTooltip(hint,hover);
            }
        }
        for (int index = 5; index < VIEW_KEYS.length; index++) {
            var bounds = layerRow(row++);
            boolean enabled = index == 5 && this.map.overviewVisible() || index == 6 && this.map.coordinateGrid() || index == 9 && this.map.reducedMotion() || index == 10 && com.example.examplemod.client.map.MinimapClient.settings().inertia || index == 12 && com.example.examplemod.client.map.MinimapClient.settings().contextHints;
            boolean hover = overBody && bounds.contains(mouseX,mouseY);
            paintControl(graphics,bounds,ControlGroup.LAYER,row-1,hover,true,enabled,false,false,true);
            String option = index == 11 ? "terrain_filter." + (com.example.examplemod.client.map.MinimapClient.settings().smoothTerrain ? "smooth" : "sharp") : VIEW_KEYS[index];
            Component label = Component.translatable("gui.createcityexpansioncore.world_map.view." + option);
            graphics.drawString(this.font, index == 8 ? "▣" : index == 7 ? "↻" : enabled ? "◆" : "◇", bounds.x() + MICRO, bounds.y() + 5, ACCENT, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label.getString(), bounds.width() - 20), bounds.x() + 14, bounds.y() + 5, TEXT, false);
            final int setting=index;
            if ("layers".equals(focusSurface())) {
                var hint=atlasText("view."+index);
                keyboard.add("layer."+row,label,hint,bounds,()->handleViewAction(setting));
                offerTooltip(hint,hover);
            }
        }

        } finally { graphics.disableScissor(); }
    }

    private void renderSearchPanel(GuiGraphics g,int mx,int my) {
        var rows=visibleSearchResults(); var p=searchPanelBounds();
        WorldMapChrome.popover(g,p.x(),p.y(),p.width(),p.height());
        WorldMapChrome.field(g,p.x()+7,p.y()+5,p.width()-14,20,searchOpen && searchFocus==-1,
                searchOpen && searchBox.isMouseOver(mx,my));
        for(int i=0;i<3;i++) {
            var label=i==0?searchTypeLabel():i==1?Component.translatable("gui.createcityexpansioncore.world_map.filter.owner."+searchOwner)
                    :Component.translatable("gui.createcityexpansioncore.world_map.filter."+(searchProblems?"problems":"any_status"));
            searchButton(g,searchFilterBounds(i),label,i,mx,my,AtlasSearch.tr("filter_help",label));
        }
        for(var tab:AtlasSearch.Tab.values()) {
            var label=AtlasSearch.tr("tab."+tab.name().toLowerCase(Locale.ROOT));
            searchButton(g,searchTabBounds(tab.ordinal()),label,3+tab.ordinal(),mx,my,AtlasSearch.tr("tab_help",label));
        }
        var selectedTab = searchTabBounds(searchTab.ordinal());
        searchIndicator.paint(g,p,selectedTab.x()+3,selectedTab.right()-3,selectedTab.bottom()-1,
                controlFrame,map.reducedMotion());
        for(int i=0;i<rows.size();i++) {
            var r=searchResultControl(i);var value=rows.get(i);
            String title,context;
            if(value instanceof AtlasSearch.Entry e) {title=e.name();context=e.context();}
            else {var v=(ViewRow)value; title=v.view.name();context=v.library.area.dimension()+" · "+Math.round(v.view.camera().x())+", "+Math.round(v.view.camera().z());}
            boolean active=searchCursor==searchOffset+i;
            paintControl(g,r,ControlGroup.RESULT,i,searchOpen && r.contains(mx,my),true,active,false,false,true);
            if(active&&searchFocus==7)g.renderOutline(r.x(),r.y(),r.width(),r.height(),ACCENT);
            int textX=r.x()+4;
            if(value instanceof AtlasSearch.Entry entry){
                var icon=AtlasPlaceIcon.forSelection(entry.selection(),entry.dimension());
                if(icon!=null){
                    icon.draw(g,textX,r.y()+(r.height()-16)/2,16);
                    textX+=20;
                }
            }
            int w=Math.max(1,r.right()-textX-(value instanceof AtlasSearch.Entry?21:5));
            g.drawString(font,font.plainSubstrByWidth(title,w),textX,r.y()+2,TEXT,false);
            g.drawString(font,font.plainSubstrByWidth(context,w),textX,r.y()+13,MUTED,false);
            if(value instanceof AtlasSearch.Entry e) {
                g.drawString(font,library!=null&&library.favorite(e.key())?"★":"☆",r.right()-16,r.y()+7,ACCENT,false);
                if(r.contains(mx,my)&&mx>=r.right()-20)offerTooltip(AtlasSearch.tr("star_help"),true);
                else offerTooltip(Component.literal(title+" · "+context),r.contains(mx,my));
            } else offerTooltip(Component.literal(title+" · "+context),r.contains(mx,my));
        }
        if(rows.isEmpty() || viewEdit!=ViewEdit.NONE || !searchMessage.isEmpty()) {
            Component message=AtlasSearch.tr(!searchMessage.isEmpty()?searchMessage:viewEdit==ViewEdit.NONE?"empty":"edit."+viewEdit.name().toLowerCase(Locale.ROOT));
            g.fill(p.x()+5,p.y()+68,p.right()-5,p.y()+91,PANEL_ALT);
            g.drawWordWrap(font,message,p.x()+8,p.y()+71,p.width()-16,MUTED);
        }
        if(searchTab==AtlasSearch.Tab.VIEWS) {
            String[] actions=viewActions();
            for(int i=0;i<actions.length;i++)searchButton(g,searchFooter(i,actions.length),AtlasSearch.tr(actions[i]),8+i,mx,my,AtlasSearch.tr(actions[i]+"_help"));
        } else {
            var dimension=data.getDimensionId();
            boolean waiting=com.example.examplemod.client.CityPublicDirectory.loading() || !LogisticsMapOverlay.hasDirectory(dimension);
            boolean tradeFilter=searchType==AtlasSearch.Type.TRADE.ordinal();
            var status=tradeFilter && !waiting && !LogisticsMapOverlay.supportsTrade(dimension)
                    ? Component.translatable("gui.createcityexpansioncore.atlas.trade.unsupported")
                    : AtlasSearch.tr(waiting || tradeFilter && AtlasTradeDirectory.loading(dimension)?"loading":"local",searchResults.size());
            g.drawString(font,font.plainSubstrByWidth(status.getString(),p.width()-12),p.x()+6,p.bottom()-14,MUTED,false);
        }
        boolean shown=searchBox.isVisible();searchBox.setVisible(true);
        try {searchBox.render(g,mx,my,0);} finally {searchBox.setVisible(shown);}
    }
    private void searchButton(GuiGraphics g,WorldMapUiLayout.Rect r,Component label,int index,int mx,int my,Component help) {
        boolean active=index>=3&&index<7&&searchTab.ordinal()==index-3;
        boolean confirm = viewEdit!=ViewEdit.NONE && index==8;
        boolean danger = confirm && viewEdit==ViewEdit.DELETE;
        paintControl(g,r,ControlGroup.SEARCH,index,searchOpen && r.contains(mx,my),true,active,confirm && !danger,danger,!confirm);
        if(index==searchFocus)g.renderOutline(r.x(),r.y(),r.width(),r.height(),ACCENT);
        g.drawString(font,WorldMapChrome.fit(font,label,Math.max(1,r.width()-5)),r.x()+3,r.y()+Math.max(0,(r.height()-8)/2),
                confirm&&!danger?WorldMapChrome.PRIMARY_TEXT:active?ACCENT:TEXT,false);
        offerTooltip(help,r.contains(mx,my));
    }

    /**
     * The right-hand column. With nothing selected it is an operational overview rather than an
     * empty box, because opening the map should answer "what is going on", not "here is a list of
     * errors". A pinned selection always wins over the overview.
     */
    private String motionCardKey="";
    private long motionCardSince;
    private void renderContextCard(GuiGraphics graphics) {
        CityMapPanel.Selection value = this.map.getSelection();
        boolean pinned = value != null;
        if (!pinned) value = this.map.getHoveredSelection();
        if (value == null) {
            this.cardActions = List.of();
            this.cardActionCount = 0;
            this.cardActionY = -1;
            motionCardKey="";

            renderOverview(graphics);
            return;
        }
        String key=value.kind()+":"+(value.id()!=null?value.id():value.pos());
        if(!key.equals(motionCardKey)) { motionCardKey=key; motionCardSince=System.nanoTime(); }
        renderSelectionPanel(graphics, value, pinned);
        double reveal=this.map.reducedMotion()?1:com.example.examplemod.client.map.MapMotion.ease((System.nanoTime()-motionCardSince)/140_000_000.0);
        if(reveal<1) graphics.fill(this.cardX+1,this.cardY+1,this.cardX+this.cardWidth-1,this.cardY+this.cardHeight-1,
            ((int)(120*(1-reveal))<<24)|(MapPalette.PAPER&0xFFFFFF));
    }

    private static Component dispatchText(String key,Object... args){return Component.translatable("gui.createcityexpansioncore.world_map.dispatch."+key,args);}
    private double changed(String key,Object value,long duration){return this.dispatchChanges.computeIfAbsent(key,ignored->new DispatchMotion.Change())
            .observe(value,System.nanoTime(),this.map.reducedMotion(),duration);}
    private boolean closingModal(){
        long now=System.nanoTime();return !this.problemCenterOpen&&surfaceMotion[2].sample(now)>0
                ||!this.guideOpen&&surfaceMotion[3].sample(now)>0||this.pendingMutation==null&&surfaceMotion[4].sample(now)>0;
    }
    private boolean closingPopover(double x,double y){
        long now=System.nanoTime();return !this.layersOpen&&surfaceMotion[0].sample(now)>0&&layersPopoverBounds().contains(x,y)
                ||!this.searchOpen&&surfaceMotion[1].sample(now)>0&&searchPanelBounds().contains(x,y);
    }

    private boolean overActivePopover(double x,double y) {
        return searchOpen && searchPanelBounds().contains(x,y) || layersOpen && layersPopoverBounds().contains(x,y);
    }

    private WorldMapUiLayout.Rect visibleLegend() {
        return workspace==null || mode==Mode.CITIES && projectWorkspace.open() ? null : workspace.legend();
    }
    /** "Сейчас важно": counts the player actually acts on, and a calm state when nothing is wrong. */
    private void renderOverview(GuiGraphics g) {
        var panel=workspace.inspector();
        this.cardX=panel.x();this.cardY=panel.y();this.cardWidth=panel.width();this.cardHeight=panel.height();
        if (panel.height()<=WorldMapUiLayout.OVERVIEW_HEADER+1) return;
        String key="overview:"+this.mode;
        if(!key.equals(this.cardKey)){this.cardKey=key;this.cardScroll=0;this.dispatchChanges.clear();}
        int pad=WorldMapUiLayout.CARD_PADDING,x=panel.x()+pad,w=panel.width()-2*pad;
        plate(g,panel.x(),panel.y(),panel.width(),panel.height());
        g.enableScissor(panel.x()+1,panel.y()+1,panel.right()-1,panel.bottom()-1);
        try {
            g.drawString(this.font,WorldMapChrome.fit(this.font,this.mode.label(),w),x,panel.y()+5,ACCENT,false);
            g.drawString(this.font,WorldMapChrome.fit(this.font,Component.translatable("gui.createcityexpansioncore.world_map.polish.summary"),w),x,panel.y()+16,TEXT,false);
            int bodyTop=panel.y()+WorldMapUiLayout.OVERVIEW_HEADER;
            int cursor=bodyTop-this.cardScroll;
            List<CityMapPanel.Overlay> metrics=switch(this.mode) {
                case CITIES->List.of(CityMapPanel.Overlay.CITIES,CityMapPanel.Overlay.TERRITORIES,CityMapPanel.Overlay.PROJECTS);

case ENERGY->List.of();
            };
            g.enableScissor(panel.x()+1,bodyTop,panel.right()-1,panel.bottom()-1);
            for(var metric:metrics) {
                var r=new WorldMapUiLayout.Rect(x,cursor,w,WorldMapUiLayout.SUMMARY_ROW_HEIGHT);
                WorldMapChrome.rounded(g,r.x(),r.y(),r.width(),r.height(),4,WorldMapChrome.INSET);
                Component label=Component.translatable("gui.createcityexpansioncore.world_map.polish.metric."+metric.name().toLowerCase(Locale.ROOT));
                g.drawString(this.font,WorldMapChrome.fit(this.font,label,w-48),x+8,cursor+8,MUTED,false);
                String count=Integer.toString(this.map.overlayCount(metric));
                double flash=changed("metric:"+this.mode+":"+metric,count,180);
                g.drawString(this.font,count,x+w-8-this.font.width(count),cursor+8,DispatchMotion.tint(TEXT,ACCENT,flash),false);
                cursor+=WorldMapUiLayout.SUMMARY_ROW_PITCH;
            }
            Component status=Component.translatable(problemCount()>0?"gui.createcityexpansioncore.world_map.overview.problems":"gui.createcityexpansioncore.world_map.overview.calm",problemCount());
            g.drawWordWrap(this.font,status,x,cursor,w,problemCount()>0?WorldMapChrome.WARNING:WorldMapChrome.POSITIVE);
            cursor+=this.font.wordWrapHeight(status,w)+SECTION;
            Component hint=Component.translatable("gui.createcityexpansioncore.world_map.polish.empty."+this.mode.name().toLowerCase(Locale.ROOT));
            g.drawWordWrap(this.font,hint,x,cursor,w,MUTED);
            cursor+=this.font.wordWrapHeight(hint,w)+SECTION;
            this.cardMaxScroll=Math.max(0,cursor+this.cardScroll-panel.bottom());
            this.cardScroll=Math.min(this.cardScroll,this.cardMaxScroll);
        }finally{g.disableScissor();g.disableScissor();}
        renderCardScroll(g,WorldMapUiLayout.inspectorScrollTrack(panel,WorldMapUiLayout.OVERVIEW_HEADER,MICRO));
    }
    private void renderCardScroll(GuiGraphics g,WorldMapUiLayout.Rect track) {
        if(this.cardMaxScroll<=0 || track.height()==0)return;
        var thumb=WorldMapUiLayout.scrollThumb(track,this.cardScroll,this.cardMaxScroll);
        g.fill(track.x(),track.y(),track.right(),track.bottom(),FRAME);
        g.fill(thumb.x(),thumb.y(),thumb.right(),thumb.bottom(),ACCENT);
    }

    /**
     * Pinned cards lead with the primary action so it stays within reach on a compact screen.
     * Hover cards keep the same header and show a short preview of the object's details.
     */
    private void renderSelectionPanel(GuiGraphics g,CityMapPanel.Selection value,boolean pinned) {
        var profile=value.kind()==CityMapPanel.SelectionKind.CITY?com.example.examplemod.client.CityPublicDirectory.profile(value.id()):null;
        this.cardActions = pinned ? actionsFor(value) : List.of();
        int pad=WorldMapUiLayout.CARD_PADDING;
        var panel=workspace.inspector();
        int x=panel.x()+pad,w=panel.width()-2*pad;
        String key=value.kind()+":"+(value.id()==null?value.pos().asLong():value.id());
        if(!key.equals(this.cardKey)){this.cardKey=key;this.cardScroll=0;this.dispatchChanges.clear();}
        this.cardX=panel.x();this.cardY=panel.y();this.cardWidth=panel.width();this.cardHeight=panel.height();
        if (panel.height()<=WorldMapUiLayout.CARD_HEADER+1) {
            cardActionCount=0;
            return;
        }
        plate(g,panel.x(),panel.y(),panel.width(),panel.height());
        g.enableScissor(panel.x()+1,panel.y()+1,panel.right()-1,panel.bottom()-1);
        var placeIcon=AtlasPlaceIcon.forSelection(value,map.dimensionId());
        int titleY=panel.y()+NORMAL;
        if(placeIcon!=null)placeIcon.badge(g,x+13,titleY+13,13,pinned,false);
        else if(profile!=null)com.example.examplemod.client.map.CityEmblemRenderer.draw(g,profile.identity(),profile.colour(),x,titleY,1);
        else MapMarkerRenderer.renderCardIcon(g,x,titleY,value.icon(),value.own(),value.warning());
        int titleX=x+32;
        g.drawString(this.font,WorldMapChrome.fit(this.font,value.title(),w-32),titleX,titleY,TEXT,false);
        g.drawString(this.font,WorldMapChrome.fit(this.font,selectionType(value),w-32),titleX,titleY+14,value.own()?ACCENT:MUTED,false);
        offerTooltip(value.title(),inside(mouseXNow,mouseYNow,titleX,titleY,w-32,12)
                && !overActivePopover(mouseXNow,mouseYNow) && !closingPopover(mouseXNow,mouseYNow));
        g.hLine(x,x+w,panel.y()+WorldMapUiLayout.CARD_HEADER-7,FRAME);
        var body=WorldMapUiLayout.cardBody(panel);
        g.enableScissor(body.x(),body.y(),body.right(),body.bottom());
        int cursor=panel.y()+WorldMapUiLayout.CARD_HEADER-this.cardScroll;
        boolean showDetailsFirst=value.kind()==CityMapPanel.SelectionKind.CARTOGRAPHY || PassengerMapLayer.isPassenger(value.kind());
        try {
            this.cardActionY=cursor;
            this.cardActionCount=cardActions.size();
            for(int i=0;i<cardActions.size();i++) {
                var row=cardActions.get(i);
                var action=editControl("card.action["+i+"]",WorldMapUiLayout.cardAction(panel,cardActionY,i));
                String name=row.action().name().toLowerCase(Locale.ROOT);
                var station = row.station();
                Component label=station == null ? atlasText("action."+name) : atlasText("action."+name,station.name(),station.distance());
                if(row.platform()!=null) label=atlasText("action.to_platform",row.platform().name());
                Component help=row.enabled()? (station == null ? atlasText("hint."+name) : atlasText("hint.station_landmark",label)) : atlasText(row.unavailable());
                if(!row.enabled()) label=atlasText("disabled",label);
                boolean danger = row.action()==AtlasObjectCard.Action.REMOVE_PIN || row.action()==AtlasObjectCard.Action.STOP;
                boolean hover = mainControls() && body.contains(mouseXNow,mouseYNow) && action.contains(mouseXNow,mouseYNow);
                paintControl(g,action,ControlGroup.CARD,i,hover,row.enabled(),false,i==0 && !danger,danger,false);
                g.drawCenteredString(font,WorldMapChrome.fit(font,label,action.width()-SECTION),action.x()+action.width()/2,
                        action.y()+(action.height()-8)/2,!row.enabled()?MUTED:i==0&&!danger?WorldMapChrome.PRIMARY_TEXT:TEXT);
                final int index=i;
                if(mainControls()) {
                    keyboard.add("card."+i,label,help,action,()->handleContextAction(value,index));
                    if(body.contains(mouseXNow,mouseYNow)) offerTooltip(help,action.contains(mouseXNow,mouseYNow));
                }
            }
            cursor+=cardActions.size()*WorldMapUiLayout.ACTION_PITCH+MICRO;
            if (showDetailsFirst) {
                for (var detail : value.details()) {
                    g.drawWordWrap(font,detail,x,cursor,w,TEXT);
                    cursor+=font.wordWrapHeight(detail,w)+MICRO;
                }
                cursor+=SECTION;
            } else {
                Component state=switch(value.kind()) {
                    case LOGISTICS -> {
                        var offer=AtlasTradeDirectory.row(value.id(),map.dimensionId());
                        if (offer==null) yield Component.literal(com.example.examplemod.client.logistics.LogisticsSkin.value(
                                LogisticsMapOverlay.markerStatus(value.id(),map.dimensionId())));
                        yield offer.itemId().isEmpty() ? Component.translatable("gui.createcityexpansioncore.atlas.trade.available")
                                : offer.details().getFirst();
                    }
                    case INDUSTRY -> value.details().getLast();
                    case PROJECT -> value.details().isEmpty()?atlasText("listed"):value.details().getFirst();
                    case PLAYER -> atlasText("player_state");
                    case WAYPOINT -> atlasText("pin_state");
                    case PLACE -> {
                        var place=AtlasPlaces.get(value.id(),map.dimensionId());
                        yield AtlasPlaces.text(place!=null && place.published()?"public":"personal");
                    }
                    case CITY, CARTOGRAPHY, PASSENGER_LINE, PASSENGER_TRAIN -> atlasText("listed");
                };
                g.drawWordWrap(this.font,atlasText("state",state),x,cursor,w,value.warning()?MapPalette.ALERT:TEXT);
                cursor+=this.font.wordWrapHeight(atlasText("state",state),w)+MICRO;
                Component freshness=switch(value.kind()) {
                    case WAYPOINT -> atlasText("local");
                    case PLAYER -> atlasText("live");
                    case LOGISTICS -> AtlasTradeDirectory.row(value.id(),map.dimensionId())!=null
                            ? Component.translatable("gui.createcityexpansioncore.atlas.trade.checked",AtlasTradeDirectory.ageSeconds())
                            : LogisticsMapOverlay.freshness(value.id(),map.dimensionId());
                    default -> atlasText("snapshot");
                };
                g.drawWordWrap(this.font,freshness,x,cursor,w,MUTED);
                cursor+=this.font.wordWrapHeight(freshness,w)+SECTION;
            }
            if (pinned && directions != null && directions.matches(value, map.dimensionId())) {
                Component note = directions.description();
                g.drawWordWrap(font, note, x, cursor, w, TEXT);
                cursor += font.wordWrapHeight(note, w) + SECTION;
            }
            if(pinned && value.kind()==CityMapPanel.SelectionKind.LOGISTICS) {
                for(var fact:LogisticsMapOverlay.inspectionLines(value.id(),map.dimensionId())) {
                    g.drawWordWrap(font,fact,x,cursor,w,TEXT);
                    cursor+=font.wordWrapHeight(fact,w)+MICRO;
                }
            }
            if(profile!=null) {
                var alliance=com.example.examplemod.client.AllianceClient.forCity(profile.id());
                if(alliance!=null){g.drawString(this.font,WorldMapChrome.fit(this.font,AllianceScreen.tr("badge",alliance.getString("Name")),w),x,cursor,0xFF000000|alliance.getInt("Color"),false);cursor+=SECTION+4;}
                Component residents=Component.translatable("gui.createcityexpansioncore.city.identity.residents",profile.residents());
                g.drawString(this.font,WorldMapChrome.fit(this.font,residents,w),x,cursor,MUTED,false);cursor+=SECTION+4;
                Component description=profile.identity().description().isBlank()?Component.translatable("gui.createcityexpansioncore.city.identity.no_description"):Component.literal(profile.identity().description());
                g.drawWordWrap(this.font,description,x,cursor,w,TEXT);cursor+=this.font.wordWrapHeight(description,w)+SECTION;
            }
            int count=showDetailsFirst?0:pinned?value.details().size():Math.min(2,value.details().size());
            for(int i=0;i<count;i++) {
                Component fact=value.details().get(i);
                g.drawWordWrap(this.font,fact,x,cursor,w,TEXT);
                cursor+=this.font.wordWrapHeight(fact,w)+WorldMapUiLayout.FACT_GAP;
            }
            if(pinned) {
                Component coords=Component.translatable("gui.createcityexpansioncore.world_map.context.coordinates",value.pos().getX(),value.pos().getZ());
                g.drawString(this.font,WorldMapChrome.fit(this.font,coords,w),x,cursor,MUTED,false);cursor+=SECTION+4;
            }
            if(!pinned) {
                var more=Component.translatable("gui.createcityexpansioncore.world_map.hover.details");
                g.drawWordWrap(font,more,x,cursor,w,ACCENT);cursor+=font.wordWrapHeight(more,w)+MICRO;
            }
            this.cardMaxScroll=Math.max(0,cursor+this.cardScroll+pad-panel.bottom());
            this.cardScroll=Math.min(this.cardScroll,this.cardMaxScroll);
        }finally{g.disableScissor();g.disableScissor();}
        renderCardScroll(g,WorldMapUiLayout.cardScrollTrack(panel));
    }

    static Component atlasText(String key, Object... args) {
        return Component.translatable("gui.createcityexpansioncore.atlas3.ui."+key, args);
    }

    public void pinMapObject(CityMapPanel.Selection value) {
        if (map==null || value==null) return;
        var before=map.getSelection();
        if (before==null || before.kind()!=value.kind() || !java.util.Objects.equals(before.id(),value.id())) {
            pushTarget(backHistory,before);
            forwardHistory.clear();
            CommunityFx.sound("map_select");
        }
        pendingSelection=null;
        map.setSelection(value);
    }

    private List<AtlasObjectCard.Row> actionsFor(CityMapPanel.Selection value) {
        if (PassengerMapLayer.isPassenger(value.kind())) {
            boolean train=value.kind()==CityMapPanel.SelectionKind.PASSENGER_TRAIN;
            var related=PassengerMapLayer.related(value,map.dimensionId());
            var action=train?AtlasObjectCard.Action.SHOW_PASSENGER_LINE:AtlasObjectCard.Action.SHOW_PASSENGER_TRAIN;
            return List.of(new AtlasObjectCard.Row(action,related==null?"unavailable":""));
        }
        if (directions != null && directions.matches(value, map.dimensionId())) return directions.rows(map, value);
        var profile=value.kind()==CityMapPanel.SelectionKind.CITY ? com.example.examplemod.client.CityPublicDirectory.profile(value.id()) : null;
        if (value.kind()==CityMapPanel.SelectionKind.LOGISTICS && LogisticsMapOverlay.inspecting(value.id(),map.dimensionId()))
            return List.of(new AtlasObjectCard.Row(AtlasObjectCard.Action.CARGO,""),new AtlasObjectCard.Row(AtlasObjectCard.Action.BACK_TO_OBJECT,""));
        var rows=AtlasObjectCard.rows(value.kind(),value.kind()==CityMapPanel.SelectionKind.LOGISTICS ? LogisticsMapOverlay.canOpenObject(value.id(),map.dimensionId()) : value.id()!=null,JourneyClient.canTrack(map,map.dimensionId(),value),
                JourneyClient.isTracking(map.dimensionId(),value),profile!=null && profile.editable(),
                !readOnly && value.own() && validReturn());
        if(value.kind()==CityMapPanel.SelectionKind.LOGISTICS && LogisticsMapOverlay.canInspect(value.id(),map.dimensionId())) {
            var inspectionRows=new ArrayList<>(rows);
            inspectionRows.addFirst(new AtlasObjectCard.Row(AtlasObjectCard.Action.INSPECT_SHIPMENT,""));
            return inspectionRows;
        }
        var actions=new ArrayList<>(rows);
        if(value.kind()==CityMapPanel.SelectionKind.PLACE){
            String key="PLACE/"+map.dimensionId()+"/"+value.id();
            actions.add(new AtlasObjectCard.Row(library!=null && library.favorite(key)?AtlasObjectCard.Action.UNFAVORITE_PLACE:AtlasObjectCard.Action.FAVORITE_PLACE,""));
            var place=AtlasPlaces.get(value.id(),map.dimensionId());
            if(place==null || place.entrances().isEmpty())actions.removeIf(row->
                    row.action()==AtlasObjectCard.Action.ROUTE || row.action()==AtlasObjectCard.Action.FROM);
            if(place!=null && place.published())actions.add(new AtlasObjectCard.Row(AtlasObjectCard.Action.SHARE_PLACE,""));
            if(value.own())actions.add(new AtlasObjectCard.Row(AtlasObjectCard.Action.EDIT_PLACE,""));
        }else if(value.kind()==CityMapPanel.SelectionKind.CARTOGRAPHY){
            var feature=CityMapFeatures.get(value.id());
            if(feature!=null && feature.kind()==com.example.examplemod.citymap.CityMapFeature.Kind.STATION) {
                int index=0;
                for(var stop:CityMapFeatures.platforms(feature))
                    actions.add(index++, new AtlasObjectCard.Row(AtlasObjectCard.Action.TO_PLATFORM,"",null,stop));
            }
            if(feature!=null && canDraw(feature.city()))
                actions.addFirst(new AtlasObjectCard.Row(AtlasObjectCard.Action.EDIT_CARTOGRAPHY,""));
        }else if(AtlasPlaces.canLink(AtlasPlaces.source(value,map.dimensionId())))actions.add(new AtlasObjectCard.Row(AtlasObjectCard.Action.LINK_PLACE,""));
        return actions;
    }

    void showCardNotice(String key) { cardNotice=key; noticeTicks=80; }

    private void handleContextAction(CityMapPanel.Selection selected,int index) {
        if (!validReturn() || map==null || selected==null || index<0 || index>=cardActions.size()) return;
        String shown=selected.kind()+":"+(selected.id()==null?selected.pos().asLong():selected.id());
        var actual=map.getSelection();
        if (!shown.equals(cardKey) || actual==null || actual.kind()!=selected.kind() || !java.util.Objects.equals(actual.id(),selected.id())) return;
        var current=map.resolveSelection(selected.kind(),selected.id(),selected.pos());
        if (current==null) { map.clearSelection(); showCardNotice("unavailable"); return; }
        var id=cardActions.get(index).action();
        var shownPlatform=cardActions.get(index).platform();
        var row=actionsFor(current).stream().filter(r->r.action()==id && java.util.Objects.equals(r.platform(),shownPlatform)).findFirst().orElse(null);
        if (row==null || !row.enabled()) { showCardNotice(row==null?"unavailable":row.unavailable()); return; }
        if (!java.util.Objects.equals(row.station(),cardActions.get(index).station())) { showCardNotice("directions_changed"); return; }
        switch (id) {
            case EDIT_CARTOGRAPHY -> {
                var feature=CityMapFeatures.get(current.id());
                if(feature!=null) openDrawingCity(feature.city(),feature);
            }
            case ROUTE -> AtlasTripScreen.open(this,map,current);
            case TO_PLATFORM -> AtlasTripScreen.openPlatform(this,map,row.platform().id());
            case SHOW_PASSENGER_LINE, SHOW_PASSENGER_TRAIN -> {
                var related=PassengerMapLayer.related(current,map.dimensionId());
                if(related!=null) {
                    pinMapObject(related);
                    if(id==AtlasObjectCard.Action.SHOW_PASSENGER_TRAIN) map.centerOn(related.pos());
                }
            }
            case FROM -> AtlasTripScreen.from(this,map,current);
            case FAVORITE_PLACE,UNFAVORITE_PLACE -> {
                if(library!=null && library.toggle("PLACE/"+map.dimensionId()+"/"+current.id()))writeLibrary(library,true);
            }
            case EDIT_PLACE -> {
                var place=AtlasPlaces.get(current.id(),map.dimensionId());
                if(place!=null && place.owner().equals(minecraft.player.getUUID()))openAtlasTool(new AtlasPlaceEditor(this,map,place,null));
            }
            case LINK_PLACE -> {
                if(AtlasPlaces.canLink(AtlasPlaces.source(current,map.dimensionId())))openAtlasTool(new AtlasPlaceEditor(this,map,null,current));
            }
            case SHARE_PLACE -> {
                var place=AtlasPlaces.get(current.id(),map.dimensionId());
                if(place!=null && place.published()){minecraft.keyboardHandler.setClipboard("place:"+place.id());showCardNotice("place_copied");}
            }
            case DIRECTIONS -> { directions = new AtlasDirections(current,map.dimensionId()); cardScroll = 0; keyboard.reset(); }
            case INSPECT_SHIPMENT -> { LogisticsMapOverlay.inspect(current.id(),map.dimensionId());cardScroll=0;keyboard.reset(); }
            case BACK_TO_OBJECT -> { LogisticsMapOverlay.finishInspection(); directions = null; cardScroll = 0; keyboard.reset(); }
            case ORIGIN_STATION, DESTINATION_STATION, DIRECT -> {
                var landmark = row.station();
                var goal = landmark == null ? current : LogisticsMapOverlay.resolveMapSelection(landmark.id(),map.dimensionId());
                if (goal != null && (JourneyClient.isTracking(map.dimensionId(),goal) || JourneyClient.track(map,map.dimensionId(),goal))) {
                    directions = null;
                    cardScroll = 0;
                } else showCardNotice("target_unavailable");
            }
            case PROJECT -> ProjectWorkspaceScreen.open(this,current.id());
            case CARGO -> LogisticsMapOverlay.openObject(current.id(),map.dimensionId());
            case CENTER -> map.centerOn(current.pos());
            case TRACK -> JourneyClient.track(map,map.dimensionId(),current);
            case STOP -> JourneyClient.cancelNavigation();
            case REMOVE_PIN -> { map.removeSelectedPin(); showCardNotice("pin_removed"); CommunityFx.sound("map_save"); }
            case ALLIANCE -> AllianceScreen.open(this,current.id());
            case RELATIONS -> CommunityScreen.openRelations(this);
            case EDIT_CITY -> {
                var profile=com.example.examplemod.client.CityPublicDirectory.profile(current.id());
                if (profile!=null && profile.editable()) minecraft.setScreen(new CityIdentityEditorScreen(this,profile));
            }
        }
    }

    private int problemCount(){return problemObjects().size();}

    /**
     * A summary card, not a screen takeover: the map stays visible behind a light dim so the player
     * keeps their orientation while reading what needs attention.
     */
    private WorldMapUiLayout.Rect problemRow(WorldMapUiLayout.Rect panel, int index) {
        return editControl("problem[" + index + "]", new WorldMapUiLayout.Rect(
                panel.x() + SECTION, panel.y() + 48 + index * 25, panel.width() - SECTION * 2, 22));
    }

    private WorldMapUiLayout.Rect modalFooter(String key, WorldMapUiLayout.Rect panel) {
        return editControl(key, new WorldMapUiLayout.Rect(
                panel.x() + SECTION, panel.bottom() - 30, panel.width() - SECTION * 2, 24));
    }

    private WorldMapUiLayout.Rect inboxBounds(WorldMapUiLayout.Rect panel) {
        return new WorldMapUiLayout.Rect(panel.x()+SECTION,panel.bottom()-53,panel.width()-2*SECTION,19);
    }

    private void renderProblemCenter(GuiGraphics graphics) {
        graphics.fill(0,0,width,height,WorldMapChrome.MODAL_SCRIM);
        var panel = WorldMapUiLayout.problemModal(this.width, this.height);
        WorldMapChrome.modal(graphics,panel.x(),panel.y(),panel.width(),panel.height());
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.problems"), panel.x() + SECTION, panel.y() + NORMAL, ACCENT, false);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.problem_count", problemCount()), panel.x() + SECTION, panel.y() + 26, TEXT, false);
        List<CityMapPanel.Selection> objects = problemObjects();
        this.problemOffset = Math.min(this.problemOffset, Math.max(0, objects.size() - problemRows()));
        if (objects.isEmpty()) graphics.drawWordWrap(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.ready"), panel.x() + SECTION, panel.y() + 50, panel.width() - SECTION * 2, MUTED);
        for (int index = 0; index < Math.min(problemRows(), objects.size() - this.problemOffset); index++) {
            var value = objects.get(index + this.problemOffset);
            var row = problemRow(panel, index);
            if(problemCenterOpen) keyboard.add("problem."+index,value.title(),atlasText("hint.center"),row,()->{selectObject(value);problemCenterOpen=false;});
            int y = row.y();
            paintControl(graphics,row,ControlGroup.MODAL,index+5,problemCenterOpen && row.contains(mouseXNow,mouseYNow),
                    true,false,false,false,true);
            MapMarkerRenderer.drawGlyph(graphics, MapMarkerRenderer.glyph(value.icon()), row.x() + 3, y + 3, MapPalette.ALERT);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(value.title().getString(), row.width() - 30), row.x() + 24, y + 3, TEXT, false);
            String detail = value.details().isEmpty() ? selectionType(value).getString() : value.details().getFirst().getString();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(detail, row.width() - 30), row.x() + 24, y + 13, MUTED, false);
        }
        var journal=inboxBounds(panel);
        var journalLabel=atlasText("inbox");
        paintControl(graphics,journal,ControlGroup.MODAL,0,problemCenterOpen && journal.contains(mouseXNow,mouseYNow),
                true,false,false,false,false);
        graphics.drawCenteredString(font,journalLabel,journal.x()+journal.width()/2,journal.y()+5,TEXT);
        if(problemCenterOpen) keyboard.add("problems.inbox",journalLabel,atlasText("inbox_hint"),journal,()->SocialHubScreen.openInbox(this));
        var close = modalFooter("problemClose", panel);
        paintControl(graphics,close,ControlGroup.MODAL,1,problemCenterOpen && close.contains(mouseXNow,mouseYNow),
                true,false,false,false,false);
        if (problemCenterOpen) keyboard.add("problems.close",atlasText("close"),atlasText("dismiss"),close,()->problemCenterOpen=false);
        graphics.drawCenteredString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.close"),
                close.x() + close.width()/2, close.y() + (close.height()-8)/2 + 2, ACCENT);
    }

    private void renderGuide(GuiGraphics graphics) {
        graphics.fill(0,0,width,height,WorldMapChrome.MODAL_SCRIM);
        WorldMapUiLayout.Rect panel = WorldMapUiLayout.guideModal(this.width, this.height);
        WorldMapChrome.modal(graphics,panel.x(),panel.y(),panel.width(),panel.height());
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.guide.title"),
                panel.x() + WorldMapUiLayout.LARGE, panel.y() + WorldMapUiLayout.SECTION, ACCENT, false);
        Component text = Component.translatable("gui.createcityexpansioncore.world_map.guide.text");
        int top = panel.y() + 30, bottom = panel.bottom() - 34;
        this.guideMaxScroll = Math.max(0, this.font.wordWrapHeight(text, panel.width() - LARGE * 2) - (bottom - top));
        this.guideScroll = Math.min(this.guideScroll, this.guideMaxScroll);
        graphics.enableScissor(panel.x() + LARGE, top, panel.right() - LARGE, bottom);
        graphics.drawWordWrap(this.font, text, panel.x() + LARGE, top - this.guideScroll, panel.width() - LARGE * 2, TEXT);
        graphics.disableScissor();
        var close = modalFooter("guideClose", panel);
        paintControl(graphics,close,ControlGroup.MODAL,2,guideOpen && close.contains(mouseXNow,mouseYNow),
                true,false,false,false,false);
        if (guideOpen) keyboard.add("guide.close",atlasText("close"),atlasText("dismiss"),close,()->guideOpen=false);
        graphics.drawCenteredString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.guide.dismiss"),
                close.x() + close.width()/2, close.y() + (close.height()-8)/2 + 2, MUTED);
    }

    private void renderStatus(GuiGraphics graphics, int mouseX, int mouseY) {
        var strip = WorldMapUiLayout.statusStrip(this.width, this.height);
        plate(graphics, strip.x(), strip.y(), strip.width(), strip.height());
        Component coordinates = this.map.coordinateStatus(mouseX, mouseY);
        int right = (this.mode==Mode.CITIES ? ProjectLayout.toggle(this.width,this.height).x() : strip.right()) - 6;
        int available = Math.max(0,right-strip.x());
        var navigation=JourneyClient.navigationSummary();
        var stop=stopNavigationBounds();
        boolean notice=noticeTicks>0;
        int hint=com.example.examplemod.client.OnboardingClient.inlineAtlasHint();
        if (stop!=null || notice || hint>=0) {
            Component message=notice ? atlasText(cardNotice) : stop!=null ? navigation : hint==1 ?
                    atlasText(map.getSelection()==null?"first.1":"first.2") : com.example.examplemod.client.OnboardingClient.body(hint);
            int textRight=stop!=null?stop.x()-3:right;
            graphics.drawString(font,WorldMapChrome.fit(font,message,Math.max(1,textRight-strip.x()-8)),strip.x()+5,strip.y()+5,
                    !notice && stop!=null?DispatchPalette.ACCENT:TEXT,false);
            if(mainControls()) offerTooltip(message,strip.contains(mouseX,mouseY));
            if (stop!=null) {
                Component label=atlasText("action.stop");
                paintControl(graphics,stop,ControlGroup.STATUS,0,mainControls() && stop.contains(mouseX,mouseY),
                        true,false,false,true,false);
                graphics.drawCenteredString(font,WorldMapChrome.fit(font,label,stop.width()-4),stop.x()+stop.width()/2,stop.y()+3,TEXT);
                if(mainControls()) {
                    var stopHint=atlasText("hint.stop");
                    keyboard.add("navigation.stop",label,stopHint,stop,JourneyClient::cancelNavigation);
                    offerTooltip(stopHint,stop.contains(mouseX,mouseY));
                }
            }
            return;
        }
        int coordinateWidth = Math.min(available / 2, this.font.width(coordinates));
        graphics.drawString(this.font, this.font.plainSubstrByWidth(coordinates.getString(), coordinateWidth), right - coordinateWidth, strip.y() + 5, MUTED, false);
        String access = projectWorkspace.open() && this.mode==Mode.CITIES ? Component.translatable("gui.createcityexpansioncore.projects.title").getString() : Component.translatable("gui.createcityexpansioncore.world_map." + (this.readOnly ? "read_only_short" : "management_short")).getString();
        String identity = this.data.getCityName() + " · " + this.data.getMemberCount() + " · " + access;
        int leftWidth = Math.min(180, Math.max(0, available - coordinateWidth - 22));
        graphics.drawString(this.font, this.font.plainSubstrByWidth(identity, leftWidth), strip.x() + 6, strip.y() + 5, TEXT, false);
        int statusX = strip.x() + leftWidth + 14;
        int statusWidth = right - coordinateWidth - statusX - 8;
        if (statusWidth > 40) graphics.drawString(this.font, this.font.plainSubstrByWidth(this.map.terrainStatus().getString(), statusWidth), statusX, strip.y() + 5, MUTED, false);
        Component statusTooltip=this.map.terrainStatus();
        offerTooltip(statusTooltip, strip.contains(mouseX, mouseY));
        if(statusWidth>40 && this.map.terrainLoading()) for(int i=0;i<3;i++) {
            int alpha = 160;
            graphics.fill(statusX-15+i*4,strip.y()+8,statusX-13+i*4,strip.y()+10,(alpha<<24)|(ACCENT&0xFFFFFF));
        }
    }

    public boolean allowsInlineHint() {
        return map!=null && mainControls() && noticeTicks==0 && !JourneyClient.canCancelNavigation()
                && com.example.examplemod.client.map.MinimapClient.settings().contextHints;
    }

    private WorldMapUiLayout.Rect stopNavigationBounds() {
        if (map==null || !JourneyClient.canCancelNavigation()) return null;
        var strip=WorldMapUiLayout.statusStrip(width,height);
        int right=(mode==Mode.CITIES ? ProjectLayout.toggle(width,height).x() : strip.right())-3;
        int w=Math.min(Math.max(16,(right-strip.x())/3),font.width(atlasText("action.stop"))+10);
        return new WorldMapUiLayout.Rect(right-w,strip.y()+2,w,Math.max(12,strip.height()-4));
    }

    private void renderTutorial(GuiGraphics graphics) {
        if (this.tutorialTicks <= 0) return;
        graphics.fill(0, 0, this.width, this.height, 0x660B1012);
        Component text = Component.translatable("gui.createcityexpansioncore.world_map.tutorial");
        WorldMapUiLayout.Rect strip = WorldMapUiLayout.tutorialStrip(this.width, this.height, this.font.width(text));
        plate(graphics, strip.x(), strip.y(), strip.width(), strip.height());
        graphics.drawCenteredString(this.font, text, this.width / 2, strip.y() + 5, TEXT);
    }

    private String focusSurface() {
        if (pendingMutation!=null) return "confirm";
        if (guideOpen) return "guide";
        if (problemCenterOpen) return "problems";
        if (searchOpen) return "search";
        if (layersOpen) return "layers";
        return "map";
    }
    private boolean useAtlasKeyboard(int key, int modifiers) {
        if (searchOpen && !guideOpen && !problemCenterOpen && pendingMutation==null || map==null || map.hasPendingClaim() || projectWorkspace.open() && !layersOpen && !guideOpen && !problemCenterOpen) return false;
        boolean tab=key==GLFW.GLFW_KEY_TAB;
        if (!tab && key!=GLFW.GLFW_KEY_ENTER && key!=GLFW.GLFW_KEY_KP_ENTER && key!=GLFW.GLFW_KEY_SPACE) return false;
        if (!focusSurface.equals(focusSurface())) { keyboard.reset(); return false; }
        var control=tab ? keyboard.move((modifiers & GLFW.GLFW_MOD_SHIFT)!=0) : keyboard.current();
        if (control==null) return false;
        if (!tab) {
            var visible = visibleFocusBounds(control);
            if (visible.width()>0 && visible.height()>0) control.activate().run();
        }
        else {
            var r=control.bounds();
            if (control.id().startsWith("card.")) {
                var body=WorldMapUiLayout.cardBody(new WorldMapUiLayout.Rect(cardX,cardY,cardWidth,cardHeight));
                cardScroll=Math.clamp(cardScroll+scrollIntoView(r,body.y(),body.bottom()),0,cardMaxScroll);
            } else if (control.id().startsWith("layer.")) {
                var body=WorldMapUiLayout.layerBody(layersPopoverBounds());
                layerScroll=Math.max(0,layerScroll+scrollIntoView(r,body.y(),body.bottom()));
            }
            minecraft.getNarrator().sayNow(control.name().copy().append(". ").append(control.hint()));
        }
        return true;
    }

    private WorldMapUiLayout.Rect visibleFocusBounds(AtlasFocus.Control control) {
        if (control.id().startsWith("card.")) {
            return WorldMapUiLayout.visiblePart(control.bounds(),
                    WorldMapUiLayout.cardBody(new WorldMapUiLayout.Rect(cardX,cardY,cardWidth,cardHeight)));
        }
        if (control.id().startsWith("layer.")) {
            return WorldMapUiLayout.visiblePart(control.bounds(),WorldMapUiLayout.layerBody(layersPopoverBounds()));
        }
        return control.bounds();
    }
    private static int scrollIntoView(WorldMapUiLayout.Rect r,int top,int bottom) {
        return r.y()<top?r.y()-top:Math.max(0,r.bottom()-bottom);
    }
    private boolean mainControls() {
        return !searchOpen && !layersOpen && !guideOpen && !problemCenterOpen && pendingMutation==null
                && map!=null && !map.hasPendingClaim() && !projectWorkspace.open();
    }
    private void offerTooltip(Component tooltip, boolean hovered) {
        if (!hovered || map==null || map.isMapDragging() || guideOpen || problemCenterOpen || pendingMutation!=null) return;
        tooltipOffered=tooltip;
    }

    private void renderTooltip(GuiGraphics graphics, Component value, int mouseX, int mouseY) {
        int pad=WorldMapUiLayout.TOOLTIP_PADDING;
        int textWidth=Math.max(1,Math.min(Math.min(220,this.font.width(value)),Math.max(1,this.width-WorldMapUiLayout.EDGE*2-pad*2)));
        var bounds=WorldMapUiLayout.tooltip(this.width,this.height,mouseX,mouseY,textWidth,this.font.wordWrapHeight(value,textWidth));
        plate(graphics,bounds.x(),bounds.y(),bounds.width(),bounds.height());
        graphics.enableScissor(bounds.x(),bounds.y(),bounds.right(),bounds.bottom());
        try { graphics.drawWordWrap(this.font,value,bounds.x()+pad,bounds.y()+pad,textWidth,TEXT); }
        finally { graphics.disableScissor(); }
    }

    private static void plate(GuiGraphics graphics, int x, int y, int width, int height) {
        WorldMapChrome.dispatchPanel(graphics,x,y,width,height);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { pressX = mouseX; pressY = mouseY; }
        return mouseClicked(mouseX,mouseY,button,Screen.hasShiftDown());
    }
    private boolean mouseClicked(double mouseX, double mouseY, int button, boolean shiftDown) {
        if(atlasTool!=null){atlasTool.mouseClicked(mouseX,mouseY,button);return true;}
        if(planEditor!=null){planEditor.mouseClicked(mouseX,mouseY,button);return true;}
        keyboard.reset();
        // Foreground surfaces own the pointer, including their closing transition.
        if (closingModal() || map!=null && closingPopover(mouseX,mouseY)) return true;
        if (pendingMutation!=null) { handleMutationClick(mouseX,mouseY,button); return true; }
        if (guideOpen || problemCenterOpen) {
            if (button==0) return handleModalClick(mouseX,mouseY);
            return true;
        }
        if (map!=null && overActivePopover(mouseX,mouseY)) {
            if (button!=0) return true;
            if (searchOpen && searchPanelBounds().contains(mouseX,mouseY)) {
                if (viewEdit!=ViewEdit.DELETE && searchBox.mouseClicked(mouseX,mouseY,button)) {
                    searchMessage="";
                    searchFocus=-1;
                    setFocused(searchBox);
                    return true;
                }
                return handleSearchClick(mouseX,mouseY);
            }
            return handleLayerClick(mouseX,mouseY);
        }
        if (button==0 && mainControls() && stopNavigationBounds()!=null && stopNavigationBounds().contains(mouseX,mouseY)) { JourneyClient.cancelNavigation(); return true; }
        if (insideMapViewport(mouseX,mouseY)) {
            searchJump=null;
            map.interruptCamera();
            com.example.examplemod.client.logistics.LogisticsMapOverlay.cancelAutomaticFocus();
        }
        if (map == null && button == 0 && com.example.examplemod.client.CityPublicDirectory.failed()
                && directoryRetryBounds().contains(mouseX,mouseY)) {
            com.example.examplemod.client.CityPublicDirectory.retry();
            return true;
        }
        if(!this.guideOpen&&!this.problemCenterOpen&&this.pendingMutation==null&&!this.layersOpen&&!this.searchOpen&&this.mode==Mode.CITIES&&this.map!=null&&!this.map.cameraPrecisionActive()&&!projectWorkspace.open()&&this.tutorialTicks==0&&com.example.examplemod.client.logistics.LogisticsMapOverlay.click(mouseX,mouseY,button))return true;
        if(!this.guideOpen&&!this.problemCenterOpen&&this.pendingMutation==null&&!this.layersOpen&&!this.searchOpen&&this.mode==Mode.CITIES&&this.map!=null&&!this.map.cameraPrecisionActive()&&!projectWorkspace.open()&&this.tutorialTicks==0&&com.example.examplemod.client.screen.ProjectWorkspaceClient.click(mouseX,mouseY,button))return true;
        var titleBounds = WorldMapUiLayout.title(this.width);
        if (button == 0 && this.readOnly && titleBounds.contains(mouseX, mouseY) && mouseY >= titleBounds.y() + 14 && this.pendingMutation == null) {
            var dimensions = com.example.examplemod.client.CityPublicDirectory.dimensions;
            if (dimensions.size() > 1) {
                requestedDimension = dimensions.get((dimensions.indexOf(this.data.getDimensionId()) + 1) % dimensions.size());
                savePreferences(); discardOwnedPanel(); preferenceWorld = null; preferences = null;
                com.example.examplemod.client.CityPublicDirectory.open(requestedDimension);
                directoryView=com.example.examplemod.client.CityPublicDirectory.viewId();
                return true;
            }
        }
        /*
         * Правая кнопка принадлежит карте: она запрашивает занятие участка 50×50.
         *
         * <p>Раньше здесь стояло «любая кнопка, кроме левой, идёт в super», и
         * ПКМ до панели карты не доходил вообще. Атлас и вкладка «Карта»
         * используют одну и ту же панель, поэтому claim работал в одном
         * экране и молчал в другом — причём молчал именно там, где игрок
         * видит пунктирный фронтир и наводится на участок.
         *
         * <p>Модальные окна по-прежнему перехватывают всё: под открытой
         * подсказкой карта не должна ни панорамировать, ни брать участок.
         */
        if (this.map == null) {
            // Пока панели нет, карта нечего обрабатывать: ни выбрать, ни сдвинуть.
            return button == 0 ? super.mouseClicked(mouseX, mouseY, button) : false;
        }
        if (button != 0) {
            if (this.readOnly) {
                if (button==1 && !planningSession && mode==Mode.CITIES && mainControls() && insideMapViewport(mouseX,mouseY)
                        && !shiftDown) {
                    showCardNotice("claim_at_pylon");
                    return true;
                }
                if(button==1 && shiftDown && !planningSession && mode==Mode.CITIES && mainControls() && tutorialTicks==0 &&
                        !map.cameraPrecisionActive() && canAddPlace() && minecraft.player.hasPermissions(2)){
                    var icon=map.placeIconAt(mouseX,mouseY,minecraft.player.getBlockY());
                    if(icon!=null){
                        openAtlasTool(AtlasPlaceEditor.sharedAt(this,map,icon));
                        return true;
                    }
                }
                return false;
            }
            return this.map.mouseClicked(mouseX, mouseY, button)
                    || super.mouseClicked(mouseX, mouseY, button);
        }
        for (int index = 0; index < this.viewButtons.size(); index++) {
            if (!this.problemCenterOpen && !this.guideOpen && editControl("view[" + index + "]", this.viewButtons.get(index)).contains(mouseX, mouseY)) {
                handleViewAction(viewActionAt(index)); dismissTutorial(); return true;
            }
        }
        Mode[] modes = Mode.values();
        for (int index = 0; index < modes.length; index++) {
            if (!modeControl(index).contains(mouseX, mouseY)) continue;
            // A disabled mode is inert: it never switches focus and never pretends to have data.
            if (modes[index].available()) applyMode(modes[index]);
            dismissTutorial();
            return true;
        }
        for (int index = 0; index < WorldMapUiLayout.GLOBAL_ACTIONS; index++) {
            if (!globalControl(index).contains(mouseX, mouseY)) continue;
            handleGlobalAction(index);
            return true;
        }
        var legend=visibleLegend();
        if (legend!=null && legend.contains(mouseX,mouseY)) {
            var rows=legendRows();
            for (int i=0;i<rows.size();i++) {
                if (rows.get(i).key().equals("alliance") && legendRowHit(legend,i).contains(mouseX,mouseY)) {
                    com.example.examplemod.client.AllianceClient.clearFocus();
                }
            }
            return true;
        }
        CityMapPanel.Selection selected = this.map.getSelection();
        if (selected != null && this.cardActionY >= 0 && inside(mouseX, mouseY, this.cardX, this.cardY, this.cardWidth, this.cardHeight)) {
            for (int index = 0; index < this.cardActionCount; index++) {
                var action=editControl("card.action[" + index + "]", WorldMapUiLayout.cardAction(new WorldMapUiLayout.Rect(this.cardX,this.cardY,this.cardWidth,this.cardHeight),this.cardActionY,index));
                var body=WorldMapUiLayout.cardBody(new WorldMapUiLayout.Rect(cardX,cardY,cardWidth,cardHeight));
                if (WorldMapUiLayout.visiblePart(action,body).contains(mouseX,mouseY)) {
                    handleContextAction(selected, index);
                    return true;
                }
            }
        }
        if (this.cardWidth > 0
                && inside(mouseX, mouseY, this.cardX, this.cardY, this.cardWidth, this.cardHeight)) return true;
        if (!insideMapViewport(mouseX,mouseY) && !map.hasPendingClaim()) return super.mouseClicked(mouseX,mouseY,button);

        pendingSelection=null;
        CityMapPanel panel = this.map;
        panel.clearSelection();
        dismissTutorial();
        CityMapPanel.Selection before = selected;
        boolean handled = panel.mouseClicked(mouseX, mouseY, button);
        // Opening a project from a marker can replace this screen during the click.
        if (this.map != panel || this.minecraft.screen != this) return true;
        CityMapPanel.Selection after = panel.getSelection();
        if (handled && after != null && !after.equals(before)) {
            pushTarget(backHistory,before);
            this.forwardHistory.clear();
            CommunityFx.sound("map_select");
        }
        return handled || super.mouseClicked(mouseX, mouseY, button);
    }

private boolean handleModalClick(double mouseX, double mouseY) {
        if (this.problemCenterOpen) {
            var objects = problemObjects();
            var bounds = WorldMapUiLayout.problemModal(this.width, this.height);
            if(inboxBounds(bounds).contains(mouseX,mouseY)){ SocialHubScreen.openInbox(this); return true; }
            for (int index = 0; index < Math.min(problemRows(), objects.size() - this.problemOffset); index++) if (problemRow(bounds, index).contains(mouseX, mouseY)) {
                selectObject(objects.get(index + this.problemOffset)); this.problemCenterOpen = false; return true;
            }
            this.problemCenterOpen = false;
            return true;
        }
        this.guideOpen = false;
        tutorialSeen = true;
        return true;
    }

    private void handleGlobalAction(int index) {
        if ((index!=WorldMapUiLayout.GLOBAL_BACK || canGoBack())
                && (index!=WorldMapUiLayout.GLOBAL_FORWARD || canGoForward())) CommunityFx.sound("click");
        switch (index) {
            case WorldMapUiLayout.GLOBAL_BACK -> { navigateBack(); dismissTutorial(); }
            case WorldMapUiLayout.GLOBAL_FORWARD -> { navigateForward(); dismissTutorial(); }
            case WorldMapUiLayout.GLOBAL_CENTER_CITY -> {  this.map.centerOn(this.data.getPylonPos()); dismissTutorial(); }
            case WorldMapUiLayout.GLOBAL_SEARCH -> {
                this.searchOpen = !this.searchOpen;
                this.layersOpen = false;
                this.searchBox.setVisible(this.searchOpen);
                if (this.searchOpen) {
                    com.example.examplemod.client.logistics.LogisticsMapOverlay.requestDirectoryForSearch();
                    searchFocus = -1;
                    rebuildSearchCatalog();
                }
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

        navigate(backHistory,forwardHistory);
    }

    private void navigateForward() {
        if (this.forwardHistory.isEmpty()) return;

        navigate(forwardHistory,backHistory);
    }

    private void pushTarget(Deque<AtlasViewContext.Target> history, CityMapPanel.Selection value) {
        if (value == null) return;
        history.push(AtlasViewContext.Target.from(value));
        if (history.size()>64) history.removeLast();
    }
    private void navigate(Deque<AtlasViewContext.Target> history, Deque<AtlasViewContext.Target> reverse) {
        if (map == null) return;
        while (!history.isEmpty()) {
            var target=history.pop();
            var value=map.resolveSelection(target.kind(),target.id(),target.pos());
            if (value!=null) {
                pushTarget(reverse,map.getSelection());
                map.selectAndCenter(value);
                return;
            }
        }
    }

    /** Only the overlays the current mode owns are clickable; a row that is not drawn is not a row. */
    private boolean handleLayerClick(double mouseX, double mouseY) {
        var panel = layersPopoverBounds();
        if (!panel.contains(mouseX, mouseY)) return false;
        if (mouseY < panel.y() + WorldMapUiLayout.POPOVER_HEADER) return true;
        int contentWidth = 228;
        int row = 0;
        for (CityMapPanel.Overlay overlay : activeOverlays()) {
            if (layerRow(row++).contains(mouseX, mouseY)) {
                toggleLayer(overlay);
                return true;
            }
        }
        for(int option=0;option<2;option++)if(layerRow(row++).contains(mouseX,mouseY)) {
            toggleTerritoryPresentation(option==0);
            return true;
        }
        for (int index = 5; index < VIEW_KEYS.length; index++) if (layerRow(row++).contains(mouseX, mouseY)) { handleViewAction(index); return true; }
        return layersPopoverBounds().contains(mouseX, mouseY);
    }

    private void toggleLayer(CityMapPanel.Overlay overlay) {
        map.setOverlay(overlay,!map.isOverlayEnabled(overlay));
        CommunityFx.sound("click");
    }

    private void toggleTerritoryPresentation(boolean changeMode) {
        map.setTerritoryPresentation(changeMode?(map.territoryMode()+1)%3:map.territoryMode(),
                changeMode?map.plotGrid():!map.plotGrid());
        CommunityFx.sound("click");
    }

    private WorldMapUiLayout.Rect layersPopoverBounds() {
        var raw = WorldMapUiLayout.layersPopover(this.width, this.modeLabelWidths, this.globalLabelWidths, layersRowCount(), 228);
        int height = Math.min(raw.height(), Math.max(1, this.height - raw.y() - 28));
        this.layerScroll = Math.max(0, Math.min(this.layerScroll, raw.height() - height));
        return new WorldMapUiLayout.Rect(raw.x(), raw.y(), raw.width(), height);
    }

    private WorldMapUiLayout.Rect layerRow(int index) {
        var raw = WorldMapUiLayout.layersRow(this.width, this.modeLabelWidths, this.globalLabelWidths, index, 228);
        return editControl("layer[" + index + "]", new WorldMapUiLayout.Rect(raw.x(), raw.y() - this.layerScroll, raw.width(), raw.height()));
    }

    private boolean handleSearchClick(double x,double y) {
        if(!searchPanelBounds().contains(x,y))return false;
        searchMessage="";
        if(viewEdit==ViewEdit.NONE) {
            for(int i=0;i<3;i++)if(searchFilterBounds(i).contains(x,y)){activateControl(i);return true;}
            for(int i=0;i<4;i++)if(searchTabBounds(i).contains(x,y)){activateControl(i+3);return true;}
            var rows=visibleSearchResults();
            for(int i=0;i<rows.size();i++)if(searchResultControl(i).contains(x,y)) {
                searchFocus=7;searchCursor=searchOffset+i;setFocused(null);
                if(rows.get(i) instanceof ViewRow row){selectedView=row.view.id();return true;}
                if(x>=searchResultControl(i).right()-20)starResult();else openCurrentSearchRow();return true;
            }
        }
        if(searchTab==AtlasSearch.Tab.VIEWS) {
            var actions=viewActions();
            for(int i=0;i<actions.length;i++)if(searchFooter(i,actions.length).contains(x,y)){activateControl(i+8);return true;}
        }
        return true;
    }
    private String[] viewActions() {
        return viewEdit==ViewEdit.NONE?new String[]{"create","rename","update","delete","open"}:new String[]{"confirm","cancel"};
    }
    private ViewRow selectedViewRow() {
        if(selectedView!=null)for(var row:visibleViews)if(row.view.id().equals(selectedView))return row;
        return searchCursor<visibleViews.size()?visibleViews.get(searchCursor):null;
    }
    private void finishNaming() {
        viewEdit=ViewEdit.NONE;searchBox.setMaxLength(64);searchBox.setValue(queryBeforeNaming);setFocused(null);searchFocus=7;
    }
    private void editWorkingView(String action) {
        if(library==null){searchMessage="unavailable";return;}
        if(action.equals("cancel")){finishNaming();return;}
        var row=selectedViewRow();
        if(action.equals("confirm")) {
            var edit=viewEdit;String name=searchBox.getValue().strip();
            if(edit==ViewEdit.CREATE) {
                UUID id=library.create(name,workingCamera());if(id==null){searchMessage="invalid";return;}
                selectedView=id;if(!writeLibrary(library,true))return;
            } else {
                if(row==null||!row.view.id().equals(selectedView)){searchMessage="unavailable";return;}
                boolean changed=edit==ViewEdit.DELETE?row.library.delete(selectedView):row.library.edit(selectedView,name,null);
                if(!changed){searchMessage="invalid";return;}if(!writeLibrary(row.library,true))return;
            }
            finishNaming();searchBox.setValue("");
            for(int i=0;i<visibleViews.size();i++)if(visibleViews.get(i).view.id().equals(selectedView))moveSearchCursor(i);
            return;
        }
        if(!action.equals("create")&&row==null){searchMessage="pick_view";return;}
        if(row!=null)selectedView=row.view.id();
        if(action.equals("open")) {
            if(row.library==library)applyWorkingView(row.view);
            else startSearchJump(ResourceLocation.parse(row.library.area.dimension()),null,row.library.area,row.view.id());
            return;
        }
        if(action.equals("update")) {
            if(row.library!=library){searchMessage="update_here";return;}
            if(row.library.edit(selectedView,null,workingCamera())&&writeLibrary(row.library,true))searchMessage="updated";
            return;
        }
        viewEdit=switch(action){case "create"->ViewEdit.CREATE;case "rename"->ViewEdit.RENAME;default->ViewEdit.DELETE;};
        queryBeforeNaming=searchBox.getValue();
        if(viewEdit!=ViewEdit.DELETE) {
            searchBox.setMaxLength(48);searchBox.setValue(viewEdit==ViewEdit.RENAME?row.view.name():"");
            searchBox.setHighlightPos(0);setFocused(searchBox);searchFocus=-1;
        } else {searchFocus=8;setFocused(null);}
    }
    private void activateControl(int index) {
        searchMessage="";searchFocus=index;setFocused(null);
        if(index>=8){var actions=viewActions();if(index-8<actions.length)editWorkingView(actions[index-8]);return;}
        if(index==7){openCurrentSearchRow();return;}
        CommunityFx.sound("click");
        if(index>=3){switchSearchTab(AtlasSearch.Tab.values()[index-3]);return;}
        if(index==0){
            var types=AtlasSearch.Type.values();
            do {searchType=(searchType+1)%types.length;} while(types[searchType]==AtlasSearch.Type.PROJECT);
        }
        else if(index==1)searchOwner=(searchOwner+1)%3;
        else searchProblems=!searchProblems;
        searchOffset=searchCursor=0;updateSearchResults();
    }
    private void moveSearchCursor(int index) {
        searchCursor=Math.clamp(index,0,Math.max(0,allSearchRows().size()-1));
        searchOffset=Math.max(searchCursor-searchRows()+1,Math.min(searchCursor,searchOffset));
        if(searchTab==AtlasSearch.Tab.VIEWS&&searchCursor<visibleViews.size())selectedView=visibleViews.get(searchCursor).view.id();
    }
    private void starResult() {
        if(library==null||searchCursor>=searchResults.size()){searchMessage="unavailable";return;}
        if(library.toggle(searchResults.get(searchCursor).key()))writeLibrary(library,true);
        else searchMessage="favorite_limit";
        updateSearchResults();
    }
    private void openCurrentSearchRow() {
        if(searchTab==AtlasSearch.Tab.VIEWS){editWorkingView("open");return;}
        if(searchCursor>=searchResults.size())return;
        var entry=searchResults.get(searchCursor);
        if(entry.type()==AtlasSearch.Type.COORDINATES&&entry.dimension().equals(data.getDimensionId())) {
            if(library!=null&&library.visit(entry.key()))writeLibrary(library,false);
            map.clearSelection();map.centerOn(entry.pos());closeSearchPanel();return;
        }
        if(!openSearchKey(entry.key())){rebuildSearchCatalog();searchMessage="unavailable";}
    }
    private boolean searchKey(int key,int modifiers) {
        if(key==GLFW.GLFW_KEY_ESCAPE){if(viewEdit==ViewEdit.NONE)closeSearchPanel();else finishNaming();return true;}
        if(key==GLFW.GLFW_KEY_TAB) {
            int last=searchTab==AtlasSearch.Tab.VIEWS?7+viewActions().length:7;
            int step=(modifiers&GLFW.GLFW_MOD_SHIFT)!=0?-1:1;
            if(viewEdit!=ViewEdit.NONE) {
                int[] order=viewEdit==ViewEdit.DELETE?new int[]{8,9}:new int[]{-1,8,9};int idx=0;
                while(idx<order.length&&order[idx]!=searchFocus)idx++;
                searchFocus=order[Math.floorMod(idx+step,order.length)];
            } else searchFocus=Math.floorMod(searchFocus+1+step,last+2)-1;
            setFocused(searchFocus==-1?searchBox:null);narrateSearchFocus();return true;
        }
        if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_KP_ENTER) {
            if(viewEdit!=ViewEdit.NONE)editWorkingView(searchFocus==9?"cancel":"confirm");
            else if(searchFocus==-1||searchFocus==7)openCurrentSearchRow();else activateControl(searchFocus);
            return true;
        }
        if(viewEdit==ViewEdit.NONE&&(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN)) {
            moveSearchCursor(searchCursor+(key==GLFW.GLFW_KEY_UP?-1:1));searchFocus=7;setFocused(null);narrateSearchFocus();return true;
        }
        if(searchFocus==7&&searchTab!=AtlasSearch.Tab.VIEWS&&key==GLFW.GLFW_KEY_SPACE){starResult();return true;}
        if(key==GLFW.GLFW_KEY_SPACE&&searchFocus>=0){activateControl(searchFocus);return true;}
        return searchFocus!=-1;
    }
    private void narrateSearchFocus() {
        Component text;
        if(searchFocus==-1)text=AtlasSearch.tr(viewEdit==ViewEdit.NONE?"search":"name");
        else if(searchFocus<3)text=searchFocus==0?searchTypeLabel():Component.translatable(searchFocus==1
                ?"gui.createcityexpansioncore.world_map.filter.owner."+searchOwner:"gui.createcityexpansioncore.world_map.filter."+(searchProblems?"problems":"any_status"));
        else if(searchFocus<7)text=AtlasSearch.tr("tab."+AtlasSearch.Tab.values()[searchFocus-3].name().toLowerCase(Locale.ROOT));
        else if(searchFocus==7&&searchCursor<allSearchRows().size()) {
            Object item=allSearchRows().get(searchCursor);text=Component.literal(item instanceof AtlasSearch.Entry e?e.name()+" · "+e.context():((ViewRow)item).view.name());
        } else if(searchFocus>=8)text=AtlasSearch.tr(viewActions()[searchFocus-8]+"_help");
        else text=AtlasSearch.tr("empty");
        minecraft.getNarrator().sayNow(text);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if(atlasTool!=null){atlasTool.mouseDragged(mouseX,mouseY,button,dragX,dragY);return true;}
        if(planEditor!=null){planEditor.mouseDragged(mouseX,mouseY,button,dragX,dragY);return true;}
        if(closingModal()||this.map!=null&&(overActivePopover(mouseX,mouseY)||closingPopover(mouseX,mouseY)))return true;
        if (this.mode==Mode.CITIES && projectWorkspace.blocksDrag(mouseX,mouseY)) return true;
        dismissTutorial();
        if (this.pendingMutation != null || this.guideOpen || this.problemCenterOpen || this.map != null && this.map.hasPendingClaim()) return true;
        // Панорамировать нечего, пока панели нет: свайп по экрану загрузки.
        if (this.map == null) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        boolean dragged = this.map.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        return dragged || super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if(atlasTool!=null){atlasTool.mouseReleased(mouseX,mouseY,button);return true;}
        if(planEditor!=null){planEditor.mouseReleased(mouseX,mouseY,button);return true;}
        // Клик мог начаться до панели и закончиться после: отпускание обязано
        // быть безопасным независимо от того, что нажатие было проигнорировано.
        if (this.map == null) return super.mouseReleased(mouseX, mouseY, button);
        return this.map.mouseReleased(mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(atlasTool!=null){atlasTool.mouseScrolled(mouseX,mouseY,scrollX,scrollY);return true;}
        if(planEditor!=null){planEditor.mouseScrolled(mouseX,mouseY,scrollX,scrollY);return true;}
        if(closingModal()||this.map!=null&&closingPopover(mouseX,mouseY))return true;
        if (this.mode==Mode.CITIES && !this.guideOpen && !this.problemCenterOpen && this.pendingMutation==null && !this.layersOpen && !this.searchOpen && projectWorkspace.scroll(mouseX,mouseY,scrollY)) return true;
        if (this.guideOpen) { this.guideScroll = Math.max(0, Math.min(this.guideMaxScroll, this.guideScroll + (scrollY > 0 ? -16 : 16))); return true; }
        if (this.pendingMutation != null || this.map != null && this.map.hasPendingClaim()) return true;
        if (this.problemCenterOpen) { this.problemOffset = Math.max(0, Math.min(Math.max(0, problemObjects().size() - problemRows()), this.problemOffset + (scrollY > 0 ? -1 : 1))); return true; }
        if (this.searchOpen && searchPanelBounds().contains(mouseX, mouseY)) {
            searchOffset=Math.clamp(searchOffset+(scrollY>0?-1:1),0,Math.max(0,allSearchRows().size()-searchRows()));
            moveSearchCursor(Math.clamp(searchCursor,searchOffset,searchOffset+searchRows()-1));return true;
        }
        if (this.layersOpen && layersPopoverBounds().contains(mouseX, mouseY)) {
            int fullHeight = WorldMapUiLayout.POPOVER_HEADER + layersRowCount() * WorldMapUiLayout.POPOVER_ROW;
            this.layerScroll = Math.max(0, Math.min(fullHeight - layersPopoverBounds().height(), this.layerScroll + (scrollY > 0 ? -20 : 20))); return true;
        }
        var legend=visibleLegend();
        if(map!=null && legend!=null && legend.contains(mouseX,mouseY)) {
            int full=WorldMapUiLayout.legendHeader(legend)+(legendRows().size()+1)*WorldMapUiLayout.LEGEND_ROW;
            legendScroll=Math.clamp(legendScroll+(scrollY>0?-18:18),0,Math.max(0,full-legend.height()));
            return true;
        }
        if (inside(mouseX, mouseY, this.cardX, this.cardY, this.cardWidth, this.cardHeight)) {
            this.cardScroll = Math.max(0, Math.min(this.cardMaxScroll, this.cardScroll + (scrollY > 0 ? -16 : 16))); return true;
        }
        dismissTutorial();
        if (mainControls() && LogisticsMapOverlay.scrollInspection(mouseX,mouseY,scrollY)) return true;
        if (this.map == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        if (scrollY!=0 && insideMapViewport(mouseX,mouseY)) com.example.examplemod.client.logistics.LogisticsMapOverlay.cancelAutomaticFocus();
        return this.map.mouseScrolled(mouseX, mouseY, scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Блокирует подтверждение занятия участка в режиме чтения.
     *
     * <p>Правая кнопка уже не доходит до панели, но подтверждение могло остаться
     * открытым с прошлого открытия панели: та же самая панель переиспользуется
     * вкладкой «Карта». Оно и нажатие Enter отправили бы claim участка — то есть
     * изменили бы город с карты, которая для чтения.
     */
    private boolean blockClaimIfReadOnly() {
        if (!this.readOnly || this.map == null || !this.map.hasPendingClaim()) {
            return false;
        }
        this.map.cancelPendingClaim();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        hoverDelay.keyboardInput(mouseXNow,mouseYNow);
        if(atlasTool!=null){atlasTool.keyPressed(keyCode,scanCode,modifiers);return true;}
        if(planEditor!=null){planEditor.keyPressed(keyCode,scanCode,modifiers);return true;}
        if (map != null) map.interruptCamera();
        if(closingModal())return true;
        if(this.map!=null&&!this.guideOpen&&!this.problemCenterOpen&&this.pendingMutation==null&&!this.map.hasPendingClaim()
                &&keyCode==GLFW.GLFW_KEY_F&&(modifiers&GLFW.GLFW_MOD_CONTROL)!=0) {
            if(!this.searchOpen)handleGlobalAction(WorldMapUiLayout.GLOBAL_SEARCH);
            searchFocus=-1;setFocused(this.searchBox);return true;
        }
        if (this.mode==Mode.CITIES && !this.guideOpen && !this.problemCenterOpen && this.pendingMutation==null && !this.layersOpen && !this.searchOpen && projectWorkspace.key(keyCode,scanCode,modifiers)) return true;
        if (useAtlasKeyboard(keyCode,modifiers)) return true;
        if (keyCode==GLFW.GLFW_KEY_ESCAPE && layersOpen && !guideOpen && !problemCenterOpen && pendingMutation==null) { layersOpen=false; return true; }
        if (this.pendingMutation != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) this.pendingMutation = null;
            return true;
        }
        if (guideOpen || problemCenterOpen) {
            if (keyCode==GLFW.GLFW_KEY_ESCAPE) { guideOpen=false; problemCenterOpen=false; }
            return true;
        }
        if (this.map != null && mainControls() && keyCode == GLFW.GLFW_KEY_ESCAPE && this.map.cancelPersonalTools()) return true;
        if (this.searchOpen && searchKey(keyCode, modifiers)) return true;
        /*
         * Открытое подтверждение claim — самое верхнее состояние атласа:
         * Escape должен отменять один участок, а не закрывать весь атлас
         * вместе с выбором, строкой поиска и панелью слоёв.
         */
        if (blockClaimIfReadOnly()) {
            return true;
        }
        if (this.map != null && this.map.hasPendingClaim()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_Q) {
                this.map.cancelPendingClaim();
            } else if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                this.map.confirmPendingClaim();
            }
            return true;
        }
        if (this.map != null && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.searchOpen) {
                this.searchOpen = false; this.searchBox.setVisible(false); setFocused(null); return true;
            }
            if (this.layersOpen) { this.layersOpen = false; return true; }
            if (map!=null && map.getSelection()!=null && LogisticsMapOverlay.inspecting(map.getSelection().id(),map.dimensionId())) {
                LogisticsMapOverlay.finishInspection(); cardScroll=0; return true;
            }
            if (directions != null) { directions = null; cardScroll = 0; return true; }
            if (this.map.getSelection()!=null) { this.map.clearSelection(); pendingSelection=null; return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean charTyped(char character,int modifiers) {
        hoverDelay.keyboardInput(mouseXNow,mouseYNow);
        if(atlasTool!=null){atlasTool.charTyped(character,modifiers);return true;}
        if(planEditor!=null){planEditor.charTyped(character,modifiers);return true;}
        if (guideOpen || problemCenterOpen || pendingMutation!=null || map!=null && map.hasPendingClaim()) return true;
        if (this.mode==Mode.CITIES && !this.guideOpen && !this.problemCenterOpen && this.pendingMutation==null && !this.layersOpen && !this.searchOpen && projectWorkspace.character(character,modifiers)) return true;
        return super.charTyped(character,modifiers);
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
        if (this.map != null && this.preferences != null) rememberOverlays();
        this.mode = mode;
        /*
         * Панели может не быть ещё или не быть вовсе.
         *
         * <p>Пилон города приходит с сервера, поэтому при первом открытии карты
         * по клавише M её ещё нет, а у игрока без города не будет никогда. Раньше
         * здесь стоял безусловный setFocus, и init() ронял игру NullPointerException
         * ровно на первом кадре: нажатие M без города было фатальным.
         *
         * <p>Режим запоминается и применяется из ensurePanel(), когда панель
         * наконец появится, — поэтому переключение вкладки до загрузки не
         * теряется.
         */
        if (this.map == null) return;
        this.map.setFocus(mode.focus);
        this.map.setOverlay(CityMapPanel.Overlay.CITIES, true);
        switch (mode) {
            case CITIES -> setOverlays(true,true);

case ENERGY -> setOverlays(false,false);
        }
        this.map.setOverlay(CityMapPanel.Overlay.RELATIONS, mode == Mode.CITIES);
        if (this.preferences != null && this.preferences.overlays[mode==Mode.CITIES?0:3] >= 0) {
            int mask = this.preferences.overlays[mode==Mode.CITIES?0:3];
            for (var layer : CityMapPanel.Overlay.values()) this.map.setOverlay(layer, (mask & (1 << layer.preferenceBit())) != 0);
        }
        /*
         * The logistics center map is about the rail network; its own "line" chip is the control
         * that hides rails there. A normal atlas preference (where railways may be off) must not
         * make the center's map show stations without the tracks that connect them.
         */
        if (mode == Mode.CITIES && com.example.examplemod.client.logistics.LogisticsMapOverlay.isSession()
                && !this.map.isOverlayEnabled(CityMapPanel.Overlay.TRACKS)) {
            this.map.setOverlay(CityMapPanel.Overlay.TRACKS, true);
            this.sessionForcedTracks = true;
        }
    }

    private void setOverlays(boolean territories,boolean projects){this.map.setOverlay(CityMapPanel.Overlay.TERRITORIES,territories);this.map.setOverlay(CityMapPanel.Overlay.PROJECTS,projects);}

    private Component selectionType(CityMapPanel.Selection value) {
        if(value.kind()==CityMapPanel.SelectionKind.CARTOGRAPHY){
            var feature=CityMapFeatures.get(value.id());
            if(feature!=null)return Component.translatable("gui.createcityexpansioncore.city_editor.kind."+feature.kind().name().toLowerCase(Locale.ROOT));
        }
        if (value.kind()==CityMapPanel.SelectionKind.LOGISTICS) return atlasText("kind."+LogisticsMapOverlay.markerKind(value.id(),map.dimensionId()));
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

    private Path preferencePath() { return this.minecraft.gameDirectory.toPath().resolve("config/ccec-atlas.properties"); }
    private void loadPreferences() {
        if (this.map == null || this.minecraft == null || this.minecraft.player == null) return;
        UUID worldId = com.example.examplemod.client.CityPublicDirectory.worldId(this.data.getDimensionId());
        if (worldId == null || worldId.equals(preferenceWorldId) && this.preferences != null) return;
        preferenceWorldId = worldId;
        String server = this.minecraft.getCurrentServer() != null ? this.minecraft.getCurrentServer().ip
                : this.minecraft.getSingleplayerServer() != null ? this.minecraft.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().toString() : "local";
        this.preferenceWorld = "world-v4:" + worldId + ":" + server + ":" + this.minecraft.player.getUUID() + ":" + this.data.getDimensionId();
        attachLibrary(server, worldId);
        AtlasPreferences loaded = AtlasPreferences.load(preferencePath(), this.preferenceWorld);
        this.preferences = null;
        applyMode(Mode.CITIES);
        this.preferences = loaded;
        if (this.preferences.positioned) this.map.restoreView(this.preferences.centerX, this.preferences.centerZ, this.preferences.zoom);
        this.map.setPresentation(this.preferences.grid, this.preferences.minimap);
        this.map.setTerritoryPresentation(this.preferences.territoryMode,this.preferences.plotGrid);
        var display = com.example.examplemod.client.map.MinimapClient.settings();
        display.importReducedMotion(this.preferences.reducedMotion);
        this.map.setMotion(true,display.reducedMotion);
        this.map.setPersonalPins(this.preferences.pins);
        int mask = this.preferences.overlays[this.mode==Mode.CITIES?0:3];
        if (mask >= 0) for (var layer : CityMapPanel.Overlay.values()) this.map.setOverlay(layer, (mask & (1 << layer.preferenceBit())) != 0);
        /*
         * Masks written before TRACKS joined the CITIES layer list have bit 3 clear because the
         * layer did not exist there. Treat such a mask as "not asked yet" once: the first updated
         * map shows real rails, and a deliberate toggle-off is persisted in every later mask.
         */
        if (!this.preferences.tracksPreference) {
            this.map.setOverlay(CityMapPanel.Overlay.TRACKS, true);
            this.preferences.tracksPreference = true;
        }
        restoreView(VIEWS.get(minecraft.getConnection(),viewKey()));
    }
    private String viewKey() {
        return preferenceWorld == null ? null : preferenceWorld + (readOnly ? planningSession ? ":planning" : ":atlas" : ":pylon");
    }
    AtlasViewContext.State captureView() {
        if (map == null) return null;
        var target = pendingSelection == null ? AtlasViewContext.Target.from(map.getSelection()) : pendingSelection;
        return new AtlasViewContext.State(map.centerX(), map.centerZ(), map.zoom(), searchOpen,
                viewEdit!=ViewEdit.NONE?queryBeforeNaming:searchBox == null ? restoredQuery : searchBox.getValue(), searchType, searchOwner,
                searchProblems, searchOffset, pendingSelection == null ? cardKey : pendingCardKey,
                pendingSelection == null ? cardScroll : pendingCardScroll, target,searchTab,searchCursor,selectedView,queryBeforeViews);
    }
    void restoreView(AtlasViewContext.State state) {
        if (map == null || state == null) return;
        map.restoreView(state.x(),state.z(),state.zoom());
        restoredQuery=state.query(); searchOpen=state.searchOpen();
        searchType=state.searchType(); searchOwner=state.searchOwner(); searchProblems=state.problems();
        searchTab=state.searchTab();selectedView=state.namedView();queryBeforeViews=state.objectQuery();viewEdit=ViewEdit.NONE;
        if (searchBox != null) {searchBox.setValue(restoredQuery);searchBox.setVisible(searchOpen);}
        searchCursor=state.searchCursor();
        searchOffset=state.searchOffset();cardKey=state.cardKey();cardScroll=state.cardScroll();
        pendingSelection=state.selected();pendingCardScroll=cardScroll;pendingCardKey=cardKey;
        map.clearSelection();
        resolvePending(false);
    }
    private void resolvePending(boolean completeDirectory) {
        if (map == null || pendingSelection == null) return;
        var target=pendingSelection;
        var value=map.resolveSelection(target.kind(),target.id(),target.pos());
        boolean authoritative=switch(target.kind()) {
            case CITY -> completeDirectory;
            case PROJECT -> com.example.examplemod.client.ProjectClientState.hasMapSnapshot();
            case LOGISTICS -> LogisticsMapOverlay.hasDirectory(map.dimensionId())
                    && (!LogisticsMapOverlay.supportsTrade(map.dimensionId()) || AtlasTradeDirectory.finishedRestore(target.id()));
            case WAYPOINT -> preferences!=null;
            case PLACE -> AtlasPlaces.ready(map.dimensionId()) || !AtlasPlaces.supported();
            case CARTOGRAPHY -> CityMapFeatures.ready(map.dimensionId()) || !CityMapFeatures.supported();
            case PASSENGER_LINE, PASSENGER_TRAIN -> CityMapFeatures.railReady(map.dimensionId());
            default -> false;
        };
        if (value!=null || authoritative) {
            if(value==null) showCardNotice("unavailable");
            map.setSelection(value);
            if(value!=null){cardScroll=pendingCardScroll;cardKey=pendingCardKey;}
            pendingSelection=null;
        }
    }
    public void privateSnapshotReady() {
        if (map == null) return;
        map.setProjects(data.getProjects());
        var target=pendingSelection!=null?pendingSelection:AtlasViewContext.Target.from(map.getSelection());
        if(target!=null && target.kind()==CityMapPanel.SelectionKind.INDUSTRY) {
            var resolved=map.resolveSelection(target.kind(),target.id(),target.pos());
            map.setSelection(resolved);
            if (resolved!=null && pendingSelection!=null) {cardScroll=pendingCardScroll;cardKey=pendingCardKey;}
            pendingSelection=null;
        }
    }
    private void rememberOverlays() {
        int mask = 0; for (var layer : CityMapPanel.Overlay.values()) if (this.map.isOverlayEnabled(layer)) mask |= 1 << layer.preferenceBit();
        if (this.sessionForcedTracks) mask &= ~(1 << CityMapPanel.Overlay.TRACKS.preferenceBit());
        this.preferences.overlays[this.mode==Mode.CITIES?0:3] = mask;
    }
    private void savePreferences() {
        if (this.preferences == null || this.map == null || minecraft.getConnection() == null || minecraft.getConnection() != connection.get()) return;
        VIEWS.put(minecraft.getConnection(), viewKey(), captureView());
        rememberOverlays(); this.preferences.mode = this.mode.ordinal(); this.preferences.centerX = this.map.centerX();
        this.preferences.centerZ = this.map.centerZ(); this.preferences.zoom = this.map.zoom(); this.preferences.positioned = true;
        this.preferences.grid = this.map.coordinateGrid(); this.preferences.minimap = this.map.overviewVisible();
        this.preferences.territoryMode=this.map.territoryMode();this.preferences.plotGrid=this.map.plotGrid();
        this.preferences.pins.clear(); this.preferences.pins.addAll(this.map.personalPins());
        try { this.preferences.save(preferencePath(), this.preferenceWorld); }
        catch (java.io.IOException error) { org.slf4j.LoggerFactory.getLogger(PolishedWorldMapScreen.class).warn("Cannot save local atlas view", error); }
    }
    @Override public void removed() {
        closeAtlasTool();AtlasPlaces.clear();
        searchCatalog=List.of();searchResults=List.of();listedPlaces=List.of();
        if(planEditor!=null){planEditor.detach();exitPlanEditor();}
        if (journeyWorkspaceOpen || finished) { super.removed(); return; }
        finished=true;
        if(map!=null)returnDimension=map.dimensionId();
        // Every way out of the atlas (ESC, a child screen, the "open object" action) must stop the
        // ambient logistics feed; otherwise a finished center session leaks its markers onto the
        // next normal world map opened in this client.
        boolean ownsView = !readOnly || java.util.Objects.equals(directoryView,com.example.examplemod.client.CityPublicDirectory.viewId());
        if (ownsView) {
            com.example.examplemod.client.logistics.LogisticsMapOverlay.deactivate();
            ProjectWorkspaceClient.clear();
            savePreferences();
        }
        this.pendingMutation = null;
        if (this.map != null) {
            this.map.setMotion(false,false); this.map.setProjectFocus(false,false); this.map.setVisualOverlay(null);
            this.map.cancelPersonalTools(); this.map.setDrawInternalControls(true); this.map.setRailOverlay(null);
            this.map.setTrackCount(0);
            if (readOnly) discardOwnedPanel();
            else if (!validReturn()) this.map.releaseTerrainTextures();
        }
        if (this.readOnly && ownsView) com.example.examplemod.client.CityPublicDirectory.reset();
        if (ownsView) com.example.examplemod.client.ProjectClientState.clear();
        super.removed();
    }
    private void discardOwnedPanel() {
        if (map == null) return;
        map.releaseTerrainTextures();
        map=null;
        com.example.examplemod.client.map.MapDiagnostics.released();
    }
    private WorldMapUiLayout.Workspace workspace;
    private boolean compactTools;

    private int layoutViewButtons() {
        this.viewButtons.clear();
        int header=WorldMapUiLayout.headerBottom(width,modeLabelWidths,globalLabelWidths);
        trainActionVisible = this.mode==Mode.CITIES && com.example.examplemod.client.logistics.LogisticsMapOverlay.selectedTrain(map.dimensionId())!=null;
        recalculateToolVisible=AtlasTripScreen.needsRecalculation();
        drawingCity=cityForDrawing();
        int[] labels=new int[(trainActionVisible ? 7 : 6)+(recalculateToolVisible?1:0)+(PassengerLinesScreen.supported()?1:0)+(drawingCity!=null?1:0)];
        for(int i=0;i<labels.length;i++)labels[i]=this.font.width(viewLabel(viewActionAt(i)));
        compactTools=WorldMapUiLayout.compactWorkspaceTools(width,height,labels);
        this.viewButtons.addAll(WorldMapUiLayout.workspaceTools(width,height,header,labels));
        return this.viewButtons.getLast().bottom();
    }
    private int viewActionAt(int index) {
        int action=index < 2 || trainActionVisible ? index : index + 1;
        if(action<5)return action;
        if(action==5)return PLAN_TOOL;
        if(action==6)return PLACE_TOOL;
        if(recalculateToolVisible){if(action==7)return RECALCULATE_TOOL;action--;}
        return action==7 && PassengerLinesScreen.supported()?PASSENGER_TOOL:CARTOGRAPHY_TOOL;
    }
    private Component viewLabel(int action) {
        if(action==CARTOGRAPHY_TOOL)return atlasText("drawing_tool");
        if(action==PASSENGER_TOOL)return PassengerLinesScreen.text("title");
        if(action==PLACE_TOOL || action==RECALCULATE_TOOL)return AtlasPlaces.text(action==PLACE_TOOL?"add":"recalculate");
        return action==PLAN_TOOL ? AtlasPlanEditor.text("tool") : Component.translatable("gui.createcityexpansioncore.world_map.view."+VIEW_KEYS[action]);
    }
    private boolean insideMapViewport(double x, double y) {
        return map != null && x >= map.viewportLeft() && x < map.viewportRight()
                && y >= map.viewportTop() && y < map.viewportBottom();
    }
    private boolean canAddPlace() {
        return planningAvailable() && AtlasPlaces.supported() && minecraft.player!=null &&
                minecraft.player.level().dimension().location().equals(map.dimensionId());
    }
    boolean canDraw(UUID city) {
        if (com.example.examplemod.citymap.CityMapNetwork.OUTSIDE.equals(city)) return map != null && CityMapFeatures.supportsIcons() && minecraft.player != null
                && minecraft.player.hasPermissions(2) && map.dimensionId().equals(minecraft.player.level().dimension().location());
        var profile=CityPublicDirectory.profile(city);
        return map!=null && CityMapFeatures.supported() && minecraft.player!=null && profile!=null &&
                profile.dimension().equals(map.dimensionId()) && profile.dimension().equals(minecraft.player.level().dimension().location()) &&
                (profile.own() || minecraft.player.hasPermissions(2));
    }
    private UUID cityForDrawing() {
        if(mode!=Mode.CITIES || !CityMapFeatures.supported() || minecraft.player==null)return null;
        var remembered=rememberedDrawingCity();
        if(remembered!=null) return remembered;
        for(var profile:CityPublicDirectory.profiles(map.dimensionId()))
            if(canDraw(profile.id()))return profile.id();
        return canDraw(com.example.examplemod.citymap.CityMapNetwork.OUTSIDE) ? com.example.examplemod.citymap.CityMapNetwork.OUTSIDE : null;
    }
    private UUID rememberedDrawingCity() {
        return library!=null && lastDrawingCity!=null && library.area.equals(lastDrawingCity.area())
                && canDraw(lastDrawingCity.id()) ? lastDrawingCity.id() : null;
    }
    List<CityPublicProfile> drawingCities() {
        return CityPublicDirectory.profiles(map.dimensionId()).stream().filter(p->canDraw(p.id()))
                .sorted(Comparator.comparing(CityPublicProfile::name,String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(CityPublicProfile::id)).toList();
    }
    void openDrawingCity(UUID target, CityMapFeature feature) {
        if(!planningAvailable() || !canDraw(target) || feature!=null && !target.equals(feature.city())) return;
        if (minecraft.player.hasPermissions(2)) lastDrawingCity=new DrawingCity(library.area,target);
        openAtlasTool(new CityCartographyScreen(this,map,target,feature));
        if(feature==null) {
            map.clearSelection();
            if (com.example.examplemod.citymap.CityMapNetwork.OUTSIDE.equals(target)) return;
            var anchor=CityPublicDirectory.profile(target).pos();
            map.restoreView(anchor.getX()+.5,anchor.getZ()+.5,map.zoom());
        }
    }
    private void renderViewButtons(GuiGraphics g,int mx,int my) {
        int top=viewButtons.getFirst().y();
        plate(g,WorldMapUiLayout.EDGE-MICRO,top-MICRO,width-2*(WorldMapUiLayout.EDGE-MICRO),
                viewButtons.getLast().bottom()-top+2*MICRO);
        for(int viewIndex=0;viewIndex<this.viewButtons.size();viewIndex++) {
            int i=viewActionAt(viewIndex);
            var r=editControl("view[" + viewIndex + "]", this.viewButtons.get(viewIndex));
            boolean active=i==2&&this.map.followedTrain()!=null||i==3&&this.map.pinToolActive()||i==4&&this.map.measureToolActive();
            boolean enabled=switch(i){case PLAN_TOOL,PASSENGER_TOOL->planningAvailable();case PLACE_TOOL->canAddPlace();default->true;};
            boolean hovered=r.contains(mx,my) && !guideOpen && !problemCenterOpen && pendingMutation==null
                    && !map.hasPendingClaim() && !closingModal() && !overActivePopover(mx,my) && !closingPopover(mx,my);
            paintControl(g,r,ControlGroup.VIEW,viewIndex,hovered,enabled,active,false,false,false);
            Component label=viewLabel(i);
            if(compactTools){
                int size=SocialHubIcons.SIZE;
                int iconX=r.x()+(r.width()-size)/2,iconY=r.y()+(r.height()-size)/2;
                int color=!enabled?MUTED:active||hovered?ACCENT:TEXT;
                if(i==CARTOGRAPHY_TOOL) SocialHubIcons.Icon.EDIT.draw(g,iconX,iconY,hovered,0);
                else if(i==PASSENGER_TOOL) MapMarkerRenderer.drawGlyph(g,AtlasGlyph.TRAIN,iconX,iconY,
                        enabled && hovered?DispatchPalette.ACCENT:color);
                else WorldMapChrome.viewIcon(g,i,iconX,iconY,color);
            }
            else g.drawString(this.font,WorldMapChrome.fit(this.font,label,r.width()-SECTION),r.x()+6,r.y()+(r.height()-8)/2,enabled?TEXT:MapPalette.TEXT_FAINT,false);
            if(mainControls()) {
                var hint=switch(i){
                    case CARTOGRAPHY_TOOL -> minecraft.player!=null && minecraft.player.hasPermissions(2)
                            ? atlasText("drawing_admin_hint") : atlasText("drawing_hint",CityPublicDirectory.profile(drawingCity).name());
                    case PLACE_TOOL -> AtlasPlaces.text(readOnly && !planningSession && mode==Mode.CITIES
                            && minecraft.player!=null && minecraft.player.hasPermissions(2)?"add_map_hint":"add_hint");
                    case RECALCULATE_TOOL -> AtlasPlaces.text("recalculate");
                    case PASSENGER_TOOL -> PassengerLinesScreen.text("hint");
                    case PLAN_TOOL -> AtlasPlanEditor.text(enabled?"tool_hint":"await_scope");
                    default -> atlasText("view."+i);
                };
                keyboard.add("view."+i,label,hint,r,()->{if(enabled)handleViewAction(i);});
                offerTooltip(hint,r.contains(mx,my));
            }
        }
        if(this.map.pinToolActive()||this.map.measureToolActive()) {
            int width=Math.max(1,WorldMapUiLayout.contextPanelLeft(this.width)-WorldMapUiLayout.EDGE-NORMAL);
            g.drawWordWrap(this.font,Component.translatable("gui.createcityexpansioncore.world_map.tool."+(this.map.pinToolActive()?"pin":"measure")),WorldMapUiLayout.EDGE,this.viewButtons.getLast().bottom()+NORMAL,width,TEXT);
        }
    }

    private void handleViewAction(int index) {
        if(index==CARTOGRAPHY_TOOL){
            var cities=drawingCities();
            if(cities.isEmpty()) { openDrawingCity(com.example.examplemod.citymap.CityMapNetwork.OUTSIDE,null); return; }
            UUID remembered=rememberedDrawingCity();
            if(remembered==null && (cities.size()>1 || canDraw(com.example.examplemod.citymap.CityMapNetwork.OUTSIDE)) && minecraft.player.hasPermissions(2))
                openAtlasTool(new CityCartographyScreen(this,map,null,null));
            else openDrawingCity(remembered==null?cities.getFirst().id():remembered,null);
            return;
        }
        if(index==PASSENGER_TOOL){if(PassengerLinesScreen.supported())openAtlasTool(new PassengerLinesScreen(this));return;}
        if(index==PLACE_TOOL){
            if(canAddPlace())
                openAtlasTool(new AtlasPlaceEditor(this,map,null,null));
            return;
        }
        if(index==RECALCULATE_TOOL){AtlasTripScreen.recalculate(this,map);return;}
        if(index==PLAN_TOOL){openPlanEditor();return;}
        if (index < 5 && index != 2) map.interruptCamera();
        switch (index) {
            case 0 -> {  this.map.centerOnPlayer(); }
            case 1 -> {  this.map.fitCity(); }
            case 2 -> this.map.toggleFollow(com.example.examplemod.client.logistics.LogisticsMapOverlay.selectedTrain(map.dimensionId()));

            case 3 -> this.map.togglePinTool();
            case 4 -> this.map.toggleMeasureTool();
            case 5 -> this.map.setPresentation(this.map.coordinateGrid(), !this.map.overviewVisible());
            case 6 -> this.map.setPresentation(!this.map.coordinateGrid(), this.map.overviewVisible());
            case 7 -> { this.map.retryTerrain(); com.example.examplemod.client.CityPublicDirectory.retry(); requestSnapshots(true); }
            case 8 -> {
                this.layersOpen = false;
                com.example.examplemod.client.CityPublicDirectory.cancelLoading();
                PlayerProfileScreen.open(this, this.minecraft.player.getUUID());
            }
            case 9 -> {
                var display = com.example.examplemod.client.map.MinimapClient.settings();
                display.setReducedMotion(!display.reducedMotion);
                this.map.setMotion(true,display.reducedMotion);
            }
            case 10 -> {
                var display = com.example.examplemod.client.map.MinimapClient.settings();
                display.inertia = !display.inertia; display.save();
                this.map.setInertiaEnabled(display.inertia);
            }
            case 12 -> {
                var display=com.example.examplemod.client.map.MinimapClient.settings();
                display.contextHints=!display.contextHints;
                display.save();
            }
            case 11 -> {
                var display = com.example.examplemod.client.map.MinimapClient.settings();
                display.smoothTerrain = !display.smoothTerrain; display.save();
                this.map.setSmoothTerrain(display.smoothTerrain);
            }
            default -> { }
        }
    }

    private int searchRows() { return Math.max(1,Math.min(8,(height-popoverY()-95)/25)); }
    private List<?> allSearchRows() {return searchTab==AtlasSearch.Tab.VIEWS?visibleViews:searchResults;}
    private List<?> visibleSearchResults() {
        List<?> all=allSearchRows();
        searchOffset=Math.clamp(searchOffset,0,Math.max(0,all.size()-searchRows()));
        return all.subList(searchOffset,Math.min(all.size(),searchOffset+searchRows()));
    }
    private WorldMapUiLayout.Rect searchPanelBounds() {
        return WorldMapUiLayout.searchPopover(width,modeLabelWidths,globalLabelWidths,visibleSearchResults().size(),228);
    }
    private WorldMapUiLayout.Rect searchTabBounds(int i) {
        var p=searchPanelBounds();int w=(p.width()-10)/4;
        return new WorldMapUiLayout.Rect(p.x()+5+i*w,p.y()+49,w-2,17);
    }
    private WorldMapUiLayout.Rect searchFooter(int i,int count) {
        var p=searchPanelBounds();int w=(p.width()-8)/count;
        return new WorldMapUiLayout.Rect(p.x()+4+i*w,p.bottom()-19,w-1,16);
    }
    private Component searchTypeLabel() {
        var types=AtlasSearch.Type.values();
        return AtlasSearch.tr("type."+types[Math.clamp(searchType,0,types.length-1)].name().toLowerCase(Locale.ROOT));
    }
    private List<CityMapPanel.Selection> listedPlaces=List.of();
    private void rebuildSearchCatalog() {
        listedPlaces=map==null || !searchOpen ? List.of() : AtlasPlaces.selections(map.dimensionId());
        searchCatalog=map==null||!searchOpen?List.of():AtlasSearch.catalog(map,data.getDimensionId(),data.getCityName());
        updateSearchResults();
    }
    private void updateSearchResults() {
        if(map==null) {searchResults=List.of();visibleViews=List.of();return;}
        String query=searchBox==null?"":searchBox.getValue();
        var types=AtlasSearch.Type.values();searchType=Math.clamp(searchType,0,types.length-1);
        searchResults=AtlasSearch.find(searchCatalog,query,types[searchType],searchOwner,searchProblems,searchTab,library,data.getDimensionId());
        if(searchTab==AtlasSearch.Tab.VIEWS) {
            String term=(viewEdit==ViewEdit.NONE?query:queryBeforeNaming).strip().toLowerCase(Locale.ROOT);
            var rows=new ArrayList<ViewRow>();
            for(var book:viewLibraries.values())for(var view:book.views())
                if(view.name().toLowerCase(Locale.ROOT).contains(term))rows.add(new ViewRow(book,view));
            rows.sort(java.util.Comparator.comparing(v->v.view.name(),String.CASE_INSENSITIVE_ORDER));
            visibleViews=List.copyOf(rows);
        }
        searchCursor=Math.clamp(searchCursor,0,Math.max(0,allSearchRows().size()-1));
        searchOffset=Math.clamp(searchOffset,0,Math.max(0,allSearchRows().size()-searchRows()));
    }
    private void attachLibrary(String server,UUID world) {
        var area=new AtlasWorkspace.Area(data.getDimensionId().toString(),world);
        Path directory=preferencePath().resolveSibling("ccec-atlas-library").resolve(AtlasWorkspace.digest(server+"\0"+minecraft.player.getUUID()));
        library=AtlasWorkspace.load(directory,area);
        viewLibraries.clear();viewLibraries.put(area,library);
        // Dimension IDs are scoped to an individual save. Only a shared live connection can establish a link.
        if(lastLibraryConnection.get()==minecraft.getConnection()&&directory.equals(lastLibraryDirectory)
                &&lastLibraryArea!=null&&!area.equals(lastLibraryArea)&&!lastLibraryArea.dimension().equals(area.dimension())) {
            var previous=AtlasWorkspace.load(directory,lastLibraryArea);
            boolean changed=library.link(previous.area);
            for(var sibling:previous.linkedAreas())if(!sibling.dimension().equals(area.dimension()))changed|=library.link(sibling);
            if(previous.link(area))writeLibrary(previous,false);
            if(changed)writeLibrary(library,false);
        }
        for(var linked:library.linkedAreas()) {
            if(!linked.dimension().equals(area.dimension())&&com.example.examplemod.client.CityPublicDirectory.dimensions.stream()
                    .anyMatch(dim->dim.toString().equals(linked.dimension())))viewLibraries.put(linked,AtlasWorkspace.load(directory,linked));
        }
        lastLibraryArea=area;lastLibraryConnection=new java.lang.ref.WeakReference<>(minecraft.getConnection());lastLibraryDirectory=directory;
    }
    private boolean writeLibrary(AtlasWorkspace book,boolean feedback) {
        try {book.save();if(feedback)com.example.examplemod.client.CommunityFx.sound("map_save");return true;}
        catch(java.io.IOException failure) {
            var restored=book.reload();viewLibraries.put(book.area,restored);
            if(library==book)library=restored;
            searchMessage="save_failed";updateSearchResults();return false;
        }
    }
    private AtlasWorkspace.Camera workingCamera() {
        int mask=0;for(var layer:CityMapPanel.Overlay.values())if(map.isOverlayEnabled(layer))mask|=1<<layer.preferenceBit();
        String query=searchTab==AtlasSearch.Tab.VIEWS?queryBeforeViews:searchBox.getValue();
        return new AtlasWorkspace.Camera(map.centerX(),map.centerZ(),map.zoom(),mask,searchType,searchOwner,searchProblems,query,
                map.territoryMode(),map.plotGrid(),map.coordinateGrid(),map.overviewVisible());
    }
    private void switchSearchTab(AtlasSearch.Tab tab) {
        searchJump=null;
        if(viewEdit!=ViewEdit.NONE)finishNaming();
        if(tab==AtlasSearch.Tab.VIEWS&&searchTab!=tab){queryBeforeViews=searchBox.getValue();searchBox.setValue("");}
        else if(searchTab==AtlasSearch.Tab.VIEWS&&tab!=searchTab)searchBox.setValue(queryBeforeViews);
        searchTab=tab;selectedView=null;searchOffset=searchCursor=0;searchMessage="";updateSearchResults();
    }
    private void closeSearchPanel() {
        if(viewEdit!=ViewEdit.NONE)finishNaming();
        searchJump=null;searchOpen=false;searchBox.setVisible(false);searchMessage="";searchFocus=-1;setFocused(null);
    }
    private void startSearchJump(ResourceLocation dimension,String key,AtlasWorkspace.Area area,UUID savedView) {
        if(!readOnly || !com.example.examplemod.client.CityPublicDirectory.dimensions.contains(dimension)) {searchMessage="dimension_unavailable";return;}
        savePreferences();discardOwnedPanel();library=null;viewLibraries.clear();preferenceWorld=null;preferences=null;
        requestedDimension=dimension;searchCatalog=List.of();searchResults=List.of();visibleViews=List.of();
        com.example.examplemod.client.CityPublicDirectory.open(dimension);
        directoryView=com.example.examplemod.client.CityPublicDirectory.viewId();
        searchJump=new SearchJump(directoryView,dimension,key,area,savedView);
        jumpDirectoryReady=false;searchJumpDeadline=System.nanoTime()+15_000_000_000L;
    }
    private void completeSearchJump() {
        var jump=searchJump;
        if(jump==null)return;
        if(!jump.view.equals(directoryView)||map==null||library==null){searchJump=null;return;}
        var arrival=AtlasSearch.arrival(jump.view,com.example.examplemod.client.CityPublicDirectory.viewId(),
                jump.dimension,data.getDimensionId(),jump.objectKey,com.example.examplemod.client.ProjectClientState.hasMapSnapshot(),
                System.nanoTime(),searchJumpDeadline);
        if(arrival==AtlasSearch.Arrival.WAIT){searchMessage="loading_target";return;}
        if(arrival==AtlasSearch.Arrival.EXPIRED){searchJump=null;searchMessage="unavailable";return;}
        searchJump=null;
        if(jump.area!=null) {
            if(!library.area.equals(jump.area)){searchMessage="unavailable";return;}
            for(var v:library.views())if(v.id().equals(jump.savedView)){applyWorkingView(v);return;}
        } else if(openSearchKey(jump.objectKey))return;
        searchMessage="unavailable";
    }
    private void applyWorkingView(AtlasWorkspace.View v) {
        var c=v.camera();searchTab=AtlasSearch.Tab.ALL;viewEdit=ViewEdit.NONE;searchType=c.type();searchOwner=c.owner();searchProblems=c.problems();
        searchBox.setValue(c.query());map.clearSelection();map.restoreView(c.x(),c.z(),c.zoom());
        map.setTerritoryPresentation(c.territoryMode(),c.plots());map.setPresentation(c.grid(),c.overview());
        for(var layer:CityMapPanel.Overlay.values())map.setOverlay(layer,(c.layers()&(1<<layer.preferenceBit()))!=0);
        if(sessionForcedTracks)map.setOverlay(CityMapPanel.Overlay.TRACKS,true);
        closeSearchPanel();
    }
    private boolean openSearchKey(String key) {
        if(map==null)return false;
        var dim=data.getDimensionId();
        for(var e:AtlasSearch.catalog(map,dim,data.getCityName()))if(e.key().equals(key)) {
            if(!dim.equals(e.dimension())){startSearchJump(e.dimension(),e.key(),null,null);return true;}
            if(library!=null&&library.visit(key))writeLibrary(library,false);
            closeSearchPanel();
            if(e.type()==AtlasSearch.Type.TRADE && e.pos()==null)LogisticsMapOverlay.openObject(java.util.UUID.fromString(e.id()),dim);
            else if(e.type()==AtlasSearch.Type.JOURNEY){map.centerOn(e.pos());openJourneyWorkspace(e.id());}
            else selectObject(e.selection());
            return true;
        }
        return false;
    }
    private WorldMapUiLayout.Rect searchFilterBounds(int index) {
        var panel = WorldMapUiLayout.searchPopover(this.width, this.modeLabelWidths, this.globalLabelWidths, 1, 228);
        int width = (panel.width() - 16) / 3;
        return editControl("searchFilter[" + index + "]", new WorldMapUiLayout.Rect(panel.x() + 6 + index * width, panel.y() + 28, width - 3, 19));
    }
    private void selectObject(CityMapPanel.Selection value) {
        if(map==null || value==null)return;
        if(mode!=Mode.CITIES)applyMode(Mode.CITIES);
        switch(value.kind()) {
            case CITY, CARTOGRAPHY -> map.setOverlay(CityMapPanel.Overlay.CITIES,true);
            case INDUSTRY, PROJECT -> map.setOverlay(CityMapPanel.Overlay.PROJECTS,true);
            default -> { }
        }
        pinMapObject(value);
        map.centerOn(value.pos());
    }
    private List<CityMapPanel.Selection> problemObjects() { return map.problemObjects(); }
    private int problemRows() { return Math.max(1, (WorldMapUiLayout.problemModal(this.width, this.height).height() - 112) / 25); }
    private WorldMapUiLayout.Rect mutationControl(WorldMapUiLayout.Rect panel, boolean confirm) {
        int half = (panel.width() - LARGE * 3) / 2;
        int x = panel.x() + (confirm ? LARGE : LARGE * 2 + half);
        return editControl(confirm ? "mutationConfirm" : "mutationCancel", new WorldMapUiLayout.Rect(
                x, panel.bottom() - 34, confirm ? half : panel.right() - LARGE - x, 22));
    }

    private void renderMutation(GuiGraphics graphics) {
        graphics.fill(0,0,width,height,WorldMapChrome.MODAL_SCRIM);
        var rect = WorldMapUiLayout.guideModal(this.width, this.height);
        WorldMapChrome.modal(graphics,rect.x(),rect.y(),rect.width(),rect.height());
        graphics.drawWordWrap(this.font, this.mutationTitle, rect.x() + LARGE, rect.y() + LARGE, rect.width() - LARGE * 2, TEXT);
        var confirm = mutationControl(rect, true);
        var cancel = mutationControl(rect, false);
        var confirmLabel=Component.translatable("gui.createcityexpansioncore.city.plot.claim_confirm");
        var cancelLabel=Component.translatable("gui.createcityexpansioncore.common.cancel");
        if (pendingMutation!=null && "confirm".equals(focusSurface())) {
            if (!readOnly) keyboard.add("mutation.confirm",confirmLabel,confirmLabel,confirm,()->completeMutation(true));
            keyboard.add("mutation.cancel",cancelLabel,cancelLabel,cancel,()->completeMutation(false));
        }
        paintControl(graphics,confirm,ControlGroup.MODAL,3,pendingMutation!=null && confirm.contains(mouseXNow,mouseYNow),
                !readOnly,false,true,false,false);
        paintControl(graphics,cancel,ControlGroup.MODAL,4,pendingMutation!=null && cancel.contains(mouseXNow,mouseYNow),
                true,false,false,false,false);
        graphics.drawString(this.font, WorldMapChrome.fit(font,confirmLabel,confirm.width()-NORMAL),
                confirm.x() + 6, confirm.y() + (confirm.height()-8)/2,
                readOnly?MUTED:WorldMapChrome.PRIMARY_TEXT, false);
        graphics.drawString(this.font, WorldMapChrome.fit(font,cancelLabel,cancel.width()-NORMAL),
                cancel.x() + 6, cancel.y() + (cancel.height()-8)/2, TEXT, false);
    }
    private void handleMutationClick(double x, double y, int button) {
        if (button != 0) return;
        var rect = WorldMapUiLayout.guideModal(this.width, this.height);
        if (mutationControl(rect, true).contains(x, y)) completeMutation(true);
        else if (mutationControl(rect, false).contains(x, y)) completeMutation(false);
    }

    private void completeMutation(boolean confirmed) {
        Runnable command = pendingMutation;
        pendingMutation = null;
        if (confirmed && !readOnly && command!=null) {
            CommunityFx.sound("click");
            command.run();
        }
    }

    private record Legend(String key,int color,AtlasGlyph glyph) {}
    private List<Legend> legendRows() {

if(this.mode==Mode.ENERGY)return List.of(new Legend("energy_soon",MapPalette.TEXT_FAINT,AtlasGlyph.ENERGY));
        return List.of(new Legend("own_city",MapPalette.OWN,AtlasGlyph.CITY),
            new Legend("other_city",MapPalette.NEUTRAL,AtlasGlyph.CITY),
            new Legend("alliance",MapPalette.OWN,null),
            new Legend("territory",MapPalette.OWN,null),
            new Legend("industry",MapPalette.OWN,AtlasGlyph.MINE),
            new Legend("partner",0xB365D8AF,null),
            new Legend("trusted",0xA06EAED0,null),
            new Legend("pending",0x906F7596,null));
    }
    private boolean legendEnabled(Legend entry){
        if(entry.key().startsWith("social_"))return true;
        var layer=switch(entry.key()){
            case "own_city","other_city","alliance"->CityMapPanel.Overlay.CITIES;
            case "territory"->CityMapPanel.Overlay.TERRITORIES;
            case "industry"->CityMapPanel.Overlay.PROJECTS;
            default->CityMapPanel.Overlay.RELATIONS;
        };return this.mode.available()&&this.map.isOverlayEnabled(layer);
    }
    private void renderModeLegend(GuiGraphics g) {
        var panel=workspace.legend();var rows=new java.util.ArrayList<>(legendRows());rows.add(new Legend("social_friend",0xFF78D7C1,null));
        int full=WorldMapUiLayout.legendHeader(panel)+rows.size()*WorldMapUiLayout.LEGEND_ROW;
        this.legendScroll=Math.min(this.legendScroll,Math.max(0,full-panel.height()));
        plate(g,panel.x(),panel.y(),panel.width(),panel.height());
        g.drawString(this.font,WorldMapChrome.fit(this.font,Component.translatable("gui.createcityexpansioncore.world_map.polish.legend"),panel.width()-40),panel.x()+12,panel.y()+10,ACCENT,false);
        if(full>panel.height())g.drawString(this.font,"↕",panel.right()-21,panel.y()+10,MUTED,false);
        var body=legendBody(panel);
        g.enableScissor(body.x(),body.y(),body.right(),body.bottom());
        try{for(int i=0;i<rows.size();i++) {
            Legend entry=rows.get(i);var r=WorldMapUiLayout.legendRow(panel,i,this.legendScroll);
            boolean enabled=legendEnabled(entry);
            var relation = switch (entry.key()) {
                case "partner" -> com.example.examplemod.network.CityRelationSnapshot.Status.PARTNER;
                case "trusted" -> com.example.examplemod.network.CityRelationSnapshot.Status.TRUSTED;
                case "pending" -> com.example.examplemod.network.CityRelationSnapshot.Status.TRUSTS_US;
                default -> com.example.examplemod.network.CityRelationSnapshot.Status.NONE;
            };
            if (relation != com.example.examplemod.network.CityRelationSnapshot.Status.NONE) {
                com.example.examplemod.client.map.CityRelationBadge.draw(g,r.x()+8,r.y()+8,relation,enabled);
            } else if(entry.glyph()!=null) {
                MapMarkerRenderer.drawGlyph(g,entry.glyph(),r.x(),r.y(),enabled?TEXT:MapPalette.TEXT_FAINT);
                int badge=entry.key().equals("attention")?MapPalette.ALERT:entry.key().equals("other_city")?MapPalette.NEUTRAL:MapPalette.OWN;
                g.fill(r.x()+12,r.y()+11,r.x()+17,r.y()+16,WorldMapChrome.INSET);
                g.fill(r.x()+13,r.y()+12,r.x()+16,r.y()+15,enabled?badge:MapPalette.TEXT_FAINT);
            }
            else if(entry.key().equals("alliance")){g.renderOutline(r.x()+1,r.y()+3,13,13,enabled?entry.color():MapPalette.TEXT_FAINT);g.renderOutline(r.x()+10,r.y()+1,5,5,enabled?entry.color():MapPalette.TEXT_FAINT);}

else if(entry.key().equals("territory")){g.fill(r.x(),r.y()+3,r.x()+16,r.y()+14,0x1ED3A363);g.renderOutline(r.x(),r.y()+3,16,11,entry.color());}

else g.fill(r.x(),r.y()+8,r.x()+17,r.y()+9,entry.color());
            Component label=Component.translatable("gui.createcityexpansioncore.world_map.legend."+entry.key());
            g.drawString(this.font,WorldMapChrome.fit(this.font,label,r.width()-24),r.x()+24,r.y()+5,enabled?TEXT:MapPalette.TEXT_FAINT,false);
            Component tooltip=enabled?label:label.copy().append(" · ").append(Component.translatable("gui.createcityexpansioncore.world_map.polish.layer_hidden"));
            if(entry.key().equals("alliance"))tooltip=AllianceScreen.tr("map_legend_hint");
            offerTooltip(tooltip,WorldMapUiLayout.visiblePart(r,body).contains(mouseXNow,mouseYNow)
                    && !overActivePopover(mouseXNow,mouseYNow) && !closingPopover(mouseXNow,mouseYNow));
        }}finally{g.disableScissor();}
    }

    private static WorldMapUiLayout.Rect legendBody(WorldMapUiLayout.Rect panel) {
        int top=panel.y()+WorldMapUiLayout.legendHeader(panel)-3;
        return new WorldMapUiLayout.Rect(panel.x()+1,top,panel.width()-2,panel.bottom()-1-top);
    }

    private WorldMapUiLayout.Rect legendRowHit(WorldMapUiLayout.Rect panel,int row) {
        return WorldMapUiLayout.visiblePart(WorldMapUiLayout.legendRow(panel,row,legendScroll),legendBody(panel));
    }

    private enum Mode {
        CITIES("minecraft:compass", "gui.createcityexpansioncore.world_map.mode.cities", CityMapPanel.Focus.WORLD, true),
        // The shipment seal, not an emerald: emerald reads as the vanilla villager currency, and a

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
