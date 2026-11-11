package com.example.examplemod.client.map;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import com.mojang.math.Axis;

/**
 * Общий визуальный язык объектов поверх карты.
 *
 * <p>Цвета берутся из {@link MapPalette}, а не задаются здесь: маркер
 * города, маркер станции и легенда обязаны быть одного тона,
 * иначе прибор начинает выглядеть собранным из разных экранов.
 */
public final class MapMarkerRenderer {
    private static final int BACKING = 0xEE1B1E20;
    private static final int FRAME = MapPalette.INK_SOFT;
    private static final int HOVER = 0xFFBCA06A;
    private static final int SELECTED = MapPalette.SELECTED;
    private static final int OWNED = MapPalette.OWN;
    private static final int WARNING = MapPalette.ALERT;
    private static final int PLAYER = MapPalette.GOOD;

    private MapMarkerRenderer() {}

    public static void renderMap(GuiGraphics graphics, double zoom, int centerX, int centerY, ItemStack icon,
                                 boolean owned, boolean warning, boolean hovered, boolean selected) {
        if (zoom >= 0.8 || hovered || selected) {
            render(graphics, centerX, centerY, icon, owned, warning, hovered, selected);
            return;
        }
        int color = owned ? OWNED : MapPalette.NEUTRAL;
        AtlasGlyph kind = glyph(icon);
        int radius = kind == AtlasGlyph.CITY ? 5 : 4;
        graphics.fill(centerX - radius - 1, centerY - radius - 1, centerX + radius + 1, centerY + radius + 1, BACKING);
        graphics.renderOutline(centerX - radius, centerY - radius, radius * 2, radius * 2, color);
        if (kind == AtlasGlyph.CITY || kind == AtlasGlyph.MINE) graphics.fill(centerX - 1, centerY - 1, centerX + 1, centerY + 1, color);
        else graphics.hLine(centerX - radius + 1, centerX + radius - 2, centerY, color);
        if (warning) warningBadge(graphics, centerX + 3, centerY - 7);
    }

    public static void render(GuiGraphics graphics, int centerX, int centerY, ItemStack icon,
                              boolean owned, boolean warning, boolean hovered, boolean selected) {
        int radius = selected ? 12 : 11;
        int left = centerX - radius;
        int top = centerY - radius;
        int size = radius * 2;
        graphics.fill(left, top, left + size, top + size,
                hovered && !selected ? 0xF5253032 : BACKING);
        graphics.renderOutline(left, top, size, size,
                selected ? SELECTED : hovered ? HOVER : FRAME);
        graphics.hLine(left + 3, left + size - 4, top + 1, OWNED);
        drawGlyph(graphics, glyph(icon), centerX - 8, centerY - 8, MapPalette.TEXT);

        if (owned) {
            graphics.fill(left + size - 6, top + size - 6, left + size - 1, top + size - 1, BACKING);
            graphics.fill(left + size - 5, top + size - 5, left + size - 2, top + size - 2, OWNED);
        }
        if (warning) {
            warningBadge(graphics, left + size - 7, top - 2);
        }
    }

    /** North-up map: Minecraft yaw zero points south, so the arrow rotates by yaw + 180. */
    public static void renderPlayer(GuiGraphics graphics, int centerX, int centerY, ItemStack icon,
                                    boolean hovered, boolean selected) {
        var player=Minecraft.getInstance().player;
        renderPlayer(graphics,centerX,centerY,icon,hovered,selected,player==null?0:player.getYRot()+180);
    }

    public static void renderPlayer(GuiGraphics graphics, int centerX, int centerY, ItemStack icon,
                                    boolean hovered, boolean selected, double yaw) {
        if(hovered||selected) for(int a=0;a<40;a++) {
            double angle=a*Math.PI/20;
            int x=centerX+(int)Math.round(Math.cos(angle)*11), y=centerY+(int)Math.round(Math.sin(angle)*11);
            graphics.fill(x,y,x+1,y+1,OWNED);
        }
        graphics.pose().pushPose();
        graphics.pose().translate(centerX,centerY,0);
        graphics.pose().mulPose(Axis.ZP.rotationDegrees((float)yaw));
        pointer(graphics,1,2,0x99070C10);
        for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++)pointer(graphics,x,y,0xFF172025);
        pointer(graphics,0,0,0xFFF5F2E9);
        graphics.fill(-1,-1,1,2,OWNED);
        graphics.pose().popPose();
    }
    private static void pointer(GuiGraphics g,int x,int y,int color) {
        for(int row=-7;row<=6;row++) {
            int half=(row+7)/2;
            if(row<3) g.fill(x-half,y+row,x+half+1,y+row+1,color);
            else { int gap=row-2; g.fill(x-half,y+row,x-gap,y+row+1,color); g.fill(x+gap+1,y+row,x+half+1,y+row+1,color); }
        }
    }

    public static void renderCardIcon(GuiGraphics graphics, int x, int y, ItemStack icon,
                                      boolean owned, boolean warning) {
        graphics.fill(x, y, x + 26, y + 26, BACKING);
        graphics.renderOutline(x, y, 26, 26, owned ? OWNED : FRAME);
        graphics.hLine(x + 4, x + 21, y + 1, OWNED);
        drawGlyph(graphics, glyph(icon), x + 5, y + 5, MapPalette.TEXT);
        if (owned) graphics.fill(x + 20, y + 20, x + 24, y + 24, OWNED);
        if (warning) warningBadge(graphics, x + 19, y - 2);
    }

    private static void warningBadge(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 8, y + 8, WARNING);
        graphics.fill(x + 3, y + 1, x + 5, y + 5, BACKING);
        graphics.fill(x + 3, y + 6, x + 5, y + 7, BACKING);
    }

    public static AtlasGlyph glyph(ItemStack icon) {
        String path = BuiltInRegistries.ITEM.getKey(icon.getItem()).getPath();
        if(path.endsWith("_postbox"))return AtlasGlyph.FREIGHT;
        return switch (path) {
            case "barrel" -> AtlasGlyph.HUB;
            case "city_pylon" -> AtlasGlyph.CITY;
            case "compass", "player_head" -> AtlasGlyph.PLAYER;
            case "track_station" -> AtlasGlyph.STATION;
            case "controls", "track" -> AtlasGlyph.TRAIN;
            case "mine_controller" -> AtlasGlyph.MINE;
            case "postbox", "shipment_seal", "contract_intake", "warehouse_gateway" -> AtlasGlyph.FREIGHT;
            case "redstone" -> AtlasGlyph.ENERGY;
            default -> AtlasGlyph.INDUSTRY;
        };
    }

    /** Runs, rather than single-pixel calls; preserves the exact native grid and batches with the map. */
    public static void drawGlyph(GuiGraphics graphics, AtlasGlyph glyph, int x, int y, int color) {
        for (int row = 0; row < AtlasGlyph.SIZE; row++) {
            int start = -1;
            for (int column = 0; column <= AtlasGlyph.SIZE; column++) {
                boolean pixel = glyph.pixel(column, row);
                if (pixel && start < 0) start = column;
                if (!pixel && start >= 0) {
                    graphics.fill(x + start, y + row, x + column, y + row + 1, color);
                    start = -1;
                }
            }
        }
    }
}
