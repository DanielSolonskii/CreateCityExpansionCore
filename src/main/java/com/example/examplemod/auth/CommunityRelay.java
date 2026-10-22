package com.example.examplemod.auth;
import com.example.examplemod.community.*;
import com.example.examplemod.social.*;
import com.google.gson.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
/** Isolated owner-notification channel. Existing read-only Companion snapshots are unchanged. */
@EventBusSubscriber(modid="createcityexpansioncore")
public final class CommunityRelay {
    private static Bridge active;private CommunityRelay(){}
    private record Notice(UUID id,UUID owner,UUID peer,String name,String kind,String text,UUID city,UUID token,long created,long after){}
    private record Ack(UUID id,String result){}
    private record Policy(UUID owner,boolean enabled,boolean preview,boolean offlineOnly,boolean online){}
    private record Batch(String serverId,List<Notice> notices,List<Ack> results,List<Policy> policies){}
    private static final class Bridge {
        final MinecraftServer server;final GameBackendClient client;final ScheduledExecutorService worker;final AtomicReference<Batch> snapshot=new AtomicReference<>();final List<Ack> results=new ArrayList<>();int ticks;
        Bridge(MinecraftServer server){this.server=server;client=new GameBackendClient(AuthenticationServerConfig.BACKEND.get(),System.getenv("CCEC_BACKEND_SECRET"));worker=Executors.newSingleThreadScheduledExecutor(r->{var t=new Thread(r,"CCEC community relay");t.setDaemon(true);return t;});}
        void capture(){var d=CommunityData.get(server);long now=System.currentTimeMillis();if(d.outbox.removeIf(n->n.created()<now-6*86400000L))d.setDirty();
            var notices=d.outbox.stream().limit(16).map(n->new Notice(n.id(),n.owner(),n.peer(),PlayerNames.name(server,n.peer()),n.kind(),n.text(),n.city(),n.token(),n.created(),n.after())).toList();
            snapshot.set(new Batch(AuthenticationServerConfig.SERVER_ID.get(),notices,List.copyOf(results.stream().limit(16).toList()),d.preferences.entrySet().stream().limit(1000).map(e->new Policy(e.getKey(),e.getValue().telegram(),e.getValue().preview(),e.getValue().offlineOnly(),server.getPlayerList().getPlayer(e.getKey())!=null)).toList()));
        }
        void flush(){var b=snapshot.get();if(b==null)return;try{var response=client.post("api/internal/game/community",b);var accepted=new HashSet<UUID>();for(var id:response.getAsJsonArray("accepted"))accepted.add(UUID.fromString(id.getAsString()));
                var commands=response.getAsJsonArray("commands");if(commands.size()>16)throw new IllegalStateException();
                server.execute(()->{var d=CommunityData.get(server);if(d.outbox.removeIf(n->accepted.contains(n.id())))d.setDirty();results.removeAll(b.results());
                    for(var raw:commands){var c=raw.getAsJsonObject();UUID id=UUID.fromString(c.get("id").getAsString());if(results.stream().anyMatch(a->a.id().equals(id)))continue;String result="invalid";
                        try{UUID actor=UUID.fromString(c.get("actor").getAsString()),target=UUID.fromString(c.get("target").getAsString());String kind=c.get("kind").getAsString();
                            if(kind.equals("REPLY")){var book=SocialSavedData.get(server).book;result=book.apply(actor,target,SocialBook.Action.SEND,book.revision(actor),c.get("text").getAsString(),0,System.currentTimeMillis(),id);if(result.equals("ok")){SocialSavedData.get(server).setDirty();var online=server.getPlayerList().getPlayer(target);if(online!=null)SocialService.notify(online,actor,"new_message");}}
                            else if(kind.equals("ACCEPT")||kind.equals("DECLINE"))result=CommunityService.respond(server,actor,target,UUID.fromString(c.get("token").getAsString()),kind.equals("ACCEPT"));
                            else if(kind.equals("MUTE")){var set=d.muted.computeIfAbsent(actor,u->new HashSet<>());if(set.size()<128||set.contains(target)){set.add(target);d.preferences.putIfAbsent(actor,d.preferences(actor));d.setDirty();result="ok";}else result="full";}
                        }catch(RuntimeException invalid){}
                        if(results.size()<128)results.add(new Ack(id,result));
                    }capture();
                });
            }catch(Exception unavailable){/* Persisted outbox and idempotent commands survive transient failures. No secrets/content are logged. */}}
    }
    @SubscribeEvent public static void started(ServerStartedEvent e){if(AuthenticationServerConfig.MODE.get()!=AuthenticationServerConfig.Mode.REQUIRED)return;try{var b=new Bridge(e.getServer());active=b;b.capture();b.worker.scheduleWithFixedDelay(b::flush,5,5,TimeUnit.SECONDS);}catch(RuntimeException disabled){active=null;}}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){var b=active;if(b!=null&&++b.ticks%100==0)b.capture();}
    @SubscribeEvent public static void stopped(ServerStoppingEvent e){var b=active;active=null;if(b!=null)b.worker.shutdownNow();}
}
