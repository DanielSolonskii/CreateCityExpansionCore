package com.example.examplemod.client;

import com.example.examplemod.block.SimulatedCompat;
import com.example.examplemod.item.AccessControlRemoteItem;
import com.example.examplemod.network.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;

/** This subscriber has no Sable types and remains loadable without Simulated. */
@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class TransportOutlineClient {
    private static TransportOutlinePayload snapshot;
    private static Object world;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        AccessOutlineNetwork.transportClient=payload->{
            var level=Minecraft.getInstance().level;
            if(level!=null&&level.dimension().location().equals(payload.dimension())){world=level;snapshot=payload;}
        };
        var level=Minecraft.getInstance().level;
        if(world!=level){world=level;snapshot=null;}
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){world=null;snapshot=null;}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS||!SimulatedCompat.isPresent())return;
        var mc=Minecraft.getInstance();
        if(mc.level==null||mc.level!=world||mc.player==null||snapshot==null||mc.options.hideGui)return;
        if(!(mc.player.getMainHandItem().getItem() instanceof AccessControlRemoteItem)
                &&!(mc.player.getOffhandItem().getItem() instanceof AccessControlRemoteItem))return;
        SableOutlineRenderer.render(event,snapshot);
    }
}
