package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CokeBatteryGuidePayload(BlockPos controllerPos) implements CustomPacketPayload {
    public static final Type<CokeBatteryGuidePayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "coke_battery_guide")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, CokeBatteryGuidePayload> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, CokeBatteryGuidePayload::controllerPos,
                    CokeBatteryGuidePayload::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
