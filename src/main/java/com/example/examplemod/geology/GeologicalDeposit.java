package com.example.examplemod.geology;

import net.minecraft.resources.ResourceLocation;

import java.math.BigInteger;
import java.util.Objects;

public final class GeologicalDeposit {

    public static final long MIN_INITIAL_RESERVE = 1L;
    public static final long MAX_INITIAL_RESERVE = Long.MAX_VALUE;
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
    private final long initialReserve;
    private long remainingReserve;
    private final double grade;
    private final int depth;
    private final double hardness;
    private final double sustainableExtractionRate;

    public GeologicalDeposit(
            ResourceLocation id,
            ResourceLocation resourceId,
            int depositIndex,
            long initialReserve,
            long remainingReserve,
            double grade,
            int depth,
            double hardness,
            double sustainableExtractionRate
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.resourceId = Objects.requireNonNull(resourceId, "resourceId");

        if (depositIndex < 0) {
            throw new IllegalArgumentException("depositIndex cannot be negative");
        }
        if (initialReserve < MIN_INITIAL_RESERVE) {
            throw new IllegalArgumentException("initialReserve must be positive");
        }
        if (remainingReserve < 0 || remainingReserve > initialReserve) {
            throw new IllegalArgumentException(
                    "remainingReserve must be between zero and initialReserve"
            );
        }
        if (!Double.isFinite(grade)
                || grade <= MIN_GRADE_EXCLUSIVE
                || grade > MAX_GRADE) {
            throw new IllegalArgumentException("grade must be finite and in (0, 1]");
        }
        if (depth < MIN_DEPTH) {
            throw new IllegalArgumentException("depth cannot be negative");
        }
        if (!Double.isFinite(hardness)
                || hardness <= MIN_HARDNESS_EXCLUSIVE) {
            throw new IllegalArgumentException("hardness must be finite and positive");
        }
        if (!Double.isFinite(sustainableExtractionRate)
                || sustainableExtractionRate <= MIN_EXTRACTION_RATE_EXCLUSIVE) {
            throw new IllegalArgumentException(
                    "sustainableExtractionRate must be finite and positive"
            );
        }

        this.depositIndex = depositIndex;
        this.initialReserve = initialReserve;
        this.remainingReserve = remainingReserve;
        this.grade = grade;
        this.depth = depth;
        this.hardness = hardness;
        this.sustainableExtractionRate = sustainableExtractionRate;
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

    public long getInitialReserve() {
        return initialReserve;
    }

    public long getRemainingReserve() {
        return remainingReserve;
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

    public long getExtractedReserve() {
        return initialReserve - remainingReserve;
    }

    public double getRemainingRatio() {
        return (double) remainingReserve / initialReserve;
    }

    public double getDepletionRatio() {
        return 1.0 - getRemainingRatio();
    }

    public boolean isDepleted() {
        return remainingReserve == 0;
    }

    public DepositStage getStage() {
        if (isRemainingGreaterThan(4, 5)) {
            return DepositStage.FRESH;
        }
        if (isRemainingGreaterThan(2, 5)) {
            return DepositStage.PEAK;
        }
        if (isRemainingGreaterThan(3, 20)) {
            return DepositStage.MATURE;
        }
        if (remainingReserve > 0) {
            return DepositStage.DEEP;
        }

        return DepositStage.RESIDUAL;
    }

    private boolean isRemainingGreaterThan(long numerator, long denominator) {
        BigInteger left = BigInteger.valueOf(remainingReserve)
                .multiply(BigInteger.valueOf(denominator));
        BigInteger right = BigInteger.valueOf(initialReserve)
                .multiply(BigInteger.valueOf(numerator));
        return left.compareTo(right) > 0;
    }

    /**
     * Уменьшает запас и возвращает фактически извлечённое количество.
     * Authoritative-код должен вызывать эту операцию через GeologyManager,
     * чтобы изменение было отмечено для сохранения.
     */
    long extract(long requestedAmount) {
        if (requestedAmount < 0) {
            throw new IllegalArgumentException("requestedAmount cannot be negative");
        }

        long extracted = Math.min(requestedAmount, remainingReserve);
        remainingReserve -= extracted;
        return extracted;
    }
}
