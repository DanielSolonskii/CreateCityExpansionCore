package com.example.examplemod.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * A button painted in the shared chrome, with a hover that lerps in instead of snapping.
 *
 * <p>The vanilla widget swaps to its hover colour on a single frame, which is the one piece of
 * motion the older windows never picked up. Subclassing {@link Button} keeps every existing click
 * handler, focus rule, narration string and {@code active} check working unchanged -- only the
 * painting differs, so these drop in where a {@code Button.builder(...)} used to be.
 */
public final class ChromeButton extends Button {
    private final boolean primary;
    /**
     * Smoothed hover. Keyed by position: a button never moves, so its coordinates are already a
     * stable identity and no caller has to supply one.
     */
    private final UiMotion motion = new UiMotion();

    public ChromeButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, false);
    }

    public ChromeButton(int x, int y, int width, int height, Component message, OnPress onPress, boolean primary) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        this.primary = primary;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        var r = new WorldMapUiLayout.Rect(getX(), getY(), getWidth(), getHeight());
        float lit = motion.hover(getX() * 7919 + getY(), this.isHoveredOrFocused(),
                motion.delta(net.minecraft.Util.getMillis()));
        WorldMapChrome.control(g, r, false, lit, this.active, this.primary);
        // The caption stays inside the button rather than being clipped by the rounded corners, and
        // a disabled control dims its text as well as its surface.
        int textX = r.x() + Math.max(6, (r.width() - minecraft.font.width(getMessage())) / 2);
        g.drawString(minecraft.font,
                WorldMapChrome.fit(minecraft.font, getMessage(), r.width() - 12),
                textX, r.y() + (r.height() - 8) / 2,
                this.active ? WorldMapChrome.TEXT : WorldMapChrome.MUTED, false);
    }
}
