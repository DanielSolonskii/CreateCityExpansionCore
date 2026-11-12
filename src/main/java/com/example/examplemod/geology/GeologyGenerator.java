package com.example.examplemod.geology;

import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Детерминированная геология по сиду мира и измерению.
 * Каждая ячейка хранит свои залежи, включая части в приграничном запасе.
 */
final class GeologyGenerator {

    /** Входит в ID залежи и меняется при смене алгоритма раскладки. */
    static final int GENERATOR_VERSION = 4;

    static final int MACRO_CELL_SIZE = 768;

    static final int STORAGE_REACH = Math.max(VeinLayout.STORAGE_REACH, DepositSizeClass.MAX_RADIUS);
    static final int LOOKUP_RING = Math.ceilDiv(STORAGE_REACH, MACRO_CELL_SIZE);

    /** Search all owner cells that may intersect the requested scan area. */
    static int nearbyLookupRing(int scanRadiusBlocks) {
        if (scanRadiusBlocks < 0) {
            throw new IllegalArgumentException("scanRadiusBlocks cannot be negative");
        }

        long reach = (long) scanRadiusBlocks + STORAGE_REACH;

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

    private static final long SALT_LAYOUT = 0x3000L;
    private static final long SALT_ORE = 0x3010L;
    private static final long SALT_SHAPE = 0x1040L;
    private static final long SALT_CORE = 0x1080L;
    private static final long SALT_QUALITY = 0x1090L;
    private static final long SALT_PEAK = 0x10A0L;

    record ResourceProfile(ResourceLocation id, int depth, double hardness) {}

    static final List<ResourceProfile> RESOURCES = List.of(
            new ResourceProfile(resource("iron"), 36, 1.8),
            new ResourceProfile(resource("coal"), 18, 1.0),
            new ResourceProfile(resource("limestone"), 24, 1.4),
            new ResourceProfile(resource("copper"), 30, 1.5),
            new ResourceProfile(resource("zinc"), 42, 2.0)
    );

    private GeologyGenerator() {}

    static List<GeologicalDeposit> createDeposits(
            ResourceLocation dimensionId, long worldSeed, int cellX, int cellZ
    ) {
        long cellSeed = cellSeed(worldSeed, dimensionId, cellX, cellZ);
        long layoutSeed = GeologyNoise.mix(worldSeed, SALT_LAYOUT, dimensionId.toString().hashCode());
        List<VeinLayout.Patch> patches = new ArrayList<>(VeinLayout.generate(layoutSeed, cellX, cellZ));
        patches.sort(Comparator.comparingInt((VeinLayout.Patch patch) -> patch.footprint().area())
                .reversed());
        SplittableRandom oreRandom = new SplittableRandom(GeologyNoise.mix(cellSeed, SALT_ORE));
        int[] assignedArea = new int[RESOURCES.size()];
        int[] nextIndex = new int[RESOURCES.size()];
        Map<Integer, Integer> componentOres = new HashMap<>();
        List<GeologicalDeposit> deposits = new ArrayList<>();
        for (VeinLayout.Patch patch : patches) {
            int used = componentOres.getOrDefault(patch.component(), 0);
            int offset = oreRandom.nextInt(RESOURCES.size());
            int ore = offset;
            int leastArea = Integer.MAX_VALUE;
            for (int i = 0; i < RESOURCES.size(); i++) {
                int candidate = (offset + i) % RESOURCES.size();
                if ((used & (1 << candidate)) == 0 && assignedArea[candidate] < leastArea) {
                    leastArea = assignedArea[candidate];
                    ore = candidate;
                }
            }
            componentOres.put(patch.component(), used | (1 << ore));
            assignedArea[ore] += patch.footprint().area();
            long seed = GeologyNoise.mix(cellSeed, SALT_SHAPE, deposits.size());
            deposits.add(createDeposit(dimensionId, RESOURCES.get(ore), cellX, cellZ,
                    nextIndex[ore]++, seed, patch.footprint()));
        }
        return deposits;
    }

    private static GeologicalDeposit createDeposit(
            ResourceLocation dimensionId, ResourceProfile profile, int cellX, int cellZ,
            int index, long candidateSeed, VeinFootprint footprint
    ) {
        int sample = footprint.coreSample();
        int step = VeinFootprint.STEP;
        int centerX = footprint.originX() + sample % VeinFootprint.SIDE * step + step / 2;
        int centerZ = footprint.originZ() + sample / VeinFootprint.SIDE * step + step / 2;
        int radius = (int) Math.round(step * Math.sqrt(footprint.area() / Math.PI));
        DepositSizeClass sizeClass = DepositSizeClass.SMALL;
        for (DepositSizeClass candidate : DepositSizeClass.values()) {
            if (radius >= candidate.minRadius()) {
                sizeClass = candidate;
            }
        }
        double coreDiameter = GeologyNoise.range(
                GeologyNoise.mix(candidateSeed, SALT_CORE),
                DepositShape.MIN_CORE_DIAMETER,
                DepositShape.MAX_CORE_DIAMETER
        );
        double coreFraction = Math.clamp(coreDiameter / (2.0 * radius),
                DepositShape.MIN_CORE_FRACTION, DepositShape.MAX_CORE_FRACTION);
        DepositShape shape = new DepositShape(
                centerX, centerZ, radius, radius, 0, coreFraction, candidateSeed, footprint
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

        return new GeologicalDeposit(
                depositId(dimensionId, cellX, cellZ, profile.id(), index),
                profile.id(),
                index,
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

    private static String encode(ResourceLocation value) {
        return HexFormat.of().formatHex(
                value.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    private static ResourceLocation resource(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }
}
