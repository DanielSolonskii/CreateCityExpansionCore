package com.example.examplemod.network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record OnboardingStatePayload(int version,int seen,int completed,boolean skipped,String city,String role,boolean openHelp) implements CustomPacketPayload {
 public static final Type<OnboardingStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","onboarding_state"));
 public static final StreamCodec<RegistryFriendlyByteBuf,OnboardingStatePayload> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.version);b.writeVarInt(p.seen);b.writeVarInt(p.completed);b.writeBoolean(p.skipped);b.writeUtf(p.city,128);b.writeUtf(p.role,32);b.writeBoolean(p.openHelp);},b->new OnboardingStatePayload(b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readBoolean(),b.readUtf(128),b.readUtf(32),b.readBoolean()));
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
