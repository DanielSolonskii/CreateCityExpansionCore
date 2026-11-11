package com.example.examplemod.journey;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
@EventBusSubscriber(modid="createcityexpansioncore",bus=EventBusSubscriber.Bus.MOD)
public final class JourneyNetwork {
    public static java.util.function.Consumer<JourneyStatePayload> client=p->{};
    public static java.util.function.Consumer<JourneyEditorStatePayload> editor=p->{};
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
        var r=event.registrar("1");
        r.playToClient(JourneyStatePayload.TYPE,JourneyStatePayload.STREAM_CODEC,(p,c)->c.enqueueWork(()->client.accept(p)));
        r.playToServer(JourneyRequestPayload.TYPE,JourneyRequestPayload.STREAM_CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)JourneyServer.request(player,p);}));
        r.playToClient(JourneyEditorStatePayload.TYPE,JourneyEditorStatePayload.STREAM_CODEC,(p,c)->c.enqueueWork(()->editor.accept(p)));
        r.playToServer(JourneyEditorPayload.TYPE,JourneyEditorPayload.STREAM_CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)JourneyAuthoring.request(player,p);}));
    }
    private JourneyNetwork(){}
}
