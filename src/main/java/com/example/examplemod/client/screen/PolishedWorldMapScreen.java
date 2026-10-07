package com.example.examplemod.client.screen;

import com.example.examplemod.client.CityWorldMapSession;
import com.example.examplemod.network.RequestCityWorldMapPayload;
import com.example.examplemod.client.WorldMapDataSource;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.client.map.MapMarkerRenderer;
import com.example.examplemod.client.map.MapPalette;
import com.example.examplemod.client.map.AtlasGlyph;
import com.example.examplemod.client.map.AtlasPreferences;
import java.nio.file.Path;
import java.util.UUID;
import com.example.examplemod.client.status.CcecProblemCenter;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.EconomyCommandPayload;
import com.example.examplemod.network.RailwayCommandPayload;
import com.example.examplemod.network.RailwayMapPayload;
import com.example.examplemod.network.RequestCityTerritoriesPayload;
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
    /*
     * Корпус атласа и его колонки берут тон из общей палитры карты, а не задают свой.
     *
     * Экран и панель карты рисуются в одном кадре и должны выглядеть одним прибором. Пока у них
     * были независимые числа, рамка карты и подложка колонки отличались оттенком при одинаковой
     * роли, и это читалось как «колонка приклеена к карте», а не «прибор собран из частей».
     */
    private static final int PANEL = 0xEC1B1E20;
    private static final int PANEL_ALT = 0xF023282A;
    private static final int FRAME = MapPalette.INK_SOFT;
    private static final int ACCENT = MapPalette.OWN;
    private static final int TEXT = MapPalette.TEXT;
    private static final int MUTED = MapPalette.TEXT_MUTED;
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
    /** Карта открыта по клавише: только просмотр, ничего не изменить. */
    private final boolean readOnly;
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
    private int searchOffset, searchType, searchOwner;
    private boolean searchProblems;
    private int problemOffset;
    private int cardScroll, cardMaxScroll;
    private int guideScroll, guideMaxScroll;
    private int layerScroll;
    private String cardKey = "";
    private UUID followingTrain;
    private AtlasPreferences preferences;
    private String preferenceWorld;
    private Runnable pendingMutation;
    private Component mutationTitle;
    private final List<WorldMapUiLayout.Rect> viewButtons = new ArrayList<>();
    private static final String[] VIEW_KEYS = {"player", "fit", "follow", "pin", "measure", "overview", "grid", "retry"};

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
            savePreferences(); if (map != null) map.releaseTerrainTextures(); map = null; preferences = null;
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
        openingIdentityEditor = false;
        ensurePanel();
        loadPreferences();
        if (this.map != null) this.map.setGuiScale(1.0F);
        applyMode(this.mode);
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
        /*
         * Ответчик вызывается позже, когда панель уже есть, но поле может быть и
         * раньше: без проверки первый же символ до загрузки ронял бы тик.
         */
        this.searchBox.setResponder(query -> { this.searchOffset = 0; updateSearchResults(); });
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
        if (this.map != null) {
            this.map.setDrawInternalControls(true);
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
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    @Override
    public void tick() {
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
    }

    private void requestSnapshots(boolean includeMap) {
        if (this.requestedDimension != null || com.example.examplemod.client.CityPublicDirectory.loading()) return;
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
            return;
        }
        PacketDistributor.sendToServer(RailwayCommandPayload.simple(
                this.data.getPylonPos(), RailwayCommandPayload.Action.REQUEST_STATE, null, null));
        PacketDistributor.sendToServer(EconomyCommandPayload.simple(
                this.data.getPylonPos(), EconomyCommandPayload.Action.REQUEST_STATE, null));
        /*
         * Атлас использует ту же панель, что и вкладка «Карта», поэтому он
         * обязан переспрашивать и снимок территорий. Раньше он полагался
         * только на отправку при открытии пилона: если снимок не дошёл,
         * в атласе не было ни заливки, ни фронтира, и ПКМ было нечего
         * принимать. Периодический запрос делает данные устойчивыми и в
         * этом экране.
         */
        PacketDistributor.sendToServer(new RequestCityTerritoriesPayload(this.data.getPylonPos()));
        if (includeMap) PacketDistributor.sendToServer(RailwayCommandPayload.simple(
                this.data.getPylonPos(), RailwayCommandPayload.Action.REQUEST_MAP, null, null));
    }

    private void syncMapData() {
        if (this.map == null) return;
        this.data.drainMapTiles().forEach(this.map::receive);
        RailwayMapPayload railwayMap = this.data.drainRailwayMap();
        if (railwayMap != null) this.map.receive(railwayMap);
        this.map.setProjects(this.data.getProjects());
        this.map.setRailwayData(this.data.getRailwayStations(), this.data.getRailwayTrains());
        this.map.setUnregisteredTrains(this.data.getRailwayTrainCandidates());
        if (this.data.getEconomyState() != null) {
            this.map.setEconomyInfrastructure(this.data.getEconomyState().infrastructure());
            this.map.setFreightData(this.data.getEconomyState().logistics());
        }
        if (this.data.getRelationsState() != null) this.map.setRelations(this.data.getRelationsState().cities());
        reconcileTrainSelection();
        updateFollowingTrain();
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
    private void reconcileTrainSelection() {
        CityMapPanel.Selection selected = this.map.getSelection();
        if (selected == null || selected.kind() != CityMapPanel.SelectionKind.TRAIN_UNREGISTERED) return;
        var now = this.data.getRailwayTrains().stream()
                .filter(train -> selected.id().equals(train.createTrainId())).findFirst().orElse(null);
        if (now != null) {
            this.map.selectTrain(now);
        } else if (this.data.getRailwayTrainCandidates().stream()
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
        if (this.map == null) {
            renderLoading(graphics);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }
        this.hoveredTooltip = null;
        boolean modal = this.guideOpen || this.problemCenterOpen || this.pendingMutation != null || this.tutorialTicks > 0;
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
        this.map.render(graphics, mapLeft, mapTop, mapWidth, Math.max(1, mapBottom - mapTop), mouseX, mouseY);
        // 3-4: markers are inside the panel render above; 5-6: chrome and the context column.
        renderTitle(graphics);
        renderModeToolbar(graphics, mouseX, mouseY);
        renderActionToolbar(graphics, mouseX, mouseY);
        renderViewButtons(graphics, mouseX, mouseY);
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
        if (this.pendingMutation != null) renderMutation(graphics);
        if (!modal && this.hoveredTooltip != null) renderTooltip(graphics, this.hoveredTooltip, mouseX, mouseY);
    }

    private void renderTitle(GuiGraphics graphics) {
        WorldMapUiLayout.Rect plate = WorldMapUiLayout.title(this.width);
        plate(graphics, plate.x(), plate.y(), plate.width(), plate.height());
        MapMarkerRenderer.drawGlyph(graphics, AtlasGlyph.CITY, plate.x() + MICRO, plate.y() + MICRO, ACCENT);
        if (plate.width() <= 32) return;
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.world_map.identity"),
                plate.x() + 28, plate.y() + 4, 0xFFFFD59A, false);
        graphics.drawString(this.font, Component.literal(this.data.getDimensionId().getPath()),
                plate.x() + 28, plate.y() + 15, MUTED, false);
        if (this.readOnly && com.example.examplemod.client.CityPublicDirectory.dimensions.size() > 1)
            graphics.drawString(this.font, "↔", plate.right() - 12, plate.y() + 15, ACCENT, false);
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
            MapMarkerRenderer.drawGlyph(graphics, switch (value) { case CITIES -> AtlasGlyph.CITY; case RAILWAY -> AtlasGlyph.TRAIN; case LOGISTICS -> AtlasGlyph.FREIGHT; case ENERGY -> AtlasGlyph.ENERGY; }, bounds.x() + MICRO, bounds.y() + MICRO, enabled ? ACCENT : MUTED);
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
            boolean enabled = (index != WorldMapUiLayout.GLOBAL_BACK || canGoBack())
                    && (index != WorldMapUiLayout.GLOBAL_FORWARD || canGoForward());
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
        return activeOverlays().size() + 3 + (this.mode == Mode.RAILWAY ? legendClasses().size() + 1 : 0);
    }

    private void renderLayers(GuiGraphics graphics, int mouseX, int mouseY) {
        int contentWidth = 228;
        int rows = layersRowCount();
        WorldMapUiLayout.Rect panel = layersPopoverBounds();
        plate(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.layers"),
                panel.x() + WorldMapUiLayout.SECTION, panel.y() + WorldMapUiLayout.NORMAL - 2, 0xFFFFD59A, false);
        graphics.enableScissor(panel.x(), panel.y() + WorldMapUiLayout.POPOVER_HEADER, panel.right(), panel.bottom());
        try {
        int row = 0;
        for (CityMapPanel.Overlay overlay : activeOverlays()) {
            WorldMapUiLayout.Rect bounds = layerRow(row++);
            boolean enabled = this.map.isOverlayEnabled(overlay);
            boolean hover = bounds.contains(mouseX, mouseY);
            if (hover) graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), 0xFF303A3D);
            graphics.drawString(this.font, enabled ? "◆" : "◇", bounds.x() + WorldMapUiLayout.MICRO, bounds.y() + 5,
                    enabled ? 0xFFFFD59A : 0xFF788589, false);
            String count = Integer.toString(this.map.overlayCount(overlay));
            Component label = Component.translatable("gui.createcityexpansioncore.world_map.overlay." + overlay.name().toLowerCase(Locale.ROOT));
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label.getString(), bounds.width() - this.font.width(count) - 24), bounds.x() + 14, bounds.y() + 5, enabled ? TEXT : MUTED, false);
            offerTooltip(label, hover && panel.contains(mouseX, mouseY));
            graphics.drawString(this.font, count, bounds.right() - this.font.width(count) - MICRO, bounds.y() + 5, MUTED, false);
        }
        for (int index = 5; index < VIEW_KEYS.length; index++) {
            var bounds = layerRow(row++);
            boolean enabled = index == 5 && this.map.overviewVisible() || index == 6 && this.map.coordinateGrid();
            boolean hover = bounds.contains(mouseX, mouseY) && panel.contains(mouseX, mouseY);
            if (hover) graphics.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), PANEL_ALT);
            Component label = Component.translatable("gui.createcityexpansioncore.world_map.view." + VIEW_KEYS[index]);
            graphics.drawString(this.font, index == 7 ? "↻" : enabled ? "◆" : "◇", bounds.x() + MICRO, bounds.y() + 5, ACCENT, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label.getString(), bounds.width() - 20), bounds.x() + 14, bounds.y() + 5, TEXT, false);
            offerTooltip(label, hover);
        }
        if (this.mode != Mode.RAILWAY) return;
        // In railway mode the legend is the whole point: a line must be identifiable without hovering it.
        WorldMapUiLayout.Rect header = layerRow(row++);
        graphics.hLine(header.x(), header.right(), header.y() + WorldMapUiLayout.POPOVER_ROW / 2, 0xFF6F542C);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.legend.title"),
                header.x() + WorldMapUiLayout.MICRO, header.y() + 5, 0xFFFFD59A, false);
        for (CityMapPanel.RailClass kind : legendClasses()) {
            WorldMapUiLayout.Rect bounds = layerRow(row++);
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
            var row = WorldMapUiLayout.searchRow(this.width, this.modeLabelWidths, this.globalLabelWidths, index, 228);
            graphics.fill(row.x(), row.y(), row.right(), row.bottom(), row.contains(mouseX, mouseY) ? 0xFF303A3D : PANEL_ALT);
            MapMarkerRenderer.drawGlyph(graphics, MapMarkerRenderer.glyph(result.icon()), row.x() + 3, row.y() + 2, result.warning() ? MapPalette.ALERT : TEXT);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(result.title().getString(), row.width() - 30), row.x() + 24, row.y() + 3, TEXT, false);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(selectionType(result).getString(), row.width() - 30), row.x() + 24, row.y() + 13, MUTED, false);
            offerTooltip(result.title(), row.contains(mouseX, mouseY));
        }
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.search.page", visible.isEmpty() ? 0 : this.searchOffset + 1, this.searchOffset + visible.size(), this.searchResults.size()), panel.x() + 8, panel.bottom() - 14, MUTED, false);
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
        int trains = this.data.getRailwayTrains().size();
        int freightPoints = this.map.freightPointCount();
        int discoveries = this.map.freightCandidateCount();
        int problems = problemCount();
        List<Component> facts = new ArrayList<>();
        facts.add(Component.translatable("gui.createcityexpansioncore.world_map.overview.trains", trains));
        facts.add(Component.translatable("gui.createcityexpansioncore.world_map.overview.freight_points", freightPoints));
        if (discoveries > 0) facts.add(Component.translatable("gui.createcityexpansioncore.world_map.overview.discoveries", discoveries));
        if (freightPoints == 0) facts.add(Component.translatable("gui.createcityexpansioncore.world_map.overview.start_freight"));
        if (trains == 0) facts.add(Component.translatable("gui.createcityexpansioncore.world_map.overview.start_train"));
        facts.add(problems > 0
                ? Component.translatable("gui.createcityexpansioncore.world_map.overview.problems", problems)
                : Component.translatable("gui.createcityexpansioncore.world_map.overview.calm"));
        if (this.readOnly && this.data.getCityName().isBlank()) {
            facts.clear();
            facts.add(com.example.examplemod.client.CityPublicDirectory.loading()
                    ? Component.translatable("gui.createcityexpansioncore.city.identity.public_loading")
                    : Component.translatable("gui.createcityexpansioncore.city.identity.public_count", com.example.examplemod.client.CityPublicDirectory.profiles(this.data.getDimensionId()).size()));
            facts.add(Component.translatable("gui.createcityexpansioncore.city.identity.public_hint"));
        }
        int factsHeight = 0;
        for (Component fact : facts) factsHeight += this.font.wordWrapHeight(fact, width - LARGE) + MICRO;
        int hintHeight = this.font.wordWrapHeight(Component.translatable("gui.createcityexpansioncore.world_map.overview.hint"), width - LARGE * 2);
        int height = 30 + hintHeight + factsHeight + 8;
        WorldMapUiLayout.Rect panel = WorldMapUiLayout.contextPanel(this.width, this.height, this.modeLabelWidths, this.globalLabelWidths, height);
        this.cardX = panel.x(); this.cardY = panel.y();
        this.cardWidth = panel.width(); this.cardHeight = panel.height();
        if (!"overview".equals(this.cardKey)) { this.cardKey = "overview"; this.cardScroll = 0; }
        this.cardMaxScroll = Math.max(0, height - panel.height());
        this.cardScroll = Math.min(this.cardScroll, this.cardMaxScroll);
        plate(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(), FRAME);
        graphics.enableScissor(panel.x() + 1, panel.y() + 1, panel.right() - 1, panel.bottom() - 1);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.overview.title"),
                panel.x() + LARGE, panel.y() + NORMAL - this.cardScroll, 0xFFFFD59A, false);
        graphics.drawWordWrap(this.font, Component.translatable("gui.createcityexpansioncore.world_map.overview.hint"), panel.x() + LARGE, panel.y() + NORMAL + 12 - this.cardScroll, width - LARGE * 2, MUTED);
        int cursor = panel.y() + 30 + hintHeight - this.cardScroll;
        for (Component fact : facts) {
            graphics.drawWordWrap(this.font, fact, panel.x() + LARGE, cursor, width - LARGE, TEXT);
            cursor += this.font.wordWrapHeight(fact, width - LARGE) + MICRO;
        }
        graphics.disableScissor();
    }

    /**
     * One structure for every kind of object: icon and title, kind/owner subtitle, status, the few
     * facts that matter, and then at most one primary action plus its siblings. Raw ids and
     * coordinates are deliberately not part of the normal card.
     */
    private void renderSelectionPanel(GuiGraphics graphics, CityMapPanel.Selection value, boolean pinned) {
        var profile = value.kind() == CityMapPanel.SelectionKind.CITY ? com.example.examplemod.client.CityPublicDirectory.profile(value.id()) : null;
        this.cardActions.clear();
        if (pinned) { this.cardActions.addAll(contextActions(value)); this.cardActions.addAll(navigationActions(value)); }
        int actionCount = this.cardActions.size();
        int width = WorldMapUiLayout.contextPanelWidth(this.width);
        int detailCount = pinned ? Math.min(6, value.details().size()) : Math.min(2, value.details().size());
        int detailsHeight = 0;
        for (int index = 0; index < detailCount; index++) {
            detailsHeight += this.font.wordWrapHeight(value.details().get(index), width - LARGE) + MICRO;
        }
        int height = 46 + detailsHeight + (pinned ? 28 : 0)
                + (actionCount > 0 ? MICRO * 2 + actionCount * 20 : 0);
        if (profile != null) height += 28 + this.font.wordWrapHeight(Component.literal(profile.identity().description().isBlank() ? Component.translatable("gui.createcityexpansioncore.city.identity.no_description").getString() : profile.identity().description()), width - LARGE) + MICRO;
        WorldMapUiLayout.Rect panel = WorldMapUiLayout.contextPanel(this.width, this.height,
                this.modeLabelWidths, this.globalLabelWidths, height);
        int x = panel.x(), y = panel.y();
        String key = value.kind() + ":" + (value.id() == null ? value.pos().asLong() : value.id());
        if (!key.equals(this.cardKey)) { this.cardScroll = 0; this.cardKey = key; }
        this.cardMaxScroll = Math.max(0, height - panel.height());
        this.cardScroll = Math.min(this.cardScroll, this.cardMaxScroll);
        this.cardX = x; this.cardY = y; this.cardWidth = panel.width(); this.cardHeight = panel.height();
        plate(graphics, x, y, panel.width(), panel.height());
        graphics.renderOutline(x, y, panel.width(), panel.height(), pinned ? ACCENT : FRAME);
        graphics.enableScissor(x + 1, y + 1, panel.right() - 1, panel.bottom() - 1);
        try {
        y -= this.cardScroll;
        if (profile != null) com.example.examplemod.client.map.CityEmblemRenderer.draw(graphics, profile.identity(), profile.colour(), x + LARGE, y + LARGE - 2, 1);
        else MapMarkerRenderer.renderCardIcon(graphics, x + LARGE, y + LARGE - 2, value.icon(), value.own(), value.warning());
        graphics.drawString(this.font, this.font.plainSubstrByWidth(value.title().getString(), width - LARGE * 2 - 32), x + 32 + LARGE, y + NORMAL, TEXT, false);
        graphics.drawString(this.font, selectionType(value), x + 32 + LARGE, y + NORMAL + 12,
                value.own() ? 0xFFFFD59A : MUTED, false);
        if (pinned) {
            // Coordinates are a last-resort detail for a pinned selection only, never for hover.
            graphics.drawString(this.font,
                    Component.translatable("gui.createcityexpansioncore.world_map.context.coordinates",
                            value.pos().getX(), value.pos().getZ()),
                    x + 32 + LARGE, y + NORMAL + 23, MUTED, false);
        }
        int cursor = y + (pinned ? 46 : 38);
        if (pinned) {
            // Status band, derived from server-authoritative fields the selection already carries:
            // a warning is the only thing that can override ownership.
            boolean attention = value.warning();
            int colour = attention ? MapPalette.ALERT : MUTED;
            graphics.fill(x + LARGE, cursor, x + panel.width() - LARGE, cursor + 12,
                    attention ? 0xFF4A2422 : value.own() ? 0xFF23382F : 0xFF2A3033);
            graphics.drawString(this.font,
                    Component.translatable("gui.createcityexpansioncore.world_map.context.status." + (attention ? "attention" : "clear")),
                    x + LARGE + MICRO, cursor + 3, colour, false);
            cursor += 14;
            graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.context.owner." + (value.own() ? "own" : "foreign")), x + LARGE, cursor + 2, MUTED, false);
            cursor += 14;
        }
        if (profile != null) {
            graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.identity.residents", profile.residents()), x + LARGE, cursor, MUTED, false);
            cursor += 14;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(profile.dimension().getPath(), width - LARGE), x + LARGE, cursor, MUTED, false);
            cursor += 14;
            Component description = profile.identity().description().isBlank() ? Component.translatable("gui.createcityexpansioncore.city.identity.no_description") : Component.literal(profile.identity().description());
            graphics.drawWordWrap(this.font, description, x + LARGE, cursor, width - LARGE, TEXT);
            cursor += this.font.wordWrapHeight(description, width - LARGE) + MICRO;
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
            graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(this.cardActions.get(index).getString(), panel.width() - LARGE * 2 - 8),
                    x + panel.width() / 2, actionY + 5, TEXT);
        }
        } finally { graphics.disableScissor(); }
    }

    /**
     * Action rows of the pinned context card. Trains and unregistered Create trains get a small
     * management list instead of the single generic action so registration, type changes and route
     * selection are reachable straight from the World Map; everything else keeps one action.
     * An empty list means "read-only card": the viewer has no rights on that selection.
     */
    private List<Component> contextActions(CityMapPanel.Selection value) {
        if (value == null) return List.of();
        /*
         * В режиме чтения на карте нет ни одной кнопки.
         *
         * <p>Проверка стоит здесь, до всех частных случаев, потому что частные
         * случаи считают права по своим данным, а карта по клавише вообще не
         * должна предлагать действие — даже если сервер по ошибке прислал снимок с
         * признаком «управлять можно». Скрытие действия на клиенте не является
         * защитой: настоящая защита в том, что команды по-прежнему требуют
         * открытое меню пилона.
         */
        if (this.readOnly) return List.of();
        if (value.kind() == CityMapPanel.SelectionKind.TRAIN) {
            var train = this.data.getRailwayTrains().stream()
                    .filter(registered -> registered.fleetTrainId().equals(value.id())).findFirst().orElse(null);
            if (train == null || !train.canManage()) return List.of();
            return List.of(
                    Component.translatable("gui.createcityexpansioncore.world_map.action.change_type"),
                    Component.translatable("gui.createcityexpansioncore.world_map.action.manage_railway"),
                    Component.translatable("gui.createcityexpansioncore.world_map.action.select_route"));
        }
        if (value.kind() == CityMapPanel.SelectionKind.TRAIN_UNREGISTERED) {
            var candidate = this.data.getRailwayTrainCandidates().stream()
                    .filter(pending -> pending.createTrainId().equals(value.id())).findFirst().orElse(null);
            if (candidate == null || !candidate.canManage()) return List.of();
            return List.of(
                    Component.translatable("gui.createcityexpansioncore.world_map.action.register_train"),
                    Component.translatable("gui.createcityexpansioncore.world_map.action.manage_railway"));
        }
        if (!value.own() || value.kind() == CityMapPanel.SelectionKind.WAYPOINT) return List.of();
        return List.of(contextAction(value));
    }

    private void handleContextAction(CityMapPanel.Selection value, int index) {
        int managed = contextActions(value).size();
        if (index >= managed) { handleNavigationAction(value, index - managed); return; }
        // Повторяет проверку contextActions: кнопки скрыты, поэтому сюда попасть
        // нельзя, но обработчик не должен доверять собственному UI.
        if (this.readOnly) {
            // Все ветви ниже отправляют пакеты или открывают экраны управления, где
            // город можно менять. Проверка одна на весь метод, а не в каждой ветви:
            // забытая ветвь означала бы действие на карте, которая для чтения.
            return;
        }
        if (value.kind() == CityMapPanel.SelectionKind.TRAIN) {
            var train = this.data.getRailwayTrains().stream()
                    .filter(registered -> registered.fleetTrainId().equals(value.id())).findFirst().orElse(null);
            if (train == null || !train.canManage()) return;
            if (index == 0) {
                RailwayTypes.TrainType next = RailwayTypes.TrainType.values()[
                        (train.type().ordinal() + 1) % RailwayTypes.TrainType.values().length];
                this.mutationTitle = Component.translatable("gui.createcityexpansioncore.world_map.confirm.type", train.name(), Component.translatable("gui.createcityexpansioncore.railway.train_type." + next.name().toLowerCase(Locale.ROOT)));
                this.pendingMutation = () -> PacketDistributor.sendToServer(new RailwayCommandPayload(this.data.getPylonPos(), RailwayCommandPayload.Action.SET_TRAIN_TYPE, train.fleetTrainId(), null, "", next.ordinal(), true, List.of()));
            } else if (index == 1) {
                this.parent.openTrainManagement(train.fleetTrainId());
                if (this.minecraft != null) this.minecraft.setScreen(this.parent);
            } else if (index == 2) {
                this.parent.openTrainManagement(train.fleetTrainId());
                PacketDistributor.sendToServer(RailwayCommandPayload.simple(this.data.getPylonPos(),
                        RailwayCommandPayload.Action.REQUEST_ROUTES, train.fleetTrainId(), null));
                if (this.minecraft != null) this.minecraft.setScreen(this.parent);
            }
            return;
        }
        if (value.kind() == CityMapPanel.SelectionKind.TRAIN_UNREGISTERED) {
            var candidate = this.data.getRailwayTrainCandidates().stream()
                    .filter(pending -> pending.createTrainId().equals(value.id())).findFirst().orElse(null);
            if (candidate == null || !candidate.canManage()) return;
            if (index == 0) {
                // Server replies with a fresh state; reconcileTrainSelection() re-pins the new
                // TRAIN marker and the card immediately offers the type action.
                this.mutationTitle = Component.translatable("gui.createcityexpansioncore.world_map.confirm.register", candidate.name());
                this.pendingMutation = () -> PacketDistributor.sendToServer(RailwayCommandPayload.simple(this.data.getPylonPos(), RailwayCommandPayload.Action.REGISTER_TRAIN, candidate.createTrainId(), null));
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
        return CcecProblemCenter.count(this.data.getEconomyState(), this.data.getRailwayOwnership(),
                this.data.getRailwayTrains());
    }

    /**
     * A summary card, not a screen takeover: the map stays visible behind a light dim so the player
     * keeps their orientation while reading what needs attention.
     */
    private void renderProblemCenter(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0x66101517);
        var panel = WorldMapUiLayout.problemModal(this.width, this.height);
        plate(graphics, panel.x(), panel.y(), panel.width(), panel.height());
        graphics.renderOutline(panel.x(), panel.y(), panel.width(), panel.height(), ACCENT);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.problems"), panel.x() + SECTION, panel.y() + NORMAL, ACCENT, false);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.problem_count", problemCount()), panel.x() + SECTION, panel.y() + 26, TEXT, false);
        List<CityMapPanel.Selection> objects = problemObjects();
        this.problemOffset = Math.min(this.problemOffset, Math.max(0, objects.size() - problemRows()));
        if (objects.isEmpty()) graphics.drawWordWrap(this.font, Component.translatable(CcecProblemCenter.nextStepKey(this.data.getEconomyState(), problemCount())), panel.x() + SECTION, panel.y() + 50, panel.width() - SECTION * 2, MUTED);
        for (int index = 0; index < Math.min(problemRows(), objects.size() - this.problemOffset); index++) {
            var value = objects.get(index + this.problemOffset); int y = panel.y() + 48 + index * 25;
            graphics.fill(panel.x() + SECTION, y, panel.right() - SECTION, y + 22, PANEL_ALT);
            MapMarkerRenderer.drawGlyph(graphics, MapMarkerRenderer.glyph(value.icon()), panel.x() + SECTION + 3, y + 3, MapPalette.ALERT);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(value.title().getString(), panel.width() - 54), panel.x() + SECTION + 24, y + 3, TEXT, false);
            String detail = value.details().isEmpty() ? selectionType(value).getString() : value.details().getFirst().getString();
            graphics.drawString(this.font, this.font.plainSubstrByWidth(detail, panel.width() - 54), panel.x() + SECTION + 24, y + 13, MUTED, false);
        }
        graphics.drawWordWrap(this.font, Component.translatable("gui.createcityexpansioncore.world_map.problems.located", objects.size(), problemCount()), panel.x() + SECTION, panel.bottom() - 52, panel.width() - SECTION * 2, MUTED);
        graphics.drawCenteredString(this.font, Component.translatable(this.readOnly ? "gui.createcityexpansioncore.world_map.close" : "gui.createcityexpansioncore.city.hub.open_logistics"), panel.x() + panel.width() / 2, panel.bottom() - 20, ACCENT);
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
        graphics.drawCenteredString(this.font, Component.translatable("gui.createcityexpansioncore.city.hub.guide.dismiss"),
                panel.x() + panel.width() / 2, panel.bottom() - 20, MUTED);
    }

    private void renderStatus(GuiGraphics graphics, int mouseX, int mouseY) {
        var strip = WorldMapUiLayout.statusStrip(this.width, this.height);
        plate(graphics, strip.x(), strip.y(), strip.width(), strip.height());
        Component coordinates = this.map.coordinateStatus(mouseX, mouseY);
        int right = strip.right() - 6;
        int coordinateWidth = Math.min(strip.width() / 2, this.font.width(coordinates));
        graphics.drawString(this.font, this.font.plainSubstrByWidth(coordinates.getString(), coordinateWidth), right - coordinateWidth, strip.y() + 5, MUTED, false);
        String identity = this.data.getCityName() + " · " + this.data.getMemberCount() + " · " + Component.translatable("gui.createcityexpansioncore.world_map." + (this.readOnly ? "read_only_short" : "management_short")).getString();
        int leftWidth = Math.min(180, Math.max(20, strip.width() - coordinateWidth - 22));
        graphics.drawString(this.font, this.font.plainSubstrByWidth(identity, leftWidth), strip.x() + 6, strip.y() + 5, TEXT, false);
        int statusX = strip.x() + leftWidth + 14;
        int statusWidth = right - coordinateWidth - statusX - 8;
        if (statusWidth > 40) graphics.drawString(this.font, this.font.plainSubstrByWidth(this.map.terrainStatus().getString(), statusWidth), statusX, strip.y() + 5, MUTED, false);
        offerTooltip(this.map.terrainStatus(), strip.contains(mouseX, mouseY));
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
            if (!this.problemCenterOpen && !this.guideOpen && this.viewButtons.get(index).contains(mouseX, mouseY)) {
                handleViewAction(index); dismissTutorial(); return true;
            }
        }
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
        if (selected != null && this.cardActionY >= 0 && inside(mouseX, mouseY, this.cardX, this.cardY, this.cardWidth, this.cardHeight)) {
            for (int index = 0; index < this.cardActionCount; index++) {
                int rowY = this.cardActionY + index * 20;
                if (inside(mouseX, mouseY, this.cardX + WorldMapUiLayout.LARGE, rowY,
                        this.cardWidth - WorldMapUiLayout.LARGE * 2, 18)) {
                    handleContextAction(selected, index);
                    return true;
                }
            }
        }
        if (this.cardWidth > 0
                && inside(mouseX, mouseY, this.cardX, this.cardY, this.cardWidth, this.cardHeight)) return true;
        this.followingTrain = null;
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
            var objects = problemObjects();
            var bounds = WorldMapUiLayout.problemModal(this.width, this.height);
            for (int index = 0; index < Math.min(problemRows(), objects.size() - this.problemOffset); index++) if (inside(mouseX, mouseY, bounds.x() + SECTION, bounds.y() + 48 + index * 25, bounds.width() - SECTION * 2, 22)) {
                selectObject(objects.get(index + this.problemOffset)); this.problemCenterOpen = false; return true;
            }
            WorldMapUiLayout.Rect panel = WorldMapUiLayout.problemModal(this.width, this.height);
            boolean open = inside(mouseX, mouseY, panel.x() + WorldMapUiLayout.SECTION, panel.bottom() - 30,
                    panel.width() - WorldMapUiLayout.SECTION * 2, 24);
            this.problemCenterOpen = false;
            // В режиме чтения экран проблем информационный: его кнопка ведёт в
            // Logistics, где можно принимать и отменять контракты.
            if (open && !this.readOnly && this.minecraft != null) {
                this.minecraft.setScreen(new LogisticsScreen(this.parent));
            }
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
            case WorldMapUiLayout.GLOBAL_CENTER_CITY -> { this.followingTrain = null; this.map.centerOn(this.data.getPylonPos()); dismissTutorial(); }
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
        this.followingTrain = null;
        CityMapPanel.Selection current=this.map.getSelection();if(current!=null)this.forwardHistory.push(current);
        this.map.selectAndCenter(this.backHistory.pop());
    }

    private void navigateForward() {
        if (this.forwardHistory.isEmpty()) return;
        this.followingTrain = null;
        CityMapPanel.Selection current=this.map.getSelection();if(current!=null)this.backHistory.push(current);
        this.map.selectAndCenter(this.forwardHistory.pop());
    }

    /**
     * Единственное действие карты по клику: перейти к выбранному объекту.
     *
     * <p>В режиме чтения остаётся только навигация — то, что не меняет город.
     * Переходы в Logistics, отношения и управление рельсами исключены: каждый из
     * них ведёт на экран, где что-то можно принять, отменить или настроить, а
     * здесь можно только смотреть.
     */
    private void handleContextAction(CityMapPanel.Selection value) {
        switch (value.kind()) {
            case FREIGHT_POINT, TRAIN, DISCOVERY -> {
                if (this.readOnly) { this.map.centerOn(value.pos()); return; }
                if (this.minecraft != null) this.minecraft.setScreen(new LogisticsScreen(this.parent));
            }
            case CITY -> {
                if (this.readOnly) { this.map.centerOn(value.pos()); return; }
                if (this.minecraft != null) this.minecraft.setScreen(new CityRelationsScreen(this.parent));
            }
            case RAILWAY_SECTION -> {
                if (this.readOnly || this.parent == null) { this.map.centerOn(value.pos()); return; }
                this.parent.openRailwayManagement();
                if (this.minecraft != null) this.minecraft.setScreen(this.parent);
            }
            case ROUTE, STATION -> applyMode(Mode.RAILWAY);
            default -> this.map.centerOn(value.pos());
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
                this.map.setOverlay(overlay, !this.map.isOverlayEnabled(overlay));
                return true;
            }
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
        return new WorldMapUiLayout.Rect(raw.x(), raw.y() - this.layerScroll, raw.width(), raw.height());
    }

    private boolean handleSearchClick(double mouseX, double mouseY) {
        var visible = visibleSearchResults();
        for (int index = 0; index < 3; index++) if (searchFilterBounds(index).contains(mouseX, mouseY)) {
            if (index == 0) this.searchType = (this.searchType + 1) % (CityMapPanel.SelectionKind.values().length + 1);
            else if (index == 1) this.searchOwner = (this.searchOwner + 1) % 3;
            else this.searchProblems = !this.searchProblems;
            this.searchOffset = 0; updateSearchResults(); return true;
        }
        for (int index = 0; index < visible.size(); index++) if (WorldMapUiLayout.searchRow(this.width, this.modeLabelWidths, this.globalLabelWidths, index, 228).contains(mouseX, mouseY)) {
            selectObject(visible.get(index)); this.searchOpen = false; this.searchBox.setVisible(false); setFocused(null); return true;
        }
        return WorldMapUiLayout.searchPopover(this.width, this.modeLabelWidths, this.globalLabelWidths, visible.size(), 228).contains(mouseX, mouseY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        dismissTutorial();
        if (this.pendingMutation != null || this.guideOpen || this.problemCenterOpen || this.map != null && this.map.hasPendingClaim()) return true;
        // Панорамировать нечего, пока панели нет: свайп по экрану загрузки.
        if (this.map == null) return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        boolean dragged = this.map.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        if (dragged) this.followingTrain = null;
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
            case CITIES -> setOverlays(true, true, false, false, false, false);
            case RAILWAY -> setOverlays(false, false, true, true, true, false);
            case LOGISTICS -> setOverlays(false, false, false, true, true, true);
            case ENERGY -> setOverlays(false, false, false, false, false, false);
        }
        this.map.setOverlay(CityMapPanel.Overlay.RELATIONS, mode == Mode.CITIES);
        if (this.preferences != null && this.preferences.overlays[mode.ordinal()] >= 0) {
            int mask = this.preferences.overlays[mode.ordinal()];
            for (var layer : CityMapPanel.Overlay.values()) this.map.setOverlay(layer, (mask & (1 << layer.ordinal())) != 0);
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

    private Path preferencePath() { return this.minecraft.gameDirectory.toPath().resolve("config/ccec-atlas.properties"); }
    private void loadPreferences() {
        if (this.preferences != null || this.map == null || this.minecraft == null) return;
        String server = this.minecraft.getCurrentServer() != null ? this.minecraft.getCurrentServer().ip
                : this.minecraft.getSingleplayerServer() != null ? this.minecraft.getSingleplayerServer().getWorldData().getLevelName() : "local";
        this.preferenceWorld = server + ":" + (this.minecraft.player == null ? "" : this.minecraft.player.getUUID()) + ":" + this.data.getDimensionId() + ":" + this.data.getPylonPos().asLong();
        AtlasPreferences loaded = AtlasPreferences.load(preferencePath(), this.preferenceWorld);
        this.preferences = null;
        applyMode(Mode.values()[loaded.mode]);
        this.preferences = loaded;
        if (this.preferences.positioned) this.map.restoreView(this.preferences.centerX, this.preferences.centerZ, this.preferences.zoom);
        this.map.setPresentation(this.preferences.grid, this.preferences.minimap);
        this.map.setPersonalPins(this.preferences.pins);
        int mask = this.preferences.overlays[this.mode.ordinal()];
        if (mask >= 0) for (var layer : CityMapPanel.Overlay.values()) this.map.setOverlay(layer, (mask & (1 << layer.ordinal())) != 0);
    }
    private void rememberOverlays() {
        int mask = 0; for (var layer : CityMapPanel.Overlay.values()) if (this.map.isOverlayEnabled(layer)) mask |= 1 << layer.ordinal();
        this.preferences.overlays[this.mode.ordinal()] = mask;
    }
    private void savePreferences() {
        if (this.preferences == null || this.map == null) return;
        rememberOverlays(); this.preferences.mode = this.mode.ordinal(); this.preferences.centerX = this.map.centerX();
        this.preferences.centerZ = this.map.centerZ(); this.preferences.zoom = this.map.zoom(); this.preferences.positioned = true;
        this.preferences.grid = this.map.coordinateGrid(); this.preferences.minimap = this.map.overviewVisible();
        this.preferences.pins.clear(); this.preferences.pins.addAll(this.map.personalPins());
        try { this.preferences.save(preferencePath(), this.preferenceWorld); }
        catch (java.io.IOException error) { org.slf4j.LoggerFactory.getLogger(PolishedWorldMapScreen.class).warn("Cannot save local atlas view", error); }
    }
    @Override public void removed() {
        savePreferences(); this.followingTrain = null; this.pendingMutation = null;
        if (this.map != null) { this.map.cancelPersonalTools(); this.map.setDrawInternalControls(true); this.map.releaseTerrainTextures(); }
        if (this.readOnly && !openingIdentityEditor) { CityWorldMapSession.INSTANCE.clear(); com.example.examplemod.client.CityPublicDirectory.reset(); }
        super.removed();
    }
    private int layoutViewButtons(int mapWidth) {
        this.viewButtons.clear();
        int startY = WorldMapUiLayout.popoverY(this.width, this.modeLabelWidths, this.globalLabelWidths) + NORMAL;
        int x = WorldMapUiLayout.EDGE, y = startY + 14;
        for (String key : java.util.Arrays.copyOf(VIEW_KEYS, 5)) {
            int width = Math.min(mapWidth, this.font.width(Component.translatable("gui.createcityexpansioncore.world_map.view." + key)) + 12);
            if (x > WorldMapUiLayout.EDGE && x + width > WorldMapUiLayout.EDGE + mapWidth) { x = WorldMapUiLayout.EDGE; y += 22; }
            this.viewButtons.add(new WorldMapUiLayout.Rect(x, y, width, 18)); x += width + MICRO;
        }
        return y + 18 + NORMAL;
    }
    private void renderViewButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.viewButtons.isEmpty()) return;
        graphics.drawString(this.font, this.mode.label(), WorldMapUiLayout.EDGE, this.viewButtons.getFirst().y() - 14, ACCENT, false);
        for (int index = 0; index < this.viewButtons.size(); index++) {
            var rect = this.viewButtons.get(index);
            boolean active = index == 2 && this.followingTrain != null || index == 3 && this.map.pinToolActive() || index == 4 && this.map.measureToolActive() || index == 5 && this.map.overviewVisible() || index == 6 && this.map.coordinateGrid();
            graphics.fill(rect.x(), rect.y(), rect.right(), rect.bottom(), active ? 0xFF443824 : PANEL_ALT);
            graphics.renderOutline(rect.x(), rect.y(), rect.width(), rect.height(), active ? ACCENT : FRAME);
            Component label = Component.translatable("gui.createcityexpansioncore.world_map.view." + VIEW_KEYS[index]);
            graphics.drawString(this.font, this.font.plainSubstrByWidth(label.getString(), rect.width() - 6), rect.x() + 3, rect.y() + 5, TEXT, false);
            offerTooltip(label, rect.contains(mouseX, mouseY));
        }
        if (this.map.pinToolActive() || this.map.measureToolActive()) {
            Component hint = Component.translatable("gui.createcityexpansioncore.world_map.tool." + (this.map.pinToolActive() ? "pin" : "measure"));
            int width = Math.max(1, WorldMapUiLayout.contextPanelLeft(this.width) - WorldMapUiLayout.EDGE - NORMAL);
            graphics.drawWordWrap(this.font, hint, WorldMapUiLayout.EDGE, this.viewButtons.getLast().bottom() + NORMAL + 4, width, TEXT);
        }
    }
    private void handleViewAction(int index) {
        switch (index) {
            case 0 -> { this.followingTrain = null; this.map.centerOnPlayer(); }
            case 1 -> { this.followingTrain = null; this.map.fitCity(); }
            case 2 -> {
                var selected = this.map.getSelection();
                this.followingTrain = this.followingTrain != null ? null : selected != null && selected.kind() == CityMapPanel.SelectionKind.TRAIN ? selected.id() : null;
            }
            case 3 -> this.map.togglePinTool();
            case 4 -> this.map.toggleMeasureTool();
            case 5 -> this.map.setPresentation(this.map.coordinateGrid(), !this.map.overviewVisible());
            case 6 -> this.map.setPresentation(!this.map.coordinateGrid(), this.map.overviewVisible());
            case 7 -> { this.map.retryTerrain(); requestSnapshots(true); }
            default -> { }
        }
    }
    private void updateFollowingTrain() {
        var selected = this.map.getSelection();
        if (this.followingTrain == null) {
            if (selected != null && selected.kind() == CityMapPanel.SelectionKind.TRAIN) {
                var current = this.data.getRailwayTrains().stream().filter(value -> value.fleetTrainId().equals(selected.id())).findFirst().orElse(null);
                if (current == null || !this.data.getDimensionId().equals(current.dimension())) this.map.clearSelection();
                else this.map.selectTrain(current);
            }
            return;
        }
        var train = this.data.getRailwayTrains().stream().filter(value -> this.followingTrain.equals(value.fleetTrainId()) && this.data.getDimensionId().equals(value.dimension())).findFirst().orElse(null);
        if (train == null) { this.followingTrain = null; return; }
        this.map.centerOn(net.minecraft.core.BlockPos.containing(train.x(), 0, train.z())); this.map.selectTrain(train);
    }
    private List<Component> navigationActions(CityMapPanel.Selection value) {
        List<Component> actions = new ArrayList<>();
        actions.add(Component.translatable("gui.createcityexpansioncore.world_map.action.center"));
        if (value.kind() == CityMapPanel.SelectionKind.TRAIN) actions.add(Component.translatable("gui.createcityexpansioncore.world_map.view.follow"));
        if (value.kind() == CityMapPanel.SelectionKind.WAYPOINT) actions.add(Component.translatable("gui.createcityexpansioncore.world_map.pin.remove"));
        var profile = value.kind() == CityMapPanel.SelectionKind.CITY ? com.example.examplemod.client.CityPublicDirectory.profile(value.id()) : null;
        if (profile != null && profile.editable()) actions.add(Component.translatable("gui.createcityexpansioncore.city.identity.edit"));
        return actions;
    }
    private void handleNavigationAction(CityMapPanel.Selection value, int index) {
        if (index == 0) { this.followingTrain = null; this.map.centerOn(value.pos()); }
        else if (index == 1 && value.kind() == CityMapPanel.SelectionKind.TRAIN) this.followingTrain = value.id().equals(this.followingTrain) ? null : value.id();
        else if (index == 1 && value.kind() == CityMapPanel.SelectionKind.WAYPOINT) this.map.removeSelectedPin();
        else if (index == 1 && value.kind() == CityMapPanel.SelectionKind.CITY) {
            var profile = com.example.examplemod.client.CityPublicDirectory.profile(value.id());
            if (profile != null && profile.editable()) {
                openingIdentityEditor = true; com.example.examplemod.client.CityPublicDirectory.cancelLoading();
                this.minecraft.setScreen(new CityIdentityEditorScreen(this, profile));
            }
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
        return new WorldMapUiLayout.Rect(panel.x() + 6 + index * width, panel.y() + 28, width - 3, 19);
    }
    private void selectObject(CityMapPanel.Selection value) {
        this.followingTrain = null;
        var before = this.map.getSelection();
        if (before != null && !before.equals(value)) this.backHistory.push(before);
        this.forwardHistory.clear(); this.followingTrain = null;
        switch (value.kind()) {
            case TRAIN, TRAIN_UNREGISTERED, STATION, ROUTE, RAILWAY_SECTION -> applyMode(Mode.RAILWAY);
            case FREIGHT_POINT, DISCOVERY, TRADE_INFRASTRUCTURE -> applyMode(Mode.LOGISTICS);
            default -> applyMode(Mode.CITIES);
        }
        for (var layer : activeOverlays()) this.map.setOverlay(layer, true);
        this.map.selectAndCenter(value);
    }
    private List<CityMapPanel.Selection> problemObjects() { return this.map.search("").stream().filter(CityMapPanel.Selection::warning).toList(); }
    private int problemRows() { return Math.max(1, (WorldMapUiLayout.problemModal(this.width, this.height).height() - 112) / 25); }
    private void renderMutation(GuiGraphics graphics) {
        graphics.fill(0, 0, this.width, this.height, 0x88101517);
        var rect = WorldMapUiLayout.guideModal(this.width, this.height);
        plate(graphics, rect.x(), rect.y(), rect.width(), rect.height());
        graphics.drawWordWrap(this.font, this.mutationTitle, rect.x() + LARGE, rect.y() + LARGE, rect.width() - LARGE * 2, TEXT);
        int half = (rect.width() - LARGE * 3) / 2;
        graphics.fill(rect.x() + LARGE, rect.bottom() - 34, rect.x() + LARGE + half, rect.bottom() - 12, 0xFF5A4522);
        graphics.fill(rect.x() + LARGE * 2 + half, rect.bottom() - 34, rect.right() - LARGE, rect.bottom() - 12, PANEL_ALT);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.city.plot.claim_confirm"), rect.x() + LARGE + 6, rect.bottom() - 27, TEXT, false);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.common.cancel"), rect.x() + LARGE * 2 + half + 6, rect.bottom() - 27, TEXT, false);
    }
    private void handleMutationClick(double x, double y, int button) {
        if (button != 0) return;
        var rect = WorldMapUiLayout.guideModal(this.width, this.height);
        int half = (rect.width() - LARGE * 3) / 2;
        if (inside(x, y, rect.x() + LARGE, rect.bottom() - 34, half, 22)) {
            Runnable command = this.pendingMutation; this.pendingMutation = null;
            if (!this.readOnly && command != null) command.run();
        } else if (inside(x, y, rect.x() + LARGE * 2 + half, rect.bottom() - 34, half, 22)) this.pendingMutation = null;
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
