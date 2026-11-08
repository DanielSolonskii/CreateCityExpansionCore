package com.example.examplemod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.List;
import java.util.ArrayList;

/** Nearby static grants, never transport coordinates. */
public record AccessOutlinePayload(ResourceLocation dimension, List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 16384;
    public record Entry(BlockPos pos, ResourceLocation blockId) {}
    public AccessOutlinePayload { entries = List.copyOf(entries); }
    public static final Type<AccessOutlinePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "access_outlines"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AccessOutlinePayload> STREAM_CODEC = StreamCodec.of((b, p) -> {
        b.writeResourceLocation(p.dimension());
        b.writeVarInt(p.entries().size());
        for (var entry : p.entries()) { b.writeBlockPos(entry.pos()); b.writeResourceLocation(entry.blockId()); }
    }, b -> {
        var dimension = b.readResourceLocation();
        int count = b.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Too many access outlines");
        var entries = new ArrayList<Entry>(count);
        for (int i = 0; i < count; i++) entries.add(new Entry(b.readBlockPos(), b.readResourceLocation()));
        return new AccessOutlinePayload(dimension, entries);
    });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
