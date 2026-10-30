package com.example.examplemod.event;

import com.example.examplemod.block.TransportRegistrationTerminalBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import java.util.ArrayList;
import java.util.List;

/** Placement hooks run before cancellable placement events; notify only after their rollback window. */
@EventBusSubscriber(modid="createcityexpansioncore")
public final class TerminalPlacementNotifications {
    private record Pending(ServerPlayer player, ServerLevel level,
                           TransportRegistrationTerminalBlockEntity terminal, Component message) {}
    private static final List<Pending> PENDING = new ArrayList<>();
    public static void enqueue(ServerPlayer player, TransportRegistrationTerminalBlockEntity terminal, Component message) {
        if (terminal.getLevel() instanceof ServerLevel level)
            PENDING.add(new Pending(player, level, terminal, message));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            var pending = iterator.next();
            if (pending.level().getServer() != event.getServer()) continue;
            iterator.remove();
            var terminal = pending.terminal();
            if (!pending.player().hasDisconnected() && !terminal.isRemoved()
                    && pending.level().hasChunkAt(terminal.getBlockPos())
                    && pending.level().getBlockEntity(terminal.getBlockPos()) == terminal)
                pending.player().sendSystemMessage(pending.message());
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        PENDING.removeIf(pending -> pending.level().getServer() == event.getServer());
    }
}
