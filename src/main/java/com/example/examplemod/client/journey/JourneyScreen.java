package com.example.examplemod.client.journey;
import com.example.examplemod.journey.JourneyRequestPayload.Action;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;

public final class JourneyScreen extends Screen {
    private final Screen parent;
    private final String marker;
    private int page;
    private int tab;
    public JourneyScreen(Screen parent,String marker){super(JourneyClient.tr("journal"));this.parent=parent;this.marker=marker;}
    private int left(){return Math.max(8,(width-440)/2);}
    private int panelWidth(){return Math.min(440,width-16);}
    private int rows(){return Math.max(1,(height-144)/42);}
    private List<net.minecraft.nbt.CompoundTag> entries(){
        if(tab==1)return JourneyClient.chains;
        if(tab==2){var list=new ArrayList<net.minecraft.nbt.CompoundTag>();for(var m:JourneyClient.markers.values()){var t=new net.minecraft.nbt.CompoundTag();t.putString("Id",m.id);t.putString("Title",m.title);t.putString("Description",m.description);list.add(t);}return list;}
        return JourneyClient.quests.stream().filter(q->marker==null||q.getString("Marker").equals(marker)||q.getList("Objectives",10).stream().anyMatch(v->((net.minecraft.nbt.CompoundTag)v).getString("Target").equals(marker))).toList();
    }
    @Override protected void init(){
        int x=left(),w=panelWidth();var entries=entries();page=Math.min(page,Math.max(0,(entries.size()-1)/rows()));int start=page*rows();
        for(int i=start;i<Math.min(entries.size(),start+rows());i++){var q=entries.get(i);boolean pinned=q.getBoolean("Pinned"),done=q.getBoolean("Done"),available=q.getBoolean("Available");int y=94+(i-start)*42;
            if(tab!=0){addRenderableWidget(JourneyAtlasStyle.builder(JourneyClient.tr(tab==1?"start":"details"),b->{if(tab==1){JourneyClient.request(Action.START,q.getString("Id"));onClose();}else minecraft.setScreen(new JourneyDetailScreen(this,q.getString("Id"),false));}).bounds(x+w-94,y,86,20).build());continue;}
            var button=JourneyAtlasStyle.builder(JourneyClient.tr(done?"done":!available?"locked":"track"),b->{JourneyClient.request(Action.TRACK_QUEST,q.getString("Id"));}).bounds(x+w-94,y,86,20).build();button.active=available&&!done;addRenderableWidget(button);
            if(pinned)addRenderableWidget(JourneyAtlasStyle.builder(Component.literal("×"),b->JourneyClient.request(Action.UNPIN,q.getString("Id"))).bounds(x+w-116,y,20,20).build());
        }
        if(marker!=null&&JourneyClient.markers.containsKey(marker))addRenderableWidget(JourneyAtlasStyle.builder(JourneyClient.tr("track_place"),b->{JourneyClient.request(Action.TRACK_MARKER,marker);onClose();}).bounds(x,67,Math.min(158,w),20).build());
        else addRenderableWidget(JourneyAtlasStyle.builder(JourneyClient.tr(JourneyClient.paused?"resume":"pause"),b->{JourneyClient.request(JourneyClient.paused?Action.RESUME:Action.PAUSE,"");onClose();}).bounds(x,67,Math.min(158,w),20).build());
        int bottom=height-28;
        addRenderableWidget(JourneyAtlasStyle.builder(Component.literal("‹"),b->{page=Math.max(0,page-1);rebuildWidgets();}).bounds(x,bottom,26,20).build());
        addRenderableWidget(JourneyAtlasStyle.builder(Component.literal("›"),b->{if((page+1)*rows()<entries.size())page++;rebuildWidgets();}).bounds(x+30,bottom,26,20).build());
        addRenderableWidget(JourneyAtlasStyle.builder(JourneyClient.tr("close"),b->onClose()).bounds(x+w-86,bottom,86,20).build());
        addRenderableWidget(JourneyAtlasStyle.builder(JourneyClient.tr("tab."+((tab+1)%3)),b->{tab=(tab+1)%3;page=0;rebuildWidgets();}).bounds(x+w-104,67,104,20).build());
    }
    private String signature="";
    @Override public void tick(){String next=JourneyClient.quests.toString()+JourneyClient.chains+JourneyClient.paused;if(!signature.equals(next)){signature=next;rebuildWidgets();}}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xef10191f);int x=left(),w=panelWidth();g.drawString(font,title,x,14,JourneyAtlasStyle.TEXT,false);
        var m=JourneyClient.markers.get(marker);String subtitle=m==null?JourneyClient.tr("journal_hint").getString():m.title;g.drawString(font,font.plainSubstrByWidth(subtitle,w),x,31,JourneyAtlasStyle.BRASS,false);
        if(m!=null){g.drawString(font,font.plainSubstrByWidth(m.description,w),x,46,JourneyAtlasStyle.MUTED,false);}else if(!JourneyClient.chain.isEmpty()){var c=JourneyClient.chains.stream().filter(v->v.getString("Id").equals(JourneyClient.chain)).findFirst().orElse(null);if(c!=null)g.drawString(font,font.plainSubstrByWidth(c.getString("Title")+" · "+c.getInt("Done")+"/"+c.getInt("Total"),w),x,46,JourneyAtlasStyle.MUTED,false);}
        var entries=entries();for(int i=page*rows();i<Math.min(entries.size(),(page+1)*rows());i++){var q=entries.get(i);int y=94+(i-page*rows())*42;g.fill(x-3,y-3,x+w+3,y+35,q.getString("Id").equals(JourneyClient.activeQuest)?0xff35413f:JourneyAtlasStyle.RAISED);g.drawString(font,font.plainSubstrByWidth(q.getString("Title"),Math.max(10,w-124)),x+5,y+3,q.getBoolean("Done")?JourneyAtlasStyle.BRASS:JourneyAtlasStyle.TEXT,false);String detail=tab==1?q.getInt("Done")+"/"+q.getInt("Total"):tab==2?q.getString("Description"):q.getInt("DoneCount")+"/"+q.getList("Objectives",10).size()+" · "+q.getString("Description");g.drawString(font,font.plainSubstrByWidth(detail,Math.max(10,w-124)),x+5,y+18,JourneyAtlasStyle.MUTED,false);}
        if(entries.isEmpty())g.drawString(font,JourneyClient.tr("empty"),x,102,JourneyAtlasStyle.MUTED,false);super.render(g,mx,my,delta);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public boolean mouseClicked(double mx,double my,int button){if(button==0&&mx>=left()&&mx<left()+panelWidth()-124&&my>=94&&my<94+rows()*42){int index=page*rows()+((int)my-94)/42;var entries=entries();if(index<entries.size()&&tab!=1){minecraft.setScreen(new JourneyDetailScreen(this,entries.get(index).getString("Id"),tab==0));return true;}}return super.mouseClicked(mx,my,button);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
