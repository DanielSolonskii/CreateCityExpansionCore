package com.example.examplemod.client.screen;

import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.EconomyCommandPayload;
import com.example.examplemod.network.RailwayCommandPayload;
import com.example.examplemod.network.RailwayMapPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/** First map-first vertical slice of the CCEC server atlas. */
public final class WorldMapScreen extends Screen {
    private static final int HEADER_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 24;
    private static final int LEFT_WIDTH = 142;
    private static final int COLLAPSED_LEFT_WIDTH = 24;
    private static final int GAP = 6;
    private static final int BACKGROUND = 0xFF111719;
    private static final int PANEL = 0xF01C2427;
    private static final int PANEL_ALT = 0xF0242D30;
    private static final int FRAME = 0xFF596064;
    private static final int ACCENT = 0xFFD09A3A;

    private final CityPylonScreen parent;
    private final CityPylonMenu menu;
    private final CityMapPanel map;
    private Mode mode = Mode.WORLD;
    private int refreshTicks;
    private boolean sidebarCollapsed;
    private int cardX, cardY, cardWidth, cardHeight;

    public WorldMapScreen(CityPylonScreen parent) {
        super(Component.translatable("gui.createcityexpansioncore.world_map.title"));
        this.parent = parent;
        this.menu = parent.getMenu();
        this.map = parent.getMapPanel();
        this.map.setDrawInternalControls(false);
    }

    @Override
    protected void init() {
        this.map.setGuiScale(1.0F);
        applyMode(Mode.WORLD);
        requestSnapshots(true);
    }

    @Override
    public void onClose() {
        this.map.setDrawInternalControls(true);
        if (this.minecraft != null) this.minecraft.setScreen(this.parent);
    }

    @Override
    public void tick() {
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
        if (includeMap) {
            PacketDistributor.sendToServer(RailwayCommandPayload.simple(
                    this.menu.getPylonPos(), RailwayCommandPayload.Action.REQUEST_MAP, null, null));
        }
    }

    private void syncMapData() {
        this.menu.drainMapTiles().forEach(this.map::receive);
        RailwayMapPayload railwayMap = this.menu.drainRailwayMap();
        if (railwayMap != null) this.map.receive(railwayMap);
        this.map.setProjects(this.menu.getProjects());
        this.map.setTerritorySize(this.menu.getTerritorySize());
        this.map.setRailwayData(this.menu.getRailwayStations(), this.menu.getRailwayTrains());
        if (this.menu.getEconomyState() != null) {
            this.map.setEconomyInfrastructure(this.menu.getEconomyState().infrastructure());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        syncMapData();
        graphics.fill(0, 0, this.width, this.height, BACKGROUND);
        renderHeader(graphics, mouseX, mouseY);

        int bodyTop = HEADER_HEIGHT + GAP;
        int bodyBottom = this.height - FOOTER_HEIGHT - GAP;
        int sidebarWidth = this.sidebarCollapsed ? COLLAPSED_LEFT_WIDTH : LEFT_WIDTH;
        int mapLeft = sidebarWidth + GAP * 2;
        int mapRight = this.width - GAP;
        int mapWidth = Math.max(80, mapRight - mapLeft);
        int mapHeight = Math.max(80, bodyBottom - bodyTop);

        renderLeftPanel(graphics, GAP, bodyTop, sidebarWidth, mapHeight, mouseX, mouseY);
        this.map.render(graphics, mapLeft, bodyTop, mapWidth, mapHeight, mouseX, mouseY);
        renderFloatingContext(graphics, mapLeft, bodyTop, mapWidth, mapHeight, mouseX, mouseY);
        renderFooter(graphics);

        if (this.mode == Mode.ENERGY) {
            int cx = mapLeft + mapWidth / 2;
            int cy = bodyTop + mapHeight / 2;
            graphics.fill(cx - 104, cy - 25, cx + 104, cy + 25, 0xE0182023);
            graphics.renderOutline(cx - 104, cy - 25, 208, 50, ACCENT);
            graphics.drawCenteredString(this.font,
                    Component.translatable("gui.createcityexpansioncore.world_map.energy_placeholder"),
                    cx, cy - 5, 0xFFE7C27B);
        }
    }

    private void renderHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.fill(0, 0, this.width, HEADER_HEIGHT, PANEL);
        graphics.hLine(0, this.width, HEADER_HEIGHT - 1, ACCENT);
        graphics.drawString(this.font, this.title, 10, 10, 0xFFFFD59A, false);
        Component dimension = Component.literal(this.menu.getDimensionId().toString());
        graphics.drawString(this.font, dimension, this.width - this.font.width(dimension) - 34, 10,
                0xFF8F9A9E, false);
        boolean hover = inside(mouseX, mouseY, this.width - 24, 6, 18, 18);
        graphics.fill(this.width - 24, 6, this.width - 6, 24, hover ? 0xFF4A3A20 : PANEL_ALT);
        graphics.renderOutline(this.width - 24, 6, 18, 18, hover ? ACCENT : FRAME);
        graphics.drawCenteredString(this.font, "×", this.width - 15, 11, 0xFFFFFFFF);
    }

