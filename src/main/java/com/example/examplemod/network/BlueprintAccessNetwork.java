package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.client.screen.BlueprintTerminalScreen;
import com.example.examplemod.multiblock.BlueprintCatalog.Structure;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, bus = EventBusSubscriber.Bus.MOD)
public final class BlueprintAccessNetwork {
    private static final UUID CITY_UPDATE = new UUID(0L, 0L);

    private BlueprintAccessNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("2");
        registrar.playToServer(Request.TYPE, Request.CODEC, (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                sendAccess(player, payload);
            }
        }));
        registrar.playToClient(State.TYPE, State.STREAM_CODEC, (payload, context) ->
                context.enqueueWork(() -> BlueprintTerminalScreen.acceptAccess(payload)));
    }

    private static void sendAccess(ServerPlayer player, Request request) {
        var terminal = CreateCityExpansionCore.BLUEPRINT_TERMINAL.get();
        if (!player.getMainHandItem().is(terminal) && !player.getOffhandItem().is(terminal)) {
            return;
        }
        City city = CityManager.get(player.serverLevel().getServer().overworld()).getMemberCity(player.getUUID());
        PacketDistributor.sendToPlayer(player, accessState(request.id(), city));
    }

    public static void syncCity(MinecraftServer server, City city) {
        State state = accessState(CITY_UPDATE, city);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (city.hasMember(player.getUUID())) {
                PacketDistributor.sendToPlayer(player, state);
            }
        }
    }

    private static State accessState(UUID requestId, City city) {
        Map<Structure, Integer> tiers = new EnumMap<>(Structure.class);
        for (Structure structure : Structure.values()) {
            tiers.put(structure, city == null ? Tier.I.level() : city.getUnlockedBlueprintTier(structure));
        }
        return new State(requestId, tiers);
    }

    public record Request(UUID id) implements CustomPacketPayload {
        private static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CreateCityExpansionCore.MODID, "blueprint_access_request"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (buffer, value) -> buffer.writeUUID(value.id()), buffer -> new Request(buffer.readUUID()));

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    public record State(UUID requestId, Map<Structure, Integer> tiers) implements CustomPacketPayload {
        private static final Type<State> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CreateCityExpansionCore.MODID, "blueprint_access_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(State::encode, State::decode);

        public State {
            tiers = Map.copyOf(tiers);
        }

        public boolean isCityUpdate() {
            return CITY_UPDATE.equals(requestId);
        }

        public int unlockedLevel(Structure structure) {
            return tiers.getOrDefault(structure, 0);
        }

        private static void encode(RegistryFriendlyByteBuf buffer, State state) {
            buffer.writeUUID(state.requestId());
            for (Structure structure : Structure.values()) {
                buffer.writeByte(state.unlockedLevel(structure));
            }
        }

        private static State decode(RegistryFriendlyByteBuf buffer) {
            UUID requestId = buffer.readUUID();
            Map<Structure, Integer> tiers = new EnumMap<>(Structure.class);
            for (Structure structure : Structure.values()) {
                int level = buffer.readUnsignedByte();
                tiers.put(structure, level <= structure.maxTier().level() ? level : 0);
            }
            return new State(requestId, tiers);
        }

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }
}
