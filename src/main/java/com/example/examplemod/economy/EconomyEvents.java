package com.example.examplemod.economy;

import com.example.examplemod.CreateCityExpansionCore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class EconomyEvents {
    private EconomyEvents() {}

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        EconomyManager.get(event.getServer().overworld()).tick(event.getServer().overworld());
    }
}
