package com.example.examplemod.social;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Additive save; existing profile, city and economy formats are unchanged. */
public final class SocialSavedData extends SavedData {
    public final SocialBook book=new SocialBook();
    public static SocialSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(SocialSavedData::new,SocialSavedData::load,null),"ccec_social");
    }
    public static SocialSavedData load(CompoundTag root,HolderLookup.Provider lookup) {
        var data=new SocialSavedData();var b=data.book;b.sequence=Math.max(0,root.getLong("Sequence"));
        for(Tag raw:root.getList("Edges",Tag.TAG_COMPOUND))try {
            var t=(CompoundTag)raw;UUID a=t.getUUID("A"),c=t.getUUID("B");if(a.equals(c))continue;
            switch(t.getString("Kind")) {
                case "friend" -> b.friends.add(SocialBook.Pair.of(a,c));
                case "request" -> b.requests.add(new SocialBook.Edge(a,c));
                case "block" -> b.blocks.add(new SocialBook.Edge(a,c));
                case "read" -> b.read.put(new SocialBook.Edge(a,c),Math.max(0,t.getLong("Value")));
            }
        }catch(IllegalArgumentException ignored){}
        for(Tag raw:root.getList("Revisions",Tag.TAG_COMPOUND))try {var t=(CompoundTag)raw;b.revisions.put(t.getUUID("Id"),Math.max(0,t.getLong("Value")));}catch(IllegalArgumentException ignored){}
        for(Tag raw:root.getList("Dialogs",Tag.TAG_COMPOUND))try {
            var t=(CompoundTag)raw;var pair=SocialBook.Pair.of(t.getUUID("A"),t.getUUID("B"));if(pair.first().equals(pair.second()))continue;
            var history=new ArrayList<SocialBook.Message>();
            for(Tag item:t.getList("Messages",Tag.TAG_COMPOUND)) { var m=(CompoundTag)item;String text=m.getString("Text");UUID sender=m.getUUID("Sender");long id=m.getLong("Id");
                if(!pair.includes(sender)||text.isBlank()||text.length()>500||id<=0)continue;
                history.add(new SocialBook.Message(id,m.hasUUID("Nonce")?m.getUUID("Nonce"):UUID.nameUUIDFromBytes((pair.toString()+id).getBytes(java.nio.charset.StandardCharsets.UTF_8)),sender,m.getLong("Time"),text));b.sequence=Math.max(b.sequence,id);
            }
            history.sort(Comparator.comparingLong(SocialBook.Message::id));
            if(history.size()>SocialBook.HISTORY_LIMIT)history=new ArrayList<>(history.subList(history.size()-SocialBook.HISTORY_LIMIT,history.size()));
            b.dialogs.put(pair,history);
        }catch(IllegalArgumentException ignored){}
        // Normalize incompatible relationships after a damaged/hand-edited save.
        b.friends.removeIf(p->b.blocks.contains(new SocialBook.Edge(p.first(),p.second()))||b.blocks.contains(new SocialBook.Edge(p.second(),p.first())));
        b.requests.removeIf(e->b.friends.contains(SocialBook.Pair.of(e.from(),e.to()))||b.blocks.contains(e)||b.blocks.contains(new SocialBook.Edge(e.to(),e.from())));
        return data;
    }
    private static CompoundTag edge(UUID a,UUID b,String kind) {var t=new CompoundTag();t.putUUID("A",a);t.putUUID("B",b);t.putString("Kind",kind);return t;}
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider lookup) {
        root.putInt("Version",1);root.putLong("Sequence",book.sequence);var edges=new ListTag();
        book.friends.forEach(p->edges.add(edge(p.first(),p.second(),"friend")));
        book.requests.forEach(e->edges.add(edge(e.from(),e.to(),"request")));book.blocks.forEach(e->edges.add(edge(e.from(),e.to(),"block")));
        book.read.forEach((e,v)->{var t=edge(e.from(),e.to(),"read");t.putLong("Value",v);edges.add(t);});root.put("Edges",edges);
        var revisions=new ListTag();book.revisions.forEach((id,v)->{var t=new CompoundTag();t.putUUID("Id",id);t.putLong("Value",v);revisions.add(t);});root.put("Revisions",revisions);
        var dialogs=new ListTag();book.dialogs.forEach((p,messages)->{var t=edge(p.first(),p.second(),"dialog");var list=new ListTag();
            for(var m:messages){var tag=new CompoundTag();tag.putLong("Id",m.id());tag.putUUID("Nonce",m.nonce());tag.putUUID("Sender",m.sender());tag.putLong("Time",m.time());tag.putString("Text",m.text());list.add(tag);}t.put("Messages",list);dialogs.add(t);});
        root.put("Dialogs",dialogs);return root;
    }
}
