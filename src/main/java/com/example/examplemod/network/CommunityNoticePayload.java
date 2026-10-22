package com.example.examplemod.network;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record CommunityNoticePayload(UUID peer,String name,String kind) implements CustomPacketPayload {
    public static final Type<CommunityNoticePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","community_notice"));
    public static final StreamCodec<RegistryFriendlyByteBuf,CommunityNoticePayload> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.peer);b.writeUtf(p.name,64);b.writeUtf(p.kind,24);},b->new CommunityNoticePayload(b.readUUID(),b.readUtf(64),b.readUtf(24)));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
