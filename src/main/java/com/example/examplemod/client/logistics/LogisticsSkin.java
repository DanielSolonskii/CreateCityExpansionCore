package com.example.examplemod.client.logistics;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;

/** Small native UI toolkit: no browser, heavyweight engine, textures or CityPylon dependency. */
public final class LogisticsSkin {
    public static final int BG=0xff0b1116,PANEL=0xff131e25,EDGE=0xff283b43,TEXT=0xffe3eee9,MUTED=0xff91a4a7,TEAL=0xff6bdbc0,BRASS=0xffdbba7b;
    private LogisticsSkin() {}
    public static Component tr(String key){return Component.translatable("ccec.center."+key);}
    public static String text(String key){return tr(key).getString();}
    public static String name(CompoundTag row){return CenterText.name(row,key->Component.translatable(key).getString());}
    public static String summary(CompoundTag row){return CenterText.summary(row,key->Component.translatable(key).getString());}
    public static String value(String code){return CenterText.value(code,key->Component.translatable(key).getString());}
    public static java.util.List<CenterText.Field> fields(CompoundTag row){return CenterText.fields(row,key->Component.translatable(key).getString());}
    public static String result(String value){
        if(value.equals("ok"))return text("success");
        for(String code:java.util.List.of("charter_permission","charter_busy","charter_not_ready","charter_dispatch_blocked"))if(value.contains(code))return text("error."+code);
        for(String code:java.util.List.of("source_unavailable","payer_denied","receiver_unavailable","quantity_invalid","offer_changed","offer_denied","source_denied","stock_unavailable","route_unavailable","quote_expired","quote_changed"))if(value.contains("delivery_"+code))return text("delivery_error."+code);

        for(String code:java.util.List.of("rate_limit","no_route","shipment_not_cancellable","shipment_refund_unavailable","shipment_cancel_denied","shipment_cancel_pending","schedule_permission","schedule_station_missing","schedule_disconnected","schedule_station_name","schedule_train_missing","schedule_conductor","schedule_existing","schedule_train_moving","schedule_conditions"))if(value.contains(code))return text("error."+code);
        if(value.contains("Insufficient CR"))return text("error.funds");if(value.contains("Quote"))return text("error.quote");
        if(value.contains("active_shipment")||value.contains("shipment_in_use"))return text("error.active_shipment");return text("error.denied");
    }
    public static void surface(GuiGraphics g,int x,int y,int w,int h,int color){if(w<5||h<5)return;g.fill(x+2,y+3,x+w+2,y+h+3,0x33000000);g.fill(x+2,y,x+w-2,y+h,color);g.fill(x,y+2,x+w,y+h-2,color);g.hLine(x+3,x+w-4,y,EDGE);}
    public static String fit(String text,int width){if(width<8)return "";var font=Minecraft.getInstance().font;return font.width(text)>width?font.plainSubstrByWidth(text,width-8)+"…":text;}
    public static int blend(int from,int to,float amount){float t=Math.max(0,Math.min(1,amount));int color=0;for(int shift:new int[]{24,16,8,0})color|=((int)(((from>>>shift)&255)*(1-t)+((to>>>shift)&255)*t))<<shift;return color;}
    /** Small semantic line glyphs, crisp at Minecraft GUI scale and independent of font symbols. */
    public static void icon(GuiGraphics g,int x,int y,int index,int color){
        switch(Math.floorMod(index,16)){
            case 0->{for(int row=0;row<2;row++)for(int col=0;col<2;col++)g.fill(x+col*7,y+row*7,x+col*7+5,y+row*7+5,color);}
            case 1->{g.vLine(x,y,y+11,color);g.vLine(x+5,y+2,y+13,color);g.vLine(x+11,y,y+11,color);g.hLine(x,x+4,y,color);g.hLine(x+6,x+10,y+12,color);g.hLine(x,x+4,y+10,color);g.hLine(x+6,x+10,y,color);}
            case 2->{g.fill(x+2,y,x+11,y+10,color);g.fill(x+3,y+2,x+10,y+5,PANEL);g.fill(x+3,y+7,x+5,y+8,PANEL);g.fill(x+8,y+7,x+10,y+8,PANEL);g.hLine(x+1,x+11,y+12,color);g.fill(x+2,y+10,x+4,y+12,color);g.fill(x+9,y+10,x+11,y+12,color);}
            case 3->{g.fill(x,y+1,x+4,y+5,color);g.fill(x+8,y+8,x+12,y+12,color);g.hLine(x+3,x+9,y+3,color);g.vLine(x+9,y+3,y+8,color);}
            case 4->{g.hLine(x,x+12,y+3,color);g.hLine(x+2,x+10,y,color);g.vLine(x+2,y+4,y+12,color);g.vLine(x+10,y+4,y+12,color);g.hLine(x,x+12,y+12,color);g.fill(x+5,y+7,x+8,y+12,color);}
            case 5->{g.hLine(x+1,x+11,y+3,color);g.hLine(x+3,x+9,y+1,color);g.vLine(x+1,y+3,y+12,color);g.vLine(x+11,y+3,y+12,color);g.hLine(x+1,x+11,y+12,color);g.fill(x+4,y+6,x+9,y+11,color);}
            case 6->{g.hLine(x,x+11,y+3,color);g.fill(x+8,y+1,x+10,y+6,color);g.hLine(x+1,x+12,y+10,color);g.fill(x+2,y+8,x+4,y+13,color);}
            case 7->{g.hLine(x+1,x+11,y+1,color);g.hLine(x+1,x+11,y+12,color);g.vLine(x+1,y+1,y+12,color);g.vLine(x+11,y+1,y+12,color);g.hLine(x+1,x+11,y+4,color);g.vLine(x+6,y+4,y+8,color);}
            case 8->{g.fill(x+1,y+5,x+12,y+13,color);g.hLine(x+4,x+9,y,color);g.vLine(x+3,y+1,y+5,color);g.vLine(x+10,y+1,y+5,color);g.fill(x+6,y+8,x+8,y+11,PANEL);}
            case 9->{g.hLine(x+1,x+11,y+2,color);g.hLine(x+4,x+8,y,color);g.vLine(x+2,y+3,y+12,color);g.vLine(x+10,y+3,y+12,color);g.hLine(x+2,x+10,y+12,color);g.vLine(x+5,y+5,y+10,color);g.vLine(x+7,y+5,y+10,color);}
            case 10->{g.hLine(x,x+4,y+2,color);g.hLine(x,x+4,y+8,color);g.vLine(x,y+2,y+8,color);g.hLine(x+8,x+12,y+5,color);g.hLine(x+8,x+12,y+11,color);g.vLine(x+12,y+5,y+11,color);g.fill(x+4,y+5,x+6,y+7,color);g.fill(x+7,y+7,x+9,y+9,color);g.fill(x+6,y,x+8,y+3,color);g.fill(x+4,y+10,x+6,y+13,color);}
            case 11->{g.hLine(x+3,x+9,y,color);g.hLine(x+3,x+9,y+12,color);g.vLine(x,y+3,y+9,color);g.vLine(x+12,y+3,y+9,color);g.fill(x+1,y+1,x+3,y+3,color);g.fill(x+10,y+1,x+12,y+3,color);g.fill(x+1,y+10,x+3,y+12,color);g.fill(x+10,y+10,x+12,y+12,color);g.vLine(x+6,y+3,y+7,color);g.hLine(x+6,x+9,y+7,color);}
            case 12->{g.hLine(x,x+12,y+2,color);g.hLine(x,x+12,y+6,color);g.hLine(x,x+12,y+10,color);g.fill(x+3,y,x+5,y+5,color);g.fill(x+8,y+4,x+10,y+9,color);g.fill(x+4,y+8,x+6,y+13,color);}
            case 13->{g.fill(x+5,y,x+8,y+3,color);g.fill(x+5,y+5,x+8,y+13,color);}
            case 14->{g.hLine(x+1,x+12,y+3,color);g.fill(x+8,y,x+11,y+3,color);g.vLine(x+11,y+4,y+9,color);g.hLine(x,x+10,y+10,color);g.fill(x+1,y+10,x+4,y+13,color);g.vLine(x+1,y+4,y+9,color);}
            case 15->{g.hLine(x+1,x+12,y+4,color);g.hLine(x+1,x+12,y+9,color);g.fill(x+8,y+1,x+10,y+4,color);g.fill(x+3,y+9,x+5,y+12,color);}
        }
    }
    public static final class ActionButton extends AbstractWidget {
        private final Runnable action;
        private final int symbol;
        private float hover;private long lastFrame,entered;private boolean engaged;
        public ActionButton(int x,int y,int w,int h,Component label,Runnable action){this(x,y,w,h,label,action,-1);}
        public ActionButton(int x,int y,int w,int h,Component label,Runnable action,int symbol){super(x,y,w,h,label);this.action=action;this.symbol=symbol;}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
            long now=net.minecraft.Util.getMillis();boolean over=active&&isHoveredOrFocused();if(over&&!engaged)entered=now;engaged=over;float step=Math.min(1,(now-lastFrame)/110f);lastFrame=now;hover+=(active&&isHoveredOrFocused()?1-hover:-hover)*step;
            surface(g,getX(),getY(),width,height,!active?0xff152027:blend(0xff1b3033,0xff28564c,hover));if(isFocused())g.hLine(getX()+4,getX()+width-5,getY()+height-2,TEAL);
            var font=Minecraft.getInstance().font;int color=!active?MUTED:TEXT;
            if(symbol<0)g.drawCenteredString(font,fit(getMessage().getString(),width-12),getX()+width/2,getY()+(height-8)/2,color);
            else{icon(g,getX()+10,getY()+(height-13)/2,symbol,!active?MUTED:symbol==9||symbol==10?BRASS:TEAL);if(engaged&&now-entered<480){int frame=(int)((now-entered)/80);int dx=frame<3?frame:5-frame;g.fill(getX()+11+dx*3,getY()+(height-13)/2+14,getX()+13+dx*3,getY()+(height-13)/2+15,TEAL);}var lines=font.split(getMessage(),Math.max(16,width-44));int count=Math.min(2,lines.size());for(int i=0;i<count;i++)g.drawString(font,lines.get(i),getX()+31,getY()+(height-count*font.lineHeight)/2+i*font.lineHeight,color,false);}
        }
        @Override public void onClick(double x,double y){action.run();}
        @Override public boolean keyPressed(int key,int scan,int mods){if(active&&isFocused()&&(key==257||key==32)){action.run();return true;}return super.keyPressed(key,scan,mods);}
        @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
}
