package com.example.examplemod.client.journey;

import com.example.examplemod.client.CommunityFx;
import com.example.examplemod.journey.*;
import com.example.examplemod.journey.JourneyModel.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.util.*;

@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class JourneyClient {
    public static final Map<String,Marker> markers=new LinkedHashMap<>();
    public static List<CompoundTag> quests=List.of(),chains=List.of();
    public static String target="",activeQuest="",chain="";
    public static boolean paused,canEdit,detour;
    public static Marker completionMarker;
    public static long targetAt,completionAt;
    private static boolean ready;
    public static final KeyMapping JOURNAL=new KeyMapping("key.createcityexpansioncore.journal",net.neoforged.neoforge.client.settings.KeyConflictContext.IN_GAME,com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_F8,"key.categories.createcityexpansioncore");
    private static Object connection;
    private static int ticks,waypoint;
    private static String routeTarget="",completed="";
    private static long completedAt;
    public static Component tr(String key,Object... args){return Component.translatable("gui.createcityexpansioncore.journey."+key,args);}
    public static void request(JourneyRequestPayload.Action action,String id){if(Minecraft.getInstance().getConnection()!=null)PacketDistributor.sendToServer(new JourneyRequestPayload(action,id));}
    private static List<CompoundTag> list(CompoundTag t,String name){var result=new ArrayList<CompoundTag>();for(var v:t.getList(name,10))result.add((CompoundTag)v);return List.copyOf(result);}
    public static void accept(JourneyStatePayload payload){
        var mc=Minecraft.getInstance();if(mc.player==null)return;connection=mc.getConnection();var t=payload.data();var oldMarker=markers.get(target);String oldTarget=target,oldActiveQuest=activeQuest;canEdit=t.getBoolean("CanEdit");markers.clear();for(var raw:t.getList("Markers",10)){var m=JourneyCodec.marker((CompoundTag)raw);markers.put(m.id,m);}quests=list(t,"Quests");chains=list(t,"Chains");target=t.getString("Target");activeQuest=t.getString("ActiveQuest");chain=t.getString("Chain");paused=t.getBoolean("Paused");
        detour=t.getBoolean("Detour");
        if(!target.equals(oldTarget))targetAt=net.minecraft.Util.getMillis();
        if(!t.getString("Completed").isEmpty()){completionMarker=quests.stream().anyMatch(q->q.getString("Id").equals(oldActiveQuest)&&q.getBoolean("Done"))?oldMarker:null;completionAt=net.minecraft.Util.getMillis();completed=t.getString("Completed");completedAt=net.minecraft.Util.getMillis();CommunityFx.sound("success");}
        ready=true;if(t.getBoolean("Open"))mc.setScreen(new JourneyScreen(mc.screen,null));
    }
    private static void clear(){markers.clear();quests=chains=List.of();target=activeQuest=chain=routeTarget=completed="";paused=ready=canEdit=detour=false;completionMarker=null;targetAt=completionAt=0;waypoint=ticks=0;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){connection=null;clear();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(connection!=mc.getConnection()){clear();connection=mc.getConnection();}
        if(mc.player==null||mc.getConnection()==null)return;
        if(++ticks%100==1&&!ready)request(JourneyRequestPayload.Action.STATE,"");
        while(JOURNAL.consumeClick())if(mc.screen==null){
            if(JourneyPlaytest.running()) JourneyPlaytest.openGuide();
            else mc.setScreen(new JourneyScreen(null,null));
        }
        var m=markers.get(target);if(m==null)return;var path=path(m);
        String routeKey=target+":"+m.point+":"+m.route.hashCode();
        if(!routeTarget.equals(routeKey)){routeTarget=routeKey;waypoint=0;}
        while(waypoint<path.size()-1&&distance(path.get(waypoint))<3)waypoint++;
    }
    public static double distance(Point p){var mc=Minecraft.getInstance();return mc.player==null?Double.POSITIVE_INFINITY:p.distance(mc.player.level().dimension().location().toString(),mc.player.getX(),mc.player.getY(),mc.player.getZ());}
    public static List<Point> path(Marker m){var points=new ArrayList<>(m.route);points.add(m.point);return points;}
    public static List<Point> remaining(Marker m){var path=path(m);return path.subList(Math.min(waypoint,path.size()-1),path.size());}
    static Marker displayTarget() {
        return JourneyPlaytest.running() ? JourneyPlaytest.marker() : paused ? null : markers.get(target);
    }
    static List<Point> displayPath(Marker marker) {
        return JourneyPlaytest.running() ? JourneyPlaytest.remaining(marker) : remaining(marker);
    }
    static long displayTargetAt() {
        return JourneyPlaytest.running() ? JourneyPlaytest.targetAt() : targetAt;
    }
    static Marker displayCompletion() {
        return JourneyPlaytest.running() ? JourneyPlaytest.completionMarker() : completionMarker;
    }
    static long displayCompletionAt() {
        return JourneyPlaytest.running() ? JourneyPlaytest.completionAt() : completionAt;
    }
    public static boolean showingGuidance() {
        return JourneyPlaytest.running() || !paused && (!target.isEmpty() || !activeQuest.isEmpty());
    }
    public static boolean reduced(){CommunityFx.load();return CommunityFx.reduced;}
    public static String symbol(Kind kind){return switch(kind){case GUIDE->"?";case QUEST->"!";case ACTIVITY->"+";case LANDMARK->"i";};}
    public static int color(Kind kind){return switch(kind){case GUIDE->0xff78d7c1;case QUEST->0xffefc47e;case ACTIVITY->0xff89bdf0;case LANDMARK->0xffc8c5d3;};}
    @SubscribeEvent public static void hud(RenderGuiEvent.Post event){JourneyHud.render(event,completed,completedAt);}
    @EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void setup(FMLClientSetupEvent event){
            event.enqueueWork(()->{
                JourneyNetwork.client=JourneyClient::accept;
                JourneyNetwork.editor=packet->{
                    var screen=Minecraft.getInstance().screen;
                    if(screen instanceof JourneyRouteEditorScreen route) route.receive(packet.data());
                    else if(screen instanceof JourneyAtlasScreen atlas) atlas.receive(packet.data());
                };
            });
        }
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event){event.register(JOURNAL);}
    }
    private JourneyClient(){}
}
