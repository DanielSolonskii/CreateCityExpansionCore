package com.example.examplemod.geology;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.math.BigInteger;

/**
 * Сериализация геологии.
 *
 * Текущий формат — 3: добавлены геометрия, класс качества,
 * класс размера и пиковый потенциал месторождения.
 *
 * Форматы 1 и 2 читаются без потерь: legacy-залежи сохраняют
 * идентификатор, ресурс, grade, initialReserve и
 * remainingReserve, а отсутствующую пространственную форму
 * получают детерминированной совместимостью. Запасы при
 * миграции не пересчитываются, если только старый формат 1
 * явно требовал этого (там размер запаса был производным).
 */
final class GeologySavedDataCodec {

    static final int CURRENT_FORMAT_VERSION = 4;
    private static final int LEGACY_V2 = 2;
    private static final int LEGACY_V1 = 1;

    /** Размер старого геологического поля 100x100 в блоках. */
    private static final int LEGACY_FIELD_SIZE = 100;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Pattern LEGACY_REGION_ID =
            Pattern.compile("^field_(-?\\d+)_(-?\\d+)$");

    private GeologySavedDataCodec() {
    }

    static LoadResult load(
            CompoundTag tag,
            ResourceLocation dimensionId
    ) {
        try {
            if (!tag.contains("FormatVersion")) {
                /*
                 * Версии без формата тоже пересоздаются: это форматы 1-2 в
                 * disguise, а в них залежи лежат друг на друге ровно так же.
                 * Отдельная миграция для них больше не нужна.
                 */
                if (tag.isEmpty()) {
                    return LoadResult.available(List.of(), false);
                }
                LOGGER.info(
                        "Regenerating geology for dimension {}: unversioned data "
                                + "precedes the per-resource seeding fix",
                        dimensionId
                );
                return LoadResult.available(List.of(), true);
            }

            requireType(tag, "FormatVersion", Tag.TAG_INT);
            int formatVersion = tag.getInt("FormatVersion");
            if (formatVersion > CURRENT_FORMAT_VERSION) {
                throw new GeologyDataException(
                        "Unsupported geology format version " + formatVersion
                );
            }

            /*
             * Формат 4 пересоздаёт геологию целиком.
             *
             * <p>В форматах 1-3 идентичность ресурса не входила в seed, поэтому
             * все пять ресурсов одной macro-cell получали одно положение, форму,
             * качество и запас. Уголь лежал ровно на железе, и сканер показывал
             * только железо — оно первое в списке ресурсов, а потенциалы совпадали
             * до бита. Сохранённые залежи починить нельзя: они физически лежат
             * друг на друге, и «развести» их, не сдвинув ни одну, невозможно.
             *
             * <p>Поэтому старые записи отбрасываются, а залежи строятся заново по
             * требованию. Запас и выработка на старых местах теряются — это
             * осознанная цена: править геологию по частям означало бы оставить
             * рядом старый и новый миры одновременно.
             *
             * <p>Измерение проверяется и здесь: файл от другого измерения — это
             * не «старый формат», а ошибка, и молча пересоздавать геологию
             * Nether в Overworld было бы хуже, чем отказать.
             */
            if (formatVersion < CURRENT_FORMAT_VERSION) {
                ResourceLocation previousDimension = readResourceLocation(
                        tag,
                        "Dimension"
                );
                if (!previousDimension.equals(dimensionId)) {
                    throw new GeologyDataException(
                            "Stored dimension " + previousDimension
                                    + " does not match " + dimensionId
                    );
                }
                LOGGER.info(
                        "Regenerating geology for dimension {}: stored format {} "
                                + "placed every resource of a cell at one point; "
                                + "deposits are rebuilt as {} cells are visited",
                        dimensionId,
                        formatVersion,
                        CURRENT_FORMAT_VERSION
                );
                return LoadResult.available(List.of(), true);
            }

            ResourceLocation storedDimension = readResourceLocation(
                    tag,
                    "Dimension"
            );
            if (!storedDimension.equals(dimensionId)) {
                throw new GeologyDataException(
                        "Stored dimension " + storedDimension
                                + " does not match " + dimensionId
                );
            }

            boolean legacyFormat = formatVersion != CURRENT_FORMAT_VERSION;
            boolean migrateReserves = formatVersion == LEGACY_V1;

            List<LoadedCell> cells = readRegions(
                    tag,
                    dimensionId,
                    legacyFormat,
                    migrateReserves
            );

            if (legacyFormat) {
                LOGGER.info(
                        "Migrated geology data from format {} to {} for dimension {}; "
                                + "legacy deposits are preserved with their ids, grades "
                                + "and remaining reserves",
                        formatVersion,
                        CURRENT_FORMAT_VERSION,
                        dimensionId
                );
            }

            return LoadResult.available(cells, legacyFormat || migrateReserves);
        } catch (GeologyDataException | RuntimeException exception) {
            LOGGER.error(
                    "Geology data for dimension {} is invalid; entering read-only "
                            + "fallback without generating replacement deposits: {}",
                    dimensionId,
                    exception.getMessage()
            );
            return LoadResult.unavailable(tag.copy());
        }
    }

