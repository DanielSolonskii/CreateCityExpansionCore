package com.example.examplemod.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TerrainInvalidatedPayload(UUID viewId, ResourceLocation dimension, UUID worldId, int level, int tileX, int tileZ, long version) implements CustomPacketPayload {
    public TerrainInvalidatedPayload { if (level < 0 || level > 7 || version < 0) throw new IllegalArgumentException("invalid terrain notification"); }
    public static final Type<TerrainInvalidatedPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "terrain_invalidated"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerrainInvalidatedPayload> STREAM_CODEC = StreamCodec.of(
            (b, p) -> { b.writeUUID(p.viewId); b.writeResourceLocation(p.dimension); b.writeUUID(p.worldId); b.writeByte(p.level); b.writeInt(p.tileX); b.writeInt(p.tileZ); b.writeVarLong(p.version); },
            b -> new TerrainInvalidatedPayload(b.readUUID(), b.readResourceLocation(), b.readUUID(), b.readUnsignedByte(), b.readInt(), b.readInt(), b.readVarLong()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
