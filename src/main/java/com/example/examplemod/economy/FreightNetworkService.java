package com.example.examplemod.economy;

import com.example.examplemod.city.*;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.FreightNetworkRequest;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.*;

@EventBusSubscriber(modid="createcityexpansioncore")
public final class FreightNetworkService {
    private static final Map<MinecraftServer,Map<UUID,Long>> throttle=new WeakHashMap<>();
    private static final Map<MinecraftServer,Map<UUID,net.minecraft.nbt.CompoundTag>> results=new WeakHashMap<>();
    private FreightNetworkService(){}
    public static void appendResult(ServerPlayer player,net.minecraft.nbt.CompoundTag snapshot){var byPlayer=results.get(player.getServer());if(byPlayer!=null&&byPlayer.containsKey(player.getUUID()))snapshot.merge(byPlayer.get(player.getUUID()).copy());}
    public static void result(ServerPlayer player,UUID target,String result){var t=new net.minecraft.nbt.CompoundTag();t.putLong("MutationRevision",net.minecraft.Util.getMillis());if(target!=null)t.putUUID("MutationTarget",target);t.putString("MutationResult",result);results.computeIfAbsent(player.getServer(),k->new HashMap<>()).put(player.getUUID(),t);}
    public static void handle(ServerPlayer player,FreightNetworkRequest request){
        if(player instanceof net.neoforged.neoforge.common.util.FakePlayer)return;
        boolean local=player.containerMenu instanceof CityPylonMenu menu&&menu.getPylonPos().equals(request.pylon())&&player.distanceToSqr(request.pylon().getCenter())<=64;
        if(!local&&request.action()!=FreightNetworkRequest.Action.ROUTE){feedback(player,"pylon_required");return;}
        var access=EconomyNetworkHandlers.memberContext(player,request.pylon(),false);
        City city=access==null?null:access.city();
        if(city==null||!(request.action()==FreightNetworkRequest.Action.ROUTE?city.canBuild(player.getUUID()):city.isAdmin(player.getUUID()))){feedback(player,"permission");return;}
        long now=net.minecraft.Util.getMillis();var budget=throttle.computeIfAbsent(player.getServer(),k->new HashMap<>());
        if(now-budget.getOrDefault(player.getUUID(),0L)<180){feedback(player,"busy");return;}budget.put(player.getUUID(),now);
        var freight=EconomyManager.get(player.getServer().overworld()).freight();String result;
        if(request.action()==FreightNetworkRequest.Action.ROUTE)result=freight.transit.configure(player.serverLevel(),city,request.target(),request.hubs(),request.enabled());
        else {
            var point=freight.point(request.target());
            if(point==null||point.removed||!point.cityId.equals(city.getId())||!point.dimension.equals(player.serverLevel().dimension().location()))result="permission";
            else if(request.action()==FreightNetworkRequest.Action.KIND)result=freight.transit.kind(player.serverLevel(),point,request.enabled());
            else if(point.kind!=FreightPoint.Kind.HUB)result="hub_required";
            else {if(request.action()==FreightNetworkRequest.Action.PUBLIC)point.publicTransit=request.enabled();else point.forwarding=request.enabled();freight.markChanged();result="ok";}
        }
        result(player,request.target(),result);feedback(player,result);if(access!=null)EconomyNetworkHandlers.sendState(access);EconomyNetworkHandlers.pushOpenMenus(player.getServer());
    }
    private static void feedback(ServerPlayer p,String key){p.displayClientMessage(Component.translatable("gui.createcityexpansioncore.freight_network.result."+key),false);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){var m=throttle.get(p.getServer());if(m!=null)m.remove(p.getUUID());var r=results.get(p.getServer());if(r!=null)r.remove(p.getUUID());}}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){throttle.remove(e.getServer());results.remove(e.getServer());}
}
