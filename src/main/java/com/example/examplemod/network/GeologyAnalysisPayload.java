package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.geology.GeologyRegionSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

public record GeologyAnalysisPayload(
        BlockPos position,
        GeologyRegionSnapshot region,
        int remainingUses
) implements CustomPacketPayload {

    public static final Type<GeologyAnalysisPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "geology_analysis"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, GeologyAnalysisPayload>
            STREAM_CODEC = StreamCodec.of(
                    GeologyAnalysisPayload::encode,
                    GeologyAnalysisPayload::decode
            );

    public GeologyAnalysisPayload {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(region, "region");
        if (remainingUses < 0) {
            throw new IllegalArgumentException("remainingUses cannot be negative");
        }
    }

    private static void encode(
            RegistryFriendlyByteBuf buffer,
            GeologyAnalysisPayload payload
    ) {
        BlockPos.STREAM_CODEC.encode(buffer, payload.position());
        GeologySnapshotCodecs.encodeRegion(buffer, payload.region());
        buffer.writeVarInt(payload.remainingUses());
    }

    private static GeologyAnalysisPayload decode(
            RegistryFriendlyByteBuf buffer
    ) {
        return new GeologyAnalysisPayload(
                BlockPos.STREAM_CODEC.decode(buffer),
                GeologySnapshotCodecs.decodeRegion(buffer),
                buffer.readVarInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
