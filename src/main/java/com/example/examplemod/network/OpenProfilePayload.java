package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Server-resolved UUID. Actual public profile is still fetched through ProfileService. */
public record OpenProfilePayload(UUID player) implements CustomPacketPayload {
    public static final Type<OpenProfilePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID,"open_profile"));
    public static final StreamCodec<RegistryFriendlyByteBuf,OpenProfilePayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> b.writeUUID(p.player), b -> new OpenProfilePayload(b.readUUID()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
