package com.example.examplemod.cargo.center;

import java.util.UUID;
import net.minecraft.nbt.*;

/** Dispatch conservation and restart metadata. Native movement needs a live Create world. */
public final class CargoCharterCheck {
    private static void require(boolean value){if(!value)throw new AssertionError("charter dispatch regression");}
    public static void run(){
        require(!CargoCharters.loaded(0,64,0,0));
        require(!CargoCharters.loaded(64,64,0,63));
        require(!CargoCharters.loaded(32,64,0,64));
        require(CargoCharters.loaded(64,64,0,64));
        require(CargoCharters.loaded(64,64,16,48));
        var root=new CompoundTag();root.putInt("Version",1);var jobs=new ListTag();
        var entry=new CompoundTag();entry.putUUID("Id",UUID.randomUUID());var job=new CompoundTag();
        job.putUUID("Shipment",UUID.randomUUID());job.putUUID("Home",UUID.randomUUID());job.putString("Phase","DELIVERY");job.putInt("Hop",2);job.putBoolean("Return",true);entry.put("Data",job);jobs.add(entry);root.put("Jobs",jobs);
        var loaded=CargoCharters.load(root,null);var saved=loaded.save(new CompoundTag(),null);
        require(saved.getList("Jobs",10).equals(jobs));
        require(CargoCharters.load(saved,null).save(new CompoundTag(),null).equals(saved));
        jobs.add(entry.copy());boolean duplicate=false;try{CargoCharters.load(root,null);}catch(IllegalArgumentException expected){duplicate=true;}require(duplicate);
        jobs.remove(1);job.putString("Phase","INVALID");boolean invalid=false;try{CargoCharters.load(root,null);}catch(IllegalArgumentException expected){invalid=true;}require(invalid);
        System.out.println("PASS: 9 charter loading conservation and restart metadata checks");
    }
}
