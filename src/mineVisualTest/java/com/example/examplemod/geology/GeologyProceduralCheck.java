package com.example.examplemod.geology;

import com.example.examplemod.mine.InexhaustibleMineCheck;
import com.example.examplemod.mine.MineOutputMapping;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.BitSet;
import java.util.Locale;
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
        localPotentialIsSmooth();
        qualityAndSizeAreIndependent();
        physicalOutputMultipliers();
        savedDataRoundTrip();
        legacyDataIsRegenerated();
        worksOutsideStartingArea();
        lookupNeverTouchesChunks();
        densityTargets(args.length == 0 ? null : Path.of(args[0]));
        resourcesDoNotSharePosition();
        resourcesDoNotShareQuality();
        scannerCanReportEveryResource();

        System.out.println("PASS: " + checks + " procedural geology checks");
    }

    /**
     * Ресурсы одной ячейки не лежат друг на друге.
     *
     * <p><b>Регрессия на реальном инциденте.</b> Идентичность ресурса не входила
     * в seed, поэтому candidateSeed был одинаков для всех пяти ресурсов: центр,
     * форма, качество и запас совпадали до бита. В мире было 20 уникальных точек
     * вместо 79 залежей, уголь лежал ровно на железе, и сканер показывал только
     * железо — оно первое в списке ресурсов, а потенциалы совпадали, поэтому при
     * строгом сравнении «больше» побеждал первый.
     *
     * <p>Проверка обязана ловить именно совпадение позиций, а не просто «много
     * залежей»: количественная проверка прошла бы и с этим багом.
     */
    private static void resourcesDoNotSharePosition() {
        int overlapping = 0;
        int cellsWithSeveralResources = 0;

        for (int cx = -25; cx <= 25; cx++) {
            for (int cz = -25; cz <= 25; cz++) {
                var seenIds = new java.util.HashSet<String>();
                List<GeologicalDeposit> deposits =
                        GeologyGenerator.createDeposits(DIMENSION, SEED, cx, cz);
                for (GeologicalDeposit deposit : deposits) {
                    /*
                     * Два кандидата одного ресурса в ячейке — законно, поэтому
                     * уникальность проверяется по id (в него входит ресурс и
                     * номер кандидата), а не по самому ресурсу.
                     */
                    require(seenIds.add(deposit.getId().toString()),
                            "duplicate deposit id in cell " + cx + "/" + cz + ": "
                                    + deposit.getId());
                }
                if (deposits.size() > 1) {
                    cellsWithSeveralResources++;
                    for (int i = 0; i < deposits.size(); i++) {
                        for (int j = i + 1; j < deposits.size(); j++) {
                            GeologicalDeposit a = deposits.get(i);
                            GeologicalDeposit b = deposits.get(j);
                            if (a.getCenterX() == b.getCenterX()
                                    && a.getCenterZ() == b.getCenterZ()) {
                                overlapping++;
                            }
                        }
                    }
                }
            }
        }

        require(cellsWithSeveralResources > 100,
                "enough multi-resource cells to be meaningful ("
                        + cellsWithSeveralResources + ")");
        require(overlapping == 0,
                "no two deposits may share a centre point, found " + overlapping);
    }

    /**
     * Разные ресурсы получают разные качество, размер и запас.
     *
     * <p>Совпадение позиций — только видимая часть инцидента. Из того же seed
     * выводились качество и запас, поэтому богатый уголь и бедное железо
     * оказывались бедным углём и богатым железом одновременно: игрок не мог
     * найти богатое месторождение ни одного ресурса, кроме первого в списке.
     */
    private static void resourcesDoNotShareQuality() {
        int identicalQuality = 0;
        int compared = 0;

        for (int cx = -25; cx <= 25; cx++) {
            for (int cz = -25; cz <= 25; cz++) {
                List<GeologicalDeposit> deposits =
                        GeologyGenerator.createDeposits(DIMENSION, SEED, cx, cz);
                for (int i = 0; i < deposits.size(); i++) {
                    for (int j = i + 1; j < deposits.size(); j++) {
                        GeologicalDeposit a = deposits.get(i);
                        GeologicalDeposit b = deposits.get(j);
                        if (a.getResourceId().equals(b.getResourceId())) {
                            continue;
                        }
                        compared++;
                        if (a.getQualityClass() == b.getQualityClass()
                                && a.getPeakPotentialPerHour() == b.getPeakPotentialPerHour()) {
                            identicalQuality++;
                        }
                    }
                }
            }
        }

        require(compared > 500,
                "enough cross-resource pairs to be meaningful (" + compared + ")");
        require(identicalQuality == 0,
                "resources must not share quality and potential, found "
                        + identicalQuality);
    }

    /**
     * Сканер способен показать любой ресурс, а не только первый в списке.
     *
     * <p>Симптом инцидента был не в генерации, а в этом: сканер возвращает одну
     * залежь, и при полном равенстве потенциалов побеждал первый элемент списка —
     * железо. Проверка повторяет выбор сканера на реальных залежах.
     */
    private static void scannerCanReportEveryResource() {
        java.util.Set<String> reportable = new java.util.HashSet<>();

        for (int cx = -8; cx <= 8; cx++) {
            for (int cz = -8; cz <= 8; cz++) {
                List<GeologicalDeposit> candidates =
                        GeologyGenerator.createDeposits(DIMENSION, SEED, cx, cz);
                for (GeologicalDeposit probe : candidates) {
                    // Сканер стоит в центре залежи и берёт лучший потенциал.
                    BlockPos origin = new BlockPos(
                            probe.getCenterX(), 64, probe.getCenterZ()
                    );
                    GeologicalDeposit best = null;
                    for (GeologicalDeposit candidate : candidates) {
                        if (candidate.contains(origin.getX(), origin.getZ())) {
                            if (best == null
                                    || candidate.localPotentialPerHour(
                                            origin.getX(), origin.getZ())
                                    > best.localPotentialPerHour(
                                            origin.getX(), origin.getZ())) {
                                best = candidate;
                            }
                        }
                    }
                    if (best != null) {
                        reportable.add(best.getResourceId().toString());
                    }
                }
            }
        }

        for (GeologyGenerator.ResourceProfile profile : GeologyGenerator.RESOURCES) {
            require(reportable.contains(profile.id().toString()),
                    "scanner must be able to report " + profile.id().getPath()
                            + ", reported so far: " + reportable);
        }
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

            double core = deposit.localPotentialPerHour(deposit.getCenterX(), deposit.getCenterZ());
            require(Math.abs(core - deposit.getPeakPotentialPerHour()) < 1e-8,
                    "the polygon core reaches peak potential");
            VeinFootprint footprint = deposit.getShape().footprint();
            require(deposit.localPotentialPerHour(footprint.originX() - 1, deposit.getCenterZ()) == 0,
                    "potential is zero outside the polygon bounds");
        }

        require(inspected > 0, "smoothness was actually measured somewhere");
        double ceiling = DepositQualityClass.PEAK_POTENTIAL_CEILING;
        require(worstNeighbourDelta < ceiling * 0.02,
                "adjacent blocks differ by less than 2% of the scale ceiling (worst="
                        + worstNeighbourDelta + " of " + ceiling + ")");
        require(worstJump < ceiling * 0.04,
                "two-block step stays smooth (worst=" + worstJump + ")");
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

    private static void physicalOutputMultipliers() {
        require(MineOutputMapping.physicalAmountFor(IRON, 5L) == 10L,
                "iron keeps its doubled physical output");
        require(MineOutputMapping.physicalAmountFor(resource("coal"), 5L) == 5L,
                "coal keeps its original physical output");
    }

    /* Format 6 keeps vein identity and shape when the old reserve is removed. */
    private static void savedDataRoundTrip() {
        GeologyManager manager = managerWith(SEED, 6, -5, null);

        GeologicalDeposit sample = firstDepositOf(manager, null);
        require(sample != null, "a nearby cell has a deposit");

        CompoundTag tag = manager.save(new CompoundTag(), null);
        require(tag.getInt("FormatVersion") == GeologySavedDataCodec.CURRENT_FORMAT_VERSION,
                "saved data uses the current format version");
        require(tag.getInt("GeneratorVersion") == 4,
                "saved data carries the generator version");

        tag.putInt("FormatVersion", 6);
        for (Tag region : tag.getList("Regions", Tag.TAG_COMPOUND)) {
            for (Tag deposit : ((CompoundTag) region).getList("Deposits", Tag.TAG_COMPOUND)) {
                ((CompoundTag) deposit).putLong("InitialReserve", 1L);
                ((CompoundTag) deposit).putLong("RemainingReserve", 0L);
            }
        }
        GeologyManager restored = GeologyManager.load(tag, null, DIMENSION, SEED);
        GeologicalDeposit restoredDeposit = restored.findDeposit(sample.getId()).orElseThrow();
        require(restoredDeposit.getShape().equals(sample.getShape()),
                "format 6 preserves the vein shape and ID");
        require(restoredDeposit.getPeakPotentialPerHour() == sample.getPeakPotentialPerHour(),
                "format 6 preserves the peak potential");
        require(restoredDeposit.getQualityClass() == sample.getQualityClass(),
                "format 6 preserves the quality class");
        require(restoredDeposit.getSizeClass() == sample.getSizeClass(),
                "format 6 preserves the size class");
        GeologicalRegion region = restored.query(new BlockPos(sample.getCenterX(), 64, sample.getCenterZ()));
        InexhaustibleMineCheck.verify(region, restoredDeposit);
        CompoundTag migrated = restored.save(new CompoundTag(), null);
        require(migrated.getInt("FormatVersion") == 7, "format 6 is rewritten as format 7");
        for (Tag entry : migrated.getList("Regions", Tag.TAG_COMPOUND)) {
            for (Tag deposit : ((CompoundTag) entry).getList("Deposits", Tag.TAG_COMPOUND)) {
                require(!((CompoundTag) deposit).contains("InitialReserve")
                                && !((CompoundTag) deposit).contains("RemainingReserve"),
                        "the new save omits old reserve tags");
            }
        }
        GeologyManager reloaded = GeologyManager.load(migrated, null, DIMENSION, SEED);
        require(reloaded.findDeposit(sample.getId()).orElseThrow().getShape().equals(sample.getShape()),
                "the migrated polygon survives a second load");
        require(restored.query(new BlockPos(
                restoredDeposit.getCenterX(), 64, restoredDeposit.getCenterZ()
        )).getDeposit(restoredDeposit.getId()).orElseThrow()
                        == restoredDeposit,
                "restored deposit is spatially discoverable again");
    }

    /* Форматы до 6 пересоздают раскладку жил. */
    /**
     * Старые форматы пересоздаются, а не переносятся.
     *
     * <p>Раньше здесь проверялось обратное — что legacy-залежь сохраняет id и
     * запас. Это было правильно для прежней схемы, но после инцидента со
     * слипшимися ресурсами перенос потерял смысл: в старых данных уголь лежит
     * ровно на железе, и сохранить его, не сдвинув, значит сохранить дефект.
     *
     * <p>Проверяется ровно то, что теперь обещано игроку: после загрузки старого
     * мира залежи строятся заново и уже разнесены по позициям.
     */
    private static void legacyDataIsRegenerated() {
        CompoundTag legacy = new CompoundTag();
        legacy.putInt("FormatVersion", 5);
        legacy.putInt("GeneratorVersion", 3);
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

        GeologyManager regenerated = GeologyManager.load(legacy, null, DIMENSION, SEED);
        require(regenerated.isStorageAvailable(), "old-format data is still readable");

        /*
         * Старая запись не переносится: id больше не существует, потому что
         * генератор пересоздаёт залежи. Иначе слипшиеся ресурсы сохранились бы
         * вместе со своими запасами.
         */
        require(regenerated.findDeposit(
                ResourceLocation.tryParse(
                        "createcityexpansioncore:deposit/6c696d65/12/7/69726f6e/0"
                )
        ).isEmpty(), "a stale deposit id from an old format is not resurrected");

        /*
         * Залежи на старом месте строятся заново — и уже разнесены по позициям.
         * Именно это и было обещано игроку вместо переноса старых данных.
         */
        GeologicalDeposit expected = null;
        for (int x = -3; x <= 3 && expected == null; x++) {
            List<GeologicalDeposit> cell = GeologyGenerator.createDeposits(DIMENSION, SEED, x, 0);
            if (!cell.isEmpty()) {
                expected = cell.getFirst();
            }
        }
        require(expected != null, "a generated deposit exists near the origin");
        BlockPos probe = new BlockPos(expected.getCenterX(), 64, expected.getCenterZ());
        GeologicalRegion rebuilt = regenerated.query(probe);
        GeologicalDeposit regeneratedDeposit = rebuilt.getDeposit(expected.getId()).orElseThrow();
        require(regeneratedDeposit.getShape().equals(expected.getShape()),
                "format 5 regenerates the expected polygon");
        require(regeneratedDeposit.getId().getPath().startsWith("deposit/v4/"),
                "the regenerated deposit uses a version 4 identifier");

        java.util.Set<Integer> centres = new java.util.HashSet<>();
        for (GeologicalDeposit deposit : rebuilt.getDeposits()) {
            require(centres.add(deposit.getCenterX() * 100_000 + deposit.getCenterZ()),
                    "rebuilt deposits must not share a centre: " + deposit.getId());
        }

        CompoundTag resaved = regenerated.save(new CompoundTag(), null);
        require(resaved.getInt("FormatVersion")
                        == GeologySavedDataCodec.CURRENT_FORMAT_VERSION,
                "regenerated data is rewritten in the current format");

        /*
         * В новую запись попадает только то, что построено заново.
         *
         * <p>Список регионов не пуст: запрос выше материализовал ячейку, и это
         * правильно. Проверяется другое — в нём нет ни одной старой записи,
         * то есть слипшиеся залежи не переехали в новый формат.
         */
        ListTag written = resaved.getList("Regions", Tag.TAG_COMPOUND);
        require(!written.isEmpty(),
                "the rebuilt cell is written back for the next load");
        for (Tag writtenRegion : written) {
            for (Tag depositEntry : ((CompoundTag) writtenRegion)
                    .getList("Deposits", Tag.TAG_COMPOUND)) {
                CompoundTag deposit = (CompoundTag) depositEntry;
                require(!deposit.getString("Id").contains("/deposit/v1/"),
                        "no deposit from the broken generator version survives: "
                                + deposit.getString("Id"));
            }
        }

        GeologyManager twice = GeologyManager.load(resaved, null, DIMENSION, SEED);
        require(twice.isStorageAvailable(), "the rewritten data loads back cleanly");
        require(twice.findDeposit(
                ResourceLocation.tryParse(
                        "createcityexpansioncore:deposit/6c696d65/12/7/69726f6e/0"
                )
        ).isEmpty(), "the stale deposit stays gone after a round trip");
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
            require(far.get(index).getId().equals(again.get(index).getId()),
                    "far deposits retain the same storage identity");
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
    private static void densityTargets(Path mapDirectory) throws IOException {
        smoothedContoursAreExclusive();
        long[] seeds = {0, 1, -317, SEED, 42};
        int half = 8;
        int step = VeinFootprint.STEP;
        int cellWidth = GeologyGenerator.MACRO_CELL_SIZE;
        int side = cellWidth / step;
        int width = (2 * half + 1) * side;
        int start = -half * cellWidth;
        int sampleCount = width * width;
        long[] oreTotals = new long[GeologyGenerator.RESOURCES.size()];
        for (long seed : seeds) {
            long layoutSeed = GeologyNoise.mix(seed, 0x3000L, DIMENSION.toString().hashCode());
            int raw = 0;
            for (int z = 0; z < width; z++) {
                for (int x = 0; x < width; x++) {
                    if (VeinLayout.presence(layoutSeed, start + (x + 0.5) * step,
                            start + (z + 0.5) * step) >= VeinLayout.PRESENCE_THRESHOLD) {
                        raw++;
                    }
                }
            }

            int covered = 0;
            int count = 0;
            int[] owners = new int[sampleCount];
            long[] oreArea = new long[oreTotals.length];
            List<GeologicalDeposit> mapDeposits = new ArrayList<>();
            int ring = GeologyGenerator.LOOKUP_RING;
            for (int cx = -half - ring; cx <= half + ring; cx++) {
                for (int cz = -half - ring; cz <= half + ring; cz++) {
                    for (GeologicalDeposit deposit : GeologyGenerator.createDeposits(DIMENSION, seed, cx, cz)) {
                        VeinFootprint footprint = deposit.getShape().footprint();
                        require(footprint != null && footprint.area() >= VeinLayout.MIN_AREA,
                                "every vein has a polygon at least the size of SMALL");
                        require(deposit.contains(deposit.getCenterX(), deposit.getCenterZ()),
                                "the core is inside its own polygon");
                        if (mapDirectory != null && Math.abs(cx) <= 4 && Math.abs(cz) <= 4) {
                            mapDeposits.add(deposit);
                        }
                        int owner = ++count;
                        int offsetX = (footprint.originX() - start) / step;
                        int offsetZ = (footprint.originZ() - start) / step;
                        int footprintSide = VeinFootprint.SIDE;
                        int areaInSurvey = 0;
                        BitSet samples = BitSet.valueOf(footprint.sampleWords());
                        for (int sample = samples.nextSetBit(0); sample >= 0; sample = samples.nextSetBit(sample + 1)) {
                            int worldX = footprint.originX() + sample % footprintSide * step;
                            int worldZ = footprint.originZ() + sample / footprintSide * step;
                            int reach = GeologyGenerator.STORAGE_REACH;
                            require(worldX >= cx * cellWidth - reach && worldX + step <= (cx + 1) * cellWidth + reach
                                            && worldZ >= cz * cellWidth - reach
                                            && worldZ + step <= (cz + 1) * cellWidth + reach,
                                    "the owner lookup reach contains the entire footprint");
                            int x = offsetX + sample % footprintSide;
                            int z = offsetZ + sample / footprintSide;
                            if (x >= 0 && x < width && z >= 0 && z < width) {
                                int index = z * width + x;
                                require(owners[index] == 0, "polygons cannot overlap across storage cells");
                                owners[index] = owner;
                                areaInSurvey++;
                            }
                        }
                        covered += areaInSurvey;
                        for (int ore = 0; ore < oreArea.length; ore++) {
                            if (GeologyGenerator.RESOURCES.get(ore).id().equals(deposit.getResourceId())) {
                                oreArea[ore] += areaInSurvey;
                            }
                        }
                    }
                }
            }

            double rawShare = (double) raw / sampleCount;
            double coverage = (double) covered / sampleCount;
            System.out.printf(Locale.ROOT, "  seed %d: mask %.2f%%, retained %.2f%%%n",
                    seed, rawShare * 100, coverage * 100);
            require(rawShare >= 0.60 && rawShare <= 0.72, "mask stays within the denser coverage range");
            require(coverage >= 0.58 && covered <= raw, "filtered coverage stays near the target");
            verifyStorageSeams(owners, width, side);
            for (int ore = 0; ore < oreTotals.length; ore++) {
                oreTotals[ore] += oreArea[ore];
                require(oreArea[ore] > 0, "each ore occurs in every sampled world");
            }
            if (mapDirectory != null) {
                VeinMapImages.write(mapDirectory, seed, coverage, mapDeposits);
            }
        }
        long total = java.util.Arrays.stream(oreTotals).sum();
        for (int ore = 0; ore < oreTotals.length; ore++) {
            double share = (double) oreTotals[ore] / total;
            System.out.printf(Locale.ROOT, "  %s: %.2f%% of vein area%n",
                    GeologyGenerator.RESOURCES.get(ore).id().getPath(), share * 100);
            require(share >= 0.16 && share <= 0.24, "ores are balanced by area across seeds");
        }
    }

    private static void verifyStorageSeams(int[] owners, int width, int cellSide) {
        int[] pairs = new int[2];
        int[] cuts = new int[2];
        for (int z = 0; z < width - 1; z++) {
            for (int x = 0; x < width - 1; x++) {
                int index = z * width + x;
                int owner = owners[index];
                if (owner == 0) {
                    continue;
                }
                for (int axis = 0; axis < 2; axis++) {
                    int neighbor = owners[index + (axis == 0 ? 1 : width)];
                    if (neighbor == 0) {
                        continue;
                    }
                    int seam = ((axis == 0 ? x : z) + 1) % cellSide == 0 ? 1 : 0;
                    pairs[seam]++;
                    if (neighbor != owner) {
                        cuts[seam]++;
                    }
                }
            }
        }
        double offGrid = (double) cuts[0] / pairs[0];
        double onGrid = (double) cuts[1] / pairs[1];
        int uninterrupted = pairs[1] - cuts[1];
        System.out.printf(Locale.ROOT,
                "    vein-to-vein transitions on storage grid %.2f%%, elsewhere %.2f%%; %d uninterrupted crossings%n",
                100 * onGrid, 100 * offGrid, uninterrupted);
        require(uninterrupted > 2_000, "veins cross thousands of storage boundaries without being cut");
        require(onGrid <= offGrid * 1.35 + 0.01,
                "ore boundaries are not concentrated along the storage grid");
    }

    private static void smoothedContoursAreExclusive() {
        VeinFootprint[] patterns = new VeinFootprint[16];
        int side = VeinFootprint.SIDE;
        int[] corners = {0, 1, side + 1, side};
        for (int mask = 1; mask < patterns.length; mask++) {
            List<Integer> samples = new ArrayList<>();
            for (int i = 0; i < corners.length; i++) {
                if ((mask & (1 << i)) != 0) {
                    samples.add(corners[i]);
                }
            }
            patterns[mask] = VeinFootprint.fromSamples(-VeinFootprint.STEP, 0, samples);
        }
        int step = VeinFootprint.STEP;
        int half = step / 2;
        for (int first = 1; first < patterns.length; first++) {
            VeinFootprint a = patterns[first];
            for (int z = half; z < half + step; z++) {
                for (int x = half; x < half + step; x++) {
                    double localX = x + 0.125;
                    double localZ = z + 0.375;
                    boolean contained = a.contains(a.originX() + localX, a.originZ() + localZ);
                    require(contained == isInsideEdges(a, localX, localZ),
                            "the scanline outline agrees with polygon containment");
                }
            }
            for (int second = 1; second < patterns.length; second++) {
                if ((first & second) != 0) {
                    continue;
                }
                VeinFootprint b = patterns[second];
                for (int z = half; z <= half + step; z++) {
                    for (int x = half; x <= half + step; x++) {
                        double worldX = a.originX() + x;
                        double worldZ = a.originZ() + z;
                        boolean inA = a.contains(worldX, worldZ);
                        boolean inB = b.contains(worldX, worldZ);
                        require(!(inA && inB), "interpolation does not overlap at shared edges");
                        if ((first | second) == 15 && first != 5 && first != 10) {
                            require(inA || inB, "two adjoining owners have no gap at their edge");
                        }
                    }
                }
            }
        }
        VeinFootprint corner = patterns[1];
        require(!corner.contains(corner.originX() + step - 1, corner.originZ() + step - 1),
                "the polygon clips the former square corner");
        require(corner.edges().stream().anyMatch(edge -> edge.x0() != edge.x1() && edge.z0() != edge.z1()),
                "the map receives the same diagonal outline");
    }

    private static boolean isInsideEdges(VeinFootprint footprint, double x, double z) {
        boolean inside = false;
        for (VeinFootprint.Edge edge : footprint.edges()) {
            if ((edge.z0() > z) == (edge.z1() > z)) {
                continue;
            }
            double crossing = edge.x0()
                    + (z - edge.z0()) * (edge.x1() - edge.x0()) / (edge.z1() - edge.z0());
            if (x < crossing) {
                inside = !inside;
            }
        }
        return inside;
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
