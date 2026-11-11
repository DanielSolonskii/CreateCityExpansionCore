package com.example.examplemod.client;

import com.example.examplemod.city.*;
import com.example.examplemod.network.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;
import java.util.*;

/** Cached, depth-tested nearby border accents. All terrain work is tick-budgeted, never per frame. */
public final class CityBoundaryRenderer {
    private static final int MAX_MARKS=BoundaryGeometry.MAX_MARKS;
    private static final int RANGE=72;
    private record Visual(CityBoundariesPayload.CityView city,BoundaryGeometry geometry){}
    private record Ground(Visual visual,BoundaryGeometry.Mark mark,double y){}
    private static List<Visual> cities=List.of();
    private static List<Ground> ground=List.of(),beacons=List.of();
    private static Visual preview;
    private static boolean previewVisible,forcedVisible,initialized;
    private static Object world;
    private static UUID lastCity;
    private static long nextRequest,lastSnapshot,pulseUntil,lastFrame;
    private static int ticks,particleCursor;
    private static float opacity;
    public static void setVisible(boolean visible,Set<CityPlot> territory,int cityColor){
        previewVisible=visible;Set<CityPlot> incoming=territory==null?Set.of():territory;
        if(preview==null||!preview.city.plots().equals(incoming)||preview.city.colour()!=cityColor){
            var data=new CityBoundariesPayload.CityView(new UUID(0,0),"",CityColors.normalize(cityColor),false,incoming);
            preview=new Visual(data,new BoundaryGeometry(data.plots()));
        }
    }
    public static void hide(){previewVisible=false;preview=null;}
    public static boolean isVisible(){return previewVisible||forcedVisible;}
    public static void accept(CityBoundariesPayload payload){
        var mc=Minecraft.getInstance();if(mc.level==null||!mc.level.dimension().location().equals(payload.dimension()))return;
        ensureWorld();Map<UUID,Visual> old=new HashMap<>();for(var c:cities)old.put(c.city.id(),c);
        List<Visual> next=new ArrayList<>();
        for(var c:payload.cities()){
            var previous=old.get(c.id());next.add(new Visual(c,previous!=null&&previous.city.plots().equals(c.plots())?previous.geometry:new BoundaryGeometry(c.plots())));
        }
        cities=List.copyOf(next);lastSnapshot=net.minecraft.Util.getMillis();refreshGround();
    }
    private static void ensureWorld(){var current=Minecraft.getInstance().level;if(world!=current){world=current;clear();}}
    private static void clear(){cities=List.of();ground=List.of();beacons=List.of();preview=null;previewVisible=false;forcedVisible=false;initialized=false;lastCity=null;nextRequest=lastSnapshot=pulseUntil=lastFrame=0;opacity=0;ticks=particleCursor=0;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){world=null;clear();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;ensureWorld();long now=net.minecraft.Util.getMillis();
        if(mc.screen==null)while(!BoundaryKeys.TOGGLE.isUnbound()&&BoundaryKeys.TOGGLE.consumeClick()){
            forcedVisible=!isVisible();previewVisible=false;
            mc.player.displayClientMessage(Component.translatable("gui.createcityexpansioncore.boundaries.mode",modeName()),true);
        }
        if(now>=nextRequest){nextRequest=now+5000;PacketDistributor.sendToServer(new RequestCityBoundariesPayload());}
        if(lastSnapshot!=0&&now-lastSnapshot>15000){cities=List.of();ground=List.of();initialized=false;lastSnapshot=0;}
        Visual current=null;var plot=CityPlot.at(mc.level.dimension().location(),mc.player.blockPosition());
        for(var c:cities)if(c.city.plots().contains(plot)){current=c;break;}
        UUID id=current==null?null:current.city.id();
        if(lastSnapshot!=0){
            if(initialized&&!Objects.equals(id,lastCity)){
                pulseUntil=now+2800;
                Component message=current==null?Component.translatable("gui.createcityexpansioncore.boundaries.wilderness"):
                    Component.literal(current.city.name()+" · ").append(Component.translatable("gui.createcityexpansioncore.boundaries."+(current.city.canBuild()?"allowed":"denied")));
                mc.player.displayClientMessage(message,true);
            }
            initialized=true;lastCity=id;
        }
        if(++ticks%5==0)refreshGround();
        if(ticks%3==0&&!mc.options.hideGui&&mc.screen==null&&mc.options.particles().get()!=ParticleStatus.MINIMAL){
            int count=mc.options.particles().get()==ParticleStatus.DECREASED?1:2;
            for(int i=0;i<count&&!beacons.isEmpty();i++){
                var point=beacons.get(Math.floorMod(particleCursor++,beacons.size()));double distance=Math.sqrt(BoundaryGeometry.distance2(point.mark,mc.player.getX(),mc.player.getZ()));
                if(!point.mark.corner()||strength(distance,now)<.1||distance>40)continue;
                int colour=point.visual.city.colour();var dust=new DustParticleOptions(new Vector3f(((colour>>16)&255)/255f,((colour>>8)&255)/255f,(colour&255)/255f),.65f);
                double rise=((ticks+i*7)%44)/20d;
                mc.level.addParticle(dust,point.mark.x(),point.y+rise,point.mark.z(),0,.012,0);
            }
        }
    }
    public static Component modeName(){return Component.translatable("gui.createcityexpansioncore.boundaries.mode."+(isVisible()?1:0));}
    private static double strength(double distance,long now){
        double base=isVisible()?.8:now<pulseUntil?.8*Math.clamp((pulseUntil-now)/900d,0,1):Math.clamp((12-distance)/12,0,1)*.45;
        return base*BoundaryGeometry.fade(distance,RANGE);
    }
    private static void refreshGround(){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;
        List<Visual> source=cities.isEmpty()&&previewVisible&&preview!=null?List.of(preview):cities;
        record Candidate(Visual visual,BoundaryGeometry.Mark mark){}
        List<Candidate> candidates=new ArrayList<>();
        for(var c:source)for(var mark:c.geometry.nearby(mc.player.getX(),mc.player.getZ(),RANGE))candidates.add(new Candidate(c,mark));
        candidates.sort(Comparator.<Candidate>comparingDouble(c->BoundaryGeometry.distance2(c.mark,mc.player.getX(),mc.player.getZ())).thenComparingInt(c->Objects.equals(c.visual.city.id(),lastCity)?0:1));
        List<Ground> next=new ArrayList<>();Set<BoundaryGeometry.Mark> seen=new HashSet<>();
        for(var c:candidates){if(next.size()>=MAX_MARKS)break;if(!seen.add(c.mark))continue;int x=(int)Math.floor(c.mark.x()),z=(int)Math.floor(c.mark.z());
            if(!mc.level.hasChunkAt(new BlockPos(x,0,z)))continue;
            int height=mc.level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
            if(height<=mc.level.getMinBuildHeight())continue;
            next.add(new Ground(c.visual,c.mark,height+.075));
        }
        ground=List.copyOf(next);beacons=ground.stream().filter(g->g.mark.corner()).toList();
    }
    @SubscribeEvent public static void onRenderLevel(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.options.hideGui||ground.isEmpty())return;
        long now=net.minecraft.Util.getMillis();float dt=lastFrame==0?.016f:Math.min(.1f,(now-lastFrame)/1000f);lastFrame=now;
        float target=1;opacity=opacity+(target-opacity)*(1-(float)Math.exp(-dt*7));
        PoseStack poses=event.getPoseStack();var camera=event.getCamera().getPosition();poses.pushPose();poses.translate(-camera.x,-camera.y,-camera.z);
        var source=mc.renderBuffers().bufferSource();var buffer=source.getBuffer(RenderType.lines());
        for(var point:ground){var m=point.mark;double distance=Math.sqrt(BoundaryGeometry.distance2(m,mc.player.getX(),mc.player.getZ()));float alpha=(float)strength(distance,now)*opacity;if(alpha<.015)continue;
            int c=point.visual.city.colour();float r=((c>>16)&255)/255f,g=((c>>8)&255)/255f,b=(c&255)/255f;
            double shimmer=(.92+.08*Math.sin(now/900d+m.x()*.08+m.z()*.08));float a=(float)(alpha*shimmer);
            if(m.corner()){
                // Small ground frame and restrained 2.4-block corner mast, never a solid wall.
                LevelRenderer.renderLineBox(poses,buffer,m.x()-.28,point.y,m.z()-.28,m.x()+.28,point.y+.015,m.z()+.28,r,g,b,a);
                LevelRenderer.renderLineBox(poses,buffer,m.x()-.025,point.y,m.z()-.025,m.x()+.025,point.y+2.4,m.z()+.025,r,g,b,a*.55f);
                double lift=(now%2400)/1000d;
                LevelRenderer.renderLineBox(poses,buffer,m.x()-.12,point.y+lift,m.z()-.12,m.x()+.12,point.y+lift+.02,m.z()+.12,r,g,b,a);
            }else{
                double dx=m.alongX()?.65:.025,dz=m.alongX()?.025:.65;
                LevelRenderer.renderLineBox(poses,buffer,m.x()-dx,point.y,m.z()-dz,m.x()+dx,point.y+.015,m.z()+dz,r,g,b,a);
            }
        }
        poses.popPose();source.endBatch(RenderType.lines());
    }
}
