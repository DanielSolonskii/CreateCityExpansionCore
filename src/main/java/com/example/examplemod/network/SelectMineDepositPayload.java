package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SelectMineDepositPayload(
        BlockPos controllerPos,
        ResourceLocation depositId
) implements CustomPacketPayload {

    public static final Type<SelectMineDepositPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "select_mine_deposit"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, SelectMineDepositPayload>
            STREAM_CODEC = StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    SelectMineDepositPayload::controllerPos,
                    ResourceLocation.STREAM_CODEC,
                    SelectMineDepositPayload::depositId,
                    SelectMineDepositPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
