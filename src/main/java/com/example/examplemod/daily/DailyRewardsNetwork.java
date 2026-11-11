package com.example.examplemod.daily;
import com.example.examplemod.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
@EventBusSubscriber(modid="createcityexpansioncore",bus=EventBusSubscriber.Bus.MOD)
public final class DailyRewardsNetwork {
    private DailyRewardsNetwork(){}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){var r=event.registrar("1");
        r.playToServer(DailyRewardsRequest.TYPE,DailyRewardsRequest.STREAM_CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)DailyRewardsService.request(player,p);}));
        r.playToClient(DailyRewardsState.TYPE,DailyRewardsState.STREAM_CODEC,(p,c)->c.enqueueWork(()->com.example.examplemod.client.DailyRewardsClient.accept(p)));
    }
}
