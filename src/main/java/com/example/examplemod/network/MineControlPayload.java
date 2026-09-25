package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.mine.MineControlCommand;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record MineControlPayload(
        BlockPos controllerPos,
        MineControlCommand command
) implements CustomPacketPayload {

    public static final Type<MineControlPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID,
                    "mine_control"
            )
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MineControlPayload>
            STREAM_CODEC = StreamCodec.of(
                    (buffer, payload) -> {
                        BlockPos.STREAM_CODEC.encode(buffer, payload.controllerPos());
                        buffer.writeEnum(payload.command());
                    },
                    buffer -> new MineControlPayload(
                            BlockPos.STREAM_CODEC.decode(buffer),
                            buffer.readEnum(MineControlCommand.class)
                    )
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
