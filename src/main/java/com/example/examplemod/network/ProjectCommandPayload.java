package com.example.examplemod.network;

import com.example.examplemod.projects.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Explicit bounded fields; an untrusted client never submits an arbitrary NBT tree. */
public record ProjectCommandPayload(ProjectManager.Command command,int page,int filter) implements CustomPacketPayload {
    public static final Type<ProjectCommandPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","project_command"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ProjectCommandPayload> STREAM_CODEC=StreamCodec.of(ProjectCommandPayload::encode,ProjectCommandPayload::decode);
    // Low two bits select lifecycle/history; high two bits: all, CITY, SERVER, OP administration.
    public ProjectCommandPayload {if(page<0||page>1_000_000||filter<0||filter>15)throw new IllegalArgumentException("Invalid project page");}
    private static void encode(RegistryFriendlyByteBuf b,ProjectCommandPayload p) {
        var c=p.command;b.writeUUID(c.request());b.writeUUID(c.project());b.writeVarLong(c.revision());b.writeEnum(c.action());b.writeUUID(c.target());b.writeVarInt(c.stage());b.writeBoolean(c.done());b.writeUtf(c.note(),512);
        b.writeVarInt(p.page);b.writeVarInt(p.filter);b.writeBoolean(c.draft()!=null);
        if(c.draft()!=null){var d=c.draft();b.writeEnum(d.scope());b.writeUtf(d.title(),96);b.writeUtf(d.description(),2048);b.writeEnum(d.category());
            var z=d.zone();b.writeResourceLocation(z.dimension());b.writeEnum(z.shape());b.writeInt(z.x1());b.writeInt(z.z1());b.writeInt(z.x2());b.writeInt(z.z2());
            b.writeVarInt(d.stages().size());for(var s:d.stages())b.writeUtf(s.title(),128);b.writeEnum(d.funding());b.writeVarLong(d.reward());}
    }
    private static ProjectCommandPayload decode(RegistryFriendlyByteBuf b) {
        UUID request=b.readUUID(),id=b.readUUID();long rev=b.readVarLong();var action=b.readEnum(ProjectManager.Action.class);UUID target=b.readUUID();int stage=b.readVarInt();boolean done=b.readBoolean();String note=b.readUtf(512);
        int page=b.readVarInt(),filter=b.readVarInt();ProjectManager.Draft draft=null;
        if(b.readBoolean()){var scope=b.readEnum(Project.Scope.class);String title=b.readUtf(96),desc=b.readUtf(2048);var category=b.readEnum(Project.Category.class);
            var zone=new ProjectZone(b.readResourceLocation(),b.readEnum(ProjectZone.Shape.class),b.readInt(),b.readInt(),b.readInt(),b.readInt());
            int count=b.readVarInt();if(count<1||count>16)throw new IllegalArgumentException("Invalid stages");List<Project.Stage> stages=new ArrayList<>();for(int i=0;i<count;i++)stages.add(new Project.Stage(b.readUtf(128),false));
            draft=new ProjectManager.Draft(scope,title,desc,category,zone,stages,b.readEnum(Project.Funding.class),b.readVarLong());}
        return new ProjectCommandPayload(new ProjectManager.Command(request,id,rev,action,target,stage,done,note,draft),page,filter);
    }
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
