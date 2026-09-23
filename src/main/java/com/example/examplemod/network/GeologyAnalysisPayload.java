package com.example.examplemod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record GeologyAnalysisPayload(
        BlockPos position,
        int copper,
        int iron,
        int coal,
        int zinc,
        int remainingUses
) implements CustomPacketPayload {

    public static final Type<GeologyAnalysisPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            "createcityexpansioncore",
                            "geology_analysis"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, GeologyAnalysisPayload>
            STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    GeologyAnalysisPayload::position,

                    net.minecraft.network.codec.ByteBufCodecs.INT,
                    GeologyAnalysisPayload::copper,

                    net.minecraft.network.codec.ByteBufCodecs.INT,
                    GeologyAnalysisPayload::iron,

                    net.minecraft.network.codec.ByteBufCodecs.INT,
                    GeologyAnalysisPayload::coal,

                    net.minecraft.network.codec.ByteBufCodecs.INT,
                    GeologyAnalysisPayload::zinc,

                    net.minecraft.network.codec.ByteBufCodecs.INT,
                    GeologyAnalysisPayload::remainingUses,

                    GeologyAnalysisPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}