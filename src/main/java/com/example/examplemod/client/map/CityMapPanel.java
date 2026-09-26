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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CityMapPanel {

    private static final double MIN_ZOOM = 0.5;
    private static final double MAX_ZOOM = 4.0;
    private static final double ZOOM_STEP = 1.25;
    private static final int MAX_PAN_DISTANCE = 512;
    /**
     * Минимальная экранная плотность одной выборки рельефа.
     * Ниже этого рельеф рисуем укрупнёнными блоками — глаз этого
     * не замечает, а число заливок падает в разы при отдалении.
     */
    private static final double MIN_SAMPLE_SCREEN_PX = 2.0;
    private static final int MAX_REQUESTS_PER_RENDER = 4;
    private static final long REQUEST_INTERVAL_MILLIS = 100L;
    private static final long REQUEST_TIMEOUT_MILLIS = 5_000L;
    private static final long INCOMPLETE_REFRESH_MILLIS = 2_000L;

    private static final int FRAME_COLOR = 0xFF596064;
    private static final int ACCENT_COLOR = 0xFFD09A3A;
    private static final int TERRITORY_FILL = 0x2437A6D1;
    private static final int TERRITORY_BORDER = 0xFF6EC5E8;

    /*
     * Соседние города.
     *
     * Свой город — основной акцентный цвет, чужие — приглушённый
     * и заметно менее насыщенный, чтобы территории не сливались
     * в одну пеструю пятнистую массу. Это инфраструктурная карта,
     * а не политическая: чужие города показываются как факт
     * существования, без какой-либо детализации.
     */
    private static final int PEER_TERRITORY_FILL = 0x1A8E9BA6;
    private static final int PEER_TERRITORY_BORDER = 0xFF6E8A94;
    private static final int PEER_MARKER_COLOR = 0xFF8E9BA6;
    private static final int PEER_MARKER_OUTLINE = 0xFF10171A;

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
    private boolean showTerritory = true;
    private boolean showProjects = true;
    private final Map<TileKey, CachedTile> tileCache = new HashMap<>();
    private final Map<TileKey, Long> requestedTiles = new HashMap<>();

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
        this.viewportHeight = Math.max(1, height - 19);
        this.centerButtonX = x + width - 58;
        this.centerButtonY = y + height - 15;
        this.layerButtonsX = x + width - 166;
        this.layerButtonsY = y + 4;

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
                renderGrid(guiGraphics);
                // Соседи рисуются ДО своей территории: собственный город
                // остаётся главным акцентом, чужие — фоном.
                if (this.showTerritory) renderPeers(guiGraphics);
                if (this.showTerritory) renderTerritory(guiGraphics);
                renderRailway(guiGraphics, mouseX, mouseY);
                renderMarkers(guiGraphics, mouseX, mouseY);
            });
        } finally {
            guiGraphics.disableScissor();
        }

        renderFooter(guiGraphics, x, y + viewportHeight + 4, width, mouseX, mouseY);

        /*
         * Подсказки рисуются после снятия scissor:
         * их не режет область карты и они всегда поверх карты.
         */
        if (this.pendingTooltip != null) {
            Component[] lines = this.pendingTooltip;
            this.pendingTooltip = null;
            renderClampedTooltip(guiGraphics, lines);
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
        if (inside(mouseX, mouseY, centerButtonX, centerButtonY, 54, 12)) {
            centerOnPylon();
            return true;
        }
        if (inside(mouseX, mouseY, layerButtonsX, layerButtonsY, 30, 12)) {
            this.showTerritory = !this.showTerritory;
            return true;
        }
        if (inside(mouseX, mouseY, layerButtonsX + 34, layerButtonsY, 30, 12)) {
            this.showProjects = !this.showProjects;
            return true;
        }
        if (inside(mouseX, mouseY, layerButtonsX + 68, layerButtonsY, 30, 12)) {
            this.showTracks = !this.showTracks;
            return true;
        }
        if (inside(mouseX, mouseY, layerButtonsX + 102, layerButtonsY, 30, 12)) {
            this.showStations = !this.showStations;
            return true;
        }
        if (inside(mouseX, mouseY, layerButtonsX + 136, layerButtonsY, 30, 12)) {
            this.showTrains = !this.showTrains;
            return true;
        }
        if (insideViewport(mouseX, mouseY)) {
            this.dragging = true;
            return true;
        }
        return false;
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
        int minTileX = worldToTile(screenToWorldX(viewportX));
        int maxTileX = worldToTile(screenToWorldX(viewportX + viewportWidth));
        int minTileZ = worldToTile(screenToWorldZ(viewportY));
        int maxTileZ = worldToTile(screenToWorldZ(viewportY + viewportHeight));
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

            int markerX = worldToScreenX(peer.pylonX() + 0.5);
            int markerY = worldToScreenY(peer.pylonZ() + 0.5);

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
            if (right >= viewportX
                    && left <= viewportX + viewportW
                    && bottom >= viewportY
                    && top <= viewportY + viewportH) {
                guiGraphics.fill(left, top, right, bottom, PEER_TERRITORY_FILL);
                guiGraphics.hLine(left, right, top, PEER_TERRITORY_BORDER);
                guiGraphics.hLine(left, right, bottom, PEER_TERRITORY_BORDER);
                guiGraphics.vLine(left, top, bottom, PEER_TERRITORY_BORDER);
                guiGraphics.vLine(right, top, bottom, PEER_TERRITORY_BORDER);
            }

            // Компактная метка пилона: контур + заливка, как у своего,
            // но приглушённая.
            guiGraphics.fill(markerX - 3, markerY - 3, markerX + 4, markerY + 4, PEER_MARKER_OUTLINE);
            guiGraphics.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, PEER_MARKER_COLOR);

            // Название — только если рядом, чтобы не превращать карту
            // в россыпь текста при множестве городов.
            String name = peer.name();
            if (name != null && !name.isEmpty()) {
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
        int markerX = worldToScreenX(this.pylonPos.getX() + 0.5);
        int markerY = worldToScreenY(this.pylonPos.getZ() + 0.5);
        guiGraphics.fill(markerX - 1, markerY - 5, markerX + 2, markerY + 6, 0xFF20170A);
        guiGraphics.fill(markerX - 5, markerY - 1, markerX + 6, markerY + 2, 0xFF20170A);
        guiGraphics.fill(markerX - 3, markerY - 3, markerX + 4, markerY + 4, ACCENT_COLOR);
        guiGraphics.drawString(
                Minecraft.getInstance().font,
                Component.translatable("gui.createcityexpansioncore.map.city_pylon"),
                markerX + 7,
                markerY - 4,
                0xFFF4D49A,
                true
        );

        if (this.showProjects) for (CityProjectSnapshot project : projects) {
            if (!dimensionId.equals(project.dimensionId())) {
                continue;
            }
            int projectX = worldToScreenX(project.controllerPos().getX() + 0.5);
            int projectY = worldToScreenY(project.controllerPos().getZ() + 0.5);
            guiGraphics.fill(projectX - 4, projectY - 4, projectX + 5, projectY + 5, 0xFF20170A);
            guiGraphics.fill(projectX - 2, projectY - 2, projectX + 3, projectY + 3, 0xFFFF9F43);
            if (inside(mouseX, mouseY, projectX - 6, projectY - 6, 12, 12)) {
                deferTooltip(mouseX, mouseY,
                        Component.translatable(
                                "gui.createcityexpansioncore.project.industrial_mine"),
                        Component.translatable(
                                "gui.createcityexpansioncore.map.resource_richness",
                                Component.translatable("resource.createcityexpansioncore."
                                        + project.resourceId().getPath()),
                                Math.round(project.richness() * 100.0)),
                        projectStatus(project.status()),
                        Component.literal("X " + project.controllerPos().getX()
                                + "  Z " + project.controllerPos().getZ()));
            }
        }
        for (EconomyStatePayload.InfrastructureView value : economyInfrastructure) {
            if (!value.cityOwned() || !dimensionId.equals(value.dimension())) continue;
            int x=worldToScreenX(value.pos().getX()+.5),y=worldToScreenY(value.pos().getZ()+.5);
            int color=value.type()==com.example.examplemod.economy.EconomyTypes.InfrastructureType.CONTRACT_INTAKE?0xFF77B992:0xFFD09A3A;
            guiGraphics.fill(x-4,y-4,x+5,y+5,0xFF141A1D);guiGraphics.fill(x-2,y-2,x+3,y+3,value.available()?color:0xFF6B7275);
            if(inside(mouseX,mouseY,x-6,y-6,12,12))deferTooltip(mouseX,mouseY,Component.translatable(value.type()==com.example.examplemod.economy.EconomyTypes.InfrastructureType.CONTRACT_INTAKE?"block.createcityexpansioncore.contract_intake":"block.createcityexpansioncore.warehouse_gateway"),Component.translatable(value.available()?"status.createcityexpansioncore.railway.available":"status.createcityexpansioncore.railway.missing"),Component.literal("X "+value.pos().getX()+"  Z "+value.pos().getZ()));
        }
    }

    private void deferTooltip(int mouseX, int mouseY, Component... lines) {
        this.pendingTooltip = lines;
        this.pendingTooltipX = mouseX;
        this.pendingTooltipY = mouseY;
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
                drawLine(graphics,
                        worldToScreenX(segment.x1()), worldToScreenY(segment.z1()),
                        worldToScreenX(segment.x2()), worldToScreenY(segment.z2()),
                        0xFF9A7040);
            }
        }
        if (this.showStations) {
            for (RailwayStationSnapshot station : this.railwayStations) {
                if (!station.dimension().equals(this.dimensionId)) continue;
                int x = worldToScreenX(station.pos().getX() + 0.5);
                int y = worldToScreenY(station.pos().getZ() + 0.5);
                graphics.fill(x - 4, y - 4, x + 5, y + 5, 0xFF101719);
                graphics.fill(x - 2, y - 2, x + 3, y + 3,
                        station.available() ? 0xFF70B7C8 : 0xFF84575B);
                if (this.zoom >= 2.0) {
                    graphics.drawString(Minecraft.getInstance().font, station.name(), x + 6, y - 4,
                            station.available() ? 0xFFBEE5ED : 0xFFD6A4A7, true);
                }
                if (inside(mouseX, mouseY, x - 5, y - 5, 10, 10)) {
                    deferTooltip(mouseX, mouseY,
                            Component.literal(station.name()),
                            Component.translatable("gui.createcityexpansioncore.railway.station_type."
                                    + station.type().name().toLowerCase(Locale.ROOT)),
                            Component.translatable("status.createcityexpansioncore.railway."
                                    + (station.available() ? "available" : "missing")));
                }
            }
        }
        if (this.showTrains) {
            double progress = Math.min(1.0,
                    Math.max(0.0, (System.currentTimeMillis() - this.railwaySnapshotAtMillis) / 1000.0));
            for (RailwayTrainSnapshot train : this.railwayTrains) {
                if (train.dimension() == null || !train.dimension().equals(this.dimensionId)) continue;
                TrainPosition previous = this.previousTrainPositions.get(train.fleetTrainId());
                double xPos = previous != null && previous.dimension().equals(train.dimension())
                        ? previous.x() + (train.x() - previous.x()) * progress : train.x();
                double zPos = previous != null && previous.dimension().equals(train.dimension())
                        ? previous.z() + (train.z() - previous.z()) * progress : train.z();
                int x = worldToScreenX(xPos);
                int y = worldToScreenY(zPos);
                graphics.fill(x - 4, y - 3, x + 5, y + 4, 0xFF12191B);
                graphics.fill(x - 2, y - 1, x + 3, y + 2,
                        train.automationSuspended() ? 0xFFD07855 : 0xFFE2BD65);
                if (inside(mouseX, mouseY, x - 5, y - 5, 10, 10)) {
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

    private void requestVisibleTiles() {
        long now = System.currentTimeMillis();
        this.requestedTiles.entrySet().removeIf(
                entry -> now - entry.getValue() >= REQUEST_TIMEOUT_MILLIS
        );
        if (now < this.nextRequestAtMillis) {
            return;
        }

        int minTileX = worldToTile(screenToWorldX(viewportX));
        int maxTileX = worldToTile(screenToWorldX(viewportX + viewportWidth));
        int minTileZ = worldToTile(screenToWorldZ(viewportY));
        int maxTileZ = worldToTile(screenToWorldZ(viewportY + viewportHeight));
        List<TileKey> missing = new ArrayList<>();

        for (int tileZ = minTileZ; tileZ <= maxTileZ; tileZ++) {
            for (int tileX = minTileX; tileX <= maxTileX; tileX++) {
                TileKey key = new TileKey(this.dimensionId, tileX, tileZ);
                CachedTile cached = this.tileCache.get(key);
                boolean needsRefresh = cached == null
                        || (!cached.complete()
                        && now - cached.receivedAtMillis() >= INCOMPLETE_REFRESH_MILLIS);
                if (needsRefresh
                        && !this.requestedTiles.containsKey(key)
                        && withinServerRange(tileX, tileZ)) {
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

    private boolean withinServerRange(int tileX, int tileZ) {
        long tileCenterX = (long) tileX * CityMapTilePayload.WORLD_SIZE
                + CityMapTilePayload.WORLD_SIZE / 2L;
        long tileCenterZ = (long) tileZ * CityMapTilePayload.WORLD_SIZE
                + CityMapTilePayload.WORLD_SIZE / 2L;
        if (Math.abs(tileCenterX - this.pylonPos.getX()) <= MAX_PAN_DISTANCE
                && Math.abs(tileCenterZ - this.pylonPos.getZ()) <= MAX_PAN_DISTANCE) {
            return true;
        }
        return projects.stream().anyMatch(project ->
                dimensionId.equals(project.dimensionId())
                        && Math.abs(tileCenterX - project.controllerPos().getX()) <= MAX_PAN_DISTANCE
                        && Math.abs(tileCenterZ - project.controllerPos().getZ()) <= MAX_PAN_DISTANCE
        );
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
        double minimumX = this.pylonPos.getX() + 0.5 - MAX_PAN_DISTANCE;
        double maximumX = this.pylonPos.getX() + 0.5 + MAX_PAN_DISTANCE;
        double minimumZ = this.pylonPos.getZ() + 0.5 - MAX_PAN_DISTANCE;
        double maximumZ = this.pylonPos.getZ() + 0.5 + MAX_PAN_DISTANCE;
        for (CityProjectSnapshot project : projects) {
            if (!dimensionId.equals(project.dimensionId())) {
                continue;
            }
            minimumX = Math.min(minimumX, project.controllerPos().getX() + 0.5 - MAX_PAN_DISTANCE);
            maximumX = Math.max(maximumX, project.controllerPos().getX() + 0.5 + MAX_PAN_DISTANCE);
            minimumZ = Math.min(minimumZ, project.controllerPos().getZ() + 0.5 - MAX_PAN_DISTANCE);
            maximumZ = Math.max(maximumZ, project.controllerPos().getZ() + 0.5 + MAX_PAN_DISTANCE);
        }
        this.centerX = clamp(
                this.centerX,
                minimumX,
                maximumX
        );
        this.centerZ = clamp(
                this.centerZ,
                minimumZ,
                maximumZ
        );
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record TileKey(ResourceLocation dimensionId, int tileX, int tileZ) {
    }

    private record CachedTile(int[] colors, boolean complete, long receivedAtMillis) {
    }
}
