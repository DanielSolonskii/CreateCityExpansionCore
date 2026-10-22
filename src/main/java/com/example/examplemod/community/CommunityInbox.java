package com.example.examplemod.community;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
/** Game-only owner-scoped event history. Stores references, never message text or financial data. */
public final class CommunityInbox extends SavedData {
    public static final int HISTORY=80,OWNERS=4096;
    public record Event(UUID id,String kind,UUID peer,UUID city,UUID alliance,String name,long time,boolean read){}
    final LinkedHashMap<UUID,List<Event>> events=new LinkedHashMap<>();
    public static CommunityInbox get(MinecraftServer s){return s.overworld().getDataStorage().computeIfAbsent(new Factory<>(CommunityInbox::new,CommunityInbox::load,null),"ccec_community_inbox");}
    public List<Event> history(UUID owner){return List.copyOf(events.getOrDefault(owner,List.of()));}
    public int unread(UUID owner){return (int)history(owner).stream().filter(e->!e.read).count();}
    public void add(UUID owner,UUID nonce,String kind,UUID peer,UUID city,UUID alliance,String name,long now){
        Objects.requireNonNull(owner);Objects.requireNonNull(nonce);if(kind.length()>32)throw new IllegalArgumentException("Inbox metadata");if(name.length()>128)name=name.substring(0,127)+"…";
        var list=events.computeIfAbsent(owner,id->new ArrayList<>());if(list.stream().anyMatch(e->e.id.equals(nonce)))return;
        // Unread messages coalesce by sender. No body or count exposes conversation contents.
        if(kind.equals("message")||kind.equals("alliance_message"))list.removeIf(e->e.kind.equals(kind)&&(kind.equals("message")?Objects.equals(e.peer,peer):Objects.equals(e.alliance,alliance))&&!e.read);
        list.addFirst(new Event(nonce,kind,peer,city,alliance,name,now,false));while(list.size()>HISTORY)list.removeLast();while(events.size()>OWNERS)events.remove(events.keySet().iterator().next());setDirty();
    }
    public void read(UUID owner,UUID id){var list=events.get(owner);if(list==null||id==null)return;for(int i=0;i<list.size();i++){var e=list.get(i);if(e.id.equals(id)&&!e.read){list.set(i,new Event(e.id,e.kind,e.peer,e.city,e.alliance,e.name,e.time,true));setDirty();return;}}}
    public void readPeer(UUID owner,UUID peer,String kind){for(var e:history(owner))if(e.kind.equals(kind)&&Objects.equals(e.peer,peer))read(owner,e.id);}
    public void readAlliance(UUID owner,UUID alliance){for(var e:history(owner))if(e.kind.equals("alliance_message")&&Objects.equals(e.alliance,alliance))read(owner,e.id);}
    public ListTag page(UUID owner,int page){var list=history(owner);int from=Math.min(list.size(),Math.max(0,page)*40);var result=new ListTag();for(var e:list.subList(from,Math.min(list.size(),from+40))){var t=encode(e);result.add(t);}return result;}
    private static CompoundTag encode(Event e){var t=new CompoundTag();t.putUUID("Id",e.id);t.putString("Kind",e.kind);if(e.peer!=null)t.putUUID("Peer",e.peer);if(e.city!=null)t.putUUID("City",e.city);if(e.alliance!=null)t.putUUID("Alliance",e.alliance);t.putString("Name",e.name);t.putLong("Time",e.time);t.putBoolean("Read",e.read);return t;}
    public static CommunityInbox load(CompoundTag root,HolderLookup.Provider lookup){if(root.getInt("Version")>1)throw new IllegalStateException("Unsupported community inbox version");var d=new CommunityInbox();for(Tag raw:root.getList("Owners",10)){var t=(CompoundTag)raw;if(!t.hasUUID("Owner")||d.events.size()>=OWNERS)continue;var list=new ArrayList<Event>();for(Tag value:t.getList("Events",10)){var e=(CompoundTag)value;if(list.size()<HISTORY&&e.hasUUID("Id")&&e.getString("Kind").length()<=32&&e.getString("Name").length()<=128)list.add(new Event(e.getUUID("Id"),e.getString("Kind"),e.hasUUID("Peer")?e.getUUID("Peer"):null,e.hasUUID("City")?e.getUUID("City"):null,e.hasUUID("Alliance")?e.getUUID("Alliance"):null,e.getString("Name"),e.getLong("Time"),e.getBoolean("Read")));}d.events.put(t.getUUID("Owner"),list);}return d;}
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup){root.putInt("Version",1);var owners=new ListTag();events.forEach((owner,list)->{var t=new CompoundTag();t.putUUID("Owner",owner);var rows=new ListTag();list.forEach(e->rows.add(encode(e)));t.put("Events",rows);owners.add(t);});root.put("Owners",owners);return root;}
}
