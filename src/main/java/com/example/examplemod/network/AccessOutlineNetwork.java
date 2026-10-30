package com.example.examplemod.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid = "createcityexpansioncore", bus = EventBusSubscriber.Bus.MOD)
public final class AccessOutlineNetwork {
    public static java.util.function.Consumer<TransportOutlinePayload> transportClient = ignored -> {};
    public static java.util.function.Consumer<AccessOutlinePayload> client = ignored -> {};
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(TransportOutlinePayload.TYPE, TransportOutlinePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> transportClient.accept(payload)));
        event.registrar("1").playToClient(AccessOutlinePayload.TYPE, AccessOutlinePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> client.accept(payload)));
    }
}
