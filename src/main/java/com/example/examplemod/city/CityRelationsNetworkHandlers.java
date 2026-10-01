package com.example.examplemod.city;

import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.CityRelationSnapshot;
import com.example.examplemod.network.CityRelationsCommandPayload;
import com.example.examplemod.network.CityRelationsStatePayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;

public final class CityRelationsNetworkHandlers {
    private CityRelationsNetworkHandlers() {}

    public static void handle(ServerPlayer player, CityRelationsCommandPayload payload) {
        if (!(player.containerMenu instanceof CityPylonMenu menu) || !menu.getPylonPos().equals(payload.pylonPos())
                || !player.level().dimension().location().equals(menu.getDimensionId())
                || player.distanceToSqr(payload.pylonPos().getCenter()) > 64.0D) return;
        CityManager manager = CityManager.get(player.serverLevel());
        City city = manager.getCityByPylon(player.serverLevel().dimension().location(), payload.pylonPos());
        if (city == null || !city.hasMember(player.getUUID())) return;
        if (payload.action() != CityRelationsCommandPayload.Action.REQUEST_STATE) {
            if (!city.isAdmin(player.getUUID()) || payload.targetCityId() == null) return;
            manager.setTrusted(city.getId(), payload.targetCityId(),
                    payload.action() == CityRelationsCommandPayload.Action.ADD_TRUST);
        }
        sendState(player, manager, city);
    }

    private static void sendState(ServerPlayer player, CityManager manager, City city) {
        var snapshots = manager.getCities().stream().filter(other -> !other.getId().equals(city.getId()))
                .sorted(Comparator.comparing(City::getName, String.CASE_INSENSITIVE_ORDER))
                .map(other -> new CityRelationSnapshot(other.getId(), other.getName(), status(manager, city, other)))
                .toList();
        PacketDistributor.sendToPlayer(player, new CityRelationsStatePayload(city.getPylonPos(), city.getId(),
                city.isAdmin(player.getUUID()), snapshots));
    }

    private static CityRelationSnapshot.Status status(CityManager manager, City city, City other) {
        boolean ours = manager.trusts(city.getId(), other.getId());
        boolean theirs = manager.trusts(other.getId(), city.getId());
        if (ours && theirs) return CityRelationSnapshot.Status.PARTNER;
        if (ours) return CityRelationSnapshot.Status.TRUSTED;
        return theirs ? CityRelationSnapshot.Status.TRUSTS_US : CityRelationSnapshot.Status.NONE;
    }
}
