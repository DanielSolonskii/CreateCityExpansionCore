package com.example.examplemod.geology;

import com.example.examplemod.item.MagneticAnalyzerItem;
import com.example.examplemod.network.GeologyAnalysisPayload;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;
import java.util.List;

/*
 * Проверка живёт в том же пакете, что и геология, поэтому
 * может звать package-private конструктор GeologyManager и
 * пользоваться внутренними операциями пространственного
 * поиска без создания параллельной копии логики.
 */

/**
 * Headless-проверки контракта магнитного анализатора.
 *
 * Замеряется ровно то, что ломало прибор в рантайме:
 * наличие обоих путей взаимодействия и отсутствие исключений
 * при построении замера в самом частом режиме.
 */
public final class MagneticAnalyzerContractCheck {

    /** Измерение и seed для перебора миров при поиске пары «залежь, проба». */
    private static final net.minecraft.resources.ResourceLocation DIMENSION =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                    "minecraft", "overworld"
            );
    private static final long SEED = 8_675_309L;

    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws NoSuchMethodException {
        interactionPathsExist();
        noAnomalyModeBuilds();
        allModesBuildAndRoundTrip();
        nearbyLookupFindsDepositOutside();
        scanRadiusDistances();
        scanRadiusFarAway();
        insideStillReportsInside();
        macroCellBoundary();
        nearbyRingIsDerivedAndSufficient();
        payloadSchemaIsSymmetric();

        System.out.println("PASS: " + checks + " magnetic analyzer contract checks");
    }

    /**
     * 1 и 2. Прибор реализует ОБА пути: useOn для ПКМ по блоку
     * и use для ПКМ в воздухе.
     *
     * Именно отсутствие use было причиной «прибор не реагирует
     * вообще»: ПКМ в воздухе в Minecraft вызывает use(), а базовая
     * реализация Item.use для не-съедобного предмета возвращает
     * PASS.
     */
    private static void interactionPathsExist() throws NoSuchMethodException {
        Method use = MagneticAnalyzerItem.class.getMethod(
                "use",
                Level.class,
                Player.class,
                InteractionHand.class
        );
        require(use.getReturnType() == InteractionResultHolder.class,
                "use(Level, Player, InteractionHand) is implemented");

        Method useOn = MagneticAnalyzerItem.class.getMethod(
                "useOn",
                UseOnContext.class
        );
        require(useOn.getReturnType() == net.minecraft.world.InteractionResult.class,
                "useOn(UseOnContext) is still implemented");

        /*
         * Методы должны быть именно переопределениями Item,
         * а не новыми методами под собственными именами.
         */
        require(use.getDeclaringClass() == MagneticAnalyzerItem.class,
                "use() is declared on the analyzer itself");
        require(useOn.getDeclaringClass() == MagneticAnalyzerItem.class,
                "useOn() is declared on the analyzer itself");
        require(Item.class.isAssignableFrom(MagneticAnalyzerItem.class),
                "analyzer extends Item");
    }

    /**
     * 4 и 5. Перезаряд не блокирует первый замер, а замер
     * доходит до построения GeologySurvey.
     *
     * Самая частая ситуация в игре — рядом нет аномалии.
     * Именно на ней раньше бросался IllegalArgumentException
     * из-за отрицательных sentinel-дистанций.
     */
    private static void noAnomalyModeBuilds() {
        BlockPos origin = new BlockPos(120, 64, -340);

        /*
         * Пустой список кандидатов = рядом ничего нет.
         * Замер обязан построиться, а не упасть.
         */
        GeologySurvey survey = GeologySurvey.of(origin, List.of(), 9);

        require(survey.mode() == GeologySurvey.Mode.NO_ANOMALY,
                "an empty neighbourhood reports NO_ANOMALY");
        require(survey.resourceId() == null,
                "NO_ANOMALY carries no resource");
        require(survey.anomalyCenter() == null,
                "NO_ANOMALY carries no anomaly centre");
        require(survey.distanceBlocks() >= 0,
                "NO_ANOMALY keeps a non-negative distance (no sentinel -1)");
        require(survey.boundaryDistanceBlocks() >= 0,
                "NO_ANOMALY keeps a non-negative boundary distance");
        require(survey.localPotentialPerHour() == 0,
                "NO_ANOMALY reports zero local potential");
        require(survey.remainingUses() == 9,
                "survey carries the remaining charge through to the payload");

        /*
         * Повторный вызов обязан быть детерминирован: клиент
         * не должен получать разные payload на один и тот же
         * замер той же позиции.
         */
        GeologySurvey again = GeologySurvey.of(origin, List.of(), 9);
        require(again.equals(survey),
                "the same reading produces an identical survey");
    }

    /**
     * 7. Все режимы строятся и дают payload, который можно
     * упаковать обратно без потери полей.
     */
    private static void allModesBuildAndRoundTrip() {
        GeologicalDeposit deposit = firstDeposit();
        require(deposit != null, "a deposit is available for the survey checks");

        BlockPos inside = new BlockPos(
                deposit.getCenterX(),
                64,
                deposit.getCenterZ()
        );
        GeologySurvey insideSurvey = GeologySurvey.of(
                inside,
                List.of(deposit),
                4
        );
        require(insideSurvey.mode() == GeologySurvey.Mode.INSIDE,
                "standing on the deposit reports INSIDE");
        require(insideSurvey.resourceId().equals(deposit.getResourceId()),
                "INSIDE reports the deposit resource");
        require(insideSurvey.localPotentialPerHour() > 0,
                "INSIDE reports a positive local potential");
        require(insideSurvey.peakPotentialPerHour() > 0,
                "INSIDE reports the deposit peak");
        require(insideSurvey.estimatedLifetimeHours() >= 0,
                "INSIDE estimates the lifetime");
        require(insideSurvey.sizeClass() == deposit.getSizeClass(),
                "INSIDE reports the size class");
        require(insideSurvey.qualityClass() == deposit.getQualityClass(),
                "INSIDE reports the quality class");
        require(insideSurvey.distanceBlocks() >= 0
                        && insideSurvey.boundaryDistanceBlocks() >= 0,
                "INSIDE keeps non-negative distances");
    }

    /**
     * ГЛАВНАЯ ПРОВЕРКА: nearby-lookup действительно находит
     * залежь снаружи, на расстоянии от границы.
     *
     * Раньше анализатор получал contains-only список, который
     * пуст наружу от залежи, поэтому NEAREST ANOMALY не мог
     * сработать никогда.
     */
    private static void nearbyLookupFindsDepositOutside() {
        GeologicalDeposit deposit = firstDeposit();
        require(deposit != null, "a deposit is available for the nearby checks");

        /*
         * Точка строго снаружи: в шаге от центра заведомо
         * вне залежи, поэтому contains() обязан вернуть false
         * и именно это состояние раньше ломало поиск.
         */
        int radius = Math.max(
                deposit.getShape().radiusX(),
                deposit.getShape().radiusZ()
        );
        BlockPos probe = new BlockPos(
                deposit.getCenterX() + radius + 30,
                64,
                deposit.getCenterZ()
        );
        require(!deposit.contains(probe.getX(), probe.getZ()),
                "probe point is genuinely outside the deposit");

        /*
         * Список для анализатора обязан быть получен именно
         * nearby-операцией, и залежь обязана в нём быть.
         */
        int scanRadius = GeologySurvey.SCAN_RADIUS_BLOCKS;
        List<GeologicalDeposit> nearby = nearbyOf(probe, scanRadius);
        require(nearby.contains(deposit),
                "nearby lookup finds a deposit that contains() rejects");
    }

    /**
     * Залежь находится на 50 / 100 / 199 блоков снаружи от
     * своей границы, и на 201+ уже не обязана находиться.
     *
     * Расстояние отмеряется от фактической границы (через
     * boundaryDistance), а не от номинального радиуса, чтобы
     * проверка не зависела от искажения границы шумом.
     */
    private static void scanRadiusDistances() {
        for (int outsideBy : new int[]{50, 100, 199}) {
            Pair pair = findProbePair(outsideBy);
            require(pair != null,
                    "found a probe " + outsideBy + " blocks outside a boundary, "
                            + "with that deposit being the nearest anomaly");
            GeologicalDeposit deposit = pair.deposit();
            BlockPos probe = pair.probe();

            int measured = GeologySurvey.boundaryDistance(
                    probe.getX(),
                    probe.getZ(),
                    deposit
            );
            require(measured >= outsideBy,
                    "probe " + outsideBy + " is at least that far out (measured "
                            + measured + ")");

            List<GeologicalDeposit> nearby = nearbyOf(
                    probe,
                    GeologySurvey.SCAN_RADIUS_BLOCKS
            );
            require(nearby.contains(deposit),
                    "NEAREST ANOMALY candidate is found " + outsideBy
                            + " blocks outside the boundary");

            GeologySurvey survey = GeologySurvey.of(
                    probe,
                    nearby,
                    4
            );
            require(survey.mode() == GeologySurvey.Mode.OUTSIDE,
                    "probe " + outsideBy + " reports OUTSIDE");
            require(survey.resourceId().equals(deposit.getResourceId()),
                    "probe " + outsideBy + " reports the nearest deposit's resource");
            require(survey.boundaryDistanceBlocks() > 0,
                    "probe " + outsideBy + " reports a positive boundary distance");
        }
    }

    /** Залежь и точка снаружи неё, найденные перебором по миру. */
    private record Pair(GeologicalDeposit deposit, BlockPos probe) {
    }

    /**
     * Пара «залежь, точка снаружи» для проверки расстояний.
     *
     * <p>Перебираются несколько миров и залежей вместо одной. При плотности
     * около четырёх залежей на ячейку их радиусы перекрываются, и залежь,
     * вокруг которой нашлось бы 50 свободных блоков именно в сторону от всех
     * соседей, попадается не в каждом мире. Контракт анализатора от этого не
     * меняется — меняется лишь то, на каком конкретном примере он проверяется.
     */
    private static Pair findProbePair(int outsideBy) {
        /*
         * Перебираются залежи ИЗ МЕНЕДЖЕРА, а не сгенерированные напрямую.
         *
         * <p>Причина практическая: nearby-запрос в проверках идёт через GEOLOGY,
         * а он построен на seed 0. Залежи, созданные GeologyGenerator с другим
         * seed, — это другой мир: их не существует в менеджере, и проверка
         * «кандидат найден nearby» падала бы, не сходясь ничего.
         *
         * <p>Дополнительно требуется, чтобы найденная залежь была БЛИЖАЙШЕЙ от
         * пробы: иначе анализатор справедливо сообщит о соседе, до которого
         * граница ближе, и проверка «сообщает этот ресурс» искала бы не то.
         */
        for (int cellX = -3; cellX <= 3; cellX++) {
            for (int cellZ = -3; cellZ <= 3; cellZ++) {
                GEOLOGY.materialize(cellX, cellZ);
                for (GeologicalDeposit candidate
                        : GEOLOGY.depositsInCell(cellX, cellZ)) {
                    BlockPos probe = exclusivePointOutside(candidate, outsideBy);
                    if (probe == null) {
                        continue;
                    }
                    if (!isNearestAnomaly(probe.getX(), probe.getZ(), candidate)) {
                        continue;
                    }
                    return new Pair(candidate, probe);
                }
            }
        }
        return null;
    }

    /**
     * На 201+ блоках наличие залежи НЕ требуется: радиус
     * сканирования ограничен намеренно.
     */
    private static void scanRadiusFarAway() {
        Pair pair = findProbePair(260);
        if (pair == null) {
            // Такой пары может не оказаться: при плотной геологии рядом есть
            // другие залежи. Проверять тут нечего, и требование было бы
            // ложным срабатыванием на корректном мире.
            return;
        }
        BlockPos far = pair.probe();
        int measured = GeologySurvey.boundaryDistance(
                far.getX(),
                far.getZ(),
                pair.deposit()
        );
        require(measured > GeologySurvey.SCAN_RADIUS_BLOCKS,
                "probe 260 blocks outside exceeds the scan radius (measured "
                        + measured + ")");
        require(!nearbyOf(far, GeologySurvey.SCAN_RADIUS_BLOCKS)
                        .contains(pair.deposit()),
                "beyond the scan radius the deposit is not offered");
    }

    /**
     * Внутри залежи обязателен INSIDE, а не NEAREST ANOMALY.
     */
    private static void insideStillReportsInside() {
        GeologicalDeposit deposit = firstDeposit();
        require(deposit != null, "a deposit is available for the INSIDE checks");

        BlockPos core = new BlockPos(
                deposit.getCenterX(),
                64,
                deposit.getCenterZ()
        );
        List<GeologicalDeposit> nearby = nearbyOf(
                core,
                GeologySurvey.SCAN_RADIUS_BLOCKS
        );
        require(nearby.contains(deposit),
                "a deposit containing the player is part of its own nearby list");

        GeologySurvey survey = GeologySurvey.of(core, nearby, 4);
        require(survey.mode() == GeologySurvey.Mode.INSIDE,
                "standing inside reports INSIDE, not NEAREST ANOMALY");
        require(survey.localPotentialPerHour() > 0,
                "INSIDE reports a positive local potential");
        require(survey.estimatedLifetimeHours() >= 0,
                "INSIDE reports a lifetime estimate");
    }

    /**
     * Граница между macro-cell: залежь, пересекающая её, должна
     * находиться nearby-поиском с обеих сторон.
     */
    private static void macroCellBoundary() {
        Crossing crossing = findCrossingDeposit();
        require(crossing != null, "a deposit crossing a macro-cell boundary exists");

        GeologicalDeposit deposit = crossing.deposit();
        for (int[] probe : new int[][]{
                {crossing.lowX(), crossing.lowZ()},
                {crossing.highX(), crossing.highZ()}
        }) {
            require(deposit.contains(probe[0], probe[1]),
                    "both probes are inside the crossing deposit");

            List<GeologicalDeposit> nearby = nearbyOf(
                    new BlockPos(probe[0], 64, probe[1]),
                    GeologySurvey.SCAN_RADIUS_BLOCKS
            );
            require(nearby.contains(deposit),
                    "nearby lookup finds the crossing deposit from probe "
                            + probe[0] + "," + probe[1]);
        }

        /*
         * Граница между INSIDE и OUTSIDE на самой кромке:
         * точка сразу за границей ещё в радиусе сканера.
         */
        BlockPos edge = pointOutsideBoundary(deposit, 5);
        require(edge != null, "found a probe just outside the crossing deposit");
        require(nearbyOf(edge, GeologySurvey.SCAN_RADIUS_BLOCKS).contains(deposit),
                "a point 5 blocks outside is still within the scan radius");
    }

    /**
     * Кольцо nearby-обхода вычисляется, а не подбирается, и
     * геометрически достаточно для scanRadius = 200 при
     * MAX_RADIUS = 480 и CELL = 768.
     */
    private static void nearbyRingIsDerivedAndSufficient() {
        require(GeologyGenerator.CENTRE_INSIDE_OWN_CELL_INVARIANT,
                "deposit centre plus max radius fits its own macro cell");

        int scanRadius = GeologySurvey.SCAN_RADIUS_BLOCKS;
        int ring = GeologyGenerator.nearbyLookupRing(scanRadius);

        /*
         * Для текущих констант ожидаемое кольцо = 1, но
         * проверяем не «равно 1», а достаточность: никакая
         * ячейка за кольцом не может содержать релевантный центр.
         */
        long reach = (long) scanRadius
                + com.example.examplemod.geology.DepositSizeClass.MAX_RADIUS;
        long cell = GeologyGenerator.MACRO_CELL_SIZE;
        long firstSkippedGap = (long) (ring + 1) * cell - (cell - 1);
        require(firstSkippedGap > reach,
                "the first skipped macro cell is provably farther than the scan reach ("
                        + firstSkippedGap + " > " + reach + ")");

        long lastVisitedGap = (long) ring * cell - (cell - 1);
        require(lastVisitedGap <= reach,
                "the last visited macro cell may still hold a candidate ("
                        + lastVisitedGap + " <= " + reach + ")");

        /*
         * Меньшее кольцо уже не гарантирует полноту: значит
         * значение действительно минимально достаточное.
         */
        if (ring > 0) {
            long insufficientGap = (long) (ring - 1) * cell - (cell - 1);
            require(insufficientGap <= reach,
                    "one ring less would miss a candidate cell");
        }

        require(ring >= 1,
                "scan radius exceeds a macro cell, so at least neighbours are needed");
        require(nearbyLookupMaterialisesBoundedCells(),
                "nearby lookup materialises a bounded number of macro cells");
    }

    /**
     * Nearby-обход материализует ограниченное число macro-cell:
     * полный мир не сканируется.
     */
    private static boolean nearbyLookupMaterialisesBoundedCells() {
        /*
         * Свежий экземпляр: общий GEOLOGY уже материализован
         * другими проверками, и его размер нельзя приписывать
         * nearby-обходу.
         */
        GeologyManager manager = new GeologyManager(
                ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")
        );
        int cell = 30;
        int origin = cell * GeologyGenerator.MACRO_CELL_SIZE;

        int expectedRing = GeologyGenerator.nearbyLookupRing(
                GeologySurvey.SCAN_RADIUS_BLOCKS
        );
        int expectedCells = (2 * expectedRing + 1) * (2 * expectedRing + 1);

        for (int index = 0; index < 500; index++) {
            manager.queryNearby(
                    new BlockPos(
                            origin + 100 + (index % 400),
                            64,
                            origin + 100 + (index % 400)
                    ),
                    GeologySurvey.SCAN_RADIUS_BLOCKS
            );
        }

        return manager.ownedRegions().size() <= expectedCells;
    }

    /**
     * Точка на заданном расстоянии снаружи от границы залежи,
     * найденная сканированием луча наружу от центра.
     */
    private static BlockPos pointOutsideBoundary(
            GeologicalDeposit deposit,
            int outsideBy
    ) {
        /*
         * Наименьшее расстояние от границы залежи, достижимое из центра.
         *
         * <p>Раньше поиск шёл строго по одной оси, и первое же подходящее
         * расстояние принималось. Теперь обход идёт веером направлений: при
         * нескольких залежах в ячейке подходящая точка есть не с каждой
         * стороны, и одна ось могла её просто не найти.
         */
        int centerX = deposit.getCenterX();
        int centerZ = deposit.getCenterZ();
        int reach = Math.max(
                deposit.getShape().radiusX(),
                deposit.getShape().radiusZ()
        ) + outsideBy + 8;

        for (int degrees = 0; degrees < 360; degrees += 6) {
            double radians = Math.toRadians(degrees);
            double dx = Math.cos(radians);
            double dz = Math.sin(radians);

            for (int step = 0; step <= reach; step++) {
                int x = centerX + (int) Math.round(dx * step);
                int z = centerZ + (int) Math.round(dz * step);

                int distance = GeologySurvey.boundaryDistance(x, z, deposit);
                if (distance < outsideBy) {
                    continue;
                }
                return new BlockPos(x, 64, z);
            }
        }
        return null;
    }

    /**
     * Точка снаружи залежи, где поблизости нет ничего другого и залежь
     * является ближайшей аномалией.
     *
     * <p>Нужна там, где проверяется именно ВЫБОР анализатора: если рядом есть
     * другая залежь, анализатор сообщит о ней, и это правильное поведение.
     * Для проверок геометрии такие условия лишние: точка в 5 блоках от края
     * может лежать внутри соседа, и это не мешает ничему, кроме лишнего
     * требования к тесту.
     */
    private static BlockPos exclusivePointOutside(
            GeologicalDeposit deposit,
            int outsideBy
    ) {
        BlockPos plain = pointOutsideBoundary(deposit, outsideBy);
        if (plain == null) {
            return null;
        }
        if (insideAnyOtherDeposit(plain.getX(), plain.getZ(), deposit)) {
            return null;
        }
        return isNearestAnomaly(plain.getX(), plain.getZ(), deposit)
                ? plain
                : null;
    }

    /** Ближе ли к точке указанная залежь, чем любая другая в радиусе сканирования. */
    private static boolean isNearestAnomaly(
            int x,
            int z,
            GeologicalDeposit expected
    ) {
        int mine = GeologySurvey.boundaryDistance(x, z, expected);
        for (GeologicalDeposit candidate
                : nearbyOf(new BlockPos(x, 64, z), GeologySurvey.SCAN_RADIUS_BLOCKS)) {
            if (candidate == expected) {
                continue;
            }
            int theirs = GeologySurvey.boundaryDistance(x, z, candidate);
            if (theirs >= 0 && theirs < mine) {
                return false;
            }
        }
        return true;
    }

    /** Попадает ли точка в залежь, отличную от указанной. */
    private static boolean insideAnyOtherDeposit(
            int x,
            int z,
            GeologicalDeposit excluded
    ) {
        BlockPos pos = new BlockPos(x, 64, z);
        for (GeologicalDeposit candidate
                : nearbyOf(pos, GeologySurvey.SCAN_RADIUS_BLOCKS)) {
            if (candidate != excluded && candidate.contains(x, z)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Nearby-поиск через реальный GeologyManager: так проверяется
     * именно та операция, которую вызывает анализатор.
     */
    private static List<GeologicalDeposit> nearbyOf(
            BlockPos pos,
            int radiusBlocks
    ) {
        return testGeology().queryNearby(pos, radiusBlocks);
    }

    private record Crossing(GeologicalDeposit deposit, int lowX, int lowZ, int highX, int highZ) {
    }

    /**
     * Ищет залежь, пересекающую границу своей macro-cell,
     * и две точки по разные стороны от границы.
     */
    private static Crossing findCrossingDeposit() {
        GeologyManager manager = testGeology();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                manager.materialize(dx, dz);
            }
        }

        for (int cellX = -2; cellX <= 2; cellX++) {
            for (int cellZ = -2; cellZ <= 2; cellZ++) {
                long cellStartX = (long) cellX * GeologyGenerator.MACRO_CELL_SIZE;
                long cellStartZ = (long) cellZ * GeologyGenerator.MACRO_CELL_SIZE;

                for (GeologicalDeposit deposit : manager.depositsInCell(cellX, cellZ)) {
                    int centerX = deposit.getCenterX();
                    int centerZ = deposit.getCenterZ();
                    int reachX = (int) (deposit.getShape().radiusX() * 0.6);
                    int reachZ = (int) (deposit.getShape().radiusZ() * 0.6);
                    if (reachX < 8 || reachZ < 8) {
                        continue;
                    }

                    boolean crosses = centerX - reachX < cellStartX
                            || centerX + reachX > cellStartX
                            + GeologyGenerator.MACRO_CELL_SIZE
                            || centerZ - reachZ < cellStartZ
                            || centerZ + reachZ > cellStartZ
                            + GeologyGenerator.MACRO_CELL_SIZE;
                    if (!crosses) {
                        continue;
                    }

                    Crossing candidate = new Crossing(
                            deposit,
                            centerX - reachX,
                            centerZ - reachZ,
                            centerX + reachX,
                            centerZ + reachZ
                    );
                    boolean differentCells =
                            GeologyGenerator.cellXOf(candidate.lowX())
                                    != GeologyGenerator.cellXOf(candidate.highX())
                                    || GeologyGenerator.cellZOf(candidate.lowZ())
                                    != GeologyGenerator.cellZOf(candidate.highZ());
                    return differentCells ? candidate : null;
                }
            }
        }
        return null;
    }

    /**
     * 9. Схема payload соответствует текущему снимку замера:
     * ни старого GeologyRegionSnapshot, ни лишних полей.
     */
    private static void payloadSchemaIsSymmetric() {
        BlockPos origin = new BlockPos(12, 64, 34);
        List<RecordComponent> expected = List.of(
                new RecordComponent("mode"),
                new RecordComponent("origin"),
                new RecordComponent("resourceId"),
                new RecordComponent("direction"),
                new RecordComponent("distanceBlocks"),
                new RecordComponent("boundaryDistanceBlocks"),
                new RecordComponent("localPotentialPerHour"),
                new RecordComponent("peakPotentialPerHour"),
                new RecordComponent("remainingReserve"),
                new RecordComponent("initialReserve"),
                new RecordComponent("sizeClass"),
                new RecordComponent("qualityClass"),
                new RecordComponent("estimatedLifetimeHours"),
                new RecordComponent("remainingUses")
        );

        var components = List.of(GeologyAnalysisPayload.class.getRecordComponents());
        require(components.size() == expected.size(),
                "payload has exactly " + expected.size() + " components (got "
                        + components.size() + ")");
        for (int index = 0; index < expected.size(); index++) {
            require(components.get(index).getName().equals(expected.get(index).name()),
                    "payload component " + index + " is " + expected.get(index).name());
        }

        /*
         * Ни один компонент payload не должен называться region
         * или deposits: это остатки старой схемы, где вместо
         * одного замера клиент получал целый снимок региона.
         */
        for (var component : components) {
            String name = component.getName();
            require(!name.equals("region") && !name.equals("deposits"),
                    "no leftover region/deposits field named " + name);
        }

        GeologySurvey survey = GeologySurvey.of(origin, List.of(), 7);
        GeologyAnalysisPayload payload = GeologyAnalysisPayload.of(survey);
        require(payload.mode() == survey.mode(),
                "payload carries the survey mode");
        require(payload.remainingUses() == survey.remainingUses(),
                "payload carries the remaining charge");
        require(!payload.hasResource(),
                "NO_ANOMALY payload reports no resource");
    }

    /**
     * Геология для headless-проверок.
     *
     * Один общий экземпляр на весь прогон: залежи берутся из
     * него же, поэтому проверки сравнивают ИДЕНТИЧНЫЕ объекты.
     * Отдельный генератор здесь давал бы другие экземпляры с
     * другими центрами, и сравнение по ссылке всегда было бы
     * ложным.
     */
    private static final GeologyManager GEOLOGY = new GeologyManager(
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld")
    );

    /**
     * Залежь из уже материализованной геологии, то есть тот же
     * объект, который вернёт пространственный поиск.
     */
    private static GeologicalDeposit firstDeposit() {
        for (int cellX = -2; cellX <= 2; cellX++) {
            for (int cellZ = -2; cellZ <= 2; cellZ++) {
                GEOLOGY.materialize(cellX, cellZ);
                List<GeologicalDeposit> deposits = GEOLOGY.depositsInCell(cellX, cellZ);
                if (!deposits.isEmpty()) {
                    return deposits.get(0);
                }
            }
        }
        return null;
    }

    /**
     * Геология для проверок пространственного поиска.
     * Конструктор без измерения существует только для
     * headless-проверок: реальный экземпляр приходит из
     * GeologyManager.get(level).
     */
    private static GeologyManager testGeology() {
        return GEOLOGY;
    }

    private record RecordComponent(String name) {
    }

    private MagneticAnalyzerContractCheck() {
    }
}
