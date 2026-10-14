package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Outcome of founding; does not alter the existing creation request or saved data. */
public record CityCreationResultPayload(BlockPos pylon, String result) implements CustomPacketPayload {
    public static final Type<CityCreationResultPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "city_creation_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityCreationResultPayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> { b.writeBlockPos(p.pylon()); b.writeUtf(p.result(),32); },
            b -> new CityCreationResultPayload(b.readBlockPos(), b.readUtf(32)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
