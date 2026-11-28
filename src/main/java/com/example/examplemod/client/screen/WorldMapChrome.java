package com.example.examplemod.client.screen;

import com.example.examplemod.client.map.DispatchMotion;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Shared opaque surfaces: terrain never competes with text through translucent panels. */
final class WorldMapChrome {
    static final int SURFACE=0xFF1D2529, RAISED=0xFF263137, EDGE=0xFF435157;
    static final int TEXT=0xFFE8E9E1, MUTED=0xFFAAB7B8, ACCENT=0xFFD3A363;
    static final int PRIMARY=0xFF996215, INSET=0xFF172025, RADIUS=6;
    static final int POSITIVE=0xFF8FBF6A, WARNING=0xFFD2705F, PRIMARY_TEXT=0xFFFFF6E5;
    private static final int HOVER_EDGE=0xFF69797F, SELECTED_FILL=0xFF3C3529;
    private static final int PRIMARY_HOVER=0xFFB07825, ATLAS_PRIMARY_HOVER=0xFF9D6519, DISABLED_EDGE=0xFF303C41;
    private static final int CONTROL_RADIUS=4, PANEL_SHADOW=0x50000000, MODAL_SHADOW=0x68000000;
    static final int MODAL_SCRIM=0x88101517;
    private WorldMapChrome() {}

    /** Opaque atlas panels share the same graphite surfaces as Community. */
    static void dispatchPanel(GuiGraphics g,int x,int y,int w,int h) {
        atlasPanel(g,x,y,w,h,SURFACE,3,PANEL_SHADOW);
    }

    static void popover(GuiGraphics g,int x,int y,int w,int h) {
        atlasPanel(g,x,y,w,h,RAISED,4,PANEL_SHADOW);
    }

    static void modal(GuiGraphics g,int x,int y,int w,int h) {
        atlasPanel(g,x,y,w,h,RAISED,5,MODAL_SHADOW);
    }

    private static void atlasPanel(GuiGraphics g,int x,int y,int w,int h,int color,int lift,int shadow) {
        rounded(g,x,y+lift,w,h,RADIUS,shadow);
        rounded(g,x,y,w,h,RADIUS,EDGE);
        rounded(g,x+1,y+1,w-2,h-2,RADIUS-1,color);
    }

    static void atlasControl(GuiGraphics g,WorldMapUiLayout.Rect r,boolean enabled,boolean primary,
                             boolean danger,boolean quiet,ControlMotion motion,boolean hover,boolean active) {
        double h = motion == null ? (hover ? 1 : 0) : motion.hovered;
        double s = motion == null ? (active ? 1 : 0) : motion.selected;
        double p = motion == null ? 0 : motion.pressed;
        int edge = !enabled ? DispatchMotion.tint(DISABLED_EDGE, PRIMARY, s * .70) : primary ? ACCENT : DispatchMotion.tint(
                DispatchMotion.tint(EDGE, HOVER_EDGE, h), PRIMARY, s);
        int fill = !enabled ? DispatchMotion.tint(INSET, SELECTED_FILL, s * .55) : primary ? DispatchMotion.tint(PRIMARY, ATLAS_PRIMARY_HOVER, h) :
                DispatchMotion.tint(DispatchMotion.tint(quiet ? RAISED : INSET, HOVER_EDGE, h * .24), SELECTED_FILL, s);
        if (enabled) {
            if (danger) edge = DispatchMotion.tint(edge, WARNING, Math.max(h, .45));
            fill = DispatchMotion.tint(fill, INSET, p * .50);
        }
        if (quiet) {
            if (fill != RAISED) rounded(g,r.x(),r.y(),r.width(),r.height(),CONTROL_RADIUS,fill);
        } else {
            rounded(g,r.x(),r.y(),r.width(),r.height(),CONTROL_RADIUS,edge);
            rounded(g,r.x()+1,r.y()+1,r.width()-2,r.height()-2,CONTROL_RADIUS-1,fill);
        }
    }

    static void field(GuiGraphics g,int x,int y,int w,int h,boolean focused,boolean hovered) {
        rounded(g,x,y,w,h,3,focused ? ACCENT : hovered ? HOVER_EDGE : EDGE);
        rounded(g,x+1,y+1,w-2,h-2,2,INSET);
    }

    static void toggleMark(GuiGraphics g,int x,int y,boolean selected,int accent) {
        g.renderOutline(x,y,7,7,selected?accent:MUTED);
        if (selected) g.fill(x+2,y+2,x+5,y+5,accent);
    }
    static void rounded(GuiGraphics g,int x,int y,int w,int h,int r,int color) {
        if(w<=0||h<=0)return;
        r=Math.min(r,Math.min(w,h)/2);
        g.fill(x+r,y,x+w-r,y+h,color);g.fill(x,y+r,x+w,y+h-r,color);
        for(int row=0;row<r;row++) {
            int inset=(int)Math.ceil(r-Math.sqrt(r*r-(r-row-.5)*(r-row-.5)));
            g.fill(x+inset,y+row,x+w-inset,y+row+1,color);
            g.fill(x+inset,y+h-row-1,x+w-inset,y+h-row,color);
        }
    }
    static void panel(GuiGraphics g,int x,int y,int w,int h) {
        if(w<=0||h<=0)return;
        rounded(g,x,y+3,w,h,RADIUS,0x50000000);
        rounded(g,x,y,w,h,RADIUS,EDGE);
        rounded(g,x+1,y+1,w-2,h-2,RADIUS-1,SURFACE);
        g.hLine(x+RADIUS,x+w-RADIUS-1,y+1,0xFF4C595D);
    }
    static void control(GuiGraphics g,WorldMapUiLayout.Rect r,boolean active,boolean hover,boolean enabled,boolean primary) {
        int edge=!enabled?DISABLED_EDGE:active||primary?PRIMARY:hover?HOVER_EDGE:EDGE;
        rounded(g,r.x(),r.y(),r.width(),r.height(),4,edge);
        rounded(g,r.x()+1,r.y()+1,r.width()-2,r.height()-2,3,
                !enabled?INSET:primary?(hover?PRIMARY_HOVER:PRIMARY):active?SELECTED_FILL:hover?RAISED:INSET);
    }
    static String fit(Font font,Component text,int width) {
        return font.width(text)<=width?text.getString():font.plainSubstrByWidth(text.getString(),Math.max(0,width-font.width("…")))+"…";
    }
    /** Compact view tools share the same 16px pixel language as mode glyphs. */
    static void viewIcon(GuiGraphics g,int index,int x,int y,int color) {
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
            case 13 -> {g.renderOutline(x+1,y+2,5,5,color);g.renderOutline(x+10,y+9,5,5,color);g.hLine(x+5,x+12,y+4,color);g.vLine(x+12,y+5,y+8,color);}
            case 14 -> {g.renderOutline(x+1,y+2,10,9,color);g.vLine(x+6,y+11,y+14,color);g.hLine(x+9,x+15,y+12,color);g.vLine(x+12,y+9,y+15,color);}
            case 15 -> {g.hLine(x+2,x+12,y+4,color);g.vLine(x+12,y+4,y+12,color);g.hLine(x+5,x+12,y+12,color);g.hLine(x+2,x+4,y+5,color);}
            default -> throw new IllegalArgumentException("Unknown view tool: "+index);
        }
    }
}
