package com.example.examplemod.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MapPrivateStatePayload(UUID viewId, UUID requestId, CityPylonStatePayload state,
                                     CityRelationsStatePayload relations) implements CustomPacketPayload {
    public static final Type<MapPrivateStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","map_private_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,MapPrivateStatePayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> {
                b.writeUUID(p.viewId); b.writeUUID(p.requestId);
                CityPylonStatePayload.STREAM_CODEC.encode(b,p.state);
                CityRelationsStatePayload.STREAM_CODEC.encode(b,p.relations);
            },
            b -> new MapPrivateStatePayload(b.readUUID(), b.readUUID(),
                    CityPylonStatePayload.STREAM_CODEC.decode(b), CityRelationsStatePayload.STREAM_CODEC.decode(b)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
