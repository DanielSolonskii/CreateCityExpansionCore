package com.example.examplemod.journey;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Catalogue entries are individually bounded; no player records are ever included. */
public record JourneyEditorStatePayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<JourneyEditorStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "journey_editor_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JourneyEditorStatePayload> STREAM_CODEC = StreamCodec.of(
            (b,p) -> b.writeNbt(p.data), b -> new JourneyEditorStatePayload(java.util.Objects.requireNonNull(b.readNbt())));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
