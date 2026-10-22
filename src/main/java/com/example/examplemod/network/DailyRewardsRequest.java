package com.example.examplemod.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;
/** The date is an expected server snapshot token, never a date chosen for reward calculation. */
public record DailyRewardsRequest(UUID request,boolean claim,long expectedDate,int wood) implements CustomPacketPayload {
    public DailyRewardsRequest{if(request==null||wood<0||wood>=8)throw new IllegalArgumentException("Daily request");}
    public static final Type<DailyRewardsRequest> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","daily_request_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,DailyRewardsRequest> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.request);b.writeBoolean(p.claim);b.writeLong(p.expectedDate);b.writeVarInt(p.wood);},b->new DailyRewardsRequest(b.readUUID(),b.readBoolean(),b.readLong(),b.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
