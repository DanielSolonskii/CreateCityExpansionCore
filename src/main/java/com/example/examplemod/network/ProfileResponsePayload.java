package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.profile.PlayerProfile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** Typed, bounded public DTO. A null profile is an explicit error, never fabricated zero statistics. */
public record ProfileResponsePayload(UUID request, String result, PlayerProfile profile) implements CustomPacketPayload {
    public static final Type<ProfileResponsePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "profile_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileResponsePayload> STREAM_CODEC = StreamCodec.of((b, p) -> {
        b.writeUUID(p.request); b.writeUtf(p.result, 24); b.writeBoolean(p.profile != null);
        if (p.profile == null) return;
        var v = p.profile;
        b.writeUUID(v.id()); b.writeUtf(v.name(), 64); b.writeBoolean(v.online()); b.writeUtf(v.city(), 128); b.writeUtf(v.cityRole(), 24);
        b.writeUtf(v.settings().about(), PlayerProfile.Settings.MAX_ABOUT); b.writeVarLong(v.settings().revision());
        for (var metric : v.settings().showcase()) b.writeEnum(metric);
        b.writeUtf(v.skinName(), 64); b.writeUtf(v.texture(), 16384); b.writeUtf(v.signature(), 2048);
        for (long value : v.values()) b.writeVarLong(value);
        b.writeVarInt(v.completed()); b.writeVarInt(v.page()); b.writeVarInt(v.projects().size());
        for (var entry : v.projects()) { b.writeUUID(entry.id()); b.writeUtf(entry.title(), 96); b.writeBoolean(entry.server()); b.writeByte(entry.roles()); }
    }, b -> {
        UUID request = b.readUUID(); String result = b.readUtf(24);
        if (!b.readBoolean()) return new ProfileResponsePayload(request, result, null);
        UUID id = b.readUUID(); String name = b.readUtf(64); boolean online = b.readBoolean(); String city = b.readUtf(128), role = b.readUtf(24);
        String about = b.readUtf(PlayerProfile.Settings.MAX_ABOUT); long revision = b.readVarLong();
        var settings = new PlayerProfile.Settings(about, List.of(b.readEnum(PlayerProfile.Metric.class), b.readEnum(PlayerProfile.Metric.class), b.readEnum(PlayerProfile.Metric.class)), revision);
        String skinName = b.readUtf(64), texture = b.readUtf(16384), signature = b.readUtf(2048);
        List<Long> values = new ArrayList<>(); for (var ignored : PlayerProfile.Metric.values()) values.add(b.readVarLong());
        int completed = b.readVarInt(), page = b.readVarInt(), count = b.readVarInt();
        if (count < 0 || count > PlayerProfile.PAGE_SIZE) throw new IllegalArgumentException("Invalid project count");
        List<PlayerProfile.ProjectEntry> projects = new ArrayList<>();
        for (int i = 0; i < count; i++) projects.add(new PlayerProfile.ProjectEntry(b.readUUID(), b.readUtf(96), b.readBoolean(), b.readUnsignedByte()));
        return new ProfileResponsePayload(request, result, new PlayerProfile(id, name, online, city, role, settings, skinName, texture, signature, values, completed, page, projects));
    });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
