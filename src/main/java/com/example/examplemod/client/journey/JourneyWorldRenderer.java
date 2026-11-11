package com.example.examplemod.client.journey;

import com.example.examplemod.journey.JourneyModel.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** Depth-tested brass signs. Only the active destination moves; no beams or running stripes. */
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class JourneyWorldRenderer {
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.options.hideGui||mc.screen!=null)return;
        String dim=mc.level.dimension().location().toString();var poses=event.getPoseStack();var camera=event.getCamera().getPosition();var source=mc.renderBuffers().bufferSource();boolean reduced=JourneyClient.reduced();long now=net.minecraft.Util.getMillis();
        var nearest=new ArrayList<>(JourneyClient.markers.values().stream().filter(m->m.inWorld&&m.point.dimension().equals(dim)&&JourneyClient.distance(m.point)<m.range&&mc.level.hasChunkAt(new BlockPos(m.point.x(),m.point.y(),m.point.z())))
                .sorted(Comparator.comparingDouble(m->m.id.equals(JourneyClient.target)?-1:JourneyClient.distance(m.point))).limit(8).toList());
        var completed=JourneyClient.completionMarker;boolean completing=completed!=null&&now-JourneyClient.completionAt<1100&&completed.inWorld&&completed.point.dimension().equals(dim)&&JourneyClient.distance(completed.point)<completed.range&&mc.level.hasChunkAt(new BlockPos(completed.point.x(),completed.point.y(),completed.point.z()));
        if(completing&&nearest.stream().noneMatch(m->m.id.equals(completed.id))){if(nearest.size()>=8)nearest.removeLast();nearest.add(completed);}
        for(var m:nearest){
            boolean done=completing&&m.id.equals(completed.id),active=!done&&m.id.equals(JourneyClient.target);double distance=JourneyClient.distance(m.point);
            float fade=(float)Math.clamp((m.range-distance)/12,0,1);if(done)fade*=1-(now-JourneyClient.completionAt)/1100f;
            double appear=active?JourneyAtlasStyle.reveal(now-JourneyClient.targetAt,reduced):1;
            double bob=active&&!reduced?Math.sin(now/1150d)*.045:0;
            poses.pushPose();poses.translate(m.point.x()+.5-camera.x,m.point.y()+1.8+bob+(1-appear)*.15-camera.y,m.point.z()+.5-camera.z);poses.mulPose(event.getCamera().rotation());
            double radius=m.size*.021;int color=JourneyAtlasStyle.color(m);var out=source.getBuffer(RenderType.lines());
            // Faceted silhouette provides depth without a vertical support line.
            double[][] v={{0,radius,0},{radius,0,0},{0,-radius,0},{-radius,0,0}};
            for(int i=0;i<4;i++){var a=v[i];var b=v[(i+1)%4];line(poses,out,a[0],a[1],a[2],b[0],b[1],b[2],active?JourneyAtlasStyle.BRASS:color,fade);line(poses,out,a[0],a[1],0,0,0,-.07,JourneyAtlasStyle.BRASS,fade*.23f);}
            if(active){double r=radius+.045;float a=fade*(float)(.32*JourneyAtlasStyle.breathe(now,reduced));for(int i=0;i<4;i++){var v1=v[i];var v2=v[(i+1)%4];line(poses,out,v1[0]*r/radius,v1[1]*r/radius,0,v2[0]*r/radius,v2[1]*r/radius,0,JourneyAtlasStyle.BRASS,a);}}
            for(var s:JourneyAtlasStyle.glyph(done?7:m.icon))line(poses,out,s[0]*.022,-s[1]*.022,.015,s[2]*.022,-s[3]*.022,.015,done?0xff91b9a4:color,fade);
            long elapsed=done?now-JourneyClient.completionAt:now-JourneyClient.targetAt;
            if(!reduced&&(done||active&&elapsed<700)){double r=radius+.03+elapsed*.00012;float a=fade*.4f*(float)Math.max(0,1-elapsed/(done?1100d:700d));for(int i=0;i<40;i++){double angle=i*Math.PI/20,next=(i+1)*Math.PI/20;line(poses,out,Math.cos(angle)*r,Math.sin(angle)*r,0,Math.cos(next)*r,Math.sin(next)*r,0,JourneyAtlasStyle.BRASS,a);}}
            source.endBatch(RenderType.lines());
            var look=mc.player.getLookAngle();var towards=new net.minecraft.world.phys.Vec3(m.point.x()+.5-camera.x,m.point.y()+1.8-camera.y,m.point.z()+.5-camera.z).normalize();boolean gazed=look.dot(towards)>.985;
            if(distance<20&&(active||gazed)||gazed&&distance<40){poses.scale(-.025f,-.025f,.025f);var matrix=poses.last().pose();String name=mc.font.plainSubstrByWidth(m.title,180),text=(int)Math.ceil(distance)+" m";int alpha=(int)(fade*255)<<24;
                mc.font.drawInBatch(name,-mc.font.width(name)/2f,15,alpha|(JourneyAtlasStyle.TEXT&0xffffff),false,matrix,source,Font.DisplayMode.NORMAL,0x80182026,LightTexture.FULL_BRIGHT);
                mc.font.drawInBatch(text,-mc.font.width(text)/2f,27,alpha|(JourneyAtlasStyle.BRASS&0xffffff),false,matrix,source,Font.DisplayMode.NORMAL,0x80182026,LightTexture.FULL_BRIGHT);
            }
            poses.popPose();
        }
        // Sparse, stationary turn signs on the authored path, never a simulated walkable path.
        var active=JourneyClient.markers.get(JourneyClient.target);
        if(active!=null&&active.inWorld&&active.point.dimension().equals(dim)&&!active.route.isEmpty()){
            var path=JourneyClient.remaining(active);poses.pushPose();poses.translate(-camera.x,-camera.y,-camera.z);var out=source.getBuffer(RenderType.lines());int count=0;
            for(int i=0;i<path.size()-1&&count<4;i++){var a=path.get(i);var b=path.get(i+1);double dx=b.x()-a.x(),dz=b.z()-a.z(),len=Math.hypot(dx,dz);if(len<2||JourneyClient.distance(a)>32||!mc.level.hasChunkAt(new BlockPos(a.x(),a.y(),a.z())))continue;
                dx/=len;dz/=len;double x=a.x()+.5,y=a.y()+.18,z=a.z()+.5;line(poses,out,x-dx*.28-dz*.16,y,z-dz*.28+dx*.16,x,y,z,JourneyAtlasStyle.BRASS,.6f);line(poses,out,x,y,z,x-dx*.28+dz*.16,y,z-dz*.28-dx*.16,JourneyAtlasStyle.BRASS,.6f);count++;
            }poses.popPose();source.endBatch(RenderType.lines());
        }
    }
    private static void line(PoseStack poses,VertexConsumer out,double ax,double ay,double az,double bx,double by,double bz,int color,float alpha){double dx=bx-ax,dy=by-ay,dz=bz-az,len=Math.sqrt(dx*dx+dy*dy+dz*dz);if(len<.0001)return;var pose=poses.last();float r=((color>>16)&255)/255f,g=((color>>8)&255)/255f,b=(color&255)/255f;out.addVertex(pose,(float)ax,(float)ay,(float)az).setColor(r,g,b,alpha).setNormal(pose,(float)(dx/len),(float)(dy/len),(float)(dz/len));out.addVertex(pose,(float)bx,(float)by,(float)bz).setColor(r,g,b,alpha).setNormal(pose,(float)(dx/len),(float)(dy/len),(float)(dz/len));}
    private JourneyWorldRenderer(){}
}
