package com.example.examplemod.city;

import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.CityMapTilePayload;
import com.example.examplemod.network.RequestCityMapTilePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;

public final class CityMapNetworkHandlers {

    private static final int MAX_REQUESTS_PER_TICK = 8;
    private static final Map<net.minecraft.server.network.ServerGamePacketListenerImpl, RequestWindow> REQUEST_WINDOWS = new java.util.WeakHashMap<>();

    private CityMapNetworkHandlers() {
    }

    public static void requestTile(
            ServerPlayer player,
            RequestCityMapTilePayload request
    ) {
        var view = CityPublicAtlas.view(player, request.viewId());
        if (!com.example.examplemod.policy.AtlasTileAccess.allowed(view != null,
                view != null && view.anchor().equals(request.pylonPos()),
                view != null && view.dimension().equals(request.dimensionId()))
                || !allowRequest(player)) return;

        long originXLong = (long) request.tileX() * (CityMapTilePayload.WORLD_SIZE<<request.level());
        long originZLong = (long) request.tileZ() * (CityMapTilePayload.WORLD_SIZE<<request.level());
        if (originXLong < Integer.MIN_VALUE
                || originXLong > Integer.MAX_VALUE
                || originZLong < Integer.MIN_VALUE
                || originZLong > Integer.MAX_VALUE) {
            return;
        }

        ServerLevel mapLevel = player.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, request.dimensionId()));
        if (mapLevel == null) return;
        CityMapSavedData atlas = CityMapSavedData.get(mapLevel);
        if (!atlas.worldId().equals(view.worldId()) || !view.worldId().equals(request.worldId())) return;
        var key = new TerrainPyramid.Key(request.level(), request.tileX(), request.tileZ());
        atlas.prioritize(key, player.serverLevel() == mapLevel ? player.blockPosition() : view.anchor());
        var snapshot = atlas.snapshot(key);
        boolean pending = snapshot == null;
        boolean unchanged = !pending && request.revision() == snapshot.hash();
        atlas.watch(player.getUUID(), request.viewId(), key, mapLevel.getGameTime(),
                pending ? -1 : snapshot.version());
        boolean hasBody = !pending && !unchanged;
        PacketDistributor.sendToPlayer(player, new CityMapTilePayload(view.anchor(), view.dimension(),
                key.x(), key.z(), !pending && snapshot.complete(), hasBody ? snapshot.colors() : new int[0],
                new java.util.BitSet(), key.level(), hasBody ? snapshot.heights() : new short[0],
                view.worldId(), pending ? 0 : snapshot.hash(), unchanged, view.viewId(), request.requestId(),
                pending ? 0 : snapshot.version(), pending));
    }

    public static void invalidate(ServerPlayer player, UUID viewId, net.minecraft.resources.ResourceLocation dimension,
                                  UUID worldId, TerrainPyramid.Key key, long version) {
        var view = CityPublicAtlas.view(player, viewId);
        if (view != null && dimension.equals(view.dimension()) && worldId.equals(view.worldId()))
            PacketDistributor.sendToPlayer(player, new com.example.examplemod.network.TerrainInvalidatedPayload(
                    viewId, dimension, worldId, key.level(), key.x(), key.z(), version));
    }

    /** Read-only atlas access; the server chooses the anchor, never the packet. */
    public static BlockPos viewerPylon(ServerPlayer player) {
        CityManager manager = CityManager.get(player.getServer().overworld());
        var dimension = player.serverLevel().dimension().location();
        if (player.containerMenu instanceof CityPylonMenu menu
                && dimension.equals(menu.getDimensionId())
                && player.distanceToSqr(menu.getPylonPos().getCenter()) <= 64.0
                && manager.getCityByPylon(dimension, menu.getPylonPos()) != null) {
            return menu.getPylonPos();
        }
        City city = manager.getMemberCity(player.getUUID());
        var publicView = CityPublicAtlas.view(player);
        if (publicView != null) return publicView.anchor();
        return city != null && city.hasMember(player.getUUID()) ? city.getPylonPos() : null;
    }

    public static net.minecraft.resources.ResourceLocation viewerDimension(ServerPlayer player) {
        var anchor = viewerPylon(player);
        if (anchor == null) return null;
        if (player.containerMenu instanceof CityPylonMenu menu && anchor.equals(menu.getPylonPos())
                && menu.getDimensionId().equals(player.serverLevel().dimension().location())
                && player.distanceToSqr(menu.getPylonPos().getCenter()) <= 64.0
                && CityManager.get(player.getServer().overworld()).getCityByPylon(menu.getDimensionId(), menu.getPylonPos()) != null) return menu.getDimensionId();
        var publicView = CityPublicAtlas.view(player);
        if (publicView != null) return publicView.dimension();
        City city = CityManager.get(player.getServer().overworld()).getMemberCity(player.getUUID());
        return city != null && city.hasMember(player.getUUID()) ? city.getPylonDimension() : null;
    }

    private static boolean allowRequest(ServerPlayer player) {
        long gameTime = player.serverLevel().getGameTime();
        RequestWindow current = REQUEST_WINDOWS.get(player.connection);
        if (current == null || current.gameTime() != gameTime) {
            REQUEST_WINDOWS.put(player.connection, new RequestWindow(gameTime, 1));
            return true;
        }
        if (current.count() >= MAX_REQUESTS_PER_TICK) {
            return false;
        }
        REQUEST_WINDOWS.put(player.connection, new RequestWindow(gameTime, current.count() + 1));
        return true;
    }

    private record RequestWindow(long gameTime, int count) {
    }
}
