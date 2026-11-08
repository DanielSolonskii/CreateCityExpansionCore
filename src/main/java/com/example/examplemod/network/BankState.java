package com.example.examplemod.network;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record BankState(CompoundTag data) implements CustomPacketPayload {
    public BankState{if(data==null||data.getList("Accounts",10).size()>8||data.getList("History",10).size()>100||data.getList("Requests",10).size()>64||data.getList("Reserves",10).size()>128)throw new IllegalArgumentException("Bank response bounds");data=data.copy();}
    public static final Type<BankState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","bank_state_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,BankState> STREAM_CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new BankState(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
