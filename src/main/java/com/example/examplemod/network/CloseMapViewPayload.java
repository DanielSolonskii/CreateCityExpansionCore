package com.example.examplemod.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CloseMapViewPayload(UUID viewId) implements CustomPacketPayload {

    public static final Type<CloseMapViewPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "close_map_view"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CloseMapViewPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeUUID(p.viewId); },
            b -> new CloseMapViewPayload(b.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
