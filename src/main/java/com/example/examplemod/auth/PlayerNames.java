package com.example.examplemod.auth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/** Server-owned presentation names. UUIDs and login profiles remain unchanged. */
public final class PlayerNames extends SavedData {
    private final Map<UUID, String> names = new HashMap<>();
    private final java.util.Set<UUID> inspected = new java.util.HashSet<>();
    public static boolean usable(String name) {
        return name != null && name.matches("[A-Za-z0-9_]{3,16}") && !name.matches("cs[a-z0-9]{14}");
    }
    private static PlayerNames get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlayerNames::new, PlayerNames::load, null), "ccec_player_names");
    }
    public static void remember(MinecraftServer server, UUID id, String name) {
        if (!usable(name)) return;
        PlayerNames data = get(server);
        if (!name.equals(data.names.put(id, name))) data.setDirty();
    }
    public static String name(ServerPlayer player) {
        String verified = AuthenticationBootstrap.verifiedDisplayName(player);
        if (usable(verified)) {
            remember(player.getServer(), player.getUUID(), verified);
            return verified;
        }
        String profile = player.getGameProfile().getName();
        if (usable(profile)) return profile;
        return name(player.getServer(), player.getUUID());
    }
    public static String name(MinecraftServer server, UUID id) {
        ServerPlayer online = server.getPlayerList().getPlayer(id);
        if (online != null) {
            String verified = AuthenticationBootstrap.verifiedDisplayName(online);
            if (usable(verified)) { remember(server, id, verified); return verified; }
            if (usable(online.getGameProfile().getName())) return online.getGameProfile().getName();
        }
        String saved = get(server).names.get(id);
        if (usable(saved)) return saved;
        // Older authenticated players already have the server-owned display name in playerdata.
        // Inspect each missing entry once, rather than doing disk I/O on every GUI refresh.
        if (get(server).inspected.add(id)) {
            var file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR).resolve(id + ".dat");
            if (java.nio.file.Files.isRegularFile(file)) try {
                CompoundTag tag = net.minecraft.nbt.NbtIo.readCompressed(file, net.minecraft.nbt.NbtAccounter.create(16L * 1024 * 1024));
                var component = net.minecraft.network.chat.Component.Serializer.fromJson(tag.getString("CustomName"), server.registryAccess());
                String legacy = component == null ? null : component.getString();
                if (usable(legacy)) { remember(server, id, legacy); return legacy; }
            } catch (Exception ignored) { /* Missing/corrupt legacy data cannot block a player list. */ }
        }
        var cache = server.getProfileCache();
        String cached = cache == null ? null : cache.get(id).map(p -> p.getName()).orElse(null);
        return usable(cached) ? cached : "Игрок " + id.toString().substring(0, 8);
    }
    public static PlayerNames load(CompoundTag tag, HolderLookup.Provider lookup) {
        PlayerNames result = new PlayerNames();
        for (String key : tag.getAllKeys()) {
            try { String value = tag.getString(key); if (usable(value)) result.names.put(UUID.fromString(key), value); }
            catch (IllegalArgumentException ignored) { }
        }
        return result;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        names.forEach((id, name) -> tag.putString(id.toString(), name));
        return tag;
    }
}
