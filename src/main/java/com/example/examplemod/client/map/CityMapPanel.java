package com.example.examplemod.client.map;

import com.example.examplemod.network.CityMapPeersPayload;

import com.example.examplemod.network.CityMapTilePayload;
import com.example.examplemod.network.RequestCityMapTilePayload;
import com.example.examplemod.city.CityProjectSnapshot;
import com.example.examplemod.network.RailwayMapPayload;
import com.example.examplemod.network.EconomyStatePayload;
import com.example.examplemod.railway.RailwayStationSnapshot;
import com.example.examplemod.railway.RailwayTrainSnapshot;
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
    private List<RailwayStationSnapshot> railwayStations = List.of();
    private List<RailwayTrainSnapshot> railwayTrains = List.of();
    private List<EconomyStatePayload.InfrastructureView> economyInfrastructure = List.of();
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
    private boolean drawInternalControls = true;
    private Selection selection;
    private Selection hoveredSelection;
    private Selection hoverCandidate;
    private long hoverCandidateSince;
    private Focus focus = Focus.WORLD;
    private final Map<TileKey, CachedTile> tileCache = new HashMap<>();
    private final Map<TileKey, Long> requestedTiles = new HashMap<>();
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

    public void setFocus(Focus focus) {
        this.focus = focus;
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
        this.selection = value;
        centerOn(value.pos());
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

    public boolean receive(RailwayMapPayload payload) {
        if (!this.pylonPos.equals(payload.pylonPos())
                || !this.dimensionId.equals(payload.dimension())) {
            return false;
        }
        this.railwayTracks = List.copyOf(payload.segments());
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
             * GuiGraphics вызывает endBatch() после КАЖДОЙ fill(), пока блок
             * не помечен managed. Карта — это десятки тысяч заливок, поэтому
             * рисуем её одним батчем: один draw call вместо десятков тысяч.
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
        return true;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        Selection hit = findSelection(mouseX, mouseY);
        if (hit != null) {
            this.selection = hit;
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
        if (this.showTrains && this.zoom >= CLOSE_DETAIL_ZOOM) for (RailwayTrainSnapshot train : this.railwayTrains) {
            if (train.dimension() == null || !train.dimension().equals(this.dimensionId)) continue;
            Selection candidate = trainSelection(train);
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
                if (!peer.own()) addMarker(entries, SelectionKind.CITY, null,
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

        for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                TileKey key = new TileKey(this.dimensionId, tileX, tileZ);
                CachedTile tile = this.tileCache.get(key);
                if (tile == null) {
                    renderMissingTile(guiGraphics, key, this.requestedTiles.containsKey(key));
                    continue;
                }
                renderTile(guiGraphics, tileX, tileZ, tile.colors(), stride);
            }
        }
    }

    /**
     * Шаг отрисовки выборок в штуках.
     * <p>
     * При отдалении выборка и так меньше двух пикселей, а рисовать
     * миллионы микро-прямоугольников бессмысленно: объединяем соседние
     * выборки в блок и берём цвет верхней левой.
     *
     * @return 1 — обычная детализация, больше — укрупнённые блоки
     */
    private int terrainStride() {
        double screenStep = CityMapTilePayload.SAMPLE_STEP * this.zoom;
        if (screenStep >= MIN_SAMPLE_SCREEN_PX) {
            return 1;
        }
        int stride = 1;
        while (stride < CityMapTilePayload.SAMPLE_COUNT
                && stride * screenStep < MIN_SAMPLE_SCREEN_PX) {
            stride <<= 1;
        }
        return stride;
    }

    private void renderTile(
            GuiGraphics guiGraphics,
            int tileX,
            int tileZ,
            int[] colors,
            int stride
    ) {
        int originX = tileX * CityMapTilePayload.WORLD_SIZE;
        int originZ = tileZ * CityMapTilePayload.WORLD_SIZE;
        int worldStep = CityMapTilePayload.SAMPLE_STEP;
        int blockWorld = worldStep * stride;
        int screenRight = viewportX + viewportWidth;
        int screenBottom = viewportY + viewportHeight;

        for (int sampleZ = 0;
             sampleZ < CityMapTilePayload.SAMPLE_COUNT;
             sampleZ += stride) {
            double worldZ = originZ + (double) sampleZ * worldStep;
            int top = worldToScreenY(worldZ);
            int bottom = worldToScreenY(worldZ + blockWorld) + 1;
            if (bottom <= viewportY || top >= screenBottom) {
                continue;
            }
            int row = sampleZ * CityMapTilePayload.SAMPLE_COUNT;
            for (int sampleX = 0;
                 sampleX < CityMapTilePayload.SAMPLE_COUNT;
                 sampleX += stride) {
                double worldX = originX + (double) sampleX * worldStep;
                int left = worldToScreenX(worldX);
                if (left >= screenRight) {
                    continue;
                }
                int right = worldToScreenX(worldX + blockWorld) + 1;
                if (right <= viewportX) {
                    continue;
                }
                int color = colors[row + sampleX];
                guiGraphics.fill(
                        left,
                        top,
                        Math.max(left + 1, right),
                        Math.max(top + 1, bottom),
                        color
                );
            }
        }
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
            ScreenPoint marker = markerPoint(SelectionKind.CITY, null, peerPos);
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
            MapMarkerRenderer.render(guiGraphics, markerX, markerY, this.cityIcon, peer.own(), false,
                    matchesTargetAt(this.hoveredSelection, SelectionKind.CITY, peer.pylonX(), peer.pylonZ()),
                    matchesTargetAt(this.selection, SelectionKind.CITY, peer.pylonX(), peer.pylonZ()));

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
                            0xFFC4CDD2,
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

    public enum Overlay { CITIES, TERRITORIES, PROJECTS, TRACKS, STATIONS, TRAINS, TRADE }

    public enum Focus { WORLD, RAILWAY, TRADE, ENERGY }

    public enum SelectionKind { PLAYER, CITY, STATION, TRAIN, INDUSTRY, TRADE_INFRASTRUCTURE }

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

    private Selection peerSelection(CityMapPeersPayload.Peer peer) {
        return new Selection(SelectionKind.CITY, null, Component.literal(peer.name()),
                new BlockPos(peer.pylonX(), this.pylonPos.getY(), peer.pylonZ()), peer.own(),
                List.of(Component.translatable("gui.createcityexpansioncore.world_map.context.territory",
                        peer.territorySize())), this.cityIcon, false);
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
        return new Selection(SelectionKind.STATION, station.registrationId(), Component.literal(station.name()),
                station.pos(), station.cityOwned(), List.of(
                Component.translatable("gui.createcityexpansioncore.railway.station_type."
                        + station.type().name().toLowerCase(Locale.ROOT)),
                Component.translatable("status.createcityexpansioncore.railway."
                        + (station.available() ? "available" : "missing"))),
                this.stationIcon, !station.available());
    }

    private Selection trainSelection(RailwayTrainSnapshot train) {
        return new Selection(SelectionKind.TRAIN, train.fleetTrainId(), Component.literal(train.name()),
                BlockPos.containing(train.x(), 0, train.z()), train.cityOwned(),
                List.of(Component.translatable("status.createcityexpansioncore.railway." + train.statusKey()),
                        train.lineName().isEmpty()
                                ? Component.translatable("status.createcityexpansioncore.railway.no_schedule")
                                : Component.literal(train.lineName())), this.trainIcon, train.automationSuspended());
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
