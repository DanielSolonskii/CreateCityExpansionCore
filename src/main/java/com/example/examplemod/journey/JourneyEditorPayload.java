package com.example.examplemod.journey;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** A single bounded edit, never an upload of player progress or the entire world data. */
public record JourneyEditorPayload(String action, String group, String id, String base, String value)
        implements CustomPacketPayload {
    public static final Type<JourneyEditorPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "journey_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JourneyEditorPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { if(p.value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>30000)throw new IllegalArgumentException("Edit too large"); b.writeUtf(p.action, 16); b.writeUtf(p.group, 16); b.writeUtf(p.id, 64); b.writeUtf(p.base, 64); b.writeUtf(p.value, 24000); },
            b -> new JourneyEditorPayload(b.readUtf(16), b.readUtf(16), b.readUtf(64), b.readUtf(64), b.readUtf(24000)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
