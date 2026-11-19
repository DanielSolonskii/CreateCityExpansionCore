package com.example.examplemod.network;

import com.example.examplemod.projects.EngineeringPlan;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public record ProjectPlanRequest(UUID request, UUID project, UUID plan, long expected, EngineeringPlan changes)
        implements CustomPacketPayload {
    public static final Type<ProjectPlanRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","project_plan_v1"));
    public static final UUID LIST = new UUID(0,0);
    public ProjectPlanRequest {
        Objects.requireNonNull(request); Objects.requireNonNull(project); Objects.requireNonNull(plan);
        if (expected < 0 || changes != null && (plan.equals(LIST) || !plan.equals(changes.id())) ||
                changes == null && expected != 0) throw new IllegalArgumentException("Invalid plan request");
    }
    public static final StreamCodec<RegistryFriendlyByteBuf,ProjectPlanRequest> CODEC=StreamCodec.of(
            (b,r)->{
                b.writeUUID(r.request); b.writeUUID(r.project); b.writeUUID(r.plan); b.writeVarLong(r.expected);
                b.writeBoolean(r.changes!=null);
                if(r.changes!=null){
                    var p=r.changes;
                    b.writeUtf(p.dimension().toString(),160); b.writeEnum(p.kind());
                    b.writeUtf(p.name(),EngineeringPlan.MAX_NAME); b.writeUtf(p.note(),EngineeringPlan.MAX_NOTE);
                    b.writeVarInt(p.nodes().size());
                    for(var n:p.nodes()){b.writeInt(n.x()); b.writeInt(n.z());}
                }
            },b->{
                var nonce=b.readUUID(); var project=b.readUUID(); var id=b.readUUID(); long version=b.readVarLong();
                EngineeringPlan doc=null;
                if(b.readBoolean()){
                    var dim=ResourceLocation.parse(b.readUtf(160)); var kind=b.readEnum(EngineeringPlan.Kind.class);
                    String name=b.readUtf(EngineeringPlan.MAX_NAME), note=b.readUtf(EngineeringPlan.MAX_NOTE);
                    int n=b.readVarInt();
                    if(n<0 || n>EngineeringPlan.MAX_NODES)throw new IllegalArgumentException("Plan node count");
                    var nodes=new ArrayList<EngineeringPlan.Node>(n);
                    for(int i=0;i<n;i++)nodes.add(new EngineeringPlan.Node(b.readInt(),b.readInt()));
                    doc=new EngineeringPlan(id,dim,kind,name,note,nodes);
                }
                return new ProjectPlanRequest(nonce,project,id,version,doc);
            });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
