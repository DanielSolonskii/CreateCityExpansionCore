package com.example.examplemod.geology;

/**
 * Класс качества месторождения: потолок локального
 * production potential в его богатом ядре.
 *
 * Класс качества и класс размера выбираются независимо,
 * поэтому возможны как богатое маленькое месторождение,
 * так и бедное гигантское.
 */
public enum DepositQualityClass {

    MODEST(0.25, 500.0, 1_500.0),
    RICH(0.35, 1_500.0, 3_000.0),
    DENSE(0.25, 3_000.0, 6_000.0),
    PRIME(0.12, 6_000.0, 8_500.0),
    EXCEPTIONAL(0.03, 8_500.0, 10_000.0);

    /** Потолок шкалы: 1.0 grade == 10 000 единиц/ч в ядре. */
    public static final double PEAK_POTENTIAL_CEILING = 10_000.0;

    private final double share;
    private final double minPeakPerHour;
    private final double maxPeakPerHour;

    DepositQualityClass(
            double share,
            double minPeakPerHour,
            double maxPeakPerHour
    ) {
        this.share = share;
        this.minPeakPerHour = minPeakPerHour;
        this.maxPeakPerHour = maxPeakPerHour;
    }

    public double share() {
        return share;
    }

    public double minPeakPerHour() {
        return minPeakPerHour;
    }

    public double maxPeakPerHour() {
        return maxPeakPerHour;
    }

    /**
     * Восстанавливает класс качества из сохранённого
     * пикового потенциала. Используется при загрузке
     * старых данных и как безопасный fallback.
     */
    public static DepositQualityClass fromPeakPerHour(double peakPerHour) {
        if (!Double.isFinite(peakPerHour)) {
            return MODEST;
        }
        for (DepositQualityClass value : values()) {
            if (peakPerHour <= value.maxPeakPerHour) {
                return value;
            }
        }
        return EXCEPTIONAL;
    }

    /**
     * Класс качества, соответствующий legacy grade 0-1.
     * Нужен только для миграции данных формата 1/2.
     */
    public static DepositQualityClass fromLegacyGrade(double grade) {
        double clamped = Math.max(0.0, Math.min(1.0, grade));
        return fromPeakPerHour(clamped * PEAK_POTENTIAL_CEILING);
    }
}