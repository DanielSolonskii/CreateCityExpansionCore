package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
public record CreateCityPayload(
        String name,
        BlockPos pylonPos
) implements CustomPacketPayload {

    public static final Type<CreateCityPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            CreateCityExpansionCore.MODID,
                            "create_city"
                    )
            );

    public static final StreamCodec<ByteBuf, CreateCityPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8,
                    CreateCityPayload::name,
                    BlockPos.STREAM_CODEC,
                    CreateCityPayload::pylonPos,
                    CreateCityPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}