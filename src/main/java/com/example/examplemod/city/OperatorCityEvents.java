package com.example.examplemod.city;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Refresh open member lists when operator status changes; permissions themselves are live. */
@EventBusSubscriber(modid="createcityexpansioncore")
public final class OperatorCityEvents {
    private static final Map<MinecraftServer,Set<UUID>> PREVIOUS=new WeakHashMap<>();
    private OperatorCityEvents() {}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server=event.getServer();if(server.overworld().getGameTime()%20!=0)return;
        var manager=CityManager.get(server.overworld());
        UUID cityId=manager.getOperatorCityId();var city=cityId==null?null:manager.getCity(cityId);
        var current=city==null?Set.<UUID>of():Set.copyOf(OperatorCityRoster.current(server).keySet());
        var previous=PREVIOUS.put(server,current);
        if(city==null||current.equals(previous))return;
        CityInviteDirectory.publishMembers(server,city);
        for(var player:server.getPlayerList().getPlayers()) {
            if(previous!=null&&previous.contains(player.getUUID())&&!current.contains(player.getUUID())
                    &&!city.hasPrimaryMember(player.getUUID())
                    &&player.containerMenu instanceof com.example.examplemod.menu.CityPylonMenu menu
                    &&player.level().dimension().location().equals(city.getPylonDimension())
                    &&menu.getPylonPos().equals(city.getPylonPos()))player.closeContainer();
        }
    }
}
