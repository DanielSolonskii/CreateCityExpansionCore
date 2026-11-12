package com.example.examplemod.journey;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record JourneyRequestPayload(Action action,String id) implements CustomPacketPayload {
    public enum Action { STATE, JOURNAL, TRACK_QUEST, TRACK_MARKER, UNPIN, START, PAUSE, RESUME }
    public static final Type<JourneyRequestPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","journey_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf,JourneyRequestPayload> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeEnum(p.action);b.writeUtf(p.id,64);},b->new JourneyRequestPayload(b.readEnum(Action.class),b.readUtf(64)));
    public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