    static CompoundTag save(
            CompoundTag tag,
            ResourceLocation dimensionId,
            Iterable<GeologicalRegion> regions
    ) {
        tag.putInt("FormatVersion", CURRENT_FORMAT_VERSION);
        tag.putInt("GeneratorVersion", GeologyGenerator.GENERATOR_VERSION);
        tag.putString("Dimension", dimensionId.toString());
        ListTag regionList = new ListTag();

        for (GeologicalRegion region : regions) {
            CompoundTag regionTag = new CompoundTag();
            regionTag.putString("Id", region.getId().toString());
            regionTag.putInt("RegionX", region.getRegionX());
            regionTag.putInt("RegionZ", region.getRegionZ());

            ListTag depositList = new ListTag();
            for (GeologicalDeposit deposit : region.getDeposits()) {
                CompoundTag depositTag = new CompoundTag();
                depositTag.putString("Id", deposit.getId().toString());
                depositTag.putString(
                        "Resource",
                        deposit.getResourceId().toString()
                );
                depositTag.putInt("DepositIndex", deposit.getDepositIndex());
                depositTag.putLong("InitialReserve", deposit.getInitialReserve());
                depositTag.putLong("RemainingReserve", deposit.getRemainingReserve());
                depositTag.putDouble("Grade", deposit.getGrade());
                depositTag.putInt("Depth", deposit.getDepth());
                depositTag.putDouble("Hardness", deposit.getHardness());
                depositTag.putDouble(
                        "SustainableExtractionRate",
                        deposit.getSustainableExtractionRate()
                );
                depositTag.putString(
                        "QualityClass",
                        deposit.getQualityClass().name()
                );
                depositTag.putString(
                        "SizeClass",
                        deposit.getSizeClass().name()
                );
                depositTag.putDouble(
                        "PeakPotentialPerHour",
                        deposit.getPeakPotentialPerHour()
                );

                DepositShape shape = deposit.getShape();
                depositTag.putInt("CenterX", shape.centerX());
                depositTag.putInt("CenterZ", shape.centerZ());
                depositTag.putInt("RadiusX", shape.radiusX());
                depositTag.putInt("RadiusZ", shape.radiusZ());
                depositTag.putDouble("Rotation", shape.rotationRadians());
                depositTag.putDouble("CoreRadius", shape.coreRadius());
                depositTag.putLong("ShapeSeed", shape.shapeSeed());

                depositList.add(depositTag);
            }

            regionTag.put("Deposits", depositList);
            regionList.add(regionTag);
        }

        tag.put("Regions", regionList);
        return tag;
    }

