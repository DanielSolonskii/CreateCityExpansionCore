package com.example.examplemod.geology;

import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Процедурная пространственная генерация геологии.
 *
 * Модель: мир -> детерминированные macro-cell -> кандидаты
 * месторождений -> пространственная геометрия.
 *
 * Никакого заранее сгенерированного списка: кандидаты
 * вычисляются по требованию, из world seed, измерения и
 * координат macro-cell, поэтому карта бесконечна, детерминирована
 * и стабильна при расширении мира. Добавление новых macro-cell
 * никогда не меняет параметры уже известных месторождений.
 *
 * Macro-cell — только пространственный индекс и владелец
 * месторождений, но не его идентичность: залежь имеет собственный
 * центр и пересекает границы ячеек.
 */
final class GeologyGenerator {

    /**
     * Версия процедурного генератора.
     *
     * Входит в идентификатор месторождения, поэтому изменение
     * правил генерации не может молча пересоздать уже известные
     * залежи: их данные хранятся в NBT и на этом остаются.
     */
    static final int GENERATOR_VERSION = 1;

    /**
     * Размер macro-cell в блоках.
     *
     * Подобран так, чтобы крупная залежь (максимальный полуось
     * 480 блоков) гарантированно помещалась в свою ячейку и при
     * этом пересекала границы соседних: запрос геологии смотрит
     * в ячейку позиции и её соседей.
     */
    static final int MACRO_CELL_SIZE = 768;

    /**
     * Число соседних macro-cell, просматриваемых при запросе
     * «залежь под этой точкой».
     *
     * Этого достаточно, потому что центр залежи гарантированно
     * лежит в своей ячейке (проверяется инвариантом
     * {@link #CENTRE_INSIDE_OWN_CELL_INVARIANT}), а значит
     * залежь не выходит за пределы своей ячейки плюс
     * MAX_RADIUS.
     */
    static final int LOOKUP_RING = (int) Math.ceil(
            DepositSizeClass.MAX_RADIUS / (double) MACRO_CELL_SIZE
    );

    /**
     * Радиус обхода для поиска залежей ВОКРУГ точки.
     *
     * Нужен анализатору, который ищет ближайшую аномалию ещё
     * до входа в неё. Для него релевантна любая залежь, чья
     * граница находится не дальше scanRadiusBlocks, то есть
     * центр которой не дальше
     * scanRadiusBlocks + MAX_RADIUS от игрока.
     *
     * Вывод кольца опирается на геометрию, а не на подбор:
     * игрок находится в ячейке C на отрезке [0, CELL), центр
     * залежи из ячейки C+k — на отрезке [k*CELL, k*CELL+CELL).
     * Минимальная дистанция между ними равна
     * gap(k) = k*CELL - (CELL - 1). Ячейка не может содержать
     * релевантный центр, если gap(k) > scanRadius + MAX_RADIUS.
     *
     * Для scanRadius = 200, MAX_RADIUS = 480, CELL = 768:
     *   k = 1 -> gap = 769 > 680  ->  ячейка ещё нужна
     *   k = 2 -> gap = 1537 > 680 ->  ячейка не нужна
     * то есть кольцо = 1. Значение вычисляется, а не зашито.
     *
     * @throws IllegalStateException если инвариант центра нарушен.
     */
    static int nearbyLookupRing(int scanRadiusBlocks) {
        if (!CENTRE_INSIDE_OWN_CELL_INVARIANT) {
            throw new IllegalStateException(
                    "Deposit centre can leave its own macro cell; "
                            + "nearby ring computation is no longer valid"
            );
        }
        if (scanRadiusBlocks < 0) {
            throw new IllegalArgumentException("scanRadiusBlocks cannot be negative");
        }

        long reach = (long) scanRadiusBlocks + DepositSizeClass.MAX_RADIUS;

        /*
         * gap(k) = k*CELL - (CELL - 1) = (k - 1)*CELL + 1.
         * Ищем первый k, для которого gap(k) > reach; кольцо
         * равно k - 1.
         */
        int k = 1;
        while (gapToCell(k) <= reach) {
            k++;
        }
        return k - 1;
    }

