package com.example.examplemod.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record FreightGuideState(CompoundTag data) implements CustomPacketPayload {
    public FreightGuideState {
        if(data == null || data.getList("Checks",10).size()>12 || data.getString("Name").length()>64)
            throw new IllegalArgumentException("Freight guide response bounds");
        data=data.copy();
    }
    public static final Type<FreightGuideState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","freight_guide_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,FreightGuideState> STREAM_CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new FreightGuideState(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
