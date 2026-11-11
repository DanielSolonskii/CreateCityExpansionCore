package com.example.examplemod.client.journey;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import static com.example.examplemod.client.journey.JourneyAtlasStyle.*;

/** Quiet navigation card, with the same brass sign as the atlas. */
public final class JourneyHud {
    public static void render(RenderGuiEvent.Post event,String completed,long completedAt){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.screen!=null||mc.options.hideGui)return;
        var g=event.getGuiGraphics();long now=net.minecraft.Util.getMillis();boolean reduced=JourneyClient.reduced();
        if(!completed.isEmpty()&&now-completedAt<3500){int w=Math.min(260,g.guiWidth()-24),x=(g.guiWidth()-w)/2,y=28+(int)(reduced?0:(1-reveal(now-completedAt,false))*-6);panel(g,x,y,w,34);for(var s:glyph(7))line(g,x+17+s[0],y+17+s[1],x+17+s[2],y+17+s[3],BRASS);g.drawString(mc.font,JourneyClient.tr("complete"),x+32,y+6,BRASS,false);g.drawString(mc.font,mc.font.plainSubstrByWidth(completed,w-42),x+32,y+20,TEXT,false);}
        var m=JourneyClient.markers.get(JourneyClient.target);if(m==null||JourneyClient.paused||mc.player.hurtTime>0||mc.player.getHealth()<=6)return;
        int w=Math.min(250,g.guiWidth()-24),x=12,y=g.guiHeight()-88;panel(g,x,y,w,48);mark(g,x+18,y+21,m,true,false,now,reduced,false);
        var q=JourneyClient.quests.stream().filter(v->v.getString("Id").equals(JourneyClient.activeQuest)).findFirst().orElse(null);
        String name=q==null?m.title:q.getString("Title");g.drawString(mc.font,mc.font.plainSubstrByWidth(name,w-46),x+36,y+7,TEXT,false);
        String progress=q==null?JourneyClient.tr("kind."+m.kind.name()).getString():q.getInt("DoneCount")+" / "+q.getList("Objectives",10).size()+" · "+m.title;
        g.drawString(mc.font,mc.font.plainSubstrByWidth(progress,w-46),x+36,y+20,MUTED,false);
        String nav;if(!m.point.dimension().equals(mc.player.level().dimension().location().toString()))nav=JourneyClient.tr("dimension",m.point.dimension()).getString();
        else{var p=JourneyClient.remaining(m).getFirst();double yaw=Math.toDegrees(Math.atan2(-(p.x()+.5-mc.player.getX()),p.z()+.5-mc.player.getZ()));double delta=Mth.wrapDegrees(yaw-mc.player.getYRot());String arrow=Math.abs(delta)<20?"↑":delta>0?"→":"←";nav=arrow+" "+(int)Math.ceil(JourneyClient.distance(m.point))+" m · "+JourneyClient.tr(m.route.isEmpty()?"direction":"route").getString();}
        g.drawString(mc.font,mc.font.plainSubstrByWidth(nav,w-46),x+36,y+34,BRASS,false);
    }
    private JourneyHud(){}
}
