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
    public static void client(ServerPlayer player,com.example.examplemod.network.ClientTelemetryPayload payload){
        Bridge b=active;if(b==null||b.server!=player.getServer())return;Session session=b.sessions.get(player.getUUID());if(session==null)return;
        if(payload.kind()==2){
            if(!Set.of("MAP","PROFILE","COMMUNITY","SOCIAL","PYLON").contains(payload.attempt()))return;
            long time=System.nanoTime();Long previous=b.surfaceWindows.get(player.getUUID());if(previous!=null&&time-previous<TimeUnit.SECONDS.toNanos(2))return;b.surfaceWindows.put(player.getUUID(),time);
            emit("UI_SURFACE",player.getUUID(),session.id(),payload.attempt());return;
        }
        if(payload.kind()!=0&&payload.kind()!=1)return;
        if(payload.kind()==0){if(b.clientWindows.containsKey(player.getUUID()))return;b.clientWindows.put(player.getUUID(),System.nanoTime());try{UUID id=UUID.fromString(payload.attempt());emit("CLIENT_CONTEXT",player.getUUID(),session.id(),id.toString());}catch(IllegalArgumentException ignored){}return;}
        long now=System.nanoTime();Long prior=b.clientWindows.get(player.getUUID());if(prior!=null&&now-prior<TimeUnit.SECONDS.toNanos(15))return;b.clientWindows.put(player.getUUID(),now);
        if(payload.fps()<0||payload.fps()>2000||payload.p95()<0||payload.p95()>60000||payload.stalls()<0||payload.stalls()>100000||payload.tiles()<0||payload.tiles()>100000||payload.hits()<0||payload.hits()>100000||payload.tileMs()<0||payload.tileMs()>120000||payload.ping() < -1||payload.ping()>60000)return;
        emit("CLIENT_QUALITY",player.getUUID(),session.id(),"fps."+payload.fps()+":p95."+payload.p95()+":stalls."+payload.stalls()+":ping."+payload.ping());
        emit("MAP_QUALITY",player.getUUID(),session.id(),"tiles."+payload.tiles()+":disk."+payload.hits()+":wait."+payload.tileMs());
    }
    public static void disconnectReason(ServerPlayer player,net.minecraft.network.chat.Component reason){
        Bridge b=active;if(b==null||b.server!=player.getServer()||!b.server.isSameThread()||!b.sessions.containsKey(player.getUUID()))return;
        String code="SERVER_DISCONNECT";
        if(reason.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text){
            String key=text.getKey();if(key.equals("disconnect.timeout"))code="TIMEOUT";else if(key.contains("kicked"))code="KICK";
        }
        b.disconnectReasons.put(player.getUUID(),code);
    }
    public static UUID sessionId(UUID player){Bridge b=active;Session s=b==null?null:b.sessions.get(player);return s==null?null:s.id();}
    private static Instant now(){return Instant.now().truncatedTo(ChronoUnit.MILLIS);}
    public record Event(UUID id,UUID minecraftUuid,String type,UUID subjectId,String detail,Instant occurredAt,long durationSeconds){}
    private record Session(UUID id,long startedNanos){}
    private record Snapshot(Instant observedAt,boolean online,long uptimeSeconds,List<UUID> players,int maxPlayers,long droppedEvents){}
    record Batch(UUID id,String serverId,UUID bootId,Instant startedAt,long revision,Instant observedAt,boolean online,long uptimeSeconds,List<UUID> players,int maxPlayers,String minecraftVersion,String ccecVersion,String serverVersion,long droppedEvents,List<Event> events){}
    private static final class Bridge {
        final MinecraftServer server;final GameBackendClient client;final UUID boot=UUID.randomUUID();final Instant started=now();final long nanos=System.nanoTime();
        final TelemetryBuffer<Event> events=new TelemetryBuffer<>(2048);final Map<UUID,Session> sessions=new HashMap<>();
        final TelemetryDiskQueue disk;volatile long spoolDropped;long lastPersist;long lastCapture;long lastCheckpoint;long tickStarted;long tickNanos;int tickSamples;
        final Map<UUID,Long> clientWindows=new HashMap<>(),surfaceWindows=new HashMap<>();
        final Map<UUID,String> disconnectReasons=new HashMap<>();
        final AtomicReference<Snapshot> snapshot=new AtomicReference<>();final ScheduledExecutorService worker;
        final String serverId,mc,ccec,version;long revision;Batch pending;long retryAfter;int failures;int ticks;
        Bridge(MinecraftServer server){
            this.server=server;disk=new TelemetryDiskQueue(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve("data/ccec-telemetry-outbox"));client=new GameBackendClient(AuthenticationServerConfig.BACKEND.get(),System.getenv("CCEC_BACKEND_SECRET"));serverId=AuthenticationServerConfig.SERVER_ID.get();
            mc=SharedConstants.getCurrentVersion().getName();ccec=ModList.get().getModContainerById(CreateCityExpansionCore.MODID).orElseThrow().getModInfo().getVersion().toString();version="NeoForge "+ModList.get().getModContainerById("neoforge").orElseThrow().getModInfo().getVersion();
            worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"CCEC telemetry");t.setDaemon(true);return t;});
        }
        void capture(boolean online){snapshot.set(new Snapshot(now(),online,Math.max(0,(System.nanoTime()-nanos)/1_000_000_000),online?server.getPlayerList().getPlayers().stream().filter(p->!(p instanceof FakePlayer)).limit(100).map(ServerPlayer::getUUID).toList():List.of(),server.getMaxPlayers(),events.dropped()+spoolDropped));}
        void session(UUID mc,String type,Session s){events.offer(new Event(UUID.randomUUID(),mc,type,s.id(),"",now(),Math.max(0,(System.nanoTime()-s.startedNanos())/1_000_000_000)));}
        void flush(){
            try{
                Snapshot state=snapshot.get();if(state==null)return;
                for(int checkpoint=0;checkpoint<42;checkpoint++){
                var queued=events.batch(50);
                // Persist events before delivery; empty heartbeats do not fill a failing outbox.
                if(!queued.isEmpty()||System.nanoTime()>=retryAfter&&System.currentTimeMillis()-lastPersist>=5000){
                    var batch=new Batch(UUID.randomUUID(),serverId,boot,started,++revision,state.observedAt(),state.online(),state.uptimeSeconds(),state.players(),state.maxPlayers(),mc,ccec,version,state.droppedEvents(),queued);
                    if(disk.put(wire(batch),started.toEpochMilli(),batch.revision())){events.acknowledge();lastPersist=System.currentTimeMillis();}else break;
                }
                if(queued.isEmpty())break;
                }
                if(System.nanoTime()<retryAfter)return;
                for(int i=0;i<8;i++){
                    var head=disk.first();if(head==null)break;
                    try{
                        var receipt=client.post("api/internal/game/telemetry",head.body());
                        if(!head.body().get("id").getAsString().equals(receipt.get("id").getAsString()))throw new IllegalStateException();
                        disk.remove(head);failures=0;retryAfter=0;
                    }catch(GameBackendClient.InvalidTelemetry invalid){spoolDropped+=head.body().getAsJsonArray("events").size();disk.remove(head);}
                }
            }catch(Exception unavailable){failures=Math.min(6,failures+1);retryAfter=System.nanoTime()+TimeUnit.SECONDS.toNanos(Math.min(60,1L<<failures));}
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
    @SubscribeEvent public static void beforeTick(ServerTickEvent.Pre event){Bridge b=active;if(b!=null&&b.server==event.getServer())b.tickStarted=System.nanoTime();}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){Bridge b=active;if(b==null||b.server!=event.getServer())return;
        long now=System.nanoTime();if(b.tickStarted!=0){b.tickNanos+=Math.max(0,now-b.tickStarted);b.tickSamples++;}
        if(now-b.lastCapture>=TimeUnit.SECONDS.toNanos(20)){b.lastCapture=now;b.capture(true);}
        if(now-b.lastCheckpoint>=TimeUnit.SECONDS.toNanos(60)){
            b.lastCheckpoint=now;b.sessions.forEach((mc,s)->b.session(mc,"SESSION_CHECKPOINT",s));
            var player=b.server.getPlayerList().getPlayers().stream().filter(p->!(p instanceof FakePlayer)).findFirst().orElse(null);
            if(player!=null&&b.tickSamples>0)emit("SERVER_QUALITY",player.getUUID(),null,"mspt."+Math.round(b.tickNanos/(double)b.tickSamples/1000000));
            b.tickNanos=0;b.tickSamples=0;
        }
    }
    @SubscribeEvent public static void joined(PlayerEvent.PlayerLoggedInEvent event){if(!(event.getEntity() instanceof ServerPlayer p)||p instanceof FakePlayer)return;Bridge b=active;if(b==null||b.server!=p.getServer())return;
        Session s=new Session(UUID.randomUUID(),System.nanoTime());if(b.sessions.putIfAbsent(p.getUUID(),s)!=null)return;
        b.session(p.getUUID(),"SESSION_START",s);emit("PLAYER_JOIN",p.getUUID(),s.id(),"");b.capture(true);
    }
    @SubscribeEvent public static void left(PlayerEvent.PlayerLoggedOutEvent event){if(!(event.getEntity() instanceof ServerPlayer p)||p instanceof FakePlayer)return;Bridge b=active;if(b==null||b.server!=p.getServer())return;
        Session s=b.sessions.remove(p.getUUID());if(s!=null){b.session(p.getUUID(),"SESSION_END",s);emit("SESSION_REASON",p.getUUID(),s.id(),b.disconnectReasons.getOrDefault(p.getUUID(),"UNKNOWN"));emit("PLAYER_LEAVE",p.getUUID(),s.id(),"");} b.clientWindows.remove(p.getUUID());b.surfaceWindows.remove(p.getUUID());b.disconnectReasons.remove(p.getUUID());
        b.capture(true);var current=b.snapshot.get();b.snapshot.set(new Snapshot(current.observedAt(),current.online(),current.uptimeSeconds(),current.players().stream().filter(id->!id.equals(p.getUUID())).toList(),current.maxPlayers(),current.droppedEvents()));
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){Bridge b=active;if(b==null||b.server!=event.getServer())return;
        b.sessions.forEach((mc,s)->{b.session(mc,"SESSION_END",s);emit("SESSION_REASON",mc,s.id(),"SERVER_STOP");emit("PLAYER_LEAVE",mc,s.id(),"");});b.sessions.clear();b.capture(false);active=null;
        b.worker.execute(()->{b.retryAfter=0;b.flush();b.retryAfter=0;b.flush();});b.worker.shutdown(); // No wait/join or HTTP on the stopping thread.
    }
}
