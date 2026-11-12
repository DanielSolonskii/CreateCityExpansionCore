package com.example.examplemod.cargo.center;
import com.example.examplemod.cargo.*;
import com.example.examplemod.city.CityManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
/** Ownership/access properties extending existing registrations. Legacy UUIDs resolve to PLAYER without duplicate registries. */
public final class CargoOwners extends SavedData {
    public record Access(OwnerRef owner,CargoTrainRegistry.AccessMode mode,Set<UUID> players,Set<UUID> cities,Set<UUID> companies){
        public Access{players=Set.copyOf(players);cities=Set.copyOf(cities);companies=Set.copyOf(companies);}
        public Access(OwnerRef owner,CargoTrainRegistry.AccessMode mode,Set<UUID> players,Set<UUID> cities){this(owner,mode,players,cities,Set.of());}
    }
    private final Map<String,Access> overrides=new HashMap<>();
    private CompoundTag unavailableData;
    private RuntimeException loadFailure;
    public static CargoOwners get(MinecraftServer s){if(!s.isSameThread())throw new IllegalStateException("Server thread");return fromStorage(s.overworld().getDataStorage());}
    public static CargoOwners fromStorage(net.minecraft.world.level.storage.DimensionDataStorage storage){var data=storage.computeIfAbsent(new Factory<>(CargoOwners::new,CargoOwners::loadForStorage,null),"ccec_cargo_ownership");if(data.loadFailure!=null)throw new IllegalStateException("Cargo ownership cannot be loaded; original data retained",data.loadFailure);return data;}
    private static CargoOwners loadForStorage(CompoundTag root,HolderLookup.Provider lookup){try{return load(root,lookup);}catch(RuntimeException failure){var data=new CargoOwners();data.unavailableData=root.copy();data.loadFailure=failure;return data;}}
    public Access access(String key,UUID legacy,CargoTrainRegistry.AccessMode mode){return overrides.getOrDefault(key,new Access(OwnerRef.player(legacy),mode,Set.of(),Set.of()));}
    public OwnerRef owner(String key,UUID legacy){return access(key,legacy,CargoTrainRegistry.AccessMode.PRIVATE).owner();}
    public boolean change(MinecraftServer s,UUID actor,String key,UUID legacy,CargoTrainRegistry.AccessMode oldMode,Access next){var old=access(key,legacy,oldMode);if(!old.owner().manages(s,actor)||!next.owner().equals(old.owner())&&!next.owner().manages(s,actor))return false;overrides.put(key,next);setDirty();return true;}
    public static boolean permits(MinecraftServer s,OwnerRef requester,Access access){if(s!=null&&(!requester.available(s)||!access.owner().available(s)))return false;if(requester.equals(access.owner()))return true;if(requester.kind()==OwnerRef.Kind.PLAYER&&access.owner().operates(s,requester.id()))return true;if(access.mode()==CargoTrainRegistry.AccessMode.PUBLIC)return true;if(access.mode()!=CargoTrainRegistry.AccessMode.RESTRICTED)return false;if(requester.kind()==OwnerRef.Kind.CITY)return access.cities().contains(requester.id());if(requester.kind()==OwnerRef.Kind.COMPANY)return access.companies().contains(requester.id());if(requester.kind()!=OwnerRef.Kind.PLAYER)return false;if(access.players().contains(requester.id()))return true;if(s==null)return false;var city=CityManager.get(s.overworld()).getMemberCity(requester.id());if(city!=null&&access.cities().contains(city.getId()))return true;return access.companies().stream().anyMatch(id->new OwnerRef(OwnerRef.Kind.COMPANY,id).operates(s,requester.id()));}
    public static OwnerRef point(MinecraftServer s,CargoPointRegistry.Point p){return get(s).owner("point/"+p.id(),p.owner());}
    public static OwnerRef router(MinecraftServer s,CargoPointRegistry.RouterRef ref,UUID legacy){return CargoPointRegistry.get(s).pointForRouter(ref).map(p->point(s,p)).orElse(OwnerRef.player(legacy));}
    public static boolean train(MinecraftServer s,OwnerRef requester,CargoTrainRegistry.Registration t){return permits(s,requester,get(s).access("train/"+t.trainId(),t.ownerId(),t.accessMode()));}
    public static CargoOwners load(CompoundTag root,HolderLookup.Provider lookup){if(root.getInt("Version")<1||root.getInt("Version")>2)throw new IllegalArgumentException("Cargo ownership version");var d=new CargoOwners();for(Tag raw:root.getList("Entries",10)){var t=(CompoundTag)raw;var players=new HashSet<UUID>();for(Tag id:t.getList("Players",11))players.add(NbtUtils.loadUUID(id));var cities=new HashSet<UUID>();for(Tag id:t.getList("Cities",11))cities.add(NbtUtils.loadUUID(id));var companies=new HashSet<UUID>();for(Tag id:t.getList("Companies",11))companies.add(NbtUtils.loadUUID(id));if(d.overrides.putIfAbsent(t.getString("Key"),new Access(OwnerRef.load(t.getCompound("Owner")),CargoTrainRegistry.AccessMode.valueOf(t.getString("Mode")),players,cities,companies))!=null)throw new IllegalArgumentException("Duplicate cargo access");}return d;}
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup){if(unavailableData!=null)return unavailableData.copy();root.putInt("Version",2);var list=new ListTag();overrides.forEach((key,a)->{var t=new CompoundTag();t.putString("Key",key);t.put("Owner",a.owner().save());t.putString("Mode",a.mode().name());var p=new ListTag();for(UUID id:a.players())p.add(NbtUtils.createUUID(id));t.put("Players",p);var c=new ListTag();for(UUID id:a.cities())c.add(NbtUtils.createUUID(id));t.put("Cities",c);var companies=new ListTag();for(UUID id:a.companies())companies.add(NbtUtils.createUUID(id));t.put("Companies",companies);list.add(t);});root.put("Entries",list);return root;}
}
