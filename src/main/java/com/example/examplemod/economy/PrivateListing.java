package com.example.examplemod.economy;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public final class PrivateListing {
    private final UUID id;
    private final EconomyTypes.ListingType type;
    private final UUID creator;
    private final ResourceLocation itemId;
    private final long amount;
    private final long price;
    private final String meetingText;
    private final long createdAt;
    /**
     * Вторая сторона сделки, если это обмен предметом на предмет (barter).
     * null — сделка «предмет ↔ валюта», как и раньше.
     */
    private final ResourceLocation requestedItemId;
    private EconomyTypes.ListingState state = EconomyTypes.ListingState.OPEN;
    private UUID counterparty;
    private long escrow;

    public PrivateListing(UUID id, EconomyTypes.ListingType type, UUID creator, ResourceLocation itemId,
                          long amount, long price, String meetingText, long createdAt) {
        this(id, type, creator, itemId, amount, price, meetingText, createdAt, null);
    }

    public PrivateListing(UUID id, EconomyTypes.ListingType type, UUID creator, ResourceLocation itemId,
                          long amount, long price, String meetingText, long createdAt,
                          ResourceLocation requestedItemId) {
        this.id = Objects.requireNonNull(id);
        this.type = Objects.requireNonNull(type);
        this.creator = Objects.requireNonNull(creator);
        this.itemId = Objects.requireNonNull(itemId);
        this.amount = EconomyMath.checkedPositive(amount, 100_000_000L, "amount");
        this.price = EconomyMath.checkedPositive(price, Long.MAX_VALUE, "price");
        String safe = meetingText == null ? "" : meetingText.trim();
        this.meetingText = safe.length() <= 160 ? safe : safe.substring(0, 160);
        this.createdAt = Math.max(0L, createdAt);
        this.requestedItemId = requestedItemId;
    }

    public boolean reserve(UUID player, long reservedMoney) {
        if (state != EconomyTypes.ListingState.OPEN || player == null || player.equals(creator)) return false;
        counterparty = player;
        escrow = reservedMoney;
        state = EconomyTypes.ListingState.RESERVED;
        return true;
    }

    boolean reserveEscrowOnly(long reservedMoney) {
        if (type != EconomyTypes.ListingType.BUY || state != EconomyTypes.ListingState.OPEN) return false;
        escrow = reservedMoney;
        return true;
    }

    public boolean handOver(UUID player) {
        if (state != EconomyTypes.ListingState.RESERVED || !supplier().equals(player)) return false;
        state = EconomyTypes.ListingState.HANDED_OVER;
        return true;
    }

    public boolean complete(UUID player) {
        if (state != EconomyTypes.ListingState.HANDED_OVER || !buyer().equals(player)) return false;
        state = EconomyTypes.ListingState.COMPLETED;
        return true;
    }

    public boolean cancel(UUID player) {
        if ((state != EconomyTypes.ListingState.OPEN && state != EconomyTypes.ListingState.RESERVED)
                || (!creator.equals(player) && !Objects.equals(counterparty, player))) return false;
        state = EconomyTypes.ListingState.CANCELLED;
        return true;
    }

    public boolean dispute(UUID player) {
        if (state != EconomyTypes.ListingState.HANDED_OVER
                || (!creator.equals(player) && !Objects.equals(counterparty, player))) return false;
        state = EconomyTypes.ListingState.DISPUTED;
        return true;
    }

    boolean adminResolve(boolean supplierWins) {
        if (state != EconomyTypes.ListingState.DISPUTED) return false;
        state = supplierWins ? EconomyTypes.ListingState.COMPLETED : EconomyTypes.ListingState.CANCELLED;
        return true;
    }

    public UUID buyer() { return type == EconomyTypes.ListingType.BUY ? creator : counterparty; }
    public UUID supplier() { return type == EconomyTypes.ListingType.SELL ? creator : counterparty; }
    public long takeEscrow() { long value = escrow; escrow = 0L; return value; }

    void restore(EconomyTypes.ListingState restoredState, UUID restoredCounterparty, long restoredEscrow) {
        state = restoredState == null ? EconomyTypes.ListingState.OPEN : restoredState;
        counterparty = restoredCounterparty;
        escrow = Math.max(0L, Math.min(price, restoredEscrow));
    }

    public UUID id() { return id; }
    public EconomyTypes.ListingType type() { return type; }
    public UUID creator() { return creator; }
    public UUID counterparty() { return counterparty; }
    public ResourceLocation itemId() { return itemId; }
    public long amount() { return amount; }
    public long price() { return price; }
    public String meetingText() { return meetingText; }
    public long createdAt() { return createdAt; }
    public EconomyTypes.ListingState state() { return state; }
    public long escrow() { return escrow; }

    /**
     * Объявление «товар ↔ товар». Для таких сделок валюта не участвует.
     */
    public boolean isBarter() { return requestedItemId != null; }

    @org.jetbrains.annotations.Nullable
    public ResourceLocation requestedItemId() { return requestedItemId; }

    /**
     * Что отдаёт создатель объявления: деньги (покупка) или товар (продажа / barter).
     */
    public boolean offersCurrency() { return type == EconomyTypes.ListingType.BUY; }

    /** Предмет, который отдаёт создатель объявления; null — если он отдаёт валюту. */
    @org.jetbrains.annotations.Nullable
    public ResourceLocation offerItemId() { return offersCurrency() ? null : itemId; }

    /** Количество/сумма того, что отдаёт создатель объявления. */
    public long offerAmount() { return offersCurrency() ? price : amount; }

    /** Предмет, который создатель хочет получить; null — если он хочет валюту. */
    @org.jetbrains.annotations.Nullable
    public ResourceLocation requestItemId() { return offersCurrency() ? itemId : requestedItemId; }

    /** Количество/сумма того, что создатель хочет получить. */
    public long requestAmount() { return offersCurrency() ? amount : price; }
}
