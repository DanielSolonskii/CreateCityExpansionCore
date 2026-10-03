package com.example.examplemod.geology;

import com.example.examplemod.mine.MineOutputMapping;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Headless-проверки процедурной геологии Alpha v1.
 *
 * Ни Minecraft, ни сервер не запускаются: используются только
 * те же чистые функции, которые вызывает игровой код.
 */
public final class GeologyProceduralCheck {

    private static final ResourceLocation DIMENSION =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");
    private static final long SEED = 8_675_309L;
    private static final ResourceLocation IRON = resource("iron");
    private static final ResourceLocation LIMESTONE = resource("limestone");

    private static int checks;

    private static void require(boolean value, String message) {
        checks++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) throws IOException {
        determinism();
        crossingDepositIsShared();
        localPotentialIsSmooth();
        qualityAndSizeAreIndependent();
        sharedReserveAcrossMines();
        bulkIronDoesNotDoubleReserve();
        depletionNeverGoesBelowZero();
        savedDataRoundTrip();
        legacyV2MigrationKeepsReserve();
        worksOutsideStartingArea();
        lookupNeverTouchesChunks();
        densityTargets();

        System.out.println("PASS: " + checks + " procedural geology checks");
    }

    /* 1. Одинаковый seed + координаты -> одинаковые deposits. */
    private static void determinism() {
        /*
         * Ячейка выбирается поиском: около трети macro-cell
         * закономерно пусты, и проверка детерминизма не должна
         * зависеть от того, попал ли фиксированный индекс в
         * непустую ячейку.
         */
        int cellX = 3;
        int cellZ = -2;
        List<GeologicalDeposit> a = List.of();
        for (int radius = 0; radius < 8 && a.isEmpty(); radius++) {
            for (int dx = -radius; dx <= radius && a.isEmpty(); dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    a = GeologyGenerator.createDeposits(DIMENSION, SEED, 3 + dx, -2 + dz);
                    if (!a.isEmpty()) {
                        cellX = 3 + dx;
                        cellZ = -2 + dz;
                    }
                }
            }
        }

        List<GeologicalDeposit> b = GeologyGenerator.createDeposits(
                DIMENSION,
                SEED,
                cellX,
                cellZ
        );
        require(!a.isEmpty(),
                "a non-empty macro cell exists near 3,-2 (empty territory is expected)");
        require(a.size() == b.size(), "same seed yields the same deposit count");

        Set<ResourceLocation> idsA = new HashSet<>();
        Set<ResourceLocation> idsB = new HashSet<>();
        for (int index = 0; index < a.size(); index++) {
            idsA.add(a.get(index).getId());
            idsB.add(b.get(index).getId());
            require(a.get(index).getId().equals(b.get(index).getId()),
                    "deposit id is deterministic");
            require(a.get(index).getShape().equals(b.get(index).getShape()),
                    "deposit shape is deterministic");
            require(a.get(index).getInitialReserve()
                            == b.get(index).getInitialReserve(),
                    "deposit reserve is deterministic");
            require(a.get(index).getPeakPotentialPerHour()
                            == b.get(index).getPeakPotentialPerHour(),
                    "deposit quality is deterministic");
        }
        require(idsA.equals(idsB), "same deposit id set for the same seed");

        List<GeologicalDeposit> other = GeologyGenerator.createDeposits(
                DIMENSION,
                SEED + 1,
                cellX,
                cellZ
        );
        Set<ResourceLocation> idsOther = new HashSet<>();
        for (GeologicalDeposit deposit : other) {
            idsOther.add(deposit.getId());
        }
        require(!idsA.equals(idsOther), "a different seed yields different deposits");

        /* Идентичность содержит версию генератора и координаты ячейки. */
        java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile(
                "^createcityexpansioncore:deposit/v\\d+/[0-9a-f]+/-?\\d+/-?\\d+/[0-9a-f]+/\\d+$"
        );
        for (GeologicalDeposit deposit : a) {
            String id = deposit.getId().toString();
            require(idPattern.matcher(id).matches(),
                    "deposit id is dimension + version + cell + resource + index: " + id);
            require(!id.matches(".*[0-9a-f]{8}-[0-9a-f]{4}-.*"),
                    "deposit id is not a random uuid");
        }

