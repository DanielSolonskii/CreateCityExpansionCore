package com.example.examplemod.network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
public record WorkspaceState(CompoundTag data) implements CustomPacketPayload {
    public WorkspaceState{if(data==null||data.getList("Rows",10).size()>16||data.getList("Chat",10).size()>100||data.getList("Marks",10).size()>64||data.getList("Presence",10).size()>16||data.getList("Events",10).size()>64)throw new IllegalArgumentException("Workspace state limits");if(data.contains("ProjectPlanFormat"))validatePlan(data);data=data.copy();}
    private static void validatePlan(CompoundTag data){
        if(data.getInt("ProjectPlanFormat")!=1 || !data.hasUUID("Request") || data.getString("Result").length()>24)
            throw new IllegalArgumentException("Project plan reply");
        for(var raw:data.getList("Rows",10)){
            var row=(CompoundTag)raw;
            if(!row.hasUUID("Id") || !row.hasUUID("Author") || row.getLong("Version")<1 ||
                    row.getString("Name").length()>com.example.examplemod.projects.EngineeringPlan.MAX_NAME)
                throw new IllegalArgumentException("Project plan index");
        }
        if(data.contains("Document")){
            var version=com.example.examplemod.projects.ProjectPlanVersion.load(data.getCompound("Document"));
            if(!data.hasUUID("Project") || !version.project().equals(data.getUUID("Project")))
                throw new IllegalArgumentException("Project plan scope");
        }
    }
    public static final Type<WorkspaceState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","workspace_state_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WorkspaceState> STREAM_CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new WorkspaceState(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
