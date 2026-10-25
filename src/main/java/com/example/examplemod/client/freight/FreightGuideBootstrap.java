package com.example.examplemod.client.freight;

import com.example.examplemod.economy.FreightGuideNetwork;
import net.createmod.ponder.foundation.PonderIndex;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.common.NeoForge;

@EventBusSubscriber(modid="createcityexpansioncore",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class FreightGuideBootstrap {
    private FreightGuideBootstrap() {}
    @SubscribeEvent public static void setup(FMLClientSetupEvent e) {
        e.enqueueWork(()->{
            FreightGuideNetwork.client=FreightGuideClient::accept;
            PonderIndex.addPlugin(new FreightPonderPlugin());
            NeoForge.EVENT_BUS.register(FreightGuideClient.class);
        });
    }
}
