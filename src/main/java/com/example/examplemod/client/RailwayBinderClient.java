package com.example.examplemod.client;

import com.example.examplemod.passenger.RailwayBinder;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

@EventBusSubscriber(modid="createcityexpansioncore",value=Dist.CLIENT)
public final class RailwayBinderClient {
    private RailwayBinderClient() {}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void markCarriage(InputEvent.InteractionKeyMappingTriggered event) {
        var mc=Minecraft.getInstance();
        if(!event.isUseItem() || mc.player==null || mc.gameMode==null
                || !(mc.player.getItemInHand(event.getHand()).getItem() instanceof RailwayBinder)
                || RailwayBinder.aimedCarriage(mc.player)==null)return;
        mc.gameMode.useItem(mc.player,event.getHand());
        event.setSwingHand(true);
        event.setCanceled(true);
    }
}
