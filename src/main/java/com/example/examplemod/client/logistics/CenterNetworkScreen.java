package com.example.examplemod.client.logistics;

import com.example.examplemod.client.CommunityFx;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import static com.example.examplemod.client.logistics.LogisticsSkin.*;

/** Authorized line topology. Painted cards and hit testing share the same measured geometry. */
final class CenterNetworkScreen extends Screen implements CenterResponseScreen {
    private record LineBox(CompoundTag line,int top,int height) {}
    private final LogisticsCenterScreen parent;
    private final long opened=net.minecraft.Util.getMillis();
    private CompoundTag snapshot;
    private int scroll,content,left,panelWidth;
    private List<LineBox> measured=List.of();
    private boolean layoutDirty=true;

    CenterNetworkScreen(LogisticsCenterScreen parent){super(tr("visual.network"));this.parent=parent;this.snapshot=parent.snapshot();}
    @Override protected void init(){
        CommunityFx.load();panelWidth=Math.min(760,width-24);left=(width-panelWidth)/2;layoutDirty=true;
        addRenderableWidget(new ActionButton(left,height-38,panelWidth,24,tr("back"),this::onClose));
    }
    @Override public void tick(){super.tick();parent.tick();}
    @Override public void accept(CompoundTag response){parent.accept(response);var next=parent.snapshot();layoutDirty|=!snapshot.getList("Network",10).equals(next.getList("Network",10));snapshot=next;}
    private int top(){return height<260?64:76+font.wordWrapHeight(tr("visual.network_help"),panelWidth-28);}
    private List<LineBox> boxes(){
        if(!layoutDirty)return measured;
        var result=new ArrayList<LineBox>();int y=0;
        for(Tag raw:snapshot.getList("Network",10)){
            var line=(CompoundTag)raw;int h=52+CenterVisualContent.lineHeight(line,panelWidth-40)+12;
            result.add(new LineBox(line,y,h));y+=h+12;
        }
        layoutDirty=false;measured=List.copyOf(result);return measured;
    }
    @Override public void render(GuiGraphics g,int mx,int my,float dt){
        background(g,width,height);
        header(g,left,12,panelWidth,title,Component.translatable("ccec.center.ui.network_line_count",snapshot.getList("Network",10).size()),3);
        if(height>=260)g.drawWordWrap(font,tr("visual.network_help"),left+14,66,panelWidth-28,MUTED);
        int top=top(),bottom=height-48;var cards=boxes();int end=top-scroll;
        g.enableScissor(left-2,top,left+panelWidth+3,bottom);
        for(var box:cards){
            int y=top+box.top()-scroll;end=y+box.height()+12;
            if(y+box.height()<top||y>bottom)continue;
            var line=box.line();boolean hover=mx>=left&&mx<left+panelWidth&&my>=Math.max(top,y)&&my<Math.min(bottom,y+box.height());
            surface(g,left,y,panelWidth,box.height(),hover?0xff1b303a:PANEL);
            g.fill(left,y+5,left+2,y+box.height()-5,hover?TEAL:EDGE);
            icon(g,left+14,y+13,3,TEAL);
            int statusWidth=Math.min(112,panelWidth/3);
            g.drawString(font,fit(name(line),panelWidth-statusWidth-56),left+37,y+13,TEXT,false);
            g.drawString(font,fit(value(line.getString("Status")),statusWidth),left+panelWidth-statusWidth-14,y+13,CenterPresentation.problem(line.getString("Status"))?BRASS:TEAL,false);
            g.drawString(font,Component.translatable("ccec.center.ui.network_terminals",line.getList("Details",10).size()),left+37,y+28,MUTED,false);
            g.hLine(left+14,left+panelWidth-15,y+42,EDGE);
            CenterVisualContent.renderLine(g,line,left+20,y+52,panelWidth-40,opened);
        }
        if(cards.isEmpty()){
            scroll=0;
            surface(g,left,top,panelWidth,Math.max(42,Math.min(100,bottom-top)),PANEL);
            g.drawWordWrap(font,tr("section_hint.lines"),left+16,top+16,panelWidth-32,MUTED);end=top+70;
        }
        if(snapshot.getBoolean("NetworkLimited")){g.drawWordWrap(font,tr("visual.network_limited"),left+14,end,panelWidth-28,BRASS);end+=font.wordWrapHeight(tr("visual.network_limited"),panelWidth-28)+12;}
        content=end+scroll-top;g.disableScissor();scroll=Math.min(scroll,CenterPresentation.scrollLimit(content,bottom-top));
        if(content>bottom-top){int track=bottom-top,thumb=Math.max(14,track*track/content);int sy=top+(track-thumb)*scroll/Math.max(1,content-track);g.fill(left+panelWidth+5,sy,left+panelWidth+7,sy+thumb,TEAL);}
        super.render(g,mx,my,dt);
        if(my>=top&&my<bottom)for(var box:cards){int y=top+box.top()-scroll;if(mx>=left&&mx<left+panelWidth&&my>=y&&my<y+box.height()){g.renderTooltip(font,tr("visual.open"),mx,my);break;}}
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button==0&&x>=left&&x<left+panelWidth&&y>=top()&&y<height-48)for(var box:boxes()){
            int cy=top()+box.top()-scroll;if(y>=cy&&y<cy+box.height()){minecraft.setScreen(new CenterDetailsScreen(parent,box.line()));return true;}}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){scroll=Math.max(0,Math.min(CenterPresentation.scrollLimit(content,height-48-top()),scroll-(int)(vertical*30)));return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
