package com.example.examplemod.client.map;

import com.example.examplemod.network.CityMapPeersPayload;

import com.example.examplemod.network.CityMapTilePayload;
import com.example.examplemod.network.RequestCityMapTilePayload;
import com.example.examplemod.city.CityColors;
import com.example.examplemod.city.CityPlot;
import com.example.examplemod.city.CityProjectSnapshot;
import com.example.examplemod.network.RailwayMapPayload;
import com.example.examplemod.network.EconomyStatePayload;
import com.example.examplemod.network.CityRelationSnapshot;
import com.example.examplemod.economy.FreightPoint;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import com.example.examplemod.railway.RailwayStationSnapshot;
import com.example.examplemod.railway.RailwayTrainSnapshot;
import com.example.examplemod.railway.RailwayTrainCandidate;
import com.example.examplemod.client.status.CcecStatusUi;
import com.example.examplemod.status.CcecStatusResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

public final class CityMapPanel {

    private static final double MIN_ZOOM = AtlasMapScale.MIN_ZOOM;
    private static final double MAX_ZOOM = AtlasMapScale.MAX_ZOOM;
    private static final double ZOOM_STEP = 1.25;
    private static final double MAX_WORLD_COORDINATE = 29_999_984.0;
    /**
     * Минимальная экранная плотность одной выборки рельефа.
     * Ниже этого рельеф рисуем укрупнёнными блоками — глаз этого
     * не замечает, а число заливок падает в разы при отдалении.
     */
    private static final double MIN_SAMPLE_SCREEN_PX = 2.0;
    /** Terrain texture rebuilds are budgeted per frame so a level-of-detail change never stalls a pan. */
    private static final int MAX_TILE_REBUILDS_PER_FRAME = 16;
    /** Upper bound on cached tiles, so exploring a large world cannot grow the client cache forever. */
    private static final int MAX_CACHED_TILES = 4096;
    private static final int MAX_REQUESTS_PER_RENDER = 8;
    private static final long REQUEST_INTERVAL_MILLIS = 100L;
    private static final long REQUEST_TIMEOUT_MILLIS = 5_000L;
    private static final long HOVER_DELAY_MILLIS = 160L;
    private static final double MEDIUM_DETAIL_ZOOM = 0.80;
    private static final double CLOSE_DETAIL_ZOOM = 1.20;

    private static final int FRAME_COLOR = MapPalette.INK_SOFT;
    private static final int ACCENT_COLOR = MapPalette.OWN;

    /**
     * Правая граница, которую карта не перекрывает.
     *
     * <p>World Map держит справа контекстную колонку. Приборная
     * обвязка обязана её уважать: иначе легенда и миникарта
     * оказываются наполовину под чужой панелью. Экран сообщает
     * границу один раз на кадр; по умолчанию справа от карты
     * ничего нет.
     */
    private int chromeAvoidRight = Integer.MAX_VALUE;

    /**
     * Границы приборной обвязки на текущем кадре.
     *
     * <p>Нужны экрану-атласу, чтобы повесить легенду рядом с
     * картой, не угадывая её размер.
     */
    private CityMapFrame.Geometry chromeGeometry;
    private java.util.function.Consumer<GuiGraphics> visualOverlay;

    /** Optional client visual layer, inside the terrain batch and below markers/chrome. */
    public void setVisualOverlay(java.util.function.Consumer<GuiGraphics> overlay) { this.visualOverlay=overlay; }

    /*
     * Территория рисуется по реальным участкам 50×50, а не по
     * квадрату вокруг пилона. Свой город заливается заметно плотнее
     * и обводится контуром, чужие — в собственном цвете города, но
     * приглушённо: карта остаётся инфраструктурной, а не политической.
     */
    /** Цвет подсветки участка под курсором, когда его можно занять. */
    private static final int CLAIMABLE_HINT = 0xFFE8C87A;
    /** Сетка участков видна только когда участок достаточно велик на экране. */
    private static final double MIN_PLOT_GRID_PX = 14.0;
    private static final int MARKER_SPACING = 22;
    private static final int[][] MARKER_OFFSETS = {
            {0, 0}, {0, -24}, {24, 0}, {0, 24}, {-24, 0},
            {18, -18}, {18, 18}, {-18, 18}, {-18, -18},
            {0, -36}, {36, 0}, {0, 36}, {-36, 0},
            {26, -26}, {26, 26}, {-26, 26}, {-26, -26}
    };

    private final BlockPos pylonPos;
    private List<CityMapPeersPayload.Peer> territoryPeers = List.of();
    private List<com.example.examplemod.city.CityPublicProfile> publicCities = List.of();
    private boolean publicDirectoryActive;
    private boolean publicAnchor;
    private boolean hasCityAnchor() { return !publicDirectoryActive || publicAnchor; }
    private boolean atAnchor(CityMapPeersPayload.Peer peer) { return peer.pylonX() == pylonPos.getX() && peer.pylonZ() == pylonPos.getZ(); }
    public void receivePublicCities(List<com.example.examplemod.city.CityPublicProfile> cities) {
        publicDirectoryActive = true; publicCities = List.copyOf(cities);
        publicAnchor = publicCities.stream().anyMatch(c -> c.pos().getX() == pylonPos.getX() && c.pos().getZ() == pylonPos.getZ());
        receivePeers(territoryPeers);
    }
    private final ResourceLocation dimensionId;
    private List<CityProjectSnapshot> projects;
    private List<RailwayMapPayload.TrackSegment> railwayTracks = List.of();
    private List<RailwayMapPayload.NetworkView> railwayNetworks = List.of();
    private List<RailwayMapPayload.SectionView> railwaySections = List.of();
    private List<RailwayMapPayload.RouteView> railwayRoutes = List.of();
    private List<RailwayStationSnapshot> railwayStations = List.of();
    private List<RailwayTrainSnapshot> railwayTrains = List.of();
    private List<RailwayTrainCandidate> railwayTrainCandidates = List.of();
    private List<EconomyStatePayload.InfrastructureView> economyInfrastructure = List.of();
    private List<FreightPoint> freightPoints = List.of();
    private List<FreightLink> freightLinks = List.of();
    private List<FreightCandidate> freightCandidates = List.of();
    private Map<UUID, String> routeStrategies = Map.of();
    private Map<UUID, CityRelationSnapshot.Status> relations = Map.of();
    /**
     * Соседние города, полученные с сервера один раз при открытии пилона.
     * Хранятся как есть и переиспользуются каждый кадр — никаких
     * вычислений и обходов мира в цикле отрисовки.
     */
    private List<CityMapPeersPayload.Peer> peers = List.of();

    /**
     * Кого просить о подтверждении claim участка.
     *
     * <p>Панель карты ничего не знает о городах: она только ловит
     * правый клик, переводит его в участок мировой сетки 50×50 и
     * сообщает координаты. Решение о claim принимает экран, а данные
     * меняет сервер. Поле {@code null} означает «правый клик по
     * карте ничего не делает» — так ведёт себя карта, открытая не
     * из City Pylon.
     */
    private java.util.function.LongConsumer plotClaimRequest;

    /** Участок под курсором в текущем кадре. Только для подсветки, без кеширования между кадрами. */
    private CityPlot hoveredPlot;
    private boolean hoveredPlotClaimable;

    /**
     * Участки, которые можно занять прямо сейчас: свободные и примыкающие стороной к своей
     * территории.
     *
     * <p>Считается один раз на снимок с сервера, а не каждый кадр: набор зависит только от
     * территории, а пересчёт на кадре означал бы обход участков в цикле отрисовки.
     *
     * <p>Без этой подсветки игрок не видит, куда вообще можно расширяться, и вынужден угадывать,
     * кликая по карте. Правило смежности становится видимым правилом, а не загадкой.
     */
    private List<CityPlot> claimableFrontier = List.of();

    /** Тот же набор для проверки попадания за O(1) в кадре. */
    private Set<CityPlot> claimableLookup = Set.of();

    /**
     * Участок, ожидающий подтверждения, или {@code null}.
     *
     * <p>Подтверждение живёт внутри карты, а не отдельным экраном: игрок должен
     * видеть участок и соседнюю территорию, пока решает. Отдельный экран
     * закрывал собой ровно то, ради чего игрок открыл подтверждение.
     */
    private CityPlot pendingClaim;

    /**
     * Геометрия подтверждения. Заполняется {@link #layoutClaimOverlay()} и
     * служит единственным источником и для отрисовки, и для проверки клика.
     */
    private int confirmX, confirmY, cancelX, cancelY;
    private int overlayX, overlayY, overlayW, overlayH, buttonW;
    private String overlayTitle = "";
    private List<String> overlayLines = List.of();

    /** Открыто ли подтверждение claim. */
    public boolean hasPendingClaim() {
        return this.pendingClaim != null;
    }

    /** Снимает подтверждение без отправки запроса. */
    public void cancelPendingClaim() {
        setPendingClaim(null);
    }

    /** Подтверждает открытый участок — то же, что нажатие кнопки. */
    public void confirmPendingClaim() {
        CityPlot plot = this.pendingClaim;
        if (plot == null) {
            return;
        }
        // Сначала снимаем, потом отправляем: отправка вызывает обработчик
        // экрана, и при обратном порядке подтверждение мигнуло бы на кадр.
        setPendingClaim(null);
        if (this.plotClaimRequest != null) {
            this.plotClaimRequest.accept(plot.packedXZ());
            LOGGER.info("City plot claim requested: plot=({},{})", plot.plotX(), plot.plotZ());
        }
    }

    /**
     * Единственная точка смены подтверждения.
     *
     * <p>Геометрия сбрасывается вместе с участком: показывать её заново
     * каждый кадр незачем, но оставлять старую нельзя — при следующем
     * открытии кнопки на секунду оказались бы на месте прошлого участка.
     */
    private void setPendingClaim(CityPlot plot) {
        this.pendingClaim = plot;
        this.overlayLines = List.of();
    }
    private final Map<UUID, TrainPosition> previousTrainPositions = new HashMap<>();
    private long railwaySnapshotAtMillis;
    private boolean showTracks = true;
    private boolean showStations = true;
    private boolean showTrains = true;
    private boolean showTradeInfrastructure = true;
    private boolean showCities = true;
    private boolean showTerritory = true;
    private int territoryMode;
    private boolean projectFocus, projectPicking, showPlotGrid;
    private final java.util.Map<UUID,List<TerritoryPresentation.Edge>> territoryEdges=new java.util.HashMap<>();
    public void setTerritoryPresentation(int mode,boolean plots){territoryMode=Math.clamp(mode,0,2);showPlotGrid=plots;}
    public int territoryMode(){return territoryMode;}
    public boolean plotGrid(){return showPlotGrid;}
    public void setProjectFocus(boolean focus,boolean picking){projectFocus=focus;projectPicking=picking;}
    private boolean showProjects = true;
    private boolean showRelations;
    private boolean drawInternalControls = true;
    private Selection selection;
    private Selection hoveredSelection;
    private Selection hoverCandidate;
    private long hoverCandidateSince;
    private Focus focus = Focus.WORLD;
    private long routeSelectionStartedAt;
    private final Map<TileKey, CachedTile> tileCache = new java.util.LinkedHashMap<>(64, .75f, true);
    private final Map<TileKey, Long> requestedTiles = new HashMap<>();
    /** Lightweight tile handles. GPU storage is shared and capped by TerrainAtlas. */
    private final Map<TileKey, TerrainTileTexture> terrainTextures = new HashMap<>();
    private TerrainTileStore terrainStore;
    private UUID terrainWorld=new UUID(0,0);

    private TerrainTileStore terrainStore() {
        if (this.terrainStore == null) this.terrainStore = TerrainCache.store(this.dimensionId.toString());
        return this.terrainStore;
    }
    private CachedTile cachedTerrain(TileKey key) {
        CachedTile local = this.tileCache.get(key);
        if (local != null) return local;
        var saved = terrainStore().get(key.tileX(), key.tileZ(),key.level());
        if (saved == null || !terrainWorld.equals(new UUID(0,0))&&!saved.worldId().equals(terrainWorld)) return null;
        local = new CachedTile(saved.colors(), saved.contours(), saved.complete(), 0L,saved.heights(),saved.revision(),saved.worldId());
        this.tileCache.put(key, local); evictTerrainTiles();
        return local;
    }
    private final Map<MarkerKey, ScreenPoint> markerPositions = new HashMap<>();

    private double centerX;
    private double centerZ;
    private double zoom = 1.25;
    private boolean dragging;
    private boolean motionEnabled, reducedMotion;
    private long motionFrame, cameraStarted, selectionStarted, dragAt;
    private double fromX, fromZ, fromZoom, toX, toZ, toZoom, velocityX, velocityZ;
    private boolean cameraMoving;
    private double zoomAnchorX, zoomAnchorZ, zoomMouseX, zoomMouseY;
    private boolean anchoredZoom;
    private double pointerYaw;
    private boolean pointerInitialized;

    public void setMotion(boolean enabled, boolean reduced) {
        motionEnabled = enabled; reducedMotion = reduced;
        if (!enabled || reduced) stopMotion();
    }
    public boolean reducedMotion() { return reducedMotion; }
    private boolean preciseTool() { return projectPicking || placingPin || measuring || pendingClaim != null; }
    private void stopMotion() { cameraMoving = false; anchoredZoom = false; velocityX = velocityZ = 0; }
    private void animateTo(double x, double z, double scale) {
        stopMotion();
        if (!motionEnabled || reducedMotion || preciseTool()) { restoreView(x,z,scale); return; }
        fromX=centerX; fromZ=centerZ; fromZoom=zoom;
        toX=x; toZ=z; toZoom=scale; cameraStarted=System.nanoTime(); cameraMoving=true;
    }
    public void followPosition(double x, double z) {
        if (preciseTool()) return;
        if (Math.abs(x-centerX)*zoom > viewportWidth*.12 || Math.abs(z-centerZ)*zoom > viewportHeight*.12)
            if (!cameraMoving || Math.hypot(x-toX,z-toZ)*zoom > 4) animateTo(x,z,zoom);
    }
    private void updateMotion() {
        long now=System.nanoTime();
        double dt=motionFrame==0?0:Math.clamp((now-motionFrame)/1e9,0,.05); motionFrame=now;
        if (preciseTool()) stopMotion();
        if (cameraMoving) {
            double t=MapMotion.ease((now-cameraStarted)/180_000_000.0);
            zoom=fromZoom+(toZoom-fromZoom)*t;
            if (anchoredZoom) {
                centerX=zoomAnchorX-(zoomMouseX-viewportX-viewportWidth/2.0)/zoom;
                centerZ=zoomAnchorZ-(zoomMouseY-viewportY-viewportHeight/2.0)/zoom;
            } else { centerX=fromX+(toX-fromX)*t; centerZ=fromZ+(toZ-fromZ)*t; }
            if (t>=1) { cameraMoving=false; anchoredZoom=false; }
        } else if (!dragging && motionEnabled && !reducedMotion && !preciseTool()) {
            double decay=Math.exp(-dt*14), step=(1-decay)/14;
            centerX+=velocityX*step; centerZ+=velocityZ*step;
            velocityX*=decay; velocityZ*=decay;
            if (Math.hypot(velocityX,velocityZ)*zoom<2) velocityX=velocityZ=0;
        }
        clampCenter();
        var player=Minecraft.getInstance().player;
        if (player!=null) {
            double target=player.getYRot()+180;
            pointerYaw=pointerInitialized && motionEnabled && !reducedMotion?MapMotion.angle(pointerYaw,target,dt):target;
            pointerInitialized=true;
        }
    }


    private int viewportX;
    private int viewportY;
    private int viewportWidth;
    private int viewportHeight;
    private int centerButtonX;
    private int centerButtonY;
    private long nextRequestAtMillis;
    private int layerButtonsX;
    private int layerButtonsY;

    private boolean showCoordinateGrid;
    private boolean showOverview;
    private boolean placingPin;
    private boolean measuring;
    private BlockPos measureStart, measureEnd;
    private final List<AtlasPreferences.Pin> personalPins = new ArrayList<>();
    private final Set<TileKey> failedTiles = new HashSet<>();
    private final Map<TileKey, Integer> tileAttempts = new HashMap<>();

    private final ItemStack cityIcon = item("createcityexpansioncore:city_pylon");
    private final ItemStack mineIcon = item("createcityexpansioncore:mine_controller");
    private final ItemStack stationIcon = item("create:track_station");
    private final ItemStack trainIcon = item("create:controls");
    private final ItemStack contractIcon = item("createcityexpansioncore:contract_intake");
    private final ItemStack warehouseIcon = item("createcityexpansioncore:warehouse_gateway");
    private final ItemStack freightIcon = item("create:postbox");
    private final ItemStack networkIcon = item("create:track");
    private final ItemStack playerIcon = item("minecraft:compass");

    /*
     * Подсказку накапливаем во время рисования карты и выводим уже
     * после снятия scissor: иначе её режет область карты и она
     * уходит под слои, нарисованные поверх.
     */
    private Component[] pendingTooltip;
    private int pendingTooltipX;
    private int pendingTooltipY;

    /**
     * Подсказка уже отложена в этом кадре.
     *
     * <p>Нужна, чтобы не дублировать текст: подпись у маркера и подсказка
     * начинаются с одного и того же названия, и оно оказывалось на экране
     * дважды. Обнуляется в начале отрисовки вместе с {@link #pendingTooltip}.
     */
    private boolean tooltipShown;

    public CityMapPanel(
            BlockPos pylonPos,
            ResourceLocation dimensionId,
            List<CityProjectSnapshot> projects
    ) {
        this.pylonPos = pylonPos.immutable();
        this.dimensionId = dimensionId;
        this.projects = List.copyOf(projects);
        centerOnPylon();
    }

    public void setProjects(List<CityProjectSnapshot> projects) {
        if (this.projects.equals(projects)) {
            return;
        }
        this.projects = List.copyOf(projects);
    }

    /**
     * Разрешает или запрещает правый клик по карте как запрос на участок.
     *
     * <p>Слушатель ставится только пока City Pylon — верхний экран:
     * вложенные экраны (Атлас мира, Управление) используют ту же панель
     * и не должны предлагать claim от имени города.
     */
    public void setPlotClaimRequest(java.util.function.LongConsumer request) {
        if (this.plotClaimRequest == request) {
            return;
        }
        this.plotClaimRequest = request;
        // Право меняется по тику, а снимок приходит отдельным пакетом: без
        // пересчёта фронтир остался бы от предыдущего права и предлагал
        // участок, который сервер уже не примет.
        rebuildClaimableFrontier();
    }

    /** Участки, доступные для расширения прямо сейчас. Пусто, если claim недоступен. */
    public List<CityPlot> claimableFrontier() {
        return this.claimableFrontier;
    }

    /** Собственный город по последнему снимку, или {@code null}. */
    private CityMapPeersPayload.Peer ownPeer() {
        for (CityMapPeersPayload.Peer peer : this.peers) {
            if (peer.own()) {
                return peer;
            }
        }
        return null;
    }

    /** Пришёл ли хоть один снимок. Отличить «ещё не пришёл» от «город без участков». */
    public boolean hasTerritorySnapshot() {
        return !this.peers.isEmpty();
    }

    /** Участков в последнем снимке для проверки вручную и в headless-проверках. */
    public int receivedPlotCount() {
        int total = 0;
        for (CityMapPeersPayload.Peer peer : this.peers) {
            total += peer.plots().size();
        }
        return total;
    }

    /** Участок под курсором в последнем отрисованном кадре. */
    public CityPlot hoveredPlot() {
        return this.hoveredPlot;
    }

    /**
     * Принимает список соседних городов с сервера.
     *
     * <p>Список неизменяемый и копируется один раз, поэтому в цикле
     * отрисовки нет ни аллокаций, ни работы со словарём.
     */
    public void receivePeers(List<CityMapPeersPayload.Peer> peers) {
        this.territoryPeers = List.copyOf(peers);
        this.peers = CityMapCities.merge(peers, publicCities, publicDirectoryActive);
        territoryEdges.clear();
        for(var peer:this.peers)territoryEdges.put(peer.cityId(),TerritoryPresentation.boundary(peer.plots()));
        if (selection != null && selection.kind() == SelectionKind.CITY && selection.id() != null) {
            var selectedPeer = this.peers.stream().filter(peer -> peer.cityId().equals(selection.id())).findFirst().orElse(null);
            selection = selectedPeer == null ? null : peerSelection(selectedPeer);
        }
        rebuildClaimableFrontier();
        /*
         * Диагностика, а не отладка: «город без территорий» и «снимок не
         * пришёл» выглядят на карте одинаково, и без этой строки в логе
         * невозможно понять, какой из двух случаев перед нами. Строка
         * печатается только при смене числа участков, то есть не каждый
         * кадр.
         */
        int plots = receivedPlotCount();
        if (plots != this.lastReportedPlotCount) {
            this.lastReportedPlotCount = plots;
            LOGGER.info(
                    "City map snapshot: {} cities, {} plots, own={}, claimable={}",
                    this.peers.size(), plots, ownPeer() != null, this.claimableFrontier.size());
        }
    }

