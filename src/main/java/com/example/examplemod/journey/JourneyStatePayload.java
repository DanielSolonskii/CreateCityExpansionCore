package com.example.examplemod.journey;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record JourneyStatePayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<JourneyStatePayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","journey_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,JourneyStatePayload> STREAM_CODEC=StreamCodec.of((b,p)->b.writeNbt(p.data),b->new JourneyStatePayload(java.util.Objects.requireNonNull(b.readNbt())));
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
