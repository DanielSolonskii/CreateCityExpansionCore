package com.example.examplemod.client.logistics;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Authorized line topology, independent of geographic track geometry. */
final class CenterNetworkScreen extends Screen implements CenterResponseScreen {
    private final LogisticsCenterScreen parent;
    private CompoundTag snapshot;
    private int scroll,content;
    CenterNetworkScreen(LogisticsCenterScreen parent){super(tr("visual.network"));this.parent=parent;this.snapshot=parent.snapshot();}
    @Override protected void init(){addRenderableWidget(new ActionButton(16,height-38,width-32,24,tr("back"),this::onClose));}
    @Override public void tick(){super.tick();parent.tick();}
    @Override public void accept(CompoundTag response){parent.accept(response);snapshot=parent.snapshot();}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        g.fillGradient(0,0,width,height,BG,0xff14272c);g.drawString(font,text("visual.network"),20,18,TEAL,false);
        g.drawWordWrap(font,tr("visual.network_help"),20,36,width-40,MUTED);
        int top=46+font.wordWrapHeight(tr("visual.network_help"),width-40),bottom=height-48,y=top-scroll;
        g.enableScissor(16,top,width-16,bottom);
        var lines=snapshot.getList("Network",10);
        if(lines.isEmpty()){g.drawWordWrap(font,tr("section_hint.lines"),20,y+12,width-40,MUTED);y+=50;}
        for(Tag raw:lines){var line=(CompoundTag)raw;surface(g,20,y,width-40,30,PANEL);icon(g,30,y+8,3,TEAL);g.drawString(font,fit(name(line),width-150),50,y+10,TEXT,false);g.drawString(font,fit(value(line.getString("Status")),90),width-120,y+10,MUTED,false);y+=40;y=CenterVisualContent.render(g,line,32,y,width-64)+18;}
        if(snapshot.getBoolean("NetworkLimited")){g.drawWordWrap(font,tr("visual.network_limited"),20,y,width-40,BRASS);y+=font.wordWrapHeight(tr("visual.network_limited"),width-40)+12;}
        content=y+scroll-top;g.disableScissor();scroll=Math.min(scroll,CenterPresentation.scrollLimit(content,bottom-top));
        if(content>bottom-top){int track=bottom-top,thumb=Math.max(14,track*track/content);int sy=top+(track-thumb)*scroll/Math.max(1,content-track);g.fill(width-10,sy,width-8,sy+thumb,TEAL);}
        super.render(g,mx,my,dt);
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        int top=46+font.wordWrapHeight(tr("visual.network_help"),width-40),cy=top-scroll;
        if(button==0&&x>=20&&x<width-20&&y>=top&&y<height-48)for(Tag raw:snapshot.getList("Network",10)){
            var line=(CompoundTag)raw;int h=40+18;for(Tag part:line.getList("Details",10)){var title=net.minecraft.network.chat.Component.literal(name((CompoundTag)part));h+=font.wordWrapHeight(title,width-89)+27;}
            if(y>=cy&&y<cy+h){minecraft.setScreen(new CenterDetailsScreen(parent,line));return true;}cy+=h+18;
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double h,double v){int top=46+font.wordWrapHeight(tr("visual.network_help"),width-40);scroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(content,height-48-top),scroll-(int)(v*28)));return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
