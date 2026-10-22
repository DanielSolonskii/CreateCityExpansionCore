package com.example.examplemod.social;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.projects.*;
import com.example.examplemod.network.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** One bounded recipient-scoped push/second; project footprints refresh at most every five seconds. */
@EventBusSubscriber(modid="createcityexpansioncore")
public final class NearbySceneServer {
    private NearbySceneServer(){}
    private static final Map<MinecraftServer,Long> NEXT=new WeakHashMap<>();
    private record ProjectPoll(ServerPlayer connection,String scope,double x,double z,long next){}
    private static final Map<MinecraftServer,Map<UUID,ProjectPoll>> POLLS=new WeakHashMap<>();
    public static void preference(ServerPlayer player,boolean enabled){
        if(player instanceof FakePlayer)return;long now=net.minecraft.Util.getMillis();var budget=player.getPersistentData();long previous=budget.getLong("CCECNearbyPreference");
        if(!NearbyScenePolicy.preferenceReady(previous,now))return;budget.putLong("CCECNearbyPreference",now);
        SocialSavedData.get(player.getServer()).shareNearby(player.getUUID(),enabled);
        // The next regular pulse removes this player from every recipient without a stale cache.
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        var server=event.getServer();long now=net.minecraft.Util.getMillis();if(now<NEXT.getOrDefault(server,0L))return;NEXT.put(server,now+1000);
        var polls=POLLS.computeIfAbsent(server,s->new HashMap<>());polls.keySet().removeIf(id->server.getPlayerList().getPlayer(id)==null);
        var social=SocialSavedData.get(server);var cities=CityManager.get(server.overworld());var projects=EconomyManager.get(server.overworld()).projects();
        for(var viewer:server.getPlayerList().getPlayers()){
            if(viewer instanceof FakePlayer)continue;var dim=viewer.serverLevel().dimension().location();
            var friends=new ArrayList<NearbyScenePayload.Friend>();
            for(var id:social.book.contacts(viewer.getUUID())){
                var friend=server.getPlayerList().getPlayer(id);if(friend==null||friend==viewer)continue;
                double dx=friend.getX()-viewer.getX(),dz=friend.getZ()-viewer.getZ();
                if(NearbyScenePolicy.friend(social.book.relation(viewer.getUUID(),id)==SocialBook.Relation.FRIEND,social.sharesNearby(id),friend.serverLevel()==viewer.serverLevel(),friend.isSpectator(),dx*dx+dz*dz))friends.add(new NearbyScenePayload.Friend(id,friend.getX(),friend.getZ()));
            }
            friends.sort(Comparator.comparingDouble(f->Math.hypot(f.x()-viewer.getX(),f.z()-viewer.getZ())));
            if(friends.size()>NearbyScenePayload.MAX_FRIENDS)friends.subList(NearbyScenePayload.MAX_FRIENDS,friends.size()).clear();
            var city=cities.getMemberCity(viewer.getUUID());var actor=ProjectNetworkHandlers.actor(viewer,cities,city);
            String identity=dim+"/"+actor.city()+"/"+actor.op()+"/"+actor.admin();var cache=polls.get(viewer.getUUID());
            boolean refresh=cache==null||cache.connection!=viewer||now>=cache.next||!identity.equals(cache.scope)
                ||Math.hypot(viewer.getX()-cache.x,viewer.getZ()-cache.z)>16;
            List<NearbyScenePayload.Site> sites=List.of();
            if(refresh){polls.put(viewer.getUUID(),new ProjectPoll(viewer,identity,viewer.getX(),viewer.getZ(),now+5000));
            sites=projects.visible(actor,0).stream().filter(p->p.zone().dimension().equals(dim)&&NearbyScenePolicy.project(p.zone(),viewer.getX(),viewer.getZ()))
                .sorted(Comparator.comparingDouble((Project p)->p.zone().distance(viewer.getX(),viewer.getZ())).thenComparing(Project::id)).limit(NearbyScenePayload.MAX_PROJECTS)
                .map(p->new NearbyScenePayload.Site(p.id(),p.title(),p.scope(),p.status(),p.zone())).toList();}
            PacketDistributor.sendToPlayer(viewer,new NearbyScenePayload(dim,social.sharesNearby(viewer.getUUID()),friends,refresh,sites));
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){NEXT.remove(event.getServer());POLLS.remove(event.getServer());}
}
