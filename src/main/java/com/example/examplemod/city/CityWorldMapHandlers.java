package com.example.examplemod.city;

import com.example.examplemod.policy.CityMapReadAccess;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Public discovery for everyone; private operational data stays restricted to members. */
public final class CityWorldMapHandlers {
    private CityWorldMapHandlers() { }
    public static void openForMember(ServerPlayer player, com.example.examplemod.network.RequestCityWorldMapPayload request) {
        CityPublicAtlas.open(player, request);
    }
}