    private void renderLeftPanel(GuiGraphics graphics, int x, int y, int width, int height,
                                 int mouseX, int mouseY) {
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.renderOutline(x, y, width, height, FRAME);
        boolean toggleHover = inside(mouseX, mouseY, x + width - 20, y + 4, 16, 16);
        graphics.fill(x + width - 20, y + 4, x + width - 4, y + 20,
                toggleHover ? 0xFF4A3A20 : PANEL_ALT);
        graphics.renderOutline(x + width - 20, y + 4, 16, 16, toggleHover ? ACCENT : FRAME);
        graphics.drawCenteredString(this.font, this.sidebarCollapsed ? ">" : "<",
                x + width - 12, y + 8, 0xFFFFFFFF);
        if (this.sidebarCollapsed) return;
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.world_map.modes"),
                x + 9, y + 9, 0xFFFFD59A, false);
        int rowY = y + 26;
        for (Mode value : Mode.values()) {
            renderRow(graphics, x + 6, rowY, width - 12,
                    Component.translatable("gui.createcityexpansioncore.world_map.mode."
                            + value.name().toLowerCase(Locale.ROOT)), value == this.mode,
                    inside(mouseX, mouseY, x + 6, rowY, width - 12, 20));
            rowY += 23;
        }

        rowY += 10;
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.world_map.overlays"),
                x + 9, rowY, 0xFFFFD59A, false);
        rowY += 17;
        for (CityMapPanel.Overlay overlay : CityMapPanel.Overlay.values()) {
            boolean enabled = this.map.isOverlayEnabled(overlay);
            Component label = Component.translatable("gui.createcityexpansioncore.world_map.overlay."
                    + overlay.name().toLowerCase(Locale.ROOT));
            renderRow(graphics, x + 6, rowY, width - 12,
                    Component.literal((enabled ? "■ " : "□ ")).append(label), enabled,
                    inside(mouseX, mouseY, x + 6, rowY, width - 12, 18));
            rowY += 20;
        }
        rowY += 8;
        renderRow(graphics, x + 6, rowY, width - 12,
                Component.translatable("gui.createcityexpansioncore.world_map.center.city"), false,
                inside(mouseX, mouseY, x + 6, rowY, width - 12, 19));
        rowY += 21;
        renderRow(graphics, x + 6, rowY, width - 12,
                Component.translatable("gui.createcityexpansioncore.world_map.center.player"), false,
                inside(mouseX, mouseY, x + 6, rowY, width - 12, 19));
    }

    private void renderFloatingContext(GuiGraphics graphics, int mapX, int mapY, int mapWidth,
                                       int mapHeight, int mouseX, int mouseY) {
        CityMapPanel.Selection selected = this.map.getSelection();
        boolean pinned = selected != null;
        if (!pinned) selected = this.map.getHoveredSelection();
        if (selected == null) { this.cardWidth = this.cardHeight = 0; return; }
        int width = pinned ? 214 : 184;
        int contentHeight = 38 + this.font.wordWrapHeight(selected.title(), width - 18)
                + selected.details().stream().mapToInt(detail ->
                this.font.wordWrapHeight(detail, width - 18) + 4).sum();
        int height = Math.min(mapHeight - 8, Math.max(pinned ? 82 : 58, contentHeight));
        int x = pinned ? mapX + mapWidth - width - 8 : mouseX + 14;
        int y = pinned ? mapY + 8 : mouseY + 12;
        x = Math.max(mapX + 4, Math.min(mapX + mapWidth - width - 4, x));
        y = Math.max(mapY + 4, Math.min(mapY + mapHeight - height - 4, y));
        this.cardX = x; this.cardY = y; this.cardWidth = width; this.cardHeight = height;
        graphics.fill(x, y, x + width, y + height, PANEL);
        graphics.renderOutline(x, y, width, height, pinned ? ACCENT : FRAME);
        int cursor = y + 9;
        graphics.drawWordWrap(this.font, selected.title(), x + 9, cursor, width - 18, 0xFFFFFFFF);
        cursor += this.font.wordWrapHeight(selected.title(), width - 18) + 7;
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.world_map.context.type",
                        Component.translatable("gui.createcityexpansioncore.world_map.selection."
                                + selected.kind().name().toLowerCase(Locale.ROOT))),
                x + 9, cursor, selected.own() ? 0xFF77B992 : 0xFFB7C0C3, false);
        cursor += 14;
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.world_map.context.coordinates",
                        selected.pos().getX(), selected.pos().getZ()),
                x + 9, cursor, 0xFFB7C0C3, false);
        cursor += 17;
        for (Component detail : selected.details()) {
            graphics.drawWordWrap(this.font, detail, x + 9, cursor, width - 18, 0xFFB7C0C3);
            cursor += this.font.wordWrapHeight(detail, width - 18) + 4;
        }
        if (selected.own()) {
            graphics.fill(x + 8, cursor + 4, x + width - 8, cursor + 5, 0xFF365C48);
            graphics.drawWordWrap(this.font,
                    Component.translatable("gui.createcityexpansioncore.world_map.context.own_detail"),
                    x + 9, cursor + 10, width - 18, 0xFF77B992);
        }
    }

    private void renderFooter(GuiGraphics graphics) {
        int y = this.height - FOOTER_HEIGHT;
        graphics.fill(0, y, this.width, this.height, PANEL);
        graphics.hLine(0, this.width, y, FRAME);
        Component city = Component.translatable("gui.createcityexpansioncore.world_map.footer",
                this.menu.getCityName(), this.menu.getCityLevel(), this.menu.getMemberCount(),
                this.menu.getProjects().size(), this.menu.getProjectCapacity());
        graphics.drawString(this.font, city, 10, y + 8, 0xFFD3D8DA, false);
    }

    private void renderRow(GuiGraphics graphics, int x, int y, int width, Component label,
                           boolean active, boolean hovered) {
        graphics.fill(x, y, x + width, y + 19,
                active ? 0xFF49391F : hovered ? 0xFF303A3D : PANEL_ALT);
        graphics.renderOutline(x, y, width, 19, hovered ? ACCENT : active ? 0xFF9E783E : FRAME);
        graphics.drawString(this.font, label, x + 7, y + 6,
                active ? 0xFFFFD59A : 0xFFBCC4C7, false);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && inside(mouseX, mouseY, this.width - 24, 6, 18, 18)) {
            onClose();
            return true;
        }
        int bodyTop = HEADER_HEIGHT + GAP;
        int sidebarWidth = this.sidebarCollapsed ? COLLAPSED_LEFT_WIDTH : LEFT_WIDTH;
        if (button == 0 && inside(mouseX, mouseY, GAP + sidebarWidth - 20, bodyTop + 4, 16, 16)) {
            this.sidebarCollapsed = !this.sidebarCollapsed;
            return true;
        }
        if (this.map.getSelection() != null
                && inside(mouseX, mouseY, cardX, cardY, cardWidth, cardHeight)) return true;
        if (button == 0) this.map.clearSelection();
        if (this.sidebarCollapsed) {
            if (this.map.mouseClicked(mouseX, mouseY, button)) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }
        int rowY = bodyTop + 26;
        for (Mode value : Mode.values()) {
            if (button == 0 && inside(mouseX, mouseY, GAP + 6, rowY, LEFT_WIDTH - 12, 20)) {
                applyMode(value);
                return true;
            }
            rowY += 23;
        }
        rowY += 27;
        for (CityMapPanel.Overlay overlay : CityMapPanel.Overlay.values()) {
            if (button == 0 && inside(mouseX, mouseY, GAP + 6, rowY, LEFT_WIDTH - 12, 18)) {
                this.map.setOverlay(overlay, !this.map.isOverlayEnabled(overlay));
                return true;
            }
            rowY += 20;
        }
        rowY += 8;
        if (button == 0 && inside(mouseX, mouseY, GAP + 6, rowY, LEFT_WIDTH - 12, 19)) {
            this.map.centerOn(this.menu.getPylonPos());
            return true;
        }
        rowY += 21;
        if (button == 0 && inside(mouseX, mouseY, GAP + 6, rowY, LEFT_WIDTH - 12, 19)
                && this.minecraft != null && this.minecraft.player != null
                && this.minecraft.player.level().dimension().location().equals(this.menu.getDimensionId())) {
            this.map.centerOn(this.minecraft.player.blockPosition());
            return true;
        }
        if (this.map.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
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
        return this.map.mouseScrolled(mouseX, mouseY, scrollY)
                || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void applyMode(Mode mode) {
        this.mode = mode;
        switch (mode) {
            case WORLD -> {
                this.map.setOverlay(CityMapPanel.Overlay.TERRITORIES, true);
                this.map.setOverlay(CityMapPanel.Overlay.PROJECTS, true);
                this.map.setOverlay(CityMapPanel.Overlay.TRACKS, true);
                this.map.setOverlay(CityMapPanel.Overlay.STATIONS, true);
                this.map.setOverlay(CityMapPanel.Overlay.TRAINS, false);
                this.map.setOverlay(CityMapPanel.Overlay.TRADE, true);
            }
            case RAILWAY -> {
                this.map.setOverlay(CityMapPanel.Overlay.TERRITORIES, true);
                this.map.setOverlay(CityMapPanel.Overlay.PROJECTS, false);
                this.map.setOverlay(CityMapPanel.Overlay.TRACKS, true);
                this.map.setOverlay(CityMapPanel.Overlay.STATIONS, true);
                this.map.setOverlay(CityMapPanel.Overlay.TRAINS, true);
                this.map.setOverlay(CityMapPanel.Overlay.TRADE, false);
            }
            case TRADE -> {
                this.map.setOverlay(CityMapPanel.Overlay.TERRITORIES, true);
                this.map.setOverlay(CityMapPanel.Overlay.PROJECTS, false);
                this.map.setOverlay(CityMapPanel.Overlay.TRACKS, true);
                this.map.setOverlay(CityMapPanel.Overlay.STATIONS, true);
                this.map.setOverlay(CityMapPanel.Overlay.TRAINS, false);
                this.map.setOverlay(CityMapPanel.Overlay.TRADE, true);
            }
            case ENERGY -> {
                this.map.setOverlay(CityMapPanel.Overlay.TERRITORIES, true);
                this.map.setOverlay(CityMapPanel.Overlay.PROJECTS, false);
                this.map.setOverlay(CityMapPanel.Overlay.TRACKS, false);
                this.map.setOverlay(CityMapPanel.Overlay.STATIONS, false);
                this.map.setOverlay(CityMapPanel.Overlay.TRAINS, false);
                this.map.setOverlay(CityMapPanel.Overlay.TRADE, false);
            }
        }
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private enum Mode { WORLD, RAILWAY, TRADE, ENERGY }
}
