package com.example.examplemod.city;

import com.example.examplemod.block.CityPylonBlock;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.CityMapTilePayload;
import com.example.examplemod.network.RequestCityMapTilePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CityMapNetworkHandlers {

    private static final int MAX_DISTANCE_FROM_PYLON = 512;
    private static final int MAX_REQUESTS_PER_TICK = 8;
    private static final int UNKNOWN_COLOR = 0xFF101719;
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
                || !(player.serverLevel().getBlockState(request.pylonPos()).getBlock()
                instanceof CityPylonBlock)
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
        long centerX = originXLong + CityMapTilePayload.WORLD_SIZE / 2L;
        long centerZ = originZLong + CityMapTilePayload.WORLD_SIZE / 2L;
        City city = CityManager.get(player.getServer().overworld())
                .getCityByPylon(request.dimensionId(), request.pylonPos());
        if (originXLong < Integer.MIN_VALUE
                || originXLong > Integer.MAX_VALUE
                || originZLong < Integer.MIN_VALUE
                || originZLong > Integer.MAX_VALUE
                || !isAuthorizedMapArea(city, request.dimensionId(), centerX, centerZ)) {
            return;
        }

        int originX = (int) originXLong;
        int originZ = (int) originZLong;
        SampledTile tile = sampleTile(player.serverLevel(), originX, originZ);
        PacketDistributor.sendToPlayer(player, new CityMapTilePayload(
                request.pylonPos(),
                request.dimensionId(),
                request.tileX(),
                request.tileZ(),
                tile.complete(),
                tile.colors()
        ));
    }

    private static boolean isAuthorizedMapArea(
            City city,
            net.minecraft.resources.ResourceLocation dimensionId,
            long centerX,
            long centerZ
    ) {
        if (city.getPylonDimension().equals(dimensionId)
                && Math.abs(centerX - city.getPylonPos().getX()) <= MAX_DISTANCE_FROM_PYLON
                && Math.abs(centerZ - city.getPylonPos().getZ()) <= MAX_DISTANCE_FROM_PYLON) {
            return true;
        }
        return city.getProjects().stream().anyMatch(project ->
                project.dimensionId().equals(dimensionId)
                        && Math.abs(centerX - project.controllerPos().getX()) <= MAX_DISTANCE_FROM_PYLON
                        && Math.abs(centerZ - project.controllerPos().getZ()) <= MAX_DISTANCE_FROM_PYLON
        );
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

    private static SampledTile sampleTile(ServerLevel level, int originX, int originZ) {
        int chunksPerTile = CityMapTilePayload.WORLD_SIZE / 16;
        boolean[][] loadedChunks = new boolean[chunksPerTile][chunksPerTile];
        boolean complete = true;
        for (int chunkZ = 0; chunkZ < chunksPerTile; chunkZ++) {
            for (int chunkX = 0; chunkX < chunksPerTile; chunkX++) {
                BlockPos chunkProbe = new BlockPos(
                        originX + chunkX * 16,
                        level.getMinBuildHeight(),
                        originZ + chunkZ * 16
                );
                loadedChunks[chunkZ][chunkX] = level.hasChunkAt(chunkProbe);
                complete &= loadedChunks[chunkZ][chunkX];
            }
        }

        int[] baseColors = new int[CityMapTilePayload.COLOR_COUNT];
        int[] heights = new int[CityMapTilePayload.COLOR_COUNT];
        for (int sampleZ = 0; sampleZ < CityMapTilePayload.SAMPLE_COUNT; sampleZ++) {
            for (int sampleX = 0; sampleX < CityMapTilePayload.SAMPLE_COUNT; sampleX++) {
                int worldX = originX + sampleX * CityMapTilePayload.SAMPLE_STEP;
                int worldZ = originZ + sampleZ * CityMapTilePayload.SAMPLE_STEP;
                int index = sampleZ * CityMapTilePayload.SAMPLE_COUNT + sampleX;
                int chunkX = sampleX * CityMapTilePayload.SAMPLE_STEP / 16;
                int chunkZ = sampleZ * CityMapTilePayload.SAMPLE_STEP / 16;
                if (!loadedChunks[chunkZ][chunkX]) {
                    baseColors[index] = UNKNOWN_COLOR;
                    heights[index] = Integer.MIN_VALUE;
                    continue;
                }
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
                BlockPos surface = new BlockPos(worldX, surfaceY, worldZ);
                BlockState state = level.getBlockState(surface);
                MapColor mapColor = state.getMapColor(level, surface);
                baseColors[index] = mapColor == MapColor.NONE ? 0x2A3031 : mapColor.col;
                heights[index] = surfaceY;
            }
        }

        int[] colors = new int[CityMapTilePayload.COLOR_COUNT];
        for (int sampleZ = 0; sampleZ < CityMapTilePayload.SAMPLE_COUNT; sampleZ++) {
            for (int sampleX = 0; sampleX < CityMapTilePayload.SAMPLE_COUNT; sampleX++) {
                int index = sampleZ * CityMapTilePayload.SAMPLE_COUNT + sampleX;
                if (heights[index] == Integer.MIN_VALUE) {
                    colors[index] = UNKNOWN_COLOR;
                    continue;
                }
                int northHeight = sampleZ > 0
                        ? heights[index - CityMapTilePayload.SAMPLE_COUNT]
                        : heights[index];
                int westHeight = sampleX > 0 ? heights[index - 1] : heights[index];
                colors[index] = shadeAndMute(
                        baseColors[index],
                        heights[index],
                        northHeight,
                        westHeight
                );
            }
        }
        return new SampledTile(colors, complete);
    }

    private static int shadeAndMute(
            int rgb,
            int height,
            int northHeight,
            int westHeight
    ) {
        double slope = 0.0;
        int comparisons = 0;
        if (northHeight != Integer.MIN_VALUE) {
            slope += height - northHeight;
            comparisons++;
        }
        if (westHeight != Integer.MIN_VALUE) {
            slope += height - westHeight;
            comparisons++;
        }
        if (comparisons > 0) {
            slope /= comparisons;
        }

        int sourceRed = (rgb >> 16) & 0xFF;
        int sourceGreen = (rgb >> 8) & 0xFF;
        int sourceBlue = rgb & 0xFF;
        int luminance = (sourceRed * 30 + sourceGreen * 59 + sourceBlue * 11) / 100;
        int mutedRed = (sourceRed * 3 + luminance) / 4;
        int mutedGreen = (sourceGreen * 3 + luminance) / 4;
        int mutedBlue = (sourceBlue * 3 + luminance) / 4;
        double factor = 0.72 + Math.max(-4.0, Math.min(4.0, slope)) * 0.018;
        int red = Math.min(255, Math.max(0, (int) (mutedRed * factor)));
        int green = Math.min(255, Math.max(0, (int) (mutedGreen * factor)));
        int blue = Math.min(255, Math.max(0, (int) (mutedBlue * factor)));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private record SampledTile(int[] colors, boolean complete) {
    }

    private record RequestWindow(long gameTime, int count) {
    }
}
