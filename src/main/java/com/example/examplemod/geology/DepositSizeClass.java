package com.example.examplemod.geology;

/** Физический размер жилы. */
public enum DepositSizeClass {

    SMALL(0.20, 150, 230),
    NORMAL(0.50, 220, 330),
    LARGE(0.25, 300, 400),
    GIANT(0.05, 380, 480);

    /** Максимальный полуось: задаёт радиус поиска соседних macro-cell. */
    public static final int MAX_RADIUS = 480;

    /** Минимальный полуось: диаметр обычной залежи не меньше 300 блоков. */
    public static final int MIN_RADIUS = 150;

    private final double share;
    private final int minRadius;
    private final int maxRadius;

    DepositSizeClass(
            double share,
            int minRadius,
            int maxRadius
    ) {
        this.share = share;
        this.minRadius = minRadius;
        this.maxRadius = maxRadius;
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

    public static DepositSizeClass fromLegacy() {
        return NORMAL;
    }
}