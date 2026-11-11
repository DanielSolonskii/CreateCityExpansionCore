package com.example.examplemod.daily;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;

/** Additive SavedData. Synchronous atomic journal saves precede player inventory changes. */
public final class DailyRewardsData extends SavedData {
    public static final String NAME="ccec_daily_rewards";
    public record Pending(UUID id,ItemStack item,int levels){public Pending{Objects.requireNonNull(id);item=item.copy();if(levels<0||levels>1000||item.isEmpty()&&levels==0)throw new IllegalArgumentException("Pending reward");}}
    public record Entry(String name,DailyRewardProgress progress,Pending pending,boolean delivered){public Entry(String name,DailyRewardProgress progress,Pending pending){this(name,progress,pending,false);}}
    private final Map<UUID,Entry> players=new HashMap<>();
    private boolean initialized;
    public static DailyRewardsData get(MinecraftServer server){
        var data=server.overworld().getDataStorage().computeIfAbsent(new Factory<>(DailyRewardsData::new,DailyRewardsData::load,null),NAME);
        if(!data.initialized&&Files.exists(path(server)))throw new IllegalStateException("Daily rewards save could not be loaded; claims disabled");return data;
    }
    public Entry entry(UUID id){return players.getOrDefault(id,new Entry("",DailyRewardProgress.fresh(),null));}
    public Map<UUID,Entry> entries(){return Map.copyOf(players);}
    public void put(UUID id,Entry entry){players.put(id,entry);setDirty();}
    public void reset(UUID id){var old=entry(id);put(id,new Entry(old.name,DailyRewardProgress.fresh(),null));}
    public void resetAll(){players.replaceAll((id,e)->new Entry(e.name,DailyRewardProgress.fresh(),null));setDirty();}
    public static Path path(MinecraftServer s){return s.getWorldPath(LevelResource.ROOT).resolve("data").resolve(NAME+".dat");}
    public void flush(MinecraftServer s)throws IOException{write(path(s),s.registryAccess());}
    private void write(Path target,HolderLookup.Provider lookup)throws IOException{
        var root=new CompoundTag();root.put("data",save(new CompoundTag(),lookup));NbtUtils.addCurrentDataVersion(root);Files.createDirectories(target.getParent());
        Path temp=Files.createTempFile(target.getParent(),NAME,".tmp");try{
            NbtIo.writeCompressed(root,temp);try(var channel=FileChannel.open(temp,StandardOpenOption.WRITE)){channel.force(true);}
            Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);initialized=true;setDirty(false);
        }finally{Files.deleteIfExists(temp);}
    }
    @Override public void save(File file,HolderLookup.Provider lookup){if(isDirty())try{write(file.toPath(),lookup);}catch(IOException e){com.mojang.logging.LogUtils.getLogger().error("Daily rewards save failed; progress stays dirty",e);}}
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup){
        root.putInt("Version",1);var list=new ListTag();players.forEach((id,e)->{
            var t=new CompoundTag();t.putUUID("Player",id);t.putString("Name",e.name);t.putBoolean("Delivered",e.delivered);var p=e.progress;t.putLong("LastDate",p.lastDate());t.putInt("Day",p.day());t.putInt("StarterMask",p.starterMask());t.putBoolean("StarterComplete",p.completedStarter());t.putBoolean("LastStarter",p.lastStarter());
            t.putInt("RunUniqueMask",p.runUniqueMask());if(e.pending!=null){var reward=new CompoundTag();reward.putUUID("Id",e.pending.id);reward.put("Item",e.pending.item.saveOptional(lookup));reward.putInt("Levels",e.pending.levels);t.put("Pending",reward);}list.add(t);
        });root.put("Players",list);return root;
    }
    public static DailyRewardsData load(CompoundTag root,HolderLookup.Provider lookup){
        if(root.getInt("Version")!=1)throw new IllegalStateException("Unsupported daily rewards version");var data=new DailyRewardsData();data.initialized=true;
        for(Tag raw:root.getList("Players",10)){var t=(CompoundTag)raw;var p=new DailyRewardProgress(t.getLong("LastDate"),t.getInt("Day"),t.getInt("StarterMask"),t.getBoolean("LastStarter"),t.getInt("RunUniqueMask"));Pending pending=null;
            if(t.contains("Pending",10)){var r=t.getCompound("Pending");pending=new Pending(r.getUUID("Id"),ItemStack.parseOptional(lookup,r.getCompound("Item")),r.getInt("Levels"));}
            UUID id=t.getUUID("Player");if(data.players.put(id,new Entry(t.getString("Name"),p,pending,t.getBoolean("Delivered")))!=null)throw new IllegalStateException("Duplicate daily player");
        }return data;
    }
}
