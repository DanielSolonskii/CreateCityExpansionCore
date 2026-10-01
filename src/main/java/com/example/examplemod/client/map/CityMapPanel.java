package com.example.examplemod.client.map;

import com.example.examplemod.network.CityMapPeersPayload;

import com.example.examplemod.network.CityMapTilePayload;
import com.example.examplemod.network.RequestCityMapTilePayload;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

public final class CityMapPanel {

    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 4.0;
    private static final double ZOOM_STEP = 1.25;
    private static final double MAX_WORLD_COORDINATE = 29_999_984.0;
    /**
     * Минимальная экранная плотность одной выборки рельефа.
     * Ниже этого рельеф рисуем укрупнёнными блоками — глаз этого
     * не замечает, а число заливок падает в разы при отдалении.
     */
    private static final double MIN_SAMPLE_SCREEN_PX = 2.0;
    /** Terrain texture rebuilds are budgeted per frame so a level-of-detail change never stalls a pan. */
    private static final int MAX_TILE_REBUILDS_PER_FRAME = 4;
    /** Upper bound on cached tiles, so exploring a large world cannot grow the client cache forever. */
    private static final int MAX_CACHED_TILES = 4096;
    private static final int MAX_REQUESTS_PER_RENDER = 4;
    private static final long REQUEST_INTERVAL_MILLIS = 100L;
    private static final long REQUEST_TIMEOUT_MILLIS = 5_000L;
    private static final long HOVER_DELAY_MILLIS = 160L;
    private static final double MEDIUM_DETAIL_ZOOM = 0.80;
    private static final double CLOSE_DETAIL_ZOOM = 1.20;

    private static final int FRAME_COLOR = 0xFF596064;
    private static final int ACCENT_COLOR = 0xFFD09A3A;
    private static final int TERRITORY_FILL = 0x0837A6D1;
    private static final int TERRITORY_BORDER = 0xFF58AAC4;

    /*
     * Соседние города.
     *
     * Свой город — основной акцентный цвет, чужие — приглушённый
     * и заметно менее насыщенный, чтобы территории не сливались
     * в одну пеструю пятнистую массу. Это инфраструктурная карта,
     * а не политическая: чужие города показываются как факт
     * существования, без какой-либо детализации.
     */
    private static final int PEER_TERRITORY_FILL = 0x068E9BA6;
    private static final int PEER_TERRITORY_BORDER = 0xFF657C84;
    private static final int MARKER_SPACING = 22;
    private static final int[][] MARKER_OFFSETS = {
            {0, 0}, {0, -24}, {24, 0}, {0, 24}, {-24, 0},
            {18, -18}, {18, 18}, {-18, 18}, {-18, -18},
            {0, -36}, {36, 0}, {0, 36}, {-36, 0},
            {26, -26}, {26, 26}, {-26, 26}, {-26, -26}
    };

    private final BlockPos pylonPos;
    private final ResourceLocation dimensionId;
    private int territorySize;
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
    private final Map<UUID, TrainPosition> previousTrainPositions = new HashMap<>();
    private long railwaySnapshotAtMillis;
    private boolean showTracks = true;
    private boolean showStations = true;
    private boolean showTrains = true;
    private boolean showTradeInfrastructure = true;
    private boolean showCities = true;
    private boolean showTerritory = true;
    private boolean showProjects = true;
    private boolean showRelations;
    private boolean drawInternalControls = true;
    private Selection selection;
    private Selection hoveredSelection;
    private Selection hoverCandidate;
    private long hoverCandidateSince;
    private Focus focus = Focus.WORLD;
    private long routeSelectionStartedAt;
    private final Map<TileKey, CachedTile> tileCache = new HashMap<>();
    private final Map<TileKey, Long> requestedTiles = new HashMap<>();
    /** One GPU texture per cached tile, built lazily and released when the tile leaves the cache. */
    private final Map<TileKey, TerrainTileTexture> terrainTextures = new HashMap<>();
    private final Map<MarkerKey, ScreenPoint> markerPositions = new HashMap<>();

    private double centerX;
    private double centerZ;
    private double zoom = 1.25;
    private boolean dragging;

    private int viewportX;
    private int viewportY;
    private int viewportWidth;
    private int viewportHeight;
    private int centerButtonX;
    private int centerButtonY;
    private long nextRequestAtMillis;
    private int layerButtonsX;
    private int layerButtonsY;

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

    public CityMapPanel(
            BlockPos pylonPos,
            ResourceLocation dimensionId,
            int territorySize,
            List<CityProjectSnapshot> projects
    ) {
        this.pylonPos = pylonPos.immutable();
        this.dimensionId = dimensionId;
        this.territorySize = territorySize;
        this.projects = List.copyOf(projects);
        centerOnPylon();
    }

    public void setProjects(List<CityProjectSnapshot> projects) {
        if (this.projects.equals(projects)) {
            return;
        }
        this.projects = List.copyOf(projects);
    }

    public void setTerritorySize(int territorySize) {
        this.territorySize = territorySize;
    }

