package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;

public record CityIdentityResultPayload(UUID city, UUID requestId, String result) implements CustomPacketPayload {
    public static final Type<CityIdentityResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "city_identity_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityIdentityResultPayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> { b.writeUUID(p.city); b.writeUUID(p.requestId); b.writeUtf(p.result,32); }, b -> new CityIdentityResultPayload(b.readUUID(), b.readUUID(), b.readUtf(32)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
