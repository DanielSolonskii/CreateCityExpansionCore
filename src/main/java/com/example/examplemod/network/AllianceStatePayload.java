package com.example.examplemod.network;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record AllianceStatePayload(CompoundTag data) implements CustomPacketPayload {
    public AllianceStatePayload{if(data==null||data.getList("Directory",10).size()>32||data.getList("Chat",10).size()>40||data.getList("Cities",10).size()>32||data.getList("Proposals",10).size()>16)throw new IllegalArgumentException("Alliance state limits");data=data.copy();}
    public static final Type<AllianceStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","alliance_state_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,AllianceStatePayload> STREAM_CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new AllianceStatePayload(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
