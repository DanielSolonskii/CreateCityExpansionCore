package com.example.examplemod.cargo.center;

import net.minecraft.nbt.*;
import net.minecraft.world.level.storage.DimensionDataStorage;
import java.nio.file.*;
import java.util.*;

/** Cancellation accounting, restart replay, and the real native SavedData failure boundary. */
public final class CargoCancellationCheck {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static final class Accounts implements CargoCommerceBook.Accounts {
        final Map<OwnerRef,Long> balances=new HashMap<>();
        public long balance(OwnerRef owner){return balances.getOrDefault(owner,0L);}
        public boolean debit(OwnerRef owner,long n){if(n<0||balance(owner)<n)return false;balances.put(owner,balance(owner)-n);return true;}
        public boolean canCredit(OwnerRef owner,long n){return n>=0&&balance(owner)<=maximum()-n;}
        public void credit(OwnerRef owner,long n){if(!canCredit(owner,n))throw new AssertionError("Unprepared credit");balances.put(owner,balance(owner)+n);}
        public long maximum(){return 10000;}
        public void dirty(){}
        long total(){return balances.values().stream().mapToLong(Long::longValue).sum();}
    }
    public static void run()throws Exception{
        var payer=OwnerRef.player(UUID.randomUUID());var seller=OwnerRef.player(UUID.randomUUID());var carrier=OwnerRef.player(UUID.randomUUID());
        var a=new Accounts();a.balances.put(payer,1000L);var b=new CargoCommerceBook(a);
        var item=new CompoundTag();item.putString("id","minecraft:iron_ingot");UUID offer=UUID.randomUUID(),id=UUID.randomUUID();
        b.publish(offer,seller.id(),seller,UUID.randomUUID(),item,"Iron",20,5);
        b.reserve(id,payer,seller,id,offer,10,50,List.of(new CargoCommerceBook.Fee(UUID.randomUUID(),0,carrier,7)));
        require(b.markAttempted(id)&&b.requestCancellation(id),"attempted box can request cancellation");
        require(!b.markAttempted(id)&&!b.refundUnattempted(id)&&!b.settle(id,true,h->true),"cancelled order cannot dispatch, refund uncertain cargo or use ordinary settlement");
        for(int n=0;n<10;n++)require(!b.settleCancellation(id,n,0,h->0)&&a.balance(payer)==943&&a.total()==943,"missing physical units retain all escrow");
        require(!b.settleCancellation(id,5,6,h->10)&&!b.settleCancellation(id,-1,11,h->10),"invalid physical totals do not pay");
        require(!b.settleCancellation(id,4,6,h->11)&&!b.settleCancellation(id,4,6,h->-1),"invalid hop proof cannot release escrow");
        var save=b.save();require(save.getInt("Version")==2,"cancellation schema is explicit");
        b=new CargoCommerceBook(a);b.load(save);require(b.order(id).orElseThrow().state()==CargoCommerceBook.State.CANCELLING,"pending cancellation survives restart");
        a.balances.put(carrier,9999L);
        require(!b.settleCancellation(id,4,6,h->6)&&a.balance(payer)==943&&a.balance(seller)==0,"recipient capacity failure leaves every balance and escrow unchanged");
        a.balances.remove(carrier);
        require(b.settleCancellation(id,4,6,h->6),"all physically delivered or recovered units close cancellation");
        require(a.balance(seller)==20&&a.balance(carrier)==4&&a.balance(payer)==976&&a.total()==1000,"mixed physical outcomes conserve CR and pay only proven service");
        require(b.offer(offer).orElseThrow().remaining()==16,"only goods physically returned to seller restore offer capacity");
        save=b.save();b=new CargoCommerceBook(a);b.load(save);
        for(int i=0;i<30;i++)require(!b.settleCancellation(id,4,6,h->6)&&!b.requestCancellation(id)&&a.total()==1000&&b.offer(offer).orElseThrow().remaining()==16,"restart and command replay cannot pay or restore stock twice");
        UUID same=UUID.randomUUID();b.reserve(same,payer,payer,same,null,10,100,List.of(new CargoCommerceBook.Fee(UUID.randomUUID(),0,payer,9)));b.markAttempted(same);b.requestCancellation(same);
        require(b.settleCancellation(same,4,6,h->4)&&a.total()==1000,"overlapping payer seller carrier credits aggregate once");
        var legacy=new CargoCommerceBook(a).save();legacy.putInt("Version",1);new CargoCommerceBook(a).load(legacy);
        var future=legacy.copy();future.putInt("Version",3);boolean rejected=false;try{new CargoCommerceBook(a).load(future);}catch(IllegalArgumentException expected){rejected=true;}
        require(rejected,"future commerce remains rejected");
        nativeStorage();
        System.out.println("PASS: "+checks+" Cargo cancellation conservation, replay and native storage checks");
    }
    private static void nativeStorage()throws Exception{
        net.minecraft.SharedConstants.tryDetectVersion();
        var directory=Files.createTempDirectory(Path.of("").toAbsolutePath(),"cargo-native-");
        try{
            var raw=new CompoundTag();raw.putInt("Version",3);raw.putString("UnknownPhysicalCargo","retained");
            var disk=new CompoundTag();disk.put("data",raw.copy());NbtUtils.addCurrentDataVersion(disk);
            var file=directory.resolve("ccec_cargo_shipments.dat");NbtIo.writeCompressed(disk,file);var bytes=Files.readAllBytes(file);
            var storage=new DimensionDataStorage(directory.toFile(),null,null);boolean rejected=false;
            try{CargoShipmentRegistry.fromStorage(storage);}catch(IllegalStateException expected){rejected=true;}
            require(rejected,"native loader cannot replace future physical cargo with empty registry");
            storage.save();net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
            require(Arrays.equals(bytes,Files.readAllBytes(file)),"ordinary save preserves unreadable cargo file bytes");
            var cached=storage.get(new net.minecraft.world.level.saveddata.SavedData.Factory<CargoShipmentRegistry>(CargoShipmentRegistry::new,(t,l)->{throw new AssertionError("cache lost");}),"ccec_cargo_shipments");
            require(cached!=null&&cached.save(new CompoundTag(),null).equals(raw),"unknown cargo tags remain cached intact");
            Files.delete(file);cached.setDirty();storage.save();net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
            require(NbtIo.readCompressed(file,NbtAccounter.unlimitedHeap()).getCompound("data").equals(raw),"forced save retains future schema and unknown cargo");
            rejected=false;try{CargoShipmentRegistry.fromStorage(storage);}catch(IllegalStateException expected){rejected=true;}
            require(rejected,"repeated native lookup remains unavailable");
            for(int version:new int[]{1,2}){var supported=new CompoundTag();supported.putInt("Version",version);disk.put("data",supported);NbtIo.writeCompressed(disk,file);require(CargoShipmentRegistry.fromStorage(new DimensionDataStorage(directory.toFile(),null,null)).all().isEmpty(),"native supported cargo version "+version+" loads");}
        }finally{
            net.neoforged.neoforge.common.IOUtilities.waitUntilIOWorkerComplete();
            if(!directory.toRealPath().startsWith(Path.of("").toRealPath()))throw new AssertionError("unsafe fixture cleanup");
            try(var paths=Files.walk(directory)){for(var path:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(path);}
        }
    }
}
