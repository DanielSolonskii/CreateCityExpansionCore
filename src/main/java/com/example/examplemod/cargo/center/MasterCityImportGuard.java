package com.example.examplemod.cargo.center;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

/** A pending physical import cannot be removed by breaking its customs inventory. */
@EventBusSubscriber(modid="createcityexpansioncore")
public final class MasterCityImportGuard {
    private MasterCityImportGuard(){}
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event){if(event.getLevel().getBlockEntity(event.getPos()) instanceof MasterCityCustomsBufferBlockEntity buffer&&buffer.buffer().locked())event.setCanceled(true);}
    @SubscribeEvent public static void exploding(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event){event.getAffectedBlocks().removeIf(pos->event.getLevel().getBlockEntity(pos) instanceof MasterCityCustomsBufferBlockEntity buffer&&buffer.buffer().locked());}
}
