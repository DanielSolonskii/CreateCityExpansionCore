package com.example.examplemod.client.map;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = "createcityexpansioncore", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
final class TerrainResourceReload {
    @SubscribeEvent static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) manager -> TerrainCache.reloadTextures());
    }
}
