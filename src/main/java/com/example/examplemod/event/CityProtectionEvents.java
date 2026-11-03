package com.example.examplemod.event;

import com.example.examplemod.city.City;
import com.example.examplemod.city.CityProtectionManager;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.block.SimulatedCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public class CityProtectionEvents {

    /** Called only after a committed type change, never from cancellable Break/Place events. */
    public static void onBlockChanged(ServerLevel level, BlockPos pos, BlockState previous, BlockState next) {
        var overworld = level.getServer().overworld();
        if (overworld == null) return;
        CityManager cities = CityManager.get(overworld);
        if (!cities.hasBlockAccessGrants()) return;
        var previousId = BuiltInRegistries.BLOCK.getKey(previous.getBlock());
        var nextId = BuiltInRegistries.BLOCK.getKey(next.getBlock());
        cities.onBlockChanged(level.dimension().location(), pos, previousId, nextId);
        if (cities.hasTransportAccessGrants())
            SimulatedCompat.onBlockChanged(level, pos, previousId, nextId, cities);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {

        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        if (!CityProtectionManager.canModify(
                player.serverLevel(),
                player,
                event.getPos()
        )) {

            event.setCanceled(true);

            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§cЭта территория защищена городом."
                    )
            );
        }
    }
    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!CityProtectionManager.canModify(
                player.serverLevel(),
                player,
                event.getPos()
        )) {

            event.setCanceled(true);

            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§cЭта территория защищена городом."
                    )
            );
        }
    }
    @SubscribeEvent
    public static void onBlockInteract(
            net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event
    ) {

        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!CityProtectionManager.canInteract(
                player.serverLevel(),
                player,
                event.getPos()
        )) {

            event.setCanceled(true);

            player.sendSystemMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§cЭта территория защищена городом."
                    )
            );
        }
    }

}
