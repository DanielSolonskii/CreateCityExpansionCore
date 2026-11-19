package com.example.examplemod.community;
import com.example.examplemod.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
@EventBusSubscriber(modid="createcityexpansioncore",bus=EventBusSubscriber.Bus.MOD)
public final class WorkspaceNetwork {
    private WorkspaceNetwork(){}
    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("project-plan-1").optional().playToServer(ProjectPlanRequest.TYPE,ProjectPlanRequest.CODEC,(payload,ctx)->ctx.enqueueWork(()->{if(ctx.player() instanceof ServerPlayer player)ProjectPlanService.handle(player,payload);}));var r=event.registrar("1");r.playToServer(WorkspaceRequest.TYPE,WorkspaceRequest.STREAM_CODEC,(p,c)->c.enqueueWork(()->{if(c.player() instanceof ServerPlayer player)ProjectWorkspaceService.handle(player,p);}));r.playToClient(WorkspaceState.TYPE,WorkspaceState.STREAM_CODEC,(p,c)->c.enqueueWork(()->{if(com.example.examplemod.client.CityPublicDirectory.currentConnection(c.connection()))com.example.examplemod.client.screen.ProjectWorkspaceClient.accept(p);}));}
}
