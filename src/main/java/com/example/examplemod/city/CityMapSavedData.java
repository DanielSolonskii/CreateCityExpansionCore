package com.example.examplemod.city;

import com.example.examplemod.network.CityMapTilePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent surveyed colours, heights and coverage for one ServerLevel.
 * Derived pyramid nodes and bounded per-view leases live only in memory. */
public final class CityMapSavedData extends SavedData {
    public static final int UNKNOWN_COLOR = 0xFF101719;
    /** Which biome colour family, if any, a block's colour is resolved through in vanilla. */
    enum TintFamily { NONE, GRASS, FOLIAGE, WATER }
    /** Neutral dark stand-in for a block with no map colour; never tinted, never shaded. */
    static final int NO_COLOR_FALLBACK = 0x2A3031;
    /**
     * How much of the biome colour is mixed into the material colour. Vanilla multiplies, which is
     * what turned savanna foliage into a solid yellow field; blending keeps biome character while the
     * material keeps its own hue.
     */
    static final double BIOME_TINT_STRENGTH = 0.45;
    /** Fraction of a pixel of terrain height difference that becomes one unit of brightness. */
    static final double HILLSHADE_SLOPE = 0.018;
    /** Hillshade is deliberately tiny: it is meant to reveal relief, not to shade the map. */
    static final double HILLSHADE_RANGE = 0.10;
    /** Every colour is pulled this far towards its own luminance. */
    static final double DESATURATION = 0.22;
    /** Largest allowed gap between the brightest and darkest channel; beyond it the map reads acid. */
    static final int MAX_CHROMA = 96;
    static final int MAX_LUMINANCE = 205;
    static final int MIN_LUMINANCE = 24;
    /** One tile per this many blocks; also the size of one vanilla chunk in blocks. */
    private static final String DATA_NAME = "createcityexpansioncore_city_map";
    /** Pushes are throttled and budgeted so a burst of chunk loads cannot flood a client. */
    private static final int MAX_TILES_PER_PASS = 16;
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("ccec.map.diagnostics");
    /**
     * Высота горизонтали в блоках.
     *
     * <p>Четыре блока — компромисс из двух требований. Чаще: на равнине горизонтали сливаются в
     * плотную сетку, неотличимую от сетки координат, и начинают спорить с линиями городов.
     * Реже: пологий склон перестаёт читаться как рельеф.
     */
    public static final int CONTOUR_INTERVAL_BLOCKS = 4;

    private java.util.UUID worldId=java.util.UUID.randomUUID();
    private final TerrainPyramid pyramid=new TerrainPyramid();
    private final TerrainSurfaceQueue surfaces = new TerrainSurfaceQueue();
    private final TerrainInterest interest = new TerrainInterest();

