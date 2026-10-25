package com.example.examplemod.network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.UUID;
public record WorkspaceRequest(UUID request,Action action,UUID project,UUID target,long revision,long roomRevision,int page,int category,int value,boolean flag,String text,int x1,int z1,int x2,int z2) implements CustomPacketPayload {
    public enum Action { DIRECTORY,TEAM,AVAILABILITY,VIEW,CHAT,MARK,APPROVE_MARK,REMOVE_MARK,PROJECT }
    public WorkspaceRequest{if(request==null||action==null||text==null||text.length()>256||page<0||page>100000||category<0||category>6||value<0||value>63||Math.abs((long)x1)>29999984||Math.abs((long)z1)>29999984||Math.abs((long)x2)>29999984||Math.abs((long)z2)>29999984)throw new IllegalArgumentException("Workspace request bounds");}
    public static final Type<WorkspaceRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","workspace_request_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WorkspaceRequest> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.request);b.writeEnum(p.action);b.writeBoolean(p.project!=null);if(p.project!=null)b.writeUUID(p.project);b.writeBoolean(p.target!=null);if(p.target!=null)b.writeUUID(p.target);b.writeLong(p.revision);b.writeLong(p.roomRevision);b.writeVarInt(p.page);b.writeVarInt(p.category);b.writeVarInt(p.value);b.writeBoolean(p.flag);b.writeUtf(p.text,256);b.writeInt(p.x1);b.writeInt(p.z1);b.writeInt(p.x2);b.writeInt(p.z2);},b->new WorkspaceRequest(b.readUUID(),b.readEnum(Action.class),b.readBoolean()?b.readUUID():null,b.readBoolean()?b.readUUID():null,b.readLong(),b.readLong(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readUtf(256),b.readInt(),b.readInt(),b.readInt(),b.readInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
