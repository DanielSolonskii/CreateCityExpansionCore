package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record RemoveCityMemberPayload(
        BlockPos pylonPos,
        UUID targetPlayer
) implements CustomPacketPayload {

    public static final Type<RemoveCityMemberPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            CreateCityExpansionCore.MODID,
                            "remove_city_member"
                    )
            );

    private static final StreamCodec<
            RegistryFriendlyByteBuf,
            UUID
            > UUID_CODEC =
            new StreamCodec<>() {

                @Override
                public UUID decode(
                        RegistryFriendlyByteBuf buffer
                ) {
                    return buffer.readUUID();
                }

                @Override
                public void encode(
                        RegistryFriendlyByteBuf buffer,
                        UUID uuid
                ) {
                    buffer.writeUUID(uuid);
                }
            };

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            RemoveCityMemberPayload
            > STREAM_CODEC =
            StreamCodec.composite(

                    BlockPos.STREAM_CODEC,
                    RemoveCityMemberPayload::pylonPos,

                    UUID_CODEC,
                    RemoveCityMemberPayload::targetPlayer,

                    RemoveCityMemberPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}