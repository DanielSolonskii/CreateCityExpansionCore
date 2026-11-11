package com.example.examplemod.client.logistics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Read-only explanation. Returning preserves the form and never repeats its request. */
final class CenterErrorScreen extends Screen implements CenterResponseScreen {
    private final Screen parent;
    private final String explanation;
    private int scroll,left,panelWidth,content;
    CenterErrorScreen(Screen parent,String explanation){super(tr("error.explain"));this.parent=parent;this.explanation=explanation;}
    @Override public void accept(net.minecraft.nbt.CompoundTag response){if(parent instanceof CenterResponseScreen screen)screen.accept(response);}
    @Override public void tick(){super.tick();parent.tick();}
    @Override protected void init(){panelWidth=Math.min(600,width-32);left=(width-panelWidth)/2;addRenderableWidget(new ActionButton(left,height-38,panelWidth,24,tr("back"),this::onClose).primary());}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        background(g,width,height);header(g,left,12,panelWidth,tr("ui.attention_title"),title,13);
        int top=66,bottom=height-50;surface(g,left,top,panelWidth,Math.max(20,bottom-top),PANEL);
        g.enableScissor(left+12,top+8,left+panelWidth-12,bottom-6);
        int y=top+14-scroll;g.fill(left+14,y,left+17,y+font.wordWrapHeight(Component.literal(explanation),panelWidth-52)+4,BRASS);
        g.drawWordWrap(font,Component.literal(explanation),left+29,y,panelWidth-48,TEXT);
        y+=font.wordWrapHeight(Component.literal(explanation),panelWidth-48)+22;
        g.hLine(left+14,left+panelWidth-15,y,EDGE);y+=16;
        g.drawWordWrap(font,tr("ui.attention_help"),left+16,y,panelWidth-32,MUTED);
        content=y+font.wordWrapHeight(tr("ui.attention_help"),panelWidth-32)+scroll-top+14;
        g.disableScissor();scroll=Math.min(scroll,CenterPresentation.scrollLimit(content,bottom-top));
        if(content>bottom-top){int track=bottom-top-12,thumb=Math.max(12,track*track/content),sy=top+6+(track-thumb)*scroll/Math.max(1,content-(bottom-top));g.fill(left+panelWidth-4,sy,left+panelWidth-2,sy+thumb,BRASS);}
        super.render(g,mx,my,partial);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(content,height-116),scroll-(int)(vertical*22)));return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
