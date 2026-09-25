package com.example.examplemod.mine;

import com.example.examplemod.geology.DepositStage;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record MineOperationResult(
        IndustrialMineState state,
        Optional<ResourceLocation> regionId,
        Optional<ResourceLocation> depositId,
        Optional<ResourceLocation> resourceId,
        long requestedAmount,
        long extractedAmount,
        OptionalLong remainingReserve,
        Optional<DepositStage> depositStage,
        boolean activeDepositChanged,
        boolean energyIntegrationPending,
        String message
) {

    public MineOperationResult {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(regionId, "regionId");
        Objects.requireNonNull(depositId, "depositId");
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(remainingReserve, "remainingReserve");
        Objects.requireNonNull(depositStage, "depositStage");
        Objects.requireNonNull(message, "message");

        if (extractedAmount < 0) {
            throw new IllegalArgumentException("extractedAmount cannot be negative");
        }
        if (remainingReserve.isPresent() != depositStage.isPresent()) {
            throw new IllegalArgumentException(
                    "remainingReserve and depositStage must have the same presence"
            );
        }
        if (remainingReserve.isPresent() && remainingReserve.getAsLong() < 0) {
            throw new IllegalArgumentException("remainingReserve cannot be negative");
        }
        if (depositId.isPresent() != resourceId.isPresent()) {
            throw new IllegalArgumentException(
                    "depositId and resourceId must have the same presence"
            );
        }
        if (extractedAmount > 0 && state != IndustrialMineState.RUNNING) {
            throw new IllegalArgumentException(
                    "Positive extraction requires RUNNING state"
            );
        }
    }

    public boolean changedGeology() {
        return extractedAmount > 0;
    }
}
