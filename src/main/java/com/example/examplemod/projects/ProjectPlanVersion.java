package com.example.examplemod.projects;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public record ProjectPlanVersion(UUID project, UUID author, UUID editor, UUID request, long version, EngineeringPlan plan) {
    public ProjectPlanVersion {
        Objects.requireNonNull(project); Objects.requireNonNull(author); Objects.requireNonNull(editor); Objects.requireNonNull(request);
        if(version<1 || !plan.complete())throw new IllegalArgumentException("Invalid published geometry");
    }
    public CompoundTag save(){
        CompoundTag row=new CompoundTag();
        row.putUUID("Project",project); row.putUUID("Author",author); row.putUUID("Editor",editor);
        row.putUUID("Request",request); row.putLong("Version",version); row.putUUID("Id",plan.id());
        row.putString("Dimension",plan.dimension().toString()); row.putString("Kind",plan.kind().name());
        row.putString("Name",plan.name()); row.putString("Note",plan.note());
        int[] points=new int[plan.nodes().size()*2]; int i=0;
        for(var node:plan.nodes()){points[i++]=node.x();points[i++]=node.z();}
        row.putIntArray("Nodes",points); return row;
    }
    public static ProjectPlanVersion load(CompoundTag row){
        if(!row.contains("Nodes",Tag.TAG_INT_ARRAY))throw new IllegalArgumentException("Missing nodes");
        var points=row.getIntArray("Nodes");
        if(points.length%2!=0 || points.length>EngineeringPlan.MAX_NODES*2)throw new IllegalArgumentException("Plan nodes");
        var nodes=new ArrayList<EngineeringPlan.Node>(points.length/2);
        for(int i=0;i<points.length;i+=2)nodes.add(new EngineeringPlan.Node(points[i],points[i+1]));
        var plan=new EngineeringPlan(row.getUUID("Id"),ResourceLocation.parse(row.getString("Dimension")),
            EngineeringPlan.Kind.valueOf(row.getString("Kind")),row.getString("Name"),row.getString("Note"),nodes);
        return new ProjectPlanVersion(row.getUUID("Project"),row.getUUID("Author"),row.getUUID("Editor"),
            row.getUUID("Request"),row.getLong("Version"),plan);
    }
}
