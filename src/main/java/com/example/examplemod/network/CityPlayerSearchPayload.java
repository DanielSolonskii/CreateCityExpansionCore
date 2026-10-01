package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client asks the server which players it may invite, by nickname.
 *
 * <p>The client deliberately does not resolve this itself: an online-player list says nothing about
 * membership, permissions or pending invites, and an offline player the server already knows cannot
 * be discovered on the client at all.
 */
public record CityPlayerSearchPayload(
        BlockPos pylonPos,
        String query
) implements CustomPacketPayload {

    public static final Type<CityPlayerSearchPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "city_player_search"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CityPlayerSearchPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CityPlayerSearchPayload::pylonPos,
                    ByteBufCodecs.STRING_UTF8, CityPlayerSearchPayload::query,
                    CityPlayerSearchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}