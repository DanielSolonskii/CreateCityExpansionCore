package com.example.examplemod.community;
import com.example.examplemod.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
@EventBusSubscriber(modid="createcityexpansioncore",bus=EventBusSubscriber.Bus.MOD)
public final class WorkspaceNetwork {
    private WorkspaceNetwork(){}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){var r=event.registrar("1");r.playToServer(WorkspaceRequest.TYPE,WorkspaceRequest.STREAM_CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)ProjectWorkspaceService.handle(player,p);}));r.playToClient(WorkspaceState.TYPE,WorkspaceState.STREAM_CODEC,(p,c)->c.enqueueWork(()->com.example.examplemod.client.screen.ProjectWorkspaceClient.accept(p)));}
}
