package com.example.examplemod.city;

import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.CityMapTilePayload;
import com.example.examplemod.network.RequestCityMapTilePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CityMapNetworkHandlers {

    private static final int MAX_REQUESTS_PER_TICK = 8;
    private static final Map<UUID, RequestWindow> REQUEST_WINDOWS = new HashMap<>();

    private CityMapNetworkHandlers() {
    }

    public static void requestTile(
            ServerPlayer player,
            RequestCityMapTilePayload request
    ) {
        BlockPos anchor = viewerPylon(player);
        if (!com.example.examplemod.policy.AtlasTileAccess.allowed(anchor != null,
                anchor != null && anchor.equals(request.pylonPos()),
                request.dimensionId().equals(viewerDimension(player)))
                || !allowRequest(player)) {
            return;
        }

        long originXLong = (long) request.tileX() * CityMapTilePayload.WORLD_SIZE;
        long originZLong = (long) request.tileZ() * CityMapTilePayload.WORLD_SIZE;
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
        // The player is about to hold this tile, so later changes to it are pushed instead of
        // waiting for a reopen that may never happen.
        atlas.watch(player.getUUID(), request.tileX(), request.tileZ());
        CityMapSavedData.TileSnapshot tile = atlas.tile(request.tileX(), request.tileZ());
        PacketDistributor.sendToPlayer(player, new CityMapTilePayload(
                request.pylonPos(),
                request.dimensionId(),
                request.tileX(),
                request.tileZ(),
                tile.complete(),
                tile.colors(),
                tile.contours()
        ));
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
        RequestWindow current = REQUEST_WINDOWS.get(player.getUUID());
        if (current == null || current.gameTime() != gameTime) {
            REQUEST_WINDOWS.put(player.getUUID(), new RequestWindow(gameTime, 1));
            return true;
        }
        if (current.count() >= MAX_REQUESTS_PER_TICK) {
            return false;
        }
        REQUEST_WINDOWS.put(player.getUUID(), new RequestWindow(gameTime, current.count() + 1));
        return true;
    }

    private record RequestWindow(long gameTime, int count) {
    }
}
