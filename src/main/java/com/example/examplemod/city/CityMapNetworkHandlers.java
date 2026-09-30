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
        if (!(player.containerMenu instanceof CityPylonMenu menu)
                || !menu.getPylonPos().equals(request.pylonPos())
                || !menu.getDimensionId().equals(request.dimensionId())
                || !player.serverLevel().dimension().location().equals(request.dimensionId())
                || player.distanceToSqr(
                        request.pylonPos().getX() + 0.5,
                        request.pylonPos().getY() + 0.5,
                        request.pylonPos().getZ() + 0.5
                ) > 64.0
                || CityManager.get(player.getServer().overworld())
                .getCityByPylon(request.dimensionId(), request.pylonPos()) == null
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

        CityMapSavedData.TileSnapshot tile = CityMapSavedData.get(player.serverLevel())
                .tile(request.tileX(), request.tileZ());
        PacketDistributor.sendToPlayer(player, new CityMapTilePayload(
                request.pylonPos(),
                request.dimensionId(),
                request.tileX(),
                request.tileZ(),
                tile.complete(),
                tile.colors()
        ));
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
