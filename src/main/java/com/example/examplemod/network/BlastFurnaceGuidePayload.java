package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record BlastFurnaceGuidePayload(BlockPos controllerPos) implements CustomPacketPayload {
    public static final Type<BlastFurnaceGuidePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "blast_furnace_guide"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlastFurnaceGuidePayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, BlastFurnaceGuidePayload::controllerPos,
                    BlastFurnaceGuidePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
