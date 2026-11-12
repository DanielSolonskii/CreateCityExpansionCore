package com.example.examplemod.journey;

import com.example.examplemod.CreateCityExpansionCore;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.Util;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** A temporary pause for the author's real Journey progress while they walk a local draft. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class JourneyPlaytestNetwork {
    private static final long LEASE_MILLIS = 30_000;
    private static final Map<ServerPlayer, Lease> LEASES = new WeakHashMap<>();
    public static Consumer<Reply> client = reply -> {};

    private JourneyPlaytestNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("journey-playtest-1").optional();
        registrar.playToServer(Request.TYPE, Request.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer player) handle(player, packet);
        });
        registrar.playToClient(Reply.TYPE, Reply.CODEC, (packet, context) -> client.accept(packet));
    }

    public static boolean active(ServerPlayer player) {
        Lease lease = LEASES.get(player);
        if (lease == null) return false;
        if (lease.expiresAt() <= Util.getMillis() || !player.isAlive() || !player.hasPermissions(2)) {
            LEASES.remove(player);
            return false;
        }
        return true;
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) LEASES.remove(player);
    }

    private static void handle(ServerPlayer player, Request request) {
        if (!player.connection.hasChannel(Reply.TYPE)) return;
        Lease previous = active(player) ? LEASES.get(player) : null;
        boolean matching = previous != null && previous.id().equals(request.id());
        switch (request.action()) {
            case BEGIN -> {
                boolean accepted = player.hasPermissions(2) && player.isAlive() && (previous == null || matching);
                if (accepted) LEASES.put(player, new Lease(request.id(), Util.getMillis() + LEASE_MILLIS));
                PacketDistributor.sendToPlayer(player, new Reply(request.id(), accepted));
            }
            case KEEP_ALIVE -> {
                if (matching) LEASES.put(player, new Lease(request.id(), Util.getMillis() + LEASE_MILLIS));
                PacketDistributor.sendToPlayer(player, new Reply(request.id(), matching));
            }
            case END -> {
                if (matching) LEASES.remove(player);
            }
        }
    }

    private record Lease(UUID id, long expiresAt) {}

    public enum Action { BEGIN, KEEP_ALIVE, END }

    public record Request(UUID id, Action action) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CreateCityExpansionCore.MODID, "journey_playtest_request"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (buffer, request) -> {
                    buffer.writeUUID(request.id());
                    buffer.writeEnum(request.action());
                },
                buffer -> new Request(buffer.readUUID(), buffer.readEnum(Action.class)));

        @Override public Type<Request> type() { return TYPE; }
    }

    public record Reply(UUID id, boolean accepted) implements CustomPacketPayload {
        private static final Type<Reply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CreateCityExpansionCore.MODID, "journey_playtest_reply"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.of(
                (buffer, reply) -> {
                    buffer.writeUUID(reply.id());
                    buffer.writeBoolean(reply.accepted());
                },
                buffer -> new Reply(buffer.readUUID(), buffer.readBoolean()));

        @Override public Type<Reply> type() { return TYPE; }
    }
}
