package com.example.examplemod.city;

import com.example.examplemod.railway.OwnedRailwayNetwork;
import com.example.examplemod.railway.RailwayOwnershipTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public final class CityRelationsCheck {
    private static int checks;
    private static void require(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }

    public static void main(String[] args) {
        CityManager manager = new CityManager();
        City a = manager.createCity("Alpha", UUID.randomUUID(), new BlockPos(0, 64, 0));
        City b = manager.createCity("Beta", UUID.randomUUID(), new BlockPos(100, 64, 0));
        require(manager.setTrusted(a.getId(), b.getId(), true), "one-way trust added");
        require(manager.trusts(a.getId(), b.getId()) && !manager.trusts(b.getId(), a.getId()), "trust is directional");
        require(CityAccessPolicy.relation(manager, a.getId(), b.getId()) == CityAccessPolicy.Relation.TRUSTED,
                "owner-to-requester relation");
        require(manager.setTrusted(b.getId(), a.getId(), true) && manager.partners(a.getId(), b.getId()), "mutual trust is partner");

        CompoundTag saved = manager.save(new CompoundTag(), null);
        CityManager restored = CityManager.load(saved, null);
        require(restored.partners(a.getId(), b.getId()), "relations survive restart");
        require(CityAccessPolicy.canAccess(restored, a.getId(), a.getId(), CityAccessPolicy.Service.FREIGHT, false),
                "own city always has access");
        require(CityAccessPolicy.canAccess(restored, a.getId(), b.getId(), CityAccessPolicy.Service.RAILWAY_PRIVATE, true),
                "trusted city can use opted-in private service");
        require(!CityAccessPolicy.canAccess(restored, a.getId(), UUID.randomUUID(), CityAccessPolicy.Service.SERVICE, true),
                "unknown city has no access");
        require(CityAccessPolicy.tariffBasisPoints(restored, a.getId(), b.getId()) == 10_000,
                "relations add no implicit tariff discount");
        require(CityAccessPolicy.tariffBasisPoints(restored, a.getId(), a.getId()) == 0,
                "own infrastructure remains free");

        OwnedRailwayNetwork privateNetwork = new OwnedRailwayNetwork(UUID.randomUUID(),
                RailwayOwnershipTypes.OwnerType.CITY, a.getId(), "Alpha Rail", RailwayOwnershipTypes.Access.PRIVATE, 100);
        require(com.example.examplemod.railway.RailwayRoutePlanner.allowsNetwork(privateNetwork,
                RailwayOwnershipTypes.OwnerType.CITY, b.getId(), restored), "trusted city passes private railway guard");
        restored.setTrusted(a.getId(), b.getId(), false);
        require(!com.example.examplemod.railway.RailwayRoutePlanner.allowsNetwork(privateNetwork,
                RailwayOwnershipTypes.OwnerType.CITY, b.getId(), restored), "revoked trust blocks private railway");
        System.out.println("PASS: " + checks + " city relation persistence and permission checks");
    }
}
