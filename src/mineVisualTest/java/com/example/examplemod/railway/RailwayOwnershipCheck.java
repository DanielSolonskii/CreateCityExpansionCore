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
        CompoundTag future=saved.copy();future.putInt("FormatVersion",6);future.putString("UnknownFutureField","preserve-me");CompoundTag untouched=future.copy();
        nativeStorageFutureCheck(future);
        RailwayManager unavailable=(RailwayManager)load.invoke(null,future,null);
        require(unavailable.save(new CompoundTag(),null).equals(future),"future railway data is retained without parsing or downgrade");
        require(future.equals(untouched),"failed railway load preserves the original data, ownership and paths");
        var rn=restored.ownedNetwork(networkId);var rs=restored.sectionsForNetwork(networkId).getFirst();require(rn!=null&&rn.ownerType()==RailwayOwnershipTypes.OwnerType.CITY&&rn.ownerId().equals(owner),"owner roundtrip");require(rn.access()==RailwayOwnershipTypes.Access.PRIVATE&&rn.freightTariff()==725,"access and tariff roundtrip");require(rs.path().equals(List.of(e1,e2))&&rs.length()==17.0&&rs.status()==RailwayOwnershipTypes.SectionStatus.TRACK_MISSING,"path references and status roundtrip");
        require(!restored.configureNetwork(networkId,RailwayOwnershipTypes.OwnerType.PLAYER,owner,RailwayOwnershipTypes.Access.PUBLIC,0L,null),"wrong owner cannot take over");require(restored.configureNetwork(networkId,RailwayOwnershipTypes.OwnerType.CITY,owner,RailwayOwnershipTypes.Access.PUBLIC,0L,"Public Main"),"owner can manage network");
        restored.removeCity(owner);require(restored.ownedNetwork(networkId)==null&&restored.sectionsForNetwork(networkId).isEmpty(),"city removal clears owned network");System.out.println("PASS: "+checks+" railway ownership persistence and authorization checks");
    }

    private static void nativeStorageFutureCheck(CompoundTag future) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        var directory=java.nio.file.Files.createTempDirectory(java.nio.file.Path.of("").toAbsolutePath(),"railway-native-");
        try {
            var file=directory.resolve("createcityexpansioncore_railway.dat");
            CompoundTag diskRoot=new CompoundTag();diskRoot.put("data",future.copy());
            net.minecraft.nbt.NbtUtils.addCurrentDataVersion(diskRoot);
            net.minecraft.nbt.NbtIo.writeCompressed(diskRoot,file);
            byte[] before=java.nio.file.Files.readAllBytes(file);
            var storage=new net.minecraft.world.level.storage.DimensionDataStorage(directory.toFile(),null,null);
            boolean rejected=false;
            try { RailwayManager.fromStorage(storage); } catch(IllegalStateException expected) { rejected=true; }
            require(rejected,"native SavedData exception handling must not replace a future railway registry");
            storage.save();
            net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
            require(Arrays.equals(before,java.nio.file.Files.readAllBytes(file)),"ordinary native save leaves the future file byte-identical");
            var cached=storage.get(new net.minecraft.world.level.saveddata.SavedData.Factory<RailwayManager>(RailwayManager::new,
                    (tag,registries)->{throw new AssertionError("retained future registry must stay cached");}),"createcityexpansioncore_railway");
            require(cached!=null&&cached.save(new CompoundTag(),null).equals(future),"cached unavailable registry retains all future NBT");
            java.nio.file.Files.delete(file);
            cached.setDirty();storage.save();
            net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
            require(net.minecraft.nbt.NbtIo.readCompressed(file,net.minecraft.nbt.NbtAccounter.unlimitedHeap()).getCompound("data").equals(future),
                    "forced native save preserves unknown format and ownership rather than writing v5");
            rejected=false;
            try { RailwayManager.fromStorage(storage); } catch(IllegalStateException expected) { rejected=true; }
            require(rejected,"repeated native lookup cannot bypass the cached future-version guard");
            CompoundTag supported=future.copy();supported.putInt("FormatVersion",5);diskRoot.put("data",supported);
            net.minecraft.nbt.NbtIo.writeCompressed(diskRoot,file);
            require(RailwayManager.fromStorage(new net.minecraft.world.level.storage.DimensionDataStorage(directory.toFile(),null,null))
                    .ownedNetworks().size()==1,"supported v5 native load preserves the existing network");
        } finally {
            net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
            // Only this newly-created fixture directory under the task's working directory.
            if(!directory.toRealPath().startsWith(java.nio.file.Path.of("").toRealPath()))throw new AssertionError("unsafe fixture cleanup");
            try(var paths=java.nio.file.Files.walk(directory)) {
                for(var path:paths.sorted(java.util.Comparator.reverseOrder()).toList())java.nio.file.Files.delete(path);
            }
        }
    }
}
