package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record CommissionMineProjectPayload(
        BlockPos controllerPos,
        UUID cityId,
        ResourceLocation depositId
) implements CustomPacketPayload {

    public static final Type<CommissionMineProjectPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID, "commission_mine_project"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, CommissionMineProjectPayload>
            STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buffer, payload.controllerPos());
                        buffer.writeUUID(payload.cityId());
                        buffer.writeResourceLocation(payload.depositId());
                    },
                    buffer -> new CommissionMineProjectPayload(
                            BlockPos.STREAM_CODEC.decode(buffer),
                            buffer.readUUID(),
                            buffer.readResourceLocation()
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
