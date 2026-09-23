package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record ChangeCityRolePayload(
        BlockPos pylonPos,
        UUID targetPlayer,
        int roleId
) implements CustomPacketPayload {

    public static final Type<ChangeCityRolePayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            CreateCityExpansionCore.MODID,
                            "change_city_role"
                    )
            );

    /*
     * =========================================
     * UUID CODEC
     * =========================================
     */

    private static final StreamCodec<
            RegistryFriendlyByteBuf,
            UUID
            > UUID_CODEC = StreamCodec.of(
            (buf, uuid) -> buf.writeUUID(uuid),
            buf -> buf.readUUID()
    );

    /*
     * =========================================
     * PACKET CODEC
     * =========================================
     */

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            ChangeCityRolePayload
            > STREAM_CODEC = StreamCodec.composite(

            BlockPos.STREAM_CODEC,
            ChangeCityRolePayload::pylonPos,

            UUID_CODEC,
            ChangeCityRolePayload::targetPlayer,

            ByteBufCodecs.VAR_INT,
            ChangeCityRolePayload::roleId,

            ChangeCityRolePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}