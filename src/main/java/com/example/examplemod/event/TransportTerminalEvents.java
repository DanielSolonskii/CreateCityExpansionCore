package com.example.examplemod.event;

import com.example.examplemod.block.*;
import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid="createcityexpansioncore")
public final class TransportTerminalEvents {
    @SubscribeEvent public static void place(BlockEvent.EntityPlaceEvent event) {
        if (!SimulatedCompat.isPresent() || !(event.getLevel() instanceof ServerLevel level)
                || !event.getPlacedBlock().is(CreateCityExpansionCore.TRANSPORT_REGISTRATION_TERMINAL.get())) return;
        if (TransportTerminalPlacement.hasOtherTerminal(level, event.getPos())) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player)
                player.sendSystemMessage(Component.translatable("message.createcityexpansioncore.transport_terminal.placement_duplicate")
                        .withStyle(ChatFormatting.RED));
            return;
        }
        TransportTerminalPlacement.invalidate(level, event.getPos());
    }
    @SubscribeEvent public static void broken(BlockEvent.BreakEvent event) {
        if (SimulatedCompat.isPresent() && event.getLevel() instanceof ServerLevel level
                && event.getState().is(CreateCityExpansionCore.TRANSPORT_REGISTRATION_TERMINAL.get()))
            TransportTerminalPlacement.invalidate(level, event.getPos());
    }
}
