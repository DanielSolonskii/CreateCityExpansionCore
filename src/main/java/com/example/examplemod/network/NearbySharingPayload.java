package com.example.examplemod.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** Only the sender's own preference. No UUID or position can be supplied. */
public record NearbySharingPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<NearbySharingPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","nearby_sharing_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf,NearbySharingPayload> STREAM_CODEC=StreamCodec.of((b,p)->b.writeBoolean(p.enabled),b->new NearbySharingPayload(b.readBoolean()));
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
