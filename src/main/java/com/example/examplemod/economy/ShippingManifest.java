package com.example.examplemod.economy;

import java.util.Objects;
import java.util.UUID;

/** Server-authoritative authorization represented in-game by a Shipping Manifest item. */
public final class ShippingManifest {
    private final UUID manifestId;
    private final UUID contractId;
    private final UUID contractorCityId;
    private final long authorizedAmount;
    private final long expiresAt;
    private long dispatchedAmount;
    private long acceptedAmount;
    private EconomyTypes.ManifestState state;

    public ShippingManifest(UUID manifestId, UUID contractId, UUID contractorCityId,
                            long authorizedAmount, long expiresAt) {
        this.manifestId = Objects.requireNonNull(manifestId);
        this.contractId = Objects.requireNonNull(contractId);
        this.contractorCityId = Objects.requireNonNull(contractorCityId);
        this.authorizedAmount = EconomyMath.checkedPositive(authorizedAmount, 100_000_000L, "authorizedAmount");
        this.expiresAt = Math.max(0L, expiresAt);
        this.state = EconomyTypes.ManifestState.ACTIVE;
    }

    public long remainingToDispatch() { return Math.max(0L, authorizedAmount - dispatchedAmount); }
    public void dispatched(long amount) { dispatchedAmount = Math.addExact(dispatchedAmount, amount); }
    public void accepted(long amount) { acceptedAmount = Math.addExact(acceptedAmount, amount); }
    public void complete() { state = EconomyTypes.ManifestState.COMPLETED; }
    public void expire() { if (state == EconomyTypes.ManifestState.ACTIVE) state = EconomyTypes.ManifestState.EXPIRED; }
    public void invalidate() { if (state == EconomyTypes.ManifestState.ACTIVE) state = EconomyTypes.ManifestState.INVALID; }

    void restore(long dispatched, long accepted, EconomyTypes.ManifestState restoredState) {
        dispatchedAmount = Math.max(0L, Math.min(authorizedAmount, dispatched));
        acceptedAmount = Math.max(0L, Math.min(dispatchedAmount, accepted));
        state = restoredState == null ? EconomyTypes.ManifestState.INVALID : restoredState;
    }

    public UUID manifestId() { return manifestId; }
    public UUID contractId() { return contractId; }
    public UUID contractorCityId() { return contractorCityId; }
    public long authorizedAmount() { return authorizedAmount; }
    public long dispatchedAmount() { return dispatchedAmount; }
    public long acceptedAmount() { return acceptedAmount; }
    public long expiresAt() { return expiresAt; }
    public EconomyTypes.ManifestState state() { return state; }
}
