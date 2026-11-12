package com.example.examplemod.client.journey;

import com.example.examplemod.journey.JourneyModel.*;
import com.example.examplemod.journey.JourneyRequestPayload.Action;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Scrollable full descriptions and objective requirements; no guide text is lost to ellipsis. */
public final class JourneyDetailScreen extends Screen {
    private final Screen parent;
    private final String id;
    private final boolean quest;
    private int scroll,total;
    public JourneyDetailScreen(Screen parent,String id,boolean quest){super(JourneyClient.tr(quest?"quest":"place"));this.parent=parent;this.id=id;this.quest=quest;}
    private CompoundTag record(){return JourneyClient.quests.stream().filter(q->q.getString("Id").equals(id)).findFirst().orElse(null);}
    @Override protected void init(){int w=Math.min(430,width-20),x=(width-w)/2;var q=record();var m=JourneyClient.markers.get(id);
        if(quest&&q!=null){var button=JourneyAtlasStyle.builder(JourneyClient.tr(q.getBoolean("Done")?"done":q.getBoolean("Available")?"track":"locked"),b->{JourneyClient.request(Action.TRACK_QUEST,id);onClose();}).bounds(x,height-32,Math.min(150,w-94),20).build();button.active=q.getBoolean("Available");addRenderableWidget(button);}
        if(!quest&&m!=null)addRenderableWidget(JourneyAtlasStyle.builder(JourneyClient.tr("track_place"),b->{JourneyClient.request(Action.TRACK_MARKER,id);onClose();}).bounds(x,height-32,Math.min(150,w-94),20).build());
        if(!quest&&m!=null)addRenderableWidget(JourneyAtlasStyle.builder(JourneyClient.tr("tab.0"),b->minecraft.setScreen(new JourneyScreen(this,id))).bounds(x,height-56,Math.min(150,w-94),20).build());
        addRenderableWidget(JourneyAtlasStyle.builder(JourneyClient.tr("close"),b->onClose()).bounds(x+w-86,height-32,86,20).build());
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xf010191f);int w=Math.min(430,width-20),x=(width-w)/2;var q=record();var m=JourneyClient.markers.get(id);String name=quest&&q!=null?q.getString("Title"):m!=null?m.title:id;g.drawString(font,font.plainSubstrByWidth(name,w),x,16,JourneyAtlasStyle.BRASS,false);
        List<Component> lines=new ArrayList<>();String description=quest&&q!=null?q.getString("Description"):m!=null?m.description:"";lines.add(Component.literal(description));
        if(quest&&q!=null)for(var raw:q.getList("Objectives",10)){var o=(CompoundTag)raw;String kind=o.getString("Kind").toLowerCase(Locale.ROOT),target=o.getString("Target");var marker=JourneyClient.markers.get(target);lines.add(JourneyClient.tr("objective."+kind,marker==null?target:marker.title,o.getInt("Count")).copy().withStyle(o.getBoolean("Met")?net.minecraft.ChatFormatting.GREEN:net.minecraft.ChatFormatting.GRAY));}
        if(!quest&&m!=null){lines.add(Component.literal(m.point.dimension()+" · "+m.point.x()+" / "+m.point.y()+" / "+m.point.z()));lines.add(JourneyClient.tr(m.route.isEmpty()?"direction":"route"));lines.add(JourneyClient.tr("related"));for(var related:JourneyClient.quests)if(related.getString("Marker").equals(id)||related.getList("Objectives",10).stream().anyMatch(v->((CompoundTag)v).getString("Target").equals(id)))lines.add(Component.literal(related.getString("Title")));}
        int y=44-scroll;g.enableScissor(x,40,x+w,height-(quest?44:68));for(var line:lines){for(var part:font.split(line,w-8)){g.drawString(font,part,x+4,y,0xffc5d0d0,false);y+=11;}y+=10;}g.disableScissor();total=y+scroll-44;super.render(g,mx,my,delta);
    }
    @Override public boolean mouseScrolled(double mx,double my,double dx,double dy){scroll=Math.clamp(scroll-(int)(dy*24),0,Math.max(0,total-(height-(quest?88:112))));return true;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
}
