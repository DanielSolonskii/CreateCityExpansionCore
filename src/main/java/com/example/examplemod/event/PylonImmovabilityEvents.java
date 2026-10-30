package com.example.examplemod.event;

import com.example.examplemod.CreateCityExpansionCore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid="createcityexpansioncore")
public final class PylonImmovabilityEvents {
    @SubscribeEvent public static void breakBlock(BlockEvent.BreakEvent event) {
        if(event.getState().is(CreateCityExpansionCore.CITY_PYLON.get()))event.setCanceled(true);
    }
    @SubscribeEvent public static void wrench(PlayerInteractEvent.RightClickBlock event) {
        if(event.getLevel().getBlockState(event.getPos()).is(CreateCityExpansionCore.CITY_PYLON.get())
                && event.getItemStack().getItem() instanceof com.simibubi.create.content.equipment.wrench.WrenchItem) {
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        }
    }
}
