package com.example.examplemod.client.screen;

import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.client.map.MapMarkerRenderer;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.EconomyCommandPayload;
import com.example.examplemod.network.RailwayCommandPayload;
import com.example.examplemod.network.RailwayMapPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

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
    private static final int BUTTON = 26;
    private static final long TOOLTIP_DELAY_MILLIS = 350L;
    private static boolean tutorialSeen;

    private final CityPylonScreen parent;
    private final CityPylonMenu menu;
    private final CityMapPanel map;
    private final ItemStack titleIcon = item("createcityexpansioncore:city_pylon");
    private final ItemStack layersIcon = item("minecraft:map");
    private final ItemStack searchIcon = item("minecraft:spyglass");
    private final ItemStack playerIcon = item("minecraft:compass");
    private final ItemStack cityIcon = item("createcityexpansioncore:city_pylon");
    private final ItemStack residentsIcon = item("minecraft:player_head");

    private Mode mode = Mode.WORLD;
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
        applyMode(Mode.WORLD);
        requestSnapshots(true);
        this.tutorialTicks = tutorialSeen ? 0 : 220;
        int searchX = Math.max(8, this.width - 236);
        this.searchBox = new EditBox(this.font, searchX + 10, popoverY() + 5, 208, 16,
                Component.translatable("gui.createcityexpansioncore.world_map.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(64);
        this.searchBox.setHint(Component.translatable("gui.createcityexpansioncore.world_map.search.hint"));
        this.searchBox.setVisible(false);
        this.searchBox.setResponder(query -> this.searchResults = this.map.search(query));
        addRenderableWidget(this.searchBox);
    }

    @Override
    public void onClose() {
        this.map.setDrawInternalControls(true);
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
        if (this.menu.getEconomyState() != null) {
            this.map.setEconomyInfrastructure(this.menu.getEconomyState().infrastructure());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        syncMapData();
        this.hoveredTooltip = null;
        graphics.fill(0, 0, this.width, this.height, 0xFF101719);
        this.map.render(graphics, 0, 0, this.width, this.height, mouseX, mouseY);
        renderTitle(graphics);
        renderModeToolbar(graphics, mouseX, mouseY);
        renderActionToolbar(graphics, mouseX, mouseY);
        renderContextCard(graphics);
        renderStatus(graphics, mouseX, mouseY);
        renderEnergyPlaceholder(graphics);
        if (this.layersOpen) renderLayers(graphics, mouseX, mouseY);
        if (this.searchOpen) renderSearchPanel(graphics, mouseX, mouseY);
        renderTutorial(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.hoveredTooltip != null) renderTooltip(graphics, this.hoveredTooltip, mouseX, mouseY);
    }

    private void renderTitle(GuiGraphics graphics) {
        int width = this.width >= 560 ? 122 : 32;
        plate(graphics, 8, 8, width, 28);
        graphics.renderItem(this.titleIcon, 14, 14);
        if (width > 32) {
            graphics.drawString(this.font,
                    Component.translatable("gui.createcityexpansioncore.world_map.identity"), 36, 13,
                    0xFFFFD59A, false);
            graphics.drawString(this.font, Component.literal(this.menu.getDimensionId().getPath()),
                    36, 24, MUTED, false);
        }
    }

    private void renderModeToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int startX = this.width >= 560 ? 136 : 44;
        plate(graphics, startX, 8, Mode.values().length * 30 + 4, 28);
        int x = startX + 3;
        for (Mode value : Mode.values()) {
            iconButton(graphics, x, 9, value.icon, value == this.mode, mouseX, mouseY,
                    Component.translatable("gui.createcityexpansioncore.world_map.mode."
                            + value.name().toLowerCase(Locale.ROOT)));
            x += 30;
        }
    }

    private void renderActionToolbar(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.width - 160;
        int y = actionY();
        plate(graphics, x - 3, y - 1, 152, 28);
        iconButton(graphics, x, y, this.playerIcon, false, mouseX, mouseY,
                Component.translatable("gui.createcityexpansioncore.world_map.center.player"));
        x += 30;
        iconButton(graphics, x, y, this.cityIcon, false, mouseX, mouseY,
                Component.translatable("gui.createcityexpansioncore.world_map.center.city"));
        x += 30;
        iconButton(graphics, x, y, this.searchIcon, this.searchOpen, mouseX, mouseY,
                Component.translatable("gui.createcityexpansioncore.world_map.search"));
        x += 30;
        iconButton(graphics, x, y, this.layersIcon, this.layersOpen, mouseX, mouseY,
                Component.translatable("gui.createcityexpansioncore.world_map.layers"));
        x += 30;
        textButton(graphics, x, y, "×", mouseX, mouseY,
                Component.translatable("gui.createcityexpansioncore.world_map.close"));
    }

    private void renderLayers(GuiGraphics graphics, int mouseX, int mouseY) {
        int width = 170;
        int x = Math.max(8, this.width - width - 8);
        int y = popoverY();
        int height = 25 + CityMapPanel.Overlay.values().length * 20;
        plate(graphics, x, y, width, height);
        graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.layers"),
                x + 9, y + 9, 0xFFFFD59A, false);
        int rowY = y + 24;
        for (CityMapPanel.Overlay overlay : CityMapPanel.Overlay.values()) {
            boolean enabled = this.map.isOverlayEnabled(overlay);
            boolean hover = inside(mouseX, mouseY, x + 5, rowY, width - 10, 18);
            if (hover) graphics.fill(x + 5, rowY, x + width - 5, rowY + 18, 0xFF303A3D);
            graphics.drawString(this.font, enabled ? "◆" : "◇", x + 10, rowY + 5,
                    enabled ? 0xFFFFD59A : 0xFF788589, false);
            graphics.drawString(this.font, Component.translatable("gui.createcityexpansioncore.world_map.overlay."
                    + overlay.name().toLowerCase(Locale.ROOT)), x + 25, rowY + 5,
                    enabled ? TEXT : MUTED, false);
            rowY += 20;
        }
    }

    private void renderSearchPanel(GuiGraphics graphics, int mouseX, int mouseY) {
        int width = 228;
        int x = Math.max(8, this.width - width - 8);
        int y = popoverY();
        int height = 31 + Math.max(1, this.searchResults.size()) * 25;
        plate(graphics, x, y, width, height);
        graphics.fill(x + 7, y + 5, x + width - 7, y + 25, 0xFF111719);
        graphics.renderOutline(x + 7, y + 5, width - 14, 20, ACCENT);
        if (this.searchResults.isEmpty() && !this.searchBox.getValue().isBlank()) {
            graphics.drawString(this.font,
                    Component.translatable("gui.createcityexpansioncore.world_map.search.empty"),
                    x + 10, y + 36, MUTED, false);
        }
        int rowY = y + 31;
        for (CityMapPanel.Selection result : this.searchResults) {
            boolean hover = inside(mouseX, mouseY, x + 5, rowY, width - 10, 23);
            graphics.fill(x + 5, rowY, x + width - 5, rowY + 23, hover ? 0xFF303A3D : PANEL_ALT);
            graphics.renderItem(result.icon(), x + 8, rowY + 3);
            graphics.drawString(this.font, result.title(), x + 29, rowY + 4, TEXT, false);
            graphics.drawString(this.font, selectionType(result), x + 29, rowY + 14, MUTED, false);
            rowY += 25;
        }
    }

    private void renderContextCard(GuiGraphics graphics) {
        CityMapPanel.Selection value = this.map.getSelection();
        boolean pinned = value != null;
        if (!pinned) value = this.map.getHoveredSelection();
        if (value == null) { this.cardWidth = this.cardHeight = 0; return; }
        int width = Math.min(pinned ? 224 : 190, this.width - 16);
        int detailCount = pinned ? value.details().size() : Math.min(2, value.details().size());
        int detailsHeight = 0;
        for (int index = 0; index < detailCount; index++) {
            detailsHeight += this.font.wordWrapHeight(value.details().get(index), width - 18) + 3;
        }
        Component ownDetail = Component.translatable("gui.createcityexpansioncore.world_map.context.own_detail");
        int footerHeight = pinned && value.own()
                ? this.font.wordWrapHeight(ownDetail, width - 18) + 11
                : pinned ? 0 : 16;
        int height = 49 + detailsHeight + footerHeight;
        CityMapPanel.ScreenPoint anchor = this.map.selectionAnchor(value);
        CityMapPanel.ScreenPoint card = this.map.contextCardPosition(anchor, width, height);
        int x = card.x();
        int y = Math.max(40, Math.min(this.height - height - 28, card.y()));
        this.cardX = x; this.cardY = y; this.cardWidth = width; this.cardHeight = height;
        plate(graphics, x, y, width, height);
        graphics.renderOutline(x, y, width, height, pinned ? ACCENT : FRAME);
        MapMarkerRenderer.renderCardIcon(graphics, x + 8, y + 8, value.icon(), value.own(), value.warning());
        graphics.drawString(this.font, value.title(), x + 42, y + 9, 0xFFFFFFFF, false);
        graphics.drawString(this.font, selectionType(value), x + 42, y + 21,
                value.own() ? 0xFFFFD59A : MUTED, false);
        graphics.drawString(this.font,
                Component.translatable("gui.createcityexpansioncore.world_map.context.coordinates",
                        value.pos().getX(), value.pos().getZ()), x + 42, y + 32, MUTED, false);
        int cursor = y + 48;
        for (int index = 0; index < detailCount; index++) {
            Component detail = value.details().get(index);
            graphics.drawWordWrap(this.font, detail, x + 9, cursor, width - 18, TEXT);
            cursor += this.font.wordWrapHeight(detail, width - 18) + 3;
        }
        if (pinned && value.own()) {
            graphics.hLine(x + 8, x + width - 8, cursor + 1, 0xFF6F542C);
            graphics.drawWordWrap(this.font, ownDetail, x + 9, cursor + 7, width - 18, 0xFFE2BD75);
        } else if (!pinned) {
            graphics.drawString(this.font,
                    Component.translatable("gui.createcityexpansioncore.world_map.hover.details"),
                    x + 9, height + y - 12, 0xFFB89A68, false);
        }
    }

    private void renderStatus(GuiGraphics graphics, int mouseX, int mouseY) {
        Component coordinates = this.map.coordinateStatus(mouseX, mouseY);
        int coordinateWidth = this.font.width(coordinates) + 16;
        int rightX = this.width - coordinateWidth - 8;
        int residentsWidth = this.font.width(Integer.toString(this.menu.getMemberCount())) + 32;
        int residentsX = Math.max(8, rightX - residentsWidth - 6);
        int cityWidth = Math.max(42, residentsX - 14);
        cityWidth = Math.min(cityWidth, Math.min(190, this.font.width(this.menu.getCityName()) + 30));
        int y = this.height - 23;
        statusChip(graphics, 8, y, cityWidth);
        graphics.renderItem(this.cityIcon, 13, y + 1);
        Component cityName = Component.literal(this.font.plainSubstrByWidth(
                this.menu.getCityName(), cityWidth - 29));
        graphics.drawString(this.font, cityName, 34, y + 5, TEXT, false);
        statusChip(graphics, residentsX, y, residentsWidth);
        graphics.renderItem(this.residentsIcon, residentsX + 5, y + 1);
        graphics.drawString(this.font, Integer.toString(this.menu.getMemberCount()),
                residentsX + 24, y + 5, MUTED, false);
        statusChip(graphics, rightX, y, coordinateWidth);
        graphics.drawString(this.font, coordinates, rightX + 8, y + 5, MUTED, false);
    }

    private void renderEnergyPlaceholder(GuiGraphics graphics) {
        if (this.mode != Mode.ENERGY) return;
        Component text = Component.translatable("gui.createcityexpansioncore.world_map.energy_placeholder");
        int width = Math.min(this.width - 24, this.font.width(text) + 28);
        int x = (this.width - width) / 2;
        int y = this.height / 2 - 16;
        plate(graphics, x, y, width, 32);
        graphics.renderOutline(x, y, width, 32, ACCENT);
        graphics.drawCenteredString(this.font, text, this.width / 2, y + 12, 0xFFE7C27B);
    }

    private void renderTutorial(GuiGraphics graphics) {
        if (this.tutorialTicks <= 0) return;
        Component text = Component.translatable("gui.createcityexpansioncore.world_map.tutorial");
        int width = Math.min(this.width - 24, this.font.width(text) + 20);
        int x = (this.width - width) / 2;
        int y = this.height - 48;
        plate(graphics, x, y, width, 18);
        graphics.drawCenteredString(this.font, text, this.width / 2, y + 6, TEXT);
    }

    private void iconButton(GuiGraphics graphics, int x, int y, ItemStack icon, boolean active,
                            int mouseX, int mouseY, Component tooltip) {
        boolean hover = inside(mouseX, mouseY, x, y, BUTTON, BUTTON);
        graphics.fill(x, y, x + BUTTON, y + BUTTON,
                active ? 0xFF4A391E : hover ? 0xFF303A3D : PANEL_ALT);
        if (active) graphics.renderOutline(x, y, BUTTON, BUTTON, ACCENT);
        else if (hover) graphics.renderOutline(x, y, BUTTON, BUTTON, FRAME);
        graphics.renderItem(icon, x + 5, y + 5);
        offerTooltip(tooltip, hover);
    }

    private void textButton(GuiGraphics graphics, int x, int y, String text, int mouseX, int mouseY,
                            Component tooltip) {
        boolean hover = inside(mouseX, mouseY, x, y, BUTTON, BUTTON);
        graphics.fill(x, y, x + BUTTON, y + BUTTON, hover ? 0xFF4A3028 : PANEL_ALT);
        graphics.renderOutline(x, y, BUTTON, BUTTON, hover ? ACCENT : FRAME);
        graphics.drawCenteredString(this.font, text, x + BUTTON / 2, y + 8, 0xFFFFFFFF);
        offerTooltip(tooltip, hover);
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
        int modeX = (this.width >= 560 ? 164 : 44) + 3;
        for (Mode value : Mode.values()) {
            if (inside(mouseX, mouseY, modeX, 9, BUTTON, BUTTON)) {
                applyMode(value); dismissTutorial(); return true;
            }
            modeX += 30;
        }
        int actionX = this.width - 160;
        int actionY = actionY();
        if (inside(mouseX, mouseY, actionX, actionY, BUTTON, BUTTON)) {
            if (this.minecraft != null && this.minecraft.player != null
                    && this.minecraft.player.level().dimension().location().equals(this.menu.getDimensionId())) {
                this.map.centerOn(this.minecraft.player.blockPosition());
            }
            dismissTutorial(); return true;
        }
        actionX += 30;
        if (inside(mouseX, mouseY, actionX, actionY, BUTTON, BUTTON)) {
            this.map.centerOn(this.menu.getPylonPos()); dismissTutorial(); return true;
        }
        actionX += 30;
        if (inside(mouseX, mouseY, actionX, actionY, BUTTON, BUTTON)) {
            this.searchOpen = !this.searchOpen; this.layersOpen = false;
            this.searchBox.setVisible(this.searchOpen);
            setFocused(this.searchOpen ? this.searchBox : null);
            return true;
        }
        actionX += 30;
        if (inside(mouseX, mouseY, actionX, actionY, BUTTON, BUTTON)) {
            this.layersOpen = !this.layersOpen; this.searchOpen = false;
            this.searchBox.setVisible(false); return true;
        }
        actionX += 30;
        if (inside(mouseX, mouseY, actionX, actionY, BUTTON, BUTTON)) { onClose(); return true; }
        if (this.searchOpen && this.searchBox.mouseClicked(mouseX, mouseY, button)) return true;
        if (this.layersOpen && handleLayerClick(mouseX, mouseY)) return true;
        if (this.searchOpen && handleSearchClick(mouseX, mouseY)) return true;
        if (this.map.getSelection() != null && inside(mouseX, mouseY, this.cardX, this.cardY,
                this.cardWidth, this.cardHeight)) return true;
        this.map.clearSelection();
        dismissTutorial();
        return this.map.mouseClicked(mouseX, mouseY, button) || super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handleLayerClick(double mouseX, double mouseY) {
        int width = 170;
        int x = Math.max(8, this.width - width - 8);
        int y = popoverY();
        int rowY = y + 24;
        for (CityMapPanel.Overlay overlay : CityMapPanel.Overlay.values()) {
            if (inside(mouseX, mouseY, x + 5, rowY, width - 10, 18)) {
                this.map.setOverlay(overlay, !this.map.isOverlayEnabled(overlay)); return true;
            }
            rowY += 20;
        }
        return inside(mouseX, mouseY, x, y, width, 25 + CityMapPanel.Overlay.values().length * 20);
    }

    private boolean handleSearchClick(double mouseX, double mouseY) {
        int width = 228;
        int x = Math.max(8, this.width - width - 8);
        int y = popoverY();
        int rowY = y + 31;
        for (CityMapPanel.Selection result : this.searchResults) {
            if (inside(mouseX, mouseY, x + 5, rowY, width - 10, 23)) {
                this.map.selectAndCenter(result); this.searchOpen = false;
                this.searchBox.setVisible(false); setFocused(null); return true;
            }
            rowY += 25;
        }
        return inside(mouseX, mouseY, x, y, width, 31 + Math.max(1, this.searchResults.size()) * 25);
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

    private void applyMode(Mode mode) {
        this.mode = mode;
        this.map.setFocus(mode.focus);
        this.map.setOverlay(CityMapPanel.Overlay.CITIES, true);
        switch (mode) {
            case WORLD -> setOverlays(true, true, true, true, false, true);
            case RAILWAY -> setOverlays(true, false, true, true, true, false);
            case TRADE -> setOverlays(true, false, true, true, false, true);
            case ENERGY -> setOverlays(true, false, false, false, false, false);
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

    private int actionY() {
        return this.width < 400 ? 41 : 9;
    }

    private int popoverY() {
        return this.width < 400 ? 72 : 40;
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private enum Mode {
        WORLD("minecraft:compass", CityMapPanel.Focus.WORLD),
        RAILWAY("create:track", CityMapPanel.Focus.RAILWAY),
        TRADE("minecraft:emerald", CityMapPanel.Focus.TRADE),
        ENERGY("minecraft:redstone", CityMapPanel.Focus.ENERGY);

        private final ItemStack icon;
        private final CityMapPanel.Focus focus;

        Mode(String iconId, CityMapPanel.Focus focus) {
            this.icon = item(iconId);
            this.focus = focus;
        }
    }
}
