package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record SyncCityMemberRolePayload(
        BlockPos pylonPos,
        UUID playerId,
        int roleId
) implements CustomPacketPayload {

    public static final Type<SyncCityMemberRolePayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            CreateCityExpansionCore.MODID,
                            "sync_city_member_role"
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
            SyncCityMemberRolePayload
            > STREAM_CODEC =
            StreamCodec.composite(

                    BlockPos.STREAM_CODEC,
                    SyncCityMemberRolePayload::pylonPos,

                    UUID_CODEC,
                    SyncCityMemberRolePayload::playerId,

                    ByteBufCodecs.VAR_INT,
                    SyncCityMemberRolePayload::roleId,

                    SyncCityMemberRolePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}