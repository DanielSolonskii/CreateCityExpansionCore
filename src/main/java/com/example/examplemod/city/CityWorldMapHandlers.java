package com.example.examplemod.city;

import com.example.examplemod.policy.CityMapReadAccess;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Public discovery for everyone; private operational data stays restricted to members. */
public final class CityWorldMapHandlers {
    private CityWorldMapHandlers() { }
    public static boolean openForMember(ServerPlayer player) {
        CityManager manager = CityManager.get(player.getServer().overworld());
        City city = manager.getMemberCity(player.getUUID());
        CityPublicAtlas.open(player);
        boolean privateAccess = CityMapReadAccess.allowed(new CityMapReadAccess.Facts(city != null, city != null && city.hasMember(player.getUUID())));
        if (!privateAccess) return true;
        var publicView = CityPublicAtlas.view(player);
        if (publicView == null || !publicView.dimension().equals(city.getPylonDimension())) return true;
        PacketDistributor.sendToPlayer(player, CityProjectNetworkHandlers.createState(player.getServer(), city, player));
        com.example.examplemod.railway.RailwayNetworkHandlers.sendMemberView(player);
        com.example.examplemod.economy.EconomyNetworkHandlers.sendMemberView(player);
        CityRelationsNetworkHandlers.sendMemberView(player);
        CityTerritoryHandlers.sendTerritories(player, city);
        return true;
    }
}
