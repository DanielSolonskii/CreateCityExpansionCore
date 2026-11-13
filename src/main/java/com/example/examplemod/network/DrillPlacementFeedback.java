package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.electrical.DrillUnitBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Client prediction and server rejections share the exact placement origin. No client classes on the server. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class DrillPlacementFeedback {
    private DrillPlacementFeedback() {}

    public static final class Failed extends Event {
        public final Level level;
        public final BlockPos origin;

        public Failed(Level level, BlockPos origin) {
            this.level = level;
            this.origin = origin.immutable();
        }
    }

    public static void failed(Level level, Player player, BlockPos origin) {
        NeoForge.EVENT_BUS.post(new Failed(level, origin));
        // Automation/test players may have no connection to receive a visual hint.
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            PacketDistributor.sendToPlayer(serverPlayer, new Failure(level.dimension().location(), origin));
        }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Failure.TYPE, Failure.CODEC, (payload, context) -> context.enqueueWork(() -> {
            Level level = context.player().level();
            if (level.dimension().location().equals(payload.dimension())) {
                NeoForge.EVENT_BUS.post(new Failed(level, payload.origin()));
            }
        }));
    }

    public record Failure(ResourceLocation dimension, BlockPos origin) implements CustomPacketPayload {
        public static final Type<Failure> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CreateCityExpansionCore.MODID, "drill_placement_failed"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Failure> CODEC = StreamCodec.of(
                (buffer, value) -> { buffer.writeResourceLocation(value.dimension()); buffer.writeBlockPos(value.origin()); },
                buffer -> new Failure(buffer.readResourceLocation(), buffer.readBlockPos()));
        @Override public Type<Failure> type() { return TYPE; }
    }

    @EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
    public static final class PlacementEvents {
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public static void placed(BlockEvent.EntityPlaceEvent event) {
            if (!(event.getEntity() instanceof ServerPlayer player)
                    || !(event.getPlacedBlock().getBlock() instanceof DrillUnitBlock)) return;
            BlockPos base = DrillUnitBlock.origin(event.getPos(), event.getPlacedBlock());
            // Check after all listeners and snapshot rollback, including later LOWEST listeners.
            player.server.tell(new TickTask(player.server.getTickCount(), () -> {
                if (event.isCanceled()) failed(player.level(), player, base);
            }));
        }
    }
}