    /**
     * Диагностика состояния claim: вызывается из обработчика ПКМ.
     *
     * <p>Каждый отказ должен быть виден в логе с причиной. «ПКМ не работает»
     * без лога невозможно отличить от «нечего брать», «нет права» и «снимок не
     * пришёл» — а это четыре разные ошибки.
     */
    private void logClaimAttempt(CityPlot plot, boolean claimable, boolean accepted) {
        if (accepted || !TERRITORY_DIAGNOSTICS) {
            return;
        }
        LOGGER.info(
                "City plot claim rejected: plot=({},{}) claimable={} listener={} cities={} own={} plots={} frontier={} zoom={}",
                plot == null ? null : plot.plotX(),
                plot == null ? null : plot.plotZ(),
                claimable,
                this.plotClaimRequest != null,
                this.peers.size(),
                ownPeer() != null,
                receivedPlotCount(),
                this.claimableFrontier.size(),
                String.format(java.util.Locale.ROOT, "%.2f", this.zoom));
    }

    /**
     * Диагностика включается системным свойством и по умолчанию выключена:
     * лог нужен при разборе, а не каждому игроку на production-сервере.
     */
    private static final boolean TERRITORY_DIAGNOSTICS =
            Boolean.getBoolean("ccec.territory.diagnostics") || System.getenv("CCEC_TERRITORY_DIAG") != null;

    private static final org.slf4j.Logger LOGGER =
            com.mojang.logging.LogUtils.getLogger();
    private int lastReportedPlotCount = -1;

    /**
     * Пересчитывает набор участков, доступных для расширения.
     *
     * <p>Берётся ровно тот же предикат, что и у сервера — свободен и смежен по стороне — поэтому
     * подсветка не может предложить то, что сервер затем отклонит. Расходиться может только
     * ситуация «свободен, но занят между двумя кадрами», и это честно: подсветка — подсказка,
     * а решение принимает сервер.
     *
     * <p>Обход ограничен территорией города и её ближайшим кольцом, а не всей картой: у каждого
     * занятого участка всего четыре соседа, поэтому работа линейна от размера территории.
     */
    private void rebuildClaimableFrontier() {
        CityMapPeersPayload.Peer own = ownPeer();
        if (own == null || this.plotClaimRequest == null) {
            /*
             * Фронтир рисуется только когда claim действительно доступен.
             * Иначе это обещание, которое сервер не выполнит.
             */
            this.claimableFrontier = List.of();
            this.claimableLookup = Set.of();
            return;
        }
        Set<CityPlot> owned = own.plots();
        Set<CityPlot> blocked = new java.util.HashSet<>();
        for (CityPlot neighbour : owned) {
            for (CityPlot adjacent : neighbour.neighbours()) {
                if (!owned.contains(adjacent)) {
                    blocked.add(adjacent);
                }
            }
        }
        /*
         * Участок занят чужим городом — он не в blocked, потому что
         * blocked строится только из своей территории. Такие участки
         * убираются через снимок соседей: участок без владельца и без
         * смежности со своей землёй заявлен быть не может.
         */
        Set<CityPlot> occupiedByPeers = new java.util.HashSet<>();
        for (CityMapPeersPayload.Peer peer : this.peers) {
            if (!peer.own()) {
                occupiedByPeers.addAll(peer.plots());
            }
        }
        blocked.removeAll(occupiedByPeers);

        List<CityPlot> frontier = new ArrayList<>(blocked.size());
        for (CityPlot candidate : blocked) {
            frontier.add(candidate);
        }
        this.claimableFrontier = List.copyOf(frontier);
        this.claimableLookup = Set.copyOf(frontier);
    }

    public void setDrawInternalControls(boolean drawInternalControls) {
        this.drawInternalControls = drawInternalControls;
    }

    public void setOverlay(Overlay overlay, boolean enabled) {
        switch (overlay) {
            case CITIES -> this.showCities = enabled;
            case TERRITORIES -> this.showTerritory = enabled;
            case PROJECTS -> this.showProjects = enabled;
            case TRACKS -> this.showTracks = enabled;
            case STATIONS -> this.showStations = enabled;
            case TRAINS -> this.showTrains = enabled;
            case TRADE -> this.showTradeInfrastructure = enabled;
            case RELATIONS -> this.showRelations = enabled;
        }
    }

    public boolean isOverlayEnabled(Overlay overlay) {
        return switch (overlay) {
            case CITIES -> this.showCities;
            case TERRITORIES -> this.showTerritory;
            case PROJECTS -> this.showProjects;
            case TRACKS -> this.showTracks;
            case STATIONS -> this.showStations;
            case TRAINS -> this.showTrains;
            case TRADE -> this.showTradeInfrastructure;
            case RELATIONS -> this.showRelations;
        };
    }

    public Selection getSelection() {
        return this.selection;
    }

    public Selection getHoveredSelection() {
        return this.hoveredSelection;
    }

    public void clearSelection() {
        this.selection = null;
    }

    public void setSelection(Selection value) {
        if (value != null && value.kind() == SelectionKind.ROUTE
                && (this.selection == null || !value.id().equals(this.selection.id()))) {
            this.routeSelectionStartedAt = System.currentTimeMillis();
        }
        if (!sameTarget(this.selection,value)) selectionStarted=System.nanoTime();
        this.selection = value;
    }

    public void setFocus(Focus focus) {
        this.focus = focus;
    }

    /**
     * Pins an already-plotted registered train without moving the viewport. Used when a marker
     * changes kind in place — most importantly right after the player registers a Create train,
     * where re-centering would yank the map away from what they were looking at.
     */
    public void selectTrain(RailwayTrainSnapshot train) {
        this.selection = trainSelection(train);
    }

    public ScreenPoint selectionAnchor(Selection value) {
        return value == null ? null : markerPosition(value.kind(), value.id(), value.pos(),
                worldToScreenX(value.pos().getX() + 0.5),
                worldToScreenY(value.pos().getZ() + 0.5));
    }

    /** Chooses a nearby viewport-clamped card position with the fewest marker overlaps. */
    public ScreenPoint contextCardPosition(ScreenPoint anchor, int width, int height) {
        int[][] placements = {
                {anchor.x() + 18, anchor.y() - height / 2},
                {anchor.x() - width - 18, anchor.y() - height / 2},
                {anchor.x() - width / 2, anchor.y() - height - 18},
                {anchor.x() - width / 2, anchor.y() + 18},
                {anchor.x() + 18, anchor.y() - height - 8},
                {anchor.x() + 18, anchor.y() + 8},
                {anchor.x() - width - 18, anchor.y() - height - 8},
                {anchor.x() - width - 18, anchor.y() + 8}
        };
        ScreenPoint best = new ScreenPoint(this.viewportX + 6, this.viewportY + 6);
        int bestOverlap = Integer.MAX_VALUE;
        for (int[] placement : placements) {
            int x = Math.max(this.viewportX + 6,
                    Math.min(this.viewportX + this.viewportWidth - width - 6, placement[0]));
            int y = Math.max(this.viewportY + 6,
                    Math.min(this.viewportY + this.viewportHeight - height - 6, placement[1]));
            int overlaps = 0;
            for (ScreenPoint point : this.markerPositions.values()) {
                if (point.x() + 12 > x && point.x() - 12 < x + width
                        && point.y() + 12 > y && point.y() - 12 < y + height) overlaps++;
            }
            if (overlaps < bestOverlap) {
                best = new ScreenPoint(x, y);
                bestOverlap = overlaps;
                if (overlaps == 0) break;
            }
        }
        return best;
    }

    public Component coordinateStatus(double mouseX, double mouseY) {
        double x = insideViewport(mouseX, mouseY) ? screenToWorldX(mouseX) : this.centerX;
        double z = insideViewport(mouseX, mouseY) ? screenToWorldZ(mouseY) : this.centerZ;
        return Component.translatable("gui.createcityexpansioncore.world_map.coordinates",
                (int) Math.floor(x), (int) Math.floor(z), (int) Math.round(this.zoom * 100.0));
    }

    public void selectAndCenter(Selection value) {
        if (value == null) return;
        setSelection(value);
        centerOn(value.pos());
    }

    public void setRelations(List<CityRelationSnapshot> values) {
        Map<UUID, CityRelationSnapshot.Status> next = new HashMap<>();
        for (CityRelationSnapshot value : values) next.put(value.cityId(), value.status());
        this.relations = Map.copyOf(next);
    }

    public List<Selection> search(String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);

