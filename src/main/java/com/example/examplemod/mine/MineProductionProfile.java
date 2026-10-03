package com.example.examplemod.mine;

import com.example.examplemod.geology.GeologicalDeposit;

import java.util.Objects;

/**
 * Производственные значения шахты, вычисленные по выбранной
 * залежи и по позиции самой шахты.
 *
 * Шахта добывает насколько позволяет ЛОКАЛЬНЫЙ потенциал
 * залежи в её собственной точке, а не один усреднённый grade
 * на всё месторождение: шахта на краю даёт меньше, чем
 * шахта в богатом ядре, даже если это одна и та же залежь.
 */
public record MineProductionProfile(
        double localPotentialPerHour,
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

    /** Локальный потенциал, при котором шахта работает на полную мощность. */
    public static final double REFERENCE_POTENTIAL_PER_HOUR = 3_000.0;

    /** Ниже этого локального потенциала добыча не имеет смысла. */
    public static final double MINIMUM_VIABLE_POTENTIAL_PER_HOUR = 100.0;

    /** Потолок шкалы локального потенциала, единиц/ч. */
    public static final double PEAK_POTENTIAL_PER_HOUR = 10_000.0;

    public static MineProductionProfile from(
            GeologicalDeposit deposit,
            int mineX,
            int mineZ
    ) {
        Objects.requireNonNull(deposit, "deposit");
        double localPotential = deposit.localPotentialPerHour(mineX, mineZ);
        return of(deposit, localPotential);
    }

    /**
     * Совместимый путь для мест, где позиция шахты ещё не
     * известна: используется пиковый потенциал залежи.
     */
    public static MineProductionProfile from(GeologicalDeposit deposit) {
        Objects.requireNonNull(deposit, "deposit");
        return of(deposit, deposit.getPeakPotentialPerHour());
    }

    private static MineProductionProfile of(
            GeologicalDeposit deposit,
            double localPotentialPerHour
    ) {
        double richness = clamp01(
                localPotentialPerHour
                        / REFERENCE_POTENTIAL_PER_HOUR
        );
        double multiplier = MIN_MULTIPLIER
                + richness * (MAX_MULTIPLIER - MIN_MULTIPLIER);
        int cycleTicks = Math.max(1, (int) Math.round(BASE_CYCLE_TICKS / multiplier));

        /*
         * Базовое количество считается в единицах базового
         * ресурса (Fe-эквивалент для железа), затем переводится
         * в физические предметы шлюза через OUTPUT_FACTORS.
         * Длительность цикла и power-limited модель этим
         * пересчётом не затрагиваются.
         */
        long baseUnits = deposit.isDepleted() ? 0L : BASE_OUTPUT_PER_CYCLE;
        long outputPerCycle = Math.max(
                1L,
                MineOutputMapping.physicalAmountFor(
                        deposit.getResourceId(),
                        baseUnits
                )
        );
        double perMinute = outputPerCycle * 1200.0 / cycleTicks;

        return new MineProductionProfile(
                localPotentialPerHour,
                clamp01(localPotentialPerHour / PEAK_POTENTIAL_PER_HOUR),
                multiplier,
                outputPerCycle,
                cycleTicks,
                perMinute
        );
    }

    public static MineProductionProfile unavailable() {
        return new MineProductionProfile(0.0, 0.0, 0.0, 0L, 0, 0.0);
    }

    /**
     * Есть ли смысл добывать в этой точке вообще.
     */
    public boolean viable() {
        return localPotentialPerHour >= MINIMUM_VIABLE_POTENTIAL_PER_HOUR;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}