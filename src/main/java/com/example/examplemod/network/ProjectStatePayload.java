package com.example.examplemod.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server-generated page, at most 16 projects. Also acknowledges the command request. */
public record ProjectStatePayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<ProjectStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","project_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ProjectStatePayload> STREAM_CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new ProjectStatePayload(b.readNbt()));
    public ProjectStatePayload {if(data==null)throw new IllegalArgumentException("Missing snapshot");data=data.copy();}
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
