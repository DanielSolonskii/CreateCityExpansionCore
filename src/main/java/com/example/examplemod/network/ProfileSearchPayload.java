package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record ProfileSearchPayload(UUID request, String query, int page) implements CustomPacketPayload {
    public ProfileSearchPayload {
        if (request == null || query == null || !query.matches("[A-Za-z0-9_]{0,16}") || page < 0 || page > 100000)
            throw new IllegalArgumentException("Invalid profile search");
    }
    public static final Type<ProfileSearchPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID,"profile_search"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ProfileSearchPayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> { b.writeUUID(p.request); b.writeUtf(p.query,16); b.writeVarInt(p.page); },
            b -> new ProfileSearchPayload(b.readUUID(),b.readUtf(16),b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
