package com.example.examplemod.network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record MapBorderPayload(ResourceLocation dimension,double x,double z,double size,double target,long remaining) implements CustomPacketPayload {
 public static final Type<MapBorderPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","map_border"));
 public static final StreamCodec<RegistryFriendlyByteBuf,MapBorderPayload> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeResourceLocation(p.dimension);b.writeDouble(p.x);b.writeDouble(p.z);b.writeDouble(p.size);b.writeDouble(p.target);b.writeVarLong(p.remaining);},b->new MapBorderPayload(b.readResourceLocation(),b.readDouble(),b.readDouble(),b.readDouble(),b.readDouble(),b.readVarLong()));
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
