package com.example.examplemod.mine;

import com.example.examplemod.geology.GeologicalDeposit;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;

import java.util.Objects;

/**
 * Converts a tier's share of the local geological potential into batches for the gateway.
 */
public record MineProductionProfile(
        double localPotentialPerHour,
        double richness,
        long outputPerCycle,
        double exactCycleTicks,
        double itemsPerMinute
) {

    public static final long BASE_OUTPUT_PER_CYCLE = 5L;
    private static final double TICKS_PER_HOUR = 72_000.0;

    /** Потолок шкалы локального потенциала, единиц/ч. */
    public static final double PEAK_POTENTIAL_PER_HOUR = 10_000.0;

    public static MineProductionProfile from(
            GeologicalDeposit deposit,
            int mineX,
            int mineZ
    ) {
        return from(deposit, mineX, mineZ, Tier.I);
    }

    static MineProductionProfile from(GeologicalDeposit deposit, int mineX, int mineZ, Tier tier) {
        Objects.requireNonNull(deposit, "deposit");
        return of(deposit, deposit.localPotentialPerHour(mineX, mineZ), tier);
    }

    /**
     * Совместимый путь для мест, где позиция шахты ещё не
     * известна: используется пиковый потенциал залежи.
     */
    public static MineProductionProfile from(GeologicalDeposit deposit) {
        Objects.requireNonNull(deposit, "deposit");
        return of(deposit, deposit.getPeakPotentialPerHour(), Tier.I);
    }

    private static MineProductionProfile of(
            GeologicalDeposit deposit,
            double localPotentialPerHour,
            Tier tier
    ) {
        double unitsPerHour = localPotentialPerHour * tier.outputPercent() / 100.0;
        double cycleTicks = TICKS_PER_HOUR * BASE_OUTPUT_PER_CYCLE / unitsPerHour;
        if (unitsPerHour <= 0.0 || !Double.isFinite(cycleTicks)) {
            return unavailable();
        }
        long outputPerCycle = MineOutputMapping.physicalAmountFor(deposit.getResourceId(), BASE_OUTPUT_PER_CYCLE);
        double perMinute = outputPerCycle * 1200.0 / cycleTicks;

        return new MineProductionProfile(
                localPotentialPerHour,
                clamp01(localPotentialPerHour / PEAK_POTENTIAL_PER_HOUR),
                outputPerCycle,
                cycleTicks,
                perMinute
        );
    }

    public static MineProductionProfile unavailable() {
        return new MineProductionProfile(0.0, 0.0, 0L, 0.0, 0.0);
    }

    public int cycleDurationTicks() {
        return (int) Math.ceil(exactCycleTicks);
    }

    public boolean viable() {
        return outputPerCycle > 0L;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
