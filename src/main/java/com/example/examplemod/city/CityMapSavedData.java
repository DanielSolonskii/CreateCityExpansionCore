package com.example.examplemod.city;

import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.CityMapTilePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent atlas samples captured only while chunks are already naturally loaded.
 *
 * <p>The atlas is sampled once per world block ({@link CityMapTilePayload#SAMPLE_STEP} is 1) with a
 * light slope shading baked in from the sampled surface heights, so the terrain reads as relief
 * instead of flat confetti. Height is additionally reduced to elevation isolines: the server marks
 * the samples a contour passes through, and the client draws those as ink. Sending the decision
 * rather than the height keeps a tile at 128 extra bytes instead of 4096.
 *
 * <p><b>Refresh.</b> A chunk load is the only capture trigger, and this class never asks a level for
 * a chunk: it only records what is already in memory. Every tile that actually changed is marked
 * dirty, and a throttled pass pushes exactly those tiles to the players who already hold them, so a
 * newly explored chunk appears on an already-open map without a reopen and without re-uploading the
 * rest of the atlas.
 */
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
    private static final int PUSH_INTERVAL_TICKS = 20;
    private static final int MAX_TILES_PER_PASS = 16;
    /**
     * Высота горизонтали в блоках.
     *
     * <p>Четыре блока — компромисс из двух требований. Чаще: на равнине горизонтали сливаются в
     * плотную сетку, неотличимую от сетки координат, и начинают спорить с линиями городов.
     * Реже: пологий склон перестаёт читаться как рельеф.
     */
    public static final int CONTOUR_INTERVAL_BLOCKS = 4;

    private final Map<Long, KnownTile> tiles = new HashMap<>();
    /** Tiles whose samples changed and still have to reach whoever is holding them. */
    private final Set<Long> dirty = new LinkedHashSet<>();
    /** Which cached tiles each player already holds, so updates go only where they are useful. */
    private final Map<UUID, Set<Long>> watched = new HashMap<>();

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

    /** Pushes tiles that changed since the last pass. Only runs when somebody is actually mapping. */
    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.getGameTime() % PUSH_INTERVAL_TICKS != 0L) {
            return;
        }
        List<ServerPlayer> mappers = new ArrayList<>();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (level.dimension().location().equals(CityMapNetworkHandlers.viewerDimension(player))) mappers.add(player);
        }
        if (mappers.isEmpty()) {
            return;
        }
        CityMapSavedData data = get(level);
        data.pruneWatchers(level.getServer().getPlayerList().getPlayers());
        List<Long> batch = data.drainDirty(MAX_TILES_PER_PASS);
        if (batch.isEmpty()) {
            return;
        }
        for (ServerPlayer player : mappers) {
            BlockPos anchor = CityMapNetworkHandlers.viewerPylon(player);
            if (anchor == null) continue;
            for (Long key : batch) {
                if (!data.isWatched(player.getUUID(), key)) {
                    continue;
                }
                TileSnapshot snapshot = data.tile(ChunkPos.getX(key), ChunkPos.getZ(key));
                PacketDistributor.sendToPlayer(player,
                        new CityMapTilePayload(anchor, level.dimension().location(),
                                ChunkPos.getX(key), ChunkPos.getZ(key),
                                snapshot.complete(), snapshot.colors(), snapshot.contours()));
            }
        }
    }

    /** Remembers that a player already holds a tile, so later changes can be pushed to them. */
    public void watch(UUID player, int tileX, int tileZ) {
        this.watched.computeIfAbsent(player, ignored -> new HashSet<>()).add(ChunkPos.asLong(tileX, tileZ));
    }

    public boolean isWatched(UUID player, long tileKey) {
        Set<Long> keys = this.watched.get(player);
        return keys != null && keys.contains(tileKey);
    }

    /** Drops watches of players who are no longer online, so the map cannot grow forever. */
    private void pruneWatchers(List<ServerPlayer> online) {
        if (this.watched.isEmpty()) {
            return;
        }
        Set<UUID> present = new HashSet<>();
        for (ServerPlayer player : online) {
            present.add(player.getUUID());
        }
        this.watched.keySet().removeIf(player -> !present.contains(player));
    }

    /** Takes at most {@code budget} dirty tiles; the rest stay for the next pass. */
    private List<Long> drainDirty(int budget) {
        if (this.dirty.isEmpty()) {
            return List.of();
        }
        List<Long> batch = new ArrayList<>(Math.min(budget, this.dirty.size()));
        for (Long key : this.dirty) {
            batch.add(key);
            if (batch.size() >= budget) {
                break;
            }
        }
        batch.forEach(this.dirty::remove);
        return batch;
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
                new BitSet(CityMapTilePayload.COLOR_COUNT)));

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
                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ) - 1;
                BlockPos surface = new BlockPos(worldX, surfaceY, worldZ);
                BlockState state = chunk.getBlockState(surface);
                MapColor mapColor = state.getMapColor(chunk.getLevel(), surface);
                heights[localSampleZ * perChunk + localSampleX] = surfaceY;
                flat[localSampleZ * perChunk + localSampleX] =
                        baseColor((ServerLevel) chunk.getLevel(), surface, state.getBlock(), mapColor);
            }
        }
        // Slope shading from the sampled heights: a multiplicative tint, so grass stays grass and
        // water stays blue instead of turning into grey mush.
        shade(heights, perChunk, flat);
        /*
         * Горизонтали помечаются здесь, а не на клиенте: высота известна только в момент
         * выборки, и отправлять её целиком ради признака «здесь проходит линия» было бы
         * расточительно.
         */
        BitSet chunkContours = markContours(heights, perChunk);
        for (int localSample = 0; localSample < perChunk * perChunk; localSample++) {
            flat[localSample] = normalize(flat[localSample]);
        }
        for (int localSampleZ = 0; localSampleZ < perChunk; localSampleZ++) {
            for (int localSampleX = 0; localSampleX < perChunk; localSampleX++) {
                int local = localSampleZ * perChunk + localSampleX;
                int index = (firstSampleZ + localSampleZ) * CityMapTilePayload.SAMPLE_COUNT
                        + firstSampleX + localSampleX;
                if (index < 0 || index >= CityMapTilePayload.COLOR_COUNT) {
                    continue;
                }
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
            this.dirty.add(tileKey);
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
        return applyTint(mapColor.col,
                biomeTint(level, pos, classifyTint(BuiltInRegistries.BLOCK.getKey(block))),
                BIOME_TINT_STRENGTH);
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
                    new KnownTile(colors, known, contours));
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

    private record KnownTile(int[] colors, BitSet known, BitSet contours) {}
}