    /** Минимальная дистанция между точкой в ячейке C и ячейкой C+k. */
    private static long gapToCell(int k) {
        return (long) (k - 1) * MACRO_CELL_SIZE + 1;
    }

    private static final String MOD_ID = "createcityexpansioncore";

    /** Не более двух кандидатов на ресурс в одной macro-cell. */
    private static final int MAX_CANDIDATES_PER_CELL = 2;

    /**
     * Отступ центра от границы ячейки, в блоках.
     * Гарантирует, что центр залежи всегда внутри своей ячейки,
     * даже при максимальном радиусе.
     */
    private static final int CENTER_INSET = 96;

    /**
     * Инвариант, на котором держатся все пространственные
     * вычисления: центр залежи вместе с максимальным радиусом
     * помещается в свою macro-cell.
     *
     * Пока это так, центр любой залежи лежит внутри своей ячейки,
     * и кольцо обхода можно вычислять аналитически, а не
     * подбирать экспериментально.
     */
    static final boolean CENTRE_INSIDE_OWN_CELL_INVARIANT =
            CENTER_INSET + DepositSizeClass.MAX_RADIUS <= MACRO_CELL_SIZE;

    /** Множитель асимметрии полуосей, чтобы залежь не была кругом. */
    private static final double MIN_AXIS_RATIO = 0.72;
    private static final double MAX_AXIS_RATIO = 1.28;

    /** Отклонение срока эксплуатации: -15% ... +20%. */
    private static final double MIN_RESERVE_NOISE = 0.85;
    private static final double MAX_RESERVE_NOISE = 1.20;

    private static final long SALT_COUNT = 0x1010L;
    private static final long SALT_CENTER_X = 0x1020L;
    private static final long SALT_CENTER_Z = 0x1030L;
    private static final long SALT_SHAPE = 0x1040L;
    private static final long SALT_SIZE = 0x1050L;
    private static final long SALT_AXIS = 0x1060L;
    private static final long SALT_ROTATION = 0x1070L;
    private static final long SALT_CORE = 0x1080L;
    private static final long SALT_QUALITY = 0x1090L;
    private static final long SALT_PEAK = 0x10A0L;

    /**
     * Профиль геологического ресурса.
     *
     * depositsPerCell — средняя частота кандидатов на macro-cell.
     * При 768-блоковой ячейке и стартовой территории 5000x5000
     * это примерно 49 ячеек, что даёт ориентир по количеству
     * месторождений: железо ~12, уголь ~12, известняк ~9,
     * медь ~9, цинк ~10.
     */
    record ResourceProfile(
            ResourceLocation id,
            double depositsPerCell,
            int depth,
            double hardness
    ) {
        ResourceProfile {
            if (depositsPerCell < 0.0 || !Double.isFinite(depositsPerCell)) {
                throw new IllegalArgumentException(
                        "depositsPerCell must be finite and non-negative"
                );
            }
            if (depositsPerCell > MAX_CANDIDATES_PER_CELL) {
                throw new IllegalArgumentException(
                        "depositsPerCell exceeds the per-cell candidate limit"
                );
            }
        }
    }

    /**
     * Ресурсы Alpha-геологии.
     *
     * Железо использует Bulk-цепочку. Уголь, известняк, медь и цинк
     * остаются обычными ресурсами и новых bulk-предметов не требуют.
     */
    static final List<ResourceProfile> RESOURCES = List.of(
            new ResourceProfile(
                    resource("iron"), 0.25, 36, 1.8
            ),
            new ResourceProfile(
                    resource("coal"), 0.25, 18, 1.0
            ),
            new ResourceProfile(
                    resource("limestone"), 0.19, 24, 1.4
            ),
            new ResourceProfile(
                    resource("copper"), 0.19, 30, 1.5
            ),
            new ResourceProfile(
                    resource("zinc"), 0.20, 42, 2.0
            )
    );

    private GeologyGenerator() {
    }

