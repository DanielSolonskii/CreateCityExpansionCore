package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;
import java.util.UUID;

public record MineCommissionContextPayload(
        BlockPos controllerPos,
        Optional<UUID> cityId,
        String cityName,
        boolean allowed,
        String message
) implements CustomPacketPayload {

    public static final Type<MineCommissionContextPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID, "mine_commission_context"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MineCommissionContextPayload>
            STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buffer, payload.controllerPos());
                        buffer.writeBoolean(payload.cityId().isPresent());
                        payload.cityId().ifPresent(buffer::writeUUID);
                        buffer.writeUtf(payload.cityName(), 128);
                        buffer.writeBoolean(payload.allowed());
                        buffer.writeUtf(payload.message(), 256);
                    },
                    buffer -> new MineCommissionContextPayload(
                            BlockPos.STREAM_CODEC.decode(buffer),
                            buffer.readBoolean() ? Optional.of(buffer.readUUID()) : Optional.empty(),
                            buffer.readUtf(128),
                            buffer.readBoolean(),
                            buffer.readUtf(256)
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
