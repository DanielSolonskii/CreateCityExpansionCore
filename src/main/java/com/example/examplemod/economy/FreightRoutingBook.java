package com.example.examplemod.economy;

import net.minecraft.nbt.*;
import java.util.*;

/** Routing intent and immutable per-token destinations, inside the existing Freight SavedData. */
public final class FreightRoutingBook {
    public static final int MAX_HUBS=4;
    public record Plan(List<UUID> hubs){public Plan{hubs=List.copyOf(hubs);if(hubs.size()>MAX_HUBS||new HashSet<>(hubs).size()!=hubs.size())throw new IllegalArgumentException("Invalid transit stops");}}
    private final Map<UUID,Plan> plans=new LinkedHashMap<>();
    private final Map<UUID,List<UUID>> tokenPaths=new LinkedHashMap<>();
    public boolean regional(UUID subject){return plans.containsKey(subject);}
    public List<UUID> hubs(UUID subject){var p=plans.get(subject);return p==null?List.of():p.hubs();}
    public void set(UUID subject,List<UUID> hubs){plans.put(subject,new Plan(hubs));}
    public void direct(UUID subject){plans.remove(subject);}
    public List<UUID> path(UUID token){return tokenPaths.getOrDefault(token,List.of());}
    public void freeze(UUID token,UUID subject,UUID destination){
        if(!regional(subject)||tokenPaths.containsKey(token))return;
        var path=new ArrayList<>(hubs(subject));path.add(destination);
        if(new HashSet<>(path).size()!=path.size())throw new IllegalStateException("Repeated final destination");
        tokenPaths.put(token,List.copyOf(path));
    }
    public UUID next(UUID token,UUID hub,int cursor){
        var path=path(token);return cursor>=0&&cursor<path.size()-1&&path.get(cursor).equals(hub)?path.get(cursor+1):null;
    }
    public CompoundTag save(){
        var out=new CompoundTag();out.putInt("Version",1);var ps=new ListTag();
        plans.forEach((id,p)->{var t=new CompoundTag();t.putUUID("Subject",id);t.put("Hubs",ids(p.hubs));ps.add(t);});out.put("Plans",ps);
        var ts=new ListTag();tokenPaths.forEach((id,path)->{var t=new CompoundTag();t.putUUID("Token",id);t.put("Path",ids(path));ts.add(t);});out.put("TokenPaths",ts);return out;
    }
    public void load(CompoundTag t){
        plans.clear();tokenPaths.clear();if(t.isEmpty())return;
        if(t.getInt("Version")!=1)throw new IllegalArgumentException("Unsupported Freight routing version");
        for(Tag raw:t.getList("Plans",10)){var p=(CompoundTag)raw;if(!p.hasUUID("Subject"))throw new IllegalArgumentException("Missing transit subject");set(p.getUUID("Subject"),read(p.getList("Hubs",10),MAX_HUBS));}
        for(Tag raw:t.getList("TokenPaths",10)){var p=(CompoundTag)raw;var path=read(p.getList("Path",10),MAX_HUBS+1);if(!p.hasUUID("Token")||path.isEmpty())throw new IllegalArgumentException("Invalid token route");tokenPaths.put(p.getUUID("Token"),path);}
    }
    private static ListTag ids(List<UUID> values){var list=new ListTag();for(UUID id:values){var t=new CompoundTag();t.putUUID("Id",id);list.add(t);}return list;}
    private static List<UUID> read(ListTag list,int max){
        if(list.size()>max)throw new IllegalArgumentException("Too many transit stops");var result=new ArrayList<UUID>();
        for(Tag raw:list){var t=(CompoundTag)raw;if(!t.hasUUID("Id")||result.contains(t.getUUID("Id")))throw new IllegalArgumentException("Invalid transit stop");result.add(t.getUUID("Id"));}return List.copyOf(result);
    }
}
