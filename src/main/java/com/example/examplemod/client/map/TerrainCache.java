package com.example.examplemod.client.map;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/** Render-thread texture pool; raw terrain persists independently of screen/anchor lifetime. */
@EventBusSubscriber(modid = "createcityexpansioncore", value = Dist.CLIENT)
final class TerrainCache {
    private static String scope;
    private static TerrainTileStore store;
    private static final LinkedHashMap<String, TerrainTileTexture> textures = new LinkedHashMap<>(64, .75f, true);
    static TerrainTileStore store(String dimension) {
        Minecraft mc = Minecraft.getInstance();
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip
                : mc.getSingleplayerServer() != null ? mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toAbsolutePath().toString() : "local";
        String key = server + ":" + (mc.player == null ? "" : mc.player.getUUID()) + ":" + dimension;
        if (!key.equals(scope)) {
            reset(); scope = key;
            store = new TerrainTileStore(mc.gameDirectory.toPath().resolve("cache/ccec-atlas"), key);
        }
        return store;
    }
    static TerrainTileTexture take(TerrainTileStore owner, int x, int z, int level) { return owner == store ? textures.remove(level + ":" + x + ":" + z) : null; }
    static void invalidate(TerrainTileStore owner, int x, int z, int level) {
        TerrainTileTexture texture = owner == store ? textures.get(level + ":" + x + ":" + z) : null;
        if (texture != null) texture.invalidate();
    }
    static void keep(TerrainTileStore owner, int x, int z, int level, TerrainTileTexture texture) {
        if (owner != store) { texture.close(); return; }
        TerrainTileTexture old = textures.put(level + ":" + x + ":" + z, texture);
        if (old != null && old != texture) old.close();
        while (textures.size() > 256) {
            var first = textures.entrySet().iterator(); first.next().getValue().close(); first.remove();
        }
    }
    static void clearTextures(TerrainTileStore owner){if(owner==store){textures.values().forEach(TerrainTileTexture::close);textures.clear();}}
    static void reset() {
        if (store != null) store.flush();
        textures.values().forEach(TerrainTileTexture::close); textures.clear(); TerrainAtlas.reset(); store = null; scope = null;
    }
    @SubscribeEvent public static void disconnected(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
}
