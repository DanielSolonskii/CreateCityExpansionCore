package com.example.examplemod.geology;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

public record DepositExtractionResult(
        Status status,
        long extractedAmount,
        OptionalLong remainingReserve,
        Optional<DepositStage> stage
) {

    public enum Status {
        EXTRACTED,
        ZERO_REQUEST,
        INVALID_REQUEST,
        DEPLETED,
        NOT_FOUND,
        STORAGE_UNAVAILABLE
    }

    public DepositExtractionResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(remainingReserve, "remainingReserve");
        Objects.requireNonNull(stage, "stage");

        if (extractedAmount < 0) {
            throw new IllegalArgumentException("extractedAmount cannot be negative");
        }
        boolean statePresent = remainingReserve.isPresent();
        if (statePresent != stage.isPresent()) {
            throw new IllegalArgumentException(
                    "remainingReserve and stage must either both be present or both be absent"
            );
        }
        if (statePresent && remainingReserve.getAsLong() < 0) {
            throw new IllegalArgumentException("remainingReserve cannot be negative");
        }
        boolean unavailable = status == Status.NOT_FOUND
                || status == Status.STORAGE_UNAVAILABLE;
        if (unavailable == statePresent) {
            throw new IllegalArgumentException(
                    "Deposit state presence does not match result status"
            );
        }
        if (status == Status.EXTRACTED && extractedAmount == 0) {
            throw new IllegalArgumentException(
                    "An EXTRACTED result must contain a positive extractedAmount"
            );
        }
        if (status != Status.EXTRACTED && extractedAmount != 0) {
            throw new IllegalArgumentException(
                    "Only an EXTRACTED result can contain a positive extractedAmount"
            );
        }
    }

    public boolean found() {
        return remainingReserve.isPresent();
    }

    public boolean changed() {
        return extractedAmount > 0;
    }

    static DepositExtractionResult forDeposit(
            Status status,
            long extractedAmount,
            GeologicalDeposit deposit
    ) {
        return new DepositExtractionResult(
                status,
                extractedAmount,
                OptionalLong.of(deposit.getRemainingReserve()),
                Optional.of(deposit.getStage())
        );
    }

    static DepositExtractionResult notFound() {
        return unavailable(Status.NOT_FOUND);
    }

    static DepositExtractionResult storageUnavailable() {
        return unavailable(Status.STORAGE_UNAVAILABLE);
    }

    private static DepositExtractionResult unavailable(Status status) {
        return new DepositExtractionResult(
                status,
                0L,
                OptionalLong.empty(),
                Optional.empty()
        );
    }
}
