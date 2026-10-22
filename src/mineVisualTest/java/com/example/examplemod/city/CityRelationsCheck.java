package com.example.examplemod.city;

import com.example.examplemod.railway.OwnedRailwayNetwork;
import com.example.examplemod.railway.RailwayOwnershipTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public final class CityRelationsCheck {
    private static int checks;
    private static void require(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }

    private static void claimLimitChecks(){
        CityManager manager=new CityManager();UUID owner=UUID.randomUUID();
        City city=manager.createCity("Limit",owner,new BlockPos(0,64,0));
        var dim=net.minecraft.world.level.Level.OVERWORLD.location();
        for(int x=1;x<25;x++)require(manager.claimPlot(city,new CityPlot(dim,x,0),owner)==CityClaimResult.CLAIMED,"claims through 25 succeed");
        require(city.getClaimedPlotCount()==25,"pylon plot included in 25");
        require(manager.claimPlot(city,new CityPlot(dim,25,0),owner)==CityClaimResult.LIMIT_REACHED,"26th plot denied");
        require(manager.isPlotFree(new CityPlot(dim,25,0)),"denied plot stays free");
        require(manager.claimPlot(city,new CityPlot(dim,24,0),owner)==CityClaimResult.ALREADY_OWNED,"duplicate remains idempotent at cap");
        java.util.Set<CityPlot> legacy=new java.util.LinkedHashSet<>();for(int x=0;x<30;x++)legacy.add(new CityPlot(dim,x,0));
        city.replaceClaims(legacy);
        CityManager loaded=CityManager.load(manager.save(new CompoundTag(),null),null);City existing=loaded.getCity(city.getId());
        require(existing.getClaimedPlotCount()==30,"old oversized city preserved");
        require(loaded.claimPlot(existing,new CityPlot(dim,30,0),owner)==CityClaimResult.LIMIT_REACHED,"old oversized city cannot expand");
    }
    public static void main(String[] args) {
        CityManager manager = new CityManager();
        City a = manager.createCity("Alpha", UUID.randomUUID(), new BlockPos(0, 64, 0));
        City b = manager.createCity("Beta", UUID.randomUUID(), new BlockPos(100, 64, 0));
        require(manager.setTrusted(a.getId(), b.getId(), true), "one-way trust added");
        require(manager.trusts(a.getId(), b.getId()) && !manager.trusts(b.getId(), a.getId()), "trust is directional");
        require(CityAccessPolicy.relation(manager, a.getId(), b.getId()) == CityAccessPolicy.Relation.TRUSTED,
                "owner-to-requester relation");
        require(manager.setTrusted(b.getId(), a.getId(), true) && manager.partners(a.getId(), b.getId()), "mutual trust is partner");

        require(!manager.grantsTerritoryAccess(a.getId(),b.getId()),"partnership alone grants no land access");
        require(!manager.allowsTerritoryAction(a,b.getOwner(),true),"partner cannot build by default");
        require(!manager.setTerritoryAccess(a.getId(),b.getId(),b.getOwner(),true),"foreign owner cannot grant access");
        require(manager.setTerritoryAccess(a.getId(),b.getId(),a.getOwner(),true),"owner grants explicit access");
        require(manager.allowsTerritoryAction(a,b.getOwner(),true),"guest can build");
        require(manager.allowsTerritoryAction(a,b.getOwner(),false),"guest can interact");
        require(!manager.allowsTerritoryAction(b,a.getOwner(),true),"access remains directional");
        require(!manager.allowsTerritoryAction(a,UUID.randomUUID(),true),"outsider denied");
        UUID guestResident=UUID.randomUUID();b.addMember(guestResident,CityMember.Role.RESIDENT);
        require(manager.allowsTerritoryAction(a,guestResident,true),"explicit grant covers guest city residents");
        b.removeMember(guestResident);
        require(!manager.allowsTerritoryAction(a,guestResident,true),"leaving guest city revokes access immediately");
        CompoundTag saved = manager.save(new CompoundTag(), null);
        CityManager restored = CityManager.load(saved, null);
        require(restored.partners(a.getId(), b.getId()), "relations survive restart");
        require(restored.grantsTerritoryAccess(a.getId(),b.getId()),"land grant survives restart");
        CompoundTag old=saved.copy();old.remove("TerritoryAccess");
        require(!CityManager.load(old,null).grantsTerritoryAccess(a.getId(),b.getId()),"old saves default to closed");
        require(restored.setTerritoryAccess(a.getId(),b.getId(),a.getOwner(),false),"grant can be revoked");
        require(!restored.allowsTerritoryAction(restored.getCity(a.getId()),b.getOwner(),true),"revocation applies immediately");
        restored.setTerritoryAccess(a.getId(),b.getId(),a.getOwner(),true);
        restored.setTerritoryAccess(b.getId(),a.getId(),b.getOwner(),true);
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
        UUID resident = UUID.randomUUID();
        City oldCity = restored.getCity(a.getId());
        oldCity.addMember(resident, CityMember.Role.RESIDENT);
        require(restored.getCityByMember(resident) == oldCity, "resident belongs to old city");
        require(restored.removeCityByOwner(oldCity.getOwner()) == oldCity, "clearcity deletes owner city");
        require(restored.getCityByMember(resident) == null, "clearcity releases resident membership");
        require(!restored.grantsTerritoryAccess(b.getId(),a.getId()),"deleting a city removes incoming grants");
        claimLimitChecks();
        require(restored.getCityByMember(oldCity.getOwner()) == null, "clearcity releases owner membership");
        CityManager afterRestart = CityManager.load(restored.save(new CompoundTag(), null), null);
        require(afterRestart.getCityByMember(resident) == null, "deleted membership stays absent after restart");
        City destination = afterRestart.getCity(b.getId());
        require(CityInviteDirectory.stateOf(false, destination.hasMember(resident),
                destination.hasInvite(resident), afterRestart.getCityByMember(resident) != null)
                == com.example.examplemod.network.CityPlayerSearchResultPayload.State.AVAILABLE,
                "former resident is available for another city invitation");
        destination.addInvite(resident, destination.getOwner());
        destination.acceptInvite(resident);
        require(afterRestart.getCityByMember(resident) == destination, "former resident joins new city");
        System.out.println("PASS: " + checks + " city relation persistence and permission checks");
    }
}
