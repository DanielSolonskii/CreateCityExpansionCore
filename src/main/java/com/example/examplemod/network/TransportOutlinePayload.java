package com.example.examplemod.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Positions are plot coordinates, never physical world coordinates. */
public record TransportOutlinePayload(ResourceLocation dimension, List<Entry> entries) implements CustomPacketPayload {
    public static final int MAX_ENTRIES = 8192;
    public record Entry(UUID sublevel, BlockPos pos, ResourceLocation blockId) {}
    public TransportOutlinePayload { entries = List.copyOf(entries); }
    public static final Type<TransportOutlinePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", "transport_outlines"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TransportOutlinePayload> STREAM_CODEC = StreamCodec.of((b,p)->{
        b.writeResourceLocation(p.dimension()); b.writeVarInt(p.entries().size());
        for(var e:p.entries()){b.writeUUID(e.sublevel());b.writeBlockPos(e.pos());b.writeResourceLocation(e.blockId());}
    },b->{
        var dim=b.readResourceLocation(); int size=b.readVarInt();
        if(size<0||size>MAX_ENTRIES)throw new IllegalArgumentException("Too many transport outlines");
        var entries=new ArrayList<Entry>(size);
        for(int i=0;i<size;i++)entries.add(new Entry(b.readUUID(),b.readBlockPos(),b.readResourceLocation()));
        return new TransportOutlinePayload(dim,entries);
    });
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
