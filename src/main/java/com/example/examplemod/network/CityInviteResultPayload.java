package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * What actually happened to an invite the inviter just sent.
 *
 * <p>Previously the invite handler reported nothing at all: every refusal was a silent return, so the
 * inviter was left guessing. The server now answers with the outcome and the reason, and the client
 * renders it as an inline success line or as the exact refusal.
 */
public record CityInviteResultPayload(
        BlockPos pylonPos,
        boolean sent,
        String reasonKey,
        String targetName
) implements CustomPacketPayload {

    public static final Type<CityInviteResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "city_invite_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CityInviteResultPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CityInviteResultPayload::pylonPos,
                    ByteBufCodecs.BOOL, CityInviteResultPayload::sent,
                    ByteBufCodecs.STRING_UTF8, CityInviteResultPayload::reasonKey,
                    ByteBufCodecs.STRING_UTF8, CityInviteResultPayload::targetName,
                    CityInviteResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}