package com.example.examplemod.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * Shared opaque surfaces: terrain never competes with text through translucent panels.
 *
 * <p>Public because the surfaces outgrew their origin. They started as the world map's chrome, but
 * every window in the mod now builds on the same rounded corner, drop shadow and lit top edge, and
 * the status widgets live in other packages. A private copy in each of them would drift apart within
 * a few edits and the windows would stop reading as one family.
 */
public final class WorldMapChrome {
    public static final int SURFACE=0xFF1D2529, RAISED=0xFF263137, EDGE=0xFF435157;
    public static final int TEXT=0xFFE8E9E1, MUTED=0xFFAAB7B8, ACCENT=0xFFD3A363;
    public static final int PRIMARY=0xFF996215, INSET=0xFF172025, RADIUS=6;
    private WorldMapChrome() {}
    public static void rounded(GuiGraphics g,int x,int y,int w,int h,int r,int color) {
        if(w<=0||h<=0)return;
        r=Math.min(r,Math.min(w,h)/2);
        g.fill(x+r,y,x+w-r,y+h,color);g.fill(x,y+r,x+w,y+h-r,color);
        for(int row=0;row<r;row++) {
            int inset=(int)Math.ceil(r-Math.sqrt(r*r-(r-row-.5)*(r-row-.5)));
            g.fill(x+inset,y+row,x+w-inset,y+row+1,color);
            g.fill(x+inset,y+h-row-1,x+w-inset,y+h-row,color);
        }
    }
    public static void panel(GuiGraphics g,int x,int y,int w,int h) {
        panel(g,x,y,w,h,EDGE,SURFACE);
    }
    /**
     * The same three-layer surface with the caller's own palette.
     *
     * <p>CityPylonTheme and LogisticsStyle keep their approved colours, but every window gets the
     * drop shadow and the lit top edge that make the profile read as a raised object rather than a
     * flat rectangle. Depth was the one thing the older themes dropped and the one that showed.
     */
    public static void panel(GuiGraphics g,int x,int y,int w,int h,int edge,int fill) {
        if(w<=0||h<=0)return;
        rounded(g,x,y+3,w,h,RADIUS,0x50000000);
        rounded(g,x,y,w,h,RADIUS,edge);
        rounded(g,x+1,y+1,w-2,h-2,RADIUS-1,fill);
        g.hLine(x+RADIUS,x+w-RADIUS-1,y+1,0xFF4C595D);
    }
    public static void control(GuiGraphics g,WorldMapUiLayout.Rect r,boolean active,boolean hover,boolean enabled,boolean primary) {
        control(g,r,active,hover?1f:0f,enabled,primary);
    }
    /**
     * The same control driven by a hover lerp rather than a switch.
     *
     * <p>The boolean form remains for callers with no animation clock; screens that own a
     * {@link UiMotion} pass the smoothed value so the outline grows under the cursor instead of
     * snapping to it.
     */
    public static void control(GuiGraphics g,WorldMapUiLayout.Rect r,boolean active,float lit,boolean enabled,boolean primary) {
        int edge=!enabled?0xFF303C41:active||primary?PRIMARY:UiMotion.mix(EDGE,0xFF69797F,lit);
        rounded(g,r.x(),r.y(),r.width(),r.height(),4,edge);
        rounded(g,r.x()+1,r.y()+1,r.width()-2,r.height()-2,3,
                !enabled?INSET:primary?UiMotion.mix(PRIMARY,0xFFB07825,lit):active?0xFF3C3529:UiMotion.mix(INSET,RAISED,lit));
    }
    public static String fit(Font font,Component text,int width) {
        return font.width(text)<=width?text.getString():font.plainSubstrByWidth(text.getString(),Math.max(0,width-font.width("…")))+"…";
    }
    /** Compact view tools share the same 16px pixel language as mode glyphs. */
    public static void viewIcon(GuiGraphics g,int index,int x,int y,int color) {
        switch(index) {
            case 0 -> com.example.examplemod.client.map.MapMarkerRenderer.drawGlyph(g,com.example.examplemod.client.map.AtlasGlyph.PLAYER,x,y,color);
            case 1 -> {
                g.hLine(x+1,x+5,y+1,color);g.vLine(x+1,y+1,y+5,color);
                g.hLine(x+10,x+14,y+1,color);g.vLine(x+14,y+1,y+5,color);
                g.hLine(x+1,x+5,y+14,color);g.vLine(x+1,y+10,y+14,color);
                g.hLine(x+10,x+14,y+14,color);g.vLine(x+14,y+10,y+14,color);
                g.renderOutline(x+6,y+6,4,4,color);
            }
            case 2 -> com.example.examplemod.client.map.MapMarkerRenderer.drawGlyph(g,com.example.examplemod.client.map.AtlasGlyph.TRAIN,x,y,color);
            case 3 -> {g.renderOutline(x+4,y+2,8,7,color);g.fill(x+6,y+4,x+10,y+7,color);g.fill(x+7,y+9,x+9,y+14,color);}
            case 4 -> {g.renderOutline(x+1,y+5,14,7,color);for(int i=3;i<14;i+=3)g.vLine(x+i,y+5,y+8,color);}
            default -> throw new IllegalArgumentException("Unknown view tool: "+index);
        }
    }
}
