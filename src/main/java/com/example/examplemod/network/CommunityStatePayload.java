package com.example.examplemod.network;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record CommunityStatePayload(CompoundTag data) implements CustomPacketPayload {
    public CommunityStatePayload{if(data==null||data.getList("Relations",10).size()>16||data.getList("Inbox",10).size()>40||data.getList("Cities",10).size()>12||data.getList("Members",10).size()>16||data.getList("Invites",10).size()>64)throw new IllegalArgumentException("Community page");data=data.copy();}
    public static final Type<CommunityStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","community_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CommunityStatePayload> STREAM_CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new CommunityStatePayload(b.readNbt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
