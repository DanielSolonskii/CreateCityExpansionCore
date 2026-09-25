package com.example.examplemod.geology;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

final class GeologyGenerator {

    static final int REGION_SIZE = 100;

    private static final String MOD_ID = "createcityexpansioncore";
    private static final double LARGE_SCALE = 100.0;
    private static final double MEDIUM_SCALE = 45.0;
    private static final double SMALL_SCALE = 20.0;
    private static final long MIN_DEPOSIT_RESERVE = 5_000_000L;
    private static final long MAX_DEPOSIT_RESERVE = 10_000_000L;

    private GeologyGenerator() {
    }

    static GeologicalRegion createRegion(
            ResourceLocation dimensionId,
            long worldSeed,
            int fieldX,
            int fieldZ
    ) {
        ResourceLocation generatedRegionId = regionId(
                dimensionId,
                fieldX,
                fieldZ
        );
        BlockPos samplePosition = new BlockPos(
                fieldX * REGION_SIZE + REGION_SIZE / 2,
                0,
                fieldZ * REGION_SIZE + REGION_SIZE / 2
        );
        List<GeologicalDeposit> deposits = new ArrayList<>();

        addGeneratedDeposit(
                deposits,
                dimensionId,
                fieldX,
                fieldZ,
                resource("copper"),
                calculateResource(worldSeed, samplePosition, 17L, 0.0),
                24,
                1.2
        );
        addGeneratedDeposit(
                deposits,
                dimensionId,
                fieldX,
                fieldZ,
                resource("iron"),
                calculateResource(worldSeed, samplePosition, 31L, 1.7),
                36,
                1.8
        );
        addGeneratedDeposit(
                deposits,
                dimensionId,
                fieldX,
                fieldZ,
                resource("coal"),
                calculateResource(worldSeed, samplePosition, 47L, 3.4),
                18,
                1.0
        );
        addGeneratedDeposit(
                deposits,
                dimensionId,
                fieldX,
                fieldZ,
                resource("zinc"),
                calculateResource(worldSeed, samplePosition, 73L, 5.1),
                48,
                2.1
        );

        return new GeologicalRegion(
                generatedRegionId,
                fieldX,
                fieldZ,
                deposits
        );
    }

    private static void addGeneratedDeposit(
            List<GeologicalDeposit> deposits,
            ResourceLocation dimensionId,
            int fieldX,
            int fieldZ,
            ResourceLocation resourceId,
            int richness,
            int depth,
            double hardness
    ) {
        if (richness <= 0) {
            return;
        }

        int depositIndex = 0;
        long initialReserve = reserveForRichness(richness);
        ResourceLocation generatedDepositId = depositId(
                dimensionId,
                fieldX,
                fieldZ,
                resourceId,
                depositIndex
        );

        deposits.add(
                new GeologicalDeposit(
                        generatedDepositId,
                        resourceId,
                        depositIndex,
                        initialReserve,
                        initialReserve,
                        richness / 100.0,
                        depth,
                        hardness,
                        richness * 250.0
                )
        );
    }

    static long reserveForGrade(double grade) {
        double clamped = Math.max(0.0, Math.min(1.0, grade));
        return MIN_DEPOSIT_RESERVE
                + Math.round((MAX_DEPOSIT_RESERVE - MIN_DEPOSIT_RESERVE) * clamped);
    }

    private static long reserveForRichness(int richness) {
        return MIN_DEPOSIT_RESERVE
                + (MAX_DEPOSIT_RESERVE - MIN_DEPOSIT_RESERVE) * richness / 100L;
    }

    static ResourceLocation regionId(
            ResourceLocation dimensionId,
            int fieldX,
            int fieldZ
    ) {
        return ResourceLocation.fromNamespaceAndPath(
                MOD_ID,
                "region/" + encode(dimensionId) + "/" + fieldX + "/" + fieldZ
        );
    }

    static ResourceLocation depositId(
            ResourceLocation dimensionId,
            int fieldX,
            int fieldZ,
            ResourceLocation resourceId,
            int depositIndex
    ) {
        if (depositIndex < 0) {
            throw new IllegalArgumentException("depositIndex cannot be negative");
        }

        return ResourceLocation.fromNamespaceAndPath(
                MOD_ID,
                "deposit/" + encode(dimensionId)
                        + "/" + fieldX
                        + "/" + fieldZ
                        + "/" + encode(resourceId)
                        + "/" + depositIndex
        );
    }

    private static String encode(ResourceLocation value) {
        return HexFormat.of().formatHex(
                value.toString().getBytes(StandardCharsets.UTF_8)
        );
    }

    private static int calculateResource(
            long worldSeed,
            BlockPos pos,
            long resourceSeed,
            double phase
    ) {
        long hash = mixSeed(worldSeed, resourceSeed);
        double offsetX = ((hash & 0xFFFFL) / 65535.0) * 1000.0;
        double offsetZ = (((hash >>> 16) & 0xFFFFL) / 65535.0) * 1000.0;
        double x = pos.getX() + offsetX;
        double z = pos.getZ() + offsetZ;

        double large =
                StrictMath.sin(x / LARGE_SCALE + phase)
                        * StrictMath.cos(z / LARGE_SCALE + phase * 0.7);
        double medium =
                StrictMath.sin((x + z * 0.7) / MEDIUM_SCALE + phase * 1.3);
        double small =
                StrictMath.sin((x - z * 0.45) / SMALL_SCALE + phase * 2.1);

        double geologicalPotential =
                large * 0.65 + medium * 0.25 + small * 0.10;
        double normalized = (geologicalPotential + 1.0) / 2.0;
        double value = StrictMath.pow(normalized, 1.35) * 100.0;

        if (geologicalPotential < -0.55) {
            value *= 0.05;
        }

        value = Math.max(0.0, Math.min(100.0, value));
        return (int) Math.round(value);
    }

    private static long mixSeed(long worldSeed, long resourceSeed) {
        long value = worldSeed ^ (resourceSeed * 0x9E3779B97F4A7C15L);
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static ResourceLocation resource(String name) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, name);
    }
}
