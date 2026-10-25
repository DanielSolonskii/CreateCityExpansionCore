package com.example.examplemod.economy;

import com.example.examplemod.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@EventBusSubscriber(modid="createcityexpansioncore",bus=EventBusSubscriber.Bus.MOD)
public final class FreightGuideNetwork {
    private FreightGuideNetwork() {}
    /** Installed by the client initializer; dedicated servers never resolve renderer classes. */
    public static java.util.function.Consumer<FreightGuideState> client=ignored->{};
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var r=event.registrar("1");
        r.playToServer(FreightGuideRequest.TYPE,FreightGuideRequest.STREAM_CODEC,(p,c)->c.enqueueWork(()->{
            if(c.player() instanceof ServerPlayer player)FreightGuideService.handle(player,p);
        }));
        r.playToClient(FreightGuideState.TYPE,FreightGuideState.STREAM_CODEC,(p,c)->c.enqueueWork(()->client.accept(p)));
    }
}
