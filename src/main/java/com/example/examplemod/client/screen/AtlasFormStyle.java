package com.example.examplemod.client.screen;

import com.example.examplemod.client.map.DispatchPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

/** Interactive form controls keep vanilla input and narration but use the atlas' existing chrome. */
public final class AtlasFormStyle {
    private AtlasFormStyle() {}

    public static Button button(int x,int y,int w,int h,Component text,Runnable action,boolean selected,boolean primary) {
        return button(Button.builder(text,ignored->action.run()).bounds(x,y,Math.max(1,w),h),selected,primary);
    }

    public static Button button(Button.Builder builder) {return button(builder,false,false);}

    private static Button button(Button.Builder builder,boolean selected,boolean primary) {
        return new Button(builder) {
            @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta) {
                var bounds=new WorldMapUiLayout.Rect(getX(),getY(),getWidth(),getHeight());
                WorldMapChrome.dispatchControl(g,bounds,selected,isHoveredOrFocused(),active||selected,primary);
                if(primary && active)g.fill(bounds.x()+4,bounds.y()+4,bounds.x()+6,bounds.bottom()-4,DispatchPalette.ACCENT);
                var font=Minecraft.getInstance().font;
                var label=WorldMapChrome.fit(font,getMessage(),Math.max(1,getWidth()-12));
                g.drawString(font,label,getX()+(getWidth()-font.width(label))/2,getY()+(getHeight()-font.lineHeight)/2+1,
                        active||selected?DispatchPalette.TEXT:DispatchPalette.MUTED,false);
                if(isFocused() && active)g.renderOutline(getX()+2,getY()+2,getWidth()-4,getHeight()-4,DispatchPalette.SELECTED);
            }
        };
    }

    public static EditBox field(Font font,int x,int y,int width,int height,Component label) {
        var box=new EditBox(font,x,y,width,height,label) {
            @Override public int getInnerWidth(){return Math.max(1,getWidth()-10);}
            @Override public void onClick(double mx,double my){super.onClick(mx-5,my);}
            @Override public void renderWidget(GuiGraphics g,int mx,int my,float delta) {
                int x=getX(),y=getY(),w=getWidth(),h=getHeight();
                fieldBackground(g,x,y,w,h,isFocused(),isHovered());
                g.enableScissor(x+3,y+1,x+w-3,y+h-1);
                g.pose().pushPose();
                try{
                    g.pose().translate(5,(h-font.lineHeight)/2+1,0);
                    super.renderWidget(g,mx-5,my,delta);
                }finally{g.pose().popPose();g.disableScissor();}
            }
        };
        box.setBordered(false);
        box.setTextColor(DispatchPalette.TEXT);
        box.setTextColorUneditable(DispatchPalette.MUTED);
        return box;
    }

    public static MultiLineEditBox multiline(Font font,int x,int y,int width,int height,Component placeholder,Component label) {
        return new MultiLineEditBox(font,x,y,width,height,placeholder,label) {
            @Override protected void renderBackground(GuiGraphics graphics) {
                fieldBackground(graphics,getX(),getY(),getWidth(),getHeight(),isFocused(),isHovered());
            }
        };
    }

    private static void fieldBackground(GuiGraphics g,int x,int y,int w,int h,boolean focused,boolean hovered) {
        int edge=focused?DispatchPalette.ACCENT:hovered?0xFF7497A5:DispatchPalette.EDGE;
        WorldMapChrome.rounded(g,x,y,w,h,3,edge);
        WorldMapChrome.rounded(g,x+1,y+1,w-2,h-2,2,DispatchPalette.INSET);
    }

    static void workspace(GuiGraphics g,Font font,int width,int height,int x,int w,Component title,boolean route) {
        g.fill(0,0,width,height,0xFF101A20);
        for(int i=Math.floorMod(x,32);i<width;i+=32)g.vLine(i,0,height,0xFF17262D);
        for(int i=0;i<height;i+=32)g.hLine(0,width,i,0xFF17262D);
        WorldMapChrome.dispatchPanel(g,x-6,3,w+12,height-6);
        WorldMapChrome.rounded(g,x,8,20,18,3,0xFF253E46);
        if(route){
            g.hLine(x+4,x+10,19,DispatchPalette.ACCENT);g.vLine(x+10,13,19,DispatchPalette.ACCENT);
            g.hLine(x+10,x+15,13,DispatchPalette.ACCENT);
            g.renderOutline(x+3,17,4,4,DispatchPalette.TEXT);g.renderOutline(x+14,11,4,4,DispatchPalette.TEXT);
        }else WorldMapChrome.viewIcon(g,14,x+2,9,DispatchPalette.ACCENT);
        g.drawString(font,WorldMapChrome.fit(font,title,w-30),x+26,13,DispatchPalette.TEXT,false);
        g.hLine(x,x+w-1,29,DispatchPalette.EDGE);
    }

    static void inset(GuiGraphics g,int x,int y,int width,int height){
        WorldMapChrome.rounded(g,x,y,width,height,4,DispatchPalette.EDGE);
        WorldMapChrome.rounded(g,x+1,y+1,width-2,height-2,3,DispatchPalette.INSET);
    }
}
