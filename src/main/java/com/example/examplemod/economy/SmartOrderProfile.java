package com.example.examplemod.economy;

import net.minecraft.resources.ResourceLocation;

public record SmartOrderProfile(
        ResourceLocation itemId,
        long targetStock,
        long reorderPoint,
        long batchSize,
        long baseUnitPrice,
        int minimumMultiplierBasisPoints,
        int maximumMultiplierBasisPoints,
        long cooldownTicks,
        long lastGeneratedAt
) {
    public SmartOrderProfile {
        if (itemId == null || targetStock <= 0L || reorderPoint < 0L || reorderPoint > targetStock
                || batchSize <= 0L || baseUnitPrice <= 0L
                || minimumMultiplierBasisPoints <= 0
                || maximumMultiplierBasisPoints < minimumMultiplierBasisPoints
                || maximumMultiplierBasisPoints > 100_000 || cooldownTicks < 20L) {
            throw new IllegalArgumentException("Invalid smart-order profile");
        }
    }

    public SmartOrderProfile generatedAt(long gameTime) {
        return new SmartOrderProfile(itemId, targetStock, reorderPoint, batchSize, baseUnitPrice,
                minimumMultiplierBasisPoints, maximumMultiplierBasisPoints, cooldownTicks, gameTime);
    }

    public EconomyTypes.DemandLevel demand(long accountedStock, long incoming) {
        long effective = EconomyMath.saturatingAdd(Math.max(0L, accountedStock), Math.max(0L, incoming), Long.MAX_VALUE);
        if (effective <= Math.max(0L, reorderPoint / 4L)) return EconomyTypes.DemandLevel.CRITICAL;
        if (effective <= reorderPoint / 2L) return EconomyTypes.DemandLevel.HIGH;
        if (effective < reorderPoint) return EconomyTypes.DemandLevel.NORMAL;
        return EconomyTypes.DemandLevel.LOW;
    }

    public int multiplierBasisPoints(long accountedStock, long incoming) {
        long effective = EconomyMath.saturatingAdd(Math.max(0L, accountedStock), Math.max(0L, incoming), targetStock);
        long shortage = Math.max(0L, targetStock - effective);
        long span = maximumMultiplierBasisPoints - (long) minimumMultiplierBasisPoints;
        long addition = targetStock == 0L ? 0L : java.math.BigInteger.valueOf(shortage)
                .multiply(java.math.BigInteger.valueOf(span))
                .divide(java.math.BigInteger.valueOf(targetStock)).longValue();
        return (int) Math.min(maximumMultiplierBasisPoints, minimumMultiplierBasisPoints + addition);
    }

    public long unitPrice(long accountedStock, long incoming) {
        return Math.max(1L, EconomyMath.applyBasisPoints(baseUnitPrice,
                multiplierBasisPoints(accountedStock, incoming)));
    }
}
