package com.example.examplemod.network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
public record WorkspaceState(CompoundTag data) implements CustomPacketPayload {
    public WorkspaceState{if(data==null||data.getList("Rows",10).size()>16||data.getList("Chat",10).size()>100||data.getList("Marks",10).size()>64||data.getList("Presence",10).size()>16||data.getList("Events",10).size()>64)throw new IllegalArgumentException("Workspace state limits");data=data.copy();}
    public static final Type<WorkspaceState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","workspace_state_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,WorkspaceState> STREAM_CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new WorkspaceState(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
