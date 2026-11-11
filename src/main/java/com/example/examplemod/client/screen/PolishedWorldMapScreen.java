package com.example.examplemod.client.screen;

import com.example.examplemod.client.CityWorldMapSession;
import com.example.examplemod.network.RequestCityWorldMapPayload;
import com.example.examplemod.client.WorldMapDataSource;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.client.map.MapMarkerRenderer;
import com.example.examplemod.client.map.MapPalette;
import com.example.examplemod.client.map.AtlasGlyph;
import com.example.examplemod.client.map.AtlasPreferences;
import com.example.examplemod.client.map.DispatchMotion;
import com.example.examplemod.client.map.DispatchPalette;
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
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/** Full-screen, map-first industrial atlas. */
public final class PolishedWorldMapScreen extends Screen {
    /* Dispatcher surfaces share one palette. City ownership and alerts keep their map semantics. */
    private static final int PANEL = DispatchPalette.SURFACE;
    private static final int PANEL_ALT = DispatchPalette.RAISED;
    private static final int FRAME = DispatchPalette.EDGE;
    private static final int ACCENT = DispatchPalette.ACCENT;
    private static final int TEXT = DispatchPalette.TEXT;
    private static final int MUTED = DispatchPalette.MUTED;
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
    private boolean journeyWorkspaceOpen;
    /** Transfer the live map to its marker workspace without tearing down the shared session. */
    public void openJourneyWorkspace(String marker) {
        if (this.map == null) return;
        journeyWorkspaceOpen = true;
        minecraft.setScreen(new com.example.examplemod.client.journey.JourneyAtlasScreen(this, this.map, marker));
    }
    public void tickJourneyWorkspace() {
        if (this.map != null) this.data.drainMapTiles().forEach(this.map::receive);
    }
    public void finishJourneyWorkspace(boolean returning) {
        journeyWorkspaceOpen = false;
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
    private static final String[] VIEW_KEYS = {"player", "fit", "follow", "pin", "measure", "overview", "grid", "retry", "player_profile", "reduce_motion"};
    private final DispatchMotion.Transition[] surfaceMotion = {
            new DispatchMotion.Transition(0), new DispatchMotion.Transition(0), new DispatchMotion.Transition(0),
            new DispatchMotion.Transition(0), new DispatchMotion.Transition(0)};
    private final double[] surfaceAlpha = new double[5];
    private final java.util.Map<String,DispatchMotion.Change> dispatchChanges = new java.util.HashMap<>();

    private long logisticsSnapshotAt;
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

    private boolean openingIdentityEditor;
    private net.minecraft.resources.ResourceLocation requestedDimension;
    public boolean acceptsDirectory(com.example.examplemod.network.CityDirectoryPayload payload) {
        if (requestedDimension != null) {
            if (!requestedDimension.equals(payload.dimension())) return false;
            if (payload.offset() == 0 && this.readOnly) CityWorldMapSession.INSTANCE.begin(payload.anchor(), payload.dimension());
            return true;
        }
        boolean accepted = this.data.getPylonPos() == null || payload.dimension().equals(this.data.getDimensionId());
        if (accepted && this.readOnly && payload.offset() == 0 && this.data.getPylonPos() != null && !payload.anchor().equals(this.data.getPylonPos())) {
            savePreferences(); if (map != null) map.releaseTerrainTextures(); map = null; preferences = null; sessionForcedTracks = false;
            CityWorldMapSession.INSTANCE.begin(payload.anchor(), payload.dimension());
        }
        return accepted;
    }
    public void directoryReady(com.example.examplemod.network.CityDirectoryPayload payload) {
        requestedDimension = null;
        ensurePanel();
        if (map != null) map.receivePublicCities(com.example.examplemod.client.CityPublicDirectory.profiles(this.data.getDimensionId()));
        updateSearchResults();
    }
    public void acceptTerritories(com.example.examplemod.network.CityMapPeersPayload payload) {
        if (!payload.dimensionId().equals(data.getDimensionId())) return;
        ensurePanel();
        if (map != null) map.receivePeers(payload.peers());
    }

    @Override
    protected void init() {
        String searchText=this.searchBox==null?"":this.searchBox.getValue();
        openingIdentityEditor = false;
        com.example.examplemod.client.ProjectClientState.begin();
        ensurePanel();
        loadPreferences();
        if (this.map != null) { this.map.setMotion(true,this.preferences!=null && this.preferences.reducedMotion); this.map.setGuiScale(1.0F); this.map.setDrawInternalControls(false); }
        applyMode(this.mode);
        requestSnapshots(true);
        this.tutorialTicks = 0; // Persistent contextual onboarding replaces the session-only intro banner.
        measureLabels();
        // The search field is positioned from the same popover the renderer uses, so the box and the
        // plate around it cannot drift apart at any width.
        WorldMapUiLayout.Rect search = WorldMapUiLayout.searchPopover(this.width, this.modeLabelWidths, this.globalLabelWidths, 0, 228);
        this.searchBox = new EditBox(this.font, search.x() + 11, search.y() + 6, 208, 14,
                Component.translatable("gui.createcityexpansioncore.world_map.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(Component.translatable("gui.createcityexpansioncore.world_map.search.hint"));
        this.searchBox.setVisible(this.searchOpen);
        /*
         * Ответчик вызывается позже, когда панель уже есть, но поле может быть и
         * раньше: без проверки первый же символ до загрузки ронял бы тик.
         */
        this.searchBox.setResponder(query -> { this.searchOffset = 0; updateSearchResults(); });
        this.searchBox.setValue(searchText);
        addWidget(this.searchBox); // Painted with its popover so opacity and content stay together.
        if(this.searchOpen)setFocused(this.searchBox);
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
    private boolean planningSession;

public void returnToCommunity(Screen screen){communityReturn=screen;}
    public void planningDimension(ResourceLocation dimension){requestedDimension=dimension;mode=Mode.CITIES;planningSession=true;}

    @Override
    public void onClose() {
        com.example.examplemod.client.logistics.LogisticsMapOverlay.deactivate();
        com.example.examplemod.client.screen.ProjectWorkspaceClient.clear();
        if (this.map != null) {
            this.map.setDrawInternalControls(true);
            this.map.setRailOverlay(null);
            this.map.setTrackCount(0);
            // The terrain textures are derived from the cached samples and the panel is shared with the
            // pylon screen, so they are released here instead of lingering on the GPU between openings.
            this.map.releaseTerrainTextures();
        }
        if (this.readOnly) {
            // Данные принадлежат сессии, а не этому экрану: держать их открытыми
            // после закрытия незачем, иначе к городу можно было бы вернуться без
            // нового запроса и увидеть устаревшую карту.
            CityWorldMapSession.INSTANCE.clear();
        }
        /*
         * Экран закрывается всегда, в том числе в режиме чтения.
         *
         * <p>Раньше здесь стоял `return` внутри ветки readOnly, и ESC не закрывал
         * карту вообще: setScreen не вызывался, экран оставался на месте, а данные
         * сессии уже были стёрты. Игрок без города, открывший карту по M, получал
         * экран, из которого нельзя выйти никак.
         *
         * <p>parent у карты по клавише M равен null, и setScreen(null) возвращает в
         * игру — это и есть нужное место. У карты из пилона parent не null, и
         * игрок возвращается к пилону как и раньше.
         */
        if (this.minecraft != null) this.minecraft.setScreen(communityReturn==null?this.parent:communityReturn);
    }

    private java.util.UUID framedAlliance;
    @Override
    public void tick() {

        com.example.examplemod.client.AllianceClient.ensure();
        if(this.map!=null&&com.example.examplemod.client.AllianceClient.focusedAlliance!=null&&!com.example.examplemod.client.AllianceClient.focusedAlliance.equals(framedAlliance)&&this.map.focusAlliance(com.example.examplemod.client.AllianceClient.focusedCities))framedAlliance=com.example.examplemod.client.AllianceClient.focusedAlliance;
        if (!this.readOnly && this.parent != null) this.parent.refreshMapClaimAccess();
        com.example.examplemod.client.ProjectClientState.tick();
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
        if (this.requestedDimension != null) return;
        if (com.example.examplemod.client.CityPublicDirectory.loading()) {requestVisibleTerritories();return;}
        if (!this.readOnly && this.data.getPylonPos() != null) PacketDistributor.sendToServer(
                new com.example.examplemod.network.RequestCityDirectoryPayload(this.data.getDimensionId(), com.example.examplemod.network.RequestCityDirectoryPayload.NEW, 0));
        if (this.readOnly) {
            if (this.data.getPylonPos() != null) PacketDistributor.sendToServer(new com.example.examplemod.network.RequestCityDirectoryPayload(
                    this.data.getDimensionId(), com.example.examplemod.network.RequestCityDirectoryPayload.NEW, 0));
            /*
             * В режиме чтения данные запрашиваются одним пакетом без координат:
             * сервер сам определяет город по членству игрока. Запросы пилона здесь
             * не работали бы — они требуют открытое меню, которого у этого экрана нет.
             */
            PacketDistributor.sendToServer(new RequestCityWorldMapPayload());
            requestVisibleTerritories();
            return;
        }

/*
         * Атлас использует ту же панель, что и вкладка «Карта», поэтому он
         * обязан переспрашивать и снимок территорий. Раньше он полагался
         * только на отправку при открытии пилона: если снимок не дошёл,
         * в атласе не было ни заливки, ни фронтира, и ПКМ было нечего
         * принимать. Периодический запрос делает данные устойчивыми и в
         * этом экране.
         */
        requestVisibleTerritories();
    }

    private void requestVisibleTerritories() {
        if(this.map==null||this.data.getPylonPos()==null)return;
        int range=(int)Math.ceil(Math.max(this.width,this.height)/Math.max(.0625,this.map.zoom())/2)+100;
        PacketDistributor.sendToServer(new RequestCityTerritoriesPayload(this.data.getPylonPos(),
                new net.minecraft.core.BlockPos((int)this.map.centerX(),0,(int)this.map.centerZ()),range));
    }

    private void syncMapData() {
        if (this.map == null) return;
        this.data.drainMapTiles().forEach(this.map::receive);

this.map.setProjects(this.data.getProjects());

if (this.data.getRelationsState() != null) this.map.setRelations(this.data.getRelationsState().cities());

if (this.searchOpen) updateSearchResults();
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
        graphics.fill(x, y, x + w, y + h, PANEL);
        graphics.renderOutline(x, y, w, h, FRAME);
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
        UiEditLayout.clearElements("map.");
        this.mouseXNow=mouseX;this.mouseYNow=mouseY;
        syncMapData();
        if (this.map == null) {
            renderLoading(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        this.hoveredTooltip = null;
        boolean modalOpen = this.guideOpen || this.problemCenterOpen || this.pendingMutation != null || this.tutorialTicks > 0;
        long now=System.nanoTime();
        boolean[] surfaces={this.layersOpen&&!modalOpen,this.searchOpen&&!modalOpen,this.problemCenterOpen,this.guideOpen,this.pendingMutation!=null};
        for(int i=0;i<surfaces.length;i++)surfaceAlpha[i]=surfaceMotion[i].value(surfaces[i]?1:0,now,this.map.reducedMotion(),140);
        boolean modal = modalOpen || closingModal();
        /*
         * 1-2: background and terrain/geometry, drawn by the panel itself.
         *
         * The map is told where the context column starts before it draws anything: the panel's own
         * chrome (legend, minimap, scale bar) is laid out against that edge, and only the panel knows
         * its viewport. Passing the boundary here rather than letting the panel guess keeps the two
         * halves of the layout honest about each other.
         */
        this.map.setChromeAvoidRight(WorldMapUiLayout.contextPanelLeft(this.width));
        graphics.fill(0, 0, this.width, this.height, MapPalette.PAPER);
        int mapLeft = WorldMapUiLayout.EDGE;
        int mapWidth = Math.max(1, WorldMapUiLayout.contextPanelLeft(this.width) - mapLeft - NORMAL);
        int mapTop = layoutViewButtons(mapWidth);
        if (this.map.pinToolActive() || this.map.measureToolActive()) mapTop += this.font.wordWrapHeight(Component.translatable("gui.createcityexpansioncore.world_map.tool." + (this.map.pinToolActive() ? "pin" : "measure")), mapWidth) + NORMAL;
        int mapBottom = WorldMapUiLayout.statusStrip(this.width, this.height).y() - MICRO;
        projectWorkspace.prepareMap(this.map,this.data.getDimensionId(),new WorldMapUiLayout.Rect(mapLeft,mapTop,mapWidth,Math.max(1,mapBottom-mapTop)),mouseX,mouseY);
        this.map.setProjectFocus(projectWorkspace.open() && this.mode==Mode.CITIES,projectWorkspace.territoryPicking() && this.mode==Mode.CITIES);
        this.map.setVisualOverlay(this.mode==Mode.CITIES && projectWorkspace.mapLayerVisible() && !modal ? projectWorkspace::renderMapLayer : null);
        this.map.setRailOverlay(this.mode==Mode.CITIES && this.map.isOverlayEnabled(CityMapPanel.Overlay.TRACKS)
                ? com.example.examplemod.client.logistics.LogisticsMapOverlay::renderRails : null);
        this.map.setTrackCount(this.mode==Mode.CITIES
                ? com.example.examplemod.client.logistics.LogisticsMapOverlay.railCount(this.data.getDimensionId()) : 0);
        this.map.render(graphics, mapLeft, mapTop, mapWidth, Math.max(1, mapBottom - mapTop), mouseX, mouseY);
        com.example.examplemod.client.screen.ProjectWorkspaceClient.render(graphics,this.map,this.data.getDimensionId(),new WorldMapUiLayout.Rect(mapLeft,mapTop,mapWidth,Math.max(1,mapBottom-mapTop)),mouseX,mouseY,this.mode==Mode.CITIES&&!modal&&!this.layersOpen&&!this.searchOpen&&!projectWorkspace.open());
        com.example.examplemod.client.logistics.LogisticsMapOverlay.render(graphics,this.map,this.data.getDimensionId(),mapLeft,mapTop,mapWidth,Math.max(1,mapBottom-mapTop),mouseX,mouseY,this.mode==Mode.CITIES&&!modal&&!this.layersOpen&&!this.searchOpen&&!projectWorkspace.open());
        // 3-4: markers are inside the panel render above; 5-6: chrome and the context column.
        renderTitle(graphics);
        renderModeToolbar(graphics, mouseX, mouseY);
        renderActionToolbar(graphics, mouseX, mouseY);
        renderViewButtons(graphics, mouseX, mouseY);
        if (!projectWorkspace.open() || this.mode != Mode.CITIES) { renderContextCard(graphics); renderModeLegend(graphics); }
        renderStatus(graphics, mouseX, mouseY);
        if(surfaceAlpha[0]>0)MapPresentationDraw.opacity(graphics,surfaceAlpha[0],()->renderLayers(graphics,mouseX,mouseY));
        if(surfaceAlpha[1]>0)MapPresentationDraw.opacity(graphics,surfaceAlpha[1],()->renderSearchPanel(graphics,mouseX,mouseY));
        projectWorkspace.render(graphics,this.font,this.map,this.data.getDimensionId(),this.width,this.height,
                WorldMapUiLayout.headerBottom(this.width,this.modeLabelWidths,this.globalLabelWidths)+NORMAL,
                new WorldMapUiLayout.Rect(mapLeft,mapTop,mapWidth,Math.max(1,mapBottom-mapTop)),mouseX,mouseY,partialTick,this.mode==Mode.CITIES && !modal && !this.layersOpen && !this.searchOpen);
        // 7-10: dim, tutorial target, modal surface and its widgets, then the tooltip last.
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.tutorialTicks > 0) renderTutorial(graphics);
        if(surfaceAlpha[2]>0)MapPresentationDraw.opacity(graphics,surfaceAlpha[2],()->renderProblemCenter(graphics));
        if(surfaceAlpha[3]>0)MapPresentationDraw.opacity(graphics,surfaceAlpha[3],()->renderGuide(graphics));
        if(surfaceAlpha[4]>0)MapPresentationDraw.opacity(graphics,surfaceAlpha[4],()->renderMutation(graphics));
        if (!modal && this.hoveredTooltip != null) renderTooltip(graphics, this.hoveredTooltip, mouseX, mouseY);
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
        WorldMapChrome.rounded(graphics,plate.x()+25,plate.y()+14,plate.width()-29,14,3,DispatchPalette.RAISED);
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
    private double tabLeft=Double.NaN, tabRight;
    private long tabFrame;
    private void renderModeToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        var activeTab=modeControl(this.mode.ordinal());
        long now=System.nanoTime(); double blend=this.map.reducedMotion()?1:com.example.examplemod.client.map.MapMotion.blend(tabFrame==0?0:(now-tabFrame)/1e9,22); tabFrame=now;
        if(Double.isNaN(tabLeft)){tabLeft=activeTab.x()+NORMAL;tabRight=activeTab.right()-NORMAL;}
        tabLeft+=(activeTab.x()+NORMAL-tabLeft)*blend; tabRight+=(activeTab.right()-NORMAL-tabRight)*blend;
        WorldMapUiLayout.Rect tabs = WorldMapUiLayout.modeTabs(this.width, this.modeLabelWidths);
        plate(graphics, tabs.x() - MICRO, tabs.y() - MICRO, tabs.width() + MICRO * 2, tabs.height() + MICRO * 2);
        Mode[] modes = Mode.values();
        for (int index = 0; index < modes.length; index++) {
            Mode value = modes[index];
            WorldMapUiLayout.Rect bounds = modeControl(index);
            boolean selected = value == this.mode;
            boolean enabled = value.available();
            boolean hover = enabled && bounds.contains(mouseX, mouseY);
            WorldMapChrome.dispatchControl(graphics,bounds,selected,hover,enabled,false);

            MapMarkerRenderer.drawGlyph(graphics, switch (value) { case CITIES -> AtlasGlyph.CITY;   case ENERGY -> AtlasGlyph.ENERGY; }, bounds.x() + MICRO, bounds.y() + (bounds.height()-16)/2, !enabled ? 0xFF667377 : selected ? ACCENT : TEXT);
            if (WorldMapUiLayout.modeTabsLabelled(this.width)) {
                int labelColour = !enabled ? 0xFF6E787C : selected ? ACCENT : TEXT;
                graphics.drawString(this.font, value.label(), bounds.x() + MICRO + 16 + MICRO, bounds.y() + (bounds.height()-8)/2,
                        labelColour, false);
            }
            Component hint = !enabled
                    ? Component.translatable("gui.createcityexpansioncore.world_map.mode.energy.soon")
                    : value.label();
            offerTooltip(hint, hover || (!enabled && bounds.contains(mouseX, mouseY)));
        }
        graphics.fill((int)Math.round(tabLeft),activeTab.bottom()-3,(int)Math.round(tabRight),activeTab.bottom()-1,ACCENT);
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
            WorldMapUiLayout.Rect bounds = globalControl(index);
            boolean active = (index == WorldMapUiLayout.GLOBAL_SEARCH && this.searchOpen)
                    || (index == WorldMapUiLayout.GLOBAL_LAYERS && this.layersOpen)
                    || (index == WorldMapUiLayout.GLOBAL_PROBLEMS && this.problemCenterOpen)
                    || (index == WorldMapUiLayout.GLOBAL_HELP && this.guideOpen);
            boolean hover = bounds.contains(mouseX, mouseY);
            boolean enabled = (index != WorldMapUiLayout.GLOBAL_BACK || canGoBack())
                    && (index != WorldMapUiLayout.GLOBAL_FORWARD || canGoForward());
            WorldMapChrome.dispatchControl(graphics,bounds,active,hover,enabled,false);
            if(index==2||index==5||index==7)graphics.vLine(bounds.x()-MICRO-1,bounds.y()+6,bounds.bottom()-6,FRAME);
            if (enabled && (active || hover)) {
                graphics.hLine(bounds.x() + 3, bounds.right() - 4, bounds.bottom() - 2, active ? ACCENT : 0xFF94704B);
            }
            boolean labelled = WorldMapUiLayout.globalToolbarLabelled(this.width,
                    this.modeLabelWidths, this.globalLabelWidths);
            int glyphX = labelled ? bounds.x() + MICRO : bounds.x() + (bounds.width() - 16) / 2;
            int glyphY = bounds.y() + (bounds.height() - 16) / 2;
            int color = !enabled ? 0xFF566164 : active || hover ? ACCENT : TEXT;
            var glyph = GLOBAL_GLYPHS[index];
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++)
                if (glyph.pixel(x, y)) graphics.fill(glyphX + x, glyphY + y, glyphX + x + 1, glyphY + y + 1, color);
            Component label = Component.translatable(GLOBAL_KEYS[index]);
            if (labelled) {
                graphics.drawString(this.font, label, bounds.x() + MICRO + 16 + MICRO, bounds.y() + NORMAL + 1,
                        enabled ? TEXT : MUTED, false);
            }
            if (index == WorldMapUiLayout.GLOBAL_PROBLEMS && problems > 0) {
                graphics.fill(bounds.right() - 7, bounds.y() + 2, bounds.right() - 1, bounds.y() + 8, 0xFF171D20);
                graphics.fill(bounds.right() - 6, bounds.y() + 3, bounds.right() - 2, bounds.y() + 7, 0xFFE8856B);
                label = label.copy().append(": " + problems);
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
                    CityMapPanel.Overlay.TRACKS, CityMapPanel.Overlay.RELATIONS, CityMapPanel.Overlay.PROJECTS);

case ENERGY -> List.of();
        };
    }

private int layersRowCount() {
        return activeOverlays().size() + 2 + (VIEW_KEYS.length - 5);
    }

    private void renderLayers(GuiGraphics graphics, int mouseX, int mouseY) {
        int contentWidth = 228;
        int rows = layersRowCount();
        WorldMapUiLayout.Rect panel = layersPopoverBounds();
        plate(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.layers"),
                panel.x() + WorldMapUiLayout.SECTION, panel.y() + WorldMapUiLayout.NORMAL - 2, ACCENT, false);
        graphics.enableScissor(panel.x(), panel.y() + WorldMapUiLayout.POPOVER_HEADER, panel.right(), panel.bottom());
        try {
        int row = 0;
        for (CityMapPanel.Overlay overlay : activeOverlays()) {
            WorldMapUiLayout.Rect bounds = layerRow(row++);
            boolean enabled = this.map.isOverlayEnabled(overlay);
            boolean hover = bounds.contains(mouseX, mouseY);
            if (hover) graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xFF303A3D);
            graphics.drawString(this.font, enabled ? "◆" : "◇", bounds.x() + WorldMapUiLayout.MICRO, bounds.y() + 5,
                    enabled ? ACCENT : MUTED, false);
            String count = Integer.toString(this.map.overlayCount(overlay));
            Component label = Component.translatable("gui.createcityexpansioncore.world_map.overlay." + overlay.name().toLowerCase(Locale.ROOT));
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label.getString(), bounds.width() - this.font.width(count) - 24), bounds.x() + 14, bounds.y() + 5, enabled ? TEXT : MUTED, false);
            offerTooltip(label, hover && panel.contains(mouseX, mouseY));
            graphics.drawString(this.font, count, bounds.right() - this.font.width(count) - MICRO, bounds.y() + 5, MUTED, false);
        }
        for(int option=0;option<2;option++) {
            var bounds=layerRow(row++);boolean hover=bounds.contains(mouseX,mouseY)&&panel.contains(mouseX,mouseY);
            if(hover)graphics.fill(bounds.x(),bounds.y(),bounds.right(),bounds.bottom(),PANEL_ALT);
            Component label=Component.translatable("gui.createcityexpansioncore.world_map."+(option==0?"territory_mode."+this.map.territoryMode():"plot_grid."+this.map.plotGrid()));
            graphics.drawString(this.font,WorldMapChrome.fit(this.font,label,bounds.width()-12),bounds.x()+6,bounds.y()+5,TEXT,false);
        }
        for (int index = 5; index < VIEW_KEYS.length; index++) {
            var bounds = layerRow(row++);
            boolean enabled = index == 5 && this.map.overviewVisible() || index == 6 && this.map.coordinateGrid() || index == 9 && this.map.reducedMotion();
            boolean hover = bounds.contains(mouseX, mouseY) && panel.contains(mouseX, mouseY);
            if (hover) graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), PANEL_ALT);
            Component label = Component.translatable("gui.createcityexpansioncore.world_map.view." + VIEW_KEYS[index]);
            graphics.drawString(this.font, index == 8 ? "▣" : index == 7 ? "↻" : enabled ? "◆" : "◇", bounds.x() + MICRO, bounds.y() + 5, ACCENT, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label.getString(), bounds.width() - 20), bounds.x() + 14, bounds.y() + 5, TEXT, false);
            offerTooltip(label, hover);
        }

        } finally { graphics.disableScissor(); }
    }

    private void renderSearchPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        List<CityMapPanel.Selection> visible = visibleSearchResults();
        var panel = WorldMapUiLayout.searchPopover(this.width, this.modeLabelWidths, this.globalLabelWidths, visible.size(), 228);
        plate(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        graphics.fill(panel.x() + 7, panel.y() + 5, panel.right() - 7, panel.y() + 25, 0xFF111719);
        graphics.renderOutline(panel.x() + 7, panel.y() + 5, panel.width() - 14, 20, ACCENT);
        for (int index = 0; index < 3; index++) {
            var rect = searchFilterBounds(index);
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), PANEL_ALT);
            Component label = index == 0 ? searchTypeLabel() : index == 1
                    ? Component.translatable("gui.createcityexpansioncore.world_map.filter.owner." + this.searchOwner)
                    : Component.translatable("gui.createcityexpansioncore.world_map.filter." + (this.searchProblems ? "problems" : "any_status"));
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label.getString(), rect.width() - 6), rect.x() + 3, rect.y() + 5, TEXT, false);
            offerTooltip(label, rect.contains(mouseX, mouseY));
        }
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.search.count", this.searchResults.size()), panel.x() + 8, panel.y() + 53, MUTED, false);
        if (visible.isEmpty()) graphics.drawWordWrap(this.font, Component.translatable("gui.createcityexpansioncore.world_map.search.empty"), panel.x() + 8, panel.y() + 73, panel.width() - 16, MUTED);
        for (int index = 0; index < visible.size(); index++) {
            var result = visible.get(index);
            var row = searchResultControl(index);
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(), row.contains(mouseX, mouseY) ? 0xFF303A3D : PANEL_ALT);
            MapMarkerRenderer.drawGlyph(graphics, MapMarkerRenderer.glyph(result.icon()), row.x() + 3, row.y() + 2, result.warning() ? MapPalette.ALERT : TEXT);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(result.title().getString(), row.width() - 30), row.x() + 24, row.y() + 3, TEXT, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(selectionType(result).getString(), row.width() - 30), row.x() + 24, row.y() + 13, MUTED, false);
            offerTooltip(result.title(), row.contains(mouseX, mouseY));
        }
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.search.page", visible.isEmpty() ? 0 : this.searchOffset + 1, this.searchOffset + visible.size(), this.searchResults.size()), panel.x() + 8, panel.bottom() - 14, MUTED, false);
        boolean visibleBefore=this.searchBox.isVisible();this.searchBox.setVisible(true);
        try{this.searchBox.render(graphics,mouseX,mouseY,0);}finally{this.searchBox.setVisible(visibleBefore);}
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
            this.cardActions.clear();
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

    private static Component shipmentMapText(String key,Object... args){return Component.translatable("gui.createcityexpansioncore.world_map.shipment."+key,args);}
    private static Component dispatchText(String key,Object... args){return Component.translatable("gui.createcityexpansioncore.world_map.dispatch."+key,args);}
    private Component logisticsFreshness(){return this.logisticsSnapshotAt==0?dispatchText("waiting"):
            dispatchText("snapshot_age",Math.max(0,(System.currentTimeMillis()-this.logisticsSnapshotAt)/1000));}
    private double changed(String key,Object value,long duration){return this.dispatchChanges.computeIfAbsent(key,ignored->new DispatchMotion.Change())
            .observe(value,System.nanoTime(),this.map.reducedMotion(),duration);}
    private boolean closingModal(){
        long now=System.nanoTime();return !this.problemCenterOpen&&surfaceMotion[2].sample(now)>0
                ||!this.guideOpen&&surfaceMotion[3].sample(now)>0||this.pendingMutation==null&&surfaceMotion[4].sample(now)>0;
    }
    private boolean closingPopover(double x,double y){
        long now=System.nanoTime();return !this.layersOpen&&surfaceMotion[0].sample(now)>0&&layersPopoverBounds().contains(x,y)
                ||!this.searchOpen&&surfaceMotion[1].sample(now)>0&&WorldMapUiLayout.searchPopover(this.width,this.modeLabelWidths,this.globalLabelWidths,visibleSearchResults().size(),228).contains(x,y);
    }
    /** "Сейчас важно": counts the player actually acts on, and a calm state when nothing is wrong. */
    private void renderOverview(GuiGraphics g) {
        var panel=WorldMapUiLayout.contextPanel(this.width,this.height,this.modeLabelWidths,this.globalLabelWidths,Integer.MAX_VALUE);
        this.cardX=panel.x();this.cardY=panel.y();this.cardWidth=panel.width();this.cardHeight=panel.height();
        String key="overview:"+this.mode;
        if(!key.equals(this.cardKey)){this.cardKey=key;this.cardScroll=0;this.dispatchChanges.clear();}
        int pad=WorldMapUiLayout.CARD_PADDING,x=panel.x()+pad,w=panel.width()-2*pad;
        plate(g,panel.x(),panel.y(),panel.width(),panel.height());
        g.enableScissor(panel.x()+1,panel.y()+1,panel.right()-1,panel.bottom()-1);
        try {
            g.drawString(this.font,WorldMapChrome.fit(this.font,this.mode.label(),w),x,panel.y()+pad,ACCENT,false);
            g.drawString(this.font,WorldMapChrome.fit(this.font,Component.translatable("gui.createcityexpansioncore.world_map.polish.summary"),w),x,panel.y()+pad+15,TEXT,false);
            int cursor=panel.y()+WorldMapUiLayout.CARD_HEADER-this.cardScroll;
            List<CityMapPanel.Overlay> metrics=switch(this.mode) {
                case CITIES->List.of(CityMapPanel.Overlay.CITIES,CityMapPanel.Overlay.TERRITORIES,CityMapPanel.Overlay.PROJECTS);

case ENERGY->List.of();
            };
            var body=WorldMapUiLayout.cardBody(panel);
            g.enableScissor(body.x(),body.y(),body.right(),body.bottom());
            for(var metric:metrics) {
                var r=new WorldMapUiLayout.Rect(x,cursor,w,WorldMapUiLayout.SUMMARY_ROW_HEIGHT);
                WorldMapChrome.rounded(g,r.x(),r.y(),r.width(),r.height(),4,WorldMapChrome.INSET);
                Component label=Component.translatable("gui.createcityexpansioncore.world_map.polish.metric."+metric.name().toLowerCase(Locale.ROOT));
                g.drawString(this.font,WorldMapChrome.fit(this.font,label,w-48),x+8,cursor+11,MUTED,false);
                String count=Integer.toString(this.map.overlayCount(metric));
                double flash=changed("metric:"+this.mode+":"+metric,count,180);
                g.drawString(this.font,count,x+w-8-this.font.width(count),cursor+11,DispatchMotion.tint(TEXT,ACCENT,flash),false);
                cursor+=WorldMapUiLayout.SUMMARY_ROW_PITCH;
            }
            Component status=Component.translatable(problemCount()>0?"gui.createcityexpansioncore.world_map.overview.problems":"gui.createcityexpansioncore.world_map.overview.calm",problemCount());
            g.drawWordWrap(this.font,status,x,cursor,w,problemCount()>0?MapPalette.ALERT:MapPalette.GOOD);
            cursor+=this.font.wordWrapHeight(status,w)+SECTION;
            Component hint=Component.translatable("gui.createcityexpansioncore.world_map.polish.empty."+this.mode.name().toLowerCase(Locale.ROOT));
            g.drawWordWrap(this.font,hint,x,cursor,w,MUTED);
            cursor+=this.font.wordWrapHeight(hint,w)+SECTION;
            this.cardMaxScroll=Math.max(0,cursor+this.cardScroll-panel.bottom());
            this.cardScroll=Math.min(this.cardScroll,this.cardMaxScroll);
        }finally{g.disableScissor();g.disableScissor();}
        renderCardScroll(g,panel);
    }
    private void renderCardScroll(GuiGraphics g,WorldMapUiLayout.Rect panel) {
        if(this.cardMaxScroll<=0)return;
        var track=WorldMapUiLayout.cardScrollTrack(panel);
        var thumb=WorldMapUiLayout.cardScrollThumb(panel,this.cardScroll,this.cardMaxScroll);
        g.fill(track.x(),track.y(),track.right(),track.bottom(),FRAME);
        g.fill(thumb.x(),thumb.y(),thumb.right(),thumb.bottom(),ACCENT);
    }

    /**
     * One structure for every kind of object: icon and title, kind/owner subtitle, status, the few
     * facts that matter, and then at most one primary action plus its siblings. Raw ids and
     * coordinates are deliberately not part of the normal card.
     */
    private void renderSelectionPanel(GuiGraphics g,CityMapPanel.Selection value,boolean pinned) {
        var profile=value.kind()==CityMapPanel.SelectionKind.CITY?com.example.examplemod.client.CityPublicDirectory.profile(value.id()):null;
        this.cardActions.clear();if(pinned){this.cardActions.addAll(contextActions(value));this.cardActions.addAll(navigationActions(value));}
        int pad=WorldMapUiLayout.CARD_PADDING;
        var panel=WorldMapUiLayout.contextPanel(this.width,this.height,this.modeLabelWidths,this.globalLabelWidths,Integer.MAX_VALUE);
        int x=panel.x()+pad,w=panel.width()-2*pad;
        String key=value.kind()+":"+(value.id()==null?value.pos().asLong():value.id());
        if(!key.equals(this.cardKey)){this.cardKey=key;this.cardScroll=0;this.dispatchChanges.clear();}
        this.cardX=panel.x();this.cardY=panel.y();this.cardWidth=panel.width();this.cardHeight=panel.height();
        plate(g,panel.x(),panel.y(),panel.width(),panel.height());
        g.enableScissor(panel.x()+1,panel.y()+1,panel.right()-1,panel.bottom()-1);
        if(profile!=null)com.example.examplemod.client.map.CityEmblemRenderer.draw(g,profile.identity(),profile.colour(),x,panel.y()+pad,1);
        else MapMarkerRenderer.renderCardIcon(g,x,panel.y()+pad,value.icon(),value.own(),value.warning());
        int titleX=x+32;
        g.drawString(this.font,WorldMapChrome.fit(this.font,value.title(),w-32),titleX,panel.y()+pad,TEXT,false);
        g.drawString(this.font,WorldMapChrome.fit(this.font,selectionType(value),w-32),titleX,panel.y()+pad+15,value.own()?ACCENT:MUTED,false);
        offerTooltip(value.title(),inside(this.mouseXNow,this.mouseYNow,titleX,panel.y()+pad,w-32,12));
        g.hLine(x,x+w,panel.y()+WorldMapUiLayout.CARD_HEADER-7,FRAME);
        var body=WorldMapUiLayout.cardBody(panel);
        g.enableScissor(body.x(),body.y(),body.right(),body.bottom());
        int cursor=panel.y()+WorldMapUiLayout.CARD_HEADER-this.cardScroll;
        try {
            if(pinned) {
                double statusFlash=changed("selection-status:"+key,value.warning(),180);
                WorldMapChrome.rounded(g,x,cursor,w,WorldMapUiLayout.STATUS_HEIGHT,4,
                        DispatchMotion.tint(value.warning()?0xFF452D2B:0xFF263A33,0xFF31575B,statusFlash));
                g.fill(x+7,cursor+8,x+11,cursor+12,value.warning()?MapPalette.ALERT:MapPalette.GOOD);
                g.drawString(this.font,WorldMapChrome.fit(this.font,Component.translatable("gui.createcityexpansioncore.world_map.context.status."+(value.warning()?"attention":"clear")),w-24),x+17,cursor+7,value.warning()?0xFFE4A297:0xFFB3D1BE,false);
                cursor+=WorldMapUiLayout.STATUS_HEIGHT+SECTION;
                Component owner=Component.translatable("gui.createcityexpansioncore.world_map.context.owner."+(value.own()?"own":"foreign"));
                g.drawString(this.font,WorldMapChrome.fit(this.font,owner,w),x,cursor,MUTED,false);cursor+=SECTION+4;
            }
            if(profile!=null) {
                var alliance=com.example.examplemod.client.AllianceClient.forCity(profile.id());
                if(alliance!=null){g.drawString(this.font,WorldMapChrome.fit(this.font,AllianceScreen.tr("badge",alliance.getString("Name")),w),x,cursor,0xFF000000|alliance.getInt("Color"),false);cursor+=SECTION+4;}
                Component residents=Component.translatable("gui.createcityexpansioncore.city.identity.residents",profile.residents());
                g.drawString(this.font,WorldMapChrome.fit(this.font,residents,w),x,cursor,MUTED,false);cursor+=SECTION+4;
                Component description=profile.identity().description().isBlank()?Component.translatable("gui.createcityexpansioncore.city.identity.no_description"):Component.literal(profile.identity().description());
                g.drawWordWrap(this.font,description,x,cursor,w,TEXT);cursor+=this.font.wordWrapHeight(description,w)+SECTION;
            }
            int count=pinned?value.details().size():Math.min(2,value.details().size());
            for(int i=0;i<count;i++) {
                Component fact=value.details().get(i);
                g.drawWordWrap(this.font,fact,x,cursor,w,TEXT);
                cursor+=this.font.wordWrapHeight(fact,w)+WorldMapUiLayout.FACT_GAP;
            }
            if(pinned) {
                Component coords=Component.translatable("gui.createcityexpansioncore.world_map.context.coordinates",value.pos().getX(),value.pos().getZ());
                g.drawString(this.font,WorldMapChrome.fit(this.font,coords,w),x,cursor,MUTED,false);cursor+=SECTION+4;
            }
            this.cardActionY=-1;this.cardActionCount=this.cardActions.size();
            if(!this.cardActions.isEmpty()) {
                cursor+=MICRO;this.cardActionY=cursor;
                for(int i=0;i<this.cardActions.size();i++) {
                    var action=editControl("card.action[" + i + "]", WorldMapUiLayout.cardAction(panel,this.cardActionY,i));
                    WorldMapChrome.dispatchControl(g,action,false,action.contains(this.mouseXNow,this.mouseYNow),true,i==0);
                    g.drawCenteredString(this.font,WorldMapChrome.fit(this.font,this.cardActions.get(i),action.width()-SECTION),action.x()+action.width()/2,action.y()+(action.height()-8)/2,TEXT);
                }
                cursor+=this.cardActions.size()*WorldMapUiLayout.ACTION_PITCH;
            }else if(!pinned){g.drawWordWrap(this.font,Component.translatable("gui.createcityexpansioncore.world_map.hover.details"),x,cursor,w,ACCENT);cursor+=24;}
            this.cardMaxScroll=Math.max(0,cursor+this.cardScroll+pad-panel.bottom());
            this.cardScroll=Math.min(this.cardScroll,this.cardMaxScroll);
        }finally{g.disableScissor();g.disableScissor();}
        renderCardScroll(g,panel);
    }

    /**
     * Action rows of the pinned context card. Trains and unregistered Create trains get a small
     * management list instead of the single generic action so registration, type changes and route
     * selection are reachable straight from the World Map; everything else keeps one action.
     * An empty list means "read-only card": the viewer has no rights on that selection.
     */
    private List<Component> contextActions(CityMapPanel.Selection value){return value==null?List.of():List.of(contextAction(value));}

    private void handleContextAction(CityMapPanel.Selection value,int index){handleContextAction(value);}

    private Component contextAction(CityMapPanel.Selection value){return Component.translatable(value.kind()==CityMapPanel.SelectionKind.CITY?"gui.createcityexpansioncore.world_map.action.open_relations":"gui.createcityexpansioncore.world_map.action.center");}

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

    private void renderProblemCenter(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0x66101517);
        var panel = WorldMapUiLayout.problemModal(this.width, this.height);
        plate(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(), ACCENT);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.problems"), panel.x() + SECTION, panel.y() + NORMAL, ACCENT, false);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.problem_count", problemCount()), panel.x() + SECTION, panel.y() + 26, TEXT, false);
        List<CityMapPanel.Selection> objects = problemObjects();
        this.problemOffset = Math.min(this.problemOffset, Math.max(0, objects.size() - problemRows()));
        if (objects.isEmpty()) graphics.drawWordWrap(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.ready"), panel.x() + SECTION, panel.y() + 50, panel.width() - SECTION * 2, MUTED);
        for (int index = 0; index < Math.min(problemRows(), objects.size() - this.problemOffset); index++) {
            var value = objects.get(index + this.problemOffset);
            var row = problemRow(panel, index);
            int y = row.y();
            graphics.fill(row.x(), y, row.right(), row.bottom(), PANEL_ALT);
            MapMarkerRenderer.drawGlyph(graphics, MapMarkerRenderer.glyph(value.icon()), row.x() + 3, y + 3, MapPalette.ALERT);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(value.title().getString(), row.width() - 30), row.x() + 24, y + 3, TEXT, false);
            String detail = value.details().isEmpty() ? selectionType(value).getString() : value.details().getFirst().getString();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(detail, row.width() - 30), row.x() + 24, y + 13, MUTED, false);
        }
        graphics.drawWordWrap(this.font, Component.translatable("gui.createcityexpansioncore.world_map.problems.located", objects.size(), problemCount()), panel.x() + SECTION, panel.bottom() - 52, panel.width() - SECTION * 2, MUTED);
        var close = modalFooter("problemClose", panel);
        graphics.drawCenteredString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.close"),
                close.x() + close.width()/2, close.y() + (close.height()-8)/2 + 2, ACCENT);
    }

    private void renderGuide(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0x66101517);
        WorldMapUiLayout.Rect panel = WorldMapUiLayout.guideModal(this.width, this.height);
        plate(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(), ACCENT);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.guide.title"),
                panel.x() + WorldMapUiLayout.LARGE, panel.y() + WorldMapUiLayout.SECTION, 0xFFFFD59A, false);
        Component text = Component.translatable("gui.createcityexpansioncore.world_map.guide.text");
        int top = panel.y() + 30, bottom = panel.bottom() - 34;
        this.guideMaxScroll = Math.max(0, this.font.wordWrapHeight(text, panel.width() - LARGE * 2) - (bottom - top));
        this.guideScroll = Math.min(this.guideScroll, this.guideMaxScroll);
        graphics.enableScissor(panel.x() + LARGE, top, panel.right() - LARGE, bottom);
        graphics.drawWordWrap(this.font, text, panel.x() + LARGE, top - this.guideScroll, panel.width() - LARGE * 2, TEXT);
        graphics.disableScissor();
        var close = modalFooter("guideClose", panel);
        graphics.drawCenteredString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.guide.dismiss"),
                close.x() + close.width()/2, close.y() + (close.height()-8)/2 + 2, MUTED);
    }

    private void renderStatus(GuiGraphics graphics, int mouseX, int mouseY) {
        var strip = WorldMapUiLayout.statusStrip(this.width, this.height);
        plate(graphics, strip.x(), strip.y(), strip.width(), strip.height());
        Component coordinates = this.map.coordinateStatus(mouseX, mouseY);
        int right = (this.mode==Mode.CITIES ? ProjectLayout.toggle(this.width,this.height).x() : strip.right()) - 6;
        int available = Math.max(0,right-strip.x());
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
            int alpha=this.map.reducedMotion()?160:90+(int)(120*(.5+.5*Math.sin(System.nanoTime()/240_000_000.0-i*1.1)));
            graphics.fill(statusX-15+i*4,strip.y()+8,statusX-13+i*4,strip.y()+10,(alpha<<24)|(ACCENT&0xFFFFFF));
        }
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
        int pad=WorldMapUiLayout.TOOLTIP_PADDING;
        int textWidth=Math.max(1,Math.min(this.font.width(value),Math.max(1,this.width-WorldMapUiLayout.EDGE*2-pad*2)));
        var bounds=WorldMapUiLayout.tooltip(this.width,this.height,mouseX,mouseY,textWidth,this.font.wordWrapHeight(value,textWidth));
        plate(graphics,bounds.x(),bounds.y(),bounds.width(),bounds.height());
        graphics.enableScissor(bounds.x(),bounds.y(),bounds.right(),bounds.bottom());
        try { graphics.drawWordWrap(this.font,value,bounds.x()+pad,bounds.y()+pad,textWidth,TEXT); }
        finally { graphics.disableScissor(); }
    }

    private static void plate(GuiGraphics graphics, int x, int y, int width, int height) {
        WorldMapChrome.dispatchPanel(graphics,x,y,width,height);
    }

    private static void statusChip(GuiGraphics graphics, int x, int y, int width) {
        graphics.fill(x, y, x + width, y + 18, 0xD5182023);
        graphics.fill(x, y, x + 2, y + 18, 0xA9596064);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // A disappearing surface still owns its input until its final frame.
        if(closingModal()||this.map!=null&&closingPopover(mouseX,mouseY))return true;
        if(this.guideOpen||this.problemCenterOpen)return handleModalClick(mouseX,mouseY);
        if(button!=0&&this.map!=null&&(this.layersOpen&&layersPopoverBounds().contains(mouseX,mouseY)
                ||this.searchOpen&&WorldMapUiLayout.searchPopover(this.width,this.modeLabelWidths,this.globalLabelWidths,visibleSearchResults().size(),228).contains(mouseX,mouseY)))return true;
        if(!this.guideOpen&&!this.problemCenterOpen&&this.pendingMutation==null&&!this.layersOpen&&!this.searchOpen&&this.mode==Mode.CITIES&&com.example.examplemod.client.logistics.LogisticsMapOverlay.click(mouseX,mouseY,button))return true;
        if(!this.guideOpen&&!this.problemCenterOpen&&this.pendingMutation==null&&!this.layersOpen&&!this.searchOpen&&this.mode==Mode.CITIES&&com.example.examplemod.client.screen.ProjectWorkspaceClient.click(mouseX,mouseY,button))return true;
        if (this.map != null && this.mode == Mode.CITIES && !this.guideOpen && !this.problemCenterOpen && this.pendingMutation == null && this.tutorialTicks == 0 && !this.layersOpen && !this.searchOpen && !this.map.hasPendingClaim()
                && projectWorkspace.click(mouseX,mouseY,button,true)) return true;
        var titleBounds = WorldMapUiLayout.title(this.width);
        if (button == 0 && this.readOnly && titleBounds.contains(mouseX, mouseY) && mouseY >= titleBounds.y() + 14 && this.pendingMutation == null) {
            var dimensions = com.example.examplemod.client.CityPublicDirectory.dimensions;
            if (dimensions.size() > 1) {
                requestedDimension = dimensions.get((dimensions.indexOf(this.data.getDimensionId()) + 1) % dimensions.size());
                savePreferences(); if (map != null) map.releaseTerrainTextures(); map = null; preferenceWorld = null; preferences = null;
                CityWorldMapSession.INSTANCE.awaitAnchor();
                PacketDistributor.sendToServer(new com.example.examplemod.network.RequestCityDirectoryPayload(requestedDimension, com.example.examplemod.network.RequestCityDirectoryPayload.NEW, 0));
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
        if (this.pendingMutation != null) { handleMutationClick(mouseX, mouseY, button); return true; }
        if (this.map == null) {
            // Пока панели нет, карта нечего обрабатывать: ни выбрать, ни сдвинуть.
            return button == 0 ? super.mouseClicked(mouseX, mouseY, button) : false;
        }
        if (button != 0) {
            if (this.problemCenterOpen || this.guideOpen) return handleModalClick(mouseX, mouseY);
            if (this.readOnly) {
                /*
                 * Правая кнопка в режиме чтения ничего не делает.
                 *
                 * <p>Панель обрабатывает ПКМ как запрос занять участок, а занятие
                 * участка — это изменение города. Отдавать клик панели значило бы
                 * предложить действие, которого на этой карте быть не должно, и
                 * открыть подтверждение, которого тут нечем подтвердить.
                 */
                return false;
            }
            return this.map.mouseClicked(mouseX, mouseY, button)
                    || super.mouseClicked(mouseX, mouseY, button);
        }
        for (int index = 0; index < this.viewButtons.size(); index++) {
            if (!this.problemCenterOpen && !this.guideOpen && editControl("view[" + index + "]", this.viewButtons.get(index)).contains(mouseX, mouseY)) {
                handleViewAction(index<2?index:index+1); dismissTutorial(); return true;
            }
        }
        // A modal owns the click: the map must not pan, select or dismiss anything underneath it.
        if (this.problemCenterOpen || this.guideOpen) return handleModalClick(mouseX, mouseY);
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
        if (this.searchOpen && this.searchBox.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.layersOpen && handleLayerClick(mouseX, mouseY)) return true;
        if (this.searchOpen && handleSearchClick(mouseX, mouseY)) return true;
        if(WorldMapUiLayout.legend(this.width,this.height).contains(mouseX,mouseY)){
            var panel=WorldMapUiLayout.legend(this.width,this.height);var rows=new java.util.ArrayList<>(legendRows());rows.add(new Legend("social_friend",0xFF78D7C1,null));rows.add(new Legend("social_project",0xFFE4B65B,null));for(int i=0;i<rows.size();i++)if(rows.get(i).key().equals("alliance")&&WorldMapUiLayout.legendRow(panel,i,this.legendScroll).contains(mouseX,mouseY))com.example.examplemod.client.AllianceClient.clearFocus();return true;
        }
        CityMapPanel.Selection selected = this.map.getSelection();
        if (selected != null && this.cardActionY >= 0 && inside(mouseX, mouseY, this.cardX, this.cardY, this.cardWidth, this.cardHeight)) {
            for (int index = 0; index < this.cardActionCount; index++) {
                var action=editControl("card.action[" + index + "]", WorldMapUiLayout.cardAction(new WorldMapUiLayout.Rect(this.cardX,this.cardY,this.cardWidth,this.cardHeight),this.cardActionY,index));
                if (WorldMapUiLayout.cardBody(new WorldMapUiLayout.Rect(this.cardX,this.cardY,this.cardWidth,this.cardHeight)).contains(mouseX,mouseY)&&action.contains(mouseX,mouseY)) {
                    handleContextAction(selected, index);
                    return true;
                }
            }
        }
        if (this.cardWidth > 0
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

private boolean handleModalClick(double mouseX, double mouseY) {
        if (this.problemCenterOpen) {
            var objects = problemObjects();
            var bounds = WorldMapUiLayout.problemModal(this.width, this.height);
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
        switch (index) {
            case WorldMapUiLayout.GLOBAL_BACK -> { navigateBack(); dismissTutorial(); }
            case WorldMapUiLayout.GLOBAL_FORWARD -> { navigateForward(); dismissTutorial(); }
            case WorldMapUiLayout.GLOBAL_CENTER_CITY -> {  this.map.centerOn(this.data.getPylonPos()); dismissTutorial(); }
            case WorldMapUiLayout.GLOBAL_SEARCH -> {
                updateSearchResults();
                this.searchOpen = !this.searchOpen;
                this.layersOpen = false;
                this.searchBox.setVisible(this.searchOpen);
                if (this.searchOpen) updateSearchResults();
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

private void handleContextAction(CityMapPanel.Selection value){
        if(value.kind()==CityMapPanel.SelectionKind.CITY&&!this.readOnly&&this.minecraft!=null)CommunityScreen.openRelations(this);
        else this.map.centerOn(value.pos());
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
                this.map.setOverlay(overlay, !this.map.isOverlayEnabled(overlay));
                return true;
            }
        }
        for(int option=0;option<2;option++)if(layerRow(row++).contains(mouseX,mouseY)) {
            this.map.setTerritoryPresentation(option==0?(this.map.territoryMode()+1)%3:this.map.territoryMode(),option==1?!this.map.plotGrid():this.map.plotGrid());return true;
        }
        for (int index = 5; index < VIEW_KEYS.length; index++) if (layerRow(row++).contains(mouseX, mouseY)) { handleViewAction(index); return true; }
        return layersPopoverBounds().contains(mouseX, mouseY);
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

    private boolean handleSearchClick(double mouseX, double mouseY) {
        var visible = visibleSearchResults();
        for (int index = 0; index < 3; index++) if (searchFilterBounds(index).contains(mouseX, mouseY)) {
            if (index == 0) this.searchType = (this.searchType + 1) % (CityMapPanel.SelectionKind.values().length + 1);
            else if (index == 1) this.searchOwner = (this.searchOwner + 1) % 3;
            else this.searchProblems = !this.searchProblems;
            this.searchOffset = 0; updateSearchResults(); return true;
        }
        for (int index = 0; index < visible.size(); index++) if (searchResultControl(index).contains(mouseX, mouseY)) {
            selectObject(visible.get(index)); this.searchOpen = false; this.searchBox.setVisible(false); setFocused(null); return true;
        }
        return WorldMapUiLayout.searchPopover(this.width, this.modeLabelWidths, this.globalLabelWidths, visible.size(), 228).contains(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if(closingModal()||this.map!=null&&closingPopover(mouseX,mouseY))return true;
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
        // Клик мог начаться до панели и закончиться после: отпускание обязано
        // быть безопасным независимо от того, что нажатие было проигнорировано.
        if (this.map == null) return super.mouseReleased(mouseX, mouseY, button);
        return this.map.mouseReleased(mouseX, mouseY, button)
                || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if(closingModal()||this.map!=null&&closingPopover(mouseX,mouseY))return true;
        if (this.mode==Mode.CITIES && !this.guideOpen && !this.problemCenterOpen && this.pendingMutation==null && !this.layersOpen && !this.searchOpen && projectWorkspace.scroll(mouseX,mouseY,scrollY)) return true;
        if (this.guideOpen) { this.guideScroll = Math.max(0, Math.min(this.guideMaxScroll, this.guideScroll + (scrollY > 0 ? -16 : 16))); return true; }
        if (this.pendingMutation != null || this.map != null && this.map.hasPendingClaim()) return true;
        if (this.problemCenterOpen) { this.problemOffset = Math.max(0, Math.min(Math.max(0, problemObjects().size() - problemRows()), this.problemOffset + (scrollY > 0 ? -1 : 1))); return true; }
        if (this.searchOpen && WorldMapUiLayout.searchPopover(this.width, this.modeLabelWidths, this.globalLabelWidths, visibleSearchResults().size(), 228).contains(mouseX, mouseY)) {
            this.searchOffset = Math.max(0, Math.min(Math.max(0, this.searchResults.size() - searchRows()), this.searchOffset + (scrollY > 0 ? -1 : 1))); return true;
        }
        if (this.layersOpen && layersPopoverBounds().contains(mouseX, mouseY)) {
            int fullHeight = WorldMapUiLayout.POPOVER_HEADER + layersRowCount() * WorldMapUiLayout.POPOVER_ROW;
            this.layerScroll = Math.max(0, Math.min(fullHeight - layersPopoverBounds().height(), this.layerScroll + (scrollY > 0 ? -20 : 20))); return true;
        }
        if(this.map!=null&&WorldMapUiLayout.legend(this.width,this.height).contains(mouseX,mouseY)) {
            int full=WorldMapUiLayout.legendHeader(WorldMapUiLayout.legend(this.width,this.height))+legendRows().size()*WorldMapUiLayout.LEGEND_ROW;
            this.legendScroll=Math.max(0,Math.min(Math.max(0,full-WorldMapUiLayout.legend(this.width,this.height).height()),this.legendScroll+(scrollY>0?-18:18)));return true;
        }
        if (inside(mouseX, mouseY, this.cardX, this.cardY, this.cardWidth, this.cardHeight)) {
            this.cardScroll = Math.max(0, Math.min(this.cardMaxScroll, this.cardScroll + (scrollY > 0 ? -16 : 16))); return true;
        }
        dismissTutorial();
        if (this.map == null) return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
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
        if(closingModal())return true;
        if(this.map!=null&&!this.guideOpen&&!this.problemCenterOpen&&this.pendingMutation==null&&!this.map.hasPendingClaim()
                &&keyCode==GLFW.GLFW_KEY_F&&(modifiers&GLFW.GLFW_MOD_CONTROL)!=0) {
            if(!this.searchOpen)handleGlobalAction(WorldMapUiLayout.GLOBAL_SEARCH);
            setFocused(this.searchBox);return true;
        }
        if (this.mode==Mode.CITIES && !this.guideOpen && !this.problemCenterOpen && this.pendingMutation==null && !this.layersOpen && !this.searchOpen && projectWorkspace.key(keyCode,scanCode,modifiers)) return true;
        if (this.pendingMutation != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) this.pendingMutation = null;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && (this.guideOpen || this.problemCenterOpen)) { this.guideOpen = false; this.problemCenterOpen = false; return true; }
        if (this.map != null && !this.searchOpen && !this.map.hasPendingClaim() && keyCode == GLFW.GLFW_KEY_ESCAPE && this.map.cancelPersonalTools()) return true;
        if (this.searchOpen && keyCode == GLFW.GLFW_KEY_ENTER && !this.searchResults.isEmpty()) {
            selectObject(this.searchResults.get(this.searchOffset)); this.searchOpen = false; this.searchBox.setVisible(false); return true;
        }
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
            if (this.map.getSelection() != null) { this.map.clearSelection(); return true; }
            if (this.searchOpen) {
                this.searchOpen = false; this.searchBox.setVisible(false); setFocused(null); return true;
            }
            if (this.layersOpen) { this.layersOpen = false; return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean charTyped(char character,int modifiers) {
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

    private Path preferencePath() { return this.minecraft.gameDirectory.toPath().resolve("config/ccec-atlas.properties"); }
    private void loadPreferences() {
        if (this.preferences != null || this.map == null || this.minecraft == null) return;
        String server = this.minecraft.getCurrentServer() != null ? this.minecraft.getCurrentServer().ip
                : this.minecraft.getSingleplayerServer() != null ? this.minecraft.getSingleplayerServer().getWorldData().getLevelName() : "local";
        this.preferenceWorld = server + ":" + (this.minecraft.player == null ? "" : this.minecraft.player.getUUID()) + ":" + this.data.getDimensionId();
        AtlasPreferences loaded = AtlasPreferences.loadWorld(preferencePath(), this.preferenceWorld);
        this.preferences = null;
        applyMode(Mode.CITIES);
        this.preferences = loaded;
        if (this.preferences.positioned) this.map.restoreView(this.preferences.centerX, this.preferences.centerZ, this.preferences.zoom);
        this.map.setPresentation(this.preferences.grid, this.preferences.minimap);
        this.map.setTerritoryPresentation(this.preferences.territoryMode,this.preferences.plotGrid);
        this.map.setMotion(true,this.preferences.reducedMotion);
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
    }
    private void rememberOverlays() {
        int mask = 0; for (var layer : CityMapPanel.Overlay.values()) if (this.map.isOverlayEnabled(layer)) mask |= 1 << layer.preferenceBit();
        if (this.sessionForcedTracks) mask &= ~(1 << CityMapPanel.Overlay.TRACKS.preferenceBit());
        this.preferences.overlays[this.mode==Mode.CITIES?0:3] = mask;
    }
    private void savePreferences() {
        if (this.preferences == null || this.map == null) return;
        rememberOverlays(); this.preferences.mode = this.mode.ordinal(); this.preferences.centerX = this.map.centerX();
        this.preferences.centerZ = this.map.centerZ(); this.preferences.zoom = this.map.zoom(); this.preferences.positioned = true;
        this.preferences.grid = this.map.coordinateGrid(); this.preferences.minimap = this.map.overviewVisible();
        this.preferences.territoryMode=this.map.territoryMode();this.preferences.plotGrid=this.map.plotGrid();
        this.preferences.pins.clear(); this.preferences.pins.addAll(this.map.personalPins());
        try { this.preferences.save(preferencePath(), this.preferenceWorld); }
        catch (java.io.IOException error) { org.slf4j.LoggerFactory.getLogger(PolishedWorldMapScreen.class).warn("Cannot save local atlas view", error); }
    }
    @Override public void removed() {
        if (journeyWorkspaceOpen) { super.removed(); return; }
        // Every way out of the atlas (ESC, a child screen, the "open object" action) must stop the
        // ambient logistics feed; otherwise a finished center session leaks its markers onto the
        // next normal world map opened in this client.
        com.example.examplemod.client.logistics.LogisticsMapOverlay.deactivate();
        ProjectWorkspaceClient.clear();
        savePreferences();  this.pendingMutation = null;
        if (this.map != null) { this.map.setMotion(false,false); this.map.setProjectFocus(false,false); this.map.setVisualOverlay(null); this.map.cancelPersonalTools(); this.map.setDrawInternalControls(true); this.map.releaseTerrainTextures(); }
        if (this.readOnly && !openingIdentityEditor) { CityWorldMapSession.INSTANCE.clear(); com.example.examplemod.client.CityPublicDirectory.reset(); }
        com.example.examplemod.client.ProjectClientState.clear();
        super.removed();
    }
    private int layoutViewButtons(int mapWidth) {
        this.viewButtons.clear();
        int start=WorldMapUiLayout.popoverY(this.width,this.modeLabelWidths,this.globalLabelWidths)+NORMAL;
        int[] labels=new int[4];
        for(int i=0;i<labels.length;i++)labels[i]=this.font.width(Component.translatable("gui.createcityexpansioncore.world_map.view."+VIEW_KEYS[i<2?i:i+1]));
        this.viewButtons.addAll(WorldMapUiLayout.viewButtons(mapWidth,start,labels));
        return this.viewButtons.getLast().bottom()+NORMAL;
    }
    private void renderViewButtons(GuiGraphics g,int mx,int my) {
        for(int viewIndex=0;viewIndex<this.viewButtons.size();viewIndex++) {
            int i=viewIndex<2?viewIndex:viewIndex+1;
            var r=editControl("view[" + viewIndex + "]", this.viewButtons.get(viewIndex));
            boolean active=i==3&&this.map.pinToolActive()||i==4&&this.map.measureToolActive();
            var selected=this.map.getSelection();
            boolean enabled=i!=2;
            WorldMapChrome.dispatchControl(g,r,active,r.contains(mx,my),enabled,false);
            Component label=Component.translatable("gui.createcityexpansioncore.world_map.view."+VIEW_KEYS[i]);
            if(WorldMapUiLayout.viewButtonsCompact(Math.max(1,WorldMapUiLayout.contextPanelLeft(this.width)-WorldMapUiLayout.EDGE-NORMAL)))
                WorldMapChrome.viewIcon(g,i,r.x()+(r.width()-16)/2,r.y()+(r.height()-16)/2,enabled?TEXT:MapPalette.TEXT_FAINT);
            else g.drawString(this.font,WorldMapChrome.fit(this.font,label,r.width()-SECTION),r.x()+6,r.y()+(r.height()-8)/2,enabled?TEXT:MapPalette.TEXT_FAINT,false);
            offerTooltip(label,r.contains(mx,my));
        }
        if(this.map.pinToolActive()||this.map.measureToolActive()) {
            int width=Math.max(1,WorldMapUiLayout.contextPanelLeft(this.width)-WorldMapUiLayout.EDGE-NORMAL);
            g.drawWordWrap(this.font,Component.translatable("gui.createcityexpansioncore.world_map.tool."+(this.map.pinToolActive()?"pin":"measure")),WorldMapUiLayout.EDGE,this.viewButtons.getLast().bottom()+NORMAL,width,TEXT);
        }
    }

    private void handleViewAction(int index) {
        switch (index) {
            case 0 -> {  this.map.centerOnPlayer(); }
            case 1 -> {  this.map.fitCity(); }

            case 3 -> this.map.togglePinTool();
            case 4 -> this.map.toggleMeasureTool();
            case 5 -> this.map.setPresentation(this.map.coordinateGrid(), !this.map.overviewVisible());
            case 6 -> this.map.setPresentation(!this.map.coordinateGrid(), this.map.overviewVisible());
            case 7 -> { this.map.retryTerrain(); requestSnapshots(true); }
            case 8 -> {
                // Keep the read-only map session while a temporary profile screen is open.
                openingIdentityEditor = true; this.layersOpen = false;
                com.example.examplemod.client.CityPublicDirectory.cancelLoading();
                PlayerProfileScreen.open(this, this.minecraft.player.getUUID());
            }
            case 9 -> { this.preferences.reducedMotion=!this.preferences.reducedMotion; this.map.setMotion(true,this.preferences.reducedMotion); }
            default -> { }
        }
    }

    private List<Component> navigationActions(CityMapPanel.Selection value) {
        List<Component> actions = new ArrayList<>();
        actions.add(Component.translatable("gui.createcityexpansioncore.world_map.action.center"));

        if (value.kind() == CityMapPanel.SelectionKind.WAYPOINT) actions.add(Component.translatable("gui.createcityexpansioncore.world_map.pin.remove"));
        var profile = value.kind() == CityMapPanel.SelectionKind.CITY ? com.example.examplemod.client.CityPublicDirectory.profile(value.id()) : null;
        if (profile != null && profile.editable()) actions.add(Component.translatable("gui.createcityexpansioncore.city.identity.edit"));
        if(value.kind()==CityMapPanel.SelectionKind.CITY)actions.add(AllianceScreen.tr("city_entry"));
        return actions;
    }
    private void handleNavigationAction(CityMapPanel.Selection value,int index){
        if(value.kind()==CityMapPanel.SelectionKind.CITY&&index==navigationActions(value).size()-1){AllianceScreen.open(this,value.id());return;}
        if(index==0){this.map.centerOn(value.pos());return;}
        if(index==1&&value.kind()==CityMapPanel.SelectionKind.WAYPOINT)this.map.removeSelectedPin();
        else if(index==1&&value.kind()==CityMapPanel.SelectionKind.CITY){
            var profile=com.example.examplemod.client.CityPublicDirectory.profile(value.id());
            if(profile!=null&&profile.editable()){openingIdentityEditor=true;com.example.examplemod.client.CityPublicDirectory.cancelLoading();this.minecraft.setScreen(new CityIdentityEditorScreen(this,profile));}
        }
    }
    private int searchRows() { return Math.max(1, Math.min(8, (this.height - popoverY() - 96) / 25)); }
    private List<CityMapPanel.Selection> visibleSearchResults() {
        int count = this.searchResults.size(); this.searchOffset = Math.max(0, Math.min(this.searchOffset, Math.max(0, count - searchRows())));
        return this.searchResults.subList(this.searchOffset, Math.min(count, this.searchOffset + searchRows()));
    }
    private Component searchTypeLabel() {
        return this.searchType == 0 ? Component.translatable("gui.createcityexpansioncore.world_map.filter.all")
                : Component.translatable("gui.createcityexpansioncore.world_map.selection." + CityMapPanel.SelectionKind.values()[this.searchType - 1].name().toLowerCase(Locale.ROOT));
    }
    private void updateSearchResults() {
        if (this.map == null) { this.searchResults = List.of(); return; }
        String query = this.searchBox == null ? "" : this.searchBox.getValue();
        this.searchResults = this.map.search(query).stream().filter(value -> this.searchType == 0 || value.kind() == CityMapPanel.SelectionKind.values()[this.searchType - 1])
                .filter(value -> this.searchOwner == 0 || value.own() == (this.searchOwner == 1))
                .filter(value -> !this.searchProblems || value.warning()).toList();
        this.searchOffset = Math.max(0, Math.min(this.searchOffset, Math.max(0, this.searchResults.size() - searchRows())));
    }
    private WorldMapUiLayout.Rect searchFilterBounds(int index) {
        var panel = WorldMapUiLayout.searchPopover(this.width, this.modeLabelWidths, this.globalLabelWidths, 1, 228);
        int width = (panel.width() - 16) / 3;
        return editControl("searchFilter[" + index + "]", new WorldMapUiLayout.Rect(panel.x() + 6 + index * width, panel.y() + 28, width - 3, 19));
    }
    private void selectObject(CityMapPanel.Selection value) {

        var before = this.map.getSelection();
        if (before != null && !before.equals(value)) this.backHistory.push(before);
        this.forwardHistory.clear();
        switch (value.kind()) {

default -> applyMode(Mode.CITIES);
        }
        for (var layer : activeOverlays()) this.map.setOverlay(layer, true);
        this.map.selectAndCenter(value);
    }
    private List<CityMapPanel.Selection> problemObjects() { return this.map.search("").stream().filter(CityMapPanel.Selection::warning).toList(); }
    private int problemRows() { return Math.max(1, (WorldMapUiLayout.problemModal(this.width, this.height).height() - 112) / 25); }
    private WorldMapUiLayout.Rect mutationControl(WorldMapUiLayout.Rect panel, boolean confirm) {
        int half = (panel.width() - LARGE * 3) / 2;
        int x = panel.x() + (confirm ? LARGE : LARGE * 2 + half);
        return editControl(confirm ? "mutationConfirm" : "mutationCancel", new WorldMapUiLayout.Rect(
                x, panel.bottom() - 34, confirm ? half : panel.right() - LARGE - x, 22));
    }

    private void renderMutation(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0x88101517);
        var rect = WorldMapUiLayout.guideModal(this.width, this.height);
        plate(graphics, rect.x(), rect.y(), rect.width(), rect.height());
        graphics.drawWordWrap(this.font, this.mutationTitle, rect.x() + LARGE, rect.y() + LARGE, rect.width() - LARGE * 2, TEXT);
        var confirm = mutationControl(rect, true);
        var cancel = mutationControl(rect, false);
        graphics.fill(confirm.x(), confirm.y(), confirm.right(), confirm.bottom(), 0xFF5A4522);
        graphics.fill(cancel.x(), cancel.y(), cancel.right(), cancel.bottom(), PANEL_ALT);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.plot.claim_confirm"),
                confirm.x() + 6, confirm.y() + (confirm.height()-8)/2, TEXT, false);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.common.cancel"),
                cancel.x() + 6, cancel.y() + (cancel.height()-8)/2, TEXT, false);
    }
    private void handleMutationClick(double x, double y, int button) {
        if (button != 0) return;
        var rect = WorldMapUiLayout.guideModal(this.width, this.height);
        if (mutationControl(rect, true).contains(x, y)) {
            Runnable command = this.pendingMutation; this.pendingMutation = null;
            if (!this.readOnly && command != null) command.run();
        } else if (mutationControl(rect, false).contains(x, y)) this.pendingMutation = null;
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
        var panel=WorldMapUiLayout.legend(this.width,this.height);var rows=new java.util.ArrayList<>(legendRows());rows.add(new Legend("social_friend",0xFF78D7C1,null));rows.add(new Legend("social_project",0xFFE4B65B,null));
        int full=WorldMapUiLayout.legendHeader(panel)+rows.size()*WorldMapUiLayout.LEGEND_ROW;
        this.legendScroll=Math.min(this.legendScroll,Math.max(0,full-panel.height()));
        plate(g,panel.x(),panel.y(),panel.width(),panel.height());
        g.drawString(this.font,WorldMapChrome.fit(this.font,Component.translatable("gui.createcityexpansioncore.world_map.polish.legend"),panel.width()-40),panel.x()+12,panel.y()+10,ACCENT,false);
        if(full>panel.height())g.drawString(this.font,"↕",panel.right()-21,panel.y()+10,MUTED,false);
        g.enableScissor(panel.x()+1,panel.y()+WorldMapUiLayout.legendHeader(panel)-3,panel.right()-1,panel.bottom()-1);
        try{for(int i=0;i<rows.size();i++) {
            Legend entry=rows.get(i);var r=WorldMapUiLayout.legendRow(panel,i,this.legendScroll);
            boolean enabled=legendEnabled(entry);
            if(entry.glyph()!=null) {
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
            offerTooltip(tooltip,r.contains(this.mouseXNow,this.mouseYNow)&&panel.contains(this.mouseXNow,this.mouseYNow));
        }}finally{g.disableScissor();}
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
