package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;

public record RequestCityDirectoryPayload(ResourceLocation dimension, UUID token, int offset) implements CustomPacketPayload {
    public static final UUID NEW = new UUID(0, 0);
    public static final Type<RequestCityDirectoryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "request_city_directory"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RequestCityDirectoryPayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> { b.writeResourceLocation(p.dimension); b.writeUUID(p.token); b.writeVarInt(p.offset); },
            b -> new RequestCityDirectoryPayload(b.readResourceLocation(), b.readUUID(), b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
