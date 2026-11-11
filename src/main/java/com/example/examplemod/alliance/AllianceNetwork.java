package com.example.examplemod.alliance;
import com.example.examplemod.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
@EventBusSubscriber(modid="createcityexpansioncore",bus=EventBusSubscriber.Bus.MOD)
public final class AllianceNetwork {
    private AllianceNetwork(){}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
        var r=event.registrar("1");
        r.playToServer(AllianceRequestPayload.TYPE,AllianceRequestPayload.STREAM_CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)AllianceService.handle(player,p);}));
        r.playToClient(AllianceStatePayload.TYPE,AllianceStatePayload.STREAM_CODEC,(p,c)->c.enqueueWork(()->com.example.examplemod.client.AllianceClient.accept(p)));
    }
}
