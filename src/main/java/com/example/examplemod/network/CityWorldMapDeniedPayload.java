package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * The server's answer to {@link RequestCityWorldMapPayload} when the player
 * belongs to no city.
 *
 * <p><b>Why an empty packet instead of silence.</b> Silence is indistinguishable
 * from a slow load. Without this packet a player with no city who pressed M got a
 * screen reading "loading city territories" that never resolved and could not be
 * dismissed. Now the client can tell "no city" apart from "not yet", say so, and
 * let the player leave.
 *
 * <p><b>Why it carries nothing.</b> The pylon is never named here, exactly as in
 * the request: membership is resolved on the server and a denial carries no city
 * identity, so nothing here is attacker-controlled or worth verifying.
 */
public record CityWorldMapDeniedPayload() implements CustomPacketPayload {

    public static final Type<CityWorldMapDeniedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(
                    CreateCityExpansionCore.MODID, "city_world_map_denied"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CityWorldMapDeniedPayload>
            STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                // Полей нет: отказ не называет ни города, ни пилона, ни игрока.
            },
            buffer -> new CityWorldMapDeniedPayload()
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}