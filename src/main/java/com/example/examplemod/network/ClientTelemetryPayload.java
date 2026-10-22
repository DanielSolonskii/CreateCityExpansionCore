package com.example.examplemod.network;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** Numeric summaries only; server supplies identity and session, never trusts client gameplay facts. */
public record ClientTelemetryPayload(int kind,String attempt,int fps,int p95,int stalls,int tiles,int hits,int tileMs,int ping) implements CustomPacketPayload {
 public static final Type<ClientTelemetryPayload> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore","client_telemetry"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ClientTelemetryPayload> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.kind);b.writeUtf(p.attempt,36);b.writeVarInt(p.fps);b.writeVarInt(p.p95);b.writeVarInt(p.stalls);b.writeVarInt(p.tiles);b.writeVarInt(p.hits);b.writeVarInt(p.tileMs);b.writeVarInt(p.ping);},b->new ClientTelemetryPayload(b.readVarInt(),b.readUtf(36),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt(),b.readVarInt()));
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
