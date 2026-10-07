package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.city.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.*;

public record CityDirectoryPayload(UUID token, BlockPos anchor, ResourceLocation dimension, int offset, int total,
                                   List<ResourceLocation> dimensions, List<CityPublicProfile> cities) implements CustomPacketPayload {
    public static final Type<CityDirectoryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "city_directory"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CityDirectoryPayload> STREAM_CODEC = StreamCodec.of(CityDirectoryPayload::encode, CityDirectoryPayload::decode);
    public CityDirectoryPayload {
        dimensions = List.copyOf(dimensions); cities = List.copyOf(cities);
        if (offset < 0 || total < offset || (long)offset + cities.size() > total || cities.size() > CityDirectoryPager.PAGE_SIZE || dimensions.size() > 256)
            throw new IllegalArgumentException("Invalid directory page");
        if (cities.stream().anyMatch(c -> !c.dimension().equals(dimension))) throw new IllegalArgumentException("Mixed directory dimensions");
    }
    private static void encode(RegistryFriendlyByteBuf b, CityDirectoryPayload p) {
        b.writeUUID(p.token); BlockPos.STREAM_CODEC.encode(b, p.anchor); b.writeResourceLocation(p.dimension);
        b.writeVarInt(p.offset); b.writeVarInt(p.total); b.writeVarInt(p.dimensions.size());
        for (var dim : p.dimensions) b.writeResourceLocation(dim);
        b.writeVarInt(p.cities.size());
        for (var c : p.cities) {
            b.writeUUID(c.id()); b.writeUtf(c.name(), 128); b.writeResourceLocation(c.dimension()); BlockPos.STREAM_CODEC.encode(b, c.pos());
            b.writeInt(c.colour()); b.writeVarInt(c.residents()); b.writeVarInt(c.plots()); b.writeBoolean(c.own()); b.writeBoolean(c.editable());
            b.writeVarLong(c.revision()); b.writeUtf(c.identity().description(), CityIdentity.MAX_DESCRIPTION); b.writeUtf(c.identity().pixels(), CityIdentity.PIXELS);
        }
    }
    private static CityDirectoryPayload decode(RegistryFriendlyByteBuf b) {
        UUID token = b.readUUID(); BlockPos anchor = BlockPos.STREAM_CODEC.decode(b); var dimension = b.readResourceLocation();
        int offset = b.readVarInt(), total = b.readVarInt(), count = b.readVarInt();
        if (offset < 0 || total < 0 || offset > total || count < 0 || count > 256) throw new IllegalArgumentException("Invalid directory header");
        List<ResourceLocation> dimensions = new ArrayList<>(); for (int i = 0; i < count; i++) dimensions.add(b.readResourceLocation());
        count = b.readVarInt(); if (count < 0 || count > CityDirectoryPager.PAGE_SIZE || (long) offset + count > total) throw new IllegalArgumentException("Invalid directory page");
        List<CityPublicProfile> cities = new ArrayList<>();
        for (int i = 0; i < count; i++) cities.add(new CityPublicProfile(b.readUUID(), b.readUtf(128), b.readResourceLocation(), BlockPos.STREAM_CODEC.decode(b),
                b.readInt(), b.readVarInt(), b.readVarInt(), b.readBoolean(), b.readBoolean(), b.readVarLong(), new CityIdentity(b.readUtf(CityIdentity.MAX_DESCRIPTION), b.readUtf(CityIdentity.PIXELS))));
        return new CityDirectoryPayload(token, anchor, dimension, offset, total, dimensions, cities);
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
