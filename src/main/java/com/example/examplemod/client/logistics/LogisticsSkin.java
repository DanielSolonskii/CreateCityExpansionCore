package com.example.examplemod.client.logistics;

import com.example.examplemod.client.CommunityFx;
import com.mojang.blaze3d.platform.InputConstants;
import java.time.Duration;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.FormattedCharSequence;

/** Native dispatch surfaces and controls. All visual motion honors the local presentation preference. */
public final class LogisticsSkin {
    public static final int BG=0xff0a121d,PANEL=0xff152130,EDGE=0xff2b3e50,TEXT=0xffedf6f4,MUTED=0xff9caebb,TEAL=0xff75e3c3,BRASS=0xffe5bf80;
    private LogisticsSkin() {}
    public static Component tr(String key){return Component.translatable("ccec.center."+key);}
    public static String text(String key){return tr(key).getString();}
    public static String name(CompoundTag row){return CenterText.name(row,key->Component.translatable(key).getString());}
    public static String summary(CompoundTag row){return CenterText.summary(row,key->Component.translatable(key).getString());}
    public static String value(String code){return CenterText.value(code,key->Component.translatable(key).getString());}
    public static java.util.List<CenterText.Field> fields(CompoundTag row){return CenterText.fields(row,key->Component.translatable(key).getString());}
    public static String result(String value){
        if(value.equals("ok"))return text("success");
        var codeMatcher=java.util.regex.Pattern.compile("(?:company|master|trade|rail|fleet)_[a-z_]+").matcher(value);
        if(codeMatcher.find()){
            String code=codeMatcher.group();for(String prefix:java.util.List.of("ccec.company.error.","ccec.center.error.","ccec.master.error.","ccec.rail.error.")){
                String key=prefix+code;if(net.minecraft.client.resources.language.I18n.exists(key))return Component.translatable(key).getString();
            }
        }
        for(String code:java.util.List.of("setup_permission","setup_expired","setup_selection_changed","setup_postbox_invalid","setup_receiver_invalid"))if(value.equals("denied: "+code)||value.equals(code))return text("error."+code);
        for(String code:java.util.List.of("charter_permission","charter_busy","charter_not_ready","charter_dispatch_blocked"))if(value.contains(code))return text("error."+code);
        for(String code:java.util.List.of("source_unavailable","payer_denied","receiver_unavailable","quantity_invalid","offer_changed","offer_denied","source_denied","stock_unavailable","route_unavailable","quote_expired","quote_changed"))if(value.contains("delivery_"+code))return text("delivery_error."+code);

        for(String code:java.util.List.of("rate_limit","no_route","shipment_not_cancellable","shipment_refund_unavailable","shipment_cancel_denied","shipment_cancel_pending","schedule_permission","schedule_station_missing","schedule_disconnected","schedule_station_name","schedule_train_missing","schedule_conductor","schedule_existing","schedule_train_moving","schedule_conditions"))if(value.contains(code))return text("error."+code);
        if(value.contains("Insufficient CR"))return text("error.funds");if(value.contains("Quote"))return text("error.quote");
        if(value.contains("active_shipment")||value.contains("shipment_in_use"))return text("error.active_shipment");return text("error.denied");
    }
    public static boolean reducedMotion(){return CommunityFx.reduced||LogisticsTutorial.reducedMotion();}
    /** Custom choice controls share this with buttons so a held Enter cannot confirm the next step. */
    public static boolean claimActivationKey(int key){return ActionButton.ACTIVATION.press(key);}

    /** A quiet drafting grid gives the workspace depth without animated full-screen effects. */
    public static void background(GuiGraphics g,int width,int height){
        g.fillGradient(0,0,width,height,BG,0xff111f2e);
        g.fillGradient(0,0,width,Math.min(height,160),0x161d756c,0x001d756c);
        for(int x=24;x<width;x+=48)g.vLine(x,0,height,0x152f5261);
        for(int y=24;y<height;y+=48)g.hLine(0,width,y,0x152f5261);
        for(int x=24;x<width;x+=144)for(int y=24;y<height;y+=144)g.fill(x,y,x+1,y+1,0x3853818a);
        g.hLine(0,width,0,0xff2b4c56);
    }

    /** Shared 44-pixel header. Text stays at native GUI size even in compact windows. */
    public static void header(GuiGraphics g,int x,int y,int w,Component title,Component subtitle,int symbol){
        surface(g,x,y,w,44,PANEL);
        surface(g,x+8,y+8,28,28,0xff1c3540);
        icon(g,x+16,y+16,symbol,TEAL);
        var font=Minecraft.getInstance().font;
        g.drawString(font,fit(title.getString(),w-56),x+46,y+10,TEXT,false);
        g.drawString(font,fit(subtitle.getString(),w-56),x+46,y+26,MUTED,false);
    }

    public static void surface(GuiGraphics g,int x,int y,int w,int h,int color){
        if(w<5||h<5)return;
        g.fill(x+2,y+h,x+w-2,y+h+2,0x28000000);
        g.fill(x+2,y,x+w-2,y+h,EDGE);
        g.fill(x,y+2,x+w,y+h-2,EDGE);
        g.fill(x+2,y+1,x+w-2,y+h-1,color);
        g.fill(x+1,y+2,x+w-1,y+h-2,color);
        g.hLine(x+3,x+w-4,y+1,blend(color,0xffb1d4da,.055f));
    }

