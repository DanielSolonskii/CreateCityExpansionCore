package com.example.examplemod.client.map;

import com.example.examplemod.network.CityMapPeersPayload;

import com.example.examplemod.network.CityMapTilePayload;
import com.example.examplemod.network.RequestCityMapTilePayload;
import com.example.examplemod.city.CityColors;
import com.example.examplemod.city.CityPlot;
import com.example.examplemod.city.CityProjectSnapshot;
import com.example.examplemod.network.CityRelationSnapshot;
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
    private List<CityProjectSnapshot> projects = List.of();
    private List<Selection> problemObjects = List.of();
    public List<Selection> problemObjects() { return problemObjects; }

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
        if (plot != null) interruptCamera();
        this.pendingClaim = plot;
        this.overlayLines = List.of();
    }

private boolean showCities = true;
    private boolean showTerritory = true;
    private int territoryMode;
    private boolean projectFocus, projectPicking, showPlotGrid;
    private final java.util.Map<UUID,List<TerritoryPresentation.Edge>> territoryEdges=new java.util.HashMap<>();
    public void setTerritoryPresentation(int mode,boolean plots){territoryMode=Math.clamp(mode,0,2);showPlotGrid=plots;}
    public int territoryMode(){return territoryMode;}
    public boolean plotGrid(){return showPlotGrid;}
    public void setProjectFocus(boolean focus,boolean picking){
        if (picking && !projectPicking) interruptCamera();
        projectFocus=focus;projectPicking=picking;
    }
    private boolean showProjects = true;
    private boolean showRelations;
    private boolean showTracks = true;
    private int trackCount;
    private java.util.function.BiConsumer<GuiGraphics, CityMapPanel> railOverlay;
    private boolean drawInternalControls = true;
    private Selection selection;
    private Selection hoveredSelection;
    private Selection hoverCandidate;
    private long hoverCandidateSince;
    private Focus focus = Focus.WORLD;
    private long routeSelectionStartedAt;
    private final Map<TileKey, CachedTile> tileCache = new java.util.LinkedHashMap<>(64, .75f, true);
    private final Map<TileKey, Boolean> requestedTiles = new HashMap<>();
    /** Lightweight tile handles. GPU storage is shared and capped by TerrainAtlas. */
    private final Map<TileKey, TerrainTileTexture> terrainTextures = new HashMap<>();
    private TerrainTileStore terrainStore;
    private MapViewBindings.Binding binding;

    private void bindTerrain() {
        var next = MapViewBindings.binding(hud, pylonPos, dimensionId);
        if (java.util.Objects.equals(next, binding)) return;
        var store = TerrainCache.store(next);
        if (terrainStore != store) {
            releaseTerrainTextures();
            tileCache.clear();
        }
        terrainStore = store;
        binding = next;
    }
    private CachedTile cachedTerrain(TileKey key) {
        if (terrainStore == null) return null;
        var saved = terrainStore.get(key.tileX(), key.tileZ(), key.level());
        if (saved == null || !saved.worldId().equals(binding.worldId()) || !terrainStore.exchange.acceptsCachedHash(key.storeKey(), saved.revision())) {
            tileCache.remove(key);
            TerrainTileTexture obsolete = terrainTextures.remove(key);
            if (obsolete != null) obsolete.close();
            return null;
        }
        CachedTile local = tileCache.get(key);
        if (local != null && local.colors() == saved.colors()) return local;
        MapDiagnostics.cacheHit();
        if (terrainStore.exchange.checked(key.storeKey()) == 0) com.example.examplemod.client.ClientTelemetry.diskHit();
        local = new CachedTile(saved.colors(), saved.contours(), saved.complete(), saved.receivedAt(),
                saved.heights(), saved.revision(), saved.worldId());
        tileCache.put(key, local);
        evictTerrainTiles();
        return local;
    }
    private final MapLabelLayout labels = new MapLabelLayout();
    private boolean deferLabels;
    public MapLabelLayout labels() { return labels; }
    public void deferLabels() { deferLabels = true; }
    public void drawLabels(GuiGraphics graphics) {
        var font = Minecraft.getInstance().font;
        labels.paint((text,color,x,y) -> graphics.drawString(font,text,x,y,color,true));
        deferLabels = false;
    }
    private final Map<MarkerKey, ScreenPoint> markerPositions = new HashMap<>();

    private double centerX;
    private double centerZ;
    private double zoom = 1.25;
    private boolean dragging;
    private boolean motionEnabled, reducedMotion;
    private long motionFrame, selectionStarted;
    private boolean inertiaEnabled = true, precisionInput;
    private final MapCameraMotion cameraMotion = new MapCameraMotion();
    private final MapProjection projection = new MapProjection();
    private UUID followedTrain;
    private boolean hasFollowSample;
    private double followX, followZ;
    private double pointerYaw;
    private boolean pointerInitialized;
    private final DispatchMotion.Transition[] layerMotion = new DispatchMotion.Transition[Overlay.values().length];
    private final double[] layerOpacity = new double[Overlay.values().length];
    private Overlay paintingLayer;
    private Selection hoverOutline;
    private DispatchMotion.Transition hoverGlow = new DispatchMotion.Transition(0);

    private int overlayMask() {
        int mask=0;for(Overlay layer:Overlay.values())if(isOverlayEnabled(layer))mask|=1<<layer.ordinal();return mask;
    }
    private void withOverlayMask(int mask,Runnable draw) {
        int before=overlayMask();
        try {for(Overlay layer:Overlay.values())setOverlay(layer,(mask&(1<<layer.ordinal()))!=0);draw.run();}
        finally {for(Overlay layer:Overlay.values())setOverlay(layer,(before&(1<<layer.ordinal()))!=0);}
    }
    private void updateLayerMotion() {
        long now=System.nanoTime();
        for(Overlay layer:Overlay.values()) {
            int i=layer.ordinal();double target=isOverlayEnabled(layer)?1:0;
            if(layerMotion[i]==null)layerMotion[i]=new DispatchMotion.Transition(target);
            layerOpacity[i]=layerMotion[i].value(target,now,!motionEnabled||reducedMotion||projectPicking,180);
        }
    }
    private void drawLayer(GuiGraphics g,Overlay layer,Runnable draw) {
        double alpha=layerOpacity[layer.ordinal()];if(alpha<=0)return;
        Overlay before=paintingLayer;paintingLayer=layer;
        try {withOverlayMask(1<<layer.ordinal(),()->MapPresentationDraw.opacity(g,alpha,draw));}
        finally {paintingLayer=before;}
    }
    private void renderDispatchLayers(GuiGraphics g,int mouseX,int mouseY) {
        int mask=overlayMask();
        int cityX=(mask&(1<<Overlay.CITIES.ordinal()))!=0?mouseX:-10000;
        int projectX=(mask&(1<<Overlay.PROJECTS.ordinal()))!=0?mouseX:-10000;
        drawLayer(g,Overlay.TERRITORIES,()->{
            if(showPlotGrid&&plotClaimRequest!=null)renderPlotGrid(g);
            renderPeers(g,-10000,-10000);renderTerritory(g);if(!projectFocus)renderClaimHint(g);
        });
        drawLayer(g,Overlay.RELATIONS,()->renderPeers(g,-10000,-10000));
        drawLayer(g,Overlay.CITIES,()->renderPeers(g,cityX,mouseY));
        drawLayer(g,Overlay.TRACKS,()->{if(this.railOverlay!=null)this.railOverlay.accept(g,this);});
        drawLayer(g,Overlay.CITIES,()->renderMarkers(g,cityX,mouseY));
        drawLayer(g,Overlay.PROJECTS,()->renderMarkers(g,projectX,mouseY));
        // The player has no visibility toggle and is drawn exactly once above the other layers.
        withOverlayMask(0,()->renderMarkers(g,mouseX,mouseY));
    }
    private void renderHoverGlow(GuiGraphics g) {
        Selection candidate=this.hoverCandidate;
        if(candidate!=null) {
            if(!sameTarget(candidate,this.hoverOutline))this.hoverGlow=new DispatchMotion.Transition(0);
            this.hoverOutline=candidate; // A moving train keeps its latest marker position without restarting the glow.
        }
        double strength=this.hoverGlow.value(candidate==null?0:1,System.nanoTime(),reducedMotion,100);
        if(this.hoverOutline==null||strength<=0)return;
        ScreenPoint point=markerPoint(this.hoverOutline.kind(),this.hoverOutline.id(),this.hoverOutline.pos());
        g.renderOutline(point.x()-13,point.y()-13,26,26,MapPalette.argb(DispatchPalette.ACCENT,(int)(180*strength)));
    }

    public void setMotion(boolean enabled, boolean reduced) {
        boolean handingOff = motionEnabled && !enabled;
        motionEnabled = enabled; reducedMotion = reduced;
        if (handingOff) interruptCamera();
        else if (reduced) stopMotion();
    }
    public boolean reducedMotion() { return reducedMotion; }
    public void setInertiaEnabled(boolean enabled) {
        if (inertiaEnabled && !enabled) cameraMotion.cancelCoast();
        inertiaEnabled = enabled;
    }
    public void setPrecisionInput(boolean precise) {
        if (precise && !precisionInput) interruptCamera();
        precisionInput = precise;
    }
    private boolean preciseTool() { return precisionInput || projectPicking || placingPin || measuring || pendingClaim != null; }
    public boolean cameraPrecisionActive() { return preciseTool(); }
    private boolean smoothCamera() { return !reducedMotion && !preciseTool(); }
    private MapCameraMotion.Pose pose() { return new MapCameraMotion.Pose(centerX, centerZ, zoom); }
    private void applyPose(MapCameraMotion.Pose pose) {
        centerX = pose.x(); centerZ = pose.z(); zoom = pose.zoom();
        clampCenter();
    }
    private void stopMotion() { cameraMotion.stop(); }
    public void interruptCamera() {
        followedTrain = null;
        hasFollowSample = false;
        dragging = false;
        stopMotion();
    }
    public UUID followedTrain() { return followedTrain; }
    public void toggleFollow(UUID train) {
        boolean active = train != null && !train.equals(followedTrain);
        interruptCamera();
        if (active && !preciseTool()) followedTrain = train;
    }
    private void animateTo(double x, double z, double scale) {
        var target = new MapCameraMotion.Pose(x,z,AtlasMapScale.clamp(scale));
        if (smoothCamera()) cameraMotion.focus(pose(),target,System.nanoTime());
        else { stopMotion(); applyPose(target); }
    }
    public void followPosition(double x, double z) {
        if (followedTrain == null || preciseTool()) return;
        followX = x;
        followZ = z;
        hasFollowSample = true;
    }
    private void updateMotion() {
        long now=System.nanoTime();
        double dt=motionFrame==0?0:(now-motionFrame)/1e9; motionFrame=now;
        var movement=cameraMotion.sample(now);
        if (movement!=null) applyPose(movement);
        if (hasFollowSample) {
            double blend = smoothCamera() ? MapMotion.blend(dt,18) : 1;
            centerX = MapCameraMotion.followAxis(centerX,followX,viewportWidth*.12/zoom,blend);
            centerZ = MapCameraMotion.followAxis(centerZ,followZ,viewportHeight*.12/zoom,blend);
            clampCenter();
            hasFollowSample = false;
        }
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



    private final ItemStack cityIcon = item("createcityexpansioncore:city_pylon");
    private final ItemStack mineIcon = item("createcityexpansioncore:mine_controller");

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
        setProjects(projects);
        centerOnPylon();
    }

    public void setProjects(List<CityProjectSnapshot> projects) {
        if (this.projects.equals(projects)) {
            return;
        }
        this.projects = List.copyOf(projects);
        // Industry status is the warning source for this panel. Rebuild once per changed snapshot.
        problemObjects = this.projects.stream().filter(project -> dimensionId.equals(project.dimensionId()))
                .filter(project -> !"RUNNING".equals(project.status())).map(this::projectSelection)
                .sorted(Comparator.comparingLong(value -> value.pos().asLong())).toList();
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
            case RELATIONS -> this.showRelations = enabled;
        }
    }

    public boolean isOverlayEnabled(Overlay overlay) {
        return switch (overlay) {
            case CITIES -> this.showCities;
            case TERRITORIES -> this.showTerritory;
            case PROJECTS -> this.showProjects;
            case TRACKS -> this.showTracks;
            case RELATIONS -> this.showRelations;
        };
    }

    /** Rail geometry comes from the logistics overlay; the panel only owns when it is painted. */
    public void setRailOverlay(java.util.function.BiConsumer<GuiGraphics, CityMapPanel> overlay) {
        this.railOverlay = overlay;
    }

    public void setTrackCount(int count) { this.trackCount = Math.max(0, count); }

    public Selection getSelection() {
        return this.selection;
    }

    /** Resolve a remembered target against this view's current data and permissions. */
    public Selection resolveSelection(SelectionKind kind, UUID id, BlockPos pos) {
        switch (kind) {
            case PLACE -> { return com.example.examplemod.client.screen.AtlasPlaces.selection(id,dimensionId); }
            case PLAYER -> {
                Selection player = playerSelection();
                if (matchesTarget(player, kind, id, pos)) return player;
            }
            case LOGISTICS -> { return com.example.examplemod.client.logistics.LogisticsMapOverlay.resolveMapSelection(id, dimensionId); }
            case PROJECT -> { return com.example.examplemod.client.screen.AtlasSearch.projectSelection(id, dimensionId); }
            case CITY -> {
                for (var peer : peers) if (peer.cityId().equals(id) || id == null && atAnchor(peer) && pylonPos.equals(pos)) return peerSelection(peer);
                if (id == null && hasCityAnchor() && pylonPos.equals(pos))
                    return new Selection(kind, null, Component.translatable("gui.createcityexpansioncore.map.city_pylon"),
                            pylonPos, true, List.of(ownTerritoryDetails()), cityIcon, false);
            }
            case INDUSTRY -> {
                for (var project : projects)
                    if (project.dimensionId().equals(dimensionId) && project.projectId().equals(id)) return projectSelection(project);
            }
            case WAYPOINT -> {
                for (int i = 0; i < personalPins.size(); i++) {
                    Selection pin = pinSelection(i);
                    if (matchesTarget(pin, kind, id, pos)) return pin;
                }
            }
        }
        return null;
    }

    public boolean isMapDragging() { return dragging; }

    public Selection getHoveredSelection() {
        return this.hoveredSelection;
    }

    public void clearSelection() {
        this.selection = null;
    }

    public void setSelection(Selection value) {

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

for (CityProjectSnapshot project : this.projects) {
            String translated = Component.translatable("gui.createcityexpansioncore.project.industrial_mine").getString();
            if (this.dimensionId.equals(project.dimensionId())
                    && translated.toLowerCase(Locale.ROOT).contains(query)) matches.add(projectSelection(project));
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
        interruptCamera();
        animateTo(pos.getX()+.5, pos.getZ()+.5, zoom);
    }

/**
     * Масштаб интерфейса. Панель рисуется внутри pose().scale(...),
     * а GuiGraphics.enableScissor ожидает экранные координаты.
     */
    public void setGuiScale(float guiScale) {
        this.guiScale = guiScale <= 0.0F ? 1.0F : guiScale;
    }

    private boolean hud;
    private long hudFrame;
    public void renderMinimap(GuiGraphics g, int x, int y, int size, double scale) {
        hud = true;
        drawInternalControls = false;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        double tx = mc.player.getX(), tz = mc.player.getZ();
        long now=System.nanoTime();
        if (reducedMotion || hudFrame == 0 || Math.hypot(centerX-tx,centerZ-tz) > 64) { centerX=tx; centerZ=tz; }
        else {
            double blend=MapMotion.blend((now-hudFrame)/1e9,18);centerX+=(tx-centerX)*blend;centerZ+=(tz-centerZ)*blend;
        }
        hudFrame=now;
        zoom=scale;
        render(g,x,y,size,size,-10000,-10000);
    }
    private float guiScale = 1.0F;
    private boolean drewSurvey;

    void applyDisplaySettings(MinimapPreferences display) {
        if (reducedMotion != display.reducedMotion) setMotion(motionEnabled, display.reducedMotion);
        setInertiaEnabled(display.inertia);
        setSmoothTerrain(display.smoothTerrain);
    }

    public void render(
            GuiGraphics guiGraphics,
            int x,
            int y,
            int width,
            int height,
            int mouseX,
            int mouseY
    ) {
        long started = MapDiagnostics.beginDraw();
        drewSurvey = false;
        try {
            renderFrame(guiGraphics, x, y, width, height, mouseX, mouseY);
            if (MapDiagnostics.awaitingFirstDraw()) {
                if (drewSurvey) MapDiagnostics.terrainDrawn(true);
                else if (confirmedUnsurveyedViewport()) MapDiagnostics.terrainDrawn(false);
            }
        } finally {
            MapDiagnostics.endDraw(started);
        }
    }

    private boolean confirmedUnsurveyedViewport() {
        if (terrainStore == null || lastViewport.isEmpty()) return false;
        int level = terrainLevel();
        int minX=terrainCoordinate(screenToWorldX(viewportX),level), maxX=terrainCoordinate(screenToWorldX(viewportX+viewportWidth-1),level);
        int minZ=terrainCoordinate(screenToWorldZ(viewportY),level), maxZ=terrainCoordinate(screenToWorldZ(viewportY+viewportHeight-1),level);
        for (int z=minZ;z<=maxZ;z++) for (int x=minX;x<=maxX;x++) {
            var key = new TileKey(dimensionId,x,z,level);
            var texture = terrainTextures.get(key);
            if (texture == null || texture.id() == null || texture.hasSurvey() || terrainStore.exchange.checked(key.storeKey()) == 0) return false;
        }
        return true;
    }
    private void renderFrame(GuiGraphics guiGraphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        MinimapClient.applyDisplay(this);
        bindTerrain();
        this.viewportX = x;
        this.viewportY = y;
        this.viewportWidth = width;
        this.viewportHeight = Math.max(1, this.drawInternalControls ? height - 19 : height);
        this.centerButtonX = x + width - 58;
        this.centerButtonY = y + height - 15;
        this.layerButtonsX = x + width - 166;
        this.layerButtonsY = y + 4;
        updateMotion();
        projection.frame(centerX,centerZ,zoom,viewportX,viewportY,viewportWidth,viewportHeight);
        updateLayerMotion();
        labels.begin(x,y,width,viewportHeight);
        if(motionEnabled&&!projectPicking) {
            int visualMask=0;for(Overlay layer:Overlay.values())if(layerOpacity[layer.ordinal()]>0)visualMask|=1<<layer.ordinal();
            withOverlayMask(visualMask,this::layoutMarkers);
        } else layoutMarkers();
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

            // Terrain owns a complete, flushed pass. Its texture uploads must never share an
            // unfinished GUI batch with city/player markers or the world-border overlay.
            guiGraphics.drawManaged(() -> renderTerrain(guiGraphics));
            guiGraphics.drawManaged(() -> {
                if (hud) return;
                if (this.focus == Focus.ENERGY) {
                    guiGraphics.fill(x, y, x + width, y + this.viewportHeight, 0x32101618);
                }
                if (this.showCoordinateGrid) renderGrid(guiGraphics);
                renderSnapshotNotice(guiGraphics);
                if(motionEnabled&&!projectPicking)renderDispatchLayers(guiGraphics,mouseX,mouseY);
                else {
                if (this.showTerritory && this.showPlotGrid && this.plotClaimRequest != null) renderPlotGrid(guiGraphics);
                // Соседи рисуются ДО своей территории: собственный город
                // остаётся главным акцентом, чужие — фоном.
                if (this.showTerritory || this.showCities || this.projectPicking) renderPeers(guiGraphics, mouseX, mouseY);
                if (this.showTerritory || this.projectPicking) {
                    if (projectPicking) renderConstructionMask(guiGraphics);
                    renderTerritory(guiGraphics);
                    if (!projectFocus) renderClaimHint(guiGraphics);
                }
                renderCityTooltips(guiGraphics, mouseX, mouseY);
                // Рельсы — часть полотна карты, а не маркеров: города и поезда
                // должны оставаться поверх путей.
                if (this.showTracks && this.railOverlay != null) this.railOverlay.accept(guiGraphics, this);
                renderMarkers(guiGraphics, mouseX, mouseY);
                }
                renderCityTooltips(guiGraphics, mouseX, mouseY);
                if(motionEnabled)renderHoverGlow(guiGraphics);
                // Placement previews and project markers must stay visible over city/player icons.
                if (this.visualOverlay != null) this.visualOverlay.accept(guiGraphics);
                if (!hud && Minecraft.getInstance().level != null && dimensionId.equals(Minecraft.getInstance().level.dimension().location()) && !projectPicking)
                    com.example.examplemod.client.screen.MapSocialLayer.render(guiGraphics, this, viewportX, viewportY, viewportWidth, viewportHeight, mouseX, mouseY);
                else com.example.examplemod.client.screen.MapSocialLayer.clear(this);
                renderPersonalTools(guiGraphics, mouseX, mouseY);
                if (placingPin && insideViewport(mouseX, mouseY)) {
                    guiGraphics.renderOutline(mouseX - 4, mouseY - 4, 9, 9, MapPalette.SELECTED);
                    var label = Component.literal((int) Math.floor(screenToWorldX(mouseX)) + " / " + (int) Math.floor(screenToWorldZ(mouseY)));
                    var font = Minecraft.getInstance().font;
                    int lx = Math.max(viewportX + 3, Math.min(mouseX + 10, viewportX + viewportWidth - font.width(label) - 4));
                    int ly = Math.max(viewportY + 3, Math.min(mouseY + 12, viewportY + viewportHeight - 12));
                    guiGraphics.fill(lx - 3, ly - 3, lx + font.width(label) + 3, ly + 11, MapPalette.PAPER_SUNK);
                    guiGraphics.drawString(font, label, lx, ly, MapPalette.TEXT, false);
                }
                if (motionEnabled && !reducedMotion && selection != null) {
                    double t = (System.nanoTime() - selectionStarted) / 400_000_000.0;
                    if (t >= 0 && t < 1) {
                        ScreenPoint selectedPoint = markerPoint(selection.kind(), selection.id(), selection.pos());
                        int sx = selectedPoint.x(), sy = selectedPoint.y();
                        int r = 12 + (int) (12 * MapMotion.ease(t)), color = ((int) (170 * (1 - t)) << 24) | (MapPalette.SELECTED & 0xFFFFFF);
                        for (int a = 0; a < 48; a++) {
                            double angle = a * Math.PI / 24;
                            int px = sx + (int) Math.round(Math.cos(angle) * r), py = sy + (int) Math.round(Math.sin(angle) * r);
                            guiGraphics.fill(px, py, px + 1, py + 1, color);
                        }
                    }
                }
            });
            if (!projectPicking && !preciseTool())
                guiGraphics.drawManaged(() -> com.example.examplemod.client.journey.JourneyMapLayer.render(guiGraphics, this, dimensionId, viewportX, viewportY, viewportWidth, viewportHeight, mouseX, mouseY, hud));
            else com.example.examplemod.client.journey.JourneyMapLayer.clear(this);
            guiGraphics.drawManaged(() -> MapBorderLayer.render(guiGraphics, this, dimensionId, x, y, width, viewportHeight, mouseX, mouseY));
            if (hud) {
                var mc = Minecraft.getInstance();
                MapMarkerRenderer.renderPlayer(guiGraphics, worldToScreenX(mc.player.getX()), worldToScreenY(mc.player.getZ()), this.playerIcon, false, false, mc.player.getYRot() + 180);
            }
        } finally {
            guiGraphics.disableScissor();
        }

        if (hud) { drawLabels(guiGraphics); return; }
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
        if (this.drawInternalControls || this.showOverview)
            labels.reserve(chromeGeometry.minimapX(),chromeGeometry.minimapY(),chromeGeometry.minimapSize(),chromeGeometry.minimapSize());
        if (drawInternalControls) {
            labels.reserve(chromeGeometry.stampX(),chromeGeometry.stampY(),chromeGeometry.stampWidth(),26);
            labels.reserve(chromeGeometry.legendX(),chromeGeometry.legendY(),chromeGeometry.legendWidth(),chromeGeometry.legendRows()*chromeGeometry.legendRowHeight()+12);
        }
        labels.reserve(chromeGeometry.scaleX(),chromeGeometry.scaleY()-10,chromeGeometry.scaleWidth(),24);
        labels.reserve(chromeGeometry.legendX(),chromeGeometry.legendY()-22-MapPalette.MICRO,22,22);
        if (!deferLabels) drawLabels(guiGraphics);
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
        return acceptTerrain(payload);
    }

    public static void invalidate(com.example.examplemod.network.TerrainInvalidatedPayload payload) {
        var view = MapViewBindings.find(payload.viewId());
        if (view == null || !view.worldId().equals(payload.worldId()) || !view.dimension().equals(payload.dimension())) return;
        var store = TerrainCache.store(view);
        if (store != null) store.exchange.invalidate(new TerrainTileStore.Key(payload.tileX(), payload.tileZ(), payload.level()),payload.version());
    }

    public static boolean acceptTerrain(CityMapTilePayload payload) {
        var view = MapViewBindings.find(payload.viewId());
        if (view == null || !view.worldId().equals(payload.worldId()) || !view.dimension().equals(payload.dimensionId())
                || !view.anchor().equals(payload.pylonPos())) {
            MapDiagnostics.stale(); return false;
        }
        var store = TerrainCache.store(view);
        if (store == null) return false;
        var key = new TerrainTileStore.Key(payload.tileX(),payload.tileZ(),payload.level());
        var previous = store.peek(key);
        boolean matching = previous != null && previous.revision() == payload.revision() && previous.worldId().equals(payload.worldId());
        long now = net.minecraft.Util.getMillis();
        if (!store.exchange.accept(key, view.viewId(), payload.requestId(), payload.version(), payload.pending(),
                payload.unchanged(), payload.revision(), matching, now)) {
            if (!payload.pending()) MapDiagnostics.stale();
            return false;
        }
        if (!payload.unchanged()) store.put(key.x(),key.z(),key.level(),new TerrainTileStore.Tile(payload.colors(),
                payload.contours(),payload.complete(),now,payload.heights(),payload.revision(),payload.worldId()));
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (insideViewport(mouseX,mouseY)) interruptCamera();
        if (button == 0 && !projectPicking && !preciseTool() && pendingClaim == null && !placingPin && !measuring && insideViewport(mouseX,mouseY)
                && com.example.examplemod.client.journey.JourneyMapLayer.click(this,mouseX,mouseY)) return true;
        if(button==1 && insideViewport(mouseX,mouseY) && !MapBorderLayer.available(dimensionId,screenToWorldX(mouseX),screenToWorldZ(mouseY))) return true;
        if(button==0&&!preciseTool()&&insideViewport(mouseX,mouseY)&&com.example.examplemod.client.screen.MapSocialLayer.click(this,mouseX,mouseY))return true;
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
        Selection hit = findSelection(mouseX, mouseY);
        if (hit != null) {
            setSelection(hit);
            if (hit.kind() != SelectionKind.WAYPOINT) beginPan();
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

if (insideViewport(mouseX, mouseY)) {
            this.selection = null;
            beginPan();
            return true;
        }
        return false;
    }

    private Selection findSelection(double mouseX, double mouseY) {
        if (!insideViewport(mouseX,mouseY)) return null;
        int markerRadius = AtlasPlaceIcon.radius(zoom);
        for (int i = personalPins.size() - 1; i >= 0; i--) {
            var pin = personalPins.get(i);
            int x = worldToScreenX(pin.x() + .5), y = worldToScreenY(pin.z() + .5);
            if (insideViewport(x,y) && AtlasPlaceIcon.contains(mouseX,mouseY,x,y,markerRadius)) return pinSelection(i);
        }
        if(placesVisible())for(var place:com.example.examplemod.client.screen.AtlasPlaces.selections(dimensionId).reversed()){
            int x=worldToScreenX(place.pos().getX()+.5),y=worldToScreenY(place.pos().getZ()+.5);
            if(insideViewport(x,y) && AtlasPlaceIcon.contains(mouseX,mouseY,x,y,markerRadius))return place;
        }
        Selection player = playerSelection();
        if (player != null) {
            ScreenPoint point = markerPoint(player.kind(), player.id(), player.pos());
            if (inside(mouseX, mouseY, point.x() - 13, point.y() - 13, 26, 26)) return player;
        }

        if (showProjects && zoom>=MIN_ZOOM) for (var project : projects.reversed()) {
            if (!dimensionId.equals(project.dimensionId())) continue;
            var point=markerPoint(SelectionKind.INDUSTRY,project.projectId(),project.controllerPos());
            int radius=projectRadius(project);
            if (inside(mouseX,mouseY,point.x()-radius,point.y()-radius,2*radius,2*radius)) return projectSelection(project);
        }

        ScreenPoint ownPoint = markerPoint(SelectionKind.CITY, null, this.pylonPos);
        if (this.showCities && hasCityAnchor() && insideCityMarker(mouseX, mouseY, ownPoint.x(), ownPoint.y())) {
            for (var peer : this.peers) if (atAnchor(peer)) return peerSelection(peer);
            if (ownPeer() != null) return peerSelection(ownPeer());
            return new Selection(SelectionKind.CITY, null,
                    Component.translatable("gui.createcityexpansioncore.map.city_pylon"), this.pylonPos, true,
                    List.of(ownTerritoryDetails()), this.cityIcon, false);
        }
        if (this.showCities) for (CityMapPeersPayload.Peer peer : this.peers.reversed()) {
            if (atAnchor(peer)) continue;
            Selection candidate = peerSelection(peer);
            ScreenPoint point = markerPoint(candidate.kind(), candidate.id(), candidate.pos());
            if (insideCityMarker(mouseX, mouseY, point.x(), point.y())) return candidate;
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

        markerPositions.clear();
        entries.sort(Comparator.comparingInt(MarkerEntry::priority).reversed());
        for (MarkerEntry entry : entries) {
            int x = worldToScreenX(entry.worldX()), y = worldToScreenY(entry.worldZ());
            var chosen = new ScreenPoint(x,y);
            for (int[] offset : MARKER_OFFSETS) {
                int cx = x+offset[0], cy = y+offset[1];
                if (labels.fits(cx-11,cy-11,MARKER_SPACING,MARKER_SPACING)) {
                    chosen = new ScreenPoint(cx,cy);
                    break;
                }
            }
            markerPositions.put(entry.key(),chosen);
            labels.reserve(chosen.x()-11,chosen.y()-11,MARKER_SPACING,MARKER_SPACING);
        }
    }

    private void addMarker(List<MarkerEntry> entries, SelectionKind kind, UUID id, BlockPos pos,
                           double worldX, double worldZ, int basePriority) {
        int x = worldToScreenX(worldX);
        int y = worldToScreenY(worldZ);
        if (x < this.viewportX - 40 || x > this.viewportX + this.viewportWidth + 40
                || y < this.viewportY - 40 || y > this.viewportY + this.viewportHeight + 40) return;
        MarkerKey key = new MarkerKey(kind, id, pos.immutable());
        int priority = matchesTarget(selection,kind,id,pos) ? 300 : matchesTarget(hoveredSelection,kind,id,pos) ? 200 : basePriority;
        entries.add(new MarkerEntry(key, worldX, worldZ, priority));
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
        applyPose(cameraMotion.drag(pose(),dragX,dragY,System.nanoTime()));
        return true;
    }

    public void beginPan() {
        interruptCamera();
        dragging = true;
        cameraMotion.beginDrag(System.nanoTime());
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.dragging) {
            this.dragging = false;
            cameraMotion.release(pose(),System.nanoTime(),inertiaEnabled && smoothCamera());
            return true;
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        if (!insideViewport(mouseX, mouseY) || !Double.isFinite(scrollY) || scrollY == 0.0) {
            return false;
        }
        followedTrain = null;
        dragging = false;
        applyPose(cameraMotion.scroll(pose(),screenToWorldX(mouseX),screenToWorldZ(mouseY),
                projection.offsetX(mouseX),projection.offsetY(mouseY),scrollY,smoothCamera(),System.nanoTime()));
        return true;
    }

    private int displayLod = -1, previousLod = -1;
    private long lodChangedAt;
    private boolean smoothTerrain;

    public void setSmoothTerrain(boolean smooth) { smoothTerrain = smooth; }

    private int terrainLevel() {
        int next=AtlasMapScale.displayLevel(zoom, viewportWidth, viewportHeight,
                Minecraft.getInstance().getWindow().getGuiScale()*guiScale, displayLod);
        if (next != displayLod) {
            previousLod=displayLod; displayLod=next; lodChangedAt=System.nanoTime();
        }
        return next;
    }
    private int terrainCoordinate(double world,int level){return (int)Math.floor(world/AtlasMapScale.span(level));}
    private void renderTerrain(GuiGraphics graphics) {
        if (terrainStore == null) return;
        TerrainAtlas.begin();
        int level=terrainLevel();
        double left=screenToWorldX(viewportX), right=screenToWorldX(viewportX+viewportWidth);
        double top=screenToWorldZ(viewportY), bottom=screenToWorldZ(viewportY+viewportHeight);
        var finerBranches = new HashSet<TileKey>();
        for (var entry : terrainTextures.entrySet()) {
            TileKey key=entry.getKey();
            double span=key.span();
            if ((key.tileX()+1)*span<=left || key.tileX()*span>=right ||
                    (key.tileZ()+1)*span<=top || key.tileZ()*span>=bottom || entry.getValue().id()==null) continue;
            // Prioritise visible fallbacks for LRU, but leave room for the new target LOD.
            entry.getValue().keepVisible(key.level()==level);
            while (key.level()<level) {
                key=terrainParent(key);
                if (!finerBranches.add(key)) break;
            }
        }
        int budget=MAX_TILE_REBUILDS_PER_FRAME;
        long now=System.nanoTime(), deadline=now+2_000_000L;
        var centre=new TileKey(dimensionId,terrainCoordinate(centerX,level),terrainCoordinate(centerZ,level),level);
        budget=renderTerrainTile(centre,budget);
        for (int lod=level;lod<=7;lod++) {
            int minX=terrainCoordinate(left,lod),maxX=terrainCoordinate(right,lod);
            int minZ=terrainCoordinate(top,lod),maxZ=terrainCoordinate(bottom,lod);
            for (int z=minZ;z<=maxZ;z++) for (int x=minX;x<=maxX;x++) {
                var key=new TileKey(dimensionId,x,z,lod);
                if (key.equals(centre)) continue;
                if (budget>0 && System.nanoTime()>=deadline) budget=0;
                budget=renderTerrainTile(key,budget);
            }
        }
        int minX=terrainCoordinate(left,level),maxX=terrainCoordinate(right,level);
        int minZ=terrainCoordinate(top,level),maxZ=terrainCoordinate(bottom,level);
        // The paper is needed only where the current resolution has no fully covered texture.
        for (int z=minZ;z<=maxZ;z++) for (int x=minX;x<=maxX;x++) {
            var key=new TileKey(dimensionId,x,z,level);
            var texture=terrainTextures.get(key);
            if (texture==null || !texture.opaque()) renderMissingTile(graphics,key,requestedTiles.containsKey(key));
        }
        // Fade only between two ready, fully surveyed images on an actual LOD transition.
        // The usual upload, same-content refresh and texture reload all switch atomically.
        float transition=motionEnabled && !reducedMotion && !preciseTool() && previousLod>level
                ? (float)MapMotion.ease((now-lodChangedAt)/100_000_000.0) : 1f;
        List<TileKey> fading=null;
        for (int z=minZ;z<=maxZ;z++) for (int x=minX;x<=maxX;x++) {
            var key=new TileKey(dimensionId,x,z,level);
            var exact=terrainTextures.get(key);
            if (exact!=null && exact.id()!=null) {
                if (transition<1 && exact.opaque() && drawParent(key,true)) {
                    if (fading==null) fading=new ArrayList<>();
                    fading.add(key);
                } else queueTerrain(exact,key,0,0,32,false,1f);
            } else drawFallback(key,finerBranches);
        }
        TerrainAtlas.flush(graphics);
        if (fading!=null) {
            for (var key:fading) queueTerrain(terrainTextures.get(key),key,0,0,32,false,transition);
            TerrainAtlas.flush(graphics);
        }
    }

    private static TileKey terrainParent(TileKey key) {
        return new TileKey(key.dimensionId(),Math.floorDiv(key.tileX(),2),Math.floorDiv(key.tileZ(),2),key.level()+1);
    }

    /** Subdivide only branches containing a retained finer tile. The leaves cover disjoint rectangles. */
    private void drawFallback(TileKey key, Set<TileKey> finerBranches) {
        var exact=terrainTextures.get(key);
        if (exact!=null && exact.id()!=null) {
            queueTerrain(exact,key,0,0,32,false,1f);
        } else if (key.level()>0 && finerBranches.contains(key)) {
            for (int dz=0;dz<2;dz++) for (int dx=0;dx<2;dx++)
                drawFallback(new TileKey(dimensionId,2*key.tileX()+dx,2*key.tileZ()+dz,key.level()-1),finerBranches);
        } else drawParent(key,false);
    }

    private boolean drawParent(TileKey key, boolean requireOpaque) {
        for (int lod=key.level()+1;lod<=7;lod++) {
            var crop=AtlasMapScale.ancestor(key.tileX(),key.tileZ(),lod-key.level());
            var parent=new TileKey(dimensionId,crop.x(),crop.z(),lod);
            var texture=terrainTextures.get(parent);
            if (texture==null || texture.id()==null) continue;
            if (requireOpaque && !texture.opaque()) return false;
            return queueTerrain(texture,key,crop.u(),crop.v(),crop.size(),true,1f);
        }
        return false;
    }
    private int renderTerrainTile(TileKey key,int budget) {
        CachedTile tile=cachedTerrain(key);
        if(tile==null)return budget;
        TerrainTileTexture texture=this.terrainTextures.computeIfAbsent(key,ignored->{
            TerrainTileTexture saved=TerrainCache.take(terrainStore,key.tileX(),key.tileZ(),key.level());
            if (saved!=null) { saved.neighbourChanged(); return saved; }
            return new TerrainTileTexture("ccec_city_map/"+this.dimensionId.getNamespace()+"_"+this.dimensionId.getPath()+"_"+key.level()+"_"+key.tileX()+"_"+key.tileZ());
        });
        texture.useSource(tile.revision(), tile.colors());
        if(!texture.matches(1) && budget>0) {
            budget--;
            texture.build(tile.colors(),tile.contours(),1,tile.heights(),key.level(),terrainNeighbours(key));
        } else if (smoothTerrain && budget>1 && texture.needsGutter()) {
            budget--;
            texture.refreshGutter(terrainNeighbours(key));
        }
        return budget;
    }

    private TerrainTileTexture[] terrainNeighbours(TileKey key) {
        var neighbours=new TerrainTileTexture[9];
        for (int dz=-1;dz<=1;dz++) for(int dx=-1;dx<=1;dx++) {
            if (dx==0 && dz==0) continue;
            neighbours[(dz+1)*3+dx+1]=terrainTextures.get(new TileKey(dimensionId,key.tileX()+dx,key.tileZ()+dz,key.level()));
        }
        return neighbours;
    }
    private boolean queueTerrain(TerrainTileTexture texture,TileKey key,float u,float v,float size,boolean coarse,float alpha) {
        double left=worldToScreenXExact((double)key.tileX()*key.span());
        double top=worldToScreenZExact((double)key.tileZ()*key.span());
        double right=worldToScreenXExact((double)(key.tileX()+1)*key.span());
        double bottom=worldToScreenZExact((double)(key.tileZ()+1)*key.span());
        if(right<=viewportX||left>=viewportX+viewportWidth||bottom<=viewportY||top>=viewportY+viewportHeight)return false;
        texture.draw(left,top,right,bottom,u,v,size,smoothTerrain,coarse,alpha);
        if (!drewSurvey && alpha>0) drewSurvey=texture.hasSurvey(u,v,size,coarse);
        return true;
    }

    /**
     * Returns textures to the bounded shared pool and schedules a raw-data disk checkpoint.
     * Reopening the same world reuses both samples and already uploaded textures.
     */
    public void releaseTerrainTextures() {
        requestedTiles.clear();
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
        double span = key.span();
        double originX = key.tileX() * span;
        double originZ = key.tileZ() * span;
        float left = (float)worldToScreenXExact(originX);
        float top = (float)worldToScreenZExact(originZ);
        float right = (float)worldToScreenXExact(originX + span);
        float bottom = (float)worldToScreenZExact(originZ + span);
        int parity = (key.tileX() + key.tileZ()) & 1;
        /*
         * Незаведённый тайл — это бумага, а не пустота: по нему
         * видно, что «сюда ещё не смотрели», и он не спорит с
         * линиями, которые на нём нарисованы.
         */
        int color = terrainStore != null && terrainStore.exchange.failed(key.storeKey()) ? 0xFF30282A : requested ?
                (parity == 0 ? 0xFF202A2E : 0xFF1C2428) : MapSurveyTransform.unexploredPaper(parity);
        var buffer=guiGraphics.bufferSource().getBuffer(net.minecraft.client.renderer.RenderType.gui());
        var matrix=guiGraphics.pose().last().pose();
        buffer.addVertex(matrix,left,top,0).setColor(color);
        buffer.addVertex(matrix,left,bottom,0).setColor(color);
        buffer.addVertex(matrix,right,bottom,0).setColor(color);
        buffer.addVertex(matrix,right,top,0).setColor(color);
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
    private void renderPeers(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        com.example.examplemod.client.AllianceClient.ensure();
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
            AllianceMapPresentation.marker(guiGraphics,markerX,markerY,peer.cityId());
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
            if ((emphasized || this.zoom < 1.1) && name != null && !name.isEmpty() && this.showCities) {
                var priority = matchesTargetAt(selection,SelectionKind.CITY,peer.pylonX(),peer.pylonZ()) ? MapLabelLayout.Priority.SELECTED
                        : emphasized ? MapLabelLayout.Priority.HOVER : peer.master() ? MapLabelLayout.Priority.NODE : MapLabelLayout.Priority.NORMAL;
                labels.offer("city/"+peer.cityId(),name,peer.master()?0xFFFFC766:0xFFC4CDD2,markerX,markerY,
                        Math.max(14,TerritoryPresentation.cityRadius(zoom)+4),Minecraft.getInstance().font.width(name),priority);
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
        if (hasCityAnchor() && this.showCities) {
        ScreenPoint ownMarker = markerPoint(SelectionKind.CITY, null, this.pylonPos);
        int markerX = ownMarker.x();
        int markerY = ownMarker.y();
        drawMarkerLeader(guiGraphics, markerX, markerY,
                worldToScreenX(this.pylonPos.getX() + 0.5), worldToScreenY(this.pylonPos.getZ() + 0.5));
        boolean ownedAnchor = this.peers.stream().anyMatch(peer -> atAnchor(peer) && peer.own());
        if (this.showCities) renderCityMarker(guiGraphics, markerX, markerY, ownedAnchor,
                matchesTarget(this.hoveredSelection, SelectionKind.CITY, null, this.pylonPos),
                matchesTarget(this.selection, SelectionKind.CITY, null, this.pylonPos));
        if(this.showCities)this.peers.stream().filter(this::atAnchor).findFirst().ifPresent(peer->AllianceMapPresentation.marker(guiGraphics,markerX,markerY,peer.cityId()));
        String name = peers.stream().filter(this::atAnchor).findFirst().map(CityMapPeersPayload.Peer::name)
                .orElseGet(() -> Component.translatable("gui.createcityexpansioncore.map.city_pylon").getString());
        labels.offer("city/anchor",name,0xFFF4D49A,markerX,markerY,Math.max(14,TerritoryPresentation.cityRadius(zoom)+4),
                Minecraft.getInstance().font.width(name),matchesTargetAt(selection,SelectionKind.CITY,pylonPos.getX(),pylonPos.getZ())
                        ? MapLabelLayout.Priority.SELECTED : MapLabelLayout.Priority.NODE);
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
            int markerRadius=projectRadius(project);
            labels.reserve(projectX-markerRadius,projectY-markerRadius,2*markerRadius,2*markerRadius);
            MapMarkerRenderer.renderMap(guiGraphics, this.zoom, projectX, projectY, this.mineIcon, true,
                    !"RUNNING".equals(project.status()),
                    matchesTarget(this.hoveredSelection, SelectionKind.INDUSTRY, project.projectId()),
                    matchesTarget(this.selection, SelectionKind.INDUSTRY, project.projectId()));
            if (inside(mouseX,mouseY,projectX-markerRadius,projectY-markerRadius,2*markerRadius,2*markerRadius)) {
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
        BlockPos playerPos = playerPos();
        if (playerPos != null && this.paintingLayer==null) {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            ScreenPoint player = markerPoint(SelectionKind.PLAYER, playerId, playerPos);
            int playerX = player.x();
            int playerY = player.y();
            MapMarkerRenderer.renderPlayer(guiGraphics, playerX, playerY, this.playerIcon,
                    matchesTarget(this.hoveredSelection, SelectionKind.PLAYER, playerId),
                    matchesTarget(this.selection, SelectionKind.PLAYER, playerId), pointerYaw);
        }


    }

    private int projectRadius(CityProjectSnapshot project) {
        if (matchesTarget(selection,SelectionKind.INDUSTRY,project.projectId())) return 12;
        return zoom>=.8 || matchesTarget(hoveredSelection,SelectionKind.INDUSTRY,project.projectId()) ? 11 : 5;
    }

    private void deferTooltip(int mouseX, int mouseY, Component... lines) {
        if (motionEnabled && hoveredSelection==null) return;
        this.pendingTooltip = lines;
        this.pendingTooltipX = mouseX;
        this.pendingTooltipY = mouseY;
        this.tooltipShown = true;
    }

    private void drawMarkerLeader(GuiGraphics graphics, int x, int y, int anchorX, int anchorY) {
        int dx = x - anchorX;
        int dy = y - anchorY;
        if (dx * dx + dy * dy < 28 * 28) return;
        drawLine(graphics, anchorX, anchorY, x, y, 0x907C6C50);
    }

    private void drawLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        var clipped=MapLineClip.clip(x0,y0,x1,y1,viewportX-3,viewportY-3,viewportX+viewportWidth+3,viewportY+viewportHeight+3);
        if(clipped==null)return;
        x0=clipped.x1();y0=clipped.y1();x1=clipped.x2();y1=clipped.y2();
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

        labels.reserve(left,top,boxWidth,boxHeight);
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
            case TRACKS -> this.trackCount;
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

public void setPresentation(boolean grid, boolean overview) { this.showCoordinateGrid = grid; this.showOverview = overview; }
    public boolean coordinateGrid() { return this.showCoordinateGrid; }
    public boolean overviewVisible() { return this.showOverview; }
    public double centerX() { return this.centerX; }
    public boolean focusAlliance(java.util.Set<UUID> cities) {
        var members=this.peers.stream().filter(p->cities.contains(p.cityId())).toList();if(members.isEmpty())return false;
        double minX=members.stream().mapToInt(p->p.pylonX()).min().orElse(0),maxX=members.stream().mapToInt(p->p.pylonX()).max().orElse(0);
        double minZ=members.stream().mapToInt(p->p.pylonZ()).min().orElse(0),maxZ=members.stream().mapToInt(p->p.pylonZ()).max().orElse(0);
        restoreView((minX+maxX)/2,(minZ+maxZ)/2,Math.min(viewportWidth/(maxX-minX+200),viewportHeight/(maxZ-minZ+200)));return true;
    }
    public double centerZ() { return this.centerZ; }
    public double zoom() { return this.zoom; }
    public ResourceLocation dimensionId() { return this.dimensionId; }
    public List<CityMapPeersPayload.Peer> planningTerritories() { return peers; }
    public PlanSurface planningHeight(int x, int z) {
        if (binding==null || terrainStore==null || !binding.equals(MapViewBindings.binding(hud,pylonPos,dimensionId))) return null;
        var key=new TerrainTileStore.Key(Math.floorDiv(x,32),Math.floorDiv(z,32),0);
        var tile=terrainStore.peek(key);
        if (tile==null || !terrainStore.exchange.acceptsCachedHash(key,tile.revision())) return null;
        return PlanSurface.sample(tile,binding.worldId(),x,z,terrainStore.exchange.checked(key),
                terrainStore.exchange.invalidated(key),net.minecraft.Util.getMillis());
    }
    public int viewportLeft() { return this.viewportX; }
    /** Projects a click on the atlas terrain. Icon height is independent of surveyed entrances. */
    public BlockPos placeIconAt(double mouseX,double mouseY,int fallbackY) {
        if(!insideViewport(mouseX,mouseY))return null;
        var chrome=chromeGeometry;
        if(chrome!=null && chrome.minimapSize()>0){
            if(showOverview && inside(mouseX,mouseY,chrome.minimapX(),chrome.minimapY(),chrome.minimapSize(),chrome.minimapSize()))return null;
            if(inside(mouseX,mouseY,chrome.scaleX(),chrome.scaleY()-10,chrome.scaleWidth(),24) ||
                    inside(mouseX,mouseY,chrome.legendX(),chrome.legendY()-22-MapPalette.MICRO,22,22))return null;
        }
        double x=screenToWorldX(mouseX),z=screenToWorldZ(mouseY);
        if(!MapBorderLayer.available(dimensionId,x,z))return null;
        var point=BlockPos.containing(x,fallbackY,z);
        var surface=planningHeight(point.getX(),point.getZ());
        return surface==null || surface.stale()?point:new BlockPos(point.getX(),surface.y()+1,point.getZ());
    }
    public int viewportTop() { return this.viewportY; }
    public int viewportRight() { return this.viewportX + this.viewportWidth; }
    public int viewportBottom() { return this.viewportY + this.viewportHeight; }
    public void restoreView(double x, double z, double zoom) {
        if (!Double.isFinite(x) || !Double.isFinite(z) || !Double.isFinite(zoom)) return;
        interruptCamera();
        this.centerX = x; this.centerZ = z; this.zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM); clampCenter();
    }
    public void centerOnPlayer() { BlockPos player = playerPos(); if (player != null) centerOn(player); }
    public void fitCity() {
        interruptCamera();
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
    public void togglePinTool() { interruptCamera(); this.placingPin = !this.placingPin; this.measuring = false; }
    public void toggleMeasureTool() { interruptCamera(); this.measuring = !this.measuring; this.placingPin = false; this.measureStart = null; this.measureEnd = null; }
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
    private boolean placesVisible(){
        return !projectPicking && focus==Focus.WORLD && Minecraft.getInstance().screen instanceof com.example.examplemod.client.screen.PolishedWorldMapScreen;
    }
    private void renderPersonalTools(GuiGraphics graphics,int mouseX,int mouseY) {
        int markerRadius = AtlasPlaceIcon.radius(zoom);
        if(placesVisible())for(var place:com.example.examplemod.client.screen.AtlasPlaces.selections(dimensionId)){
            int x=worldToScreenX(place.pos().getX()+.5),y=worldToScreenY(place.pos().getZ()+.5);
            if(!insideViewport(x,y))continue;
            boolean pinned=matchesTarget(selection,SelectionKind.PLACE,place.id());
            boolean hovered=matchesTarget(hoveredSelection,SelectionKind.PLACE,place.id());
            var icon=AtlasPlaceIcon.forSelection(place,dimensionId);
            if(icon==null)continue;
            icon.badge(graphics,x,y,markerRadius,pinned,hovered);
            labels.reserve(x-markerRadius,y-markerRadius,2*markerRadius,2*markerRadius);
            String label=place.title().getString();
            labels.offer("place:"+place.id(),label,pinned?DispatchPalette.SELECTED:MapPalette.TEXT,x,y,markerRadius+2,Minecraft.getInstance().font.width(label),
                    pinned?MapLabelLayout.Priority.SELECTED:hovered?MapLabelLayout.Priority.HOVER:MapLabelLayout.Priority.NORMAL);
        }
        for (var pin : personalPins) {
            int x = worldToScreenX(pin.x()+.5), y = worldToScreenY(pin.z()+.5);
            if (!insideViewport(x,y)) continue;
            AtlasPlaceIcon.PERSONAL.badge(graphics,x,y,markerRadius,
                    matchesTargetAt(selection,SelectionKind.WAYPOINT,pin.x(),pin.z()),
                    matchesTargetAt(hoveredSelection,SelectionKind.WAYPOINT,pin.x(),pin.z()));
            labels.reserve(x-markerRadius,y-markerRadius,2*markerRadius,2*markerRadius);
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
        boolean failed=false, loading=false, empty=false, stale=false, cached=false, unknown=false;
        long now=net.minecraft.Util.getMillis();
        int level=terrainLevel();
        int minX=terrainCoordinate(screenToWorldX(viewportX),level),maxX=terrainCoordinate(screenToWorldX(viewportX+viewportWidth),level);
        int minZ=terrainCoordinate(screenToWorldZ(viewportY),level),maxZ=terrainCoordinate(screenToWorldZ(viewportY+viewportHeight),level);
        for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++) {
            var key=new TileKey(dimensionId,x,z,level);
            if (terrainStore == null) { empty=true; continue; }
            var exchange=terrainStore.exchange;
            var k=key.storeKey();
            var tile=tileCache.get(key);
            failed |= exchange.failed(k);
            loading |= exchange.pending(k) || exchange.publishing(k);
            if(tile==null) { empty=true; continue; }
            long checked=exchange.checked(k);
            cached |= checked==0 && !exchange.invalidated(k);
            stale |= exchange.invalidated(k) || checked!=0 && now-checked>=ttl(tile);
            var displayed=terrainTextures.get(key);
            if (displayed==null || displayed.id()==null) loading=true;
            else if (!displayed.current(tile.revision())) stale=true;
            unknown |= checked!=0 && !tile.complete();
        }
        String status=failed?"failed":stale?"stale":cached?"cached":loading?"loading":empty?"waiting":unknown?"unknown":"ready";
        return Component.translatable("gui.createcityexpansioncore.world_map.terrain."+status);
    }

    private static long ttl(CachedTile tile) { return tile!=null && tile.complete()?60_000L:5_000L; }
    public void retryTerrain() {
        if (terrainStore == null) return;
        for (TileKey key : lastViewport) terrainStore.exchange.retry(key.storeKey());
        nextRequestAtMillis = 0;
    }

    public enum Overlay { CITIES, TERRITORIES, PROJECTS, RELATIONS, TRACKS;
        /**
         * Bits are frozen by saved preferences. TRACKS keeps its historical bit 3 and RELATIONS its
         * historical bit 7, so adding the enum constant at the end cannot silently reshuffle either.
         */
        public int preferenceBit(){return this==TRACKS?3:this==RELATIONS?7:ordinal();}
    }

    public enum Focus { WORLD, ENERGY }

    public enum SelectionKind { WAYPOINT, PLAYER, CITY, INDUSTRY, PROJECT, LOGISTICS, PLACE }

    public record Selection(SelectionKind kind, UUID id, Component title, BlockPos pos,
                            boolean own, List<Component> details, ItemStack icon, boolean warning) {
        public Selection {
            details = List.copyOf(details);
        }
    }

    public record ScreenPoint(int x, int y) {}

    private record MarkerKey(SelectionKind kind, UUID id, BlockPos pos) {
    }

    private record MarkerEntry(MarkerKey key, double worldX, double worldZ, int priority) {}

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

/** A Create train the player has not registered yet: card explains the gap and offers the fix. */

private Selection projectSelection(CityProjectSnapshot project) {
        return new Selection(SelectionKind.INDUSTRY, project.projectId(),
                Component.translatable("gui.createcityexpansioncore.project.industrial_mine"),
                project.controllerPos(), true, List.of(
                Component.translatable("gui.createcityexpansioncore.map.resource_richness",
                        Component.translatable("resource.createcityexpansioncore." + project.resourceId().getPath()),
                        Math.round(project.richness() * 100.0)), projectStatus(project.status())),
                this.mineIcon, !"RUNNING".equals(project.status()));
    }

/**
     * Classifies one section from data the server already sends. Deliberately takes primitives so
     * the policy is verified headless instead of only through a rendered frame.
     *
     * <p>An offline section is reported as such whatever its access is: a dark, dead line must not
     * keep advertising a tariff or an ownership as if it were usable.
     */

/** Inner line colour. Paid public track is the one saturated colour on the map, on purpose. */

/** Inner width. Paid and own track are the heaviest, private the lightest. */

private UUID ownCityId(){return peers.stream().filter(CityMapPeersPayload.Peer::own).map(CityMapPeersPayload.Peer::cityId).findFirst().orElse(null);}
    private String cityName(UUID id){return peers.stream().filter(peer->peer.cityId().equals(id)).map(CityMapPeersPayload.Peer::name).findFirst().orElse(shortId(id));}
    private static String shortId(UUID id){return id==null?"?":id.toString().substring(0,8);}

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

    private final Set<TileKey> lastViewport = new HashSet<>();
    private void requestVisibleTiles() {
        if (terrainStore == null) return;
        long now=net.minecraft.Util.getMillis();
        if(now < nextRequestAtMillis) return;
        nextRequestAtMillis=now+(hud?500:REQUEST_INTERVAL_MILLIS);
        TerrainCache.expire(now);
        int level=terrainLevel();
        List<TileKey> missing=new ArrayList<>();
        List<TileKey> margin=new ArrayList<>();
        lastViewport.clear();
        requestedTiles.clear();
        var exchange=terrainStore.exchange;
        for(int lod=Math.min(7,level+2);lod>=level;lod--) {
            int minX=terrainCoordinate(screenToWorldX(viewportX),lod)-1,maxX=terrainCoordinate(screenToWorldX(viewportX+viewportWidth),lod)+1;
            int minZ=terrainCoordinate(screenToWorldZ(viewportY),lod)-1,maxZ=terrainCoordinate(screenToWorldZ(viewportY+viewportHeight),lod)+1;
            for(int z=minZ;z<=maxZ;z++)for(int x=minX;x<=maxX;x++) {
                double span=AtlasMapScale.span(lod);
                if (!MapBorderLayer.intersects(dimensionId,x*span,z*span,(x+1)*span,(z+1)*span)) continue;
                var key=new TileKey(dimensionId,x,z,lod);
                var k=key.storeKey();
                boolean visible=x>minX && x<maxX && z>minZ && z<maxZ;
                if(visible)lastViewport.add(key);
                if (exchange.pending(k)) { if(visible)requestedTiles.put(key,true); continue; }
                var known=cachedTerrain(key);
                if(terrainStore.ready(x,z,lod) && exchange.due(k,now, known==null?0:ttl(known)))
                    (visible?missing:margin).add(key);
            }
        }
        var focus = new TileKey(dimensionId, terrainCoordinate(centerX, level), terrainCoordinate(centerZ, level), level);
        var priority=Comparator.comparing((TileKey key) -> !key.equals(focus))
                .thenComparing(key -> tileCache.containsKey(key))
                .thenComparingInt(k->-k.level()).thenComparingDouble(this::distanceFromCenterSquared);
        missing.sort(priority);
        int budget=Math.min(hud?2:MAX_REQUESTS_PER_RENDER, TerrainExchange.LIMIT-TerrainCache.inflight());
        int visibleCount=missing.size();
        if(visibleCount<budget && !margin.isEmpty()){
            margin.sort(priority);
            int spare=Math.min(Math.min(budget-visibleCount,hud?1:2),margin.size());
            missing.addAll(margin.subList(0,spare));
        }
        int count=Math.min(budget,missing.size());
        for (int i=0;i<count;i++) {
            TileKey key=missing.get(i);
            var k=key.storeKey();
            var known=tileCache.get(key);
            long hash=known==null?0:known.revision();
            boolean retry=exchange.retrying(k);
            var request=exchange.start(k,binding.viewId(),hash,now);
            if(request==null) continue;
            if(i<visibleCount)requestedTiles.put(key,true);
            MapDiagnostics.request(retry);
            PacketDistributor.sendToServer(new RequestCityMapTilePayload(pylonPos,dimensionId,k.x(),k.z(),k.level(),
                    hash,binding.worldId(),binding.viewId(),request.id()));
        }
        MapDiagnostics.queues(TerrainCache.inflight(),TerrainTileTexture.queueSize());
        MapDiagnostics.sampleResources(now,terrainTextures.size());
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
        return (int)Math.floor(worldToScreenXExact(worldX));
    }

    public int worldToScreenY(double worldZ) {
        return (int)Math.floor(worldToScreenZExact(worldZ));
    }

    public double worldToScreenXExact(double worldX) { return projection.screenX(worldX); }
    public double worldToScreenZExact(double worldZ) { return projection.screenY(worldZ); }
    public double screenToWorldX(double screenX) {
        return projection.worldX(screenX);
    }

    public double screenToWorldZ(double screenY) {
        return projection.worldZ(screenY);
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
        interruptCamera();
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
        TerrainTileStore.Key storeKey() { return new TerrainTileStore.Key(tileX,tileZ,level); }
    }

    private record CachedTile(
            int[] colors,
            BitSet contours,
            boolean complete,
            long receivedAtMillis,short[] heights,long revision,UUID worldId
    ) {
    }
}
