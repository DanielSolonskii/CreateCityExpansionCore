package com.example.examplemod.geology;

/**
 * Класс размера месторождения.
 *
 * Определяет и физический масштаб залежи
 * (полуоси эллипса), и ориентировочный срок
 * эксплуатации, из которого выводится reserve.
 *
 * Reserve не зависит от качества: богатое маленькое
 * и бедное гигантское месторождение равновероятны.
 */
public enum DepositSizeClass {

    SMALL(0.20, 150, 230, 50, 100),
    NORMAL(0.50, 220, 330, 120, 180),
    LARGE(0.25, 300, 400, 180, 300),
    GIANT(0.05, 380, 480, 300, 500);

    /** Опорная производительность для расчёта срока эксплуатации. */
    public static final double REFERENCE_THROUGHPUT_PER_HOUR = 3_000.0;

    /** Максимальный полуось: задаёт радиус поиска соседних macro-cell. */
    public static final int MAX_RADIUS = 480;

    /** Минимальный полуось: диаметр обычной залежи не меньше 300 блоков. */
    public static final int MIN_RADIUS = 150;

    private final double share;
    private final int minRadius;
    private final int maxRadius;
    private final int minLifetimeHours;
    private final int maxLifetimeHours;

    DepositSizeClass(
            double share,
            int minRadius,
            int maxRadius,
            int minLifetimeHours,
            int maxLifetimeHours
    ) {
        this.share = share;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
        this.minLifetimeHours = minLifetimeHours;
        this.maxLifetimeHours = maxLifetimeHours;
    }

    public double share() {
        return share;
    }

    public int minRadius() {
        return minRadius;
    }

    public int maxRadius() {
        return maxRadius;
    }

    public int minLifetimeHours() {
        return minLifetimeHours;
    }

    public int maxLifetimeHours() {
        return maxLifetimeHours;
    }

    public static DepositSizeClass fromLegacy() {
        return NORMAL;
    }
}