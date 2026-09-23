package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record RespondCityInvitePayload(
        BlockPos pylonPos,
        boolean accept
) implements CustomPacketPayload {

    public static final Type<RespondCityInvitePayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            CreateCityExpansionCore.MODID,
                            "respond_city_invite"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            RespondCityInvitePayload
            > STREAM_CODEC =
            StreamCodec.composite(

                    BlockPos.STREAM_CODEC,
                    RespondCityInvitePayload::pylonPos,

                    ByteBufCodecs.BOOL,
                    RespondCityInvitePayload::accept,

                    RespondCityInvitePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}