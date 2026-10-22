package com.example.examplemod.onboarding;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
/** New and existing UUIDs get one v1 pass. Death/reconnect does not reset it. */
public final class OnboardingSavedData extends SavedData {
 private final Map<UUID,OnboardingProgress> players=new HashMap<>();
 public static OnboardingSavedData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(OnboardingSavedData::new,OnboardingSavedData::load,null),"ccec_onboarding");}
 public OnboardingProgress progress(UUID id){return players.getOrDefault(id,OnboardingProgress.fresh());}
 public void put(UUID id,OnboardingProgress value){if(!value.equals(players.put(id,value)))setDirty();}
 public static OnboardingSavedData load(CompoundTag root,HolderLookup.Provider lookup){
  var data=new OnboardingSavedData();for(var raw:root.getList("Players",Tag.TAG_COMPOUND)){var tag=(CompoundTag)raw;if(tag.hasUUID("Id"))data.players.put(tag.getUUID("Id"),new OnboardingProgress(tag.getInt("Seen"),tag.getInt("Completed"),tag.getBoolean("Skipped")));}return data;
 }
 @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup){root.putInt("Version",OnboardingProgress.VERSION);ListTag list=new ListTag();players.forEach((id,value)->{var tag=new CompoundTag();tag.putUUID("Id",id);tag.putInt("Seen",value.seen());tag.putInt("Completed",value.completed());tag.putBoolean("Skipped",value.skipped());list.add(tag);});root.put("Players",list);return root;}
}
