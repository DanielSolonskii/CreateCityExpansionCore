package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record SyncCityMemberRemovalPayload(
        BlockPos pylonPos,
        UUID playerId
) implements CustomPacketPayload {

    public static final Type<SyncCityMemberRemovalPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            CreateCityExpansionCore.MODID,
                            "sync_city_member_removal"
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
            SyncCityMemberRemovalPayload
            > STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    SyncCityMemberRemovalPayload::pylonPos,

                    UUID_CODEC,
                    SyncCityMemberRemovalPayload::playerId,

                    SyncCityMemberRemovalPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}