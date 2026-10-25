package com.example.examplemod.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The graphite/brass palette and six-pixel surfaces from the approved mockup. */
public final class CityPylonTheme {
    public static final int BG=0xFF141A1D, METAL=0xFF1D2529, PANEL=0xFF222C30, DEEP=0xFF192125;
    public static final int LINE=0xFF3B474B, TEXT=0xFFE6E8DF, MUTED=0xFFA6B1B2;
    public static final int BRASS=0xFF996215, BRASS_TEXT=0xFFD1A85B, GREEN=0xFF9CBEA6;
    public static final int GAP=13, RADIUS=6;
    private CityPylonTheme() {}

    public static void rounded(GuiGraphics g,int x,int y,int w,int h,int radius,int color) {
        if(w<=0||h<=0)return;
        int r=Math.min(radius,Math.min(w,h)/2);
        g.fill(x+r,y,x+w-r,y+h,color);
        g.fill(x,y+r,x+w,y+h-r,color);
        for(int row=0;row<r;row++) {
            int inset=(int)Math.ceil(r-Math.sqrt(r*r-(r-row-0.5)*(r-row-0.5)));
            g.fill(x+inset,y+row,x+w-inset,y+row+1,color);
            g.fill(x+inset,y+h-row-1,x+w-inset,y+h-row,color);
        }
    }
    /**
     * Raised surface in the theme's own colours.
     *
     * <p>Delegates to the shared chrome so this window carries the same drop shadow and lit top
     * edge as the profile. It used to be a flat two-layer fill, which is the difference between a
     * panel that looks placed on the screen and one that looks painted into it.
     */
    public static void panel(GuiGraphics g,int x,int y,int w,int h) {
        WorldMapChrome.panel(g,x,y,w,h,LINE,PANEL);
    }
    public static Component fit(Font font,Component text,int width) {
        return font.width(text)<=width?text:Component.literal(font.plainSubstrByWidth(text.getString(),Math.max(1,width-6))+"…");
    }
    public static void button(GuiGraphics g,Font font,int x,int y,int w,int h,Component text,boolean primary,boolean hover) {
        button(g,font,x,y,w,h,text,primary,hover?1f:0f);
    }
    /**
     * The same button driven by a hover lerp rather than a switch.
     *
     * <p>The boolean form stays for callers that have no animation clock; screens that own a
     * {@link UiMotion} pass the smoothed value so the highlight grows under the cursor instead of
     * snapping to it.
     */
    public static void button(GuiGraphics g,Font font,int x,int y,int w,int h,Component text,boolean primary,float lit) {
        rounded(g,x,y,w,h,4,primary?BRASS:UiMotion.mix(LINE,0xFF3C484D,lit));
        if(!primary)rounded(g,x+1,y+1,w-2,h-2,3,UiMotion.mix(DEEP,0xFF303B40,lit));
        g.drawCenteredString(font,fit(font,text,w-14),x+w/2,y+(h-8)/2,TEXT);
    }
}
