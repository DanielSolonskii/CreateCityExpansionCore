package com.example.examplemod.journey;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;
import static com.example.examplemod.journey.JourneyModel.*;

public final class JourneyData extends SavedData {
    private CompoundTag unavailable;
    CompoundTag routeDraft = new CompoundTag();
    public final Map<String,Marker> markers=new LinkedHashMap<>();
    public final Map<String,Quest> quests=new LinkedHashMap<>();
    public final Map<String,Chain> chains=new LinkedHashMap<>();
    public final Map<UUID,Progress> players=new LinkedHashMap<>();
    public static JourneyData get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(new Factory<>(JourneyData::new,JourneyData::loadForStorage,null),"ccec_journeys");}
    public void requireAvailable(){if(unavailable!=null)throw new IllegalArgumentException("Journey data unavailable; original saved data retained");}
    public Progress player(UUID id){requireAvailable();return players.computeIfAbsent(id,k->new Progress());}
    public static JourneyData load(CompoundTag tag,HolderLookup.Provider lookup) {
        JourneyData data = JourneyCodec.load(tag);
        data.routeDraft = tag.getCompound("RouteDraft").copy();
        return data;
    }
    public static JourneyData loadForStorage(CompoundTag tag,HolderLookup.Provider lookup){try{return load(tag,lookup);}catch(RuntimeException failure){var d=new JourneyData();d.unavailable=tag.copy();com.mojang.logging.LogUtils.getLogger().error("CCEC journey data unavailable; retaining original data",failure);return d;}}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup) {
        if (unavailable != null) {
            tag.merge(unavailable.copy());
            return tag;
        }
        tag.merge(JourneyCodec.save(this));
        if (!routeDraft.isEmpty()) tag.put("RouteDraft", routeDraft.copy());
        return tag;
    }
    public Marker marker(String id){var m=markers.get(id);if(m==null)throw new IllegalArgumentException("Unknown marker: "+id);return m;}
    public Quest quest(String id){var q=quests.get(id);if(q==null)throw new IllegalArgumentException("Unknown quest: "+id);return q;}
    public Chain chain(String id){var c=chains.get(id);if(c==null)throw new IllegalArgumentException("Unknown journey: "+id);return c;}
}
