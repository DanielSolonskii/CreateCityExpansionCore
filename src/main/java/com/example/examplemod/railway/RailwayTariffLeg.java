package com.example.examplemod.railway;

import java.math.BigInteger;
import java.util.UUID;

/** Immutable tariff and ownership snapshot for one paid part of a trip. */
public record RailwayTariffLeg(UUID networkId, RailwayOwnershipTypes.OwnerType ownerType, UUID ownerId,
                               long tariffPerThousandBlocks, double startDistance, double endDistance,
                               long reservedAmount) {
    public RailwayTariffLeg {
        if (endDistance < startDistance || tariffPerThousandBlocks < 0 || reservedAmount < 0)
            throw new IllegalArgumentException("Invalid tariff leg");
    }

    public double usedLength(double travelled) {
        return Math.max(0, Math.min(endDistance, travelled) - startDistance);
    }

    public long charge(double length) {
        if (length <= 0 || tariffPerThousandBlocks == 0) return 0;
        long milliBlocks = Math.max(0, Math.round(length * 1000.0));
        BigInteger product = BigInteger.valueOf(milliBlocks).multiply(BigInteger.valueOf(tariffPerThousandBlocks));
        return product.add(BigInteger.valueOf(999_999)).divide(BigInteger.valueOf(1_000_000)).longValueExact();
    }
}
