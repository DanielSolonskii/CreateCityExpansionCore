package com.example.examplemod.event;

import com.example.examplemod.city.City;
import com.example.examplemod.city.CityProtectionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public class CityProtectionEvents {

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