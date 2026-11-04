package com.example.examplemod.cargo.center;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.Consumer;
@EventBusSubscriber(modid="createcityexpansioncore",bus=EventBusSubscriber.Bus.MOD)
public final class CenterNetwork {
    public static Consumer<CompoundTag> CLIENT=data->{};
    private CenterNetwork(){}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){var r=event.registrar("1");
        r.playToServer(CenterRequest.TYPE,CenterRequest.CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)CenterService.handle(player,p.data());}));
        r.playToClient(CenterState.TYPE,CenterState.CODEC,(p,c)->c.enqueueWork(()->CLIENT.accept(p.data())));
    }
}
