package com.example.examplemod.economy;

import com.example.examplemod.network.FreightNetworkRequest;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid="createcityexpansioncore",bus=EventBusSubscriber.Bus.MOD)
public final class FreightRegionalNetwork {
    private FreightRegionalNetwork(){}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToServer(FreightNetworkRequest.TYPE,FreightNetworkRequest.STREAM_CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer s)FreightNetworkService.handle(s,p);}));}
}
