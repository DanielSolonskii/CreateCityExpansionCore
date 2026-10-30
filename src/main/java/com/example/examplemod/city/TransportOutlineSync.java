package com.example.examplemod.city;

import com.example.examplemod.block.SimulatedCompat;
import com.example.examplemod.item.AccessControlRemoteItem;
import com.example.examplemod.network.TransportOutlinePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

@EventBusSubscriber(modid="createcityexpansioncore")
public final class TransportOutlineSync {
    private static final Map<ServerPlayer,TransportOutlinePayload> LAST=new WeakHashMap<>();
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event){
        if(!SimulatedCompat.isPresent()||!(event.getEntity() instanceof ServerPlayer player))return;
        boolean holding=player.getMainHandItem().getItem() instanceof AccessControlRemoteItem
                ||player.getOffhandItem().getItem() instanceof AccessControlRemoteItem;
        if(!holding){
            if(LAST.remove(player)!=null)PacketDistributor.sendToPlayer(player,new TransportOutlinePayload(player.serverLevel().dimension().location(),List.of()));
            return;
        }
        var snapshot=new TransportOutlinePayload(player.serverLevel().dimension().location(),
                com.example.examplemod.block.TransportOutlineServer.collect(player));
        if(!snapshot.equals(LAST.get(player))){LAST.put(player,snapshot);PacketDistributor.sendToPlayer(player,snapshot);}
    }
}
