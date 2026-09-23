package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CityInviteNotificationPayload(
        BlockPos pylonPos,
        String cityName,
        String ownerName
) implements CustomPacketPayload {

    public static final Type<CityInviteNotificationPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            CreateCityExpansionCore.MODID,
                            "city_invite_notification"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            CityInviteNotificationPayload
            > STREAM_CODEC =
            StreamCodec.composite(

                    BlockPos.STREAM_CODEC,
                    CityInviteNotificationPayload::pylonPos,

                    ByteBufCodecs.STRING_UTF8,
                    CityInviteNotificationPayload::cityName,

                    ByteBufCodecs.STRING_UTF8,
                    CityInviteNotificationPayload::ownerName,

                    CityInviteNotificationPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}