    public static String fit(String text,int width){
        if(width<=0||text.isEmpty())return "";
        var font=Minecraft.getInstance().font;
        if(font.width(text)<=width)return text;
        int ellipsis=font.width("…");
        return width<ellipsis?font.plainSubstrByWidth(text,width):font.plainSubstrByWidth(text,width-ellipsis)+"…";
    }

    public static int blend(int from,int to,float amount){
        float t=Float.isFinite(amount)?Math.max(0,Math.min(1,amount)):0;
        int color=0;
        for(int shift=24;shift>=0;shift-=8)color|=((int)(((from>>>shift)&255)*(1-t)+((to>>>shift)&255)*t))<<shift;
        return color;
    }
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
        private static final LogisticsActivationLatch ACTIVATION=new LogisticsActivationLatch();
        private static long latchPolledAt=-1;
        private final Runnable action;
        private final int symbol;
        private float hover;
        private long lastFrame,pressedAt;
        private int cachedWidth=-1;
        private boolean primary,selected;
        private Component narration;
        private String cachedLabel="",centerLabel="";
        private List<FormattedCharSequence> labelLines=List.of();
        public ActionButton(int x,int y,int w,int h,Component label,Runnable action){this(x,y,w,h,label,action,-1);}
        public ActionButton(int x,int y,int w,int h,Component label,Runnable action,int symbol){
            super(x,y,w,h,label);this.action=action;this.symbol=symbol;
            setTooltipDelay(Duration.ofMillis(280));
        }
        public ActionButton primary(){primary=true;return this;}
        public ActionButton selected(boolean value){selected=value;return this;}
        public ActionButton hint(Component value){setTooltip(Tooltip.create(value));return this;}
        public ActionButton narration(Component value){narration=value;return this;}

        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
            long now=net.minecraft.Util.getMillis();
            if(latchPolledAt!=now){latchPolledAt=now;ACTIVATION.reconcile(ActionButton::physicallyDown);}
            boolean reduced=reducedMotion();
            float target=active&&isHoveredOrFocused()?1:0;
            float elapsed=lastFrame==0?16:Math.clamp(now-lastFrame,0,100);
            lastFrame=now;
            hover=reduced?target:hover+(target-hover)*(float)(1-Math.exp(-elapsed/65));
            float press=pressedAt==0?0:1-CenterPresentation.ease(now-pressedAt,150);
            int base=!active?0xff15212c:primary?0xff215144:selected?0xff23473f:0xff1b2d3b;
            int body=blend(base,primary?0xff2c6a57:0xff2b4b4b,hover*.8f);
            if(active&&press>0)body=blend(body,0xff10212b,press*.35f);
            surface(g,getX(),getY(),width,height,body);
            if(active){
                int edge=selected||primary?blend(EDGE,TEAL,.55f):blend(EDGE,TEAL,hover*.5f);
                g.hLine(getX()+3,getX()+width-4,getY(),edge);
                if(selected)g.fill(getX()+2,getY()+4,getX()+4,getY()+height-4,TEAL);
                if(isFocused()){
                    g.hLine(getX()+2,getX()+width-3,getY()+height-2,TEAL);
                    g.vLine(getX()+1,getY()+3,getY()+height-4,TEAL);
                    g.vLine(getX()+width-2,getY()+3,getY()+height-4,TEAL);
                }
            }
            var font=Minecraft.getInstance().font;
            int color=!active?0xff6e8392:TEXT,dy=reduced?0:Math.round(press);
            boolean glyph=symbol>=0&&(width>=52||getMessage().getString().isBlank());
            int textWidth=Math.max(1,width-(glyph?40:12));
            String label=getMessage().getString();
            if(!label.equals(cachedLabel)||cachedWidth!=textWidth){
                cachedLabel=label;cachedWidth=textWidth;
                centerLabel=fit(label,textWidth);
                labelLines=font.split(getMessage(),textWidth);
            }
            if(glyph){
                int ix=label.isBlank()?getX()+(width-13)/2:getX()+11;
                icon(g,ix,getY()+(height-13)/2+dy,symbol,!active?0xff6e8392:symbol==9||symbol==10?BRASS:TEAL);
                if(!label.isBlank()){
                    int count=Math.min(labelLines.size(),Math.min(2,Math.max(1,(height-5)/font.lineHeight)));
                    for(int i=0;i<count;i++)g.drawString(font,labelLines.get(i),getX()+32,getY()+(height-count*font.lineHeight)/2+i*font.lineHeight+dy,color,false);
                }
            }else g.drawString(font,centerLabel,getX()+(width-font.width(centerLabel))/2,getY()+(height-font.lineHeight)/2+dy,color,false);
        }
        private void activate(){if(active&&visible){pressedAt=net.minecraft.Util.getMillis();action.run();}}
        private static boolean physicallyDown(int key){return InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(),key);}
        @Override public void onClick(double x,double y){activate();}
        @Override public boolean keyPressed(int key,int scan,int mods){
            if(active&&visible&&isFocused()&&(key==257||key==335||key==32)){
                if(claimActivationKey(key)){playDownSound(Minecraft.getInstance().getSoundManager());activate();}
                return true;
            }
            return super.keyPressed(key,scan,mods);
        }
        @Override public boolean keyReleased(int key,int scan,int mods){return ACTIVATION.release(key)||super.keyReleased(key,scan,mods);}
        @Override protected MutableComponent createNarrationMessage(){return wrapDefaultNarrationMessage(narration==null?getMessage():narration);}
        @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
}
