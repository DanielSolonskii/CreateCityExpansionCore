package com.example.examplemod.client.logistics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Read-only detail surface for narrow scaled windows. Never performs mutations. */
public final class CenterDetailsScreen extends Screen implements CenterResponseScreen {
    private final LogisticsCenterScreen parent;
    private CompoundTag row;
    private final java.util.UUID id;
    private int left, panelWidth, scroll, contentHeight;
    public CenterDetailsScreen(LogisticsCenterScreen parent, CompoundTag row) {
        super(tr("details")); this.parent=parent; this.row=row.copy();this.id=row.getUUID("Id");
    }
    @Override protected void init() {
        panelWidth=Math.min(500,width-24);left=(width-panelWidth)/2;
        addRenderableWidget(new ActionButton(left,height-38,panelWidth,24,tr("back"),this::onClose));
        parent.selectForDetails(id);
    }
    @Override public void tick(){super.tick();parent.tick();}
    @Override public void accept(CompoundTag response){parent.accept(response);var current=parent.snapshot().getCompound("SelectedRow");row=current.hasUUID("Id")&&current.getUUID("Id").equals(id)?current.copy():new CompoundTag();}
    @Override public void render(GuiGraphics g,int mx,int my,float dt) {
        g.fillGradient(0,0,width,height,BG,0xff14272c);
        surface(g,left,12,panelWidth,42,PANEL);
        icon(g,left+14,23,7,TEAL);
        g.drawString(font,fit(row.isEmpty()?text("object_unavailable"):name(row),panelWidth-56),left+36,24,TEXT,false);
        g.drawString(font,text("details"),left+36,39,MUTED,false);
        if(row.isEmpty())scroll=0;int top=64,bottom=height-48,y=top+12-scroll;
        surface(g,left,top,panelWidth,Math.max(10,bottom-top),PANEL);
        g.enableScissor(left+8,top+6,left+panelWidth-8,bottom-5);
        if(row.isEmpty())y=wrapped(g,text("object_unavailable"),y,MUTED);
        else for(var field:fields(row)){
            icon(g,left+14,y,field.icon(),MUTED);var label=Component.literal(field.label());g.drawWordWrap(font,label,left+33,y,panelWidth-49,MUTED);
            y+=font.wordWrapHeight(label,panelWidth-49)+6;y=wrapped(g,field.value(),y,TEXT)+10;
        }
        contentHeight=y+scroll-top+16;
        scroll=Math.min(scroll,CenterPresentation.scrollLimit(contentHeight,bottom-top));
        g.disableScissor();
        if(contentHeight>bottom-top){int track=bottom-top-12,thumb=Math.max(14,track*(bottom-top)/contentHeight);int sy=top+6+(track-thumb)*scroll/Math.max(1,contentHeight-(bottom-top));g.fill(left+panelWidth-4,sy,left+panelWidth-2,sy+thumb,TEAL);}
        super.render(g,mx,my,dt);
    }
    private int wrapped(GuiGraphics g,String value,int y,int color) {
        if(value.isBlank())return y;
        Component component=Component.literal(value);
        g.drawWordWrap(font,component,left+14,y,panelWidth-30,color);
        return y+font.wordWrapHeight(component,panelWidth-30)+5;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {
        scroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(contentHeight,height-112),scroll-(int)(vertical*22)));
        return true;
    }
    @Override public void onClose() { minecraft.setScreen(parent); }
    @Override public boolean isPauseScreen() { return false; }
}
