package com.example.examplemod.network;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record DailyRewardsState(CompoundTag data) implements CustomPacketPayload {
    public DailyRewardsState{if(data==null||data.getList("Cards",10).size()>7)throw new IllegalArgumentException("Daily state");data=data.copy();}
    public static final Type<DailyRewardsState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","daily_state_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DailyRewardsState> STREAM_CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new DailyRewardsState(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