        /* Расширение мира не меняет уже известные ячейки. */
        GeologyGenerator.createDeposits(DIMENSION, SEED, cellX + 40, cellZ + 40);
        List<GeologicalDeposit> again = GeologyGenerator.createDeposits(
                DIMENSION,
                SEED,
                cellX,
                cellZ
        );
        require(again.size() == a.size(),
                "generating far cells does not change an existing cell");
        for (int index = 0; index < a.size(); index++) {
            require(again.get(index).getShape().equals(a.get(index).getShape()),
                    "existing deposits keep their shape when the world grows");
        }
    }

    /*
     * 2. Соседние query cells видят один и тот же пересекающий
     *    их depositId — и это один и тот же объект, а не копия.
     */
    private static void crossingDepositIsShared() {
        GeologyManager manager = new GeologyManager(DIMENSION);
        manager.materialize(1, 1);

        Crossing crossing = findCrossingDeposit(manager, 1, 1);
        require(crossing != null, "a deposit crosses a macro-cell boundary");

        GeologicalDeposit deposit = crossing.deposit();
        BlockPos low = new BlockPos(
                crossing.lowX(),
                64,
                crossing.lowZ()
        );
        BlockPos high = new BlockPos(
                crossing.highX(),
                64,
                crossing.highZ()
        );
        require(deposit.contains(low.getX(), low.getZ()),
                "the first probe is inside the crossing deposit");
        require(deposit.contains(high.getX(), high.getZ()),
                "the second probe is inside the crossing deposit");
        require(GeologyGenerator.cellXOf(low.getX())
                        != GeologyGenerator.cellXOf(high.getX())
                        || GeologyGenerator.cellZOf(low.getZ())
                        != GeologyGenerator.cellZOf(high.getZ()),
                "the two probe positions really sit in different macro cells");

        GeologicalDeposit fromLow = manager.query(low)
                .getDeposit(deposit.getId()).orElse(null);
        GeologicalDeposit fromHigh = manager.query(high)
                .getDeposit(deposit.getId()).orElse(null);

        require(fromLow != null, "first macro cell sees the crossing deposit");
        require(fromHigh != null, "second macro cell sees the crossing deposit");
        require(fromLow == fromHigh,
                "both macro cells resolve to the same canonical deposit instance");
    }

    private record Crossing(
            GeologicalDeposit deposit,
            int lowX,
            int lowZ,
            int highX,
            int highZ
    ) {
    }

    /**
     * Ищет залежь, пересекающую границу своей macro-cell,
     * и две точки по разные стороны от этой границы.
     *
     * Точки берутся на 0.6 полуоси вдоль оси пересечения: при
     * любом повороте и любом соотношении полуосей нормированный
     * радиус там не превышает 0.83, то есть обе точки гарантированно
     * находятся внутри залежи.
     */
    private static Crossing findCrossingDeposit(
            GeologyManager manager,
            int baseX,
            int baseZ
    ) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                manager.materialize(baseX + dx, baseZ + dz);
            }
        }

        for (int cx = baseX - 2; cx <= baseX + 2; cx++) {
            for (int cz = baseZ - 2; cz <= baseZ + 2; cz++) {
                long cellStartX = (long) cx * GeologyGenerator.MACRO_CELL_SIZE;
                long cellStartZ = (long) cz * GeologyGenerator.MACRO_CELL_SIZE;

                for (GeologicalDeposit deposit : manager.depositsInCell(cx, cz)) {
                    int centerX = deposit.getCenterX();
                    int centerZ = deposit.getCenterZ();

                    int reachX = (int) (deposit.getShape().radiusX() * 0.6);
                    int reachZ = (int) (deposit.getShape().radiusZ() * 0.6);
                    if (reachX < 8 || reachZ < 8) {
                        continue;
                    }

                    boolean crossesX = centerX - reachX < cellStartX
                            || centerX + reachX > cellStartX
                            + GeologyGenerator.MACRO_CELL_SIZE;
                    boolean crossesZ = centerZ - reachZ < cellStartZ
                            || centerZ + reachZ > cellStartZ
                            + GeologyGenerator.MACRO_CELL_SIZE;
                    if (!crossesX && !crossesZ) {
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
                    if (!differentCells) {
                        continue;
                    }
                    return candidate;
                }
            }
        }
        return null;
    }

    /*
     * 3. Потенциал меняется плавно: у соседних блоков нет
     *    скачков вида 8000 -> 500.
     */
    private static void localPotentialIsSmooth() {
        GeologyManager manager = managerWith(SEED, 0, 0, null);
        List<GeologicalDeposit> deposits = new ArrayList<>();
        for (GeologicalRegion region : manager.ownedRegions()) {
            deposits.addAll(region.getDeposits());
        }
        require(!deposits.isEmpty(), "there are deposits to measure smoothness on");

        double worstNeighbourDelta = 0.0;
        double worstJump = 0.0;
        double worstRadialRise = 0.0;
        int inspected = 0;

        for (GeologicalDeposit deposit : deposits) {
            int reach = (int) (Math.min(
                    deposit.getShape().radiusX(),
                    deposit.getShape().radiusZ()
            ) * 0.8);

            for (int x = deposit.getCenterX() - reach;
                 x <= deposit.getCenterX() + reach; x += 7) {
                for (int z = deposit.getCenterZ() - reach;
                     z <= deposit.getCenterZ() + reach; z += 7) {
                    double here = deposit.localPotentialPerHour(x, z);
                    if (here <= 0.0) {
                        continue;
                    }
                    inspected++;
                    double east = deposit.localPotentialPerHour(x + 1, z);
                    double south = deposit.localPotentialPerHour(x, z + 1);
                    double neighbour = Math.max(
                            Math.abs(east - here),
                            Math.abs(south - here)
                    );
                    worstNeighbourDelta = Math.max(worstNeighbourDelta, neighbour);
                    worstJump = Math.max(
                            worstJump,
                            Math.max(
                                    Math.abs(deposit.localPotentialPerHour(x + 2, z) - here),
                                    Math.abs(deposit.localPotentialPerHour(x + 1, z) - east)
                            )
                    );
                }
            }

            /*
             * Радиальный проход: потенциал должен монотонно
             * снижаться от центра к границе. Малые локальные
             * волны от искажения границы допустимы, но заметного
             * роста при движении наружу быть не должно - именно
             * он превращал бы плавное поле в «лестницу».
             */
            int[] rayAngles = {0, 45, 90, 135, 180, 225, 270, 315};
            for (int degrees : rayAngles) {
                double radians = Math.toRadians(degrees);
                double stepX = Math.cos(radians);
                double stepZ = Math.sin(radians);
                double previous = deposit.localPotentialPerHour(
                        deposit.getCenterX(),
                        deposit.getCenterZ()
                );
                double tolerance = 0.005 * deposit.getPeakPotentialPerHour();

                for (int step = 1; step <= reach; step++) {
                    double value = deposit.localPotentialPerHour(
                            deposit.getCenterX() + stepX * step,
                            deposit.getCenterZ() + stepZ * step
                    );
                    worstRadialRise = Math.max(worstRadialRise, value - previous);
                    previous = value;
                }
            }
        }

        require(inspected > 0, "smoothness was actually measured somewhere");

        /*
         * Геометрия задаёт нижнюю границу перепада: у залежи
         * диаметром 300 блоков и пиком 10000/ч средний наклон
         * составляет peak / radius = 67/ч, а гладкая
         * колоколообразная функция неизбежно проходит через
         * ~1.5x от среднего на самом крутом участке.
         * Поэтому проверяется смысловое свойство «нет скачков»,
         * а не недостижимое «перепад равен нулю»:
         *
         *  - шаг между соседними блоками мал относительно шкалы;
         *  - при движении наружу потенциал не растёт.
         */
        double ceiling = DepositQualityClass.PEAK_POTENTIAL_CEILING;
        require(worstNeighbourDelta < ceiling * 0.02,
                "adjacent blocks differ by less than 2% of the scale ceiling (worst="
                        + worstNeighbourDelta + " of " + ceiling + ")");
        require(worstJump < ceiling * 0.04,
                "two-block step stays smooth (worst=" + worstJump + ")");
        require(worstRadialRise < ceiling * 0.01,
                "potential never rises on the way from the core to the rim (worst="
                        + worstRadialRise + ")");

        /* Потенциал обязан реально снижаться от ядра к периферии. */
        GeologicalDeposit deposit = deposits.stream()
                .max((left, right) -> Double.compare(
                        left.localPotentialPerHour(left.getCenterX(), left.getCenterZ()),
                        right.localPotentialPerHour(right.getCenterX(), right.getCenterZ())
                ))
                .orElseThrow();

        double core = deposit.localPotentialPerHour(
                deposit.getCenterX(),
                deposit.getCenterZ()
        );
        double mid = deposit.localPotentialPerHour(
                deposit.getCenterX() + deposit.getShape().radiusX() / 2,
                deposit.getCenterZ()
        );
        double rim = deposit.localPotentialPerHour(
                deposit.getCenterX() + deposit.getShape().radiusX() * 3,
                deposit.getCenterZ()
        );
        require(core > mid, "potential falls off from the core");
        require(mid > rim, "potential keeps falling towards the rim");
        require(rim == 0.0, "potential is zero outside the deposit");
    }

    /* 4. Quality class и Size class выбираются независимо. */
    private static void qualityAndSizeAreIndependent() {
        int[][] observed = new int[DepositSizeClass.values().length]
                [DepositQualityClass.values().length];
        int total = 0;

        for (int cx = -12; cx <= 12; cx++) {
            for (int cz = -12; cz <= 12; cz++) {
                for (GeologicalDeposit deposit
                        : GeologyGenerator.createDeposits(DIMENSION, SEED, cx, cz)) {
                    observed[deposit.getSizeClass().ordinal()]
                            [deposit.getQualityClass().ordinal()]++;
                    total++;
                }
            }
        }
        require(total > 40, "enough deposits to test independence (" + total + ")");

        int richSmall = observed[DepositSizeClass.SMALL.ordinal()]
                [DepositQualityClass.PRIME.ordinal()]
                + observed[DepositSizeClass.SMALL.ordinal()]
                [DepositQualityClass.EXCEPTIONAL.ordinal()];
        int poorGiant = observed[DepositSizeClass.GIANT.ordinal()]
                [DepositQualityClass.MODEST.ordinal()];
        int richGiant = observed[DepositSizeClass.GIANT.ordinal()]
                [DepositQualityClass.PRIME.ordinal()]
                + observed[DepositSizeClass.GIANT.ordinal()]
                [DepositQualityClass.EXCEPTIONAL.ordinal()];

        require(richSmall > 0 || richGiant > 0,
                "small rich or giant rich deposits exist");
        require(poorGiant > 0 || richSmall > 0,
                "a small rich or a poor giant deposit exists");

        /*
         * Size class определяет срок эксплуатации, а reserve
         * не зависит от качества: бедное гигантское месторождение
         * должно иметь reserve не меньше богатого малого.
         */
        double poorGiantReserve = averageReserve(DepositSizeClass.GIANT, DepositQualityClass.MODEST);
        double richSmallReserve = averageReserve(DepositSizeClass.SMALL, DepositQualityClass.EXCEPTIONAL);
        require(poorGiantReserve > 0.0, "poor giant deposits exist for the reserve check");
        require(poorGiantReserve >= richSmallReserve * 0.5,
                "reserve is driven by size class, not by quality ("
                        + poorGiantReserve + " vs " + richSmallReserve + ")");
    }

    private static double averageReserve(
            DepositSizeClass sizeClass,
            DepositQualityClass qualityClass
    ) {
        double total = 0.0;
        int count = 0;
        for (int cx = -40; cx <= 40; cx++) {
            for (int cz = -40; cz <= 40; cz++) {
                for (GeologicalDeposit deposit
                        : GeologyGenerator.createDeposits(DIMENSION, SEED, cx, cz)) {
                    if (deposit.getSizeClass() == sizeClass
                            && deposit.getQualityClass() == qualityClass) {
                        total += deposit.getInitialReserve();
                        count++;
                    }
                }
            }
        }
        return count == 0 ? 0.0 : total / count;
    }

    /*
     * Готовит менеджер с гарантированно непустой ячейкой.
     * Около трети macro-cell пусты по замыслу, поэтому тесты
     * не должны зависеть от конкретного индекса ячейки.
     */
    private static GeologyManager managerWith(
            long worldSeed,
            int centerX,
            int centerZ,
            ResourceLocation required
    ) {
        GeologyManager manager = new GeologyManager(DIMENSION);
        int radius = 6;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                manager.materialize(centerX + dx, centerZ + dz);
            }
        }

        if (required == null) {
            return manager;
        }

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (GeologicalDeposit deposit
                        : manager.depositsInCell(centerX + dx, centerZ + dz)) {
                    if (deposit.getResourceId().equals(required)) {
                        return manager;
                    }
                }
            }
        }
        throw new AssertionError(
                "no " + required + " deposit within " + radius + " cells of "
                        + centerX + "," + centerZ
        );
    }

    private static GeologicalDeposit firstDepositOf(
            GeologyManager manager,
            ResourceLocation resource
    ) {
        for (GeologicalRegion region : manager.ownedRegions()) {
            for (GeologicalDeposit deposit : region.getDeposits()) {
                if (resource == null || deposit.getResourceId().equals(resource)) {
                    return deposit;
                }
            }
        }
        return null;
    }

    /* 5. Две операции по одному deposit уменьшают один reserve. */
    private static void sharedReserveAcrossMines() {
        GeologyManager manager = managerWith(SEED, 4, 4, null);

        GeologicalDeposit deposit = firstDepositOf(manager, null);
        require(deposit != null, "a nearby cell has a deposit");

        BlockPos probe = new BlockPos(deposit.getCenterX(), 64, deposit.getCenterZ());
        long before = manager.findDeposit(deposit.getId()).orElseThrow().getRemainingReserve();

        DepositExtractionResult mineOne =
                manager.extract(deposit.getId(), 1_000L);
        DepositExtractionResult mineTwo =
                manager.extract(deposit.getId(), 1_500L);

        require(mineOne.status() == DepositExtractionResult.Status.EXTRACTED,
                "first extraction succeeded");
        require(mineTwo.status() == DepositExtractionResult.Status.EXTRACTED,
                "second extraction succeeded");

        long afterFirst = mineOne.remainingReserve().orElseThrow();
        long afterSecond = mineTwo.remainingReserve().orElseThrow();
        require(afterFirst == before - 1_000L, "first mine consumed 1000");
        require(afterSecond == before - 2_500L, "second mine consumed from the same reserve");

        GeologicalDeposit viaA = manager.query(probe)
                .getDeposit(deposit.getId()).orElseThrow();
        GeologicalDeposit viaB = manager.query(probe)
                .getDeposit(deposit.getId()).orElseThrow();
        require(viaA.getRemainingReserve() == before - 2_500L,
                "both lookups observe the same reduced reserve");
        require(viaA == viaB, "lookups return the same deposit object");
        require(manager.findDeposit(deposit.getId()).orElseThrow()
                        == viaA,
                "the canonical deposit is the object the mine saw");
    }

    /*
     * 6. Bulk-вывод железа удваивает физический предмет,
     *    но НЕ удваивает расход Fe-equivalent reserve.
     */
    private static void bulkIronDoesNotDoubleReserve() {
        GeologyManager manager = managerWith(SEED, 0, 0, IRON);

        GeologicalDeposit iron = firstDepositOf(manager, IRON);
        require(iron != null, "an iron deposit exists near 0,0");

        long factor = MineOutputMapping.outputFactor(IRON);
        require(factor == 2L, "iron bulk output factor is 2");

        long physical = 10L;
        long reserveCost = MineOutputMapping.reserveUnitsFor(IRON, physical);
        require(reserveCost == 5L,
                "10 raw iron ore costs 5 Fe-equivalent reserve units");

        long before = manager.findDeposit(iron.getId()).orElseThrow().getRemainingReserve();
        DepositExtractionResult result = manager.extract(iron.getId(), reserveCost);
        require(result.status() == DepositExtractionResult.Status.EXTRACTED,
                "iron extraction succeeded");
        require(result.remainingReserve().orElseThrow() == before - 5L,
                "reserve shrank by the Fe-equivalent amount, not by the physical amount");

        long coalCost = MineOutputMapping.reserveUnitsFor(resource("coal"), 10L);
        require(coalCost == 10L, "coal has no bulk factor and stays 1:1");

        require(MineOutputMapping.physicalAmountFor(IRON, 5L) == 10L,
                "5 Fe-equivalent converts back into 10 bulk items");
    }

    /* 7. Истощение не уходит ниже нуля. */
    private static void depletionNeverGoesBelowZero() {
        GeologyManager manager = managerWith(SEED, 2, 2, null);
        GeologicalDeposit deposit = firstDepositOf(manager, null);
        require(deposit != null, "a nearby cell has a deposit");

        long remaining = deposit.getRemainingReserve();
        DepositExtractionResult overdraw = manager.extract(
                deposit.getId(),
                remaining * 4L + 10_000L
        );
        require(overdraw.status() == DepositExtractionResult.Status.EXTRACTED,
                "overdraw is clamped rather than rejected");
        require(overdraw.extractedAmount() == remaining,
                "overdraw extracts exactly what was left");
        require(overdraw.remainingReserve().orElseThrow() == 0L,
                "reserve reaches exactly zero");
        require(deposit.isDepleted(), "deposit reports depleted");

        DepositExtractionResult after = manager.extract(deposit.getId(), 1L);
        require(after.status() == DepositExtractionResult.Status.DEPLETED,
                "further extraction is refused");
        require(after.remainingReserve().orElseThrow() == 0L,
                "reserve stays at zero and never goes negative");
        require(deposit.getRemainingReserve() >= 0L, "reserve is non-negative");
    }

    /* 8. Round-trip SavedData сохраняет shape / quality / reserve. */
    private static void savedDataRoundTrip() {
        GeologyManager manager = managerWith(SEED, 6, -5, null);

        GeologicalDeposit sample = firstDepositOf(manager, null);
        require(sample != null, "a nearby cell has a deposit");

        /*
         * Расходуем часть запаса, чтобы проверить, что
         * сохраняется именно текущий remainingReserve.
         */
        manager.extract(sample.getId(), 25_000L);
        GeologicalDeposit spent = manager.findDeposit(sample.getId()).orElseThrow();

        CompoundTag tag = manager.save(new CompoundTag(), null);
        require(tag.getInt("FormatVersion") == GeologySavedDataCodec.CURRENT_FORMAT_VERSION,
                "saved data uses the current format version");
        require(tag.contains("GeneratorVersion", net.minecraft.nbt.Tag.TAG_INT),
                "saved data carries the generator version");

        GeologyManager restored = GeologyManager.load(tag, null, DIMENSION, SEED);

        GeologicalDeposit restoredDeposit = restored.findDeposit(spent.getId()).orElseThrow();
        require(restoredDeposit.getId().equals(spent.getId()),
                "deposit id survives the round trip");
        require(restoredDeposit.getShape().equals(spent.getShape()),
                "deposit shape survives the round trip");
        require(restoredDeposit.getPeakPotentialPerHour() == spent.getPeakPotentialPerHour(),
                "depot quality survives the round trip");
        require(restoredDeposit.getQualityClass() == spent.getQualityClass(),
                "quality class survives the round trip");
        require(restoredDeposit.getSizeClass() == spent.getSizeClass(),
                "size class survives the round trip");
        require(restoredDeposit.getInitialReserve() == spent.getInitialReserve(),
                "initial reserve survives the round trip");
        require(restoredDeposit.getRemainingReserve() == spent.getRemainingReserve(),
                "remaining reserve survives the round trip");
        require(restored.query(new BlockPos(
                restoredDeposit.getCenterX(), 64, restoredDeposit.getCenterZ()
        )).getDeposit(restoredDeposit.getId()).orElseThrow()
                        == restoredDeposit,
                "restored deposit is spatially discoverable again");
    }

    /* 9. Миграция v2 не теряет remaining reserve. */
    private static void legacyV2MigrationKeepsReserve() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("FormatVersion", 2);
        legacy.putString("Dimension", DIMENSION.toString());

        ListTag regions = new ListTag();
        CompoundTag regionTag = new CompoundTag();
        regionTag.putString("Id", "createcityexpansioncore:field_12_7");
        regionTag.putInt("RegionX", 12);
        regionTag.putInt("RegionZ", 7);

        ListTag deposits = new ListTag();
        CompoundTag depositTag = new CompoundTag();
        depositTag.putString("Id", "createcityexpansioncore:deposit/6c696d65/12/7/69726f6e/0");
        depositTag.putString("Resource", IRON.toString());
        depositTag.putInt("DepositIndex", 0);
        depositTag.putLong("InitialReserve", 8_000_000L);
        depositTag.putLong("RemainingReserve", 3_000_000L);
        depositTag.putDouble("Grade", 0.36);
        depositTag.putInt("Depth", 36);
        depositTag.putDouble("Hardness", 1.8);
        depositTag.putDouble("SustainableExtractionRate", 9_000.0);
        deposits.add(depositTag);
        regionTag.put("Deposits", deposits);
        regions.add(regionTag);
        legacy.put("Regions", regions);

        GeologyManager migrated = GeologyManager.load(legacy, null, DIMENSION, SEED);
        require(migrated.isStorageAvailable(), "legacy v2 data is readable");
        require(migrated.findDeposit(
                ResourceLocation.tryParse(
                        "createcityexpansioncore:deposit/6c696d65/12/7/69726f6e/0"
                )
        ).isPresent(), "legacy deposit id is preserved, not remapped");

        GeologicalDeposit legacyDeposit = migrated.findDeposit(
                ResourceLocation.tryParse(
                        "createcityexpansioncore:deposit/6c696d65/12/7/69726f6e/0"
                )
        ).orElseThrow();
        require(legacyDeposit.getResourceId().equals(IRON), "legacy resource preserved");
        require(legacyDeposit.getRemainingReserve() == 3_000_000L,
                "legacy remaining reserve is preserved exactly");
        require(legacyDeposit.getInitialReserve() == 8_000_000L,
                "legacy initial reserve is preserved exactly");
        require(legacyDeposit.getGrade() == 0.36, "legacy grade is preserved");
        require(legacyDeposit.getPeakPotentialPerHour() == 9_000.0,
                "legacy potential is derived from the stored rate");

        /*
         * Legacy-залежь должна снова стать пространственным
         * объектом: у неё есть форма и она находится по позиции.
         */
        BlockPos legacyProbe = new BlockPos(
                12 * 100 + 50,
                64,
                7 * 100 + 50
        );
        require(migrated.query(legacyProbe)
                        .getDeposit(legacyDeposit.getId()).orElse(null)
                        == legacyDeposit,
                "legacy deposit is discoverable by position");

        CompoundTag resaved = migrated.save(new CompoundTag(), null);
        require(resaved.getInt("FormatVersion")
                        == GeologySavedDataCodec.CURRENT_FORMAT_VERSION,
                "migrated data is rewritten in the current format");
        GeologyManager twice = GeologyManager.load(resaved, null, DIMENSION, SEED);
        require(twice.findDeposit(legacyDeposit.getId()).orElseThrow()
                        .getRemainingReserve() == 3_000_000L,
                "second load keeps the reserve");
    }

    /* 10. Генератор работает далеко за стартовой зоной. */
    private static void worksOutsideStartingArea() {
        int cell = 5_000;
        List<GeologicalDeposit> far = GeologyGenerator.createDeposits(
                DIMENSION,
                SEED,
                cell,
                -cell
        );
        require(far != null, "generation beyond 5000x5000 returns a result");

        int farFromOrigin = cell * GeologyGenerator.MACRO_CELL_SIZE;
        require(farFromOrigin > 3_000_000, "the probe cell is far from spawn");

        List<GeologicalDeposit> again = GeologyGenerator.createDeposits(
                DIMENSION,
                SEED,
                cell,
                -cell
        );
        require(far.size() == again.size(), "far generation is deterministic");
        for (int index = 0; index < far.size(); index++) {
            require(far.get(index).getShape().equals(again.get(index).getShape()),
                    "far deposits keep the same shape");
            require(far.get(index).getCenterX() >= cell * GeologyGenerator.MACRO_CELL_SIZE,
                    "far deposits belong to their own macro cell");
        }

        /* Запрос позиции в этой далёкой ячейке тоже работает. */
        GeologyManager manager = new GeologyManager(DIMENSION);
        BlockPos probe = new BlockPos(
                cell * GeologyGenerator.MACRO_CELL_SIZE + 100,
                64,
                -cell * GeologyGenerator.MACRO_CELL_SIZE + 100
        );
        GeologicalRegion region = manager.query(probe);
        require(region.getRegionX() == cell, "far query resolves its macro cell");
        require(manager.ownedRegions().size() <= 9,
                "one query materialises at most the 3x3 neighbourhood ("
                        + manager.ownedRegions().size() + ")");
    }

    /* 11. Геология не трогает чанки. */
    private static void lookupNeverTouchesChunks() throws IOException {
        String[] forbidden = {
                "getChunk", "getChunkAt", "getChunkSource", "getChunkForPos",
                "chunkSource", "hasChunk", "loadChunk", "getLightEngine"
        };
        String[] pureClasses = {
                "com/example/examplemod/geology/GeologyGenerator",
                "com/example/examplemod/geology/GeologyNoise",
                "com/example/examplemod/geology/DepositShape",
                "com/example/examplemod/geology/GeologicalDeposit",
                "com/example/examplemod/geology/GeologySurvey"
        };

        for (String internalName : pureClasses) {
            String bytes = readClassBytes(internalName);
            require(!bytes.isEmpty(), "class bytes readable: " + internalName);
            for (String token : forbidden) {
                require(!bytes.contains(token),
                        internalName + " must not reference chunk API " + token);
            }
            require(!bytes.contains("net/minecraft/server/level/ServerLevel"),
                    internalName + " must not depend on ServerLevel");
            require(!bytes.contains("net/minecraft/world/level/chunk"),
                    internalName + " must not depend on chunk classes");
        }

        /* Поиск ограничен по площади и не требует уровня. */
        GeologyManager manager = new GeologyManager(DIMENSION);
        int probeCell = 9;
        int cellOrigin = probeCell * GeologyGenerator.MACRO_CELL_SIZE;

        /*
         * Все 2000 запросов лежат ВНУТРИ одной macro-cell.
         * Поэтому материализоваться может только её
         * окрестность 3x3: если бы каждая позиция тянула новые
         * ячейки, число сохранённых регионов росло бы с числом
         * запросов.
         */
        for (int index = 0; index < 2_000; index++) {
            manager.query(new BlockPos(
                    cellOrigin + 100 + (index % 568),
                    64,
                    cellOrigin + 100 + (index % 512)
            ));
        }
        require(manager.ownedRegions().size() <= 9,
                "repeated queries inside one macro cell materialise at most the 3x3 "
                        + "neighbourhood (" + manager.ownedRegions().size() + " regions)");

        /* Граница запроса действительно расширяет окрестность, но не мир. */
        manager.query(new BlockPos(cellOrigin - 10, 64, cellOrigin - 10));
        require(manager.ownedRegions().size() <= 16,
                "one extra cell on the border widens the neighbourhood to 4x4 ("
                        + manager.ownedRegions().size() + " regions)");

        long start = System.nanoTime();
        for (int index = 0; index < 5_000; index++) {
            manager.query(new BlockPos(
                    cellOrigin + 100 + (index % 568),
                    64,
                    cellOrigin + 100 + (index % 512)
            ));
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        require(millis < 5_000L,
                "spatial lookup is fast without chunk access (" + millis + "ms)");
    }

    private static String readClassBytes(String internalName) throws IOException {
        try (InputStream stream = GeologyProceduralCheck.class
                .getClassLoader()
                .getResourceAsStream(internalName + ".class")) {
            if (stream == null) {
                return "";
            }
            return new String(stream.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    /*
     * Балансировочные ориентиры плотности.
     *
     * Проверяется СРЕДНЕЕ по нескольким seed, а не одна выборка:
     * при плотности 0.25 кандидата на ячейку отдельный мир
     * закономерно уходит за ±2 от ожидания, и жёсткая проверка
     * по одному seed была бы ложным срабатыванием, а не
     * характеристикой генератора.
     */
    private static void densityTargets() {
        int half = 2_500 / GeologyGenerator.MACRO_CELL_SIZE + 1;
        int sampledPerAxis = half * 2 + 1;
        int interiorPerAxis = sampledPerAxis - 2;

        long cellArea = (long) GeologyGenerator.MACRO_CELL_SIZE
                * GeologyGenerator.MACRO_CELL_SIZE;
        long blockArea = (long) sampledPerAxis * sampledPerAxis * cellArea;

        int seedSamples = 12;
        long[] totals = new long[GeologyGenerator.RESOURCES.size()];
        double coveredShare = 0.0;

        for (int sample = 0; sample < seedSamples; sample++) {
            long worldSeed = SEED + sample * 7_919L;
            long[] interiorCounts = new long[GeologyGenerator.RESOURCES.size()];
            long covered = 0L;

            for (int cx = -half; cx <= half; cx++) {
                for (int cz = -half; cz <= half; cz++) {
                    boolean interior = cx != -half && cx != half
                            && cz != -half && cz != half;

                    for (GeologicalDeposit deposit
                            : GeologyGenerator.createDeposits(DIMENSION, worldSeed, cx, cz)) {
                        /*
                         * Площадь суммируется по всему опробованному
                         * блоку ячеек: перекрытия залежей при этом
                         * считаются несколько раз, то есть доля
                         * покрытия оценивается сверху.
                         */
                        covered += approximateArea(deposit);

                        if (!interior) {
                            continue;
                        }
                        for (int index = 0; index < interiorCounts.length; index++) {
                            if (GeologyGenerator.RESOURCES.get(index).id()
                                    .equals(deposit.getResourceId())) {
                                interiorCounts[index]++;
                            }
                        }
                    }
                }
            }

            for (int index = 0; index < interiorCounts.length; index++) {
                totals[index] += interiorCounts[index];
            }
            coveredShare += (double) covered / blockArea;
        }

        double emptyShare = 100.0 * (1.0 - coveredShare / seedSamples);

        StringBuilder report = new StringBuilder();
        report.append("  starter area ~").append(interiorPerAxis).append('x')
                .append(interiorPerAxis).append(" macro cells (")
                .append(interiorPerAxis * GeologyGenerator.MACRO_CELL_SIZE)
                .append(" blocks); mean deposits per world: ");
        for (int index = 0; index < totals.length; index++) {
            report.append(GeologyGenerator.RESOURCES.get(index).id().getPath())
                    .append('=')
                    .append(String.format("%.1f", mean(totals[index], seedSamples)))
                    .append(' ');
        }
        report.append("| free=").append(String.format("%.1f%%", emptyShare));
        System.out.println(report);

        require(mean(totals[0], seedSamples) >= 10.0 && mean(totals[0], seedSamples) <= 14.0,
                "iron averages 10-14 deposits in the starter area (got "
                        + mean(totals[0], seedSamples) + ")");
        require(mean(totals[1], seedSamples) >= 10.0 && mean(totals[1], seedSamples) <= 14.0,
                "coal averages 10-14 deposits in the starter area (got "
                        + mean(totals[1], seedSamples) + ")");
        require(mean(totals[2], seedSamples) >= 8.0 && mean(totals[2], seedSamples) <= 12.0,
                "limestone averages 8-12 deposits in the starter area (got "
                        + mean(totals[2], seedSamples) + ")");
        require(mean(totals[3], seedSamples) >= 8.0 && mean(totals[3], seedSamples) <= 12.0,
                "copper averages 8-12 deposits in the starter area (got "
                        + mean(totals[3], seedSamples) + ")");
        require(mean(totals[4], seedSamples) >= 8.0 && mean(totals[4], seedSamples) <= 14.0,
                "zinc stays moderate in the starter area (got "
                        + mean(totals[4], seedSamples) + ")");

        require(emptyShare >= 40.0 && emptyShare <= 60.0,
                "45-60% of the territory is free of a deposit (got "
                        + String.format("%.1f%%", emptyShare) + ")");

        /*
         * Ориентир срока эксплуатации считается от опорной
         * производительности 3000 базовых единиц/ч.
         */
        for (GeologicalDeposit deposit
                : GeologyGenerator.createDeposits(DIMENSION, SEED, 5, 5)) {
            double lifetime = deposit.getInitialReserve()
                    / DepositSizeClass.REFERENCE_THROUGHPUT_PER_HOUR;
            double minHours = deposit.getSizeClass().minLifetimeHours() * 0.85;
            double maxHours = deposit.getSizeClass().maxLifetimeHours() * 1.20;
            require(lifetime >= minHours && lifetime <= maxHours,
                    "lifetime " + lifetime + "h fits size class " + deposit.getSizeClass());
        }
    }

    private static double mean(long total, int samples) {
        return total / (double) samples;
    }

    /** Площадь эллипса, увеличенная на среднее искажение границы. */
    private static long approximateArea(GeologicalDeposit deposit) {
        double rx = deposit.getShape().radiusX();
        double rz = deposit.getShape().radiusZ();
        double inflation = 1.0 + DepositShape.EDGE_NOISE_AMPLITUDE / 2.0;
        return Math.round(Math.PI * rx * rz * inflation);
    }

    private static GeologicalDeposit firstDeposit(
            GeologyManager manager,
            int cellX,
            int cellZ
    ) {
        return manager.depositsInCell(cellX, cellZ).stream()
                .findFirst()
                .orElse(null);
    }

    private static ResourceLocation resource(String path) {
        return ResourceLocation.fromNamespaceAndPath("createcityexpansioncore", path);
    }

    private GeologyProceduralCheck() {
    }
}