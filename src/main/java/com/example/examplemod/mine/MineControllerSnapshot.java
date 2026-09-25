package com.example.examplemod.mine;

import com.example.examplemod.geology.GeologyDepositSnapshot;
import com.example.examplemod.geology.GeologyRegionSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable server-produced view consumed by the mine screen. */
public record MineControllerSnapshot(
        BlockPos controllerPos,
        ResourceLocation dimensionId,
        IndustrialMineState state,
        MineElectricalState electricalState,
        double machineProgress,
        double miningProgress,
        double richness,
        double productionPerMinute,
        long outputPerCycle,
        int cycleDurationTicks,
        boolean structureValid,
        String structureMessage,
        boolean geologyAvailable,
        MinePowerSnapshot power,
        MineOutputSnapshot output,
        Optional<GeologyRegionSnapshot> region,
        Optional<ResourceLocation> selectedDepositId,
        Optional<UUID> creatorId,
        boolean specialized,
        boolean commissioned,
        Optional<UUID> cityId,
        Optional<UUID> projectId,
        long requestedExtraction,
        long actualExtraction,
        boolean energyIntegrationPending,
        String actionMessage
) {

    public MineControllerSnapshot {
        Objects.requireNonNull(controllerPos, "controllerPos");
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(electricalState, "electricalState");
        Objects.requireNonNull(structureMessage, "structureMessage");
        Objects.requireNonNull(power, "power");
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(selectedDepositId, "selectedDepositId");
        Objects.requireNonNull(creatorId, "creatorId");
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(actionMessage, "actionMessage");
        if (!Double.isFinite(machineProgress) || machineProgress < 0.0 || machineProgress > 1.0
                || !Double.isFinite(miningProgress) || miningProgress < 0.0 || miningProgress > 1.0
                || !Double.isFinite(richness) || richness < 0.0 || richness > 1.0
                || !Double.isFinite(productionPerMinute) || productionPerMinute < 0.0
                || outputPerCycle < 0L || cycleDurationTicks < 0) {
            throw new IllegalArgumentException("Progress must be finite and within [0, 1]");
        }
        if (actualExtraction < 0) {
            throw new IllegalArgumentException("actualExtraction cannot be negative");
        }
        if (selectedDepositId.isPresent()
                && region.flatMap(value -> value.findDeposit(
                        selectedDepositId.get()
                )).isEmpty()) {
            throw new IllegalArgumentException(
                    "selectedDepositId must belong to the snapshot region"
            );
        }
        if (commissioned && (cityId.isEmpty() || projectId.isEmpty()
                || selectedDepositId.isEmpty())) {
            throw new IllegalArgumentException("commissioned controller requires city, project and deposit");
        }
    }

    public Optional<GeologyDepositSnapshot> selectedDeposit() {
        return selectedDepositId.flatMap(
                id -> region.flatMap(value -> value.findDeposit(id))
        );
    }
}
