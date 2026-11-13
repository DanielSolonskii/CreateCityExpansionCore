package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-confirmed visual only: recipients cannot use this packet to paint blocks. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class PaintSprayFeedback {
    public static void send(ServerPlayer player, InteractionHand hand, Vec3 target, DyeColor color) {
        if (player.connection == null) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                new Spray(player.level().dimension().location(), player.getId(), hand, target, color.getId()));
    }

    public static final class Received extends Event {
        public final Level level;
        public final Spray spray;
        public Received(Level level, Spray spray) { this.level = level; this.spray = spray; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(Spray.TYPE, Spray.CODEC, (packet, context) -> context.enqueueWork(() -> {
            var level = context.player().level();
            if (level.dimension().location().equals(packet.dimension()))
                NeoForge.EVENT_BUS.post(new Received(level, packet));
        }));
    }

    public record Spray(ResourceLocation dimension, int playerId, InteractionHand hand, Vec3 target, int colorId)
            implements CustomPacketPayload {
        public static final Type<Spray> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CreateCityExpansionCore.MODID, "paint_spray"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Spray> CODEC = StreamCodec.of(
                (buf, p) -> {
                    buf.writeResourceLocation(p.dimension); buf.writeVarInt(p.playerId); buf.writeEnum(p.hand);
                    buf.writeDouble(p.target.x); buf.writeDouble(p.target.y); buf.writeDouble(p.target.z);
                    buf.writeVarInt(p.colorId);
                }, buf -> new Spray(buf.readResourceLocation(), buf.readVarInt(), buf.readEnum(InteractionHand.class),
                        new Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()),buf.readVarInt()));
        @Override public Type<Spray> type() { return TYPE; }
    }
}
