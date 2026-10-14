package com.example.examplemod.auth;

import com.example.examplemod.CreateCityExpansionCore;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.SharedConstants;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-thread immutable captures; all serialization and HTTP run on a single bounded worker. */
@EventBusSubscriber(modid=CreateCityExpansionCore.MODID)
public final class ServerTelemetryBridge {
    private static volatile Bridge active;
    private ServerTelemetryBridge(){}
    public static void emit(String type,UUID subject,UUID id,String detail){
        Bridge bridge=active;if(bridge==null||subject==null||!bridge.server.isSameThread())return;
        bridge.events.offer(new Event(UUID.randomUUID(),subject,type,id,detail,now(),0));
    }
    private static Instant now(){return Instant.now().truncatedTo(ChronoUnit.MILLIS);}
    public record Event(UUID id,UUID minecraftUuid,String type,UUID subjectId,String detail,Instant occurredAt,long durationSeconds){}
    private record Session(UUID id,long startedNanos){}
    private record Snapshot(Instant observedAt,boolean online,long uptimeSeconds,List<UUID> players,int maxPlayers,long droppedEvents){}
    record Batch(UUID id,String serverId,UUID bootId,Instant startedAt,long revision,Instant observedAt,boolean online,long uptimeSeconds,List<UUID> players,int maxPlayers,String minecraftVersion,String ccecVersion,String serverVersion,long droppedEvents,List<Event> events){}
    private static final class Bridge {
        final MinecraftServer server;final GameBackendClient client;final UUID boot=UUID.randomUUID();final Instant started=now();final long nanos=System.nanoTime();
        final TelemetryBuffer<Event> events=new TelemetryBuffer<>(2048);final Map<UUID,Session> sessions=new HashMap<>();
        final AtomicReference<Snapshot> snapshot=new AtomicReference<>();final ScheduledExecutorService worker;
        final String serverId,mc,ccec,version;long revision;Batch pending;long retryAfter;int failures;int ticks;
        Bridge(MinecraftServer server){
            this.server=server;client=new GameBackendClient(AuthenticationServerConfig.BACKEND.get(),System.getenv("CCEC_BACKEND_SECRET"));serverId=AuthenticationServerConfig.SERVER_ID.get();
            mc=SharedConstants.getCurrentVersion().getName();ccec=ModList.get().getModContainerById(CreateCityExpansionCore.MODID).orElseThrow().getModInfo().getVersion().toString();version="NeoForge "+ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion();
            worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"CCEC telemetry");t.setDaemon(true);return t;});
        }
        void capture(boolean online){snapshot.set(new Snapshot(now(),online,Math.max(0,(System.nanoTime()-nanos)/1_000_000_000),online?server.getPlayerList().getPlayers().stream().filter(p->!(p instanceof FakePlayer)).limit(100).map(ServerPlayer::getUUID).toList():List.of(),server.getMaxPlayers(),events.dropped()));}
        void session(UUID mc,String type,Session s){events.offer(new Event(UUID.randomUUID(),mc,type,s.id(),"",now(),Math.max(0,(System.nanoTime()-s.startedNanos())/1_000_000_000)));}
        void flush(){
            if(System.nanoTime()<retryAfter)return;
            try{
                if(pending==null){Snapshot s=snapshot.get();if(s==null)return;pending=new Batch(UUID.randomUUID(),serverId,boot,started,++revision,s.observedAt(),s.online(),s.uptimeSeconds(),s.players(),s.maxPlayers(),mc,ccec,version,s.droppedEvents(),events.batch(50));}
                var receipt=client.post("api/internal/game/telemetry",wire(pending));
                if(!pending.id().toString().equals(receipt.get("id").getAsString()))throw new IllegalStateException();
                events.acknowledge();pending=null;failures=0;
            }catch(Exception unavailable){
                if(rejectPermanent(events,unavailable)) {
                    pending=null;failures=0;retryAfter=0;
                    com.mojang.logging.LogUtils.getLogger().warn("CCEC telemetry: invalid batch discarded; heartbeat will continue. Dropped events: {}",events.dropped());
                } else {failures=Math.min(6,failures+1);retryAfter=System.nanoTime()+TimeUnit.SECONDS.toNanos(Math.min(60,1L<<failures));}
            }
        }
    }
    static boolean rejectPermanent(TelemetryBuffer<?> events,Exception failure){
        if(!(failure instanceof GameBackendClient.InvalidTelemetry))return false;
        events.discardPending();return true;
    }
    static Map<String,Object> wire(Batch b){
        Map<String,Object> body=new LinkedHashMap<>();body.put("id",b.id());body.put("serverId",b.serverId());body.put("bootId",b.bootId());body.put("startedAt",b.startedAt().toString());body.put("revision",b.revision());body.put("observedAt",b.observedAt().toString());body.put("online",b.online());body.put("uptimeSeconds",b.uptimeSeconds());body.put("players",b.players());body.put("maxPlayers",b.maxPlayers());body.put("minecraftVersion",b.minecraftVersion());body.put("ccecVersion",b.ccecVersion());body.put("serverVersion",b.serverVersion());body.put("droppedEvents",b.droppedEvents());
        body.put("events",b.events().stream().map(e->{Map<String,Object> value=new LinkedHashMap<>();value.put("id",e.id());value.put("minecraftUuid",e.minecraftUuid());value.put("type",e.type());value.put("subjectId",e.subjectId());value.put("detail",e.detail());value.put("occurredAt",e.occurredAt().toString());value.put("durationSeconds",e.durationSeconds());return value;}).toList());return body;
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        try{Bridge bridge=new Bridge(event.getServer());active=bridge;bridge.capture(true);bridge.worker.scheduleWithFixedDelay(bridge::flush,0,5,TimeUnit.SECONDS);}
        catch(RuntimeException unavailable){active=null; /* Optional telemetry; never disable gameplay. */}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){Bridge b=active;if(b==null||b.server!=event.getServer())return;
        if(++b.ticks%400==0)b.capture(true);
        if(b.ticks%1200==0)b.sessions.forEach((mc,s)->b.session(mc,"SESSION_CHECKPOINT",s));
    }
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event){if(!(event.getEntity() instanceof ServerPlayer p)||p instanceof FakePlayer)return;Bridge b=active;if(b==null||b.server!=p.getServer())return;
        Session s=new Session(UUID.randomUUID(),System.nanoTime());if(b.sessions.putIfAbsent(p.getUUID(),s)!=null)return;
        b.session(p.getUUID(),"SESSION_START",s);emit("PLAYER_JOIN",p.getUUID(),s.id(),"");b.capture(true);
    }
    @SubscribeEvent public static void left(PlayerEvent.PlayerLoggedOutEvent event){if(!(event.getEntity() instanceof ServerPlayer p)||p instanceof FakePlayer)return;Bridge b=active;if(b==null||b.server!=p.getServer())return;
        Session s=b.sessions.remove(p.getUUID());if(s!=null){b.session(p.getUUID(),"SESSION_END",s);emit("PLAYER_LEAVE",p.getUUID(),s.id(),"");} // capture next tick: logout list may still contain the player.
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){Bridge b=active;if(b==null||b.server!=event.getServer())return;
        b.sessions.forEach((mc,s)->{b.session(mc,"SESSION_END",s);emit("PLAYER_LEAVE",mc,s.id(),"");});b.sessions.clear();b.capture(false);active=null;
        b.worker.execute(()->{b.retryAfter=0;b.flush();b.retryAfter=0;b.flush();});b.worker.shutdown(); // No wait/join or HTTP on the stopping thread.
    }
}
