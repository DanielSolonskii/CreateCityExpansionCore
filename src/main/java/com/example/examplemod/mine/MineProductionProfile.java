package com.example.examplemod.mine;

import com.example.examplemod.geology.GeologicalDeposit;

/** Server-side production values derived from the selected geological deposit. */
public record MineProductionProfile(
        double richness,
        double multiplier,
        long outputPerCycle,
        int cycleDurationTicks,
        double itemsPerMinute
) {

    public static final long BASE_OUTPUT_PER_CYCLE = 5L;
    public static final int BASE_CYCLE_TICKS = 200;
    public static final double MIN_MULTIPLIER = 0.5;
    public static final double MAX_MULTIPLIER = 1.5;

    public static MineProductionProfile from(GeologicalDeposit deposit) {
        double richness = clamp01(deposit.getGrade());
        double multiplier = MIN_MULTIPLIER
                + richness * (MAX_MULTIPLIER - MIN_MULTIPLIER);
        int cycleTicks = Math.max(1, (int) Math.round(BASE_CYCLE_TICKS / multiplier));
        long outputPerCycle = deposit.isDepleted() ? 0L : BASE_OUTPUT_PER_CYCLE;
        double perMinute = outputPerCycle * 1200.0 / cycleTicks;
        return new MineProductionProfile(
                richness,
                multiplier,
                outputPerCycle,
                cycleTicks,
                perMinute
        );
    }

    public static MineProductionProfile unavailable() {
        return new MineProductionProfile(0.0, 0.0, 0L, 0, 0.0);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
