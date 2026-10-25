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
    static final int BG=0xFF141A1D, PANEL=0xFF1D2529, BORDER=0xFF435157;
    static final int TEXT=0xFFE8E9E1, MUTED=0xFFAAB7B8, GOLD=0xFFD3A363;
    static final int GREEN=0xFF83D5B3, RED=0xFFEE9A86, BLUE=0xFF85CBE7;
    static Component tr(String key, Object... args) { return FreightUi.tr("console."+key,args); }
    static String fit(Font font, Component text, int width) {
        String s=text.getString();
        return font.width(s)<=width?s:font.plainSubstrByWidth(s,Math.max(0,width-font.width("…")))+"…";
    }
    static void text(GuiGraphics g,Font f,Component t,int x,int y,int w,int color) {
        if(g!=null)g.drawString(f,fit(f,t,w),x,y,color,false);
    }
    static void panel(GuiGraphics g,int x,int y,int w,int h,int color) {
        if(g==null)return;
        g.fill(x,y,x+w,y+h,color);g.fill(x,y,x+w,y+1,BORDER);
        g.fill(x,y+h-1,x+w,y+h,BORDER);
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
        private float hoverFade;private long lastRender;
        Action(int x,int y,int w,Component label,boolean primary,Runnable action) {
            super(x,y,w,22,label,b->action.run(),DEFAULT_NARRATION);
            this.primary=primary;setTooltip(Tooltip.create(label));
        }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float pt) {
            long now=net.minecraft.Util.getMillis();float dt=Math.min(50,Math.max(0,now-lastRender));lastRender=now;
            hoverFade+=(float)(1-Math.exp(-dt/90))*(active&&isHoveredOrFocused()?1-hoverFade:-hoverFade);
            int edge=!active?BORDER:primary||isHoveredOrFocused()?GOLD:BORDER;
            WorldMapChrome.rounded(g,getX(),getY(),width,height,3,edge);
            int base=primary?0xFF3E3529:PANEL,target=primary?0xFF665032:0xFF303B40;
            int color=0xff000000;for(int shift:new int[]{0,8,16})color|=(int)(((base>>shift)&255)*(1-hoverFade)+((target>>shift)&255)*hoverFade)<<shift;
            WorldMapChrome.rounded(g,getX()+1,getY()+1,width-2,height-2,2,color);
            Font f=Minecraft.getInstance().font;
            String s=fit(f,getMessage(),width-12);
            g.drawString(f,s,getX()+(width-f.width(s))/2,getY()+7,
                    !active?MUTED:primary?GOLD:TEXT,false);
        }
    }
}
