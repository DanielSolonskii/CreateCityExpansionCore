package com.example.examplemod.places;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

final class PlaceBook extends SavedData {
    static final int MAX=512, PER_OWNER=24, PUBLIC_PER_OWNER=8;
    final Map<UUID,AtlasPlace> entries=new LinkedHashMap<>();
    private CompoundTag unreadable;
    static PlaceBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(PlaceBook::new,PlaceBook::load,null),"ccec_atlas_places_v1");
    }
    boolean available(){return unreadable==null;}
    String change(UUID actor,UUID id,long expected,AtlasPlace candidate) {
        if(!available())return "incompatible";
        var old=entries.get(id);
        if(old!=null && !old.owner().equals(actor))return "unavailable";
        if(old==null && (expected!=0 || candidate==null))return "unavailable";
        if(expected!=(old==null?0:old.version()) || expected==Long.MAX_VALUE)return "conflict";
        if(candidate==null){
            if(old==null)return "unavailable";
            entries.remove(id);
        } else {
            if(!candidate.id().equals(id))return "invalid";
            if(old==null && (entries.size()>=MAX || entries.values().stream().filter(p->p.owner().equals(actor)).count()>=PER_OWNER))return "limit";
            if(candidate.published() && (old==null || !old.published()) && entries.values().stream().filter(p->p.owner().equals(actor)&&p.published()).count()>=PUBLIC_PER_OWNER)return "limit";
            entries.put(id,candidate.next(actor,expected+1));
        }
        setDirty(); return "saved";
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider provider) {
        if(unreadable!=null)return unreadable.copy();
        tag.putInt("Format",1);var list=new ListTag();entries.values().forEach(p->list.add(p.save()));tag.put("Places",list);return tag;
    }
    static PlaceBook load(CompoundTag tag,HolderLookup.Provider provider) {
        var data=new PlaceBook();
        try {
            if(tag.getInt("Format")!=1 || !(tag.get("Places") instanceof ListTag list) || list.size()>MAX
                    || !list.isEmpty() && list.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Place format");
            for(var entry:list){
                var p=AtlasPlace.load((CompoundTag)entry);
                if(p.source()!=null && p.source().kind()==AtlasPlace.SourceKind.PROJECT){
                    p=new AtlasPlace(p.id(),p.owner(),p.version(),p.name(),p.category(),p.description(),p.dimension(),
                            p.icon(),p.entrances(),p.published(),null);
                    data.setDirty();
                }
                if(data.entries.put(p.id(),p)!=null)throw new IllegalArgumentException("Duplicate place");
            }
        } catch(RuntimeException invalid){data.entries.clear();data.unreadable=tag.copy();}
        return data;
    }
}
