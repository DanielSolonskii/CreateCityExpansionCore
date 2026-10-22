package com.example.examplemod.client.screen;

import com.example.examplemod.client.map.MinimapClient;
import com.example.examplemod.client.map.MinimapLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Compact local settings; never changes server/world state. */
public final class MinimapSettingsScreen extends Screen {
    private boolean known,sharing;
    public MinimapSettingsScreen() { super(Component.translatable("gui.createcityexpansioncore.minimap.settings")); }
    @Override protected void init() {
        var settings = MinimapClient.settings();
        var layout = MinimapLayout.settings(width, height);
        known=com.example.examplemod.client.NearbySceneClient.known;sharing=com.example.examplemod.client.NearbySceneClient.sharing;
        String[] keys = {"enabled", "size", "zoom", "opacity", "corner", "cities", "projects", "sharing"};
        String[] values = {on(settings.enabled), "" + settings.size,
                String.format(java.util.Locale.ROOT, "%.2fx", settings.zoom), settings.opacity + "%",
                Component.translatable("gui.createcityexpansioncore.minimap." + (settings.corner == 0 ? "right" : "left")).getString(),
                on(settings.cities), on(settings.projects),known?on(sharing):Component.translatable("gui.createcityexpansioncore.nearby.loading").getString()};
        for (int i = 0; i < keys.length; i++) {
            final int index = i;
            var control=addRenderableWidget(new SettingButton(layout.buttonX(i), layout.buttonY(i), layout.buttonWidth(),
                    Component.translatable("gui.createcityexpansioncore.minimap." + keys[i], values[i]), button -> {
                switch (index) {
                    case 0 -> settings.enabled = !settings.enabled;
                    case 1 -> settings.size = settings.size >= 156 ? 96 : settings.size + 16;
                    case 2 -> settings.zoom = settings.zoom >= 2 ? .125 : settings.zoom * 2;
                    case 3 -> settings.opacity = settings.opacity >= 100 ? 40 : settings.opacity + 15;
                    case 4 -> settings.corner = 1 - settings.corner;
                    case 5 -> settings.cities = !settings.cities;
                    case 6 -> settings.projects = !settings.projects;
                    case 7 -> com.example.examplemod.client.NearbySceneClient.share(!sharing);
                }
                settings.save(); rebuildWidgets();
            }));
            if(i==7){control.active=known;control.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("gui.createcityexpansioncore.nearby.sharing_hint")));}
        }
        addRenderableWidget(new SettingButton(layout.x() + 10, layout.y() + layout.height() - 28,
                layout.width() - 20, Component.translatable("gui.done"), button -> onClose()));
    }
    private static String on(boolean value) { return Component.translatable(value ? "options.on" : "options.off").getString(); }
    @Override public void tick(){if(known!=com.example.examplemod.client.NearbySceneClient.known||sharing!=com.example.examplemod.client.NearbySceneClient.sharing)rebuildWidgets();}
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        var layout = MinimapLayout.settings(width, height);
        WorldMapChrome.panel(graphics, layout.x(), layout.y(), layout.width(), layout.height());
        graphics.fill(layout.x() + 8, layout.y() + 8, layout.x() + 10, layout.y() + 21, WorldMapChrome.ACCENT);
        graphics.drawString(font, title, layout.x() + 17, layout.y() + 10, WorldMapChrome.ACCENT, false);
        super.render(graphics, mouseX, mouseY, partial);
    }
    @Override public boolean isPauseScreen() { return false; }
    private static final class SettingButton extends Button {
        SettingButton(int x, int y, int width, Component message, OnPress action) {
            super(x, y, width, 20, message, action, DEFAULT_NARRATION);
        }
        @Override protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partial) {
            var bounds = new WorldMapUiLayout.Rect(getX(), getY(), getWidth(), getHeight());
            WorldMapChrome.control(graphics, bounds, false, isHoveredOrFocused(), active, false);
            var font = Minecraft.getInstance().font;
            graphics.drawCenteredString(font, WorldMapChrome.fit(font, getMessage(), getWidth() - 12),
                    getX() + getWidth() / 2, getY() + 6, isHoveredOrFocused() ? WorldMapChrome.ACCENT : WorldMapChrome.TEXT);
        }
    }
}