    private static List<LoadedCell> readRegions(
            CompoundTag tag,
            ResourceLocation dimensionId,
            boolean legacyFormat,
            boolean migrateReserves
    ) throws GeologyDataException {
        requireType(tag, "Regions", Tag.TAG_LIST);
        ListTag regionList = (ListTag) tag.get("Regions");
        List<LoadedCell> cells = new ArrayList<>();
        Map<ResourceLocation, Boolean> regionIds = new HashMap<>();
        Map<ResourceLocation, Boolean> depositIds = new HashMap<>();

        for (Tag rawRegionTag : regionList) {
            if (!(rawRegionTag instanceof CompoundTag regionTag)) {
                throw new GeologyDataException(
                        "Regions list contains a non-compound element"
                );
            }

            RegionCoordinates coordinates = legacyFormat
                    ? readLegacyRegionCoordinates(regionTag)
                    : readRegionCoordinates(regionTag);

            ResourceLocation regionId = readResourceLocation(regionTag, "Id");
            if (regionIds.put(regionId, Boolean.TRUE) != null) {
                throw new GeologyDataException(
                        "Duplicate region ID: " + regionId
                );
            }

            List<GeologicalDeposit> deposits = readDeposits(
                    regionTag,
                    legacyFormat,
                    migrateReserves,
                    depositIds,
                    legacyFormat
                            ? coordinates.x() * LEGACY_FIELD_SIZE + LEGACY_FIELD_SIZE / 2
                            : 0,
                    legacyFormat
                            ? coordinates.z() * LEGACY_FIELD_SIZE + LEGACY_FIELD_SIZE / 2
                            : 0
            );

            /*
             * Владелец записи — macro-cell, в которой физически
             * лежит центр залежи.
             *
             * В формате 3 координаты региона уже являются
             * координатами macro-cell, а центры залежей по
             * построению лежат внутри своей ячейки.
             *
             * В legacy-форматах регион — это поле 100x100,
             * поэтому ячейка вычисляется от центра поля.
             */
            int cellX = legacyFormat
                    ? GeologyGenerator.cellXOf(
                            coordinates.x() * LEGACY_FIELD_SIZE + LEGACY_FIELD_SIZE / 2
                    )
                    : coordinates.x();
            int cellZ = legacyFormat
                    ? GeologyGenerator.cellZOf(
                            coordinates.z() * LEGACY_FIELD_SIZE + LEGACY_FIELD_SIZE / 2
                    )
                    : coordinates.z();

            cells.add(new LoadedCell(
                    cellX,
                    cellZ,
                    new GeologicalRegion(regionId, coordinates.x(), coordinates.z(), deposits)
            ));
        }

        return cells;
    }

