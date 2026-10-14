package com.example.examplemod.economy;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** One-time server ledger entry for one physical Create Package. */
public final class ShipmentToken {
    private final UUID tokenId;
    @Nullable private final UUID manifestId;
    private final UUID subjectId;
    private final EconomyTypes.ShipmentTokenKind kind;
    private final UUID senderCityId;
    private final UUID receiverCityId;
    private final ResourceLocation itemId;
    private final long amount;
    private final long createdAt;
    private final long expiresAt;
    private EconomyTypes.ShipmentTokenState state = EconomyTypes.ShipmentTokenState.ACTIVE;
    private long consumedAt;

    public ShipmentToken(UUID tokenId, @Nullable UUID manifestId, UUID subjectId,
                         EconomyTypes.ShipmentTokenKind kind, UUID senderCityId, UUID receiverCityId,
                         ResourceLocation itemId, long amount, long createdAt, long expiresAt) {
        this.tokenId = Objects.requireNonNull(tokenId);
        this.manifestId = manifestId;
        this.subjectId = Objects.requireNonNull(subjectId);
        this.kind = Objects.requireNonNull(kind);
        this.senderCityId = Objects.requireNonNull(senderCityId);
        this.receiverCityId = Objects.requireNonNull(receiverCityId);
        this.itemId = Objects.requireNonNull(itemId);
        this.amount = EconomyMath.checkedPositive(amount, 100_000_000L, "amount");
        this.createdAt = Math.max(0L, createdAt);
        this.expiresAt = Math.max(0L, expiresAt);
    }

    public void consume(long now) { state = EconomyTypes.ShipmentTokenState.CONSUMED; consumedAt = Math.max(0L, now); }
    public void invalidate() { if (state == EconomyTypes.ShipmentTokenState.ACTIVE) state = EconomyTypes.ShipmentTokenState.INVALID; }
    void restore(EconomyTypes.ShipmentTokenState restoredState, long restoredConsumedAt) {
        state = restoredState == null ? EconomyTypes.ShipmentTokenState.INVALID : restoredState;
        consumedAt = Math.max(0L, restoredConsumedAt);
    }

    public UUID tokenId() { return tokenId; }
    @Nullable public UUID manifestId() { return manifestId; }
    public UUID subjectId() { return subjectId; }
    public EconomyTypes.ShipmentTokenKind kind() { return kind; }
    public UUID senderCityId() { return senderCityId; }
    public UUID receiverCityId() { return receiverCityId; }
    public ResourceLocation itemId() { return itemId; }
    public long amount() { return amount; }
    public long createdAt() { return createdAt; }
    public long expiresAt() { return expiresAt; }
    public EconomyTypes.ShipmentTokenState state() { return state; }
    public long consumedAt() { return consumedAt; }
}
