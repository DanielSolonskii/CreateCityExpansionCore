package com.example.examplemod.geology;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;

/**
 * Immutable client-facing view of a deposit. It deliberately contains values,
 * not a mutable GeologicalDeposit reference.
 */
public record GeologyDepositSnapshot(
        ResourceLocation depositId,
        ResourceLocation resourceId,
        long initialReserve,
        long remainingReserve,
        DepositStage stage,
        double grade,
        int depth,
        double hardness,
        double sustainableExtractionRate
) {

    public GeologyDepositSnapshot {
        Objects.requireNonNull(depositId, "depositId");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(stage, "stage");
        if (initialReserve <= 0) {
            throw new IllegalArgumentException("initialReserve must be positive");
        }
        if (remainingReserve < 0 || remainingReserve > initialReserve) {
            throw new IllegalArgumentException("remainingReserve is outside deposit bounds");
        }
        if (!Double.isFinite(grade) || grade <= 0.0 || grade > 1.0) {
            throw new IllegalArgumentException("grade must be finite and in (0, 1]");
        }
        if (depth < 0) {
            throw new IllegalArgumentException("depth cannot be negative");
        }
        if (!Double.isFinite(hardness) || hardness <= 0.0) {
            throw new IllegalArgumentException("hardness must be finite and positive");
        }
        if (!Double.isFinite(sustainableExtractionRate)
                || sustainableExtractionRate <= 0.0) {
            throw new IllegalArgumentException(
                    "sustainableExtractionRate must be finite and positive"
            );
        }
    }

    public static GeologyDepositSnapshot from(GeologicalDeposit deposit) {
        Objects.requireNonNull(deposit, "deposit");
        return new GeologyDepositSnapshot(
                deposit.getId(),
                deposit.getResourceId(),
                deposit.getInitialReserve(),
                deposit.getRemainingReserve(),
                deposit.getStage(),
                deposit.getGrade(),
                deposit.getDepth(),
                deposit.getHardness(),
                deposit.getSustainableExtractionRate()
        );
    }

    public double remainingRatio() {
        return (double) remainingReserve / initialReserve;
    }

    public boolean depleted() {
        return remainingReserve == 0;
    }
}
