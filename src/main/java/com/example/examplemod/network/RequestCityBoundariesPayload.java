package com.example.examplemod.network;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
/** The server chooses position, dimension, range and permissions. */
public record RequestCityBoundariesPayload() implements CustomPacketPayload {
 public static final Type<RequestCityBoundariesPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","request_city_boundaries"));
 public static final StreamCodec<RegistryFriendlyByteBuf,RequestCityBoundariesPayload> STREAM_CODEC=StreamCodec.of((b,p)->{},b->new RequestCityBoundariesPayload());
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
