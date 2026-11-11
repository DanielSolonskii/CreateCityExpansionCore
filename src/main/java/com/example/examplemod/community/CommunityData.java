package com.example.examplemod.community;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
/** Additive community preferences, bounded audit, and owner-only Telegram outbox. */
public final class CommunityData extends SavedData {
    public record Preferences(boolean telegram,boolean preview,boolean offlineOnly,boolean quiet,int offset){public static Preferences defaults(){return new Preferences(false,false,true,true,180);}}
    public record Notice(UUID id,UUID owner,UUID peer,String kind,String text,UUID city,UUID token,long created,long after){}
    public final Map<UUID,Preferences> preferences=new HashMap<>();
    public final Map<UUID,Set<UUID>> muted=new HashMap<>();
    public final List<Notice> outbox=new ArrayList<>();
    public final List<CompoundTag> audit=new ArrayList<>();
    public static CommunityData get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(new Factory<>(CommunityData::new,CommunityData::load,null),"ccec_community");}
    public Preferences preferences(UUID id){return preferences.getOrDefault(id,Preferences.defaults());}
    public boolean muted(UUID owner,UUID peer){return muted.getOrDefault(owner,Set.of()).contains(peer);}
    public void audit(UUID city,UUID actor,UUID target,String action){var t=new CompoundTag();t.putUUID("City",city);t.putUUID("Actor",actor);if(target!=null)t.putUUID("Target",target);t.putString("Action",action);t.putLong("Time",System.currentTimeMillis());audit.add(t);while(audit.size()>512)audit.removeFirst();setDirty();}
    public void enqueue(UUID owner,UUID peer,String kind,String text,boolean online){enqueue(owner,peer,kind,text,online,null,null);}
    public void enqueue(UUID owner,UUID peer,String kind,String text,boolean online,UUID city,UUID token){
        var p=preferences(owner);if(!p.telegram()||p.offlineOnly()&&online||muted(owner,peer))return;
        long now=System.currentTimeMillis(),after=now+10_000;
        if(p.quiet()){var z=java.time.ZoneOffset.ofTotalSeconds(p.offset()*60);var local=java.time.Instant.ofEpochMilli(after).atOffset(z);if(local.getHour()>=22||local.getHour()<8){var day=local.toLocalDate().plusDays(local.getHour()>=22?1:0);after=day.atTime(8,0).toInstant(z).toEpochMilli();}}
        String content=kind.equals("INVITE")||p.preview()?text:"";
        // Coalesce pending events for the same recipient/peer without exporting a friends list.
        outbox.removeIf(n->n.owner.equals(owner)&&n.peer.equals(peer)&&n.kind.equals(kind)&&n.created>now-10_000);
        if(outbox.size()>=512)outbox.removeFirst();outbox.add(new Notice(UUID.randomUUID(),owner,peer,kind,content,city,token,now,after));setDirty();
    }
    public static CommunityData load(CompoundTag root,HolderLookup.Provider lookup){var d=new CommunityData();
        for(Tag raw:root.getList("Preferences",Tag.TAG_COMPOUND))try{var t=(CompoundTag)raw;UUID id=t.getUUID("Id");d.preferences.put(id,new Preferences(t.getBoolean("Telegram"),t.getBoolean("Preview"),!t.getBoolean("AlsoOnline"),!t.getBoolean("NoQuiet"),Math.clamp(t.getInt("Offset"),-840,840)));Set<UUID> muted=new HashSet<>();for(Tag v:t.getList("Muted",Tag.TAG_STRING))if(muted.size()<128)muted.add(UUID.fromString(v.getAsString()));d.muted.put(id,muted);}catch(IllegalArgumentException ignored){}
        for(Tag raw:root.getList("Outbox",Tag.TAG_COMPOUND))try{var t=(CompoundTag)raw;if(d.outbox.size()<512&&t.getString("Text").length()<=500)d.outbox.add(new Notice(t.getUUID("Id"),t.getUUID("Owner"),t.getUUID("Peer"),t.getString("Kind"),t.getString("Text"),t.hasUUID("City")?t.getUUID("City"):null,t.hasUUID("Token")?t.getUUID("Token"):null,t.getLong("Created"),t.getLong("After")));}catch(IllegalArgumentException ignored){}
        for(Tag raw:root.getList("Audit",Tag.TAG_COMPOUND))if(d.audit.size()<512)d.audit.add(((CompoundTag)raw).copy());return d;}
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup){root.putInt("Version",1);var settings=new ListTag();preferences.forEach((id,p)->{var t=new CompoundTag();t.putUUID("Id",id);t.putBoolean("Telegram",p.telegram);t.putBoolean("Preview",p.preview);t.putBoolean("AlsoOnline",!p.offlineOnly);t.putBoolean("NoQuiet",!p.quiet);t.putInt("Offset",p.offset);var m=new ListTag();muted.getOrDefault(id,Set.of()).forEach(v->m.add(StringTag.valueOf(v.toString())));t.put("Muted",m);settings.add(t);});root.put("Preferences",settings);
        var queue=new ListTag();for(var n:outbox){var t=new CompoundTag();t.putUUID("Id",n.id);t.putUUID("Owner",n.owner);t.putUUID("Peer",n.peer);t.putString("Kind",n.kind);t.putString("Text",n.text);if(n.city!=null)t.putUUID("City",n.city);if(n.token!=null)t.putUUID("Token",n.token);t.putLong("Created",n.created);t.putLong("After",n.after);queue.add(t);}root.put("Outbox",queue);var log=new ListTag();audit.forEach(t->log.add(t.copy()));root.put("Audit",log);return root;}
}
