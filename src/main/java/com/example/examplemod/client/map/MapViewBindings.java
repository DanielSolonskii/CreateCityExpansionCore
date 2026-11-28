package com.example.examplemod.client.map;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Only validated directory handlers may install a binding. All access is on the client thread. */
public final class MapViewBindings {
    public record Binding(BlockPos anchor, ResourceLocation dimension, UUID viewId, UUID worldId) {}
    private static Binding atlas, minimap;
    private MapViewBindings() {}

    public static Binding binding(boolean hud, BlockPos anchor, ResourceLocation dimension) {
        Binding value = hud ? minimap : atlas;
        return value != null && value.anchor().equals(anchor) && value.dimension().equals(dimension) ? value : null;
    }

    static boolean active(String dimension, UUID world) {
        return matches(atlas, dimension, world) || matches(minimap, dimension, world);
    }
    private static boolean matches(Binding b, String dimension, UUID world) {
        return b != null && b.worldId().equals(world) && b.dimension().toString().equals(dimension);
    }
    static Binding find(UUID viewId) {
        if (atlas != null && atlas.viewId().equals(viewId)) return atlas;
        return minimap != null && minimap.viewId().equals(viewId) ? minimap : null;
    }

    public static void confirm(boolean hud, BlockPos anchor, ResourceLocation dimension, UUID viewId, UUID worldId) {
        Binding value = new Binding(anchor.immutable(), dimension, viewId, worldId);
        Binding old = hud ? minimap : atlas;
        if (value.equals(old)) return;
        invalidate(hud);
        if (hud) minimap = value;
        else atlas = value;
    }

    public static void invalidate(boolean hud) {
        Binding old = hud ? minimap : atlas;
        if (old != null) TerrainCache.forgetRequests(old.viewId());
        if (hud) minimap = null;
        else atlas = null;
    }

    public static void reset() { invalidate(false); invalidate(true); }
}