    /**
     * Детерминированные месторождения одной macro-cell.
     * Вызывается только по требованию и только для реально
     * посещённых или запрошенных ячеек.
     */
    static List<GeologicalDeposit> createDeposits(
            ResourceLocation dimensionId,
            long worldSeed,
            int cellX,
            int cellZ
    ) {
        List<GeologicalDeposit> deposits = new ArrayList<>();

        for (ResourceProfile profile : RESOURCES) {
            long cellSeed = cellSeed(worldSeed, dimensionId, cellX, cellZ);
            int candidates = candidateCount(profile, cellSeed);

            for (int index = 0; index < candidates; index++) {
                deposits.add(
                        createDeposit(
                                dimensionId,
                                profile,
                                worldSeed,
                                cellX,
                                cellZ,
                                index,
                                cellSeed
                        )
                );
            }
        }

        return deposits;
    }

    static int candidateCount(ResourceProfile profile, long cellSeed) {
        double perCell = profile.depositsPerCell();
        int whole = (int) Math.floor(perCell);
        double fraction = perCell - whole;
        if (fraction <= 0.0) {
            return whole;
        }

        long hash = GeologyNoise.mix(cellSeed, SALT_COUNT);
        return whole + (GeologyNoise.unit01(hash) < fraction ? 1 : 0);
    }

    private static GeologicalDeposit createDeposit(
            ResourceLocation dimensionId,
            ResourceProfile profile,
            long worldSeed,
            int cellX,
            int cellZ,
            int index,
            long cellSeed
    ) {
        long candidateSeed = GeologyNoise.mix(cellSeed, SALT_SHAPE, index);

        int centerX = cellX * MACRO_CELL_SIZE
                + CENTER_INSET
                + (int) (GeologyNoise.range(
                        GeologyNoise.mix(candidateSeed, SALT_CENTER_X),
                        0.0,
                        MACRO_CELL_SIZE - 2 * CENTER_INSET
                ));
        int centerZ = cellZ * MACRO_CELL_SIZE
                + CENTER_INSET
                + (int) (GeologyNoise.range(
                        GeologyNoise.mix(candidateSeed, SALT_CENTER_Z),
                        0.0,
                        MACRO_CELL_SIZE - 2 * CENTER_INSET
                ));

        DepositSizeClass sizeClass = pickSizeClass(
                GeologyNoise.mix(candidateSeed, SALT_SIZE)
        );

        int radiusX = (int) GeologyNoise.range(
                GeologyNoise.mix(candidateSeed, SALT_AXIS),
                sizeClass.minRadius(),
                sizeClass.maxRadius()
        );
        double axisRatio = GeologyNoise.range(
                GeologyNoise.mix(candidateSeed, SALT_AXIS, 1L),
                MIN_AXIS_RATIO,
                MAX_AXIS_RATIO
        );
        int radiusZ = (int) Math.max(
                DepositSizeClass.MIN_RADIUS,
                Math.round(radiusX * axisRatio)
        );

        double rotation = GeologyNoise.unit01(
                GeologyNoise.mix(candidateSeed, SALT_ROTATION)
        ) * Math.PI;

        long shapeSeed = GeologyNoise.mix(candidateSeed, SALT_SHAPE, 7L);

        double coreDiameter = GeologyNoise.range(
                GeologyNoise.mix(candidateSeed, SALT_CORE),
                DepositShape.MIN_CORE_DIAMETER,
                DepositShape.MAX_CORE_DIAMETER
        );
        double meanRadius = 0.5 * (radiusX + radiusZ);
        double coreFraction = Math.max(
                DepositShape.MIN_CORE_FRACTION,
                Math.min(
                        DepositShape.MAX_CORE_FRACTION,
                        coreDiameter / (2.0 * meanRadius)
                )
        );

        DepositShape shape = new DepositShape(
                centerX,
                centerZ,
                radiusX,
                radiusZ,
                rotation,
                coreFraction,
                shapeSeed
        );

        DepositQualityClass qualityClass = pickQualityClass(
                GeologyNoise.mix(candidateSeed, SALT_QUALITY)
        );
        double peakPerHour = GeologyNoise.range(
                GeologyNoise.mix(candidateSeed, SALT_PEAK),
                qualityClass.minPeakPerHour(),
                qualityClass.maxPeakPerHour()
        );
        double grade = peakPerHour
                / DepositQualityClass.PEAK_POTENTIAL_CEILING;

        long reserve = computeReserve(
                GeologyNoise.mix(candidateSeed, SALT_PEAK, 3L),
                sizeClass
        );

        return new GeologicalDeposit(
                depositId(dimensionId, cellX, cellZ, profile.id(), index),
                profile.id(),
                index,
                reserve,
                reserve,
                grade,
                profile.depth(),
                profile.hardness(),
                peakPerHour,
                shape,
                peakPerHour,
                qualityClass,
                sizeClass
        );
    }

