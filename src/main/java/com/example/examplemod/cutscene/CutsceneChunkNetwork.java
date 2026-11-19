package com.example.examplemod.cutscene;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.client.cutscene.CutscenePlayback;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.UUID;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class CutsceneChunkNetwork {
    private CutsceneChunkNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("cutscene-chunks-1").optional();
        registrar.playToServer(Request.TYPE, Request.CODEC, (request, context) -> {
            if (context.player() instanceof ServerPlayer player && player.connection.hasChannel(Accept.TYPE)) {
                CutsceneChunks.handle(player, request);
            }
        });
        registrar.playToClient(Accept.TYPE, Accept.CODEC, (packet, context) -> CutscenePlayback.acceptChunks(packet.session()));
    }

    static void accept(ServerPlayer player, UUID id) {
        PacketDistributor.sendToPlayer(player, new Accept(id));
    }

    public record Request(UUID session, int action, int tick, String json) implements CustomPacketPayload {
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CreateCityExpansionCore.MODID, "cutscene_chunk_request"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = new StreamCodec<>() {
            @Override
            public Request decode(RegistryFriendlyByteBuf buffer) {
                UUID session = buffer.readUUID();
                int action = buffer.readUnsignedByte();
                int tick = buffer.readVarInt();
                String json = new String(ByteBufCodecs.byteArray(CutsceneJson.MAX_JSON_BYTES).decode(buffer),
                        java.nio.charset.StandardCharsets.UTF_8);
                return new Request(session, action, tick, json);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Request packet) {
                buffer.writeUUID(packet.session());
                buffer.writeByte(packet.action());
                buffer.writeVarInt(packet.tick());
                ByteBufCodecs.byteArray(CutsceneJson.MAX_JSON_BYTES).encode(buffer,
                        packet.json().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        };

        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    public record Accept(UUID session) implements CustomPacketPayload {
        public static final Type<Accept> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(
                CreateCityExpansionCore.MODID, "cutscene_chunk_accept"));
        private static final StreamCodec<RegistryFriendlyByteBuf, Accept> CODEC = new StreamCodec<>() {
            @Override
            public Accept decode(RegistryFriendlyByteBuf buffer) {
                return new Accept(buffer.readUUID());
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buffer, Accept packet) {
                buffer.writeUUID(packet.session());
            }
        };

        @Override
        public Type<Accept> type() {
            return TYPE;
        }
    }
}
