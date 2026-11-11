package com.example.examplemod.economy;
import com.example.examplemod.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
@EventBusSubscriber(modid="createcityexpansioncore",bus=EventBusSubscriber.Bus.MOD)
public final class BankNetwork {
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){var r=event.registrar("1");r.playToServer(BankRequest.TYPE,BankRequest.STREAM_CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer s)BankService.handle(s,p);}));r.playToClient(BankState.TYPE,BankState.STREAM_CODEC,(p,c)->c.enqueueWork(()->com.example.examplemod.client.BankClient.accept(p)));}
}
