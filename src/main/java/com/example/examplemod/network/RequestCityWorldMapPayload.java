package com.example.examplemod.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestCityWorldMapPayload(UUID viewId, UUID requestId) implements CustomPacketPayload {

    public static final Type<RequestCityWorldMapPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "request_city_world_map"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCityWorldMapPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeUUID(p.viewId); b.writeUUID(p.requestId); },
            b -> new RequestCityWorldMapPayload(b.readUUID(), b.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
