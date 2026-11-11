package com.example.examplemod.client.journey;

import com.example.examplemod.journey.JourneyModel.Marker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Small vector artwork shared by the map, preview and world signs. No font-dependent icons. */
public final class JourneyAtlasStyle {
    public static final int SURFACE=0xf5182026, RAISED=0xff222d33, EDGE=0xff3b4548,
            BRASS=0xffd0ad73, TEXT=0xffeee9dd, MUTED=0xffa4b0b2;
    private static final int[][][] GLYPHS={
            {{0,-5,3,3},{3,3,0,1},{0,1,-3,3},{-3,3,0,-5}}, // compass
            {{0,-5,5,0},{5,0,0,5},{0,5,-5,0},{-5,0,0,-5},{0,-2,0,1},{0,3,0,3}},
            {{-4,-4,4,4},{-4,4,4,-4},{-5,-3,-3,-5},{3,5,5,3}},
            {{-4,-1,0,-5},{0,-5,4,-1},{-3,-1,-3,4},{3,-1,3,4},{-3,4,3,4}},
            {{-4,-3,4,-3},{4,-3,4,4},{4,4,-4,4},{-4,4,-4,-3},{-4,-3,0,0},{0,0,4,-3},{0,0,0,4}},
            {{-5,-4,0,-3},{0,-3,5,-4},{5,-4,5,4},{5,4,0,3},{0,3,-5,4},{-5,4,-5,-4},{0,-3,0,3}},
            {{-4,-4,4,-4},{4,-4,4,2},{4,2,-4,2},{-4,2,-4,-4},{-2,2,-4,5},{2,2,4,5},{-2,-1,-1,-1},{1,-1,2,-1}},
            {{-4,0,-1,3},{-1,3,5,-4}}
    };
    public static int[][] glyph(int icon){return GLYPHS[Math.clamp(icon,0,GLYPHS.length-1)];}
    public static int color(Marker m){return m.tint==0?JourneyClient.color(m.kind):0xff000000|(m.tint&0xffffff);}
    public static double breathe(long now,boolean reduced){return reduced?1:.86+.14*Math.sin(now/1150.0);}
    public static double reveal(long elapsed,boolean reduced){return reduced?1:1-Math.pow(1-Math.clamp(elapsed/360.0,0,1),3);}
    public static void line(GuiGraphics g,int x1,int y1,int x2,int y2,int color){
        int dx=Math.abs(x2-x1),sx=x1<x2?1:-1,dy=-Math.abs(y2-y1),sy=y1<y2?1:-1,error=dx+dy;
        // Caller clips world geometry before this rasterizer. Glyphs are always tiny.
        for(int budget=0;budget<4096;budget++){g.fill(x1,y1,x1+1,y1+1,color);if(x1==x2&&y1==y2)break;int e=error*2;if(e>=dy){error+=dy;x1+=sx;}if(e<=dx){error+=dx;y1+=sy;}}
    }
    public static void diamond(GuiGraphics g,int x,int y,int r,int color){for(int row=-r;row<=r;row++){int half=r-Math.abs(row);g.fill(x-half,y+row,x+half+1,y+row+1,color);}}
    public static void mark(GuiGraphics g,int x,int y,Marker m,boolean active,boolean hover,long now,boolean reduced,boolean done){
        int r=m.size,ink=done?0xff91b9a4:color(m);
        if(active){int a=(int)(32*breathe(now,reduced));diamond(g,x,y,r+5,(a<<24)|(BRASS&0xffffff));diamond(g,x,y,r+2,BRASS);}
        diamond(g,x+1,y+2,r+1,0x60000000);diamond(g,x,y,r,hover?TEXT:active?BRASS:EDGE);diamond(g,x,y,r-1,SURFACE);
        for(var s:glyph(done?7:m.icon))line(g,x+s[0],y+s[1],x+s[2],y+s[3],ink);
        if(active)g.fill(x-1,y-r-4,x+2,y-r-2,BRASS);
    }
    public static void panel(GuiGraphics g,int x,int y,int w,int h){g.fill(x+2,y+3,x+w+2,y+h+3,0x35000000);g.fill(x,y,x+w,y+h,SURFACE);g.renderOutline(x,y,w,h,EDGE);}
    public static Button button(int x,int y,int w,String key,Runnable action){return new FlatButton(x,y,w,20,JourneyClient.tr(key),action);}
    public static FlatBuilder builder(Component title,Button.OnPress press){return new FlatBuilder(title,press);}
    public static final class FlatBuilder {
        private final Component title;private final Button.OnPress press;private int x,y,w,h;
        private FlatBuilder(Component title,Button.OnPress press){this.title=title;this.press=press;}
        public FlatBuilder bounds(int x,int y,int w,int h){this.x=x;this.y=y;this.w=w;this.h=h;return this;}
        public Button build(){return new FlatButton(x,y,w,h,title,press);}
    }
    public static final class FlatButton extends Button {
        private double glow;private long frame;
        public FlatButton(int x,int y,int w,int h,Component title,Runnable action){super(x,y,w,h,title,b->action.run(),DEFAULT_NARRATION);}
        public FlatButton(int x,int y,int w,int h,Component title,Button.OnPress action){super(x,y,w,h,title,action,DEFAULT_NARRATION);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){
            boolean hover=isHoveredOrFocused();long now=net.minecraft.Util.getMillis();double dt=frame==0?0:Math.clamp((now-frame)/1000d,0,.1);frame=now;double goal=hover&&active?1:0;glow=JourneyClient.reduced()?goal:glow+(goal-glow)*(1-Math.exp(-dt*18));g.fill(getX(),getY(),getX()+width,getY()+height,mix(RAISED,0xff344047,glow));
            g.renderOutline(getX(),getY(),width,height,mix(EDGE,BRASS,glow));
            var font=Minecraft.getInstance().font;g.drawCenteredString(font,font.plainSubstrByWidth(getMessage().getString(),width-10),getX()+width/2,getY()+(height-8)/2,active?TEXT:MUTED);
        }
    }
    private static int mix(int a,int b,double t){int r=(int)(((a>>16)&255)*(1-t)+((b>>16)&255)*t),g=(int)(((a>>8)&255)*(1-t)+((b>>8)&255)*t),blue=(int)((a&255)*(1-t)+(b&255)*t);return 0xff000000|(r<<16)|(g<<8)|blue;}
    private JourneyAtlasStyle(){}
}
