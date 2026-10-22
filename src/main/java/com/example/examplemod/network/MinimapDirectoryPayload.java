package com.example.examplemod.network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** Reuses the public directory codec while isolating late HUD replies from the full atlas session. */
public record MinimapDirectoryPayload(CityDirectoryPayload directory) implements CustomPacketPayload {
 public static final Type<MinimapDirectoryPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","minimap_directory"));
 public static final StreamCodec<RegistryFriendlyByteBuf,MinimapDirectoryPayload> STREAM_CODEC=StreamCodec.of((b,p)->CityDirectoryPayload.STREAM_CODEC.encode(b,p.directory),b->new MinimapDirectoryPayload(CityDirectoryPayload.STREAM_CODEC.decode(b)));
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
