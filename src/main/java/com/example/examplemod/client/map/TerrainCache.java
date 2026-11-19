package com.example.examplemod.client.map;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Connection-local owners of derived terrain. Only the client/render thread accesses these maps. */
@EventBusSubscriber(modid = "createcityexpansioncore", value = Dist.CLIENT)
final class TerrainCache {
    private record Scope(UUID player, UUID world, String dimension) {}
    private record TextureKey(TerrainTileStore store, int x, int z, int level) {}
    private static final Map<Scope,TerrainTileStore> stores = new LinkedHashMap<>(4,.75f,true);
    private static final Map<TextureKey,TerrainTileTexture> textures = new LinkedHashMap<>(64,.75f,true);

    static TerrainTileStore store(MapViewBindings.Binding binding) {
        Minecraft mc = Minecraft.getInstance();
        if (binding == null || mc.player == null) return null;
        Scope scope = new Scope(mc.player.getUUID(), binding.worldId(), binding.dimension().toString());
        TerrainTileStore existing = stores.get(scope);
        if (existing != null) return existing;
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip
                : mc.getSingleplayerServer() != null ? mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().toString() : "local";
        if (stores.size() >= 2) {
            var it = stores.entrySet().iterator();
            while (it.hasNext()) {
                var entry = it.next();
                if (MapViewBindings.active(entry.getKey().dimension(), entry.getKey().world())) continue;
                clearTextures(entry.getValue());
                entry.getValue().flush();
                it.remove();
                break;
            }
        }
        TerrainTileStore value = new TerrainTileStore(mc.gameDirectory.toPath().resolve("cache/ccec-atlas"),
                server + ":" + scope.player + ":" + scope.world + ":" + scope.dimension);
        stores.put(scope, value);
        return value;
    }
    static int cachedTiles() { return stores.values().stream().mapToInt(TerrainTileStore::size).sum(); }
    static int pooledTextures() { return textures.size(); }
    static void forgetRequests(UUID view) { stores.values().forEach(s -> s.exchange.forget(view)); }
    static int inflight() { return stores.values().stream().mapToInt(s -> s.exchange.inflight()).sum(); }
    static void expire(long now) { stores.values().forEach(s -> s.exchange.expire(now)); }
    static TerrainTileTexture take(TerrainTileStore owner, int x, int z, int level) {
        return textures.remove(new TextureKey(owner,x,z,level));
    }
    static void keep(TerrainTileStore owner, int x, int z, int level, TerrainTileTexture texture) {
        if (!stores.containsValue(owner)) { texture.close(); return; }
        TerrainTileTexture old = textures.put(new TextureKey(owner,x,z,level), texture);
        if (old != null && old != texture) old.close();
        while (textures.size() > 256) {
            var it = textures.values().iterator(); it.next().close(); it.remove();
        }
    }
    static void clearTextures(TerrainTileStore owner) {
        textures.entrySet().removeIf(e -> {
            if (e.getKey().store != owner) return false;
            e.getValue().close(); return true;
        });
    }
    static void reloadTextures() {
        textures.values().forEach(TerrainTileTexture::close);
        textures.clear(); TerrainAtlas.reset();
    }
    static void reset() {
        MapViewBindings.reset();
        stores.values().forEach(TerrainTileStore::flush);
        stores.clear(); reloadTextures();
    }
    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
}
