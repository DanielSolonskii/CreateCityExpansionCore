package com.example.examplemod.citymap;

import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.map.CityMapFeatures;
import com.example.examplemod.passenger.PassengerService;
import com.example.examplemod.passenger.PassengerRailMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@EventBusSubscriber(modid = "createcityexpansioncore", bus = EventBusSubscriber.Bus.MOD)
public final class CityMapNetwork {
    public static final UUID ALL = new UUID(0, 0);
    public static final UUID OUTSIDE = new UUID(0, 1);
    public enum Action { LIST, SAVE, DELETE, REGISTER_STATION, BIND_PLATFORM, UNBIND_PLATFORM }

    public record Request(UUID nonce, UUID view, UUID world, ResourceLocation dimension, UUID city, Action action,
                          UUID id, long expected, CityMapFeature value, UUID platform) implements CustomPacketPayload {
        public Request(UUID nonce, UUID view, UUID world, ResourceLocation dimension, UUID city, Action action,
                       UUID id, long expected, CityMapFeature value) {
            this(nonce, view, world, dimension, city, action, id, expected, value, null);
        }
        public Request {
            if (expected < 0 || action == Action.LIST && expected != 0 ||
                    action != Action.LIST && (city.equals(ALL) || id.equals(ALL)) ||
                    (action == Action.BIND_PLATFORM || action == Action.UNBIND_PLATFORM) != (platform != null) ||
                    (action == Action.SAVE) != (value != null) || value != null &&
                    (!value.id().equals(id) || !value.city().equals(city) || !value.dimension().equals(dimension)
                            || value.revision() != expected)) throw new IllegalArgumentException("invalid");
        }

        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                "createcityexpansioncore", "city_cartography_request_v1"));
        static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of((buf, r) -> write(buf, r, false), buf -> read(buf, false));
        private static void write(RegistryFriendlyByteBuf buf, Request r, boolean modern) {
            buf.writeUUID(r.nonce);
            buf.writeUUID(r.view);
            buf.writeUUID(r.world);
            buf.writeUtf(r.dimension.toString(), 160);
            buf.writeUUID(r.city);
            buf.writeEnum(r.action);
            buf.writeUUID(r.id);
            buf.writeVarLong(r.expected);
            if (r.value != null) {
                if (modern) CityMapFeature.writeV2(buf, r.value);
                else CityMapFeature.write(buf, r.value);
            }
            if (modern && r.platform != null) buf.writeUUID(r.platform);
        }
        private static Request read(RegistryFriendlyByteBuf buf, boolean modern) {
            var nonce = buf.readUUID();
            var view = buf.readUUID();
            var world = buf.readUUID();
            var dim = ResourceLocation.parse(buf.readUtf(160));
            var city = buf.readUUID();
            var action = buf.readEnum(Action.class);
            var id = buf.readUUID();
            long expected = buf.readVarLong();
            var value = action != Action.SAVE ? null : modern ? CityMapFeature.readV2(buf) : CityMapFeature.read(buf);
            var platform = modern && (action == Action.BIND_PLATFORM || action == Action.UNBIND_PLATFORM) ? buf.readUUID() : null;
            return new Request(nonce, view, world, dim, city, action, id, expected, value, platform);
        }
        @Override public Type<Request> type() { return TYPE; }
    }

    public record Reply(UUID nonce, UUID city, String status, boolean canEdit,
                        List<CityMapFeature> features, List<PassengerService.Stop> platforms,
                        PassengerRailMap.Snapshot railMap) implements CustomPacketPayload {
        public Reply(UUID nonce, UUID city, String status, boolean canEdit, List<CityMapFeature> features) {
            this(nonce, city, status, canEdit, features, List.of(), PassengerRailMap.EMPTY);
        }
        public Reply(UUID nonce, UUID city, String status, boolean canEdit, List<CityMapFeature> features,
                     List<PassengerService.Stop> platforms) {
            this(nonce, city, status, canEdit, features, platforms, PassengerRailMap.EMPTY);
        }
        public Reply {
            features = List.copyOf(features);
            platforms = List.copyOf(platforms);
            if (features.size() > CityMapBook.MAX || platforms.size() > 96) throw new IllegalArgumentException("limit");
        }
        public static final Type<Reply> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                "createcityexpansioncore", "city_cartography_reply_v1"));
        static final StreamCodec<RegistryFriendlyByteBuf, Reply> CODEC = StreamCodec.of((buf, r) -> write(buf, r, false), buf -> read(buf, false));
        private static void write(RegistryFriendlyByteBuf buf, Reply r, boolean modern) {
            buf.writeUUID(r.nonce);
            buf.writeUUID(r.city);
            buf.writeUtf(r.status, 120);
            buf.writeBoolean(r.canEdit);
            buf.writeVarInt(r.features.size());
            if (!modern) {
                var visible = new HashMap<UUID, CityMapFeature>();
                r.features.forEach(f -> visible.put(f.id(), f));
                r.features.forEach(f -> CityMapFeature.writeLegacyRow(buf, f, visible));
                return;
            }
            r.features.forEach(f -> CityMapFeature.writeV2(buf, f));
            buf.writeVarInt(r.platforms.size());
            for (var stop : r.platforms) {
                buf.writeUUID(stop.id());
                buf.writeUtf(stop.name(), 96);
                buf.writeUtf(stop.dimension().toString(), 160);
                buf.writeBlockPos(stop.entrance());
                buf.writeBlockPos(stop.stationPos());
                buf.writeNullable(stop.feature(), (b, id) -> b.writeUUID(id));
            }
            PassengerRailMap.write(buf, r.railMap);
        }
        private static Reply read(RegistryFriendlyByteBuf buf, boolean modern) {
            var nonce = buf.readUUID();
            var city = buf.readUUID();
            var status = buf.readUtf(120);
            boolean canEdit = buf.readBoolean();
            int count = buf.readVarInt();
            if (count < 0 || count > CityMapBook.MAX) throw new IllegalArgumentException("limit");
            var features = new ArrayList<CityMapFeature>(count);
            for (int i = 0; i < count; i++) features.add(modern ? CityMapFeature.readV2(buf) : CityMapFeature.read(buf));
            if (!modern) return new Reply(nonce, city, status, canEdit, features);
            int stops = buf.readVarInt();
            if (stops < 0 || stops > 96) throw new IllegalArgumentException("limit");
            var platforms = new ArrayList<PassengerService.Stop>(stops);
            for (int i = 0; i < stops; i++) platforms.add(new PassengerService.Stop(buf.readUUID(), buf.readUtf(96),
                    ResourceLocation.parse(buf.readUtf(160)), buf.readBlockPos(), buf.readBlockPos(), buf.readNullable(b -> b.readUUID())));
            return new Reply(nonce, city, status, canEdit, features, platforms, PassengerRailMap.read(buf));
        }
        @Override public Type<Reply> type() { return TYPE; }
    }

    public record RequestV2(Request value) implements CustomPacketPayload {
        public static final Type<RequestV2> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "city_cartography_request_v2"));
        static final StreamCodec<RegistryFriendlyByteBuf, RequestV2> CODEC = StreamCodec.of(
                (buf, packet) -> Request.write(buf, packet.value, true), buf -> new RequestV2(Request.read(buf, true)));
        @Override public Type<RequestV2> type() { return TYPE; }
    }

    public record ReplyV2(Reply value) implements CustomPacketPayload {
        public static final Type<ReplyV2> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "city_cartography_reply_v2"));
        static final StreamCodec<RegistryFriendlyByteBuf, ReplyV2> CODEC = StreamCodec.of(
                (buf, packet) -> Reply.write(buf, packet.value, true), buf -> new ReplyV2(Reply.read(buf, true)));
        @Override public Type<ReplyV2> type() { return TYPE; }
    }

    public record RequestV3(Request value) implements CustomPacketPayload {
        public static final Type<RequestV3> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "city_cartography_request_v3"));
        static final StreamCodec<RegistryFriendlyByteBuf, RequestV3> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    Request.write(buf, packet.value, true);
                    if (packet.value.value != null) buf.writeEnum(packet.value.value.icon());
                }, buf -> {
                    var r = Request.read(buf, true);
                    if (r.value != null) r = new Request(r.nonce, r.view, r.world, r.dimension, r.city, r.action, r.id, r.expected,
                            r.value.withIcon(buf.readEnum(CityMapFeature.Icon.class)), r.platform);
                    return new RequestV3(r);
                });
        @Override public Type<RequestV3> type() { return TYPE; }
    }

    public record ReplyV3(Reply value) implements CustomPacketPayload {
        public static final Type<ReplyV3> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "city_cartography_reply_v3"));
        static final StreamCodec<RegistryFriendlyByteBuf, ReplyV3> CODEC = StreamCodec.of(
                (buf, packet) -> {
                    Reply.write(buf, packet.value, true);
                    for (var f : packet.value.features) buf.writeEnum(f.icon());
                }, buf -> {
                    var r = Reply.read(buf, true);
                    var features = r.features.stream().map(f -> f.withIcon(buf.readEnum(CityMapFeature.Icon.class))).toList();
                    return new ReplyV3(new Reply(r.nonce, r.city, r.status, r.canEdit, features, r.platforms, r.railMap));
                });
        @Override public Type<ReplyV3> type() { return TYPE; }
    }

    public static CustomPacketPayload outbound(Request request, boolean modern) {
        if (modern) return new RequestV2(request);
        if (request.platform != null || request.value != null && !request.value.links().equals(CityMapFeature.Links.EMPTY))
            throw new IllegalArgumentException("unavailable");
        return request;
    }

    @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
        var registry = event.registrar("city-cartography-1").optional();
        registry.playToServer(Request.TYPE, Request.CODEC, (packet, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer actor && actor.connection.hasChannel(Reply.TYPE))
                PacketDistributor.sendToPlayer(actor, CityMapService.handle(actor, packet, false));
        }));
        registry.playToClient(Reply.TYPE, Reply.CODEC, (packet, ctx) -> ctx.enqueueWork(() -> {
            if (CityPublicDirectory.currentConnection(ctx.connection())) CityMapFeatures.receive(packet);
        }));
        registry.playToServer(RequestV2.TYPE, RequestV2.CODEC, (packet, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer actor && actor.connection.hasChannel(ReplyV2.TYPE))
                PacketDistributor.sendToPlayer(actor, new ReplyV2(CityMapService.handle(actor, packet.value(), true)));
        }));
        registry.playToClient(ReplyV2.TYPE, ReplyV2.CODEC, (packet, ctx) -> ctx.enqueueWork(() -> {
            if (CityPublicDirectory.currentConnection(ctx.connection())) CityMapFeatures.receive(packet.value());
        }));
        registry.playToServer(RequestV3.TYPE, RequestV3.CODEC, (packet, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer actor && actor.connection.hasChannel(ReplyV3.TYPE))
                PacketDistributor.sendToPlayer(actor, new ReplyV3(CityMapService.handle(actor, packet.value(), true, true)));
        }));
        registry.playToClient(ReplyV3.TYPE, ReplyV3.CODEC, (packet, ctx) -> ctx.enqueueWork(() -> {
            if (CityPublicDirectory.currentConnection(ctx.connection())) CityMapFeatures.receive(packet.value());
        }));
    }
    private CityMapNetwork() {}
}
