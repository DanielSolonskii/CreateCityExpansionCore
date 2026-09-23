package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record InvitePlayerPayload(
        BlockPos pylonPos,
        UUID targetPlayer
) implements CustomPacketPayload {

    public static final Type<InvitePlayerPayload> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(
                            CreateCityExpansionCore.MODID,
                            "invite_player"
                    )
            );

    private static final StreamCodec<
            RegistryFriendlyByteBuf,
            UUID
            > UUID_CODEC = StreamCodec.of(
            (buf, uuid) -> buf.writeUUID(uuid),
            buf -> buf.readUUID()
    );

    public static final StreamCodec<
            RegistryFriendlyByteBuf,
            InvitePlayerPayload
            > STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    InvitePlayerPayload::pylonPos,

                    UUID_CODEC,
                    InvitePlayerPayload::targetPlayer,

                    InvitePlayerPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}