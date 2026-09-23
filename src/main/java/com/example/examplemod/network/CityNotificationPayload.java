package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record CityNotificationPayload(
        String title,
        String message
) implements CustomPacketPayload {

    public static final Type<CityNotificationPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            CreateCityExpansionCore.MODID,
                            "city_notification"
                    )
            );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            CityNotificationPayload
            > STREAM_CODEC =
            StreamCodec.composite(

                    ByteBufCodecs.STRING_UTF8,
                    CityNotificationPayload::title,

                    ByteBufCodecs.STRING_UTF8,
                    CityNotificationPayload::message,

                    CityNotificationPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}