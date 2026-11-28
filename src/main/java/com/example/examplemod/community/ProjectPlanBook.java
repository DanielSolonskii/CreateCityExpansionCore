package com.example.examplemod.community;

import com.example.examplemod.network.ProjectPlanRequest;
import com.example.examplemod.projects.ProjectPlanVersion;
import net.minecraft.nbt.*;
import java.util.*;

final class ProjectPlanBook {
    static final int PER_PROJECT=16, TOTAL=2048;
    private final Map<UUID, LinkedHashMap<UUID,ProjectPlanVersion>> rooms=new HashMap<>();
    private int count;
    private CompoundTag unsupported;
    boolean available(){return unsupported==null;}
    Collection<ProjectPlanVersion> list(UUID project){
        var room=rooms.get(project);return room==null?List.of():Collections.unmodifiableCollection(room.values());
    }
    ProjectPlanVersion get(UUID project,UUID id){
        var room=rooms.get(project);return room==null?null:room.get(id);
    }
    String put(UUID actor,ProjectPlanRequest request,boolean manager){
        if(!available())return "incompatible";
        var previous=get(request.project(),request.plan()); var plan=request.changes();
        if(previous!=null && !manager && !previous.author().equals(actor))return "permission";
        if(previous!=null && previous.request().equals(request.request())){
            return previous.editor().equals(actor) && previous.version()-1==request.expected() && previous.plan().equals(plan)?"repeat":"conflict";
        }
        long version=previous==null?0:previous.version();
        if(request.expected()!=version || version==Long.MAX_VALUE)return "conflict";
        if(previous==null && (count>=TOTAL || list(request.project()).size()>=PER_PROJECT))return "limit";
        var next=new ProjectPlanVersion(request.project(),previous==null?actor:previous.author(),actor,request.request(),version+1,plan);
        rooms.computeIfAbsent(request.project(),k->new LinkedHashMap<>()).put(plan.id(),next);
        if(previous==null)count++;
        return "saved";
    }
    CompoundTag save(){
        if(unsupported!=null)return unsupported.copy();
        var tag=new CompoundTag();tag.putInt("Format",1);
        var rows=new ListTag();
        for(var room:rooms.values())for(var version:room.values())rows.add(version.save());
        tag.put("Rows",rows);return tag;
    }
    void load(CompoundTag tag){
        if(tag.isEmpty())return;
        try {
            if(tag.getInt("Format")!=1 || !tag.contains("Rows",Tag.TAG_LIST))throw new IllegalArgumentException();
            var rows=(ListTag)tag.get("Rows");
            if(!rows.isEmpty() && rows.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException();
            if(rows.size()>TOTAL)throw new IllegalArgumentException();
            for(var raw:rows){
                var row=ProjectPlanVersion.load((CompoundTag)raw);
                var room=rooms.computeIfAbsent(row.project(),k->new LinkedHashMap<>());
                if(room.size()>=PER_PROJECT || room.putIfAbsent(row.plan().id(),row)!=null)throw new IllegalArgumentException();
                count++;
            }
        } catch(RuntimeException invalid){rooms.clear();count=0;unsupported=tag.copy();}
    }
}
