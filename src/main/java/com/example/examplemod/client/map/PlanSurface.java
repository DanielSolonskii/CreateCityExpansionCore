package com.example.examplemod.client.map;

import com.example.examplemod.city.TerrainPyramid;
import com.example.examplemod.city.CityMapSavedData;
import java.util.UUID;

/** Only the full-resolution survey can identify the exact column below a blueprint node. */
public record PlanSurface(int y, long checkedAt, boolean stale) {
    public static PlanSurface sample(TerrainTileStore.Tile tile, UUID world, int x, int z, long checked, boolean invalidated, long now) {
        if(tile==null || !world.equals(tile.worldId()) || tile.heights().length!=34*34) return null;
        int localX=Math.floorMod(x,32), localZ=Math.floorMod(z,32);
        short height=tile.heights()[(localZ+1)*34+localX+1];
        if(height==TerrainPyramid.NO_HEIGHT || tile.colors()[localZ*32+localX]==CityMapSavedData.UNKNOWN_COLOR) return null;
        return new PlanSurface(height, checked, invalidated || checked==0 || now-checked > (tile.complete()?60_000:5_000));
    }
}
