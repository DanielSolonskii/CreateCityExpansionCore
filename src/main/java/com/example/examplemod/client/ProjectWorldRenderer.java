package com.example.examplemod.client;
import com.example.examplemod.network.NearbyScenePayload;
import com.example.examplemod.projects.Project;
import com.example.examplemod.client.screen.ProjectMapStyle;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import java.util.*;
/** Depth-tested construction surveying marks; never requests or generates a chunk. */
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class ProjectWorldRenderer {
    private ProjectWorldRenderer(){}
    private record Ground(NearbyScenePayload.Site site,ProjectWorldGeometry.Segment mark,double y1,double y2){}
    private record Height(double y,long time){}
    private static List<Ground> ground=List.of();
    private static final Map<Long,Height> heights=new HashMap<>();
    private static Object world;
    private static int ticks,reads;
    private static long frame;
    private static float opacity;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(world!=mc.level){world=mc.level;ground=List.of();heights.clear();opacity=0;frame=0;}
        if(mc.level==null||mc.player==null)return;if(++ticks%5!=0)return;
        var sites=NearbySceneClient.projects();if(sites.isEmpty()){ground=List.of();return;}
        long now=net.minecraft.Util.getMillis();heights.entrySet().removeIf(e->now-e.getValue().time>5000);reads=0;
        record Candidate(NearbyScenePayload.Site site,ProjectWorldGeometry.Segment mark){}
        var candidates=new ArrayList<Candidate>();
        sites.stream().filter(s->!s.status().terminal()).sorted(Comparator.comparingDouble(s->s.zone().distance(mc.player.getX(),mc.player.getZ()))).limit(8)
            .forEach(s->{for(var mark:ProjectWorldGeometry.nearby(s.zone(),mc.player.getX(),mc.player.getZ()))candidates.add(new Candidate(s,mark));});
        candidates.sort(Comparator.comparingDouble(c->c.mark.distance(mc.player.getX(),mc.player.getZ())));
        var next=new ArrayList<Ground>();
        for(var c:candidates){if(next.size()>=ProjectWorldGeometry.MAX_SEGMENTS)break;Double a=height(c.mark.x1(),c.mark.z1(),now),b=height(c.mark.x2(),c.mark.z2(),now);if(a==null||b==null)continue;
            // Do not stretch a surveying dash across a vertical cliff.
            if(Math.abs(a-b)>3)continue;next.add(new Ground(c.site,c.mark,a,b));}
        ground=List.copyOf(next);
    }
    private static Double height(double x,double z,long now){
        var level=Minecraft.getInstance().level;int bx=(int)Math.floor(x),bz=(int)Math.floor(z);var pos=new BlockPos(bx,0,bz);if(!level.hasChunkAt(pos))return null;
        long key=BlockPos.asLong(bx,0,bz);var old=heights.get(key);if(old!=null&&now-old.time<3000)return old.y;
        if(reads>=64)return old==null?null:old.y;reads++;int y=level.getHeight(Heightmap.Types.WORLD_SURFACE,bx,bz);if(y<=level.getMinBuildHeight())return null;
        double result=y+.09;if(heights.size()<2048||old!=null)heights.put(key,new Height(result,now));return result;
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.options.hideGui||mc.screen!=null||ground.isEmpty()||NearbySceneClient.projects().isEmpty())return;
        long now=net.minecraft.Util.getMillis();float dt=frame==0?.016f:Math.min(.1f,(now-frame)/1000f);frame=now;opacity=CommunityFx.reduced?1:opacity+(1-opacity)*(1-(float)Math.exp(-dt*7));
        var poses=event.getPoseStack();var camera=event.getCamera().getPosition();poses.pushPose();poses.translate(-camera.x,-camera.y,-camera.z);
        var source=mc.renderBuffers().bufferSource();var buffer=source.getBuffer(RenderType.lines());Ground label=null;double nearest=24;
        for(var p:ground){
            if(!mc.level.hasChunkAt(BlockPos.containing(p.mark.x1(),0,p.mark.z1()))||!mc.level.hasChunkAt(BlockPos.containing(p.mark.x2(),0,p.mark.z2())))continue;
            double distance=p.mark.distance(mc.player.getX(),mc.player.getZ());float alpha=(float)Math.clamp((64-distance)/18,0,1)*opacity*.8f;if(alpha<.02)continue;
            int colour=ProjectMapStyle.color(p.site.scope(),p.site.status());float r=((colour>>16)&255)/255f,g=((colour>>8)&255)/255f,b=(colour&255)/255f;
            float pulse=!CommunityFx.reduced&&(p.site.status()==Project.Status.BUILDING||p.site.status()==Project.Status.REVIEW)?(float)(.94+.06*Math.sin(now/850d)):1;
            if(p.mark.post()){
                line(poses,buffer,p.mark.x1(),p.y1,p.mark.z1(),p.mark.x1(),p.y1+1.15,p.mark.z1(),r,g,b,alpha*.7f);
                double radius=p.site.scope()==Project.Scope.SERVER?.24:.18;
                for(int i=0;i<4;i++){double a=(i*.5+(p.site.scope()==Project.Scope.SERVER?0:.25))*Math.PI,c=a+Math.PI/2;
                    line(poses,buffer,p.mark.x1()+Math.cos(a)*radius,p.y1+1.15,p.mark.z1()+Math.sin(a)*radius,p.mark.x1()+Math.cos(c)*radius,p.y1+1.15,p.mark.z1()+Math.sin(c)*radius,r,g,b,alpha);}
            }else line(poses,buffer,p.mark.x1(),p.y1,p.mark.z1(),p.mark.x2(),p.y2,p.mark.z2(),r,g,b,alpha*pulse);
            double labelDistance=Math.hypot(p.mark.x1()-mc.player.getX(),p.mark.z1()-mc.player.getZ());if(labelDistance<nearest){nearest=labelDistance;label=p;}
        }
        poses.popPose();source.endBatch(RenderType.lines());
        if(label!=null&&nearest>2){double dx=label.mark.x1()-camera.x,dy=label.y1+1.7-camera.y,dz=label.mark.z1()-camera.z;var look=mc.player.getLookAngle();double length=Math.sqrt(dx*dx+dy*dy+dz*dz);
            if(length>0&&(dx*look.x+dy*look.y+dz*look.z)/length>.90)label(event,label,source);}
    }
    private static void line(PoseStack poses,VertexConsumer out,double ax,double ay,double az,double bx,double by,double bz,float r,float g,float b,float alpha){
        double dx=bx-ax,dy=by-ay,dz=bz-az,len=Math.sqrt(dx*dx+dy*dy+dz*dz);if(len<.0001)return;var pose=poses.last();
        out.addVertex(pose,(float)ax,(float)ay,(float)az).setColor(r,g,b,alpha).setNormal(pose,(float)(dx/len),(float)(dy/len),(float)(dz/len));
        out.addVertex(pose,(float)bx,(float)by,(float)bz).setColor(r,g,b,alpha).setNormal(pose,(float)(dx/len),(float)(dy/len),(float)(dz/len));
    }
    private static void label(RenderLevelStageEvent event,Ground p,MultiBufferSource.BufferSource source){
        var mc=Minecraft.getInstance();var poses=event.getPoseStack();var camera=event.getCamera();var position=camera.getPosition();poses.pushPose();
        poses.translate(p.mark.x1()-position.x,p.y1+1.7-position.y,p.mark.z1()-position.z);poses.mulPose(camera.rotation());poses.scale(-.023f,-.023f,.023f);
        String name=mc.font.plainSubstrByWidth(p.site.title(),170);var detail=Component.translatable("gui.createcityexpansioncore.nearby."+(p.site.scope()==Project.Scope.SERVER?"server":"city"))
            .append(" · ").append(Component.translatable("gui.createcityexpansioncore.projects.status."+p.site.status().name().toLowerCase(Locale.ROOT)));
        int colour=ProjectMapStyle.color(p.site.scope(),p.site.status());var matrix=poses.last().pose();
        mc.font.drawInBatch(name,-mc.font.width(name)/2f,0,colour,false,matrix,source,Font.DisplayMode.NORMAL,0xB0131D22,LightTexture.FULL_BRIGHT);
        mc.font.drawInBatch(detail,-mc.font.width(detail)/2f,11,0xFFC4CECE,false,matrix,source,Font.DisplayMode.NORMAL,0xB0131D22,LightTexture.FULL_BRIGHT);
        poses.popPose();
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){world=null;ground=List.of();heights.clear();opacity=0;frame=0;}
}
