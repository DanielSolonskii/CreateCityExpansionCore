package com.example.examplemod.alliance;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Additive storage: no migration of CityManager, economy or Alpha city data. */
public final class AllianceSavedData extends SavedData {
    public final AllianceBook book=new AllianceBook();
    public static AllianceSavedData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(AllianceSavedData::new,AllianceSavedData::load,null),"ccec_alliances");}
    public static AllianceSavedData load(CompoundTag root,HolderLookup.Provider lookup){
        if(root.getInt("Version")>1)throw new IllegalStateException("Unsupported CCEC alliance storage version");
        var data=new AllianceSavedData();var seen=new HashSet<UUID>();
        for(Tag raw:root.getList("Alliances",10)){
            var t=(CompoundTag)raw;if(!t.hasUUID("Id")||!AllianceBook.validText(t.getString("Name"),48,false)||!AllianceBook.validText(t.getString("Description"),180,true))throw new IllegalStateException("Invalid saved alliance");
            var a=new AllianceBook.Alliance(t.getUUID("Id"),t.getString("Name"),t.getString("Description"),t.getInt("Color")&0xFFFFFF,Math.floorMod(t.getInt("Emblem"),3));a.revision=Math.max(1,t.getLong("Revision"));
            for(Tag item:t.getList("Cities",10)){var c=(CompoundTag)item;if(!c.hasUUID("Id")||!seen.add(c.getUUID("Id")))throw new IllegalStateException("Duplicate alliance city");UUID id=c.getUUID("Id");a.cities.add(id);if(c.hasUUID("Representative"))a.representatives.put(id,c.getUUID("Representative"));}
            if(a.cities.isEmpty()||a.cities.size()>AllianceBook.MAX_CITIES)throw new IllegalStateException("Invalid alliance size");
            for(Tag item:t.getList("Invites",10)){var i=(CompoundTag)item;if(i.hasUUID("City"))a.invitations.put(i.getUUID("City"),i.getLong("Expires"));}
            for(Tag item:t.getList("Proposals",10)){var p=(CompoundTag)item;var kind=AllianceBook.Action.valueOf(p.getString("Kind"));if(kind!=AllianceBook.Action.INVITE&&kind!=AllianceBook.Action.RENAME&&kind!=AllianceBook.Action.EXCLUDE)throw new IllegalStateException("Invalid council proposal");
                var electorate=new LinkedHashSet<UUID>();for(Tag vote:p.getList("Electorate",10))electorate.add(((CompoundTag)vote).getUUID("City"));
                var proposal=new AllianceBook.Proposal(p.getUUID("Id"),kind,p.hasUUID("Target")?p.getUUID("Target"):null,p.getString("Text"),electorate,p.getLong("Expires"));
                for(Tag vote:p.getList("Votes",10)){var v=(CompoundTag)vote;if(electorate.contains(v.getUUID("City")))proposal.votes.put(v.getUUID("City"),v.getBoolean("Yes"));}a.proposals.put(proposal.id,proposal);
            }
            for(Tag item:t.getList("Chat",10)){var m=(CompoundTag)item;if(!AllianceBook.validText(m.getString("Text"),256,false))throw new IllegalStateException("Invalid saved alliance message");a.chat.add(new AllianceBook.Message(m.getUUID("Id"),m.getUUID("Author"),m.getUUID("City"),m.getString("Name"),m.getString("Text"),m.getLong("Time")));}
            if(a.invitations.size()>32||a.proposals.size()>16||a.chat.size()>200||data.book.alliances.putIfAbsent(a.id,a)!=null)throw new IllegalStateException("Invalid alliance storage limits");
        }
        if(data.book.alliances.size()>256)throw new IllegalStateException("Too many alliances");
        for(Tag raw:root.getList("Requests",10)){var t=(CompoundTag)raw;if(t.hasUUID("Id"))data.book.requests.add(t.getUUID("Id"));}
        while(data.book.requests.size()>4096)data.book.requests.remove(data.book.requests.iterator().next());return data;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup){
        root.putInt("Version",1);var list=new ListTag();for(var a:book.all()){
            var t=new CompoundTag();t.putUUID("Id",a.id);t.putString("Name",a.name);t.putString("Description",a.description);t.putInt("Color",a.color);t.putInt("Emblem",a.emblem);t.putLong("Revision",a.revision);
            var cities=new ListTag();for(UUID id:a.cities){var c=new CompoundTag();c.putUUID("Id",id);if(a.representatives.containsKey(id))c.putUUID("Representative",a.representatives.get(id));cities.add(c);}t.put("Cities",cities);
            var invites=new ListTag();a.invitations.forEach((id,expires)->{var i=new CompoundTag();i.putUUID("City",id);i.putLong("Expires",expires);invites.add(i);});t.put("Invites",invites);
            var proposals=new ListTag();for(var p:a.proposals.values()){var v=new CompoundTag();v.putUUID("Id",p.id);v.putString("Kind",p.kind.name());if(p.target!=null)v.putUUID("Target",p.target);v.putString("Text",p.text);v.putLong("Expires",p.expires);var electorate=new ListTag();for(UUID id:p.electorate){var e=new CompoundTag();e.putUUID("City",id);electorate.add(e);}v.put("Electorate",electorate);var votes=new ListTag();p.votes.forEach((id,yes)->{var q=new CompoundTag();q.putUUID("City",id);q.putBoolean("Yes",yes);votes.add(q);});v.put("Votes",votes);proposals.add(v);}t.put("Proposals",proposals);
            var chat=new ListTag();for(var m:a.chat){var v=new CompoundTag();v.putUUID("Id",m.id());v.putUUID("Author",m.author());v.putUUID("City",m.city());v.putString("Name",m.name());v.putString("Text",m.text());v.putLong("Time",m.time());chat.add(v);}t.put("Chat",chat);list.add(t);
        }root.put("Alliances",list);var requests=new ListTag();for(UUID id:book.requests){var t=new CompoundTag();t.putUUID("Id",id);requests.add(t);}root.put("Requests",requests);return root;
    }
}
