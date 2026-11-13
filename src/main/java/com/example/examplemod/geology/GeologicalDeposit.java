package com.example.examplemod.geology;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/** Неистощимая жила с неизменяемой геометрией и потенциалом. */
public final class GeologicalDeposit {

    public static final double MIN_GRADE_EXCLUSIVE = 0.0;
    public static final double MAX_GRADE = 1.0;
    public static final int MIN_DEPTH = 0;
    public static final int MAX_DEPTH = Integer.MAX_VALUE;
    public static final double MIN_HARDNESS_EXCLUSIVE = 0.0;
    public static final double MAX_HARDNESS = Double.MAX_VALUE;
    public static final double MIN_EXTRACTION_RATE_EXCLUSIVE = 0.0;
    public static final double MAX_EXTRACTION_RATE = Double.MAX_VALUE;

    private final ResourceLocation id;
    private final ResourceLocation resourceId;
    private final int depositIndex;
    private final double grade;
    private final int depth;
    private final double hardness;
    private final double sustainableExtractionRate;
    private final DepositShape shape;
    private final double peakPotentialPerHour;
    private final DepositQualityClass qualityClass;
    private final DepositSizeClass sizeClass;

    /**
     * Основной конструктор процедурной геологии.
     * Все значения зафиксированы на момент материализации
     * и не пересчитываются при следующих обращениях.
     */
    public GeologicalDeposit(
            ResourceLocation id,
            ResourceLocation resourceId,
            int depositIndex,
            double grade,
            int depth,
            double hardness,
            double sustainableExtractionRate,
            DepositShape shape,
            double peakPotentialPerHour,
            DepositQualityClass qualityClass,
            DepositSizeClass sizeClass
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");
        this.shape = Objects.requireNonNull(shape, "shape");
        this.qualityClass = Objects.requireNonNull(qualityClass, "qualityClass");
        this.sizeClass = Objects.requireNonNull(sizeClass, "sizeClass");

        if (depositIndex < 0) {
            throw new IllegalArgumentException("depositIndex cannot be negative");
        }
        if (!Double.isFinite(grade)
                || grade <= MIN_GRADE_EXCLUSIVE
                || grade > MAX_GRADE) {
            throw new IllegalArgumentException("grade must be finite and in (0, 1]");
        }
        if (depth < MIN_DEPTH) {
            throw new IllegalArgumentException("depth cannot be negative");
        }
        if (!Double.isFinite(hardness) || hardness <= MIN_HARDNESS_EXCLUSIVE) {
            throw new IllegalArgumentException("hardness must be finite and positive");
        }
        if (!Double.isFinite(sustainableExtractionRate)
                || sustainableExtractionRate <= MIN_EXTRACTION_RATE_EXCLUSIVE) {
            throw new IllegalArgumentException(
                    "sustainableExtractionRate must be finite and positive"
            );
        }
        if (!Double.isFinite(peakPotentialPerHour)
                || peakPotentialPerHour <= MIN_EXTRACTION_RATE_EXCLUSIVE
                || peakPotentialPerHour > DepositQualityClass.PEAK_POTENTIAL_CEILING) {
            throw new IllegalArgumentException(
                    "peakPotentialPerHour must be finite and within (0, "
                            + DepositQualityClass.PEAK_POTENTIAL_CEILING + "]"
            );
        }

        this.depositIndex = depositIndex;
        this.grade = grade;
        this.depth = depth;
        this.hardness = hardness;
        this.sustainableExtractionRate = sustainableExtractionRate;
        this.peakPotentialPerHour = peakPotentialPerHour;
    }

    public ResourceLocation getId() {
        return id;
    }

    public ResourceLocation getResourceId() {
        return resourceId;
    }

    public int getDepositIndex() {
        return depositIndex;
    }

    public double getGrade() {
        return grade;
    }

    public int getDepth() {
        return depth;
    }

    public double getHardness() {
        return hardness;
    }

    public double getSustainableExtractionRate() {
        return sustainableExtractionRate;
    }

    public DepositShape getShape() {
        return shape;
    }

    public DepositQualityClass getQualityClass() {
        return qualityClass;
    }

    public DepositSizeClass getSizeClass() {
        return sizeClass;
    }

    /**
     * Потолок production potential в богатом ядре, единиц/ч.
     */
    public double getPeakPotentialPerHour() {
        return peakPotentialPerHour;
    }

    public int getCenterX() {
        return shape.centerX();
    }

    public int getCenterZ() {
        return shape.centerZ();
    }

    public boolean contains(int worldX, int worldZ) {
        return shape.contains(worldX, worldZ);
    }

    /**
     * Локальный production potential конкретного блока, единиц/ч.
     *
     * Плавно снижается от ядра к периферии: производная функции
     * обращается в ноль и на границе ядра, и на границе залежи,
     * поэтому скачков между соседними блоками не возникает.
     */
    public double localPotentialPerHour(int worldX, int worldZ) {
        return localPotentialPerHour((double) worldX, (double) worldZ);
    }

    public double localPotentialPerHour(double worldX, double worldZ) {
        double radius = shape.normalizedRadius(worldX, worldZ);
        if (radius >= 1.0) {
            return 0.0;
        }
        double core = Math.max(
                shape.coreRadius(),
                DepositShape.MIN_CORE_FRACTION
        );
        double t = (radius - core) / (1.0 - core);
        t = Math.max(0.0, Math.min(1.0, t));
        double smooth = t * t * (3.0 - 2.0 * t);
        return peakPotentialPerHour * (1.0 - smooth);
    }
}
