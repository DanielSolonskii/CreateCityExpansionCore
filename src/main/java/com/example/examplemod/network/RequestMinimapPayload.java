package com.example.examplemod.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RequestMinimapPayload(UUID token, int offset, UUID viewId, UUID requestId) implements CustomPacketPayload {
    public RequestMinimapPayload(UUID token, int offset) { this(token, offset, UUID.randomUUID(), UUID.randomUUID()); }
    public static final Type<RequestMinimapPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "request_minimap"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestMinimapPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeUUID(p.token); b.writeVarInt(p.offset); b.writeUUID(p.viewId); b.writeUUID(p.requestId); },
            b -> new RequestMinimapPayload(b.readUUID(), b.readVarInt(), b.readUUID(), b.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
