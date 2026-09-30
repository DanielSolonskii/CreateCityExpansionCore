package com.example.examplemod.client.map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/** Shared visual language for physical objects placed over the atlas. */
public final class MapMarkerRenderer {
    private static final int BACKING = 0xEE111719;
    private static final int FRAME = 0xFF596064;
    private static final int HOVER = 0xFFBCA06A;
    private static final int SELECTED = 0xFFFFD58A;
    private static final int OWNED = 0xFFD09A3A;
    private static final int WARNING = 0xFFD26C62;
    private static final int PLAYER = 0xFF71D6E4;

    private MapMarkerRenderer() {}

    public static void render(GuiGraphics graphics, int centerX, int centerY, ItemStack icon,
                              boolean owned, boolean warning, boolean hovered, boolean selected) {
        int radius = selected ? 11 : hovered ? 10 : 9;
        int left = centerX - radius;
        int top = centerY - radius;
        int size = radius * 2;
        graphics.fill(left, top, left + size, top + size,
                hovered && !selected ? 0xF5253032 : BACKING);
        graphics.renderOutline(left, top, size, size,
                selected ? SELECTED : hovered ? HOVER : FRAME);
        graphics.renderItem(icon, centerX - 8, centerY - 8);

        if (owned) {
            graphics.fill(left + size - 6, top + size - 6, left + size - 1, top + size - 1, BACKING);
            graphics.fill(left + size - 5, top + size - 5, left + size - 2, top + size - 2, OWNED);
        }
        if (warning) {
            graphics.fill(left + 2, top + 2, left + 7, top + 4, WARNING);
        }
    }

    /** Player position uses a compass with a cool crosshair, distinct from owned infrastructure. */
    public static void renderPlayer(GuiGraphics graphics, int centerX, int centerY, ItemStack icon,
                                    boolean hovered, boolean selected) {
        render(graphics, centerX, centerY, icon, false, false, hovered, selected);
        int color = selected ? SELECTED : PLAYER;
        graphics.hLine(centerX - 13, centerX - 10, centerY, color);
        graphics.hLine(centerX + 10, centerX + 13, centerY, color);
        graphics.vLine(centerX, centerY - 13, centerY - 10, color);
        graphics.vLine(centerX, centerY + 10, centerY + 13, color);
    }

    public static void renderCardIcon(GuiGraphics graphics, int x, int y, ItemStack icon,
                                      boolean owned, boolean warning) {
        graphics.fill(x, y, x + 26, y + 26, BACKING);
        graphics.renderOutline(x, y, 26, 26, owned ? OWNED : FRAME);
        graphics.renderItem(icon, x + 5, y + 5);
        if (owned) graphics.fill(x + 20, y + 20, x + 24, y + 24, OWNED);
        if (warning) graphics.fill(x + 2, y + 2, x + 8, y + 4, WARNING);
    }
}
