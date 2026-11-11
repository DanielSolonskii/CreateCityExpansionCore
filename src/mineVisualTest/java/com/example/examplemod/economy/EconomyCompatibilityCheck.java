package com.example.examplemod.economy;

import net.minecraft.nbt.*;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import java.nio.file.*;
import java.util.*;

/** Real native storage: preserve supported production ledgers and reject future data without replacement. */
public final class EconomyCompatibilityCheck {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var config=BankCheck.class.getDeclaredMethod("config");config.setAccessible(true);config.invoke(null);
        net.minecraft.SharedConstants.tryDetectVersion();
        UUID buyer=UUID.randomUUID(),seller=UUID.randomUUID(),order=UUID.randomUUID();
        var economy=new EconomyManager();economy.adminSetWallet(buyer,1000);
        economy.cargo().reserve(order,com.example.examplemod.cargo.center.OwnerRef.player(buyer),
                com.example.examplemod.cargo.center.OwnerRef.player(seller),order,null,10,120,List.of());
        var production=economy.save(new CompoundTag(),null);production.putInt("FormatVersion",15);
        production.getCompound("CargoCommerce").putInt("Version",1);
        var opaque=new CompoundTag();opaque.putString("RetiredLedger","retain verbatim");production.put("Commerce",opaque.copy());production.put("FreightLogistics",opaque.copy());
        var dir=Files.createTempDirectory(Path.of("").toAbsolutePath(),"economy-compat-");
        try{
            var file=dir.resolve("createcityexpansioncore_economy.dat");write(file,production);
            var loaded=EconomyManager.fromStorage(new DimensionDataStorage(dir.toFile(),null,null));
            require(loaded.wallet(buyer)==880&&loaded.cargo().order(order).orElseThrow().total()==120,"production v15 wallets and real Cargo escrow retained");
            var migrated=loaded.save(new CompoundTag(),null);
            require(migrated.getInt("FormatVersion")==17&&migrated.getCompound("CargoCommerce").getInt("Version")==3,"metadata matches current persisted versions");
            require(migrated.getCompound("Commerce").equals(opaque)&&migrated.getCompound("FreightLogistics").equals(opaque),"retired production tags remain opaque and untouched");
            var previous=production.copy();previous.putInt("FormatVersion",16);previous.getCompound("CargoCommerce").putInt("Version",2);previous.remove("Companies");previous.remove("CompanyAccounts");previous.remove("RoadTolls");previous.remove("MasterCityTrade");write(file,previous);
            var v16=EconomyManager.fromStorage(new DimensionDataStorage(dir.toFile(),null,null));require(v16.wallet(buyer)==880&&v16.cargo().order(order).orElseThrow().total()==120&&v16.companies().all().isEmpty(),"previous v16 world keeps wallets, escrow and empty additive books");
            var future=production.copy();future.putInt("FormatVersion",18);future.putString("UnknownMoney","preserve");write(file,future);
            var storage=new DimensionDataStorage(dir.toFile(),null,null);boolean rejected=false;
            try{EconomyManager.fromStorage(storage);}catch(IllegalStateException expected){rejected=true;}
            require(rejected,"native storage rejects future financial data outside its catch-and-replace loader");
            var retained=storage.get(new SavedData.Factory<EconomyManager>(EconomyManager::new,(tag,registries)->{throw new AssertionError("must use retained native cache");}),"createcityexpansioncore_economy");
            require(retained!=null&&retained.save(new CompoundTag(),null).equals(future),"cache retains all original future financial NBT");
            Files.delete(file);retained.setDirty();storage.save();net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
            require(NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap()).getCompound("data").equals(future),"forced native write cannot downgrade rejected data");
            rejected=false;try{EconomyManager.fromStorage(storage);}catch(IllegalStateException expected){rejected=true;}
            require(rejected,"cached rejected registry remains inaccessible");
            var metadata=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(Objects.requireNonNull(EconomyCompatibilityCheck.class.getResourceAsStream("/META-INF/ccec-save-formats.json")),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var formats=metadata.getAsJsonObject("savedData").getAsJsonObject("createcityexpansioncore_economy");
            require(formats.getAsJsonObject("FormatVersion").get("max").getAsInt()==migrated.getInt("FormatVersion")&&formats.getAsJsonObject("CargoCommerce.Version").get("max").getAsInt()==migrated.getCompound("CargoCommerce").getInt("Version"),"release compatibility metadata matches real economy codec");
        }finally{
            net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
            if(!dir.toRealPath().startsWith(Path.of("").toRealPath()))throw new AssertionError("unsafe fixture cleanup");
            try(var paths=Files.walk(dir)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
        }
        System.out.println("PASS: "+checks+" native Economy compatibility checks");
    }
    private static void write(Path file,CompoundTag data)throws Exception{var root=new CompoundTag();root.put("data",data.copy());NbtUtils.addCurrentDataVersion(root);NbtIo.writeCompressed(root,file);}
}
