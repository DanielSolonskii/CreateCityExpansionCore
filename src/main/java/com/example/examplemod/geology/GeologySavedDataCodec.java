package com.example.examplemod.geology;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.math.BigInteger;

final class GeologySavedDataCodec {

    static final int CURRENT_FORMAT_VERSION = 2;
    private static final int PREVIOUS_FORMAT_VERSION = 1;

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
                if (!tag.contains("Regions")) {
                    if (tag.isEmpty()) {
                        return LoadResult.available(Map.of(), false);
                    }
                    throw new GeologyDataException(
                            "Unversioned geology data has no migratable Regions list"
                    );
                }

                Map<ResourceLocation, GeologicalRegion> migrated = readRegions(
                        tag,
                        dimensionId,
                        true,
                        true
                );
                LOGGER.info(
                        "Migrated unversioned geology data to format {} for dimension {}",
                        CURRENT_FORMAT_VERSION,
                        dimensionId
                );
                return LoadResult.available(migrated, true);
            }

            requireType(tag, "FormatVersion", Tag.TAG_INT);
            int formatVersion = tag.getInt("FormatVersion");
            if (formatVersion != CURRENT_FORMAT_VERSION
                    && formatVersion != PREVIOUS_FORMAT_VERSION) {
                throw new GeologyDataException(
                        "Unsupported geology format version " + formatVersion
                );
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

            boolean migrateReserves = formatVersion == PREVIOUS_FORMAT_VERSION;
            Map<ResourceLocation, GeologicalRegion> regions = readRegions(
                    tag,
                    dimensionId,
                    false,
                    migrateReserves
            );
            if (migrateReserves) {
                LOGGER.info(
                        "Migrated geology reserves from format {} to {} for dimension {}",
                        PREVIOUS_FORMAT_VERSION,
                        CURRENT_FORMAT_VERSION,
                        dimensionId
                );
            }
            return LoadResult.available(regions, migrateReserves);
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
                depositTag.putInt(
                        "DepositIndex",
                        deposit.getDepositIndex()
                );
                depositTag.putLong(
                        "InitialReserve",
                        deposit.getInitialReserve()
                );
                depositTag.putLong(
                        "RemainingReserve",
                        deposit.getRemainingReserve()
                );
                depositTag.putDouble("Grade", deposit.getGrade());
                depositTag.putInt("Depth", deposit.getDepth());
                depositTag.putDouble("Hardness", deposit.getHardness());
                depositTag.putDouble(
                        "SustainableExtractionRate",
                        deposit.getSustainableExtractionRate()
                );
                depositList.add(depositTag);
            }

            regionTag.put("Deposits", depositList);
            regionList.add(regionTag);
        }

        tag.put("Regions", regionList);
        return tag;
    }

    private static Map<ResourceLocation, GeologicalRegion> readRegions(
            CompoundTag tag,
            ResourceLocation dimensionId,
            boolean legacy,
            boolean migrateReserves
    ) throws GeologyDataException {
        requireType(tag, "Regions", Tag.TAG_LIST);
        ListTag regionList = (ListTag) tag.get("Regions");
        Map<ResourceLocation, GeologicalRegion> regions = new LinkedHashMap<>();
        Set<ResourceLocation> depositIds = new HashSet<>();

        for (Tag rawRegionTag : regionList) {
            if (!(rawRegionTag instanceof CompoundTag regionTag)) {
                throw new GeologyDataException(
                        "Regions list contains a non-compound element"
                );
            }

            RegionCoordinates coordinates = legacy
                    ? readLegacyRegionCoordinates(regionTag)
                    : readRegionCoordinates(regionTag);
            ResourceLocation expectedRegionId = GeologyGenerator.regionId(
                    dimensionId,
                    coordinates.x(),
                    coordinates.z()
            );

            if (!legacy) {
                ResourceLocation storedRegionId = readResourceLocation(
                        regionTag,
                        "Id"
                );
                if (!storedRegionId.equals(expectedRegionId)) {
                    throw new GeologyDataException(
                            "Region ID does not match its dimension and coordinates: "
                                    + storedRegionId
                    );
                }
            }

            if (regions.containsKey(expectedRegionId)) {
                throw new GeologyDataException(
                        "Duplicate region ID: " + expectedRegionId
                );
            }

            List<GeologicalDeposit> deposits = readDeposits(
                    regionTag,
                    dimensionId,
                    coordinates,
                    legacy,
                    migrateReserves,
                    depositIds
            );
            regions.put(
                    expectedRegionId,
                    new GeologicalRegion(
                            expectedRegionId,
                            coordinates.x(),
                            coordinates.z(),
                            deposits
                    )
            );
        }

        return regions;
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
            ResourceLocation dimensionId,
            RegionCoordinates coordinates,
            boolean legacy,
            boolean migrateReserves,
            Set<ResourceLocation> allDepositIds
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
            int depositIndex;

            if (legacy) {
                depositIndex = legacyIndices.getOrDefault(resourceId, 0);
                if (depositIndex == Integer.MAX_VALUE) {
                    throw new GeologyDataException(
                            "Too many legacy deposits for resource " + resourceId
                    );
                }
                legacyIndices.put(resourceId, depositIndex + 1);
            } else {
                requireType(depositTag, "DepositIndex", Tag.TAG_INT);
                depositIndex = depositTag.getInt("DepositIndex");
            }

            ResourceLocation expectedDepositId = GeologyGenerator.depositId(
                    dimensionId,
                    coordinates.x(),
                    coordinates.z(),
                    resourceId,
                    depositIndex
            );

            if (legacy) {
                readResourceLocation(depositTag, "Id");
            } else {
                ResourceLocation storedDepositId = readResourceLocation(
                        depositTag,
                        "Id"
                );
                if (!storedDepositId.equals(expectedDepositId)) {
                    throw new GeologyDataException(
                            "Deposit ID does not match its identity fields: "
                                    + storedDepositId
                    );
                }
            }

            GeologicalDeposit deposit = readDeposit(
                    depositTag,
                    expectedDepositId,
                    resourceId,
                    depositIndex,
                    legacy,
                    migrateReserves
            );
            if (deposit == null) {
                continue;
            }
            if (!allDepositIds.add(deposit.getId())) {
                throw new GeologyDataException(
                        "Duplicate deposit ID: " + deposit.getId()
                );
            }
            deposits.add(deposit);
        }

        return deposits;
    }

    private static GeologicalDeposit readDeposit(
            CompoundTag tag,
            ResourceLocation id,
            ResourceLocation resourceId,
            int depositIndex,
            boolean legacy,
            boolean migrateReserves
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

        if (legacy
                && initialReserve == 1L
                && remainingReserve >= 0L
                && remainingReserve <= 1L
                && Double.compare(grade, 0.0) == 0
                && depth >= 0
                && Double.isFinite(hardness)
                && hardness > 0.0
                && Double.compare(sustainableExtractionRate, 0.0) == 0) {
            return null;
        }

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
                    sustainableExtractionRate
            );
        } catch (IllegalArgumentException exception) {
            throw new GeologyDataException(
                    "Invalid deposit " + id + ": " + exception.getMessage()
            );
        }
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
            Map<ResourceLocation, GeologicalRegion> regions,
            boolean storageAvailable,
            boolean migrated,
            CompoundTag preservedFallbackData
    ) {
        private static LoadResult available(
                Map<ResourceLocation, GeologicalRegion> regions,
                boolean migrated
        ) {
            return new LoadResult(regions, true, migrated, null);
        }

        private static LoadResult unavailable(CompoundTag preservedData) {
            return new LoadResult(Map.of(), false, false, preservedData);
        }
    }

    private record RegionCoordinates(int x, int z) {
    }

    private static final class GeologyDataException extends Exception {
        private GeologyDataException(String message) {
            super(message);
        }
    }
}