    private static RegionCoordinates readLegacyRegionCoordinates(
            CompoundTag regionTag
    ) throws GeologyDataException {
        ResourceLocation legacyId = readResourceLocation(regionTag, "Id");
        Matcher matcher = LEGACY_REGION_ID.matcher(legacyId.getPath());
        if (!matcher.matches()) {
            throw new GeologyDataException(
                    "Cannot migrate legacy region ID: " + legacyId
            );
        }

        try {
            return new RegionCoordinates(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2))
            );
        } catch (NumberFormatException exception) {
            throw new GeologyDataException(
                    "Legacy region coordinates are outside the integer range: "
                            + legacyId
            );
        }
    }

    private static RegionCoordinates readRegionCoordinates(
            CompoundTag regionTag
    ) throws GeologyDataException {
        requireType(regionTag, "RegionX", Tag.TAG_INT);
        requireType(regionTag, "RegionZ", Tag.TAG_INT);
        return new RegionCoordinates(
                regionTag.getInt("RegionX"),
                regionTag.getInt("RegionZ")
        );
    }

    private static List<GeologicalDeposit> readDeposits(
            CompoundTag regionTag,
            boolean legacyFormat,
            boolean migrateReserves,
            Map<ResourceLocation, Boolean> allDepositIds,
            int legacyCenterX,
            int legacyCenterZ
    ) throws GeologyDataException {
        requireType(regionTag, "Deposits", Tag.TAG_LIST);
        ListTag depositList = (ListTag) regionTag.get("Deposits");
        List<GeologicalDeposit> deposits = new ArrayList<>();
        Map<ResourceLocation, Integer> legacyIndices = new HashMap<>();

        for (Tag rawDepositTag : depositList) {
            if (!(rawDepositTag instanceof CompoundTag depositTag)) {
                throw new GeologyDataException(
                        "Deposits list contains a non-compound element"
                );
            }

            ResourceLocation resourceId = readResourceLocation(
                    depositTag,
                    "Resource"
            );

            /*
             * Legacy-идентификаторы принимаются как есть: это
             * единственный способ сохранить уже выданные reserve
             * и привязку существующих шахт. Они не пересчитываются
             * и не сопоставляются с новой схемой - иначе старые
             * запасы были бы потеряны или задвоены.
             */
            ResourceLocation depositId = readResourceLocation(depositTag, "Id");
            if (allDepositIds.put(depositId, Boolean.TRUE) != null) {
                throw new GeologyDataException(
                        "Duplicate deposit ID: " + depositId
                );
            }

            int depositIndex = legacyFormat
                    ? legacyIndices.getOrDefault(resourceId, 0)
                    : requireInt(depositTag, "DepositIndex");
            if (legacyFormat) {
                legacyIndices.put(resourceId, depositIndex + 1);
            }

            GeologicalDeposit deposit = readDeposit(
                    depositTag,
                    depositId,
                    resourceId,
                    depositIndex,
                    migrateReserves,
                    legacyCenterX,
                    legacyCenterZ
            );
            if (deposit != null) {
                deposits.add(deposit);
            }
        }

        return deposits;
    }

    private static GeologicalDeposit readDeposit(
            CompoundTag tag,
            ResourceLocation id,
            ResourceLocation resourceId,
            int depositIndex,
            boolean migrateReserves,
            int legacyCenterX,
            int legacyCenterZ
    ) throws GeologyDataException {
        requireType(tag, "InitialReserve", Tag.TAG_LONG);
        requireType(tag, "RemainingReserve", Tag.TAG_LONG);
        requireType(tag, "Grade", Tag.TAG_DOUBLE);
        requireType(tag, "Depth", Tag.TAG_INT);
        requireType(tag, "Hardness", Tag.TAG_DOUBLE);
        requireType(tag, "SustainableExtractionRate", Tag.TAG_DOUBLE);

        long initialReserve = tag.getLong("InitialReserve");
        long remainingReserve = tag.getLong("RemainingReserve");
        double grade = tag.getDouble("Grade");
        int depth = tag.getInt("Depth");
        double hardness = tag.getDouble("Hardness");
        double sustainableExtractionRate = tag.getDouble(
                "SustainableExtractionRate"
        );

        /*
         * Пиковый потенциал и классы качества и размера берутся
         * из записи формата 3. Если их нет (форматы 1 и 2),
         * потенциал восстанавливается из сохранённого
         * sustainableExtractionRate: в legacy-форматах это поле
         * уже нело смысл производительности залежи.
         */
        boolean modern = tag.contains("PeakPotentialPerHour", Tag.TAG_DOUBLE);
        double peakPerHour = Math.max(
                1.0,
                Math.min(
                        DepositQualityClass.PEAK_POTENTIAL_CEILING,
                        modern
                                ? tag.getDouble("PeakPotentialPerHour")
                                : sustainableExtractionRate
                )
        );
        DepositQualityClass qualityClass = modern
                ? readEnum(
                        tag,
                        "QualityClass",
                        DepositQualityClass.class,
                        DepositQualityClass.fromPeakPerHour(peakPerHour)
                )
                : DepositQualityClass.fromPeakPerHour(peakPerHour);
        DepositSizeClass sizeClass = modern
                ? readEnum(
                        tag,
                        "SizeClass",
                        DepositSizeClass.class,
                        DepositSizeClass.fromLegacy()
                )
                : DepositSizeClass.fromLegacy();

        DepositShape shape = readShape(
                tag,
                id,
                resourceId,
                peakPerHour,
                legacyCenterX,
                legacyCenterZ
        );

        if (migrateReserves) {
            if (initialReserve <= 0L || remainingReserve < 0L
                    || remainingReserve > initialReserve) {
                throw new GeologyDataException(
                        "Cannot migrate invalid reserve values for " + id
                );
            }
            long migratedInitial = GeologyGenerator.reserveForGrade(grade);
            long migratedRemaining = BigInteger.valueOf(remainingReserve)
                    .multiply(BigInteger.valueOf(migratedInitial))
                    .divide(BigInteger.valueOf(initialReserve))
                    .longValueExact();
            if (remainingReserve > 0L && migratedRemaining == 0L) {
                migratedRemaining = 1L;
            }
            initialReserve = migratedInitial;
            remainingReserve = Math.min(migratedRemaining, migratedInitial);
        }

        try {
            return new GeologicalDeposit(
                    id,
                    resourceId,
                    depositIndex,
                    initialReserve,
                    remainingReserve,
                    grade,
                    depth,
                    hardness,
                    peakPerHour,
                    shape,
                    peakPerHour,
                    qualityClass,
                    sizeClass
            );
        } catch (IllegalArgumentException exception) {
            throw new GeologyDataException(
                    "Invalid deposit " + id + ": " + exception.getMessage()
            );
        }
    }

    private static DepositShape readShape(
            CompoundTag tag,
            ResourceLocation id,
            ResourceLocation resourceId,
            double peakPerHour,
            int legacyCenterX,
            int legacyCenterZ
    ) {
        if (!tag.contains("CenterX", Tag.TAG_INT)
                || !tag.contains("ShapeSeed", Tag.TAG_LONG)) {
            /*
             * Формата 3 в записи нет: это legacy-залежь из
             * формата 1/2, сохранённая без пространственной формы.
             * Геометрия выводится детерминированно из её
             * идентичности и положения старого поля, поэтому
             * после рестарта она та же самая и не «прыгает».
             */
            return legacyShape(id, resourceId, peakPerHour, legacyCenterX, legacyCenterZ);
        }

        try {
            return new DepositShape(
                    tag.getInt("CenterX"),
                    tag.getInt("CenterZ"),
                    Math.max(1, tag.getInt("RadiusX")),
                    Math.max(1, tag.getInt("RadiusZ")),
                    tag.getDouble("Rotation"),
                    clamp(
                            tag.getDouble("CoreRadius"),
                            DepositShape.MIN_CORE_FRACTION,
                            DepositShape.MAX_CORE_FRACTION
                    ),
                    tag.getLong("ShapeSeed")
            );
        } catch (IllegalArgumentException exception) {
            LOGGER.warn(
                    "Deposit {} has unusable geometry; falling back to a "
                            + "deterministic legacy shape",
                    id,
                    exception
            );
            return legacyShape(id, resourceId, peakPerHour, legacyCenterX, legacyCenterZ);
        }
    }

    /**
     * Совместимая форма для legacy-залежей: круговая залежь
     * на месте старого поля 100x100.
     *
     * Радиус выводится из пикового потенциала: богатые старые
     * залежи получают больший физический объём, бедные —
     * меньший. Это только совместимость формы: запасы, grade
     * и идентичность не меняются.
     */
    private static DepositShape legacyShape(
            ResourceLocation id,
            ResourceLocation resourceId,
            double peakPerHour,
            int centerX,
            int centerZ
    ) {
        DepositSizeClass sizeClass = DepositSizeClass.fromLegacy();
        long seed = GeologyNoise.mix(
                GeologyNoise.mix(
                        id.toString().hashCode(),
                        resourceId.toString().hashCode()
                ),
                Double.doubleToLongBits(peakPerHour)
        );

        double potentialRatio = Math.max(
                0.0,
                Math.min(
                        1.0,
                        peakPerHour / DepositQualityClass.PEAK_POTENTIAL_CEILING
                )
        );
        int radius = (int) Math.round(
                DepositSizeClass.MIN_RADIUS
                        + potentialRatio * (sizeClass.maxRadius()
                        - DepositSizeClass.MIN_RADIUS)
        );
        return new DepositShape(
                centerX,
                centerZ,
                radius,
                radius,
                GeologyNoise.unit01(seed) * Math.PI,
                0.35,
                GeologyNoise.mix(seed, 0x5DEECE66DL)
        );
    }

    private static int requireInt(CompoundTag tag, String key) throws GeologyDataException {
        requireType(tag, key, Tag.TAG_INT);
        return tag.getInt(key);
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Enum-поле NBT с безопасным fallback.
     * Неизвестное значение из будущего формата не должно
     * ломать загрузку: используется значение по умолчанию.
     */
    private static <T extends Enum<T>> T readEnum(
            CompoundTag tag,
            String key,
            Class<T> type,
            T fallback
    ) {
        if (!tag.contains(key, Tag.TAG_STRING)) {
            return fallback;
        }
        String name = tag.getString(key);
        for (T value : type.getEnumConstants()) {
            if (value.name().equals(name)) {
                return value;
            }
        }
        LOGGER.warn(
                "Unknown {} value '{}' in geology data; falling back to {}",
                type.getSimpleName(),
                name,
                fallback
        );
        return fallback;
    }

    private static ResourceLocation readResourceLocation(
            CompoundTag tag,
            String key
    ) throws GeologyDataException {
        requireType(tag, key, Tag.TAG_STRING);
        ResourceLocation value = ResourceLocation.tryParse(tag.getString(key));
        if (value == null) {
            throw new GeologyDataException(
                    "Invalid ResourceLocation in field " + key
            );
        }
        return value;
    }

    private static void requireType(
            CompoundTag tag,
            String key,
            int expectedType
    ) throws GeologyDataException {
        if (!tag.contains(key, expectedType)) {
            throw new GeologyDataException(
                    "Missing field or wrong NBT type: " + key
            );
        }
    }

    record LoadResult(
            List<LoadedCell> cells,
            boolean storageAvailable,
            boolean migrated,
            CompoundTag preservedFallbackData
    ) {
        private static LoadResult available(
                List<LoadedCell> cells,
                boolean migrated
        ) {
            return new LoadResult(List.copyOf(cells), true, migrated, null);
        }

        private static LoadResult unavailable(CompoundTag preservedData) {
            return new LoadResult(List.of(), false, false, preservedData);
        }
    }

    record LoadedCell(int cellX, int cellZ, GeologicalRegion region) {
    }

    private record RegionCoordinates(int x, int z) {
    }

    private static final class GeologyDataException extends Exception {
        private GeologyDataException(String message) {
            super(message);
        }
    }
}