    private static void queueSurface(net.minecraft.world.level.LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel server)
            get(server).surfaces.changed(ChunkPos.asLong(Math.floorDiv(pos.getX(),16), Math.floorDiv(pos.getZ(),16)));
    }

    @SubscribeEvent public static void onSurfaceBreak(net.neoforged.neoforge.event.level.BlockEvent.BreakEvent event) {
        queueSurface(event.getLevel(),event.getPos());
    }
    @SubscribeEvent public static void onSurfacePlace(net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent event) {
        queueSurface(event.getLevel(),event.getPos());
    }

    private void refresh(ServerLevel level) {
        surfaces.advance(level.getGameTime(), interest.size() != 0, interest::covers, key -> {
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk != null) {
                capture(chunk);
                surfaces.complete(key);
            }
        });
    }

    public TerrainPyramid.Snapshot snapshot(TerrainPyramid.Key key) {
        return surfaces.pending(key, pyramid.hasSamples(key)) ? null : pyramid.snapshot(key);
    }

    private long version(TerrainPyramid.Key key) {
        return surfaces.pending(key, pyramid.hasSamples(key)) ? -1 : pyramid.version(key);
    }

    public void prioritize(TerrainPyramid.Key key, BlockPos focus) {
        surfaces.prioritize(key, Math.floorDiv(focus.getX(),16), Math.floorDiv(focus.getZ(),16));
    }

    private boolean previouslySurveyed(ChunkPos chunk) {
        var tile = tiles.get(ChunkPos.asLong(Math.floorDiv(chunk.x,2), Math.floorDiv(chunk.z,2)));
        if (tile == null) return false;
        int start = Math.floorMod(chunk.x,2)*16 + Math.floorMod(chunk.z,2)*16*32;
        for (int z=0; z<16; z++) {
            int row = start + z*32;
            int first = tile.known().nextSetBit(row);
            if (first >= 0 && first < row+16) return true;
        }
        return false;
    }

    public java.util.UUID worldId(){return worldId;}
    public TerrainPyramid.Data terrain(TerrainPyramid.Key key){return pyramid.get(key);}
    public short[] terrainHeights(TerrainPyramid.Key key){return pyramid.halo(key);}
    private void index(long key,KnownTile tile) {
        int[] colors=tile.colors().clone();
        for(int i=0;i<colors.length;i++)if(!tile.known().get(i))colors[i]=0;
        pyramid.put(ChunkPos.getX(key),ChunkPos.getZ(key),colors,tile.heights());
    }
    private final Map<Long, KnownTile> tiles = new HashMap<>();
    public static CityMapSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(() -> {
                    CityMapSavedData data = new CityMapSavedData();
                    data.setDirty(); // Persist the dimension identity even for a still-empty survey.
                    return data;
                }, CityMapSavedData::load, null),
                DATA_NAME
        );
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        var data = get(level);
        long key = chunk.getPos().toLong();
        // ChunkEvent.Load may fire before FULL status. Sample it on the level tick once
        // getChunkNow returns it, sharing the same budget as edits and background refresh.
        data.surfaces.load(key, data.previouslySurveyed(chunk.getPos()));
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getChunk() instanceof LevelChunk chunk)) return;
        var data = get(level);
        long key = chunk.getPos().toLong();
        // A confirmed edit might occur on the last tick before natural unload.
        if (data.surfaces.dirty(key)) data.capture(chunk);
        data.surfaces.unload(key);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        var data = get(level);
        data.refresh(level);
        data.pyramid.advance(16, 2_000_000L);
        if (DIAGNOSTICS && level.getGameTime() % 600 == 0) {
            org.slf4j.LoggerFactory.getLogger(CityMapSavedData.class).info(
                    "Map server resources watches={} sourceQueue={} dirtyParents={} queuedRoots={}",
                    data.interest.size(), data.surfaces.prioritySize(), data.pyramid.pendingTiles(), data.pyramid.queuedRoots());
        }
        data.interest.advance(level.getGameTime(), 256, MAX_TILES_PER_PASS, data::version, (address, version) -> {
            var player = level.getServer().getPlayerList().getPlayer(address.player());
            if (player != null) CityMapNetworkHandlers.invalidate(player, address.view(),
                    level.dimension().location(), data.worldId(), address.tile(), version);
        });
    }

    public void watch(UUID player, UUID viewId, TerrainPyramid.Key key, long tick, long lastVersion) {
        interest.watch(player, viewId, key, tick, lastVersion);
    }

    public void unwatch(UUID player, UUID viewId) {
        interest.unwatch(player, viewId);
    }

    public TileSnapshot tile(int tileX, int tileZ) {
        KnownTile tile = this.tiles.get(ChunkPos.asLong(tileX, tileZ));
        if (tile == null) {
            int[] colors = new int[CityMapTilePayload.COLOR_COUNT];
            Arrays.fill(colors, UNKNOWN_COLOR);
            return new TileSnapshot(colors, new BitSet(CityMapTilePayload.COLOR_COUNT), false);
        }
        int[] colors = tile.colors().clone();
        for (int index = tile.known().nextClearBit(0);
             index >= 0 && index < colors.length;
             index = tile.known().nextClearBit(index + 1)) {
            colors[index] = UNKNOWN_COLOR;
        }
        /*
         * Горизонтали за неизведанной зоной убираются тем же проходом, что и цвет: карта не
         * должна рисовать линии там, где игрок ничего не видел.
         */
        BitSet contours = (BitSet) tile.contours().clone();
        for (int index = tile.known().nextClearBit(0);
             index >= 0 && index < contours.size();
             index = tile.known().nextClearBit(index + 1)) {
            contours.clear(index);
        }
        return new TileSnapshot(colors, contours, tile.known().cardinality() == colors.length);
    }

    /**
     * Samples one naturally loaded chunk. Only samples whose colour actually changed set the dirty
     * flag, so re-loading an unchanged chunk — which happens constantly as the player walks — costs
     * a compare and produces no network traffic at all.
     */
    private void capture(LevelChunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        int tileX = Math.floorDiv(chunkPos.getMinBlockX(), CityMapTilePayload.WORLD_SIZE);
        int tileZ = Math.floorDiv(chunkPos.getMinBlockZ(), CityMapTilePayload.WORLD_SIZE);
        long tileKey = ChunkPos.asLong(tileX, tileZ);
        KnownTile tile = this.tiles.computeIfAbsent(tileKey, ignored -> new KnownTile(
                new int[CityMapTilePayload.COLOR_COUNT],
                new BitSet(CityMapTilePayload.COLOR_COUNT),
                new BitSet(CityMapTilePayload.COLOR_COUNT), emptyHeights()));

        int tileOriginX = tileX * CityMapTilePayload.WORLD_SIZE;
        int tileOriginZ = tileZ * CityMapTilePayload.WORLD_SIZE;
        int firstSampleX = Math.floorDiv(chunkPos.getMinBlockX() - tileOriginX, CityMapTilePayload.SAMPLE_STEP);
        int firstSampleZ = Math.floorDiv(chunkPos.getMinBlockZ() - tileOriginZ, CityMapTilePayload.SAMPLE_STEP);
        int perChunk = 16 / CityMapTilePayload.SAMPLE_STEP;

        boolean changed = false;
        int[] heights = new int[perChunk * perChunk];
        int[] flat = new int[CityMapTilePayload.COLOR_COUNT];
        for (int localSampleZ = 0; localSampleZ < perChunk; localSampleZ++) {
            for (int localSampleX = 0; localSampleX < perChunk; localSampleX++) {
                int localX = localSampleX * CityMapTilePayload.SAMPLE_STEP;
                int localZ = localSampleZ * CityMapTilePayload.SAMPLE_STEP;
                int worldX = chunkPos.getMinBlockX() + localX;
                int worldZ = chunkPos.getMinBlockZ() + localZ;
                // ChunkAccess.getHeight already returns firstAvailable - 1: the occupied surface.
                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
                BlockPos surface = new BlockPos(worldX, surfaceY, worldZ);
                BlockState state = chunk.getBlockState(surface);
                MapColor mapColor = state.getMapColor(chunk.getLevel(), surface);
                // Transparent/uncoloured decoration must not stamp dark speckles over the ground.
                for(int skip=0;mapColor==MapColor.NONE&&skip<8&&surfaceY>chunk.getMinBuildHeight();skip++){
                    surface=surface.below();surfaceY--;state=chunk.getBlockState(surface);
                    mapColor=state.getMapColor(chunk.getLevel(),surface);
                }
                heights[localSampleZ * perChunk + localSampleX] = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,localX,localZ);
                flat[localSampleZ * perChunk + localSampleX] =
                        baseColor((ServerLevel) chunk.getLevel(), surface, state.getBlock(), mapColor);
            }
        }
        // Slope shading from the sampled heights: a multiplicative tint, so grass stays grass and
        // water stays blue instead of turning into grey mush.
        // Raw colour; the client derives relief from persisted heights.
        /*
         * Горизонтали помечаются здесь, а не на клиенте: высота известна только в момент
         * выборки, и отправлять её целиком ради признака «здесь проходит линия» было бы
         * расточительно.
         */
        BitSet chunkContours = markContours(heights, perChunk);
        for (int localSample = 0; localSample < perChunk * perChunk; localSample++) {
            flat[localSample] |= 0xFF000000;
        }
        for (int localSampleZ = 0; localSampleZ < perChunk; localSampleZ++) {
            for (int localSampleX = 0; localSampleX < perChunk; localSampleX++) {
                int local = localSampleZ * perChunk + localSampleX;
                int index = (firstSampleZ + localSampleZ) * CityMapTilePayload.SAMPLE_COUNT
                        + firstSampleX + localSampleX;
                if (index < 0 || index >= CityMapTilePayload.COLOR_COUNT) {
                    continue;
                }
                if(tile.heights()[index]!=(short)heights[local]){tile.heights()[index]=(short)heights[local];changed=true;}
                if (tile.colors()[index] != flat[local] || !tile.known().get(index)) {
                    tile.colors()[index] = flat[local];
                    tile.known().set(index);
                    changed = true;
                }
                /*
                 * Горизонталь обновляется независимо от цвета: пересобранный рельеф может
                 * оставить цвет тем же и одновременно снять линию — и наоборот.
                 */
                if (chunkContours.get(local)) {
                    if (!tile.contours().get(index)) {
                        tile.contours().set(index);
                        changed = true;
                    }
                } else if (tile.contours().get(index)) {
                    tile.contours().clear(index);
                    changed = true;
                }
            }
        }
        if (changed) {
            index(tileKey,tile);
            setDirty();
        }
    }

    /**
     * Marks the samples an elevation isoline passes through.
     *
     * <p>A sample belongs to a contour when its own height or any of its four neighbours sits on a
     * different side of an {@link #CONTOUR_INTERVAL_BLOCKS} boundary than the sample does. Testing
     * the neighbours as well as the centre is what turns a staircase of single marked blocks into a
     * continuous line: without it, a contour crossing a diagonal slope leaves gaps exactly where the
     * eye expects a line to be.
     *
     * <p>Neighbours outside the chunk are treated as the same height as the border sample, so a
     * contour that leaves the chunk simply ends there instead of drawing a false step. The adjacent
     * chunk marks its own side when it loads.
     */
    public static BitSet markContours(int[] heights, int size) {
        BitSet marked = new BitSet(size * size);
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int index = z * size + x;
                int height = heights[index];
                if (crossesInterval(height, height)
                        || crossesInterval(height, heights[z * size + Math.max(0, x - 1)])
                        || crossesInterval(height, heights[z * size + Math.min(size - 1, x + 1)])
                        || crossesInterval(height, heights[Math.max(0, z - 1) * size + x])
                        || crossesInterval(height, heights[Math.min(size - 1, z + 1) * size + x])) {
                    marked.set(index);
                }
            }
        }
        return marked;
    }

    /**
     * True when a contour level lies strictly between two heights.
     *
     * <p>A level exactly equal to one of the heights does not count: every flat surface would then
     * be drawn as a contour, which is precisely the case contours are supposed to leave empty.
     */
    public static boolean crossesInterval(int from, int to) {
        if (from == to) {
            return false;
        }
        /*
         * Сравниваются полосы, а не сами высоты. Полоса — это интервал между соседними
         * уровнями, поэтому шаг с 63 на 64 пересекает уровень 64 и рисуется, а шаг с 64 на 65
         * остаётся внутри полосы и не рисуется. Сравнение «уровень лежит строго между
         * высотами» давало бы обратный ответ на обоих шагах: склон, дошедший ровно до
         * отметки, не получил бы линии — а это как раз тот склон, который игрок и ищет.
         */
        return Math.floorDiv(from, CONTOUR_INTERVAL_BLOCKS)
                != Math.floorDiv(to, CONTOUR_INTERVAL_BLOCKS);
    }

    /**
     * Light relief shading: each sample is brightened or darkened by how much the ground rises
     * towards +X and +Z. Deliberately weak, because a strong shade makes terrain look painted.
     */
    private static void shade(int[] heights, int size, int[] colors) {
        for (int z = 0; z < size; z++) {
            for (int x = 0; x < size; x++) {
                int index = z * size + x;
                int left = heights[z * size + Math.max(0, x - 1)];
                int right = heights[z * size + Math.min(size - 1, x + 1)];
                int up = heights[Math.max(0, z - 1) * size + x];
                int down = heights[Math.min(size - 1, z + 1) * size + x];
                // Light from the upper left: rising ground to +X darkens, rising ground to +Z does not.
                double slope = ((right - left) - (down - up)) * HILLSHADE_SLOPE;
                double factor = Math.max(1.0 - HILLSHADE_RANGE, Math.min(1.0 + HILLSHADE_RANGE, 1.0 + slope));
                colors[index] = scaleBrightness(colors[index], factor);
            }
        }
    }

    private static int scaleBrightness(int rgb, double factor) {
        int red = clamp((int) Math.round((rgb >> 16 & 0xFF) * factor));
        int green = clamp((int) Math.round((rgb >> 8 & 0xFF) * factor));
        int blue = clamp((int) Math.round((rgb & 0xFF) * factor));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static int clamp(int value) {
        return value < 0 ? 0 : Math.min(value, 255);
    }

    /** Linear blend between two channel values; every step uses it, so no stage can jump a hue. */
    static int mix(int from, int to, double amount) {
        return (int) Math.round(from + (to - from) * amount);
    }

    static int luminance(int red, int green, int blue) {
        return (red * 30 + green * 59 + blue * 11) / 100;
    }

    /**
     * Which biome tint family a block belongs to, or {@link TintFamily#NONE} when the block is not
     * biome-tinted at all.
     *
     * <p>This is deliberately keyed on the block, never on {@link MapColor}. A {@code MapColor} is
     * the colour the block contributes to a filled map; it says nothing about whether the client tints
     * that block with a biome colour. Treating {@code MapColor.PLANT} as "foliage" and
     * {@code MapColor.GRASS} as "grass" tinted every other block that merely happens to share that
     * map colour — nether and modded vegetation included — which is how unrelated blocks ended up
     * with a biome colour they never take in vanilla.
     */
    static TintFamily classifyTint(ResourceLocation id) {
        if (!"minecraft".equals(id.getNamespace())) {
            return TintFamily.NONE;
        }
        String path = id.getPath();
        if (path.equals("water") || path.equals("bubble_column")) {
            return TintFamily.WATER;
        }
        if (path.endsWith("_leaves") || path.equals("vine") || path.equals("lily_pad")) {
            return TintFamily.FOLIAGE;
        }
        return switch (path) {
            // Exactly the blocks whose colour vanilla resolves through the grass colour resolver.
            case "grass_block", "grass", "tall_grass", "fern", "large_fern", "dead_bush",
                 "seagrass", "tall_seagrass" -> TintFamily.GRASS;
            default -> TintFamily.NONE;
        };
    }

    /**
     * The biome tint for a block, or 0 when the biome has none to give.
     *
     * <p>Only authoritative server data is read here: a biome's authored colour overrides and its
     * water colour. {@code Biome.getGrassColor}/{@code getFoliageColor} are deliberately NOT used —
     * they fall back to {@code GrassColor}/{@code FoliageColor} colour-texture tables that the client
     * uploads and a server never initialises, so on a server they return whatever those tables happen
     * to hold. That, not a stray coefficient, is what painted the map orange and magenta.
     *
     * <p>Consequence, stated plainly: terrain keeps the biome's authored tints where a biome authors
     * one (swamp, jungle, mesa) and stays on plain vanilla material colours everywhere else. That is
     * honest server data, and inventing the client-side gradient server-side was never an option.
     */
    static int biomeTint(ServerLevel level, BlockPos pos, TintFamily family) {
        if (family == TintFamily.NONE) {
            return 0;
        }
        BiomeSpecialEffects effects = level.getBiome(pos).value().getSpecialEffects();
        return switch (family) {
            case GRASS -> effects.getGrassColorOverride().orElse(0);
            case FOLIAGE -> effects.getFoliageColorOverride().orElse(0);
            case WATER -> effects.getWaterColor();
            case NONE -> 0;
        };
    }

    /** Applies a biome tint as a blend, never as a multiply, so the material keeps its hue. */
    static int applyTint(int base, int tint, double strength) {
        if (tint == 0) {
            return base;
        }
        int tintRed = tint >> 16 & 0xFF;
        int tintGreen = tint >> 8 & 0xFF;
        int tintBlue = tint & 0xFF;
        int red = base >> 16 & 0xFF;
        int green = base >> 8 & 0xFF;
        int blue = base & 0xFF;
        return 0xFF000000
                | mix(red, red * tintRed / 255, strength) << 16
                | mix(green, green * tintGreen / 255, strength) << 8
                | mix(blue, blue * tintBlue / 255, strength);
    }

    static int baseColor(ServerLevel level, BlockPos pos, Block block, MapColor mapColor) {
        if (mapColor == MapColor.NONE) {
            return NO_COLOR_FALLBACK;
        }
        TintFamily family=classifyTint(BuiltInRegistries.BLOCK.getKey(block));
        return applyTint(materialColor(family,mapColor.col),
                biomeTint(level, pos, family),
                BIOME_TINT_STRENGTH);
    }

    /** Material categories come from actual block identity, never inferred from rendered RGB. */
    public static int materialColor(TintFamily family,int fallback){
        return switch(family){case GRASS->0x78AB55;case FOLIAGE->0x3F743D;case WATER->0x3C79B4;case NONE->fallback;};
    }

    static int normalize(int rgb) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        int lum = luminance(red, green, blue);
        red = mix(red, lum, DESATURATION);
        green = mix(green, lum, DESATURATION);
        blue = mix(blue, lum, DESATURATION);
        lum = luminance(red, green, blue);
        int chroma = Math.max(red, Math.max(green, blue)) - Math.min(red, Math.min(green, blue));
        if (chroma > MAX_CHROMA) {
            double excess = (double) (chroma - MAX_CHROMA) / chroma;
            red = mix(red, lum, excess);
            green = mix(green, lum, excess);
            blue = mix(blue, lum, excess);
        }
        lum = luminance(red, green, blue);
        if (lum > MAX_LUMINANCE) {
            double factor = (double) MAX_LUMINANCE / Math.max(1, lum);
            red = clamp((int) Math.round(red * factor));
            green = clamp((int) Math.round(green * factor));
            blue = clamp((int) Math.round(blue * factor));
        } else if (lum < MIN_LUMINANCE && lum > 0) {
            double factor = (double) MIN_LUMINANCE / lum;
            red = clamp((int) Math.round(red * factor));
            green = clamp((int) Math.round(green * factor));
            blue = clamp((int) Math.round(blue * factor));
        }
        return 0xFF000000 | clamp(red) << 16 | clamp(green) << 8 | clamp(blue);
    }

    private static CityMapSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        CityMapSavedData data = new CityMapSavedData();
        if(tag.hasUUID("WorldId"))data.worldId=tag.getUUID("WorldId");else data.setDirty();
        for (Tag raw : tag.getList("Tiles", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) raw;
            int[] colors = entry.getIntArray("Colors");
            if (colors.length != CityMapTilePayload.COLOR_COUNT) continue;
            BitSet known = BitSet.valueOf(entry.getLongArray("Known"));
            /*
             * Горизонтали читаются как пустое множество, если их нет в файле. Старая карта
             * поэтому грузится без ошибок и просто остаётся без горизонталей там, где её блоки
             * ещё ни разу не пересобирались — обновятся сами, когда игрок пройдёт рядом.
             */
            BitSet contours = entry.contains("Contours", Tag.TAG_LONG_ARRAY)
                    ? BitSet.valueOf(entry.getLongArray("Contours"))
                    : new BitSet(CityMapTilePayload.COLOR_COUNT);
            data.tiles.put(ChunkPos.asLong(entry.getInt("X"), entry.getInt("Z")),
                    new KnownTile(colors, known, contours,readHeights(entry)));
        }
        data.tiles.forEach(data::index);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putUUID("WorldId",worldId);tag.putInt("TerrainVersion",3);
        ListTag list = new ListTag();
        this.tiles.forEach((key, tile) -> {
            CompoundTag entry = new CompoundTag();
            entry.putInt("X", ChunkPos.getX(key));
            entry.putInt("Z", ChunkPos.getZ(key));
            entry.putIntArray("Colors", tile.colors());
            int[] h=new int[1024];for(int i=0;i<h.length;i++)h[i]=tile.heights()[i];entry.putIntArray("Heights",h);
            entry.putLongArray("Known", tile.known().toLongArray());
            entry.putLongArray("Contours", tile.contours().toLongArray());
            list.add(entry);
        });
        tag.put("Tiles", list);
        return tag;
    }

    /**
     * @param contours one bit per sample: true when a contour line crosses this sample.
     */
    public record TileSnapshot(int[] colors, BitSet contours, boolean complete) {}

    private static short[] emptyHeights(){short[] h=new short[1024];java.util.Arrays.fill(h,TerrainPyramid.NO_HEIGHT);return h;}
    private static short[] readHeights(CompoundTag tag){short[] h=emptyHeights();int[] saved=tag.getIntArray("Heights");if(saved.length==1024)for(int i=0;i<1024;i++)h[i]=(short)saved[i];return h;}
    private record KnownTile(int[] colors, BitSet known, BitSet contours,short[] heights) {}
}
