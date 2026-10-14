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
     * <p><b>Версия 2 пересоздаёт геологию мира целиком.</b> Причина: в версии 1
     * идентичность ресурса не входила в seed, поэтому все пять ресурсов в одной
     * macro-cell получали одно и то же положение, форму, качество и запас. В
     * мире это выглядело как 20 точек, в каждой из которых уголь лежал точно на
     * железе, а сканер показывал только железо — первым в списке ресурсов, при
     * полном равенстве потенциалов. Никакими правками сохранённых залежей это
     * не лечилось: они остались бы лежать друг на друге навсегда.
     *
     * <p>Версия входит в идентификатор месторождения, поэтому новые залежи
     * получают другие ID и не конфликтуют со старыми. Старые записи отбрасываются
     * при загрузке (см. GeologySavedDataCodec), а не «достраиваются».
     */
    static final int GENERATOR_VERSION = 2;

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
    /** Идентичность ресурса: без этой соли все ресурсы ячейки сливались. */
    private static final long SALT_RESOURCE = 0x1095L;
    /** Общая для всех ресурсов монетка заселённости ячейки. */
    private static final long SALT_CELL_OCCUPIED = 0x1098L;
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
     * <p><b>candidateChances — вероятности появления кандидатов по порядку.</b>
     * Раньше здесь было одно число {@code depositsPerCell} на все кандидаты,
     * поэтому ресурс мог дать максимум одну залежь, а доля пустых клеток
     * получалась одинаковой для всех ресурсов.
     *
     * <p>Список, а не одно число, потому что нужна разная частота для первого и
     * второго кандидата: первый определяет «есть ли здесь вообще что-то», второй
     * добавляет плотность. Оба числа независимы друг от друга.
     */
    record ResourceProfile(
            ResourceLocation id,
            List<Double> candidateChances,
            int depth,
            double hardness
    ) {
        ResourceProfile {
            if (candidateChances == null || candidateChances.isEmpty()) {
                throw new IllegalArgumentException(
                        "candidateChances must not be empty for " + id
                );
            }
            if (candidateChances.size() > MAX_CANDIDATES_PER_CELL) {
                throw new IllegalArgumentException(
                        "candidateChances exceeds the per-cell candidate limit for " + id
                );
            }
            for (Double chance : candidateChances) {
                if (chance == null || chance < 0.0 || chance > 1.0 || !Double.isFinite(chance)) {
                    throw new IllegalArgumentException(
                            "candidate chance must be finite and within [0,1] for " + id
                    );
                }
            }
        }

        /** Ожидаемое число залежей этого ресурса на macro-cell. */
        double expectedPerCell() {
            double sum = 0.0;
            for (Double chance : candidateChances) {
                sum += chance;
            }
            return sum;
        }
    }

    /**
     * Ресурсы Alpha-геологии.
     *
     * <p>Первый кандидат появляется с вероятностью 0.80, второй — 0.15. Вместе с
     * {@link #CELL_OCCUPIED_CHANCE} это даёт около 25% пустых клеток и 4-5 залежей
     * в заселённых.
     *
     * <p>Железо использует Bulk-цепочку. Уголь, известняк, медь и цинк остаются
     * обычными ресурсами и новых bulk-предметов не требуют.
     */
    static final List<ResourceProfile> RESOURCES = List.of(
            new ResourceProfile(
                    resource("iron"), List.of(0.80, 0.15), 36, 1.8
            ),
            new ResourceProfile(
                    resource("coal"), List.of(0.80, 0.15), 18, 1.0
            ),
            new ResourceProfile(
                    resource("limestone"), List.of(0.80, 0.15), 24, 1.4
            ),
            new ResourceProfile(
                    resource("copper"), List.of(0.80, 0.15), 30, 1.5
            ),
            new ResourceProfile(
                    resource("zinc"), List.of(0.80, 0.15), 42, 2.0
            )
    );

    /**
     * Вероятность того, что macro-cell вообще заселена.
     *
     * <p><b>Зачем отдельная монетка клетки.</b> Требования «пустых клеток около
     * 25%» и «в заселённой клетке 4-5 залежей» несовместимы при независимых
     * бросках по каждому ресурсу: чтобы пустых клеток было 25%, каждый ресурс
     * должен появляться с вероятностью около 0.24, и тогда на клетку выходило бы
     * 1.2 залежи вместо 4-5. Общая монетка снимает противоречие: сначала решается
     * «есть ли здесь что искать», и только потом каждый ресурс решает независимо.
     *
     * <p>Именно так и выглядит мир: где-то участок пустой, где-то богатый.
     */
    static final double CELL_OCCUPIED_CHANCE = 0.75;

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
        long cellSeed = cellSeed(worldSeed, dimensionId, cellX, cellZ);

        /*
         * Монетка заселённости клетки. Общая для всех ресурсов намеренно:
         * см. CELL_OCCUPIED_CHANCE. Раньше общей была и другая монета — решения
         * «есть ли залежь», — но она считалась для каждого ресурса из одного и
         * того же числа без соли, поэтому все пять ресурсов получали одно
         * решение одновременно: либо все пять, либо никакого. Отсюда были и
         * 75% пустых клеток, и складывание всех ресурсов в одну точку.
         */
        if (!isCellOccupied(cellSeed)) {
            return deposits;
        }

        for (ResourceProfile profile : RESOURCES) {
            /*
             * Идентичность ресурса входит в seed. Это ключевая правка всего
             * инцидента: без неё candidateSeed одинаков для всех ресурсов
             * ячейки, а значит одинаковы центр, форма, качество и запас.
             */
            long resourceSeed = GeologyNoise.mix(
                    cellSeed,
                    SALT_RESOURCE,
                    profile.id().toString().hashCode()
            );
            int candidates = candidateCount(profile, resourceSeed);

            for (int index = 0; index < candidates; index++) {
                deposits.add(
                        createDeposit(
                                dimensionId,
                                profile,
                                worldSeed,
                                cellX,
                                cellZ,
                                index,
                                resourceSeed
                        )
                );
            }
        }

        return deposits;
    }

    /** Заселена ли ячейка: общий для всех ресурсов бросок. */
    static boolean isCellOccupied(long cellSeed) {
        return GeologyNoise.unit01(GeologyNoise.mix(cellSeed, SALT_CELL_OCCUPIED))
                < CELL_OCCUPIED_CHANCE;
    }

    /**
     * Сколько кандидатов даёт ресурс в этой ячейке.
     *
     * <p>Каждый кандидат бросает свою монетку, поэтому второй не зависит от
     * первого: можно получить ноль, одну или две залежи. Счёт по порядку идёт до
     * первого неудачного броска — так «два кандидата» не могут появиться без
     * первого, и распределение остаётся разумным.
     */
    static int candidateCount(ResourceProfile profile, long resourceSeed) {
        int count = 0;
        List<Double> chances = profile.candidateChances();

        for (int index = 0; index < chances.size(); index++) {
            long hash = GeologyNoise.mix(resourceSeed, SALT_COUNT, index);
            if (GeologyNoise.unit01(hash) >= chances.get(index)) {
                break;
            }
            count++;
        }

        return count;
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