        List<Selection> matches = new ArrayList<>();
        for (CityMapPeersPayload.Peer peer : CityMapCities.matching(this.peers, query)) {
            matches.add(peerSelection(peer));
        }
        for (RailwayStationSnapshot station : this.railwayStations) {
            if (station.dimension().equals(this.dimensionId)
                    && station.name().toLowerCase(Locale.ROOT).contains(query)) matches.add(stationSelection(station));
        }
        for (RailwayTrainSnapshot train : this.railwayTrains) {
            if (train.dimension() != null && train.dimension().equals(this.dimensionId)
                    && train.name().toLowerCase(Locale.ROOT).contains(query)) matches.add(trainSelection(train));
        }
        for (RailwayTrainCandidate train : this.railwayTrainCandidates) {
            if (train.dimension() != null && train.dimension().equals(this.dimensionId)
                    && train.name().toLowerCase(Locale.ROOT).contains(query)) matches.add(unregisteredTrainSelection(train));
        }
        for (CityProjectSnapshot project : this.projects) {
            String translated = Component.translatable("gui.createcityexpansioncore.project.industrial_mine").getString();
            if (this.dimensionId.equals(project.dimensionId())
                    && translated.toLowerCase(Locale.ROOT).contains(query)) matches.add(projectSelection(project));
        }
        for (EconomyStatePayload.InfrastructureView value : this.economyInfrastructure) {
            Component name = infrastructureName(value);
            if (this.dimensionId.equals(value.dimension())
                    && name.getString().toLowerCase(Locale.ROOT).contains(query)) matches.add(infrastructureSelection(value));
        }
        for(FreightPoint point:this.freightPoints)if(point.dimension.equals(this.dimensionId)&&point.name.toLowerCase(Locale.ROOT).contains(query))matches.add(freightSelection(point));
        for (var section : this.railwaySections) {
            Selection value = sectionSelection(section);
            if (!section.segments().isEmpty() && value.title().getString().toLowerCase(Locale.ROOT).contains(query)) matches.add(value);
        }
        for (var route : this.railwayRoutes) {
            Selection value = routeSelection(route);
            if (!route.segments().isEmpty() && value.title().getString().toLowerCase(Locale.ROOT).contains(query)) matches.add(value);
        }
        for (var candidate : this.freightCandidates) {
            Selection value = discoverySelection(candidate);
            if (value.title().getString().toLowerCase(Locale.ROOT).contains(query)) matches.add(value);
        }
        for (int index = 0; index < this.personalPins.size(); index++) {
            Selection pin = pinSelection(index);
            if (pin.title().getString().toLowerCase(Locale.ROOT).contains(query)) matches.add(pin);
        }
        matches.sort(Comparator.comparing((Selection value) -> value.title().getString().toLowerCase(Locale.ROOT))
                .thenComparing(value -> value.kind().name()).thenComparingLong(value -> value.pos().asLong()));
        return List.copyOf(matches);
    }

    public void centerOn(BlockPos pos) {
        animateTo(pos.getX()+.5, pos.getZ()+.5, zoom);
    }

    public void setEconomyInfrastructure(List<EconomyStatePayload.InfrastructureView> values) {
        if (this.economyInfrastructure.equals(values)) {
            return;
        }
        this.economyInfrastructure = List.copyOf(values);
    }

    public void setFreightData(CompoundTag logistics) {
        if (logistics == null) { this.freightPoints=List.of();this.freightLinks=List.of();this.freightCandidates=List.of();this.routeStrategies=Map.of();return; }
        List<FreightPoint> points=new ArrayList<>();for(Tag raw:logistics.getList("Points",Tag.TAG_COMPOUND))try{FreightPoint point=FreightPoint.load((CompoundTag)raw);if(!point.removed)points.add(point);}catch(RuntimeException ignored){}
        List<FreightLink> links=new ArrayList<>();Map<UUID,String> strategies=new HashMap<>();for(Tag raw:logistics.getList("Assignments",Tag.TAG_COMPOUND)){CompoundTag value=(CompoundTag)raw;if(value.hasUUID("Subject")&&value.hasUUID("Source")&&value.hasUUID("Destination"))links.add(new FreightLink(value.getUUID("Subject"),value.getUUID("Source"),value.getUUID("Destination"),value.getString("State")));if(value.hasUUID("RouteSelection")&&!value.getString("RouteStrategy").isBlank())strategies.put(value.getUUID("RouteSelection"),value.getString("RouteStrategy"));}
        List<FreightCandidate> candidates=new ArrayList<>();for(Tag raw:logistics.getList("Candidates",Tag.TAG_COMPOUND)){CompoundTag value=(CompoundTag)raw;candidates.add(new FreightCandidate(BlockPos.of(value.getLong("Postbox")),BlockPos.of(value.getLong("Packager")),value.getString("Station")));}
        this.freightPoints=List.copyOf(points);this.freightLinks=List.copyOf(links);this.freightCandidates=List.copyOf(candidates);this.routeStrategies=Map.copyOf(strategies);
    }

    public void setRailwayData(
            List<RailwayStationSnapshot> stations,
            List<RailwayTrainSnapshot> trains
    ) {
        if (this.railwayStations.equals(stations) && this.railwayTrains.equals(trains)) {
            return;
        }
        Map<UUID, TrainPosition> current = new HashMap<>();
        for (RailwayTrainSnapshot train : this.railwayTrains) {
            if (train.dimension() != null) {
                current.put(train.fleetTrainId(), new TrainPosition(train.dimension(), train.x(), train.z()));
            }
        }
        this.previousTrainPositions.clear();
        this.previousTrainPositions.putAll(current);
        this.railwayStations = List.copyOf(stations);
        this.railwayTrains = List.copyOf(trains);
        this.railwaySnapshotAtMillis = System.currentTimeMillis();
    }

    /**
     * Create trains that exist but are not registered in CCEC. Kept separate from
     * {@link #setRailwayData} because registering a train changes this list without changing the
     * registered one, and the early-return above would otherwise swallow the update.
     */
    public void setUnregisteredTrains(List<RailwayTrainCandidate> candidates) {
        List<RailwayTrainCandidate> copy = List.copyOf(candidates);
        if (this.railwayTrainCandidates.equals(copy)) return;
        this.railwayTrainCandidates = copy;
    }

    public boolean receive(RailwayMapPayload payload) {
        if (!this.pylonPos.equals(payload.pylonPos())
                || !this.dimensionId.equals(payload.dimension())) {
            return false;
        }
        this.railwayTracks = List.copyOf(payload.segments());
        this.railwayNetworks=List.copyOf(payload.networks());this.railwaySections=List.copyOf(payload.sections());this.railwayRoutes=List.copyOf(payload.routes());
        return true;
    }

    /**
     * Масштаб интерфейса. Панель рисуется внутри pose().scale(...),
     * а GuiGraphics.enableScissor ожидает экранные координаты.
     */
    public void setGuiScale(float guiScale) {
        this.guiScale = guiScale <= 0.0F ? 1.0F : guiScale;
    }

    private float guiScale = 1.0F;

    public void render(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY
    ) {
        this.viewportX = x;
        this.viewportY = y;
        this.viewportWidth = width;
        this.viewportHeight = Math.max(1, this.drawInternalControls ? height - 19 : height);
        this.centerButtonX = x + width - 58;
        this.centerButtonY = y + height - 15;
        this.layerButtonsX = x + width - 166;
        this.layerButtonsY = y + 4;
        updateMotion();
        layoutMarkers();
        updateHover(insideViewport(mouseX, mouseY) ? findSelection(mouseX, mouseY) : null);
        this.hoveredPlot = plotAtScreen(mouseX, mouseY);
        this.hoveredPlotClaimable = canRequestPlot(this.hoveredPlot);
        // Флаг сбрасывается здесь, а не в конце отрисовки: слои идут после
        // этой точки, и именно они наводят подсказку.
        this.tooltipShown = false;

        guiGraphics.fill(x - 1, y - 1, x + width + 1, y + viewportHeight + 1, FRAME_COLOR);
        guiGraphics.fill(x, y, x + width, y + viewportHeight, MapPalette.PAPER);

        requestVisibleTiles();

        com.example.examplemod.client.screen.CityPylonWorkspace.clip(guiGraphics,
                Math.round(x * this.guiScale),
                Math.round(y * this.guiScale),
                Math.round((x + width) * this.guiScale),
                Math.round((y + this.viewportHeight) * this.guiScale));
        try {
            /*
             * GuiGraphics вызывает endBatch() после КАЖДОЙ fill()/blit(), пока блок
             * не помечен managed. Раньше карта была десятками тысяч заливок — по одной
             * на каждый сэмпл каждого тайла; теперь тайл это один blit готовой
             * текстуры, а оверлеи поверх идут в том же батче.
             * Scissor меняется уже вне блока, порядок с текстом сохраняется.
             */
            guiGraphics.drawManaged(() -> {
                renderTerrain(guiGraphics);
                if (this.focus == Focus.RAILWAY) {
                    guiGraphics.fill(x, y, x + width, y + this.viewportHeight, 0x24101618);
                } else if (this.focus == Focus.TRADE) {
                    guiGraphics.fill(x, y, x + width, y + this.viewportHeight, 0x16101618);
                } else if (this.focus == Focus.ENERGY) {
                    guiGraphics.fill(x, y, x + width, y + this.viewportHeight, 0x32101618);
                }
                if (this.showCoordinateGrid) renderGrid(guiGraphics);
                renderSnapshotNotice(guiGraphics);
                if (this.showTerritory && this.showPlotGrid && this.plotClaimRequest != null) renderPlotGrid(guiGraphics);
                // Соседи рисуются ДО своей территории: собственный город
                // остаётся главным акцентом, чужие — фоном.
                if (this.showTerritory || this.showCities || this.projectPicking) renderPeers(guiGraphics, mouseX, mouseY);
                if (this.showTerritory || this.projectPicking) {
                    if(projectPicking)renderConstructionMask(guiGraphics);
                    renderTerritory(guiGraphics);
                    if (!projectFocus) renderClaimHint(guiGraphics);
                }
                renderCityTooltips(guiGraphics, mouseX, mouseY);
                renderRailway(guiGraphics, mouseX, mouseY);
                renderMarkers(guiGraphics, mouseX, mouseY);
                // Placement previews and project markers must stay visible over city/player icons.
                if (this.visualOverlay != null) this.visualOverlay.accept(guiGraphics);
                renderPersonalTools(guiGraphics,mouseX,mouseY);
                if (placingPin && insideViewport(mouseX,mouseY)) {
                    guiGraphics.renderOutline(mouseX-4,mouseY-4,9,9,MapPalette.SELECTED);
                    var label=Component.literal((int)Math.floor(screenToWorldX(mouseX))+" / "+(int)Math.floor(screenToWorldZ(mouseY)));
                    var font=Minecraft.getInstance().font;
                    int lx=Math.max(viewportX+3,Math.min(mouseX+10,viewportX+viewportWidth-font.width(label)-4));
                    int ly=Math.max(viewportY+3,Math.min(mouseY+12,viewportY+viewportHeight-12));
                    guiGraphics.fill(lx-3,ly-3,lx+font.width(label)+3,ly+11,MapPalette.PAPER_SUNK);
                    guiGraphics.drawString(font,label,lx,ly,MapPalette.TEXT,false);
                }
                if (motionEnabled && !reducedMotion && selection!=null) {
                    double t=(System.nanoTime()-selectionStarted)/400_000_000.0;
                    if(t>=0 && t<1) {
                        ScreenPoint selectedPoint=markerPoint(selection.kind(),selection.id(),selection.pos());
                        int sx=selectedPoint.x(), sy=selectedPoint.y();
                        int r=12+(int)(12*MapMotion.ease(t)), color=((int)(170*(1-t))<<24)|(MapPalette.SELECTED&0xFFFFFF);
                        for(int a=0;a<48;a++) { double angle=a*Math.PI/24; int px=sx+(int)Math.round(Math.cos(angle)*r),py=sy+(int)Math.round(Math.sin(angle)*r); guiGraphics.fill(px,py,px+1,py+1,color); }
                    }
                }
            });
        } finally {
            guiGraphics.disableScissor();
        }

        if (this.drawInternalControls) {
            renderFooter(guiGraphics, x, y + viewportHeight + 4, width, mouseX, mouseY);
        }

        /*
         * Приборная обвязка рисуется после снятия scissor: её
         * элементы лежат на полях вьюпорта и не должны обрезаться
         * по его границе. Геометрия считается здесь же, поэтому
         * экран-атлас может поднять легенду, зная её размер.
         */
        this.chromeGeometry = CityMapFrame.Geometry.of(
                x,
                y,
                viewportWidth,
                viewportHeight,
                this.chromeAvoidRight,
                frameLegendEntries().size()
        );
        CityMapFrame.render(
                guiGraphics,
                this.chromeGeometry,
                frameContext(), this.drawInternalControls, this.drawInternalControls || this.showOverview
        );

        /*
         * Подсказки рисуются после снятия scissor:
         * их не режет область карты и они всегда поверх карты.
         *
         * <p>Раньше они рисовались только вместе с внутренними элементами
         * управления. Но атлас отключает их (у него своя панель), и вместе с
         * ними пропадали все подсказки: наведение на чужой город не давало
         * ничего. Подсказка — часть карты, а не её приборной панели, поэтому
         * она рисуется всегда.
         */
        if (this.pendingTooltip != null) {
            Component[] lines = this.pendingTooltip;
            this.pendingTooltip = null;
            renderClampedTooltip(guiGraphics, lines);
        } else {
            this.pendingTooltip = null;
        }

        /*
         * Подтверждение claim рисуется последним: оно поверх подсказки,
         * иначе подсказка перекрывала бы кнопки.
         */
        renderClaimOverlay(guiGraphics, mouseX, mouseY);
    }

    public boolean receive(CityMapTilePayload payload) {
        if (!this.pylonPos.equals(payload.pylonPos())
                || !this.dimensionId.equals(payload.dimensionId())) {
            return false;
        }

        TileKey key=new TileKey(payload.dimensionId(),payload.tileX(),payload.tileZ(),payload.level());
        this.requestedTiles.remove(key);this.failedTiles.remove(key);this.tileAttempts.remove(key);
        if(!terrainWorld.equals(payload.worldId())) {
            boolean mismatch=this.tileCache.values().stream().anyMatch(t->!t.worldId().equals(payload.worldId()));
            terrainWorld=payload.worldId();if(mismatch)TerrainCache.clearTextures(this.terrainStore);
            this.tileCache.entrySet().removeIf(e->!e.getValue().worldId().equals(terrainWorld));
            if(mismatch){this.terrainTextures.values().forEach(TerrainTileTexture::close);this.terrainTextures.clear();}
        }
        CachedTile previous=cachedTerrain(key);long now=System.currentTimeMillis();
        if(payload.unchanged()) {
            if(previous!=null)this.tileCache.put(key,new CachedTile(previous.colors(),previous.contours(),previous.complete(),now,previous.heights(),previous.revision(),previous.worldId()));
            return true;
        }
        this.tileCache.put(key,new CachedTile(payload.colors(),payload.contours(),payload.complete(),now,payload.heights(),payload.revision(),payload.worldId()));
        terrainStore().put(payload.tileX(),payload.tileZ(),payload.level(),new TerrainTileStore.Tile(payload.colors(),payload.contours(),payload.complete(),now,payload.heights(),payload.revision(),payload.worldId()));
        TerrainTileTexture texture=this.terrainTextures.get(key);
        if(previous==null||previous.revision()!=payload.revision()) {
            if(texture!=null)texture.invalidate();
            TerrainCache.invalidate(this.terrainStore,key.tileX(),key.tileZ(),key.level());
        }
        evictTerrainTiles();
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (insideViewport(mouseX,mouseY)) { stopMotion(); dragAt=0; }
        /*
         * Подтверждение claim владеет кликами, пока открыто: карта под ним
         * не панорамируется и не выбирает объекты, иначе подтверждение
         * можно было бы случайно отменить перетаскиванием.
         */
        if (this.pendingClaim != null) {
            return handleClaimOverlayClick(mouseX, mouseY, button);
        }
        if (button == 1) {
            /*
             * Правая кнопка на карте — это запрос участка, а не панорама.
             *
             * <p>Запрос открывает подтверждение ВНУТРИ карты: игрок видит
             * участок и соседнюю территорию, пока решает. Раньше
             * открывался отдельный экран поверх всей игры, и участок на
             * карте переставал быть виден.
             *
             * <p>Ограничения проверяются на клике, но окончательное
             * решение принимает сервер.
             */
            if (!insideViewport(mouseX, mouseY)) {
                LOGGER.info("City plot claim ignored: click outside the map viewport");
                return false;
            }
            CityPlot plot = plotAtScreen(mouseX, mouseY);
            boolean claimable = canRequestPlot(plot);
            if (this.plotClaimRequest == null || plot == null || !claimable) {
                logClaimAttempt(plot, claimable, false);
                return false;
            }
            setPendingClaim(plot);
            return true;
        }
        if (button != 0) {
            return false;
        }
        if (insideViewport(mouseX, mouseY) && (this.placingPin || this.measuring)) {
            BlockPos point = BlockPos.containing(screenToWorldX(mouseX), 0, screenToWorldZ(mouseY));
            if (this.placingPin) {
                if (this.personalPins.size() < AtlasPreferences.MAX_PINS) {
                    this.personalPins.add(new AtlasPreferences.Pin(point.getX(), point.getZ()));
                    setSelection(pinSelection(this.personalPins.size() - 1));
                }
                this.placingPin = false;
            } else if (this.measureStart == null || this.measureEnd != null) {
                this.measureStart = point;
                this.measureEnd = null;
            } else this.measureEnd = point;
            return true;
        }
        for (int index = 0; insideViewport(mouseX, mouseY) && index < this.personalPins.size(); index++) {
            Selection pin = pinSelection(index);
            if (inside(mouseX, mouseY, worldToScreenX(pin.pos().getX()) - 8,
                    worldToScreenY(pin.pos().getZ()) - 8, 16, 16)) { setSelection(pin); return true; }
        }
        Selection hit = findSelection(mouseX, mouseY);
        if (hit != null) {
            setSelection(hit);
            return true;
        }
        if (this.drawInternalControls && inside(mouseX, mouseY, centerButtonX, centerButtonY, 54, 12)) {
            centerOnPylon();
            return true;
        }
        if (this.drawInternalControls && inside(mouseX, mouseY, layerButtonsX, layerButtonsY, 30, 12)) {
            this.showTerritory = !this.showTerritory;
            return true;
        }
        if (this.drawInternalControls && inside(mouseX, mouseY, layerButtonsX + 34, layerButtonsY, 30, 12)) {
            this.showProjects = !this.showProjects;
            return true;
        }
        if (this.drawInternalControls && inside(mouseX, mouseY, layerButtonsX + 68, layerButtonsY, 30, 12)) {
            this.showTracks = !this.showTracks;
            return true;
        }
        if (this.drawInternalControls && inside(mouseX, mouseY, layerButtonsX + 102, layerButtonsY, 30, 12)) {
            this.showStations = !this.showStations;
            return true;
        }
        if (this.drawInternalControls && inside(mouseX, mouseY, layerButtonsX + 136, layerButtonsY, 30, 12)) {
            this.showTrains = !this.showTrains;
            return true;
        }
        if (insideViewport(mouseX, mouseY)) {
            this.selection = null;
            this.dragging = true;
            return true;
        }
        return false;
    }

    private Selection findSelection(double mouseX, double mouseY) {
        if(this.showTracks){Selection route=findRouteSelection(mouseX,mouseY);if(route!=null)return route;Selection section=findSectionSelection(mouseX,mouseY);if(section!=null)return section;}
        Selection player = playerSelection();
        if (player != null) {
            ScreenPoint point = markerPoint(player.kind(), player.id(), player.pos());
            if (inside(mouseX, mouseY, point.x() - 13, point.y() - 13, 26, 26)) return player;
        }
        if (this.showTradeInfrastructure && this.zoom >= MIN_ZOOM) for (EconomyStatePayload.InfrastructureView value : this.economyInfrastructure) {
            if (!value.cityOwned() || !this.dimensionId.equals(value.dimension())) continue;
            Selection candidate = infrastructureSelection(value);
            ScreenPoint point = markerPoint(candidate.kind(), candidate.id(), candidate.pos());
            if (inside(mouseX, mouseY, point.x() - 11, point.y() - 11, 22, 22)) return candidate;
        }
        if(this.showTradeInfrastructure&&this.zoom>=MIN_ZOOM)for(FreightPoint point:this.freightPoints){if(point.dimension.equals(this.dimensionId)){Selection candidate=freightSelection(point);ScreenPoint marker=markerPoint(candidate.kind(),candidate.id(),candidate.pos());if(inside(mouseX,mouseY,marker.x()-11,marker.y()-11,22,22))return candidate;}}
        if(this.showTradeInfrastructure&&this.zoom>=MIN_ZOOM)for(FreightCandidate value:this.freightCandidates){int x=worldToScreenX(value.postbox.getX()+.5),y=worldToScreenY(value.postbox.getZ()+.5);if(inside(mouseX,mouseY,x-8,y-9,16,18))return discoverySelection(value);}
        if (this.showTrains && this.zoom >= MIN_ZOOM) for (RailwayTrainSnapshot train : this.railwayTrains) {
            if (train.dimension() == null || !train.dimension().equals(this.dimensionId)) continue;
            Selection candidate = trainSelection(train);
            ScreenPoint point = markerPoint(candidate.kind(), candidate.id(), candidate.pos());
            if (inside(mouseX, mouseY, point.x() - 11, point.y() - 11, 22, 22)) return candidate;
        }
        if (this.showTrains && this.zoom >= MIN_ZOOM) for (RailwayTrainCandidate train : this.railwayTrainCandidates) {
            if (train.dimension() == null || !train.dimension().equals(this.dimensionId)) continue;
            Selection candidate = unregisteredTrainSelection(train);
            ScreenPoint point = markerPoint(candidate.kind(), candidate.id(), candidate.pos());
            if (inside(mouseX, mouseY, point.x() - 11, point.y() - 11, 22, 22)) return candidate;
        }
        if (this.showStations && this.zoom >= MIN_ZOOM) for (RailwayStationSnapshot station : this.railwayStations) {
            if (!station.dimension().equals(this.dimensionId)) continue;
            Selection candidate = stationSelection(station);
            ScreenPoint point = markerPoint(candidate.kind(), candidate.id(), candidate.pos());
            if (inside(mouseX, mouseY, point.x() - 11, point.y() - 11, 22, 22)) return candidate;
        }
        if (this.showProjects && this.zoom >= MIN_ZOOM) for (CityProjectSnapshot project : this.projects) {
            if (!this.dimensionId.equals(project.dimensionId())) continue;
            Selection candidate = projectSelection(project);
            ScreenPoint point = markerPoint(candidate.kind(), candidate.id(), candidate.pos());
            if (inside(mouseX, mouseY, point.x() - 11, point.y() - 11, 22, 22)) return candidate;
        }
        if (this.showCities) for (CityMapPeersPayload.Peer peer : this.peers) {
            if (atAnchor(peer)) continue;
            Selection candidate = peerSelection(peer);
            ScreenPoint point = markerPoint(candidate.kind(), candidate.id(), candidate.pos());
            if (insideCityMarker(mouseX, mouseY, point.x(), point.y())) return candidate;
        }
        ScreenPoint ownPoint = markerPoint(SelectionKind.CITY, null, this.pylonPos);
        if (this.showCities && hasCityAnchor() && insideCityMarker(mouseX, mouseY, ownPoint.x(), ownPoint.y())) {
            for (var peer : this.peers) if (atAnchor(peer)) return peerSelection(peer);
            if (ownPeer() != null) return peerSelection(ownPeer());
            return new Selection(SelectionKind.CITY, null,
                    Component.translatable("gui.createcityexpansioncore.map.city_pylon"), this.pylonPos, true,
                    List.of(ownTerritoryDetails()), this.cityIcon, false);
        }
        return null;
    }

    /** Карточка своего города: участки и их площадь вместо старого размера квадрата. */
    private Component ownTerritoryDetails() {
        CityMapPeersPayload.Peer own = ownPeer();
        if (own == null) {
            return Component.translatable("gui.createcityexpansioncore.world_map.context.plots", 0);
        }
        return Component.translatable("gui.createcityexpansioncore.world_map.context.plots",
                own.plots().size());
    }

    /** Repositions only screen markers. Their world positions and all map data remain untouched. */
    private void layoutMarkers() {
        List<MarkerEntry> entries = new ArrayList<>();
        if (this.showCities) {
            if (hasCityAnchor()) addMarker(entries, SelectionKind.CITY, null, this.pylonPos,
                    this.pylonPos.getX() + 0.5, this.pylonPos.getZ() + 0.5, 80);
            for (CityMapPeersPayload.Peer peer : this.peers) {
                if (!atAnchor(peer)) addMarker(entries, SelectionKind.CITY, peer.cityId(),
                        new BlockPos(peer.pylonX(), this.pylonPos.getY(), peer.pylonZ()),
                        peer.pylonX() + 0.5, peer.pylonZ() + 0.5, 80);
            }
        }
        Selection player = playerSelection();
        if (player != null) addMarker(entries, player.kind(), player.id(), player.pos(),
                Minecraft.getInstance().player.getX(), Minecraft.getInstance().player.getZ(), 100);
        if (this.showStations && this.zoom >= MIN_ZOOM) for (RailwayStationSnapshot station : this.railwayStations) {
            if (station.dimension().equals(this.dimensionId)) addMarker(entries, SelectionKind.STATION,
                    station.registrationId(), station.pos(), station.pos().getX() + 0.5,
                    station.pos().getZ() + 0.5, 70);
        }
        if (this.showTrains && this.zoom >= MIN_ZOOM) for (RailwayTrainSnapshot train : this.railwayTrains) {
            if (train.dimension() == null || !train.dimension().equals(this.dimensionId)) continue;
            TrainPosition previous = this.previousTrainPositions.get(train.fleetTrainId());
            double progress = Math.min(1.0,
                    Math.max(0.0, (System.currentTimeMillis() - this.railwaySnapshotAtMillis) / 1000.0));
            double x = previous != null && previous.dimension().equals(train.dimension())
                    ? previous.x() + (train.x() - previous.x()) * progress : train.x();
            double z = previous != null && previous.dimension().equals(train.dimension())
                    ? previous.z() + (train.z() - previous.z()) * progress : train.z();
            addMarker(entries, SelectionKind.TRAIN, train.fleetTrainId(),
                    BlockPos.containing(train.x(), 0, train.z()), x, z, 40);
        }
        if (this.showTrains && this.zoom >= MIN_ZOOM) for (RailwayTrainCandidate train : this.railwayTrainCandidates) {
            if (train.dimension() == null || !train.dimension().equals(this.dimensionId)) continue;
            addMarker(entries, SelectionKind.TRAIN_UNREGISTERED, train.createTrainId(),
                    BlockPos.containing(train.x(), 0, train.z()), train.x(), train.z(), 41);
        }
        if (this.showProjects && this.zoom >= MIN_ZOOM) for (CityProjectSnapshot project : this.projects) {
            if (this.dimensionId.equals(project.dimensionId())) addMarker(entries, SelectionKind.INDUSTRY,
                    project.projectId(), project.controllerPos(), project.controllerPos().getX() + 0.5,
                    project.controllerPos().getZ() + 0.5, 50);
        }
        if (this.showTradeInfrastructure && this.zoom >= MIN_ZOOM) {
            for (EconomyStatePayload.InfrastructureView value : this.economyInfrastructure) {
                if (value.cityOwned() && this.dimensionId.equals(value.dimension())) addMarker(entries,
                        SelectionKind.TRADE_INFRASTRUCTURE, value.id(), value.pos(),
                        value.pos().getX() + 0.5, value.pos().getZ() + 0.5, 60);
            }
        }
        if(this.showTradeInfrastructure&&this.zoom>=MIN_ZOOM)for(FreightPoint point:this.freightPoints)if(point.dimension.equals(this.dimensionId))addMarker(entries,SelectionKind.FREIGHT_POINT,point.id,point.postbox,point.postbox.getX()+.5,point.postbox.getZ()+.5,65);

        entries.sort(Comparator.comparingInt(MarkerEntry::priority).reversed()
                .thenComparingInt(entry -> entry.key().kind().ordinal())
                .thenComparing(entry -> entry.key().stableId()));
        this.markerPositions.clear();
        List<ScreenPoint> placed = new ArrayList<>(entries.size());
        for (MarkerEntry entry : entries) {
            ScreenPoint anchor = new ScreenPoint(worldToScreenX(entry.worldX()), worldToScreenY(entry.worldZ()));
            ScreenPoint selected = anchor;
            for (int[] offset : MARKER_OFFSETS) {
                ScreenPoint candidate = new ScreenPoint(anchor.x() + offset[0], anchor.y() + offset[1]);
                if (candidate.x() < this.viewportX + 12 || candidate.x() > this.viewportX + this.viewportWidth - 12
                        || candidate.y() < this.viewportY + 12 || candidate.y() > this.viewportY + this.viewportHeight - 12) {
                    continue;
                }
                boolean collision = false;
                for (ScreenPoint other : placed) {
                    if (Math.abs(candidate.x() - other.x()) < MARKER_SPACING
                            && Math.abs(candidate.y() - other.y()) < MARKER_SPACING) {
                        collision = true;
                        break;
                    }
                }
                if (!collision) {
                    selected = candidate;
                    break;
                }
            }
            this.markerPositions.put(entry.key(), selected);
            placed.add(selected);
        }
    }

    private void addMarker(List<MarkerEntry> entries, SelectionKind kind, UUID id, BlockPos pos,
                           double worldX, double worldZ, int basePriority) {
        int x = worldToScreenX(worldX);
        int y = worldToScreenY(worldZ);
        if (x < this.viewportX - 40 || x > this.viewportX + this.viewportWidth + 40
                || y < this.viewportY - 40 || y > this.viewportY + this.viewportHeight + 40) return;
        MarkerKey key = new MarkerKey(kind, id, pos.immutable());
        entries.add(new MarkerEntry(key, worldX, worldZ, basePriority));
    }

    private ScreenPoint markerPoint(SelectionKind kind, UUID id, BlockPos pos) {
        return markerPosition(kind, id, pos,
                worldToScreenX(pos.getX() + 0.5), worldToScreenY(pos.getZ() + 0.5));
    }

    private ScreenPoint markerPosition(SelectionKind kind, UUID id, BlockPos pos, int anchorX, int anchorY) {
        return this.markerPositions.getOrDefault(new MarkerKey(kind, id, pos.immutable()),
                new ScreenPoint(anchorX, anchorY));
    }

    private void updateHover(Selection candidate) {
        long now = System.currentTimeMillis();
        if (!sameTarget(candidate, this.hoverCandidate)) {
            this.hoverCandidate = candidate;
            this.hoverCandidateSince = now;
            this.hoveredSelection = null;
        } else if (candidate != null && now - this.hoverCandidateSince >= HOVER_DELAY_MILLIS) {
            this.hoveredSelection = candidate;
        }
        if (candidate == null) this.hoveredSelection = null;
    }

    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        if (!this.dragging || button != 0) {
            return false;
        }
        long now=System.nanoTime();
        double dt=dragAt==0?1.0/60:Math.clamp((now-dragAt)/1e9,.008,.05); dragAt=now;
        velocityX=Math.clamp(-dragX/zoom/dt,-1800/zoom,1800/zoom);
        velocityZ=Math.clamp(-dragY/zoom/dt,-1800/zoom,1800/zoom);
        this.centerX -= dragX / this.zoom;
        this.centerZ -= dragY / this.zoom;
        clampCenter();
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.dragging) {
            this.dragging = false;
            if (System.nanoTime()-dragAt>80_000_000L) velocityX=velocityZ=0;
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!insideViewport(mouseX, mouseY) || scrollY == 0.0) {
            return false;
        }

        double worldXBefore = screenToWorldX(mouseX);
        double worldZBefore = screenToWorldZ(mouseY);
        double factor = scrollY > 0.0 ? ZOOM_STEP : 1.0 / ZOOM_STEP;
        double newZoom = clamp((cameraMoving && anchoredZoom ? toZoom : this.zoom) * factor, MIN_ZOOM, MAX_ZOOM);
        if (newZoom == this.zoom) {
            return true;
        }

        if (motionEnabled && !reducedMotion && !preciseTool()) {
            animateTo(centerX,centerZ,newZoom);
            anchoredZoom=true; zoomAnchorX=worldXBefore; zoomAnchorZ=worldZBefore;
            zoomMouseX=mouseX; zoomMouseY=mouseY; return true;
        }
        stopMotion();
        this.zoom = newZoom;
        this.centerX += worldXBefore - screenToWorldX(mouseX);
        this.centerZ += worldZBefore - screenToWorldZ(mouseY);
        clampCenter();
        return true;
    }

    private int terrainLevel(){return AtlasMapScale.terrainLevel(this.zoom,viewportWidth,viewportHeight,
            Minecraft.getInstance().getWindow().getGuiScale()*this.guiScale);}
    private int terrainCoordinate(double world,int level){return (int)Math.floor(world/AtlasMapScale.span(level));}
    private void renderTerrain(GuiGraphics guiGraphics) {
        TerrainAtlas.begin();
        int level=terrainLevel(),budget=MAX_TILE_REBUILDS_PER_FRAME,fallbackLevel=-1;
        long until=System.nanoTime()+2_000_000L;
        // Pin existing visible samples before new uploads can reuse their atlas slots.
        for(var entry:this.terrainTextures.entrySet()) {
            TileKey key=entry.getKey();
            double span=key.span();
            if((key.tileX()+1)*span<screenToWorldX(viewportX)||key.tileX()*span>screenToWorldX(viewportX+viewportWidth)
                    ||(key.tileZ()+1)*span<screenToWorldZ(viewportY)||key.tileZ()*span>screenToWorldZ(viewportY+viewportHeight))continue;
            if(key.level()<level) {
                if(entry.getValue().id()!=null)fallbackLevel=Math.max(fallbackLevel,key.level());
                continue;
            }
            entry.getValue().pin();
        }
        // Prepare cached overview first, within the same time/count budget as detailed uploads.
        for(int lod=7;lod>=level;lod--) {
            int minX=terrainCoordinate(screenToWorldX(viewportX),lod),maxX=terrainCoordinate(screenToWorldX(viewportX+viewportWidth),lod);
            int minZ=terrainCoordinate(screenToWorldZ(viewportY),lod),maxZ=terrainCoordinate(screenToWorldZ(viewportY+viewportHeight),lod);
            for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++) {
                if(System.nanoTime()>until)budget=0;
                budget=renderTerrainTile(guiGraphics,new TileKey(this.dimensionId,x,z,lod),1,budget);
            }
        }
        int minX=terrainCoordinate(screenToWorldX(viewportX),level),maxX=terrainCoordinate(screenToWorldX(viewportX+viewportWidth),level);
        int minZ=terrainCoordinate(screenToWorldZ(viewportY),level),maxZ=terrainCoordinate(screenToWorldZ(viewportY+viewportHeight),level);
        // A cropped parent only fills a missing/partial child: never redraw eight full-screen LODs.
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++) {
            TileKey key=new TileKey(dimensionId,x,z,level);
            TerrainTileTexture exact=terrainTextures.get(key);CachedTile raw=tileCache.get(key);
            if(exact!=null&&exact.id()!=null&&raw!=null&&raw.complete())continue;
            for(int parent=level+1;parent<=7;parent++) {
                int factor=1<<(parent-level);
                var texture=terrainTextures.get(new TileKey(dimensionId,Math.floorDiv(x,factor),Math.floorDiv(z,factor),parent));
                if(texture==null||texture.id()==null)continue;
                float size=32f/factor;
                queueTerrain(texture,key,Math.floorMod(x,factor)*size,Math.floorMod(z,factor)*size,size);
                break;
            }
        }
        TerrainAtlas.flush(guiGraphics);
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++) {
            TileKey key=new TileKey(dimensionId,x,z,level);var texture=terrainTextures.get(key);
            if(texture!=null&&texture.id()!=null)queueTerrain(texture,key,0,0,32);
        }
        TerrainAtlas.flush(guiGraphics);
        // Briefly retain finer already-loaded samples when zooming out into an uncached overview.
        int retained=0;
        for(var entry:terrainTextures.entrySet()) {
            TileKey key=entry.getKey();if(key.level()!=fallbackLevel||entry.getValue().id()==null)continue;
            int factor=1<<(level-key.level());var target=terrainTextures.get(new TileKey(dimensionId,
                    Math.floorDiv(key.tileX(),factor),Math.floorDiv(key.tileZ(),factor),level));
            if(target!=null&&target.id()!=null)continue;
            if(queueTerrain(entry.getValue(),key,0,0,32)&&++retained>=128)break;
        }
        TerrainAtlas.flush(guiGraphics);
    }
    private int renderTerrainTile(GuiGraphics g,TileKey key,int stride,int budget) {
        CachedTile tile=cachedTerrain(key);
        if(tile==null)return budget;
        TerrainTileTexture texture=this.terrainTextures.computeIfAbsent(key,ignored->{
            TerrainTileTexture saved=TerrainCache.take(terrainStore(),key.tileX(),key.tileZ(),key.level());
            return saved!=null?saved:new TerrainTileTexture("ccec_city_map/"+this.dimensionId.getNamespace()+"_"+this.dimensionId.getPath()+"_"+key.level()+"_"+key.tileX()+"_"+key.tileZ());
        });
        if(!texture.matches(stride)) {
            if(budget>0){budget--;texture.build(tile.colors(),tile.contours(),stride,tile.heights(),key.level());}
            else if(texture.id()==null)return budget;
        }
        return budget;
    }
    private boolean queueTerrain(TerrainTileTexture texture,TileKey key,float u,float v,float size) {
        int left=worldToScreenX((double)key.tileX()*key.span());
        int top=worldToScreenY((double)key.tileZ()*key.span());
        int right=worldToScreenX((double)(key.tileX()+1)*key.span());
        int bottom=worldToScreenY((double)(key.tileZ()+1)*key.span());
        if(right<=viewportX||left>=viewportX+viewportWidth||bottom<=viewportY||top>=viewportY+viewportHeight)return false;
        texture.draw(left,top,right,bottom,u,v,size);
        return true;
    }

    /**
     * Returns textures to the bounded shared pool and schedules a raw-data disk checkpoint.
     * Reopening the same world reuses both samples and already uploaded textures.
     */
    public void releaseTerrainTextures() {
        if (this.terrainStore != null) this.terrainStore.flush();
        this.terrainTextures.forEach((key, texture) -> TerrainCache.keep(this.terrainStore, key.tileX(), key.tileZ(),key.level(), texture));
        this.terrainTextures.clear();
    }

    /**
     * Caps how many tiles this client holds. Anything beyond the cap is dropped oldest-first, and
     * the texture is closed with it, so panning across a large explored world cannot grow without
     * bound. Dropped tiles are simply re-requested when they come back into view.
     */
    private void evictTerrainTiles() {
        while (this.tileCache.size() > MAX_CACHED_TILES) {
            var entries = this.tileCache.entrySet().iterator();
            TileKey oldest = entries.next().getKey(); entries.remove();
            TerrainTileTexture texture = this.terrainTextures.remove(oldest);
            if (texture != null) {
                texture.close();
            }
        }
    }

    /**
     * Level of detail for the terrain textures.
     * <p>
     * One sample per world block means a sample is only smaller than two screen pixels when the map
     * is zoomed out; past that point the tile texture is rebuilt once with a coarser stride instead
     * of drawing a smaller square for every sample.
     *
     * @return 1 — one texel per sample, больше — area-averaged blocks
     */
    private int terrainStride() {
        return terrainStrideFor(CityMapTilePayload.SAMPLE_STEP * this.zoom);
    }

    /**
     * Pure level-of-detail rule: how many samples one texel covers.
     *
     * <p>Split out so the LOD ladder is verified headless rather than only by looking at the map.
     * Zoomed in, {@code screenStep} is already a pixel or more per sample and the answer is 1 — every
     * block gets its own texel. Zoomed out the stride doubles until a texel covers at least two
     * pixels, which is the point where more detail would be invisible.
     */
    static int terrainStrideFor(double screenStep) {
        if (screenStep >= MIN_SAMPLE_SCREEN_PX) {
            return 1;
        }
        int stride = 1;
        while (stride < CityMapTilePayload.SAMPLE_COUNT && stride * screenStep < MIN_SAMPLE_SCREEN_PX) {
            stride <<= 1;
        }
        return stride;
    }

    private void renderMissingTile(GuiGraphics guiGraphics, TileKey key, boolean requested) {
        int originX = key.tileX() * CityMapTilePayload.WORLD_SIZE;
        int originZ = key.tileZ() * CityMapTilePayload.WORLD_SIZE;
        int left = worldToScreenX(originX);
        int top = worldToScreenY(originZ);
        int right = worldToScreenX(originX + CityMapTilePayload.WORLD_SIZE);
        int bottom = worldToScreenY(originZ + CityMapTilePayload.WORLD_SIZE);
        int parity = (key.tileX() + key.tileZ()) & 1;
        /*
         * Незаведённый тайл — это бумага, а не пустота: по нему
         * видно, что «сюда ещё не смотрели», и он не спорит с
         * линиями, которые на нём нарисованы.
         */
        int color = MapSurveyTransform.unexploredPaper(parity);
        guiGraphics.fill(left, top, right, bottom, color);
    }

    private void renderGrid(GuiGraphics guiGraphics) {
        double minWorldX = screenToWorldX(viewportX);
        double maxWorldX = screenToWorldX(viewportX + viewportWidth);
        double minWorldZ = screenToWorldZ(viewportY);
        double maxWorldZ = screenToWorldZ(viewportY + viewportHeight);
        int gridStep=32;while(gridStep*this.zoom<32)gridStep*=2;
        int firstX=(int)Math.floor(minWorldX/gridStep)*gridStep;
        int firstZ=(int)Math.floor(minWorldZ/gridStep)*gridStep;

        for (int worldX = firstX; worldX <= maxWorldX; worldX += gridStep) {
            int screenX = worldToScreenX(worldX);
            guiGraphics.vLine(screenX, viewportY, viewportY + viewportHeight, MapPalette.INK_GRID);
        }
        for (int worldZ = firstZ; worldZ <= maxWorldZ; worldZ += gridStep) {
            int screenY = worldToScreenY(worldZ);
            guiGraphics.hLine(viewportX, viewportX + viewportWidth, screenY, MapPalette.INK_GRID);
        }
    }

    /**
     * Рисует соседние города: территорию, метку пилона и название.
     *
     * <p>Использует тот же world→screen transform, что и остальная
     * карта: второго координатного пространства не заводится.
     *
     * <p>Каждый город проверяется на попадание в текущий вьюпорт
     * и пропускается, если он целиком за кадром. Это и есть
     * clipping: за кадром не рисуется ничего, а данные уже отфильтрованы
     * сервером по дистанции, поэтому ни обхода мира, ни выборки
     * чанков в цикле кадра не происходит.
     */
    private boolean insideCityMarker(double mx,double my,int x,int y) {
        int r=TerritoryPresentation.cityRadius(this.zoom);return inside(mx,my,x-r,y-r,r*2,r*2);
    }
    private void renderCityMarker(GuiGraphics g,int x,int y,boolean owned,boolean hover,boolean selected) {
        int r=TerritoryPresentation.cityRadius(this.zoom),color=selected?MapPalette.SELECTED:hover?0xFFE0C395:owned?MapPalette.OWN:MapPalette.NEUTRAL;
        g.fill(x-r,y-r,x+r,y+r,0xEE172025);g.renderOutline(x-r,y-r,r*2,r*2,color);
        float scale=(r-3)/8F;
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);
        MapMarkerRenderer.drawGlyph(g,AtlasGlyph.CITY,-8,-8,color);g.pose().popPose();
    }
    private final List<int[]> cityLabelBounds=new ArrayList<>();
    private void reserveCityLabel(int x,int y,int width) { cityLabelBounds.add(new int[]{x-3,y-3,x+width+3,y+11}); }
    private boolean cityLabelFits(int x,int y,int width) {
        if(cityLabelBounds.size()>=24)return false;
        for(int[] r:cityLabelBounds) if(x-3<r[2]&&x+width+3>r[0]&&y-3<r[3]&&y+11>r[1])return false;
        reserveCityLabel(x,y,width);return true;
    }
    private void renderPeers(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        cityLabelBounds.clear();
        var labelFont=Minecraft.getInstance().font;
        if(showCities&&hasCityAnchor()) {
            ScreenPoint anchor=markerPoint(SelectionKind.CITY,null,pylonPos);
            String name=peers.stream().filter(this::atAnchor).findFirst().map(CityMapPeersPayload.Peer::name).orElse("");
            reserveCityLabel(anchor.x()+TerritoryPresentation.cityRadius(zoom)+4,anchor.y()-4,labelFont.width(name));
        }
        for(var peer:peers) if(matchesTargetAt(selection,SelectionKind.CITY,peer.pylonX(),peer.pylonZ())
                ||matchesTargetAt(hoveredSelection,SelectionKind.CITY,peer.pylonX(),peer.pylonZ())) {
            ScreenPoint point=markerPoint(SelectionKind.CITY,peer.cityId(),new BlockPos(peer.pylonX(),pylonPos.getY(),peer.pylonZ()));
            reserveCityLabel(point.x()+TerritoryPresentation.cityRadius(zoom)+4,point.y()-4,labelFont.width(peer.name()));
        }
        if (this.showRelations) for (CityMapPeersPayload.Peer peer : this.peers) {
            CityRelationSnapshot.Status status = this.relations.getOrDefault(peer.cityId(), CityRelationSnapshot.Status.NONE);
            if (status == CityRelationSnapshot.Status.NONE || peer.own()) continue;
            int color = status == CityRelationSnapshot.Status.PARTNER ? 0xB365D8AF
                    : status == CityRelationSnapshot.Status.TRUSTED ? 0xA06EAED0 : 0x906F7596;
            drawLine(guiGraphics, worldToScreenX(this.pylonPos.getX() + .5), worldToScreenY(this.pylonPos.getZ() + .5),
                    worldToScreenX(peer.pylonX() + .5), worldToScreenY(peer.pylonZ() + .5), color);
        }
        for (CityMapPeersPayload.Peer peer : this.peers) {
            // Свой город рисуется отдельно и ярче.
            if ((this.showTerritory || this.projectPicking) && !peer.own()) renderClaims(guiGraphics, peer, false);
            if (atAnchor(peer)) {
                continue;
            }

            int viewportX = this.viewportX;
            int viewportY = this.viewportY;
            int viewportW = this.viewportWidth;
            int viewportH = this.viewportHeight;

            BlockPos peerPos = new BlockPos(peer.pylonX(), this.pylonPos.getY(), peer.pylonZ());
            ScreenPoint marker = markerPoint(SelectionKind.CITY, peer.cityId(), peerPos);
            int markerX = marker.x();
            int markerY = marker.y();

            // Метка целиком за вьюпортом — ничего не рисуем.
            if (markerX < viewportX - 16
                    || markerX > viewportX + viewportW + 16
                    || markerY < viewportY - 8
                    || markerY > viewportY + viewportH + 8) {
                continue;
            }

            /*
             * Территория рисуется по реальным участкам 50×50 в
             * собственном цвете города. Раньше здесь был квадрат
             * вокруг пилона серого цвета, и одинаковый серый для всех
             * чужих городов делал соседние территории неразличимыми.
             */


            if (!this.showCities) continue;
            drawMarkerLeader(guiGraphics, markerX, markerY,
                    worldToScreenX(peer.pylonX() + 0.5), worldToScreenY(peer.pylonZ() + 0.5));
            renderCityMarker(guiGraphics, markerX, markerY, peer.own(),
                    matchesTargetAt(this.hoveredSelection, SelectionKind.CITY, peer.pylonX(), peer.pylonZ()),
                    matchesTargetAt(this.selection, SelectionKind.CITY, peer.pylonX(), peer.pylonZ()));
            /*
             * Подсказка с фактами о городе — как при наведении очками Create.
             * Без неё наведение на чужой город давало только квадрат вокруг
             * маркера: ни названия, ни размера территории.
             */
            if (insideCityMarker(mouseX, mouseY, markerX, markerY)) {
                deferCityTooltip(mouseX, mouseY, peer);
            }
            CityRelationSnapshot.Status relation = this.relations.getOrDefault(peer.cityId(), CityRelationSnapshot.Status.NONE);
            if (relation != CityRelationSnapshot.Status.NONE) guiGraphics.renderOutline(markerX - 7, markerY - 7, 14, 14,
                    relation == CityRelationSnapshot.Status.PARTNER ? 0xFF65D8AF : relation == CityRelationSnapshot.Status.TRUSTED ? 0xFF79BBD8 : 0xFF9D8BC2);

            // Название — только если рядом, чтобы не превращать карту
            // в россыпь текста при множестве городов.
            String name = peer.name();
            boolean emphasized = matchesTargetAt(this.hoveredSelection, SelectionKind.CITY,
                    peer.pylonX(), peer.pylonZ())
                    || matchesTargetAt(this.selection, SelectionKind.CITY, peer.pylonX(), peer.pylonZ());
            /*
             * Под подсказкой подпись не рисуется: она начинается с того же
             * названия, и на экране оно оказывалось дважды — у маркера и в
             * окошке в паре пикселей от него.
             */
            if ((emphasized || this.zoom < 1.1) && !tooltipShown && name != null && !name.isEmpty()) {
                int textX = markerX + TerritoryPresentation.cityRadius(this.zoom) + 4;
                int textY = markerY - 4;
                var font = Minecraft.getInstance().font;
                if (textX + font.width(name) < viewportX + viewportW
                        && textX > viewportX
                        && textY > viewportY
                        && textY < viewportY + viewportH
                        && (!motionEnabled || emphasized || cityLabelFits(textX,textY,font.width(name)))) {
                    guiGraphics.drawString(
                            font,
                            name,
                            textX + 1,
                            textY + 1,
                            0x80000000,
                            false
                    );
                    guiGraphics.drawString(
                            font,
                            name,
                            textX,
                            textY,
                            peer.master() ? 0xFFFFC766 : 0xFFC4CDD2,
                            false
                    );
                }
            }
        }
    }

    /**
     * Показывает, что снимок территорий ещё не пришёл.
     *
     * <p>Без этой подсказки карта без территорий выглядит как «город пустой» и
     * наведение отвечает красной рамкой на любом участке — то есть на отказ,
     * который на самом деле означает «данных ещё нет». Подсказка отделяет
     * «ещё загружается» от «нельзя», чтобы отказ был читаемым.
     */
    private void renderSnapshotNotice(GuiGraphics guiGraphics) {
        if (this.peers.isEmpty() || !this.showTerritory) {
            return;
        }
        /*
         * Снимок без участков бывает у города без территории, и это не
         * ошибка доставки — молчать нужно только когда не пришёл ни один
         * город вовсе.
         */
        if (ownPeer() != null) {
            return;
        }
        Component notice = Component.translatable("gui.createcityexpansioncore.map.territory_pending");
        int width = Minecraft.getInstance().font.width(notice);
        int x = this.viewportX + (this.viewportWidth - width) / 2;
        int y = this.viewportY + 6;
        guiGraphics.fill(x - 4, y - 2, x + width + 4, y + 11, 0xC0101719);
        guiGraphics.drawString(Minecraft.getInstance().font, notice, x, y, 0xFFD09A3A, false);
    }

    /**
     * Сетка участков 50×50.
     *
     * <p>Тонкие линии по границам мировой сетки участков: без них игрок
     * не может мысленно выбрать участок, потому что заливка территории
     * рисуется без внутренних границ. Как только участок на экране
     * становится меньше {@link #MIN_PLOT_GRID_PX}, сетка исчезает: она
     * нужна для выбора, а не для украшения, и на мелком масштабе только
     * бы превращалась в муар.
     */
    private void renderPlotGrid(GuiGraphics guiGraphics) {
        if (this.zoom * CityPlot.PLOT_SIZE < MIN_PLOT_GRID_PX) {
            return;
        }
        double minWorldX = screenToWorldX(viewportX);
        double maxWorldX = screenToWorldX(viewportX + viewportWidth);
        double minWorldZ = screenToWorldZ(viewportY);
        double maxWorldZ = screenToWorldZ(viewportY + viewportHeight);
        int firstX = (int) Math.floor(minWorldX / CityPlot.PLOT_SIZE) * CityPlot.PLOT_SIZE;
        int firstZ = (int) Math.floor(minWorldZ / CityPlot.PLOT_SIZE) * CityPlot.PLOT_SIZE;
        for (int worldX = firstX; worldX <= maxWorldX; worldX += CityPlot.PLOT_SIZE) {
            guiGraphics.vLine(worldToScreenX(worldX), viewportY, viewportY + viewportHeight, 0x1FFFFFFF);
        }
        for (int worldZ = firstZ; worldZ <= maxWorldZ; worldZ += CityPlot.PLOT_SIZE) {
            guiGraphics.hLine(viewportX, viewportX + viewportWidth, worldToScreenY(worldZ), 0x1FFFFFFF);
        }
    }

    /**
     * Подсветка участка под курсором, если его можно занять.
     *
     * <p>Это единственная обратная связь при выборе участка: игрок видит,
     * какой именно участок 50×50 сейчас под мышью, до того как нажал
     * правую кнопку. Проверки занятости и смежности всё равно делает
     * сервер — здесь только визуальный отказ от обещания.
     */
    private void renderClaimHint(GuiGraphics guiGraphics) {
        CityPlot plot = this.hoveredPlot;
        if (plot == null) {
            return;
        }
        int left = worldToScreenX(plot.minX());
        int top = worldToScreenY(plot.minZ());
        int right = worldToScreenX(plot.maxX() + 1);
        int bottom = worldToScreenY(plot.maxZ() + 1);
        if (right <= viewportX || left >= viewportX + viewportWidth
                || bottom <= viewportY || top >= viewportY + viewportHeight) {
            return;
        }
        boolean own = plotOwnedBy(plot, ownPeer());
        int color = this.hoveredPlotClaimable
                ? CLAIMABLE_HINT
                : own ? 0x80FFFFFF : 0x60C86A62;
        if (this.hoveredPlotClaimable) {
            // Заливка только у активного участка: рамки фронтира уже
            // показывают, где можно взять, лишняя заливка muddies карту.
            guiGraphics.fill(left, top, right, bottom, 0x30E8C87A);
        }
        guiGraphics.renderOutline(left, top, right - left, bottom - top, color);
    }

    private static boolean plotOwnedBy(CityPlot plot, CityMapPeersPayload.Peer peer) {
        return peer != null && peer.plots().contains(plot);
    }

    /** Участок под экранными координатами мыши. */
    private CityPlot plotAtScreen(double mouseX, double mouseY) {
        if (!insideViewport(mouseX, mouseY)) {
            return null;
        }
        return CityPlot.at(this.dimensionId,
                (int) Math.floor(screenToWorldX(mouseX)),
                (int) Math.floor(screenToWorldZ(mouseY)));
    }

    /**
     * Можно ли предложить claim этого участка.
     *
     * <p>Та же логика, что и на сервере, но только для показа подсказки:
     * участок должен быть свободен и примыкать стороной к своей
     * территории. Ответ сервера остаётся окончательным.
     */
    private boolean canRequestPlot(CityPlot plot) {
        return plot != null && this.claimableLookup.contains(plot);
    }

    /**
     * Геометрия подтверждения claim.
     *
     * <p>Ширина панели никогда не превышает вьюпорт, кнопки сжимаются вместе
     * с ней, а текст переносится и обрезается под остаток. Все три условия
     * нужны одновременно: перенос без ограничения ширины панели всё равно
     * выпустил бы кнопки за экран на узком окне.
     */
    private void layoutClaimOverlay() {
        Font font = Minecraft.getInstance().font;
        /*
         * Ширина никогда не превышает вьюпорт. Минимальная ширина — 140, но
         * если вьюпорту уже меньше, берётся он: панель шире карты вышла бы
         * за её пределы, а узкая панель — единственное, что помещается.
         */
        this.overlayW = Math.min(this.viewportWidth,
                Math.max(140, Math.min(300, this.viewportWidth - 16)));
        int innerWidth = Math.max(20, this.overlayW - 2 * OVERLAY_PADDING);

        /*
         * Бюджет строк по высоте. Без него на низком экране панель с
         * заголовком, текстом и кнопками не поместилась бы по высоте и
         * кнопки ушли бы за нижний край. Лишние строки обрезаются с
         * многоточием, а не теряют кнопки.
         */
        int lineBudget = (this.viewportHeight - OVERLAY_MIN_CHROME_H) / font.lineHeight;
        int maxLines = Math.max(1, Math.min(OVERLAY_MAX_LINES, lineBudget));

        this.overlayTitle = CityMapTextLayout.truncateWithEllipsis(
                Component.translatable("gui.createcityexpansioncore.city.plot.claim_title").getString(),
                innerWidth, font::width);
        this.overlayLines = CityMapTextLayout.fit(List.of(
                Component.translatable("gui.createcityexpansioncore.city.plot.plot_label").getString()
                        + "  " + pendingClaim.plotX() + " : " + pendingClaim.plotZ(),
                Component.translatable("gui.createcityexpansioncore.city.plot.blocks_label").getString()
                        + "  " + pendingClaim.minX() + "…" + pendingClaim.maxX()
                        + "  /  " + pendingClaim.minZ() + "…" + pendingClaim.maxZ(),
                /*
                 * Правило примыкания показывается в подтверждении, а не только
                 * в подсказке вкладки: игрок смотрит в это окно в момент
                 * решения, и именно здесь «нужно стороной, по диагонали нельзя»
                 * отвечает на вопрос «почему сервер отказал».
                 */
                Component.translatable("gui.createcityexpansioncore.city.plot.claim_note").getString()),
                innerWidth, maxLines, font::width);

        // Высота считается по уже обрезанным строкам, иначе панель была бы
        // на отброшенные строки выше, чем её содержимое.
        this.overlayH = Math.min(this.viewportHeight, OVERLAY_TITLE_H + 2
                + this.overlayLines.size() * font.lineHeight
                + OVERLAY_PADDING + BUTTON_H + OVERLAY_PADDING);

        this.overlayX = this.viewportX + (this.viewportWidth - this.overlayW) / 2;
        this.overlayY = this.viewportY + Math.max(0,
                Math.min(OVERLAY_TOP_GAP, (this.viewportHeight - this.overlayH) / 2));

        this.buttonW = Math.max(20, (innerWidth - OVERLAY_BUTTON_GAP) / 2);
        this.confirmX = this.overlayX + OVERLAY_PADDING;
        this.cancelX = this.confirmX + this.buttonW + OVERLAY_BUTTON_GAP;
        this.confirmY = this.overlayY + this.overlayH - BUTTON_H - OVERLAY_PADDING;
        this.cancelY = this.confirmY;
    }

    private static final int OVERLAY_PADDING = 10;
    private static final int OVERLAY_TITLE_H = 16;
    private static final int OVERLAY_BUTTON_GAP = 8;
    private static final int BUTTON_H = 18;
    private static final int OVERLAY_TOP_GAP = 8;

    /**
     * Высота панели без единой строки текста: заголовок, кнопки, отступы.
     *
     * <p>Вычитается из высоты вьюпорта, чтобы узнать, сколько строк вообще
     * помещается.
     */
    private static final int OVERLAY_MIN_CHROME_H =
            OVERLAY_TITLE_H + 2 + OVERLAY_PADDING + BUTTON_H + OVERLAY_PADDING;

    /**
     * Максимум строк в подтверждении до обрезки многоточием.
     *
     * <p>Четыре строки — это номер участка, диапазон блоков и правило
     * примыкания. Больше не нужно, а лишние строки вытеснили бы кнопки за
     * нижний край окна на невысоком экране.
     */
    private static final int OVERLAY_MAX_LINES = 4;

    /** Подтверждение claim поверх карты. Ничего не отправляет сама. */
    private void renderClaimOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.pendingClaim == null) {
            return;
        }
        Font font = Minecraft.getInstance().font;
        layoutClaimOverlay();

        int x = this.overlayX;
        int y = this.overlayY;
        guiGraphics.fill(x - 2, y - 2,
                x + this.overlayW + 2, y + this.overlayH + 2, 0xF0101719);
        guiGraphics.renderOutline(x - 2, y - 2, this.overlayW + 4, this.overlayH + 4, ACCENT_COLOR);

        guiGraphics.drawString(font, this.overlayTitle, x + OVERLAY_PADDING, y + 4, 0xFFFFD08A);

        int textY = y + OVERLAY_TITLE_H + 2;
        for (String line : this.overlayLines) {
            guiGraphics.drawString(font, line, x + OVERLAY_PADDING, textY, 0xFFD1D5D6);
            textY += font.lineHeight;
        }

        renderOverlayButton(guiGraphics, font, this.confirmX, this.confirmY, this.buttonW,
                Component.translatable("gui.createcityexpansioncore.city.plot.claim_confirm"),
                true, inside(mouseX, mouseY, this.confirmX, this.confirmY, this.buttonW, BUTTON_H));
        renderOverlayButton(guiGraphics, font, this.cancelX, this.cancelY, this.buttonW,
                Component.translatable("gui.createcityexpansioncore.common.cancel"),
                false, inside(mouseX, mouseY, this.cancelX, this.cancelY, this.buttonW, BUTTON_H));
    }

    private static void renderOverlayButton(
            GuiGraphics graphics, Font font, int x, int y, int width, Component label,
            boolean primary, boolean hovered
    ) {
        graphics.fill(x, y, x + width, y + BUTTON_H,
                hovered ? 0xFF4A3A20 : primary ? 0xFF3A2F18 : 0xFF252A2C);
        graphics.renderOutline(x, y, width, BUTTON_H,
                hovered ? ACCENT_COLOR : FRAME_COLOR);
        graphics.drawCenteredString(font, label, x + width / 2, y + 5,
                hovered ? 0xFFFFFFFF : primary ? 0xFFFFD08A : 0xFFD1D5D6);
    }

    /**
     * Клик по подтверждению.
     *
     * <p>Клик мимо кнопок ничего не делает и не закрывает окно: случайное
     * нажатие мимо не должно отменять намерение игрока. Отмена — только
     * явной кнопкой или Escape.
     */
    private boolean handleClaimOverlayClick(double mouseX, double mouseY, int button) {
        /*
         * Геометрия обычно уже посчитана отрисовкой, но клик может прийти
         * раньше первого кадра с подтверждением: правый щелчок и следующий
         * левый попадают в один тик, когда окно ещё ни разу не рисовали.
         * Без досчёта прямо здесь кнопки лежали бы в нуле и клик пропадал бы.
         */
        if (this.overlayLines.isEmpty()) {
            layoutClaimOverlay();
        }
        if (button == 0) {
            if (inside(mouseX, mouseY, this.confirmX, this.confirmY, this.buttonW, BUTTON_H)) {
                confirmPendingClaim();
                return true;
            }
            if (inside(mouseX, mouseY, this.cancelX, this.cancelY, this.buttonW, BUTTON_H)) {
                setPendingClaim(null);
                return true;
            }
            return true;
        }
        if (button == 1) {
            // Правая кнопка отменяет: привычное «назад» правым щелчком.
            setPendingClaim(null);
            return true;
        }
        return true;
    }

    /**
     * Подсказка при наведении на город: название, жители и территория.
     *
     * <p>В стиле очков Create: короткая карточка у курсора с фактами о
     * объекте. Данные только визуальные — состав участников, роли и экономика
     * чужих городов не отправляются.
     */
    private void deferCityTooltip(int mouseX, int mouseY, CityMapPeersPayload.Peer peer) {
        if (motionEnabled && hoveredSelection==null) return;
        Component title = Component.literal(peer.name());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(
                "gui.createcityexpansioncore.map.city_residents", peer.residents()));
        lines.add(Component.translatable(
                "gui.createcityexpansioncore.world_map.context.plots", peer.plots().size()));
        if (peer.master()) {
            lines.add(Component.translatable("gui.createcityexpansioncore.world_map.master_city"));
        }
        CityRelationSnapshot.Status relation =
                this.relations.getOrDefault(peer.cityId(), CityRelationSnapshot.Status.NONE);
        if (relation != CityRelationSnapshot.Status.NONE) {
            lines.add(Component.translatable("gui.createcityexpansioncore.world_map.relation",
                    Component.translatable("gui.createcityexpansioncore.relations.status."
                            + relation.name().toLowerCase(Locale.ROOT))));
        }
        if (peer.own()) {
            lines.add(Component.translatable("gui.createcityexpansioncore.map.city_own"));
        }
        deferTooltip(mouseX, mouseY, prepend(title, lines));
    }

    private static Component[] prepend(Component first, List<Component> rest) {
        Component[] result = new Component[rest.size() + 1];
        result[0] = first;
        for (int index = 0; index < rest.size(); index++) {
            result[index + 1] = rest.get(index);
        }
        return result;
    }

    private void renderTerritory(GuiGraphics guiGraphics) {
        CityMapPeersPayload.Peer own = ownPeer();
        if (own == null) {
            return;
        }
        if (!projectFocus && this.showPlotGrid) renderClaimableFrontier(guiGraphics, own);
        renderClaims(guiGraphics, own, true);
    }

    private void renderConstructionMask(GuiGraphics g) {
        var own=ownPeer();if(own==null)return;
        int minX=CityPlot.plotXOf((int)Math.floor(screenToWorldX(viewportX))),maxX=CityPlot.plotXOf((int)Math.ceil(screenToWorldX(viewportX+viewportWidth)));
        int minZ=CityPlot.plotZOf((int)Math.floor(screenToWorldZ(viewportY))),maxZ=CityPlot.plotZOf((int)Math.ceil(screenToWorldZ(viewportY+viewportHeight)));
        if((long)(maxX-minX+1)*(maxZ-minZ+1)>4096)return;
        for(int z=minZ;z<=maxZ;z++) {
            int start=minX;
            for(int x=minX;x<=maxX+1;x++) {
                boolean available=x<=maxX&&!own.plots().contains(new CityPlot(dimensionId,x,z));
                if(available)continue;
                if(x>start)g.fill(worldToScreenX(start*CityPlot.PLOT_SIZE),worldToScreenY(z*CityPlot.PLOT_SIZE),worldToScreenX(x*CityPlot.PLOT_SIZE),worldToScreenY((z+1)*CityPlot.PLOT_SIZE),0x6010181C);
                start=x+1;
            }
        }
    }

    /**
     * Подсказка города, на который наведён курсор.
     *
     * <p>Свой город проверяется здесь, а не в {@code renderPeers}: тот
     * пропускает его намеренно — свой город рисуется отдельным, более ярким
     * слоем. Без этой проверки наведение на собственный пилон на карте не
     * давало ничего, хотя на чужие города подсказка появилась.
     *
     * <p>Подсказка не зависит от слоя территорий: слой можно выключить,
     * а вопрос «сколько у меня участков» всё равно задаётся.
     */
    private void renderCityTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.showCities) {
            return;
        }
        ScreenPoint ownPoint = markerPoint(SelectionKind.CITY, null, this.pylonPos);
        if (insideCityMarker(mouseX, mouseY, ownPoint.x(), ownPoint.y())) {
            CityMapPeersPayload.Peer own = ownPeer();
            if (own != null) {
                deferOwnCityTooltip(mouseX, mouseY, own);
            }
        }
    }

    /**
     * Подсказка своего города: тот же формат, что у чужих.
     *
     * <p>Формат общий, чтобы игрок не гадал, где смотреть: наведение на любой
     * город даёт одну и ту же карточку, различается только пометка «ваш».
     */
    private void deferOwnCityTooltip(int mouseX, int mouseY, CityMapPeersPayload.Peer own) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.createcityexpansioncore.map.city_residents", own.residents()));
        lines.add(Component.translatable(
                "gui.createcityexpansioncore.world_map.context.plots", own.plots().size()));
        if (own.master()) {
            lines.add(Component.translatable("gui.createcityexpansioncore.world_map.master_city"));
        }
        lines.add(Component.translatable("gui.createcityexpansioncore.map.city_own"));
        deferTooltip(mouseX, mouseY, prepend(Component.literal(own.name()), lines));
    }

    /**
     * Подсвечивает участки, которые можно занять следующим шагом.
     *
     * <p>Рисуется ДО заливки своей территории, иначе фронтир оказался бы под ней и пропал бы.
     *
     * <p>Это тонкая штриховая рамка без заливки: она читается как «здесь можно», но не
     * конкурирует с заливкой уже принадлежащей земли. Диагональные углы не рисуются — фронтир
     * повторяет ровно то смежное кольцо, которое сервер примет.
     *
     * <p>Внутренние стороны, обращённые к своей территории, не рисуются: иначе каждая заявленная
     * земля была бы обведена со всех сторон и карта превращалась бы в сетку.
     */
    private void renderClaimableFrontier(GuiGraphics guiGraphics, CityMapPeersPayload.Peer own) {
        if (this.claimableFrontier.isEmpty()) {
            return;
        }
        /*
         * На мелком масштабе рамка в несколько пикселей превращается в
         * сплошную заливку и ничего не сообщает. Ниже порога фронтир не
         * рисуется вовсе — это молчание полезнее шума.
         */
        if (this.zoom * CityPlot.PLOT_SIZE < MIN_PLOT_GRID_PX) {
            return;
        }
        int color = CityColors.argb(CityColors.lift(own.colorRgb(), 150), 0xB0);
        Set<CityPlot> owned = own.plots();

        for (CityPlot plot : this.claimableFrontier) {
            int left = worldToScreenX(plot.minX());
            int top = worldToScreenY(plot.minZ());
            int right = worldToScreenX(plot.maxX() + 1);
            int bottom = worldToScreenY(plot.maxZ() + 1);
            if (right <= viewportX || left >= viewportX + viewportWidth
                    || bottom <= viewportY || top >= viewportY + viewportHeight) {
                continue;
            }
            // Только внешние стороны: сторона, обращённая к своей
            // территории, — это уже граница города, а не предложение.
            if (!owned.contains(plot.offset(0, -1))) {
                drawDashedLine(guiGraphics, left, top, right, top, color);
            }
            if (!owned.contains(plot.offset(0, 1))) {
                drawDashedLine(guiGraphics, left, bottom, right, bottom, color);
            }
            if (!owned.contains(plot.offset(-1, 0))) {
                drawDashedLine(guiGraphics, left, top, left, bottom, color);
            }
            if (!owned.contains(plot.offset(1, 0))) {
                drawDashedLine(guiGraphics, right, top, right, bottom, color);
            }
        }
    }

    /**
     * Пунктирная линия через равные промежутки.
     *
     * <p>Сплошная линия отличалась бы от контура территории только цветом
     * и читалась бы как уже занятая земля. Пунктир говорит «ещё можно» без
     * подписи, а стоит ровно столько же, сколько сплошная.
     */
    private static void drawDashedLine(
            GuiGraphics graphics, int x0, int y0, int x1, int y1, int color
    ) {
        int length = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (length == 0) {
            return;
        }
        int stepX = Integer.signum(x1 - x0);
        int stepY = Integer.signum(y1 - y0);
        for (int offset = 0; offset < length; offset++) {
            // Ровно половина сегмента нарисована: 4 px метка, 4 px пауза.
            if (((offset >> 2) & 1) != 0) {
                continue;
            }
            int x = x0 + stepX * offset;
            int y = y0 + stepY * offset;
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    /**
     * Рисует территорию города по его реальным участкам 50×50.
     *
     * <p>Сначала заливка каждого участка цветом города, затем внешний
     * контур: ребро участка рисуется, только если соседний участок не
     * занят этим же городом. Поэтому большая территория читается как
     * одна земля с чёткой границей, а не как шахматная доска.
     *
     * <p>Каждый участок один {@code fill} и не более четырёх линий,
     * а всё целиком отсекается вьюпортом: за кадром не рисуется
     * ничего, и обход мировых данных в кадре не происходит.
     */
    private void renderClaims(GuiGraphics g,CityMapPeersPayload.Peer peer,boolean emphasize) {
        var style=TerritoryPresentation.style(this.zoom,this.territoryMode,projectFocus,projectPicking&&peer.own());
        if(style.borderAlpha()==0&&style.fillAlpha()==0)return;
        if(style.fillAlpha()>0)for(var plot:peer.plots()) {
            int l=worldToScreenX(plot.minX()),t=worldToScreenY(plot.minZ());
            int r=worldToScreenX(plot.maxX()+1),b=worldToScreenY(plot.maxZ()+1);
            if(r>viewportX&&l<viewportX+viewportWidth&&b>viewportY&&t<viewportY+viewportHeight)
                g.fill(l,t,r,b,CityColors.argb(peer.colorRgb(),style.fillAlpha()));
        }
        int border=CityColors.argb(CityColors.lift(peer.colorRgb(),emphasize?90:60),style.borderAlpha());
        for(var edge:territoryEdges.getOrDefault(peer.cityId(),List.of())) {
            int x1=worldToScreenX(edge.x1()),y1=worldToScreenY(edge.z1()),x2=worldToScreenX(edge.x2()),y2=worldToScreenY(edge.z2());
            if(x1==x2) {if(x1>=viewportX&&x1<viewportX+viewportWidth&&y2>=viewportY&&y1<viewportY+viewportHeight)
                g.fill(x1,Math.max(viewportY,y1),x1+1,Math.min(viewportY+viewportHeight,y2),border);}
            else if(y1>=viewportY&&y1<viewportY+viewportHeight&&x2>=viewportX&&x1<viewportX+viewportWidth)
                g.fill(Math.max(viewportX,x1),y1,Math.min(viewportX+viewportWidth,x2),y1+1,border);
        }
    }
    private void renderMarkers(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (hasCityAnchor()) {
        ScreenPoint ownMarker = markerPoint(SelectionKind.CITY, null, this.pylonPos);
        int markerX = ownMarker.x();
        int markerY = ownMarker.y();
        drawMarkerLeader(guiGraphics, markerX, markerY,
                worldToScreenX(this.pylonPos.getX() + 0.5), worldToScreenY(this.pylonPos.getZ() + 0.5));
        boolean ownedAnchor = this.peers.stream().anyMatch(peer -> atAnchor(peer) && peer.own());
        if (this.showCities) renderCityMarker(guiGraphics, markerX, markerY, ownedAnchor,
                matchesTarget(this.hoveredSelection, SelectionKind.CITY, null, this.pylonPos),
                matchesTarget(this.selection, SelectionKind.CITY, null, this.pylonPos));
        if (this.showCities) guiGraphics.drawString(
                Minecraft.getInstance().font,
                this.peers.stream().filter(this::atAnchor).findFirst().map(peer -> Component.literal(peer.name())).orElse(Component.translatable("gui.createcityexpansioncore.map.city_pylon")),
                markerX + TerritoryPresentation.cityRadius(this.zoom) + 4,
                markerY - 4,
                0xFFF4D49A,
                true
        );
        }

        BlockPos playerPos = playerPos();
        if (playerPos != null) {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            ScreenPoint player = markerPoint(SelectionKind.PLAYER, playerId, playerPos);
            int playerX = player.x();
            int playerY = player.y();
            MapMarkerRenderer.renderPlayer(guiGraphics, playerX, playerY, this.playerIcon,
                    matchesTarget(this.hoveredSelection, SelectionKind.PLAYER, playerId),
                    matchesTarget(this.selection, SelectionKind.PLAYER, playerId), pointerYaw);
        }

        if (this.showProjects && this.zoom >= MIN_ZOOM) for (CityProjectSnapshot project : projects) {
            if (!dimensionId.equals(project.dimensionId())) {
                continue;
            }
            ScreenPoint projectPoint = markerPoint(SelectionKind.INDUSTRY, project.projectId(), project.controllerPos());
            int projectX = projectPoint.x();
            int projectY = projectPoint.y();
            drawMarkerLeader(guiGraphics, projectX, projectY,
                    worldToScreenX(project.controllerPos().getX() + 0.5),
                    worldToScreenY(project.controllerPos().getZ() + 0.5));
            MapMarkerRenderer.renderMap(guiGraphics, this.zoom, projectX, projectY, this.mineIcon, true,
                    !"RUNNING".equals(project.status()),
                    matchesTarget(this.hoveredSelection, SelectionKind.INDUSTRY, project.projectId()),
                    matchesTarget(this.selection, SelectionKind.INDUSTRY, project.projectId()));
            if (inside(mouseX, mouseY, projectX - 11, projectY - 11, 22, 22)) {
                deferTooltip(mouseX, mouseY,
                        Component.translatable(
                                "gui.createcityexpansioncore.project.industrial_mine"),
                        Component.translatable(
                                "gui.createcityexpansioncore.map.resource_richness",
                                Component.translatable("resource.createcityexpansioncore."
                                        + project.resourceId().getPath()),
                                Math.round(project.richness() * 100.0)),
                        projectStatus(project.status()),
                        Component.translatable("gui.createcityexpansioncore.world_map.context.coordinates",
                                project.controllerPos().getX(), project.controllerPos().getZ()));
            }
        }
        if (this.showTradeInfrastructure && this.zoom >= MIN_ZOOM) for (EconomyStatePayload.InfrastructureView value : economyInfrastructure) {
            if (!value.cityOwned() || !dimensionId.equals(value.dimension())) continue;
            ScreenPoint point = markerPoint(SelectionKind.TRADE_INFRASTRUCTURE, value.id(), value.pos());
            int x = point.x(), y = point.y();
            drawMarkerLeader(guiGraphics, x, y,
                    worldToScreenX(value.pos().getX() + .5), worldToScreenY(value.pos().getZ() + .5));
            ItemStack icon = value.type() == com.example.examplemod.economy.EconomyTypes.InfrastructureType.CONTRACT_INTAKE
                    ? this.contractIcon : this.warehouseIcon;
            MapMarkerRenderer.renderMap(guiGraphics, this.zoom, x, y, icon, value.cityOwned(), !value.available(),
                    matchesTarget(this.hoveredSelection, SelectionKind.TRADE_INFRASTRUCTURE, value.id()),
                    matchesTarget(this.selection, SelectionKind.TRADE_INFRASTRUCTURE, value.id()));
            if (inside(mouseX, mouseY, x - 11, y - 11, 22, 22)) deferTooltip(mouseX, mouseY,
                    Component.translatable(value.type() == com.example.examplemod.economy.EconomyTypes.InfrastructureType.CONTRACT_INTAKE
                            ? "block.createcityexpansioncore.contract_intake" : "block.createcityexpansioncore.warehouse_gateway"),
                    Component.translatable(value.available() ? "status.createcityexpansioncore.railway.available"
                            : "status.createcityexpansioncore.railway.missing"),
                    Component.translatable("gui.createcityexpansioncore.world_map.context.coordinates",
                            value.pos().getX(), value.pos().getZ()));
        }
        if(this.showTradeInfrastructure&&this.zoom>=MIN_ZOOM)for(FreightPoint freight:this.freightPoints){if(!freight.dimension.equals(this.dimensionId))continue;ScreenPoint point=markerPoint(SelectionKind.FREIGHT_POINT,freight.id,freight.postbox);drawMarkerLeader(guiGraphics,point.x(),point.y(),worldToScreenX(freight.postbox.getX()+.5),worldToScreenY(freight.postbox.getZ()+.5));MapMarkerRenderer.renderMap(guiGraphics, this.zoom,point.x(),point.y(),this.freightIcon,freight.cityId.equals(ownCityId()),!freight.online(),matchesTarget(this.hoveredSelection,SelectionKind.FREIGHT_POINT,freight.id),matchesTarget(this.selection,SelectionKind.FREIGHT_POINT,freight.id));if(inside(mouseX,mouseY,point.x()-11,point.y()-11,22,22))deferTooltip(mouseX,mouseY,Component.literal(freight.name),Component.literal(freight.address),Component.literal(freight.stationName),Component.translatable("gui.createcityexpansioncore.world_map.freight."+(freight.online()?"online":"offline")));}
        if(this.showTradeInfrastructure&&this.zoom>=MIN_ZOOM)for(FreightCandidate candidate:this.freightCandidates){int x=worldToScreenX(candidate.postbox.getX()+.5),y=worldToScreenY(candidate.postbox.getZ()+.5);boolean hovered=inside(mouseX,mouseY,x-8,y-9,16,18),selected=matchesTarget(this.selection,SelectionKind.DISCOVERY,discoveryId(candidate));int pulse=reducedMotion?32:(int)(32+10*Math.sin(System.nanoTime()/650_000_000.0));guiGraphics.fill(x-5,y-7,x+6,y+7,hovered||selected?0xF06B4A20:0xE54A3518);guiGraphics.renderOutline(x-5,y-7,11,14,0xFFFFC45B);if(hovered||selected)guiGraphics.renderOutline(x-7,y-9,15,18,(pulse<<24)|0x00FFC45B);guiGraphics.drawCenteredString(Minecraft.getInstance().font,"!",x,y-4,0xFFFFFFFF);if(hovered)deferTooltip(mouseX,mouseY,Component.translatable("gui.createcityexpansioncore.world_map.discovery.title"),Component.translatable("gui.createcityexpansioncore.world_map.discovery.freight"),Component.literal(candidate.station.isBlank()?candidate.postbox.toShortString():candidate.station));}
    }

    private void deferTooltip(int mouseX, int mouseY, Component... lines) {
        if (motionEnabled && hoveredSelection==null) return;
        this.pendingTooltip = lines;
        this.pendingTooltipX = mouseX;
        this.pendingTooltipY = mouseY;
        this.tooltipShown = true;
    }

    private static void drawMarkerLeader(GuiGraphics graphics, int x, int y, int anchorX, int anchorY) {
        int dx = x - anchorX;
        int dy = y - anchorY;
        if (dx * dx + dy * dy < 28 * 28) return;
        drawLine(graphics, anchorX, anchorY, x, y, 0x907C6C50);
    }

    private void renderRailway(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.showTracks) {
            double minWorldX = screenToWorldX(this.viewportX) - 8;
            double maxWorldX = screenToWorldX(this.viewportX + this.viewportWidth) + 8;
            double minWorldZ = screenToWorldZ(this.viewportY) - 8;
            double maxWorldZ = screenToWorldZ(this.viewportY + this.viewportHeight) + 8;
            for (RailwayMapPayload.TrackSegment segment : this.railwayTracks) {
                if ((segment.x1() < minWorldX && segment.x2() < minWorldX)
                        || (segment.x1() > maxWorldX && segment.x2() > maxWorldX)
                        || (segment.z1() < minWorldZ && segment.z2() < minWorldZ)
                        || (segment.z1() > maxWorldZ && segment.z2() > maxWorldZ)) continue;
                int x1 = worldToScreenX(segment.x1()), y1 = worldToScreenY(segment.z1());
                int x2 = worldToScreenX(segment.x2()), y2 = worldToScreenY(segment.z2());
                drawLine(graphics, x1, y1, x2, y2, 0xCC171310);
                drawLine(graphics,
                        x1, y1 + (this.focus == Focus.RAILWAY ? 1 : 0),
                        x2, y2 + (this.focus == Focus.RAILWAY ? 1 : 0),
                        this.focus == Focus.RAILWAY ? 0xFFE0AD59 : 0xFF9A7040);
            }
            for(var section:this.railwaySections){drawSemanticSection(graphics,section);}
            RailwayMapPayload.RouteView focused = this.selection != null && this.selection.kind() == SelectionKind.ROUTE ? route(this.selection.id()) : null;
            if (focused != null) graphics.fill(this.viewportX, this.viewportY, this.viewportX + this.viewportWidth, this.viewportY + this.viewportHeight, 0x24101719);
            for(var route:this.railwayRoutes) {
                if (focused != null && !focused.id().equals(route.id())) continue;
                int color=focused!=null||route.selected()?MapPalette.SELECTED:route.status().equals("active")?0xFF8BCDB1:0xFFE0A776;
                if(focused!=null||route.selected())drawAnimatedRoute(graphics,route,color);
                else {drawSegments(graphics,route.segments(),0xE0121B20,-1,5);drawSegments(graphics,route.segments(),color,3);}
            }
        }
        // Freight links belong to the logistics overlay, independently of railway visibility.
        if(this.showTradeInfrastructure)for(FreightLink link:this.freightLinks){FreightPoint source=freight(link.source),destination=freight(link.destination);if(source!=null&&destination!=null&&source.dimension.equals(this.dimensionId)&&destination.dimension.equals(this.dimensionId))drawLine(graphics,worldToScreenX(source.postbox.getX()+.5),worldToScreenY(source.postbox.getZ()+.5),worldToScreenX(destination.postbox.getX()+.5),worldToScreenY(destination.postbox.getZ()+.5),0xB36ACFCE);}
        if (this.showStations && this.zoom >= MIN_ZOOM) {
            for (RailwayStationSnapshot station : this.railwayStations) {
                if (!station.dimension().equals(this.dimensionId)) continue;
                ScreenPoint point = markerPoint(SelectionKind.STATION, station.registrationId(), station.pos());
                int x = point.x();
                int y = point.y();
                drawMarkerLeader(graphics, x, y,
                        worldToScreenX(station.pos().getX() + 0.5), worldToScreenY(station.pos().getZ() + 0.5));
                MapMarkerRenderer.renderMap(graphics, this.zoom, x, y, this.stationIcon, station.cityOwned(),
                        !station.available(),
                        matchesTarget(this.hoveredSelection, SelectionKind.STATION, station.registrationId()),
                        matchesTarget(this.selection, SelectionKind.STATION, station.registrationId()));
                if (matchesTarget(this.hoveredSelection, SelectionKind.STATION, station.registrationId())
                        || matchesTarget(this.selection, SelectionKind.STATION, station.registrationId())) {
                    graphics.drawString(Minecraft.getInstance().font, station.name(), x + 6, y - 4,
                            station.available() ? 0xFFBEE5ED : 0xFFD6A4A7, true);
                }
                if (inside(mouseX, mouseY, x - 11, y - 11, 22, 22)) {
                    deferTooltip(mouseX, mouseY,
                            Component.literal(station.name()),
                            Component.translatable("gui.createcityexpansioncore.railway.station_type."
                                    + station.type().name().toLowerCase(Locale.ROOT)),
                            Component.translatable("status.createcityexpansioncore.railway."
                                    + (station.available() ? "available" : "missing")));
                }
            }
        }
        if (this.showTrains && this.zoom >= MIN_ZOOM) {
            double progress = Math.min(1.0,
                    Math.max(0.0, (System.currentTimeMillis() - this.railwaySnapshotAtMillis) / 1000.0));
            for (RailwayTrainSnapshot train : this.railwayTrains) {
                if (train.dimension() == null || !train.dimension().equals(this.dimensionId)) continue;
                TrainPosition previous = this.previousTrainPositions.get(train.fleetTrainId());
                double xPos = previous != null && previous.dimension().equals(train.dimension())
                        ? previous.x() + (train.x() - previous.x()) * progress : train.x();
                double zPos = previous != null && previous.dimension().equals(train.dimension())
                        ? previous.z() + (train.z() - previous.z()) * progress : train.z();
                BlockPos trainBlockPos = BlockPos.containing(train.x(), 0, train.z());
                ScreenPoint point = markerPosition(SelectionKind.TRAIN, train.fleetTrainId(), trainBlockPos,
                        worldToScreenX(xPos), worldToScreenY(zPos));
                int x = point.x();
                int y = point.y();
                drawMarkerLeader(graphics, x, y, worldToScreenX(xPos), worldToScreenY(zPos));
                MapMarkerRenderer.renderMap(graphics, this.zoom, x, y, this.trainIcon, train.cityOwned(),
                        train.automationSuspended(),
                        matchesTarget(this.hoveredSelection, SelectionKind.TRAIN, train.fleetTrainId()),
                        matchesTarget(this.selection, SelectionKind.TRAIN, train.fleetTrainId()));
                if (inside(mouseX, mouseY, x - 11, y - 11, 22, 22)) {
                    deferTooltip(mouseX, mouseY,
                            Component.literal(train.name()),
                            train.lineName().isEmpty()
                                    ? Component.translatable("status.createcityexpansioncore.railway.no_schedule")
                                    : Component.literal(train.lineName()),
                            Component.translatable("status.createcityexpansioncore.railway." + train.statusKey()),
                            train.nextStation().isEmpty() ? Component.empty()
                                    : Component.translatable("gui.createcityexpansioncore.railway.next_station",
                                    train.nextStation()));
                }
            }
            for (RailwayTrainCandidate train : this.railwayTrainCandidates) {
                if (train.dimension() == null || !train.dimension().equals(this.dimensionId)) continue;
                BlockPos trainBlockPos = BlockPos.containing(train.x(), 0, train.z());
                ScreenPoint point = markerPosition(SelectionKind.TRAIN_UNREGISTERED, train.createTrainId(),
                        trainBlockPos, worldToScreenX(train.x()), worldToScreenY(train.z()));
                int x = point.x();
                int y = point.y();
                drawMarkerLeader(graphics, x, y, worldToScreenX(train.x()), worldToScreenY(train.z()));
                MapMarkerRenderer.renderMap(graphics, this.zoom, x, y, this.trainIcon, false, true,
                        matchesTarget(this.hoveredSelection, SelectionKind.TRAIN_UNREGISTERED, train.createTrainId()),
                        matchesTarget(this.selection, SelectionKind.TRAIN_UNREGISTERED, train.createTrainId()));
                if (inside(mouseX, mouseY, x - 11, y - 11, 22, 22)) {
                    deferTooltip(mouseX, mouseY,
                            Component.literal(train.name()),
                            Component.translatable("status.createcityexpansioncore.unified.train_unregistered.title"),
                            train.stationName().isEmpty() ? Component.empty()
                                    : Component.translatable("gui.createcityexpansioncore.railway.next_station",
                                    train.stationName()));
                }
            }
        }

        if (!this.drawInternalControls) return;
        renderLayerButton(graphics, this.layerButtonsX, this.layerButtonsY,
                Component.translatable("gui.createcityexpansioncore.map.layer.territory_short"), this.showTerritory, mouseX, mouseY);
        renderLayerButton(graphics, this.layerButtonsX + 34, this.layerButtonsY,
                Component.translatable("gui.createcityexpansioncore.map.layer.projects_short"), this.showProjects, mouseX, mouseY);
        renderLayerButton(graphics, this.layerButtonsX + 68, this.layerButtonsY,
                Component.translatable("gui.createcityexpansioncore.map.layer.tracks_short"), this.showTracks, mouseX, mouseY);
        renderLayerButton(graphics, this.layerButtonsX + 102, this.layerButtonsY,
                Component.translatable("gui.createcityexpansioncore.map.layer.stations_short"), this.showStations, mouseX, mouseY);
        renderLayerButton(graphics, this.layerButtonsX + 136, this.layerButtonsY,
                Component.translatable("gui.createcityexpansioncore.map.layer.trains_short"), this.showTrains, mouseX, mouseY);
    }

    private static void renderLayerButton(GuiGraphics graphics, int x, int y, Component label,
                                          boolean enabled, int mouseX, int mouseY) {
        boolean hovered = inside(mouseX, mouseY, x, y, 30, 12);
        graphics.fill(x, y, x + 30, y + 12, enabled ? 0xD04A3A20 : 0xD0202729);
        graphics.renderOutline(x, y, 30, 12, hovered ? ACCENT_COLOR : FRAME_COLOR);
        graphics.drawCenteredString(Minecraft.getInstance().font, label, x + 15, y + 2,
                enabled ? 0xFFF2D39A : 0xFF7A8589);
    }

    private static void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int doubled = error * 2;
            if (doubled >= dy) { error += dy; x0 += sx; }
            if (doubled <= dx) { error += dx; y0 += sy; }
        }
    }

    /**
     * Подсказка, привязанная к курсору и прижатая внутрь карты.
     * <p>
     * Вызывается уже после снятия scissor, поэтому её не режет
     * область карты и она гарантированно поверх остальных слоёв.
     *
     * <p><b>Текст переносится под ширину окна.</b> Раньше ширина бралась как
     * «самая длинная строка», поэтому название города или станции длиннее
     * карты расширяло подсказку за её пределы, и кламп по левому краю
     * превращался в обрезку. Теперь содержимое сначала переносится и
     * ограничивается по высоте, и только потом рисуется — так что подсказка
     * всегда целиком внутри карты.
     */
    private void renderClampedTooltip(GuiGraphics graphics, Component[] lines) {
        Font font = Minecraft.getInstance().font;
        /*
         * Ширина и высота окна подсказки берутся от вьюпорта, а не только
         * ограничиваются им. clampTo прижимает окно к левому краю, когда
         * оно шире вьюпорта, и правый край уезжал за карту. Здесь ширина
         * просто не может превысить вьюпорт, потому что текст под неё
         * переносится.
         */
        int contentWidth = Math.max(20, this.viewportWidth - 16 - TOOLTIP_PADDING * 2);

        List<String> text = new ArrayList<>(lines.length);
        for (Component line : lines) {
            text.add(line.getString());
        }
        // Бюджет строк по высоте вьюпорта: подсказка не должна
        // превращаться в колонку во весь экран.
        int maxLines = Math.max(1,
                (this.viewportHeight - 8 - TOOLTIP_PADDING * 2) / font.lineHeight);
        List<String> fitted = CityMapTextLayout.fit(text, contentWidth, maxLines, font::width);
        if (fitted.isEmpty() || (fitted.size() == 1 && fitted.get(0).isEmpty())) {
            return;
        }

        int boxWidth = Math.min(this.viewportWidth,
                CityMapTextLayout.widest(fitted, font::width) + TOOLTIP_PADDING * 2);
        int boxHeight = Math.min(this.viewportHeight,
                fitted.size() * font.lineHeight + TOOLTIP_PADDING * 2);
        int left = clampTo(this.pendingTooltipX + 8, boxWidth, viewportX, viewportX + viewportWidth);
        int top = clampTo(this.pendingTooltipY + 8, boxHeight, viewportY, viewportY + viewportHeight);

        graphics.fill(left, top, left + boxWidth, top + boxHeight, 0xE8101719);
        graphics.renderOutline(left, top, boxWidth, boxHeight, ACCENT_COLOR);
        int row = 0;
        for (String line : fitted) {
            if (!line.isEmpty()) {
                graphics.drawString(font, line,
                        left + TOOLTIP_PADDING,
                        top + TOOLTIP_PADDING + row * font.lineHeight,
                        row == 0 ? 0xFFFFD08A : 0xFFD1D5D6, false);
            }
            row++;
        }
    }

    /** Отступ текста подсказки от рамки, он же настолько же с каждой стороны. */
    private static final int TOOLTIP_PADDING = 4;

    private static int clampTo(int value, int size, int min, int max) {
        if (max - min < size) {
            return min;
        }
        return Math.max(min, Math.min(value, max - size));
    }

    private void renderFooter(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int mouseX,
            int mouseY
    ) {
        Font font = Minecraft.getInstance().font;
        double displayX = insideViewport(mouseX, mouseY) ? screenToWorldX(mouseX) : this.centerX;
        double displayZ = insideViewport(mouseX, mouseY) ? screenToWorldZ(mouseY) : this.centerZ;
        Component status = Component.translatable(
                "gui.createcityexpansioncore.map.coordinates",
                (int) Math.floor(displayX),
                (int) Math.floor(displayZ),
                String.format(Locale.ROOT, "%.2f", this.zoom)
        );
        guiGraphics.drawString(font, status, x, y + 2, 0xFFA7B1B5, false);

        boolean hovered = inside(mouseX, mouseY, centerButtonX, centerButtonY, 54, 12);
        guiGraphics.fill(centerButtonX, centerButtonY, centerButtonX + 54, centerButtonY + 12,
                hovered ? 0xFF4A3A20 : 0xFF252A2C);
        guiGraphics.renderOutline(centerButtonX, centerButtonY, 54, 12,
                hovered ? ACCENT_COLOR : FRAME_COLOR);
        guiGraphics.drawCenteredString(font, Component.translatable(
                        "gui.createcityexpansioncore.map.center"),
                centerButtonX + 27, centerButtonY + 2,
                hovered ? 0xFFFFFFFF : 0xFFD1D5D6);
    }

    private static Component projectStatus(String status) {
        String key = switch (status) {
            case "OFFLINE / UNLOADED" -> "offline_unloaded";
            case "OFFLINE / CONTROLLER MISSING" -> "controller_missing";
            default -> status.toLowerCase(Locale.ROOT);
        };
        return Component.translatable("status.createcityexpansioncore.mine." + key);
    }

    private record TrainPosition(ResourceLocation dimension, double x, double z) {}

    /**
 * Сообщает карте, где заканчивается доступная область.
 *
     * <p>Правая контекстная колонка World Map лежит поверх карты, и
     * обвязка, посчитанная без этой границы, уезжала бы под неё
     * наполовину. Экран вызывает это перед отрисовкой кадра.
     */
    public void setChromeAvoidRight(int screenX) {
        this.chromeAvoidRight = screenX;
    }

    /** Границы обвязки текущего кадра; {@code null} до первой отрисовки. */
    public CityMapFrame.Geometry chromeGeometry() {
        return this.chromeGeometry;
    }

    /**
     * Легенда слоёв: подпись, счётчик и включён ли слой.
     *
     * <p>Собирается из того же состояния, которым рисуется сама
     * карта, поэтому легенда не может разойтись с содержимым:
     * выключенный слой здесь гаснет ровно так же, как исчезает с
     * карты.
     */
    private List<CityMapFrame.LegendEntry> frameLegendEntries() {
        List<CityMapFrame.LegendEntry> entries = new ArrayList<>();
        for (Overlay overlay : Overlay.values()) {
            entries.add(new CityMapFrame.LegendEntry(
                    Component.translatable(
                            "gui.createcityexpansioncore.world_map.overlay."
                                    + overlay.name().toLowerCase(Locale.ROOT)
                    ).getString(),
                    Integer.toString(overlayCount(overlay)),
                    isOverlayEnabled(overlay)
            ));
        }
        return entries;
    }

    /**
     * Сколько объектов реально лежит под слоем в этом измерении.
     *
     * <p>Считается из того же состояния, которым слой рисуется. Отдельный набор чисел в легенде
     * рано или поздно разошёлся бы с картой, и игрок искал бы отсутствующий объект.
     */
    public int overlayCount(Overlay overlay) {
        return switch (overlay) {
            case CITIES -> this.peers.size();
            case TERRITORIES -> plotCount();
            case PROJECTS -> (int) this.projects.stream().filter(p -> this.dimensionId.equals(p.dimensionId())).count();
            case TRACKS -> this.railwayTracks.size();
            case STATIONS -> (int) this.railwayStations.stream().filter(p -> this.dimensionId.equals(p.dimension())).count();
            case TRAINS -> (int) this.railwayTrains.stream().filter(p -> this.dimensionId.equals(p.dimension())).count();
            case TRADE -> freightPointCount();
            case RELATIONS -> (int) this.peers.stream().filter(p -> !p.own() && this.relations.getOrDefault(p.cityId(), CityRelationSnapshot.Status.NONE) != CityRelationSnapshot.Status.NONE).count();
        };
    }

    private int plotCount() {
        CityMapPeersPayload.Peer own = ownPeer();
        return own == null ? 0 : own.plots().size();
    }

    /** Данные обвязки на текущий кадр. */
    private CityMapFrame.Context frameContext() {
        BlockPos player = playerPos();
        List<CityMapFrame.MapMark> marks = new ArrayList<>();

        for (CityMapPeersPayload.Peer peer : this.peers) {
            marks.add(new CityMapFrame.MapMark(
                    peer.name(),
                    peer.pylonX(),
                    peer.pylonZ(),
                    peer.own() ? MapPalette.OWN : MapPalette.NEUTRAL,
                    peer.own()
            ));
        }
        if (player != null) {
            marks.add(new CityMapFrame.MapMark(
                    "player",
                    player.getX(),
                    player.getZ(),
                    MapPalette.GOOD,
                    true
            ));
        }

        return new CityMapFrame.Context(
                this.centerX,
                this.centerZ,
                this.zoom,
                player == null ? (int) Math.floor(this.centerX) : player.getX(),
                player == null ? (int) Math.floor(this.centerZ) : player.getZ(),
                this.pylonPos.getX(),
                this.pylonPos.getZ(),
                marks,
                frameLegendEntries(),
                this.dimensionId.getPath().toUpperCase(Locale.ROOT)
        );
    }

    /** Counts for the World Map overview panel; filtered to this dimension so the numbers mean something. */
    public int freightPointCount() {
        int total = 0;
        for (FreightPoint point : this.freightPoints) if (point.dimension.equals(this.dimensionId)) total++;
        return total;
    }

    /** Unregistered Postbox/Packager pairs the player could still turn into a Freight Point. */
    public int freightCandidateCount() { return this.freightCandidates.size(); }

    public void setPresentation(boolean grid, boolean overview) { this.showCoordinateGrid = grid; this.showOverview = overview; }
    public boolean coordinateGrid() { return this.showCoordinateGrid; }
    public boolean overviewVisible() { return this.showOverview; }
    public double centerX() { return this.centerX; }
    public double centerZ() { return this.centerZ; }
    public double zoom() { return this.zoom; }
    public void restoreView(double x, double z, double zoom) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(zoom)) return;
        stopMotion();
        this.centerX = x; this.centerZ = z; this.zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM); clampCenter();
    }
    public void centerOnPlayer() { BlockPos player = playerPos(); if (player != null) centerOn(player); }
    public void fitCity() {
        CityMapPeersPayload.Peer own = ownPeer();
        if (own == null || own.plots().isEmpty()) { centerOnPylon(); return; }
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (CityPlot plot : own.plots()) {
            minX = Math.min(minX, plot.plotX() * 50); minZ = Math.min(minZ, plot.plotZ() * 50);
            maxX = Math.max(maxX, (plot.plotX() + 1) * 50); maxZ = Math.max(maxZ, (plot.plotZ() + 1) * 50);
        }
        double fitZoom = clamp(Math.min(Math.max(1, this.viewportWidth - 48) / (double) Math.max(1, maxX - minX),
                Math.max(1, this.viewportHeight - 48) / (double) Math.max(1, maxZ - minZ)), MIN_ZOOM, MAX_ZOOM);
        animateTo((minX+(double)maxX)/2,(minZ+(double)maxZ)/2,fitZoom);
    }
    public void setPersonalPins(List<AtlasPreferences.Pin> pins) { this.personalPins.clear(); this.personalPins.addAll(pins.stream().limit(AtlasPreferences.MAX_PINS).toList()); }
    public List<AtlasPreferences.Pin> personalPins() { return List.copyOf(this.personalPins); }
    public void togglePinTool() { this.placingPin = !this.placingPin; this.measuring = false; }
    public void toggleMeasureTool() { this.measuring = !this.measuring; this.placingPin = false; this.measureStart = null; this.measureEnd = null; }
    public boolean pinToolActive() { return this.placingPin; }
    public boolean measureToolActive() { return this.measuring; }
    public boolean cancelPersonalTools() {
        boolean active = this.placingPin || this.measuring;
        this.placingPin = false; this.measuring = false; this.measureStart = null; this.measureEnd = null; return active;
    }
    public void removeSelectedPin() {
        if (this.selection == null || this.selection.kind() != SelectionKind.WAYPOINT) return;
        this.personalPins.removeIf(pin -> pin.x() == this.selection.pos().getX() && pin.z() == this.selection.pos().getZ());
        clearSelection();
    }
    private Selection pinSelection(int index) {
        AtlasPreferences.Pin pin = this.personalPins.get(index);
        return new Selection(SelectionKind.WAYPOINT, UUID.nameUUIDFromBytes((this.dimensionId + ":pin:" + pin.x() + ":" + pin.z()).getBytes(StandardCharsets.UTF_8)),
                Component.translatable("gui.createcityexpansioncore.world_map.pin", index + 1), new BlockPos(pin.x(), 0, pin.z()),
                true, List.of(Component.translatable("gui.createcityexpansioncore.world_map.pin.local")), this.cityIcon, false);
    }
    private void renderPersonalTools(GuiGraphics graphics,int mouseX,int mouseY) {
        for (int index = 0; index < this.personalPins.size(); index++) {
            Selection pin = pinSelection(index);
            int x = worldToScreenX(pin.pos().getX()), y = worldToScreenY(pin.pos().getZ());
            graphics.fill(x - 4, y - 5, x + 5, y + 4, MapPalette.PAPER_SUNK);
            graphics.renderOutline(x - 3, y - 4, 7, 7, MapPalette.SELECTED);
            graphics.vLine(x, y + 3, y + 9, MapPalette.SELECTED);
        }
        if (this.measuring && this.measureStart != null) {
            int x = worldToScreenX(this.measureStart.getX()), y = worldToScreenY(this.measureStart.getZ());
            graphics.renderOutline(x - 3, y - 3, 6, 6, MapPalette.SELECTED);
            BlockPos end=this.measureEnd!=null?this.measureEnd:insideViewport(mouseX,mouseY)
                    ?BlockPos.containing(screenToWorldX(mouseX),0,screenToWorldZ(mouseY)):null;
            if (end != null) {
                int ex = worldToScreenX(end.getX()), ey = worldToScreenY(end.getZ());
                drawLine(graphics, x, y, ex, ey, MapPalette.SELECTED);
                double distance = Math.hypot(end.getX() - (double) this.measureStart.getX(), end.getZ() - (double) this.measureStart.getZ());
                Component label = Component.translatable("gui.createcityexpansioncore.world_map.measure.distance", String.format(Locale.ROOT, "%.1f", distance));
                var font = Minecraft.getInstance().font;
                int lx = Math.max(this.viewportX + 4, Math.min(this.viewportX + this.viewportWidth - font.width(label) - 4, (x + ex) / 2));
                int ly = Math.max(this.viewportY + 4, Math.min(this.viewportY + this.viewportHeight - 12, (y + ey) / 2));
                graphics.fill(lx - 2, ly - 2, lx + font.width(label) + 2, ly + 10, MapPalette.PAPER_SUNK);
                graphics.drawString(font, label, lx, ly, MapPalette.TEXT, false);
            }
        }
    }
    public boolean terrainLoading() { return !requestedTiles.isEmpty(); }
    public Component terrainStatus() {
        boolean failed=false,loading=false,unknown=false;int level=terrainLevel();
        int minX=terrainCoordinate(screenToWorldX(viewportX),level),maxX=terrainCoordinate(screenToWorldX(viewportX+viewportWidth),level);
        int minZ=terrainCoordinate(screenToWorldZ(viewportY),level),maxZ=terrainCoordinate(screenToWorldZ(viewportY+viewportHeight),level);
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++) {
            TileKey key=new TileKey(dimensionId,x,z,level);CachedTile tile=tileCache.get(key);
            failed|=failedTiles.contains(key);loading|=requestedTiles.containsKey(key);unknown|=tile==null||!tile.complete();
        }
        return Component.translatable("gui.createcityexpansioncore.world_map.terrain."+(failed?"failed":loading?"loading":unknown?"unknown":"ready"));
    }
    public void retryTerrain() { this.failedTiles.clear(); this.tileAttempts.clear(); this.requestedTiles.clear(); }

    public enum Overlay { CITIES, TERRITORIES, PROJECTS, TRACKS, STATIONS, TRAINS, TRADE, RELATIONS }

    public enum Focus { WORLD, RAILWAY, TRADE, ENERGY }

    public enum SelectionKind { WAYPOINT, PLAYER, CITY, STATION, TRAIN, TRAIN_UNREGISTERED, INDUSTRY, TRADE_INFRASTRUCTURE, FREIGHT_POINT, DISCOVERY, RAILWAY_SECTION, ROUTE }

    public record Selection(SelectionKind kind, UUID id, Component title, BlockPos pos,
                            boolean own, List<Component> details, ItemStack icon, boolean warning) {
        public Selection {
            details = List.copyOf(details);
        }
    }

    public record ScreenPoint(int x, int y) {}

    private record MarkerKey(SelectionKind kind, UUID id, BlockPos pos) {
        private String stableId() {
            return this.id != null ? this.id.toString() : this.pos.getX() + ":" + this.pos.getZ();
        }
    }

    private record MarkerEntry(MarkerKey key, double worldX, double worldZ, int priority) {}
    private record FreightLink(UUID id,UUID source,UUID destination,String status) {}
    private record FreightCandidate(BlockPos postbox,BlockPos packager,String station) {}

    private UUID discoveryId(FreightCandidate value){return UUID.nameUUIDFromBytes((this.dimensionId+":"+value.postbox.asLong()+":"+value.packager.asLong()).getBytes(StandardCharsets.UTF_8));}
    private Selection discoverySelection(FreightCandidate value){return new Selection(SelectionKind.DISCOVERY,discoveryId(value),Component.translatable("gui.createcityexpansioncore.world_map.discovery.title"),value.postbox,true,List.of(Component.translatable("gui.createcityexpansioncore.world_map.discovery.freight"),Component.literal(value.station.isBlank()?value.postbox.toShortString():value.station),Component.translatable("gui.createcityexpansioncore.world_map.discovery.setup_hint")),this.freightIcon,true);}

    private Selection peerSelection(CityMapPeersPayload.Peer peer) {
        var profile = com.example.examplemod.client.CityPublicDirectory.profile(peer.cityId());
        List<Component> details=new ArrayList<>();details.add(Component.translatable("gui.createcityexpansioncore.world_map.context.plots",profile == null ? peer.plots().size() : profile.plots()));if(peer.master())details.add(Component.translatable("gui.createcityexpansioncore.world_map.master_city"));
        CityRelationSnapshot.Status relation = this.relations.getOrDefault(peer.cityId(), CityRelationSnapshot.Status.NONE);
        if (!peer.own()) details.add(Component.translatable("gui.createcityexpansioncore.world_map.relation",
                Component.translatable("gui.createcityexpansioncore.relations.status." + relation.name().toLowerCase(Locale.ROOT))));
        if (relation == CityRelationSnapshot.Status.TRUSTED || relation == CityRelationSnapshot.Status.PARTNER)
            details.add(Component.translatable("gui.createcityexpansioncore.world_map.relation.private_access"));
        else if (!peer.own()) details.add(Component.translatable("gui.createcityexpansioncore.world_map.relation.public_only"));
        return new Selection(SelectionKind.CITY, peer.cityId(), Component.literal(peer.name()),
                new BlockPos(peer.pylonX(), this.pylonPos.getY(), peer.pylonZ()), peer.own(),
                details, this.cityIcon, false);
    }

    private Selection playerSelection() {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos pos = playerPos();
        if (pos == null) return null;
        return new Selection(SelectionKind.PLAYER, minecraft.player.getUUID(),
                Component.translatable("gui.createcityexpansioncore.world_map.my_position"),
                pos, true, List.of(), this.playerIcon, false);
    }

    private BlockPos playerPos() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && minecraft.player.level().dimension().location().equals(this.dimensionId)
                ? minecraft.player.blockPosition() : null;
    }

    private Selection stationSelection(RailwayStationSnapshot station) {
        List<Component> details=new ArrayList<>();details.add(Component.translatable("gui.createcityexpansioncore.railway.station_type."+station.type().name().toLowerCase(Locale.ROOT)));details.add(Component.translatable("status.createcityexpansioncore.railway."+(station.available()?"available":"missing")));this.freightPoints.stream().filter(point->station.name().equals(point.stationName)).findFirst().ifPresent(point->details.add(Component.translatable("gui.createcityexpansioncore.world_map.station.freight",point.name)));
        return new Selection(SelectionKind.STATION, station.registrationId(), Component.literal(station.name()),
                station.pos(), station.cityOwned(), details,
                this.stationIcon, !station.available());
    }

    private Selection trainSelection(RailwayTrainSnapshot train) {
        List<Component> details = new ArrayList<>();
        details.add(CcecStatusUi.title(train.uiStatus()));
        details.add(Component.translatable("status.createcityexpansioncore.railway." + train.statusKey()));
        details.add(Component.translatable("gui.createcityexpansioncore.world_map.train.owner",
                Component.literal(train.ownerLabel())));
        details.add(Component.translatable("gui.createcityexpansioncore.world_map.train.station",
                train.currentStation().isEmpty() ? Component.literal("—") : Component.literal(train.currentStation())));
        details.add(Component.translatable("gui.createcityexpansioncore.world_map.train.line",
                train.lineName().isEmpty() ? Component.literal("—") : Component.literal(train.lineName())));
        details.add(Component.translatable("gui.createcityexpansioncore.world_map.train.type",
                Component.translatable("gui.createcityexpansioncore.railway.train_type."
                        + train.type().name().toLowerCase(Locale.ROOT))));
        if (!train.activeTrip().isEmpty()) {
            details.add(Component.translatable("gui.createcityexpansioncore.world_map.train.trip",
                    Component.translatable("gui.createcityexpansioncore.railway.trip_status." + train.activeTrip())));
        }
        if (!train.activeShipment().isEmpty()) {
            details.add(Component.translatable("gui.createcityexpansioncore.world_map.train.shipment",
                    Component.translatable("gui.createcityexpansioncore.railway.shipment_status." + train.activeShipment())));
        }
        return new Selection(SelectionKind.TRAIN, train.fleetTrainId(), Component.literal(train.name()),
                BlockPos.containing(train.x(), 0, train.z()), train.canManage(), details,
                this.trainIcon, train.automationSuspended());
    }

    /** A Create train the player has not registered yet: card explains the gap and offers the fix. */
    private Selection unregisteredTrainSelection(RailwayTrainCandidate train) {
        var status = CcecStatusResolver.unregisteredTrain();
        List<Component> details = new ArrayList<>();
        details.add(CcecStatusUi.title(status));
        details.add(CcecStatusUi.reason(status));
        if (!train.stationName().isEmpty()) {
            details.add(Component.translatable("gui.createcityexpansioncore.world_map.train.station",
                    Component.literal(train.stationName())));
        }
        return new Selection(SelectionKind.TRAIN_UNREGISTERED, train.createTrainId(), Component.literal(train.name()),
                BlockPos.containing(train.x(), 0, train.z()), train.canManage(), details,
                this.trainIcon, true);
    }

    private Selection projectSelection(CityProjectSnapshot project) {
        return new Selection(SelectionKind.INDUSTRY, project.projectId(),
                Component.translatable("gui.createcityexpansioncore.project.industrial_mine"),
                project.controllerPos(), true, List.of(
                Component.translatable("gui.createcityexpansioncore.map.resource_richness",
                        Component.translatable("resource.createcityexpansioncore." + project.resourceId().getPath()),
                        Math.round(project.richness() * 100.0)), projectStatus(project.status())),
                this.mineIcon, !"RUNNING".equals(project.status()));
    }

    private Selection infrastructureSelection(EconomyStatePayload.InfrastructureView value) {
        return new Selection(SelectionKind.TRADE_INFRASTRUCTURE, value.id(), infrastructureName(value),
                value.pos(), value.cityOwned(), List.of(Component.translatable(value.available()
                ? "status.createcityexpansioncore.railway.available"
                : "status.createcityexpansioncore.railway.missing")),
                value.type() == com.example.examplemod.economy.EconomyTypes.InfrastructureType.CONTRACT_INTAKE
                        ? this.contractIcon : this.warehouseIcon, !value.available());
    }

    private Selection freightSelection(FreightPoint point){String city=cityName(point.cityId);List<Component> details=new ArrayList<>();details.add(Component.translatable("gui.createcityexpansioncore.world_map.freight.city",city));details.add(Component.translatable("gui.createcityexpansioncore.world_map.freight.station",point.stationName.isBlank()?"—":point.stationName));details.add(Component.literal(point.address));details.add(Component.translatable("gui.createcityexpansioncore.world_map.freight."+(point.online()?"online":"offline")));return new Selection(SelectionKind.FREIGHT_POINT,point.id,Component.literal(point.name),point.postbox,point.cityId.equals(ownCityId()),details,this.freightIcon,!point.online());}
    private Selection findSectionSelection(double mouseX, double mouseY) {
        for (var section : this.railwaySections)
            if (hitSegments(section.segments(), mouseX, mouseY, 5)) return sectionSelection(section);
        return null;
    }
    private Selection sectionSelection(RailwayMapPayload.SectionView section) {
        var network=network(section.networkId());String owner=network==null?"?":network.ownerName();List<Component> details=List.of(Component.translatable("gui.createcityexpansioncore.world_map.rail.owner",owner),Component.translatable("gui.createcityexpansioncore.world_map.rail.owner_type",network==null?"?":Component.translatable("gui.createcityexpansioncore.world_map.rail.owner_type."+network.ownerType().name().toLowerCase(Locale.ROOT))),Component.translatable("gui.createcityexpansioncore.world_map.rail.length",String.format(Locale.ROOT,"%.1f",section.length())),Component.translatable("gui.createcityexpansioncore.world_map.rail.access",network==null?"?":Component.translatable("gui.createcityexpansioncore.world_map.rail.access."+network.access().name().toLowerCase(Locale.ROOT))),Component.translatable("gui.createcityexpansioncore.world_map.rail.tariff",network==null?0:network.tariff()),Component.translatable("gui.createcityexpansioncore.world_map.rail.status",Component.translatable("gui.createcityexpansioncore.world_map.rail.status."+section.status().name().toLowerCase(Locale.ROOT))));return new Selection(SelectionKind.RAILWAY_SECTION,section.id(),Component.literal(network==null?shortId(section.networkId()):network.name()),midpoint(section.segments()),ownsNetwork(network),details,this.networkIcon,section.status()!=com.example.examplemod.railway.RailwayOwnershipTypes.SectionStatus.ONLINE);
    }
    private Selection findRouteSelection(double mouseX, double mouseY) {
        for (var route : this.railwayRoutes)
            if (hitSegments(route.segments(), mouseX, mouseY, 6)) return routeSelection(route);
        return null;
    }
    private Selection routeSelection(RailwayMapPayload.RouteView route) {
        List<Component> details=new ArrayList<>();details.add(Component.translatable("gui.createcityexpansioncore.world_map.route.distance",String.format(Locale.ROOT,"%.1f",route.distance())));details.add(Component.translatable("gui.createcityexpansioncore.world_map.route.price",route.price()));if(route.etaTicks()>0)details.add(Component.translatable("gui.createcityexpansioncore.world_map.route.eta",route.etaTicks()/20));String strategy=this.routeStrategies.get(route.id());if(strategy!=null)details.add(Component.translatable("gui.createcityexpansioncore.world_map.route.strategy",Component.translatable("gui.createcityexpansioncore.railway.route.strategy."+strategy.toLowerCase(Locale.ROOT))));details.add(Component.translatable("gui.createcityexpansioncore.world_map.route.status",route.status()));return new Selection(SelectionKind.ROUTE,route.id(),Component.translatable(route.selected()?"gui.createcityexpansioncore.world_map.route.selected":"gui.createcityexpansioncore.world_map.route.active"),midpoint(route.segments()),true,details,this.networkIcon,!route.status().equals("active")&&!route.selected());
    }
    private boolean hitSegments(List<RailwayMapPayload.TrackSegment> segments,double mouseX,double mouseY,double radius){for(var s:segments)if(distanceToSegment(mouseX,mouseY,worldToScreenX(s.x1()),worldToScreenY(s.z1()),worldToScreenX(s.x2()),worldToScreenY(s.z2()))<=radius)return true;return false;}
    private static double distanceToSegment(double px,double py,double x1,double y1,double x2,double y2){double dx=x2-x1,dy=y2-y1;if(dx==0&&dy==0)return Math.hypot(px-x1,py-y1);double t=Math.max(0,Math.min(1,((px-x1)*dx+(py-y1)*dy)/(dx*dx+dy*dy)));return Math.hypot(px-(x1+t*dx),py-(y1+t*dy));}
    private static BlockPos midpoint(List<RailwayMapPayload.TrackSegment> values){if(values.isEmpty())return BlockPos.ZERO;var s=values.get(values.size()/2);return BlockPos.containing((s.x1()+s.x2())/2,0,(s.z1()+s.z2())/2);}
    private void drawSegments(GuiGraphics graphics,List<RailwayMapPayload.TrackSegment> values,int color,int width){drawSegments(graphics,values,color,0,width);}
    private void drawSegments(GuiGraphics graphics,List<RailwayMapPayload.TrackSegment> values,int color,int start,int count){
        for(var s:values){int x1=worldToScreenX(s.x1()),y1=worldToScreenY(s.z1()),x2=worldToScreenX(s.x2()),y2=worldToScreenY(s.z2());double length=Math.max(1,Math.hypot(x2-x1,y2-y1));double nx=-(y2-y1)/length,ny=(x2-x1)/length;for(int offset=start;offset<start+count;offset++){double band=offset-(count-1)/2.0-start;int dx=(int)Math.round(nx*band),dy=(int)Math.round(ny*band);drawLine(graphics,x1+dx,y1+dy,x2+dx,y2+dy,color);}}
    }

    /**
     * How a railway line reads on the atlas. Colour alone is not the contract: every class also has
     * its own inner width and is named in the railway legend, so ownership and access survive a
     * greyscale screenshot or a colour-blind reader.
     */
    public enum RailClass { OWN, PUBLIC_FREE, PUBLIC_PAID, PRIVATE, OFFLINE }

    /**
     * Classifies one section from data the server already sends. Deliberately takes primitives so
     * the policy is verified headless instead of only through a rendered frame.
     *
     * <p>An offline section is reported as such whatever its access is: a dark, dead line must not
     * keep advertising a tariff or an ownership as if it were usable.
     */
    public static RailClass classifyRail(boolean own,boolean isPublic,long tariff,boolean online) {
        if (!online) return RailClass.OFFLINE;
        if (own) return RailClass.OWN;
        if (!isPublic) return RailClass.PRIVATE;
        return tariff > 0L ? RailClass.PUBLIC_PAID : RailClass.PUBLIC_FREE;
    }

    /** Inner line colour. Paid public track is the one saturated colour on the map, on purpose. */
    public static int railInnerColor(RailClass kind) {
        return switch (kind) {
            case OWN -> 0xFFC7D0D6;
            case PUBLIC_FREE -> 0xFF7FA8BE;
            case PUBLIC_PAID -> 0xFF70C58E;
            case PRIVATE -> 0xFFB47B79;
            case OFFLINE -> 0xFF6E777C;
        };
    }

    /** Outline under every railway line, so it reads on terrain, territory fills and each other. */
    public static int railOutlineColor(RailClass kind) {
        return kind == RailClass.PRIVATE || kind == RailClass.OFFLINE ? 0xE00A0507 : 0xE00B0F11;
    }

    /** Inner width. Paid and own track are the heaviest, private the lightest. */
    public static int railInnerWidth(RailClass kind) {
        return switch (kind) {
            case OWN -> 3;
            case PUBLIC_PAID -> 3;
            case PUBLIC_FREE -> 2;
            case PRIVATE, OFFLINE -> 1;
        };
    }

    private void drawSemanticSection(GuiGraphics graphics,RailwayMapPayload.SectionView section) {
        RailwayMapPayload.NetworkView network = network(section.networkId());
        RailClass kind = classifyRail(
                ownsNetwork(network),
                network != null && network.access() == com.example.examplemod.railway.RailwayOwnershipTypes.Access.PUBLIC,
                network == null ? 0L : network.tariff(),
                section.status() == com.example.examplemod.railway.RailwayOwnershipTypes.SectionStatus.ONLINE);
        int inner = railInnerWidth(kind);
        // Dark first, one pixel above and below the colour band, then the semantic colour on top.
        drawSegments(graphics, section.segments(), railOutlineColor(kind), -1, inner + 2);
        drawSegments(graphics, section.segments(), railInnerColor(kind), 0, inner);
        // A tariff is never spelled out along the line; a repeating tick at close zoom says it
        // without turning the map into text.
        if (kind == RailClass.PUBLIC_PAID && this.zoom >= MIN_ZOOM) {
            int spacing = Math.max(24, (int) (14 / Math.max(0.35, this.zoom)));
            for (var segment : section.segments()) {
                int x1 = worldToScreenX(segment.x1()), y1 = worldToScreenY(segment.z1());
                int x2 = worldToScreenX(segment.x2()), y2 = worldToScreenY(segment.z2());
                int length = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
                if (length < spacing) continue;
                for (int step = spacing; step < length; step += spacing) {
                    int px = x1 + (x2 - x1) * step / length, py = y1 + (y2 - y1) * step / length;
                    graphics.fill(px - 1, py, px + 1, py + 3, 0xFF06301A);
                    graphics.fill(px, py + 1, px + 1, py + 2, 0xFFB6F5CD);
                }
            }
        }
    }
    private void drawAnimatedRoute(GuiGraphics graphics,RailwayMapPayload.RouteView route,int color) {
        if(route.segments().isEmpty())return;
        int alpha=reducedMotion?36:(int)(36+12*Math.sin(System.nanoTime()/1_100_000_000.0));
        drawSegments(graphics,route.segments(),(alpha<<24)|(color&0xFFFFFF),-2,9);
        drawSegments(graphics,route.segments(),0xF011181D,-1,5);
        drawSegments(graphics,route.segments(),color,3);
        var first=route.segments().getFirst();var last=route.segments().getLast();
        for(var point:List.of(new double[]{first.x1(),first.z1()},new double[]{last.x2(),last.z2()})) {
            int x=worldToScreenX(point[0]),y=worldToScreenY(point[1]);
            graphics.fill(x-3,y-3,x+4,y+4,0xFF172025);
            graphics.renderOutline(x-3,y-3,7,7,color);graphics.fill(x-1,y-1,x+2,y+2,color);
        }
    }
    private RailwayMapPayload.RouteView route(UUID id){return railwayRoutes.stream().filter(value->value.id().equals(id)).findFirst().orElse(null);}
    private RailwayMapPayload.NetworkView network(UUID id){return railwayNetworks.stream().filter(value->value.id().equals(id)).findFirst().orElse(null);}
    private boolean ownsNetwork(RailwayMapPayload.NetworkView network){if(network==null)return false;if(network.ownerType()==com.example.examplemod.railway.RailwayOwnershipTypes.OwnerType.CITY)return network.ownerId().equals(ownCityId());return Minecraft.getInstance().player!=null&&network.ownerId().equals(Minecraft.getInstance().player.getUUID());}
    private FreightPoint freight(UUID id){return freightPoints.stream().filter(value->value.id.equals(id)).findFirst().orElse(null);}
    private UUID ownCityId(){return peers.stream().filter(CityMapPeersPayload.Peer::own).map(CityMapPeersPayload.Peer::cityId).findFirst().orElse(null);}
    private String cityName(UUID id){return peers.stream().filter(peer->peer.cityId().equals(id)).map(CityMapPeersPayload.Peer::name).findFirst().orElse(shortId(id));}
    private static String shortId(UUID id){return id==null?"?":id.toString().substring(0,8);}

    private static Component infrastructureName(EconomyStatePayload.InfrastructureView value) {
        return Component.translatable(value.type()
                == com.example.examplemod.economy.EconomyTypes.InfrastructureType.CONTRACT_INTAKE
                ? "block.createcityexpansioncore.contract_intake"
                : "block.createcityexpansioncore.warehouse_gateway");
    }

    private static boolean sameTarget(Selection first, Selection second) {
        if (first == second) return true;
        if (first == null || second == null || first.kind() != second.kind()) return false;
        return first.id() != null ? first.id().equals(second.id()) : first.pos().equals(second.pos());
    }

    private static boolean matchesTarget(Selection value, SelectionKind kind, UUID id, BlockPos pos) {
        if (value == null || value.kind() != kind) return false;
        return id != null ? id.equals(value.id()) : pos.equals(value.pos());
    }

    private static boolean matchesTarget(Selection value, SelectionKind kind, UUID id) {
        return value != null && value.kind() == kind && id.equals(value.id());
    }

    private static boolean matchesTargetAt(Selection value, SelectionKind kind, int x, int z) {
        return value != null && value.kind() == kind
                && value.pos().getX() == x && value.pos().getZ() == z;
    }

    private static ItemStack item(String id) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
    }

    private void requestVisibleTiles() {
        long now=System.currentTimeMillis();
        requestedTiles.entrySet().removeIf(e->now-e.getValue()>=REQUEST_TIMEOUT_MILLIS);
        if(now<nextRequestAtMillis||requestedTiles.size()>=64)return;
        int level=terrainLevel();List<TileKey> missing=new ArrayList<>();
        // Small overview first, then intermediate coverage and physical-pixel detail.
        for(int lod=Math.min(7,level+2);lod>=level;lod--) {
            int minX=terrainCoordinate(screenToWorldX(viewportX),lod)-1,maxX=terrainCoordinate(screenToWorldX(viewportX+viewportWidth),lod)+1;
            int minZ=terrainCoordinate(screenToWorldZ(viewportY),lod)-1,maxZ=terrainCoordinate(screenToWorldZ(viewportY+viewportHeight),lod)+1;
            for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++) {
                TileKey key=new TileKey(dimensionId,x,z,lod);CachedTile known=cachedTerrain(key);
                if(!terrainStore().ready(x,z,lod))continue;
                if((known==null||now-known.receivedAtMillis()>=(known.complete()?60_000L:5_000L))&&!requestedTiles.containsKey(key)&&!failedTiles.contains(key))missing.add(key);
            }
        }
        // Cancel obsolete queued view requests; late replies may still enrich the cache.
        requestedTiles.keySet().removeIf(k->{double span=k.span();return (k.tileX()+1)*span<screenToWorldX(viewportX)-span||k.tileX()*span>screenToWorldX(viewportX+viewportWidth)+span||(k.tileZ()+1)*span<screenToWorldZ(viewportY)-span||k.tileZ()*span>screenToWorldZ(viewportY+viewportHeight)+span;});
        missing.sort(Comparator.<TileKey>comparingInt(k->tileCache.containsKey(k)?1:0).thenComparingInt(k->-k.level()).thenComparingDouble(this::distanceFromCenterSquared));
        int count=Math.min(MAX_REQUESTS_PER_RENDER,missing.size());
        for(int i=0;i<count;i++) {
            TileKey key=missing.get(i);if(tileAttempts.merge(key,1,Integer::sum)>3){failedTiles.add(key);continue;}
            CachedTile known=cachedTerrain(key);requestedTiles.put(key,now);
            PacketDistributor.sendToServer(new RequestCityMapTilePayload(pylonPos,dimensionId,key.tileX(),key.tileZ(),key.level(),known==null?0:known.revision(),known==null?new UUID(0,0):known.worldId()));
        }
        if(count>0)nextRequestAtMillis=now+REQUEST_INTERVAL_MILLIS;
    }

    private double distanceFromCenterSquared(TileKey key) {
        double tileCenterX = (double) key.tileX() * key.span()
                + key.span() / 2.0;
        double tileCenterZ = (double) key.tileZ() * key.span()
                + key.span() / 2.0;
        double deltaX = tileCenterX - this.centerX;
        double deltaZ = tileCenterZ - this.centerZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    public int worldToScreenX(double worldX) {
        return (int) Math.floor(this.viewportX + this.viewportWidth / 2.0
                + (worldX - this.centerX) * this.zoom);
    }

    public int worldToScreenY(double worldZ) {
        return (int) Math.floor(this.viewportY + this.viewportHeight / 2.0
                + (worldZ - this.centerZ) * this.zoom);
    }

    public double screenToWorldX(double screenX) {
        return this.centerX + (screenX - this.viewportX - this.viewportWidth / 2.0) / this.zoom;
    }

    public double screenToWorldZ(double screenY) {
        return this.centerZ + (screenY - this.viewportY - this.viewportHeight / 2.0) / this.zoom;
    }

    private int worldToTile(double worldCoordinate) {
        return (int) Math.floor(worldCoordinate / CityMapTilePayload.WORLD_SIZE);
    }

    private boolean insideViewport(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, viewportX, viewportY, viewportWidth, viewportHeight);
    }

    private static boolean inside(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height
    ) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private void centerOnPylon() {
        stopMotion();
        this.centerX = this.pylonPos.getX() + 0.5;
        this.centerZ = this.pylonPos.getZ() + 0.5;
        this.dragging = false;
    }

    private void clampCenter() {
        this.centerX = clamp(this.centerX, -MAX_WORLD_COORDINATE, MAX_WORLD_COORDINATE);
        this.centerZ = clamp(this.centerZ, -MAX_WORLD_COORDINATE, MAX_WORLD_COORDINATE);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record TileKey(ResourceLocation dimensionId, int tileX, int tileZ,int level) {
        TileKey(ResourceLocation dimensionId,int x,int z){this(dimensionId,x,z,0);}
        int span(){return AtlasMapScale.span(level);}
    }

    private record CachedTile(
            int[] colors,
            BitSet contours,
            boolean complete,
            long receivedAtMillis,short[] heights,long revision,UUID worldId
    ) {
    }
}
