package com.example.examplemod.city;

import com.example.examplemod.network.CityMapTilePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;

/** Persistent atlas samples captured only while chunks are already naturally loaded. */
public final class CityMapSavedData extends SavedData {
    public static final int UNKNOWN_COLOR = 0xFF101719;
    private static final String DATA_NAME = "createcityexpansioncore_city_map";
    private final Map<Long, KnownTile> tiles = new HashMap<>();

    public static CityMapSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CityMapSavedData::new, CityMapSavedData::load, null),
                DATA_NAME
        );
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        // A chunk load event is the only capture trigger. This method never asks a level for a chunk.
        get(level).capture(chunk);
    }

    public TileSnapshot tile(int tileX, int tileZ) {
        KnownTile tile = this.tiles.get(ChunkPos.asLong(tileX, tileZ));
        if (tile == null) {
            int[] colors = new int[CityMapTilePayload.COLOR_COUNT];
            Arrays.fill(colors, UNKNOWN_COLOR);
            return new TileSnapshot(colors, false);
        }
        int[] colors = tile.colors().clone();
        for (int index = tile.known().nextClearBit(0);
             index >= 0 && index < colors.length;
             index = tile.known().nextClearBit(index + 1)) {
            colors[index] = UNKNOWN_COLOR;
        }
        return new TileSnapshot(colors, tile.known().cardinality() == colors.length);
    }

    private void capture(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int tileX = Math.floorDiv(chunkPos.getMinBlockX(), CityMapTilePayload.WORLD_SIZE);
        int tileZ = Math.floorDiv(chunkPos.getMinBlockZ(), CityMapTilePayload.WORLD_SIZE);
        KnownTile tile = this.tiles.computeIfAbsent(ChunkPos.asLong(tileX, tileZ), ignored ->
                new KnownTile(new int[CityMapTilePayload.COLOR_COUNT], new BitSet(CityMapTilePayload.COLOR_COUNT)));

        int tileOriginX = tileX * CityMapTilePayload.WORLD_SIZE;
        int tileOriginZ = tileZ * CityMapTilePayload.WORLD_SIZE;
        int firstSampleX = Math.floorDiv(chunkPos.getMinBlockX() - tileOriginX, CityMapTilePayload.SAMPLE_STEP);
        int firstSampleZ = Math.floorDiv(chunkPos.getMinBlockZ() - tileOriginZ, CityMapTilePayload.SAMPLE_STEP);

        for (int localSampleZ = 0; localSampleZ < 16 / CityMapTilePayload.SAMPLE_STEP; localSampleZ++) {
            for (int localSampleX = 0; localSampleX < 16 / CityMapTilePayload.SAMPLE_STEP; localSampleX++) {
                int localX = localSampleX * CityMapTilePayload.SAMPLE_STEP;
                int localZ = localSampleZ * CityMapTilePayload.SAMPLE_STEP;
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ) - 1;
                BlockPos surface = new BlockPos(worldX, surfaceY, worldZ);
                BlockState state = chunk.getBlockState(surface);
                MapColor mapColor = state.getMapColor(chunk.getLevel(), surface);
                int index = (firstSampleZ + localSampleZ) * CityMapTilePayload.SAMPLE_COUNT
                        + firstSampleX + localSampleX;
                tile.colors()[index] = mute(mapColor == MapColor.NONE ? 0x2A3031 : mapColor.col);
                tile.known().set(index);
            }
        }
        setDirty();
    }

    private static int mute(int rgb) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        int luminance = (red * 30 + green * 59 + blue * 11) / 100;
        red = (red * 3 + luminance) / 4;
        green = (green * 3 + luminance) / 4;
        blue = (blue * 3 + luminance) / 4;
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static CityMapSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CityMapSavedData data = new CityMapSavedData();
        for (Tag raw : tag.getList("Tiles", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            int[] colors = entry.getIntArray("Colors");
            if (colors.length != CityMapTilePayload.COLOR_COUNT) continue;
            BitSet known = BitSet.valueOf(entry.getLongArray("Known"));
            data.tiles.put(ChunkPos.asLong(entry.getInt("X"), entry.getInt("Z")),
                    new KnownTile(colors, known));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        this.tiles.forEach((key, tile) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", ChunkPos.getX(key));
            entry.putInt("Z", ChunkPos.getZ(key));
            entry.putIntArray("Colors", tile.colors());
            entry.putLongArray("Known", tile.known().toLongArray());
            list.add(entry);
        });
        tag.put("Tiles", list);
        return tag;
    }

    public record TileSnapshot(int[] colors, boolean complete) {}
    private record KnownTile(int[] colors, BitSet known) {}
}
