package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Public identity only: no balances, coordinates, private counters or authentication aliases. */
public record ProfileDirectoryPayload(UUID request, String query, String result, int page, int total, List<Entry> entries) implements CustomPacketPayload {
    public static final int PAGE_SIZE = 8;
    public record Entry(UUID id, String name, boolean online, String city) {
        public Entry {
            if (id == null || name == null || name.length() > 16 || city == null || city.length() > 128)
                throw new IllegalArgumentException("Invalid directory entry");
        }
    }
    public ProfileDirectoryPayload {
        if (request == null || query == null || query.length() > 16 || result == null || result.length() > 24
                || page < 0 || page > 100000 || total < 0 || entries == null || entries.size() > PAGE_SIZE)
            throw new IllegalArgumentException("Invalid profile directory");
        entries = List.copyOf(entries);
    }
    public static final Type<ProfileDirectoryPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID,"profile_directory"));
    public static final StreamCodec<RegistryFriendlyByteBuf,ProfileDirectoryPayload> STREAM_CODEC = StreamCodec.of((b,p) -> {
        b.writeUUID(p.request); b.writeUtf(p.query,16); b.writeUtf(p.result,24); b.writeVarInt(p.page); b.writeVarInt(p.total); b.writeVarInt(p.entries.size());
        for (Entry e : p.entries) { b.writeUUID(e.id); b.writeUtf(e.name,16); b.writeBoolean(e.online); b.writeUtf(e.city,128); }
    }, b -> {
        UUID id=b.readUUID(); String query=b.readUtf(16), result=b.readUtf(24); int page=b.readVarInt(), total=b.readVarInt(), count=b.readVarInt();
        if (count < 0 || count > PAGE_SIZE) throw new IllegalArgumentException("Invalid directory count");
        List<Entry> entries=new ArrayList<>(count);
        for(int i=0;i<count;i++) entries.add(new Entry(b.readUUID(),b.readUtf(16),b.readBoolean(),b.readUtf(128)));
        return new ProfileDirectoryPayload(id,query,result,page,total,entries);
    });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
