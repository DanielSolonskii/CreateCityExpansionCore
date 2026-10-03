package com.example.examplemod.geology;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Server-authoritative слой геологии.
 *
 * Архитектура сохранения и извлечения не изменилась:
 * это по-прежнему SavedData, стабильный depositId,
 * initialReserve / remainingReserve, общий reserve для всех
 * шахт одного месторождения и fail-safe при повреждённых данных.
 *
 * Изменилась только пространственная модель: месторождение
 * больше не равно ячейке. Оно владеет собственным центром и
 * геометрией, пересекает множество macro-cell, а macro-cell
 * используется только как пространственный индекс и как
 * владелец записи в сохранении.
 *
 * Инвариант: в {@link #depositsById} лежит ровно один
 * экземпляр на depositId. Все обращения и все шахты работают
 * с одним и тем же объектом, поэтому reserve общий.
 */
public final class GeologyManager extends SavedData {

    static final int CURRENT_FORMAT_VERSION =
            GeologySavedDataCodec.CURRENT_FORMAT_VERSION;

    private static final String DATA_NAME =
            "createcityexpansioncore_geology";

    /** Верхняя граница кэша временных поисковых представлений. */
    private static final int QUERY_VIEW_CACHE_LIMIT = 128;

    private final ResourceLocation dimensionId;
    private final long worldSeed;

    /**
     * Владельцы месторождений: macro-cell -> её собственные залежи.
     * Именно это множество сохраняется в NBT.
     */
    private final Map<ResourceLocation, GeologicalRegion> regions =
            new LinkedHashMap<>();

    /** Канонические экземпляры, по одному на depositId. */
    private final Map<ResourceLocation, GeologicalDeposit> depositsById =
            new LinkedHashMap<>();

    /** Пространственный индекс: macro-cell -> месторождения этой ячейки. */
    private final Map<Long, List<GeologicalDeposit>> byCell = new LinkedHashMap<>();

    /** Кэш временных поисковых представлений. Не сохраняется. */
    private final Map<Long, GeologicalRegion> queryViews =
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<Long, GeologicalRegion> eldest
                ) {
                    return size() > QUERY_VIEW_CACHE_LIMIT;
                }
            };

    /**
     * Мягкая стартовая гарантия: произвольная опорная точка,
     * вокруг которой при запросе дополнительно проверяется один
     * слой macro-cell.
     *
     * Намеренно НЕ зависит от City / Economy: процедурная
     * геология полностью автономна. Точку выставляет владелец
     * сервера (см. GeologyStarterGuarantee), поэтому привязка
     * к Master City остаётся отдельным hook, а не зависимостью
     * геологии от города.
     */
    private BlockPos starterAnchor;

    private final boolean storageAvailable;
    private final CompoundTag preservedFallbackData;

    GeologyManager(ResourceLocation dimensionId) {
        this(dimensionId, 0L, true, null);
    }

    private GeologyManager(
            ResourceLocation dimensionId,
            long worldSeed,
            boolean storageAvailable,
            CompoundTag preservedFallbackData
    ) {
        this.dimensionId = Objects.requireNonNull(dimensionId, "dimensionId");
        this.worldSeed = worldSeed;
        this.storageAvailable = storageAvailable;
        this.preservedFallbackData = preservedFallbackData;
    }

    static GeologyManager load(
            CompoundTag tag,
            HolderLookup.Provider registries,
            ResourceLocation dimensionId,
            long worldSeed
    ) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(dimensionId, "dimensionId");

        GeologySavedDataCodec.LoadResult result =
                GeologySavedDataCodec.load(tag, dimensionId);
        GeologyManager manager = new GeologyManager(
                dimensionId,
                worldSeed,
                result.storageAvailable(),
                result.preservedFallbackData()
        );
        manager.adopt(result);

        if (result.migrated()) {
            manager.setDirty();
        }

        return manager;
    }

    private void adopt(GeologySavedDataCodec.LoadResult result) {
        for (GeologySavedDataCodec.LoadedCell cell : result.cells()) {
            regions.put(cell.region().getId(), cell.region());
            for (GeologicalDeposit deposit : cell.region().getDeposits()) {
                depositsById.put(deposit.getId(), deposit);
                index(cell.cellX(), cell.cellZ(), deposit);
            }
        }
    }

    @Override
    public CompoundTag save(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        if (!storageAvailable) {
            return preservedFallbackData.copy();
        }

        return GeologySavedDataCodec.save(
                tag,
                dimensionId,
                regions.values()
        );
    }

    public static GeologyManager get(ServerLevel level) {
        ResourceLocation dimensionId = level.dimension().location();
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        () -> new GeologyManager(dimensionId),
                        (tag, registries) -> load(
                                tag,
                                registries,
                                dimensionId,
                                level.getSeed()
                        ),
                        null
                ),
                DATA_NAME
        );
    }

    /**
     * Месторождения, релевантные для позиции: все залежи,
     * пересекающие блок, из macro-cell позиции и её соседей.
     *
     * Ограниченный по площади поиск: не более
     * (2 * LOOKUP_RING + 1)^2 macro-cell, чанки не грузятся.
     */
    public static GeologicalRegion getRegion(
            ServerLevel level,
            BlockPos pos
    ) {
        return get(level).query(pos);
    }

    public static Collection<GeologicalDeposit> getDeposits(
            ServerLevel level,
            BlockPos pos
    ) {
        return getRegion(level, pos).getDeposits();
    }

    public static Optional<GeologicalDeposit> getDeposit(
            ServerLevel level,
            ResourceLocation depositId
    ) {
        return get(level).findDeposit(depositId);
    }

    public static DepositExtractionResult extractDeposit(
            ServerLevel level,
            ResourceLocation depositId,
            long requestedAmount
    ) {
        return get(level).extract(depositId, requestedAmount);
    }

    /**
     * Мягкая стартовая гарантия ресурсов рядом с опорной точкой.
     *
     * Не изменяет базовую вероятность и не создаёт запасов вне
     * расчёта: лишь гарантирует, что в радиусе гарантии есть
     * кандидаты. Используется внешним hook'ом, если владелец
     * сервера знает координаты Master City.
     */
    public void setStarterAnchor(BlockPos anchor) {
        this.starterAnchor = anchor == null ? null : anchor.immutable();
        queryViews.clear();
    }

    public Optional<BlockPos> starterAnchor() {
        return Optional.ofNullable(starterAnchor);
    }

    private List<GeologicalDeposit> gather(
            int centerCellX,
            int centerCellZ,
            BlockPos pos,
            NearbyFilter filter,
            int ring
    ) {
        List<GeologicalDeposit> found = new ArrayList<>();

        for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
                collect(centerCellX + dx, centerCellZ + dz, pos, filter, found);
            }
        }

        if (starterAnchor != null) {
            collect(
                    GeologyGenerator.cellXOf(starterAnchor.getX()),
                    GeologyGenerator.cellZOf(starterAnchor.getZ()),
                    pos,
                    filter,
                    found
            );
        }

        return found;
    }

    private void collect(
            int cellX,
            int cellZ,
            BlockPos pos,
            NearbyFilter filter,
            List<GeologicalDeposit> out
    ) {
        materialize(cellX, cellZ);
        List<GeologicalDeposit> candidates =
                byCell.get(GeologyGenerator.cellKey(cellX, cellZ));
        if (candidates == null) {
            return;
        }
        for (GeologicalDeposit deposit : candidates) {
            if (out.contains(deposit)) {
                continue;
            }
            if (filter.accepts(deposit, pos.getX(), pos.getZ())) {
                out.add(deposit);
            }
        }
    }

    /**
     * Предикат отбора залежи для одного обхода.
     *
     * Обе операции — это один и тот же обход по соседним
     * macro-cell с разным предикатом: query() просит
     * «покрывает ли блок», queryNearby() — «не дальше ли
     * граница от точки».
     */
    @FunctionalInterface
    private interface NearbyFilter {
        boolean accepts(GeologicalDeposit deposit, int x, int z);
    }

    /**
     * Обе операции — разные фильтры одного обхода:
     *
     *  - {@link #query(BlockPos)} отвечает на вопрос «что ПОД
     *    этой точкой» и нужен шахте и режиму INSIDE: в список
     *    попадают только залежи, ПОКРЫВАЮЩИЕ блок;
     *
     *  - {@link #queryNearby(BlockPos, int)} отвечает на вопрос
     *    «что ВОКРУГ этой точки» и нужен анализатору для
     *    NEAREST ANOMALY: в список попадают залежи, чья граница
     *    не дальше заданного радиуса, даже если игрок ещё не
     *    внутри.
     *
     * Смешивать их нельзя. Contains-only список пуст ровно там,
     * где анализатору нужны данные, поэтому режим NEAREST
     * ANOMALY был структурно недостижим: наружу от залежи
     * список всегда оказывался пустым.
     */
    public GeologicalRegion query(BlockPos pos) {
        int cellX = GeologyGenerator.cellXOf(pos.getX());
        int cellZ = GeologyGenerator.cellZOf(pos.getZ());
        long key = GeologyGenerator.cellKey(cellX, cellZ);

        GeologicalRegion cached = queryViews.get(key);
        if (cached != null) {
            return cached;
        }

        List<GeologicalDeposit> found = gather(
                cellX,
                cellZ,
                pos,
                CONTAINS_FILTER_MARKER,
                GeologyGenerator.LOOKUP_RING
        );

        GeologicalRegion view = new GeologicalRegion(
                GeologyGenerator.regionId(dimensionId, cellX, cellZ),
                cellX,
                cellZ,
                found
        );
        queryViews.put(key, view);
        return view;
    }

    /**
     * Залежи вокруг точки, без предварительного contains().
     *
     * Кольцо обхода вычисляется из геометрии, а не подобрано:
     * см. {@link GeologyGenerator#nearbyLookupRing(int)}.
     * Ограничено несколькими соседними macro-cell, чанки не
     * грузятся.
     */
    public List<GeologicalDeposit> queryNearby(
            BlockPos pos,
            int radiusBlocks
    ) {
        int cellX = GeologyGenerator.cellXOf(pos.getX());
        int cellZ = GeologyGenerator.cellZOf(pos.getZ());
        int ring = GeologyGenerator.nearbyLookupRing(radiusBlocks);

        return gather(
                cellX,
                cellZ,
                pos,
                (deposit, x, z) -> GeologySurvey.boundaryDistance(x, z, deposit)
                        <= radiusBlocks,
                ring
        );
    }

    /**
     * Фильтр «залежь покрывает этот блок».
     *
     * Именно он используется запросом под точкой: шахте и
     * режиму INSIDE нужен этот список, и только этот.
     */
    private static final NearbyFilter CONTAINS_FILTER_MARKER =
            (deposit, x, z) -> deposit.contains(x, z);

    /**
     * Приводит macro-cell к материализованному состоянию.
     * Уже известные месторождения не пересоздаются: reserve,
     * форма и качество остаются ровно теми, что были сохранены.
     */
    void materialize(int cellX, int cellZ) {
        ResourceLocation regionId = GeologyGenerator.regionId(
                dimensionId,
                cellX,
                cellZ
        );
        if (regions.containsKey(regionId)) {
            return;
        }

        if (!storageAvailable) {
            regions.put(
                    regionId,
                    new GeologicalRegion(regionId, cellX, cellZ, List.of())
            );
            return;
        }

        boolean added = false;
        List<GeologicalDeposit> owned = new ArrayList<>();
        for (GeologicalDeposit candidate
                : GeologyGenerator.createDeposits(
                        dimensionId,
                        worldSeed,
                        cellX,
                        cellZ
                )) {
            GeologicalDeposit existing = depositsById.get(candidate.getId());
            if (existing != null) {
                owned.add(existing);
                continue;
            }
            depositsById.put(candidate.getId(), candidate);
            index(cellX, cellZ, candidate);
            owned.add(candidate);
            added = true;
        }

        regions.put(
                regionId,
                new GeologicalRegion(regionId, cellX, cellZ, owned)
        );

        if (added) {
            queryViews.clear();
            setDirty();
        }
    }

    private void index(int cellX, int cellZ, GeologicalDeposit deposit) {
        byCell.computeIfAbsent(
                GeologyGenerator.cellKey(cellX, cellZ),
                ignored -> new ArrayList<>(4)
        ).add(deposit);
    }

    Optional<GeologicalDeposit> findDeposit(ResourceLocation depositId) {
        Objects.requireNonNull(depositId, "depositId");
        return Optional.ofNullable(depositsById.get(depositId));
    }

    Collection<GeologicalRegion> ownedRegions() {
        return Collections.unmodifiableCollection(regions.values());
    }

    /**
     * Месторождения, центры которых лежат в указанной macro-cell.
     * Не зависит от позиции наблюдателя и не сканирует мир.
     */
    java.util.List<GeologicalDeposit> depositsInCell(
            int cellX,
            int cellZ
    ) {
        List<GeologicalDeposit> deposits = byCell.get(
                GeologyGenerator.cellKey(cellX, cellZ)
        );
        return deposits == null ? List.of() : List.copyOf(deposits);
    }

    DepositExtractionResult extract(
            ResourceLocation depositId,
            long requestedAmount
    ) {
        Objects.requireNonNull(depositId, "depositId");

        if (!storageAvailable) {
            return DepositExtractionResult.storageUnavailable();
        }

        Optional<GeologicalDeposit> depositResult = findDeposit(depositId);
        if (depositResult.isEmpty()) {
            return DepositExtractionResult.notFound();
        }

        GeologicalDeposit deposit = depositResult.get();
        if (requestedAmount < 0) {
            return DepositExtractionResult.forDeposit(
                    DepositExtractionResult.Status.INVALID_REQUEST,
                    0L,
                    deposit
            );
        }
        if (requestedAmount == 0) {
            return DepositExtractionResult.forDeposit(
                    DepositExtractionResult.Status.ZERO_REQUEST,
                    0L,
                    deposit
            );
        }
        if (deposit.isDepleted()) {
            return DepositExtractionResult.forDeposit(
                    DepositExtractionResult.Status.DEPLETED,
                    0L,
                    deposit
            );
        }

        long extracted = deposit.extract(requestedAmount);
        setDirty();
        return DepositExtractionResult.forDeposit(
                DepositExtractionResult.Status.EXTRACTED,
                extracted,
                deposit
        );
    }

    public boolean isStorageAvailable() {
        return storageAvailable;
    }

    ResourceLocation getDimensionId() {
        return dimensionId;
    }
}