package com.example.examplemod.railway;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public final class RailwayOwnershipCheck {
    private static int checks;
    private static void require(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    @SuppressWarnings("unchecked") private static <T> Map<UUID,T> map(RailwayManager manager,String field)throws Exception{var f=RailwayManager.class.getDeclaredField(field);f.setAccessible(true);return(Map<UUID,T>)f.get(manager);}
    public static void main(String[] args)throws Exception{
        RailwayManager manager=new RailwayManager();UUID owner=UUID.randomUUID(),networkId=UUID.randomUUID(),sectionId=UUID.randomUUID(),graph=UUID.randomUUID();
        OwnedRailwayNetwork network=new OwnedRailwayNetwork(networkId,RailwayOwnershipTypes.OwnerType.CITY,owner,"Main Line",RailwayOwnershipTypes.Access.PRIVATE,725);
        var n1=new RailwayNodeReference(ResourceLocation.parse("minecraft:overworld"),0,128,0,0);var n2=new RailwayNodeReference(n1.dimension(),20,128,0,0);var n3=new RailwayNodeReference(n1.dimension(),40,128,0,0);
        var e1=new RailwayEdgeReference(n1,n2).normalized();var e2=new RailwayEdgeReference(n2,n3).normalized();
        var a=new RailwayEndpoint(UUID.randomUUID(),n1.dimension(),new BlockPos(0,65,1),n1.dimension(),new BlockPos(0,64,0),true,graph,e1,1.5);
        var b=new RailwayEndpoint(UUID.randomUUID(),n1.dimension(),new BlockPos(20,65,1),n1.dimension(),new BlockPos(20,64,0),false,graph,e2,8.5);
        OwnedRailwaySection section=new OwnedRailwaySection(sectionId,networkId,a,b,List.of(e1,e2),17.0,RailwayOwnershipTypes.SectionStatus.TRACK_MISSING);
        map(manager,"ownedNetworks").put(networkId,network);map(manager,"ownedSections").put(sectionId,section);
        CompoundTag saved=manager.save(new CompoundTag(),null);require(saved.getInt("FormatVersion")==5,"railway v5 format");require(saved.getList("OwnedNetworks",10).size()==1&&saved.getList("OwnedSections",10).size()==1,"ownership persisted");
        var load=RailwayManager.class.getDeclaredMethod("load",CompoundTag.class,net.minecraft.core.HolderLookup.Provider.class);load.setAccessible(true);RailwayManager restored=(RailwayManager)load.invoke(null,saved,null);
        var rn=restored.ownedNetwork(networkId);var rs=restored.sectionsForNetwork(networkId).getFirst();require(rn!=null&&rn.ownerType()==RailwayOwnershipTypes.OwnerType.CITY&&rn.ownerId().equals(owner),"owner roundtrip");require(rn.access()==RailwayOwnershipTypes.Access.PRIVATE&&rn.freightTariff()==725,"access and tariff roundtrip");require(rs.path().equals(List.of(e1,e2))&&rs.length()==17.0&&rs.status()==RailwayOwnershipTypes.SectionStatus.TRACK_MISSING,"path references and status roundtrip");
        require(!restored.configureNetwork(networkId,RailwayOwnershipTypes.OwnerType.PLAYER,owner,RailwayOwnershipTypes.Access.PUBLIC,0L,null),"wrong owner cannot take over");require(restored.configureNetwork(networkId,RailwayOwnershipTypes.OwnerType.CITY,owner,RailwayOwnershipTypes.Access.PUBLIC,0L,"Public Main"),"owner can manage network");
        restored.removeCity(owner);require(restored.ownedNetwork(networkId)==null&&restored.sectionsForNetwork(networkId).isEmpty(),"city removal clears owned network");System.out.println("PASS: "+checks+" railway ownership persistence and authorization checks");
    }
}
