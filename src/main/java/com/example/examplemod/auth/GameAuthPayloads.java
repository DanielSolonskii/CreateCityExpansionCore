package com.example.examplemod.auth;

import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public final class GameAuthPayloads {
    private GameAuthPayloads() {}
    public record Challenge(String serverId, UUID bootId, UUID connectionId, String nonce) implements CustomPacketPayload {
        public static final Type<Challenge> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "auth_challenge"));
        public Challenge {
            if (serverId == null || !serverId.matches("[a-z0-9_-]{1,64}") || bootId == null || connectionId == null || !token(nonce))
                throw new IllegalArgumentException("Invalid authentication challenge");
        }
        public static final StreamCodec<FriendlyByteBuf, Challenge> CODEC = new StreamCodec<>() {
            public Challenge decode(FriendlyByteBuf b) {
                if (b.readVarInt() != 1) throw new IllegalArgumentException("Authentication protocol mismatch");
                return new Challenge(b.readUtf(64), b.readUUID(), b.readUUID(), b.readUtf(43));
            }
            public void encode(FriendlyByteBuf b, Challenge p) { b.writeVarInt(1); b.writeUtf(p.serverId(),64); b.writeUUID(p.bootId()); b.writeUUID(p.connectionId()); b.writeUtf(p.nonce(),43); }
        };
        public Type<Challenge> type() { return TYPE; }
    }
    public record Proof(Challenge challenge, UUID gameSessionId, String secret) implements CustomPacketPayload {
        public static final Type<Proof> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "auth_proof"));
        public Proof {
            if (challenge == null || gameSessionId == null || !token(secret)) throw new IllegalArgumentException("Invalid authentication proof");
        }
        public static final StreamCodec<FriendlyByteBuf, Proof> CODEC = new StreamCodec<>() {
            public Proof decode(FriendlyByteBuf b) { return new Proof(Challenge.CODEC.decode(b),b.readUUID(),b.readUtf(43)); }
            public void encode(FriendlyByteBuf b, Proof p) { Challenge.CODEC.encode(b,p.challenge()); b.writeUUID(p.gameSessionId()); b.writeUtf(p.secret(),43); }
        };
        public Type<Proof> type() { return TYPE; }
        @Override public String toString() { return "GameAuthProof[redacted]"; }
    }
    public static boolean token(String value) { return value != null && value.matches("[A-Za-z0-9_-]{43}"); }
}
