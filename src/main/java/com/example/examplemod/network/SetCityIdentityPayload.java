package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.CityIdentity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;

public record SetCityIdentityPayload(UUID city, UUID requestId, long revision, int colour, String description, String pixels) implements CustomPacketPayload {
    public static final Type<SetCityIdentityPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "set_city_identity"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SetCityIdentityPayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> { b.writeUUID(p.city); b.writeUUID(p.requestId); b.writeVarLong(p.revision); b.writeInt(p.colour); b.writeUtf(p.description,CityIdentity.MAX_DESCRIPTION); b.writeUtf(p.pixels,CityIdentity.PIXELS); },
            b -> new SetCityIdentityPayload(b.readUUID(), b.readUUID(), b.readVarLong(), b.readInt(), b.readUtf(CityIdentity.MAX_DESCRIPTION), b.readUtf(CityIdentity.PIXELS)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
