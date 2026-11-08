package ru.recreated.companion;

import com.example.examplemod.city.CityManager;
import com.example.examplemod.profile.ProfileSavedData;
import com.example.examplemod.auth.AuthenticationServerConfig;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minecraft.stats.Stats;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.fml.common.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Read-only bounded captures on the game thread; JSON and HTTP only on worker. */
@Mod("recreated_companion_bridge")
@EventBusSubscriber(modid="recreated_companion_bridge")
public final class CompanionBridge {
    private static ScheduledExecutorService worker;
    private static final AtomicReference<Map<String,Object>> latest=new AtomicReference<>();
    private static UUID boot;private static int ticks;
    public CompanionBridge(){}
    @SubscribeEvent public static void started(ServerStartedEvent e){
        try{
            String secret=System.getenv("CCEC_BACKEND_SECRET");
            URI base=URI.create(AuthenticationServerConfig.BACKEND.get());
            if(secret==null||secret.length()<32||base.getUserInfo()!=null||base.getQuery()!=null||base.getFragment()!=null||!"/".equals(base.getPath())&& !base.getPath().isEmpty()
                ||!("https".equals(base.getScheme())||"http".equals(base.getScheme())&&Set.of("127.0.0.1","localhost","[::1]").contains(base.getHost())))return;
            HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
            boot=UUID.randomUUID();ticks=0;
            worker=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"RECREATED companion bridge");t.setDaemon(true);return t;});
            worker.scheduleWithFixedDelay(()->{
                var capture=latest.getAndSet(null);if(capture==null)return;
                try{
                    var request=HttpRequest.newBuilder(base.resolve("/api/internal/game/companion")).timeout(Duration.ofSeconds(5)).header("X-Game-Service-Secret",secret).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(capture))).build();
                    var response=http.send(request,HttpResponse.BodyHandlers.discarding());
                    if(response.statusCode()!=200)System.err.println("RECREATED companion: snapshot deferred; next capture will retry.");
                }catch(Exception unavailable){if(unavailable instanceof InterruptedException)Thread.currentThread().interrupt();}
            },5,5,TimeUnit.SECONDS);
        }catch(RuntimeException optional){worker=null;}
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(worker==null||++ticks%600!=0)return;
        try{
            var cities=CityManager.get(e.getServer().overworld()).getCities();
            if(cities.size()>1000)return;
            List<Map<String,Object>> rows=new ArrayList<>();Set<UUID> players=new HashSet<>();
            for(var c:cities){
                if(c.getMembers().size()>1000)return;
                var members=c.getMembers().stream().map(m->{players.add(m.getPlayerId());return Map.of("minecraftUuid",m.getPlayerId(),"role",m.getRole().name());}).toList();
                if(players.size()>1000)return;
                rows.add(Map.of("id",c.getId(),"name",c.getName(),"level",c.getLevel(),"plots",c.getClaimedPlotCount(),"blocks",c.getTerritoryBlockCount(),"members",members));
            }
            e.getServer().getPlayerList().getPlayers().forEach(p->players.add(p.getUUID()));
            if(players.size()>1000)return;
            var profiles=ProfileSavedData.get(e.getServer());
            var about=players.stream().map(id->{
                Map<String,Object> row=new HashMap<>();row.put("minecraftUuid",id);row.put("about",profiles.settings(id).about());
                var online=e.getServer().getPlayerList().getPlayer(id);
                if(online!=null)row.put("playTicks",Math.max(0,online.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME))));
                // SkinRestorer can update textures after the login event. Read the
                // current online GameProfile; preserve the last Backend snapshot offline.
                String texture="";
                if(online!=null){
                    var property=online.getGameProfile().getProperties().get("textures").stream().findFirst().orElse(null);
                    texture=property==null?"":property.value();
                    row.put("skinUrl",""); // Explicit reset/default skin, not a missing offline update.
                }
                if(!texture.isEmpty()&&texture.length()<=16384)try{
                    var json=JsonParser.parseString(new String(Base64.getDecoder().decode(texture),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                    String url=json.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
                    if(url.startsWith("http://textures.minecraft.net/"))url="https://"+url.substring(7);
                    if(url.matches("https://textures\\.minecraft\\.net/texture/[0-9a-f]{32,64}"))row.put("skinUrl",url);
                }catch(RuntimeException unavailable){}
                return row;
            }).toList();
            latest.set(Map.of("serverId",AuthenticationServerConfig.SERVER_ID.get(),"bootId",boot,"observedAt",Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).toString(),"cities",List.copyOf(rows),"profiles",about));
        }catch(RuntimeException optional){/* Projection must never interrupt a server tick. */}
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){if(worker!=null)worker.shutdownNow();worker=null;latest.set(null);}
}