    /**
     * Reserve не зависит от качества: только класс размера и
     * детерминированный шум срока эксплуатации.
     *
     * reserve = 3000 базовых единиц/ч * срок_часов * шум
     */
    static long computeReserve(long seed, DepositSizeClass sizeClass) {
        double lifetimeHours = GeologyNoise.range(
                seed,
                sizeClass.minLifetimeHours(),
                sizeClass.maxLifetimeHours()
        );
        double noise = GeologyNoise.range(
                GeologyNoise.mix(seed, 11L),
                MIN_RESERVE_NOISE,
                MAX_RESERVE_NOISE
        );
        double reserve = DepositSizeClass.REFERENCE_THROUGHPUT_PER_HOUR
                * lifetimeHours
                * noise;
        return Math.max(
                GeologicalDeposit.MIN_INITIAL_RESERVE,
                Math.round(reserve)
        );
    }

    static DepositSizeClass pickSizeClass(long hash) {
        double roll = GeologyNoise.unit01(hash);
        double cumulative = 0.0;
        DepositSizeClass[] values = DepositSizeClass.values();
        for (DepositSizeClass value : values) {
            cumulative += value.share();
            if (roll < cumulative) {
                return value;
            }
        }
        return values[values.length - 1];
    }

    static DepositQualityClass pickQualityClass(long hash) {
        double roll = GeologyNoise.unit01(hash);
        double cumulative = 0.0;
        DepositQualityClass[] values = DepositQualityClass.values();
        for (DepositQualityClass value : values) {
            cumulative += value.share();
            if (roll < cumulative) {
                return value;
            }
        }
        return values[values.length - 1];
    }

    static long cellSeed(
            long worldSeed,
            ResourceLocation dimensionId,
            int cellX,
            int cellZ
    ) {
        return GeologyNoise.mix(
                GeologyNoise.mix(
                        GeologyNoise.mix(worldSeed, dimensionId.toString().hashCode()),
                        cellX
                ),
                cellZ
        );
    }

    static int cellXOf(int blockX) {
        return Math.floorDiv(blockX, MACRO_CELL_SIZE);
    }

    static int cellZOf(int blockZ) {
        return Math.floorDiv(blockZ, MACRO_CELL_SIZE);
    }

    static long cellKey(int cellX, int cellZ) {
        return (((long) cellX) << 32) ^ (cellZ & 0xFFFFFFFFL);
    }

    /**
     * Идентификатор macro-cell. Владеет месторождениями,
     * но не является идентичностью месторождения.
     */
    static ResourceLocation regionId(
            ResourceLocation dimensionId,
            int cellX,
            int cellZ
    ) {
        return ResourceLocation.fromNamespaceAndPath(
                MOD_ID,
                "macro/" + encode(dimensionId) + "/" + cellX + "/" + cellZ
        );
    }

    /**
     * Стабильный идентификатор месторождения:
     * dimension + generatorVersion + macroCell + resource + candidateIndex.
     * Никаких UUID.
     */
    static ResourceLocation depositId(
            ResourceLocation dimensionId,
            int cellX,
            int cellZ,
            ResourceLocation resourceId,
            int depositIndex
    ) {
        return ResourceLocation.fromNamespaceAndPath(
                MOD_ID,
                "deposit/v" + GENERATOR_VERSION + "/" + encode(dimensionId)
                        + "/" + cellX + "/" + cellZ
                        + "/" + encode(resourceId) + "/" + depositIndex
        );
    }

    /**
     * Сохранённый запас для legacy-миграции формата 1.
     * Сама процедурная генерация от этого больше не зависит.
     */
    static long reserveForGrade(double grade) {
        double clamped = Math.max(0.0, Math.min(1.0, grade));
        return 5_000_000L + Math.round(5_000_000L * clamped);
    }

    private static String encode(ResourceLocation value) {
        return HexFormat.of().formatHex(
                value.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    private static ResourceLocation resource(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }
}