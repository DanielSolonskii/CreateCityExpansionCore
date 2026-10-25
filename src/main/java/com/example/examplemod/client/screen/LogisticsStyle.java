package com.example.examplemod.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Client-only industrial palette and ordinary Minecraft widgets; no external textures. */
final class LogisticsStyle {
    static final int BG=0xFF101A21, PANEL=0xFF1B2B34, BORDER=0xFF344D59;
    static final int TEXT=0xFFE6EDF0, MUTED=0xFFA0B8C4, GOLD=0xFFE9BD76;
    static final int GREEN=0xFF83D5B3, RED=0xFFEE9A86, BLUE=0xFF85CBE7;
    static Component tr(String key, Object... args) { return FreightUi.tr("console."+key,args); }
    static String fit(Font font, Component text, int width) {
        String s=text.getString();
        return font.width(s)<=width?s:font.plainSubstrByWidth(s,Math.max(0,width-font.width("…")))+"…";
    }
    static void text(GuiGraphics g,Font f,Component t,int x,int y,int w,int color) {
        if(g!=null)g.drawString(f,fit(f,t,w),x,y,color,false);
    }
    /**
     * Raised surface in the console's own colours.
     *
     * <p>Was a flat fill with two one-pixel rules top and bottom -- no rounding, no shadow, so a
     * logistics panel read as a table cell while every other window in the mod read as a card.
     * Delegating to the shared chrome fixes that without moving a single coordinate, which matters
     * here: these screens are embedded in the Pylon workspace and their geometry is load-bearing.
     */
    static void panel(GuiGraphics g,int x,int y,int w,int h,int color) {
        if(g==null)return;
        WorldMapChrome.panel(g,x,y,w,h,BORDER,color);
    }
    static ItemStack item(String id) {
        ResourceLocation key=ResourceLocation.tryParse(id);
        return key==null?ItemStack.EMPTY:new ItemStack(BuiltInRegistries.ITEM.get(key));
    }
    static void icon(GuiGraphics g,String id,int x,int y) { if(g!=null)g.renderItem(item(id),x,y); }
    static void badge(GuiGraphics g,Font font,Component label,int x,int y,int maxWidth,int color) {
        if(g==null)return;
        int w=Math.min(maxWidth,font.width(label)+12);
        g.fill(x,y,x+w,y+15,0xFF102129);g.fill(x,y,x+2,y+15,color);
        text(g,font,label,x+7,y+4,w-11,color);
    }
    static int statusColor(String status) {
        return switch(status) {
            case "online","complete","running" -> GREEN;
            case "disabled","removed","unloaded","expired" -> MUTED;
            case "paused","requires_setup" -> GOLD;
            default -> RED;
        };
    }
    /** Fallback colour when only a unified status is available (mirrors CcecStatusUi). */
    static int statusColor(com.example.examplemod.status.CcecStatus s) {
        return switch(s.category()) {
            case SUCCESS -> GREEN;
            case ACTION_REQUIRED -> 0xFFD6A342;
            case WARNING -> 0xFFE08045;
            default -> RED;
        };
    }
    static final class Action extends Button {
        private final boolean primary;
        /** Smoothed hover, keyed by position: the button never moves, so that is its identity. */
        private final UiMotion motion = new UiMotion();
        Action(int x,int y,int w,Component label,boolean primary,Runnable action) {
            super(x,y,w,22,label,b->action.run(),DEFAULT_NARRATION);
            this.primary=primary;setTooltip(Tooltip.create(label));
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float pt) {
            float lit=motion.hover(getX()*7919+getY(),active&&isHoveredOrFocused(),
                    motion.delta(net.minecraft.Util.getMillis()));
            // Both ends of the blend are the colours this control used before it lerped, so a
            // settled button is pixel-identical to the old one and only the travel is new.
            int edge=!active?BORDER:primary?GOLD:UiMotion.mix(BORDER,BLUE,lit);
            int fill=primary?0xFF3E3529:PANEL;
            g.fill(getX(),getY(),getX()+width,getY()+height,edge);
            g.fill(getX()+1,getY()+1,getX()+width-1,getY()+height-1,
                    active?UiMotion.mix(fill,0xFF304753,lit):fill);
            Font f=Minecraft.getInstance().font;
            String s=fit(f,getMessage(),width-12);
            g.drawString(f,s,getX()+(width-f.width(s))/2,getY()+7,
                    !active?MUTED:primary?GOLD:TEXT,false);
        }
    }
}
