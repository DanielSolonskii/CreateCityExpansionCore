package com.example.examplemod.journey;

import com.example.examplemod.city.CityManager;
import com.example.examplemod.cargo.center.CargoShipmentRegistry;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.projects.Project;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static com.example.examplemod.journey.JourneyModel.*;

@EventBusSubscriber(modid="createcityexpansioncore")
public final class JourneyServer {
    public static boolean visible(Marker m,Progress p){return m.enabled&&(!m.newcomers||!p.graduated)&&(m.afterQuest.isEmpty()||p.completed.contains(m.afterQuest));}
    public static Point location(ServerPlayer p){var b=p.blockPosition();return new Point(p.level().dimension().location().toString(),b.getX(),b.getY(),b.getZ());}
    public static boolean near(Marker m,ServerPlayer p){return m.point.distance(p.level().dimension().location().toString(),p.getX(),p.getY(),p.getZ())<=m.radius;}
    public static void request(ServerPlayer player,JourneyRequestPayload request){
        if(player instanceof FakePlayer)return;
        var budget=player.getPersistentData();long window=net.minecraft.Util.getMillis()/1000;
        if(budget.getLong("CCECJourneyWindow")!=window){budget.putLong("CCECJourneyWindow",window);budget.putInt("CCECJourneyRequests",0);}
        int count=budget.getInt("CCECJourneyRequests");if(count>=6)return;budget.putInt("CCECJourneyRequests",count+1);
        var d=JourneyData.get(player.getServer());var p=d.player(player.getUUID());
        try{switch(request.action()){
            case STATE,JOURNAL -> {}
            case TRACK_QUEST -> {var q=d.quest(request.id());if(!available(q,p,d.chains.values()))throw new IllegalArgumentException("Quest unavailable");p.accept(q.id);}
            case TRACK_MARKER -> {var m=d.marker(request.id());if(!visible(m,p))throw new IllegalArgumentException("Marker unavailable");p.activeQuest="";p.activeMarker=m.id;p.paused=false;}
            case UNPIN -> {p.accepted.remove(request.id());if(p.activeQuest.equals(request.id())){p.activeQuest="";p.paused=true;}}
            case START -> {var c=d.chain(request.id());if(!c.enabled||c.steps.isEmpty())throw new IllegalArgumentException("Journey unavailable");String next=JourneyModel.next(c,p);if(!next.isEmpty()&&!p.accepted.contains(next)&&p.accepted.size()>=3)throw new IllegalArgumentException("Only three pinned quests are allowed");p.chain=c.id;p.activeMarker=p.activeQuest="";p.autoStarted=true;p.paused=false;advance(d,p);}
            case PAUSE -> p.paused=true;
            case RESUME -> {p.paused=false;advance(d,p);}
        }}catch(IllegalArgumentException failure){player.displayClientMessage(Component.translatable("gui.createcityexpansioncore.journey.error",failure.getMessage()),false);}
        d.setDirty();send(player,request.action()==JourneyRequestPayload.Action.JOURNAL,"");
    }
    public static void advance(JourneyData d,Progress p){
        var c=d.chains.get(p.chain);if(p.paused||c==null||!c.enabled)return;
        String next=JourneyModel.next(c,p);
        if(next.isEmpty()){if(c.automatic)p.graduated=true;p.chain="";return;}
        if(!p.activeQuest.isEmpty()||!p.activeMarker.isEmpty())return;
        var q=d.quests.get(next);if(available(q,p,d.chains.values())&&(p.accepted.contains(next)||p.accepted.size()<3))p.accept(next);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        var server=event.getServer();if(server.getTickCount()%20!=0)return;var d=JourneyData.get(server);
        if(d.markers.isEmpty()&&d.quests.isEmpty())return;
        // Server state is sampled once per second; no chunks are requested or generated.
        List<CargoShipmentRegistry.Shipment> shipments=null;List<Project> projects=null;
        boolean cargo=d.quests.values().stream().anyMatch(q->q.enabled&&q.objectives.stream().anyMatch(o->o.kind()==ObjectiveKind.SHIPMENT_DELIVERED));
        boolean construction=d.quests.values().stream().anyMatch(q->q.enabled&&q.objectives.stream().anyMatch(o->o.kind()==ObjectiveKind.PROJECT_COMPLETED));
        if(cargo)shipments=CargoShipmentRegistry.get(server).all();if(construction)projects=EconomyManager.get(server.overworld()).projects().all();
        for(var player:server.getPlayerList().getPlayers()){
            if(player instanceof FakePlayer)continue;var p=d.player(player.getUUID());boolean changed=false;
            if(!p.autoStarted&&!p.paused){var c=d.chains.values().stream().filter(v->v.enabled&&v.automatic&&!v.steps.isEmpty()).findFirst().orElse(null);if(c!=null){p.chain=c.id;p.autoStarted=true;changed=true;advance(d,p);}}
            String completed="";
            if(!p.paused)for(String id:List.copyOf(p.accepted)){
                var q=d.quests.get(id);if(!available(q,p,d.chains.values()))continue;
                for(var o:q.objectives){String key=q.id+"/"+o.id();if(!p.met.contains(key)&&satisfied(d,player,o,shipments,projects)){p.met.add(key);changed=true;}}
                if(p.finish(q)){completed=q.title;changed=true;}
            }
            String old=p.activeQuest;boolean graduated=p.graduated;advance(d,p);changed|=!old.equals(p.activeQuest)||graduated!=p.graduated;
            if(changed){d.setDirty();send(player,false,completed);}
        }
    }
    private static boolean satisfied(JourneyData d,ServerPlayer p,Objective o,List<CargoShipmentRegistry.Shipment> shipments,List<Project> projects){
        return switch(o.kind()){
            case VISIT -> {var m=d.markers.get(o.target());yield m!=null&&visible(m,d.player(p.getUUID()))&&near(m,p);}
            case INTERACT -> false; // Only the authoritative interaction event may satisfy this.
            case ITEM -> {int total=0;for(var stack:p.getInventory().items)if(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(o.target()))total+=stack.getCount();yield total>=o.count();}
            case CITY_MEMBER -> CityManager.get(p.getServer().overworld()).getMemberCity(p.getUUID())!=null;
            case SHIPMENT_DELIVERED -> shipments!=null&&shipments.stream().filter(s->s.creator().equals(p.getUUID())&&s.stage()==CargoShipmentRegistry.Stage.COMPLETED&&s.physicalComplete()).count()>=o.count();
            case PROJECT_COMPLETED -> projects!=null&&projects.stream().filter(v->v.status()==Project.Status.COMPLETED&&v.executor()!=null&&v.executor().kind()==Project.ApplicantKind.PLAYER&&v.executor().id().equals(p.getUUID())).count()>=o.count();
            case ADVANCEMENT -> {var a=p.getServer().getAdvancements().get(ResourceLocation.parse(o.target()));yield a!=null&&p.getAdvancements().getOrStartProgress(a).isDone();}
        };
    }
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public static void interact(PlayerInteractEvent.RightClickBlock event){
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer player)||player instanceof FakePlayer)return;
        var d=JourneyData.get(player.getServer());var p=d.player(player.getUUID());if(p.paused)return;boolean changed=false;
        for(String id:p.accepted){var q=d.quests.get(id);if(!available(q,p,d.chains.values()))continue;for(var o:q.objectives){if(o.kind()!=ObjectiveKind.INTERACT)continue;var m=d.markers.get(o.target());if(m!=null&&visible(m,p)&&near(m,player)&&m.point.x()==event.getPos().getX()&&m.point.y()==event.getPos().getY()&&m.point.z()==event.getPos().getZ())changed|=p.met.add(q.id+"/"+o.id());}}
        if(changed)d.setDirty();
    }
    public static String target(JourneyData d,Progress p){
        if(p.paused)return "";if(!p.activeMarker.isEmpty())return p.activeMarker;var q=d.quests.get(p.activeQuest);if(!available(q,p,d.chains.values()))return "";
        for(var o:q.objectives)if(!p.met.contains(q.id+"/"+o.id())&&(o.kind()==ObjectiveKind.VISIT||o.kind()==ObjectiveKind.INTERACT))return o.target();return q.marker;
    }
    public static void send(ServerPlayer player,boolean open,String completed){
        var d=JourneyData.get(player.getServer());var state=snapshot(d,d.player(player.getUUID()),open,completed);state.putBoolean("CanEdit",player.hasPermissions(2));PacketDistributor.sendToPlayer(player,new JourneyStatePayload(state));
    }
    public static CompoundTag snapshot(JourneyData d,Progress p,boolean open,String completed){
        var t=new CompoundTag();var markers=new ListTag();String target=target(d,p);d.markers.values().stream().filter(m->visible(m,p)).forEach(m->{var v=JourneyCodec.marker(m);if(!m.id.equals(target))v.remove("Route");markers.add(v);});t.put("Markers",markers);
        var quests=new ListTag();for(var q:d.quests.values()){if(!q.enabled)continue;var v=JourneyCodec.quest(q);for(var raw:v.getList("Objectives",10)){var o=(CompoundTag)raw;o.putBoolean("Met",p.met.contains(q.id+"/"+o.getString("Id")));o.remove("Id");}v.putBoolean("Available",available(q,p,d.chains.values()));v.putBoolean("Done",p.completed.contains(q.id));v.putBoolean("Pinned",p.accepted.contains(q.id));int done=(int)q.objectives.stream().filter(o->p.met.contains(q.id+"/"+o.id())).count();v.putInt("DoneCount",done);quests.add(v);}t.put("Quests",quests);
        var chains=new ListTag();for(var c:d.chains.values())if(c.enabled){var v=new CompoundTag();v.putString("Id",c.id);v.putString("Title",c.title);v.put("Steps",JourneyCodec.strings(c.steps));v.putInt("Total",c.steps.size());v.putInt("Done",(int)c.steps.stream().filter(p.completed::contains).count());chains.add(v);}t.put("Chains",chains);
        t.putString("Target",target(d,p));t.putString("ActiveQuest",p.activeQuest);t.putString("Chain",p.chain);t.putBoolean("Paused",p.paused);t.putBoolean("Open",open);t.putString("Completed",completed);return t;
    }
    private JourneyServer(){}
}
