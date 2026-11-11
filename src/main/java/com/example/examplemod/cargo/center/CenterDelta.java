package com.example.examplemod.cargo.center;

import net.minecraft.nbt.*;
import java.util.*;

/** Small view-only NBT patches. Removed objects/permissions disappear immediately from client caches. */
public final class CenterDelta {
    private CenterDelta() {}
    public static CompoundTag diff(CompoundTag before,CompoundTag after){var patch=new CompoundTag();patch.putBoolean("$merge",true);var removed=new ListTag();for(String key:before.getAllKeys())if(!after.contains(key))removed.add(StringTag.valueOf(key));if(!removed.isEmpty())patch.put("$delete",removed);
        for(String key:after.getAllKeys()){Tag value=after.get(key),old=before.get(key);if(Objects.equals(old,value))continue;
            if(value instanceof CompoundTag next&&old instanceof CompoundTag previous)patch.put(key,diff(previous,next));
            else if(Set.of("Rows","Markers").contains(key)&&value instanceof ListTag next&&old instanceof ListTag previous)patch.put(key,rows(previous,next));
            else patch.put(key,value.copy());}return patch;
    }
    private static String identity(CompoundTag t){return t.getString("Kind")+"/"+t.getUUID("Id");}
    private static LinkedHashMap<String,CompoundTag> index(ListTag rows){var map=new LinkedHashMap<String,CompoundTag>();for(Tag raw:rows){var t=(CompoundTag)raw;map.put(identity(t),t);}return map;}
    private static CompoundTag rows(ListTag before,ListTag after){var old=index(before);var next=index(after);var patch=new CompoundTag();patch.putBoolean("$rows",true);var removed=new ListTag();for(String key:old.keySet())if(!next.containsKey(key))removed.add(StringTag.valueOf(key));patch.put("Remove",removed);var updated=new ListTag();for(var entry:next.entrySet())if(!entry.getValue().equals(old.get(entry.getKey())))updated.add(entry.getValue().copy());patch.put("Upsert",updated);
        if(!new ArrayList<>(old.keySet()).equals(new ArrayList<>(next.keySet()))){var order=new ListTag();for(String key:next.keySet())order.add(StringTag.valueOf(key));patch.put("Order",order);}return patch;
    }
    public static CompoundTag apply(CompoundTag base,CompoundTag patch){if(!patch.getBoolean("$merge"))return patch.copy();var result=base.copy();for(Tag key:patch.getList("$delete",8))result.remove(key.getAsString());
        for(String key:patch.getAllKeys()){if(key.startsWith("$"))continue;var value=patch.get(key);
            if(value instanceof CompoundTag nested&&nested.getBoolean("$rows")){var rows=index(result.getList(key,10));for(Tag id:nested.getList("Remove",8))rows.remove(id.getAsString());for(Tag raw:nested.getList("Upsert",10)){var row=(CompoundTag)raw;rows.put(identity(row),row.copy());}var list=new ListTag();if(nested.contains("Order")){for(Tag id:nested.getList("Order",8)){var row=rows.get(id.getAsString());if(row!=null)list.add(row.copy());}}else for(var row:rows.values())list.add(row.copy());result.put(key,list);}
            else if(value instanceof CompoundTag nested&&nested.getBoolean("$merge"))result.put(key,apply(result.getCompound(key),nested));else result.put(key,value.copy());}return result;
    }
}
