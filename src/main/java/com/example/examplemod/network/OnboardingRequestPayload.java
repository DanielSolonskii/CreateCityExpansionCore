package com.example.examplemod.network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
public record OnboardingRequestPayload(Action action,int hint) implements CustomPacketPayload {
 public enum Action { STATE, SEEN, COMPLETE, SKIP }
 public static final Type<OnboardingRequestPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","onboarding_request"));
 public static final StreamCodec<RegistryFriendlyByteBuf,OnboardingRequestPayload> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeEnum(p.action);b.writeVarInt(p.hint);},b->new OnboardingRequestPayload(b.readEnum(Action.class),b.readVarInt()));
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