    /**
     * Принимает список соседних городов с сервера.
     *
     * <p>Список неизменяемый и копируется один раз, поэтому в цикле
     * отрисовки нет ни аллокаций, ни работы со словарём.
     */
    public void receivePeers(List<CityMapPeersPayload.Peer> peers) {
        this.peers = List.copyOf(peers);
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
        if (query.isEmpty()) return List.of();
        List<Selection> matches = new ArrayList<>();
        for (CityMapPeersPayload.Peer peer : this.peers) {
            if (peer.name().toLowerCase(Locale.ROOT).contains(query)) matches.add(peerSelection(peer));
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
        return matches.size() <= 6 ? List.copyOf(matches) : List.copyOf(matches.subList(0, 6));
    }

    public void centerOn(BlockPos pos) {
        this.centerX = pos.getX() + 0.5;
        this.centerZ = pos.getZ() + 0.5;
        clampCenter();
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
        layoutMarkers();
        updateHover(insideViewport(mouseX, mouseY) ? findSelection(mouseX, mouseY) : null);

        guiGraphics.fill(x - 1, y - 1, x + width + 1, y + viewportHeight + 1, FRAME_COLOR);
        guiGraphics.fill(x, y, x + width, y + viewportHeight, 0xFF101719);

        requestVisibleTiles();

        guiGraphics.enableScissor(
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
                renderGrid(guiGraphics);
                // Соседи рисуются ДО своей территории: собственный город
                // остаётся главным акцентом, чужие — фоном.
                if (this.showTerritory || this.showCities) renderPeers(guiGraphics);
                if (this.showTerritory) renderTerritory(guiGraphics);
                renderRailway(guiGraphics, mouseX, mouseY);
                renderMarkers(guiGraphics, mouseX, mouseY);
            });
        } finally {
            guiGraphics.disableScissor();
        }

        if (this.drawInternalControls) {
            renderFooter(guiGraphics, x, y + viewportHeight + 4, width, mouseX, mouseY);
        }

