package com.example.examplemod.network;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.profile.PlayerProfile;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

public record ProfileRequestPayload(UUID request, UUID player, boolean save, int page, PlayerProfile.Settings settings) implements CustomPacketPayload {
    public ProfileRequestPayload {
        if (page < 0 || page > 100000) throw new IllegalArgumentException("Invalid profile page");
    }
    public static final Type<ProfileRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(CreateCityExpansionCore.MODID, "profile_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfileRequestPayload> STREAM_CODEC = StreamCodec.of((b, p) -> {
        b.writeUUID(p.request); b.writeUUID(p.player); b.writeBoolean(p.save); b.writeVarInt(p.page);
        b.writeUtf(p.settings.about(), PlayerProfile.Settings.MAX_ABOUT); b.writeVarLong(p.settings.revision());
        for (var metric : p.settings.showcase()) b.writeEnum(metric);
    }, b -> {
        UUID request = b.readUUID(), player = b.readUUID(); boolean save = b.readBoolean(); int page = b.readVarInt();
        String about = b.readUtf(PlayerProfile.Settings.MAX_ABOUT); long revision = b.readVarLong();
        return new ProfileRequestPayload(request, player, save, page, new PlayerProfile.Settings(about,
                List.of(b.readEnum(PlayerProfile.Metric.class), b.readEnum(PlayerProfile.Metric.class), b.readEnum(PlayerProfile.Metric.class)), revision));
    });
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
