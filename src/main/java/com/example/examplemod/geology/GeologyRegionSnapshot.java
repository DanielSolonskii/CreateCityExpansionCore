package com.example.examplemod.geology;

import net.minecraft.resources.ResourceLocation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable, compact view intended for UI/network transport. */
public record GeologyRegionSnapshot(
        ResourceLocation regionId,
        int regionX,
        int regionZ,
        List<GeologyDepositSnapshot> deposits
) {

    public GeologyRegionSnapshot {
        Objects.requireNonNull(regionId, "regionId");
        deposits = List.copyOf(Objects.requireNonNull(deposits, "deposits"));
    }

    public static GeologyRegionSnapshot from(GeologicalRegion region) {
        Objects.requireNonNull(region, "region");
        List<GeologyDepositSnapshot> snapshots = region.getDeposits()
                .stream()
                .map(GeologyDepositSnapshot::from)
                .sorted(Comparator.comparing(snapshot -> snapshot.depositId().toString()))
                .toList();
        return new GeologyRegionSnapshot(
                region.getId(),
                region.getRegionX(),
                region.getRegionZ(),
                snapshots
        );
    }

    public Optional<GeologyDepositSnapshot> findDeposit(
            ResourceLocation depositId
    ) {
        Objects.requireNonNull(depositId, "depositId");
        return deposits.stream()
                .filter(deposit -> deposit.depositId().equals(depositId))
                .findFirst();
    }
}
