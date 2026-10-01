package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * The inviter learns how their invite ended.
 *
 * <p>Only the invitee was ever told anything before, so an accepted invite left the sender with no
 * confirmation at all. This is a plain, immediate, server-authoritative event: it is deliberately not
 * routed through the Problem Center, because an accepted invite is a success, not a problem.
 */
public record CityInviteOutcomePayload(
        UUID invitedPlayer,
        String invitedName,
        String cityName,
        boolean accepted
) implements CustomPacketPayload {

    public static final Type<CityInviteOutcomePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "city_invite_outcome"));

    /** Minecraft 1.21.1's ByteBufCodecs has no UUID entry, so the codec is declared here. */
    private static final StreamCodec<RegistryFriendlyByteBuf, UUID> UUID_CODEC =
            StreamCodec.of((buffer, uuid) -> buffer.writeUUID(uuid), buffer -> buffer.readUUID());

    public static final StreamCodec<RegistryFriendlyByteBuf, CityInviteOutcomePayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUID_CODEC, CityInviteOutcomePayload::invitedPlayer,
                    ByteBufCodecs.STRING_UTF8, CityInviteOutcomePayload::invitedName,
                    ByteBufCodecs.STRING_UTF8, CityInviteOutcomePayload::cityName,
                    ByteBufCodecs.BOOL, CityInviteOutcomePayload::accepted,
                    CityInviteOutcomePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}