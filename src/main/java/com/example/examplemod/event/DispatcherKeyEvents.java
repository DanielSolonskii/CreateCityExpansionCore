package com.example.examplemod.event;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.item.DispatcherKeyItem;
import com.simibubi.create.content.trains.station.StationBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Ensures the dispatcher key wins over Create Station's own block interaction. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class DispatcherKeyEvents {
    private DispatcherKeyEvents() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onStationUse(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)
                || !(event.getItemStack().getItem() instanceof DispatcherKeyItem)
                || !(level.getBlockState(event.getPos()).getBlock() instanceof StationBlock)) {
            return;
        }

        InteractionResult result = DispatcherKeyItem.useOnStation(level, player, event.getPos());
        if (result.consumesAction()) {
            event.setCanceled(true);
            event.setCancellationResult(result);
        }
    }
}