        /*
         * Подсказки рисуются после снятия scissor:
         * их не режет область карты и они всегда поверх карты.
         */
        if (this.drawInternalControls && this.pendingTooltip != null) {
            Component[] lines = this.pendingTooltip;
            this.pendingTooltip = null;
            renderClampedTooltip(guiGraphics, lines);
        } else {
            this.pendingTooltip = null;
        }
    }

    public boolean receive(CityMapTilePayload payload) {
        if (!this.pylonPos.equals(payload.pylonPos())
                || !this.dimensionId.equals(payload.dimensionId())) {
            return false;
        }

        TileKey key = new TileKey(payload.dimensionId(), payload.tileX(), payload.tileZ());
        this.requestedTiles.remove(key);
        this.tileCache.put(key, new CachedTile(
                payload.colors(),
                payload.complete(),
                System.currentTimeMillis()
        ));
        // Only this tile changed, so only its texture is stale. Everything else on screen keeps the
        // texture it already has: an incremental update, never a full atlas rebuild.
        TerrainTileTexture texture = this.terrainTextures.get(key);
        if (texture != null) {
            texture.invalidate();
        }
        evictTerrainTiles();
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
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
        if (this.showTradeInfrastructure && this.zoom >= CLOSE_DETAIL_ZOOM) for (EconomyStatePayload.InfrastructureView value : this.economyInfrastructure) {
            if (!value.cityOwned() || !this.dimensionId.equals(value.dimension())) continue;
            Selection candidate = infrastructureSelection(value);
            ScreenPoint point = markerPoint(candidate.kind(), candidate.id(), candidate.pos());
            if (inside(mouseX, mouseY, point.x() - 11, point.y() - 11, 22, 22)) return candidate;
        }
        if(this.showTradeInfrastructure&&this.zoom>=MEDIUM_DETAIL_ZOOM)for(FreightPoint point:this.freightPoints){if(point.dimension.equals(this.dimensionId)){Selection candidate=freightSelection(point);ScreenPoint marker=markerPoint(candidate.kind(),candidate.id(),candidate.pos());if(inside(mouseX,mouseY,marker.x()-11,marker.y()-11,22,22))return candidate;}}
        if(this.showTradeInfrastructure&&this.zoom>=MEDIUM_DETAIL_ZOOM)for(FreightCandidate value:this.freightCandidates){int x=worldToScreenX(value.postbox.getX()+.5),y=worldToScreenY(value.postbox.getZ()+.5);if(inside(mouseX,mouseY,x-8,y-9,16,18))return discoverySelection(value);}
        if (this.showTrains && this.zoom >= CLOSE_DETAIL_ZOOM) for (RailwayTrainSnapshot train : this.railwayTrains) {
            if (train.dimension() == null || !train.dimension().equals(this.dimensionId)) continue;
            Selection candidate = trainSelection(train);
            ScreenPoint point = markerPoint(candidate.kind(), candidate.id(), candidate.pos());
            if (inside(mouseX, mouseY, point.x() - 11, point.y() - 11, 22, 22)) return candidate;
        }
        if (this.showTrains && this.zoom >= CLOSE_DETAIL_ZOOM) for (RailwayTrainCandidate train : this.railwayTrainCandidates) {
            if (train.dimension() == null || !train.dimension().equals(this.dimensionId)) continue;
            Selection candidate = unregisteredTrainSelection(train);
            ScreenPoint point = markerPoint(candidate.kind(), candidate.id(), candidate.pos());
            if (inside(mouseX, mouseY, point.x() - 11, point.y() - 11, 22, 22)) return candidate;
        }
        if (this.showStations && this.zoom >= MEDIUM_DETAIL_ZOOM) for (RailwayStationSnapshot station : this.railwayStations) {
            if (!station.dimension().equals(this.dimensionId)) continue;
            Selection candidate = stationSelection(station);
            ScreenPoint point = markerPoint(candidate.kind(), candidate.id(), candidate.pos());
            if (inside(mouseX, mouseY, point.x() - 11, point.y() - 11, 22, 22)) return candidate;
        }
        if (this.showProjects && this.zoom >= CLOSE_DETAIL_ZOOM) for (CityProjectSnapshot project : this.projects) {
            if (!this.dimensionId.equals(project.dimensionId())) continue;
            Selection candidate = projectSelection(project);
            ScreenPoint point = markerPoint(candidate.kind(), candidate.id(), candidate.pos());
            if (inside(mouseX, mouseY, point.x() - 11, point.y() - 11, 22, 22)) return candidate;
        }
        if (this.showCities) for (CityMapPeersPayload.Peer peer : this.peers) {
            if (peer.own()) continue;
            Selection candidate = peerSelection(peer);
            ScreenPoint point = markerPoint(candidate.kind(), candidate.id(), candidate.pos());
            if (inside(mouseX, mouseY, point.x() - 11, point.y() - 11, 22, 22)) return candidate;
        }
        ScreenPoint ownPoint = markerPoint(SelectionKind.CITY, null, this.pylonPos);
        if (this.showCities && inside(mouseX, mouseY, ownPoint.x() - 11, ownPoint.y() - 11, 22, 22)) {
            return new Selection(SelectionKind.CITY, null,
                    Component.translatable("gui.createcityexpansioncore.map.city_pylon"), this.pylonPos, true,
                    List.of(Component.translatable("gui.createcityexpansioncore.world_map.context.territory",
                            this.territorySize)), this.cityIcon, false);
        }
        return null;
    }

    /** Repositions only screen markers. Their world positions and all map data remain untouched. */
    private void layoutMarkers() {
        List<MarkerEntry> entries = new ArrayList<>();
        if (this.showCities) {
            addMarker(entries, SelectionKind.CITY, null, this.pylonPos,
                    this.pylonPos.getX() + 0.5, this.pylonPos.getZ() + 0.5, 80);
            for (CityMapPeersPayload.Peer peer : this.peers) {
                if (!peer.own()) addMarker(entries, SelectionKind.CITY, peer.cityId(),
                        new BlockPos(peer.pylonX(), this.pylonPos.getY(), peer.pylonZ()),
                        peer.pylonX() + 0.5, peer.pylonZ() + 0.5, 80);
            }
        }
        Selection player = playerSelection();
        if (player != null) addMarker(entries, player.kind(), player.id(), player.pos(),
                player.pos().getX() + 0.5, player.pos().getZ() + 0.5, 75);
        if (this.showStations && this.zoom >= MEDIUM_DETAIL_ZOOM) for (RailwayStationSnapshot station : this.railwayStations) {
            if (station.dimension().equals(this.dimensionId)) addMarker(entries, SelectionKind.STATION,
                    station.registrationId(), station.pos(), station.pos().getX() + 0.5,
                    station.pos().getZ() + 0.5, 70);
        }
        if (this.showTrains && this.zoom >= CLOSE_DETAIL_ZOOM) for (RailwayTrainSnapshot train : this.railwayTrains) {
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
        if (this.showTrains && this.zoom >= CLOSE_DETAIL_ZOOM) for (RailwayTrainCandidate train : this.railwayTrainCandidates) {
            if (train.dimension() == null || !train.dimension().equals(this.dimensionId)) continue;
            addMarker(entries, SelectionKind.TRAIN_UNREGISTERED, train.createTrainId(),
                    BlockPos.containing(train.x(), 0, train.z()), train.x(), train.z(), 41);
        }
        if (this.showProjects && this.zoom >= CLOSE_DETAIL_ZOOM) for (CityProjectSnapshot project : this.projects) {
            if (this.dimensionId.equals(project.dimensionId())) addMarker(entries, SelectionKind.INDUSTRY,
                    project.projectId(), project.controllerPos(), project.controllerPos().getX() + 0.5,
                    project.controllerPos().getZ() + 0.5, 50);
        }
        if (this.showTradeInfrastructure && this.zoom >= CLOSE_DETAIL_ZOOM) {
            for (EconomyStatePayload.InfrastructureView value : this.economyInfrastructure) {
                if (value.cityOwned() && this.dimensionId.equals(value.dimension())) addMarker(entries,
                        SelectionKind.TRADE_INFRASTRUCTURE, value.id(), value.pos(),
                        value.pos().getX() + 0.5, value.pos().getZ() + 0.5, 60);
            }
        }
        if(this.showTradeInfrastructure&&this.zoom>=MEDIUM_DETAIL_ZOOM)for(FreightPoint point:this.freightPoints)if(point.dimension.equals(this.dimensionId))addMarker(entries,SelectionKind.FREIGHT_POINT,point.id,point.postbox,point.postbox.getX()+.5,point.postbox.getZ()+.5,65);

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
        this.centerX -= dragX / this.zoom;
        this.centerZ -= dragY / this.zoom;
        clampCenter();
        return true;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.dragging) {
            this.dragging = false;
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
        double newZoom = clamp(this.zoom * factor, MIN_ZOOM, MAX_ZOOM);
        if (newZoom == this.zoom) {
            return true;
        }

        this.zoom = newZoom;
        this.centerX += worldXBefore - screenToWorldX(mouseX);
        this.centerZ += worldZBefore - screenToWorldZ(mouseY);
        clampCenter();
        return true;
    }

    private void renderTerrain(GuiGraphics guiGraphics) {
        int minTileX = worldToTile(screenToWorldX(viewportX)) - 1;
        int maxTileX = worldToTile(screenToWorldX(viewportX + viewportWidth)) + 1;
        int minTileZ = worldToTile(screenToWorldZ(viewportY)) - 1;
        int maxTileZ = worldToTile(screenToWorldZ(viewportY + viewportHeight)) + 1;
        int stride = terrainStride();
        int budget = MAX_TILE_REBUILDS_PER_FRAME;

        for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                TileKey key = new TileKey(this.dimensionId, tileX, tileZ);
                CachedTile tile = this.tileCache.get(key);
                if (tile == null) {
                    renderMissingTile(guiGraphics, key, this.requestedTiles.containsKey(key));
                    continue;
                }
                // A tile is one blit. Rebuilding its texture is the only per-tile cost, and it is
                // budgeted per frame so a zoom that changes the level of detail never stalls.
                TerrainTileTexture texture = this.terrainTextures.computeIfAbsent(key,
                        ignored -> new TerrainTileTexture("ccec_city_map/" + this.dimensionId.getNamespace()
                                + "_" + this.dimensionId.getPath() + "_" + ignored.tileX() + "_" + ignored.tileZ()));
                if (!texture.matches(stride)) {
                    if (budget > 0) {
                        budget--;
                        texture.build(tile.colors(), stride);
                    } else if (texture.id() == null) {
                        // Nothing to draw yet and no budget left this frame: skip rather than blit an
                        // unbuilt texture. A tile whose texture exists is still drawn at its previous
                        // level of detail, so the budget never punches a hole in the terrain.
                        continue;
                    }
                }
                ResourceLocation id = texture.id();
                if (id == null) {
                    continue;
                }
                int left = worldToScreenX((double) tileX * CityMapTilePayload.WORLD_SIZE);
                int top = worldToScreenY((double) tileZ * CityMapTilePayload.WORLD_SIZE);
                int right = worldToScreenX((double) (tileX + 1) * CityMapTilePayload.WORLD_SIZE);
                int bottom = worldToScreenY((double) (tileZ + 1) * CityMapTilePayload.WORLD_SIZE);
                if (right <= viewportX || left >= viewportX + viewportWidth
                        || bottom <= viewportY || top >= viewportY + viewportHeight) {
                    continue;
                }
                guiGraphics.blit(id, left, top, Math.max(1, right - left), Math.max(1, bottom - top),
                        0.0F, 0.0F, TerrainTileTexture.SIZE, TerrainTileTexture.SIZE,
                        TerrainTileTexture.SIZE, TerrainTileTexture.SIZE);
            }
        }
    }

    /**
     * Releases every terrain texture. The textures are derived from the cached samples, so dropping
     * them costs nothing but a budgeted rebuild — and it means a closed map leaves no GPU texture
     * behind.
     */
    public void releaseTerrainTextures() {
        this.terrainTextures.values().forEach(TerrainTileTexture::close);
        this.terrainTextures.clear();
    }

    /**
     * Caps how many tiles this client holds. Anything beyond the cap is dropped oldest-first, and
     * the texture is closed with it, so panning across a large explored world cannot grow without
     * bound. Dropped tiles are simply re-requested when they come back into view.
     */
    private void evictTerrainTiles() {
        while (this.tileCache.size() > MAX_CACHED_TILES) {
            TileKey oldest = null;
            long oldestAt = Long.MAX_VALUE;
            for (TileKey key : this.tileCache.keySet()) {
                long at = this.tileCache.get(key).receivedAtMillis();
                if (at < oldestAt) {
                    oldestAt = at;
                    oldest = key;
                }
            }
            if (oldest == null) {
                return;
            }
            this.tileCache.remove(oldest);
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
        int color = requested
                ? (parity == 0 ? 0xFF182529 : 0xFF152125)
                : (parity == 0 ? 0xFF131A1C : 0xFF101719);
        guiGraphics.fill(left, top, right, bottom, color);
    }

    private void renderGrid(GuiGraphics guiGraphics) {
        double minWorldX = screenToWorldX(viewportX);
        double maxWorldX = screenToWorldX(viewportX + viewportWidth);
        double minWorldZ = screenToWorldZ(viewportY);
        double maxWorldZ = screenToWorldZ(viewportY + viewportHeight);
        int firstX = (int) Math.floor(minWorldX / 32.0) * 32;
        int firstZ = (int) Math.floor(minWorldZ / 32.0) * 32;

        for (int worldX = firstX; worldX <= maxWorldX; worldX += 32) {
            int screenX = worldToScreenX(worldX);
            guiGraphics.vLine(screenX, viewportY, viewportY + viewportHeight, 0x243F565D);
        }
        for (int worldZ = firstZ; worldZ <= maxWorldZ; worldZ += 32) {
            int screenY = worldToScreenY(worldZ);
            guiGraphics.hLine(viewportX, viewportX + viewportWidth, screenY, 0x243F565D);
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
    private void renderPeers(GuiGraphics guiGraphics) {
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
            if (peer.own()) {
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

            double half = peer.territorySize() / 2.0;
            int left = worldToScreenX(peer.pylonX() - half);
            int top = worldToScreenY(peer.pylonZ() - half);
            int right = worldToScreenX(peer.pylonX() + half);
            int bottom = worldToScreenY(peer.pylonZ() + half);

            // Территория рисуется, только если хоть часть её в кадре.
            if (this.showTerritory && right >= viewportX
                    && left <= viewportX + viewportW
                    && bottom >= viewportY
                    && top <= viewportY + viewportH) {
                guiGraphics.fill(left, top, right, bottom, PEER_TERRITORY_FILL);
                guiGraphics.hLine(left, right, top, PEER_TERRITORY_BORDER);
                guiGraphics.hLine(left, right, bottom, PEER_TERRITORY_BORDER);
                guiGraphics.vLine(left, top, bottom, PEER_TERRITORY_BORDER);
                guiGraphics.vLine(right, top, bottom, PEER_TERRITORY_BORDER);
            }

            if (!this.showCities) continue;
            drawMarkerLeader(guiGraphics, markerX, markerY,
                    worldToScreenX(peer.pylonX() + 0.5), worldToScreenY(peer.pylonZ() + 0.5));
            MapMarkerRenderer.render(guiGraphics, markerX, markerY, this.cityIcon, peer.own(), peer.master(),
                    matchesTargetAt(this.hoveredSelection, SelectionKind.CITY, peer.pylonX(), peer.pylonZ()),
                    matchesTargetAt(this.selection, SelectionKind.CITY, peer.pylonX(), peer.pylonZ()));
            CityRelationSnapshot.Status relation = this.relations.getOrDefault(peer.cityId(), CityRelationSnapshot.Status.NONE);
            if (relation != CityRelationSnapshot.Status.NONE) guiGraphics.renderOutline(markerX - 7, markerY - 7, 14, 14,
                    relation == CityRelationSnapshot.Status.PARTNER ? 0xFF65D8AF : relation == CityRelationSnapshot.Status.TRUSTED ? 0xFF79BBD8 : 0xFF9D8BC2);

            // Название — только если рядом, чтобы не превращать карту
            // в россыпь текста при множестве городов.
            String name = peer.name();
            boolean emphasized = matchesTargetAt(this.hoveredSelection, SelectionKind.CITY,
                    peer.pylonX(), peer.pylonZ())
                    || matchesTargetAt(this.selection, SelectionKind.CITY, peer.pylonX(), peer.pylonZ());
            if (emphasized && name != null && !name.isEmpty()) {
                int textX = markerX + 7;
                int textY = markerY - 4;
                var font = Minecraft.getInstance().font;
                if (textX + font.width(name) < viewportX + viewportW
                        && textX > viewportX
                        && textY > viewportY
                        && textY < viewportY + viewportH) {
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

    private void renderTerritory(GuiGraphics guiGraphics) {
        double half = this.territorySize / 2.0;
        int left = worldToScreenX(this.pylonPos.getX() - half);
        int top = worldToScreenY(this.pylonPos.getZ() - half);
        int right = worldToScreenX(this.pylonPos.getX() + half);
        int bottom = worldToScreenY(this.pylonPos.getZ() + half);
        guiGraphics.fill(left, top, right, bottom, TERRITORY_FILL);
        guiGraphics.hLine(left, right, top, TERRITORY_BORDER);
        guiGraphics.hLine(left, right, bottom, TERRITORY_BORDER);
        guiGraphics.vLine(left, top, bottom, TERRITORY_BORDER);
        guiGraphics.vLine(right, top, bottom, TERRITORY_BORDER);
    }

    private void renderMarkers(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        ScreenPoint ownMarker = markerPoint(SelectionKind.CITY, null, this.pylonPos);
        int markerX = ownMarker.x();
        int markerY = ownMarker.y();
        drawMarkerLeader(guiGraphics, markerX, markerY,
                worldToScreenX(this.pylonPos.getX() + 0.5), worldToScreenY(this.pylonPos.getZ() + 0.5));
        if (this.showCities) MapMarkerRenderer.render(guiGraphics, markerX, markerY, this.cityIcon, true, false,
                matchesTarget(this.hoveredSelection, SelectionKind.CITY, null, this.pylonPos),
                matchesTarget(this.selection, SelectionKind.CITY, null, this.pylonPos));
        if (this.showCities && (matchesTarget(this.hoveredSelection, SelectionKind.CITY, null, this.pylonPos)
                || matchesTarget(this.selection, SelectionKind.CITY, null, this.pylonPos))) guiGraphics.drawString(
                Minecraft.getInstance().font,
                Component.translatable("gui.createcityexpansioncore.map.city_pylon"),
                markerX + 7,
                markerY - 4,
                0xFFF4D49A,
                true
        );

        BlockPos playerPos = playerPos();
        if (playerPos != null) {
            UUID playerId = Minecraft.getInstance().player.getUUID();
            ScreenPoint player = markerPoint(SelectionKind.PLAYER, playerId, playerPos);
            int playerX = player.x();
            int playerY = player.y();
            drawMarkerLeader(guiGraphics, playerX, playerY,
                    worldToScreenX(playerPos.getX() + 0.5), worldToScreenY(playerPos.getZ() + 0.5));
            MapMarkerRenderer.renderPlayer(guiGraphics, playerX, playerY, this.playerIcon,
                    matchesTarget(this.hoveredSelection, SelectionKind.PLAYER, playerId),
                    matchesTarget(this.selection, SelectionKind.PLAYER, playerId));
        }

        if (this.showProjects && this.zoom >= CLOSE_DETAIL_ZOOM) for (CityProjectSnapshot project : projects) {
            if (!dimensionId.equals(project.dimensionId())) {
                continue;
            }
            ScreenPoint projectPoint = markerPoint(SelectionKind.INDUSTRY, project.projectId(), project.controllerPos());
            int projectX = projectPoint.x();
            int projectY = projectPoint.y();
            drawMarkerLeader(guiGraphics, projectX, projectY,
                    worldToScreenX(project.controllerPos().getX() + 0.5),
                    worldToScreenY(project.controllerPos().getZ() + 0.5));
            MapMarkerRenderer.render(guiGraphics, projectX, projectY, this.mineIcon, true,
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
        if (this.showTradeInfrastructure && this.zoom >= CLOSE_DETAIL_ZOOM) for (EconomyStatePayload.InfrastructureView value : economyInfrastructure) {
            if (!value.cityOwned() || !dimensionId.equals(value.dimension())) continue;
            ScreenPoint point = markerPoint(SelectionKind.TRADE_INFRASTRUCTURE, value.id(), value.pos());
            int x = point.x(), y = point.y();
            drawMarkerLeader(guiGraphics, x, y,
                    worldToScreenX(value.pos().getX() + .5), worldToScreenY(value.pos().getZ() + .5));
            ItemStack icon = value.type() == com.example.examplemod.economy.EconomyTypes.InfrastructureType.CONTRACT_INTAKE
                    ? this.contractIcon : this.warehouseIcon;
            MapMarkerRenderer.render(guiGraphics, x, y, icon, value.cityOwned(), !value.available(),
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
        if(this.showTradeInfrastructure&&this.zoom>=MEDIUM_DETAIL_ZOOM)for(FreightPoint freight:this.freightPoints){if(!freight.dimension.equals(this.dimensionId))continue;ScreenPoint point=markerPoint(SelectionKind.FREIGHT_POINT,freight.id,freight.postbox);drawMarkerLeader(guiGraphics,point.x(),point.y(),worldToScreenX(freight.postbox.getX()+.5),worldToScreenY(freight.postbox.getZ()+.5));MapMarkerRenderer.render(guiGraphics,point.x(),point.y(),this.freightIcon,freight.cityId.equals(ownCityId()),!freight.online(),matchesTarget(this.hoveredSelection,SelectionKind.FREIGHT_POINT,freight.id),matchesTarget(this.selection,SelectionKind.FREIGHT_POINT,freight.id));if(inside(mouseX,mouseY,point.x()-11,point.y()-11,22,22))deferTooltip(mouseX,mouseY,Component.literal(freight.name),Component.literal(freight.address),Component.literal(freight.stationName),Component.translatable("gui.createcityexpansioncore.world_map.freight."+(freight.online()?"online":"offline")));}
        if(this.showTradeInfrastructure&&this.zoom>=MEDIUM_DETAIL_ZOOM)for(FreightCandidate candidate:this.freightCandidates){int x=worldToScreenX(candidate.postbox.getX()+.5),y=worldToScreenY(candidate.postbox.getZ()+.5);boolean hovered=inside(mouseX,mouseY,x-8,y-9,16,18),selected=matchesTarget(this.selection,SelectionKind.DISCOVERY,discoveryId(candidate));int pulse=(int)(24+18*Math.sin(System.currentTimeMillis()/180.0));guiGraphics.fill(x-5,y-7,x+6,y+7,hovered||selected?0xF06B4A20:0xE54A3518);guiGraphics.renderOutline(x-5,y-7,11,14,0xFFFFC45B);if(hovered||selected)guiGraphics.renderOutline(x-7,y-9,15,18,(pulse<<24)|0x00FFC45B);guiGraphics.drawCenteredString(Minecraft.getInstance().font,"!",x,y-4,0xFFFFFFFF);if(hovered)deferTooltip(mouseX,mouseY,Component.translatable("gui.createcityexpansioncore.world_map.discovery.title"),Component.translatable("gui.createcityexpansioncore.world_map.discovery.freight"),Component.literal(candidate.station.isBlank()?candidate.postbox.toShortString():candidate.station));}
    }

    private void deferTooltip(int mouseX, int mouseY, Component... lines) {
        this.pendingTooltip = lines;
        this.pendingTooltipX = mouseX;
        this.pendingTooltipY = mouseY;
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
            if (focused != null) graphics.fill(this.viewportX, this.viewportY, this.viewportX + this.viewportWidth, this.viewportY + this.viewportHeight, 0x54101719);
            for(var route:this.railwayRoutes) {
                if (focused != null && !focused.id().equals(route.id())) continue;
                int color=route.selected()?0xFFFFFF76:route.status().equals("active")?0xFF61E6B0:0xFFE48C54;
                if (focused != null) drawAnimatedRoute(graphics, route, color); else drawSegments(graphics,route.segments(),color,3);
            }
            if(this.showTradeInfrastructure)for(FreightLink link:this.freightLinks){FreightPoint source=freight(link.source),destination=freight(link.destination);if(source!=null&&destination!=null&&source.dimension.equals(this.dimensionId)&&destination.dimension.equals(this.dimensionId))drawLine(graphics,worldToScreenX(source.postbox.getX()+.5),worldToScreenY(source.postbox.getZ()+.5),worldToScreenX(destination.postbox.getX()+.5),worldToScreenY(destination.postbox.getZ()+.5),0xB36ACFCE);}
        }
        if (this.showStations && this.zoom >= MEDIUM_DETAIL_ZOOM) {
            for (RailwayStationSnapshot station : this.railwayStations) {
                if (!station.dimension().equals(this.dimensionId)) continue;
                ScreenPoint point = markerPoint(SelectionKind.STATION, station.registrationId(), station.pos());
                int x = point.x();
                int y = point.y();
                drawMarkerLeader(graphics, x, y,
                        worldToScreenX(station.pos().getX() + 0.5), worldToScreenY(station.pos().getZ() + 0.5));
                MapMarkerRenderer.render(graphics, x, y, this.stationIcon, station.cityOwned(),
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
        if (this.showTrains && this.zoom >= CLOSE_DETAIL_ZOOM) {
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
                MapMarkerRenderer.render(graphics, x, y, this.trainIcon, train.cityOwned(),
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
                MapMarkerRenderer.render(graphics, x, y, this.trainIcon, false, true,
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
     */
    private void renderClampedTooltip(GuiGraphics graphics, Component[] lines) {
        Font font = Minecraft.getInstance().font;
        int width = 0;
        int visible = 0;
        for (Component line : lines) {
            if (line.getString().isEmpty()) continue;
            width = Math.max(width, font.width(line));
            visible++;
        }
        if (visible == 0) return;

        int boxWidth = width + 8;
        int boxHeight = visible * 11 + 6;
        int left = this.pendingTooltipX + 8;
        int top = this.pendingTooltipY + 8;
        /*
         * Кламп по обеим осям: подсказка не вылезает за карту
         * ни вправо, ни вниз, ни вверх у края экрана.
         */
        left = clampTo(left, boxWidth, viewportX, viewportX + viewportWidth);
        top = clampTo(top, boxHeight, viewportY, viewportY + viewportHeight);

        graphics.fill(left, top, left + boxWidth, top + boxHeight, 0xE8101719);
        graphics.renderOutline(left, top, boxWidth, boxHeight, ACCENT_COLOR);
        int row = 0;
        for (Component line : lines) {
            if (line.getString().isEmpty()) continue;
            graphics.drawString(font, line, left + 4, top + 4 + row * 11,
                    row == 0 ? 0xFFFFD08A : 0xFFD1D5D6, false);
            row++;
        }
    }

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

    /** Counts for the World Map overview panel; filtered to this dimension so the numbers mean something. */
    public int freightPointCount() {
        int total = 0;
        for (FreightPoint point : this.freightPoints) if (point.dimension.equals(this.dimensionId)) total++;
        return total;
    }

    /** Unregistered Postbox/Packager pairs the player could still turn into a Freight Point. */
    public int freightCandidateCount() { return this.freightCandidates.size(); }

    public enum Overlay { CITIES, TERRITORIES, PROJECTS, TRACKS, STATIONS, TRAINS, TRADE, RELATIONS }

    public enum Focus { WORLD, RAILWAY, TRADE, ENERGY }

    public enum SelectionKind { PLAYER, CITY, STATION, TRAIN, TRAIN_UNREGISTERED, INDUSTRY, TRADE_INFRASTRUCTURE, FREIGHT_POINT, DISCOVERY, RAILWAY_SECTION, ROUTE }

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
        List<Component> details=new ArrayList<>();details.add(Component.translatable("gui.createcityexpansioncore.world_map.context.territory",peer.territorySize()));if(peer.master())details.add(Component.translatable("gui.createcityexpansioncore.world_map.master_city"));
        CityRelationSnapshot.Status relation = this.relations.getOrDefault(peer.cityId(), CityRelationSnapshot.Status.NONE);
        if (!peer.own()) details.add(Component.translatable("gui.createcityexpansioncore.world_map.relation",
                Component.translatable("gui.createcityexpansioncore.relations.status." + relation.name().toLowerCase(Locale.ROOT))));
        if (relation == CityRelationSnapshot.Status.TRUSTED || relation == CityRelationSnapshot.Status.PARTNER)
            details.add(Component.translatable("gui.createcityexpansioncore.world_map.relation.private_access"));
        else if (!peer.own()) details.add(Component.translatable("gui.createcityexpansioncore.world_map.relation.public_only"));
        return new Selection(SelectionKind.CITY, peer.cityId(), Component.literal(peer.name()),
                new BlockPos(peer.pylonX(), this.pylonPos.getY(), peer.pylonZ()), peer.own(),
                details, this.cityIcon, peer.master());
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
    private Selection findSectionSelection(double mouseX,double mouseY){for(var section:this.railwaySections)if(hitSegments(section.segments(),mouseX,mouseY,5)){var network=network(section.networkId());String owner=network==null?"?":network.ownerName();List<Component> details=List.of(Component.translatable("gui.createcityexpansioncore.world_map.rail.owner",owner),Component.translatable("gui.createcityexpansioncore.world_map.rail.owner_type",network==null?"?":Component.translatable("gui.createcityexpansioncore.world_map.rail.owner_type."+network.ownerType().name().toLowerCase(Locale.ROOT))),Component.translatable("gui.createcityexpansioncore.world_map.rail.length",String.format(Locale.ROOT,"%.1f",section.length())),Component.translatable("gui.createcityexpansioncore.world_map.rail.access",network==null?"?":Component.translatable("gui.createcityexpansioncore.world_map.rail.access."+network.access().name().toLowerCase(Locale.ROOT))),Component.translatable("gui.createcityexpansioncore.world_map.rail.tariff",network==null?0:network.tariff()),Component.translatable("gui.createcityexpansioncore.world_map.rail.status",Component.translatable("gui.createcityexpansioncore.world_map.rail.status."+section.status().name().toLowerCase(Locale.ROOT))));return new Selection(SelectionKind.RAILWAY_SECTION,section.id(),Component.literal(network==null?shortId(section.networkId()):network.name()),midpoint(section.segments()),ownsNetwork(network),details,this.networkIcon,section.status()!=com.example.examplemod.railway.RailwayOwnershipTypes.SectionStatus.ONLINE);}return null;}
    private Selection findRouteSelection(double mouseX,double mouseY){for(var route:this.railwayRoutes)if(hitSegments(route.segments(),mouseX,mouseY,6)){List<Component> details=new ArrayList<>();details.add(Component.translatable("gui.createcityexpansioncore.world_map.route.distance",String.format(Locale.ROOT,"%.1f",route.distance())));details.add(Component.translatable("gui.createcityexpansioncore.world_map.route.price",route.price()));if(route.etaTicks()>0)details.add(Component.translatable("gui.createcityexpansioncore.world_map.route.eta",route.etaTicks()/20));String strategy=this.routeStrategies.get(route.id());if(strategy!=null)details.add(Component.translatable("gui.createcityexpansioncore.world_map.route.strategy",Component.translatable("gui.createcityexpansioncore.railway.route.strategy."+strategy.toLowerCase(Locale.ROOT))));details.add(Component.translatable("gui.createcityexpansioncore.world_map.route.status",route.status()));return new Selection(SelectionKind.ROUTE,route.id(),Component.translatable(route.selected()?"gui.createcityexpansioncore.world_map.route.selected":"gui.createcityexpansioncore.world_map.route.active"),midpoint(route.segments()),true,details,this.networkIcon,!route.status().equals("active")&&!route.selected());}return null;}
    private boolean hitSegments(List<RailwayMapPayload.TrackSegment> segments,double mouseX,double mouseY,double radius){for(var s:segments)if(distanceToSegment(mouseX,mouseY,worldToScreenX(s.x1()),worldToScreenY(s.z1()),worldToScreenX(s.x2()),worldToScreenY(s.z2()))<=radius)return true;return false;}
    private static double distanceToSegment(double px,double py,double x1,double y1,double x2,double y2){double dx=x2-x1,dy=y2-y1;if(dx==0&&dy==0)return Math.hypot(px-x1,py-y1);double t=Math.max(0,Math.min(1,((px-x1)*dx+(py-y1)*dy)/(dx*dx+dy*dy)));return Math.hypot(px-(x1+t*dx),py-(y1+t*dy));}
    private static BlockPos midpoint(List<RailwayMapPayload.TrackSegment> values){if(values.isEmpty())return BlockPos.ZERO;var s=values.get(values.size()/2);return BlockPos.containing((s.x1()+s.x2())/2,0,(s.z1()+s.z2())/2);}
    private void drawSegments(GuiGraphics graphics,List<RailwayMapPayload.TrackSegment> values,int color,int width){drawSegments(graphics,values,color,0,width);}
    private void drawSegments(GuiGraphics graphics,List<RailwayMapPayload.TrackSegment> values,int color,int start,int count){
        for(var s:values){int x1=worldToScreenX(s.x1()),y1=worldToScreenY(s.z1()),x2=worldToScreenX(s.x2()),y2=worldToScreenY(s.z2());for(int offset=start;offset<start+count;offset++)drawLine(graphics,x1,y1+offset,x2,y2+offset,color);}
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
            case PUBLIC_PAID -> 0xFF3FBF6A;
            case PRIVATE -> 0xFF8A4A48;
            case OFFLINE -> 0xFF5A4748;
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
        if (kind == RailClass.PUBLIC_PAID && this.zoom >= CLOSE_DETAIL_ZOOM) {
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
    private void drawAnimatedRoute(GuiGraphics graphics,RailwayMapPayload.RouteView route,int color){
        if(route.segments().isEmpty())return;double progress=Math.min(1.0,(System.currentTimeMillis()-this.routeSelectionStartedAt)/700.0);int count=Math.max(1,(int)Math.ceil(route.segments().size()*progress));drawSegments(graphics,route.segments().subList(0,Math.min(count,route.segments().size())),color,3);var first=route.segments().getFirst();var last=route.segments().getLast();int pulse=3+(int)(Math.abs(Math.sin(System.currentTimeMillis()/260.0))*3);graphics.renderOutline(worldToScreenX(first.x1())-pulse,worldToScreenY(first.z1())-pulse,pulse*2,pulse*2,0xFFFFE59A);graphics.renderOutline(worldToScreenX(last.x2())-pulse,worldToScreenY(last.z2())-pulse,pulse*2,pulse*2,0xFFFFE59A);
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
        long now = System.currentTimeMillis();
        this.requestedTiles.entrySet().removeIf(
                entry -> now - entry.getValue() >= REQUEST_TIMEOUT_MILLIS
        );
        if (now < this.nextRequestAtMillis) {
            return;
        }

        int minTileX = worldToTile(screenToWorldX(viewportX)) - 1;
        int maxTileX = worldToTile(screenToWorldX(viewportX + viewportWidth)) + 1;
        int minTileZ = worldToTile(screenToWorldZ(viewportY)) - 1;
        int maxTileZ = worldToTile(screenToWorldZ(viewportY + viewportHeight)) + 1;
        List<TileKey> missing = new ArrayList<>();

        for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                TileKey key = new TileKey(this.dimensionId, tileX, tileZ);
                if (!this.tileCache.containsKey(key)
                        && !this.requestedTiles.containsKey(key)) {
                    missing.add(key);
                }
            }
        }

        /*
         * Ничего не пропущено — сортировку и аллокацию списка
         * пропускаем целиком, этот метод вызывается каждый кадр.
         */
        if (missing.isEmpty()) {
            return;
        }

        missing.sort(Comparator.comparingDouble(this::distanceFromCenterSquared));
        int requestCount = Math.min(MAX_REQUESTS_PER_RENDER, missing.size());
        for (int index = 0; index < requestCount; index++) {
            TileKey key = missing.get(index);
            this.requestedTiles.put(key, now);
            PacketDistributor.sendToServer(new RequestCityMapTilePayload(
                    this.pylonPos,
                    this.dimensionId,
                    key.tileX(),
                    key.tileZ()
            ));
        }
        if (requestCount > 0) {
            this.nextRequestAtMillis = now + REQUEST_INTERVAL_MILLIS;
        }
    }

    private double distanceFromCenterSquared(TileKey key) {
        double tileCenterX = (double) key.tileX() * CityMapTilePayload.WORLD_SIZE
                + CityMapTilePayload.WORLD_SIZE / 2.0;
        double tileCenterZ = (double) key.tileZ() * CityMapTilePayload.WORLD_SIZE
                + CityMapTilePayload.WORLD_SIZE / 2.0;
        double deltaX = tileCenterX - this.centerX;
        double deltaZ = tileCenterZ - this.centerZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    private int worldToScreenX(double worldX) {
        return (int) Math.floor(this.viewportX + this.viewportWidth / 2.0
                + (worldX - this.centerX) * this.zoom);
    }

    private int worldToScreenY(double worldZ) {
        return (int) Math.floor(this.viewportY + this.viewportHeight / 2.0
                + (worldZ - this.centerZ) * this.zoom);
    }

    private double screenToWorldX(double screenX) {
        return this.centerX + (screenX - this.viewportX - this.viewportWidth / 2.0) / this.zoom;
    }

    private double screenToWorldZ(double screenY) {
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

    private record TileKey(ResourceLocation dimensionId, int tileX, int tileZ) {
    }

    private record CachedTile(int[] colors, boolean complete, long receivedAtMillis) {
    }
}
