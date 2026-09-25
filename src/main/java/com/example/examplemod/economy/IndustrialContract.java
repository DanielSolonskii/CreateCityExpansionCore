package com.example.examplemod.economy;

import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class IndustrialContract {
    private final UUID id;
    private final EconomyTypes.ContractKind kind;
    private final EconomyTypes.ContractMode mode;
    private final UUID issuerCityId;
    private final UUID createdBy;
    private final String title;
    private final String description;
    private final ResourceLocation itemId;
    private final long amount;
    private final long reward;
    private final UUID destinationRegistrationId;
    private final UUID stationRegistrationId;
    private final long createdAt;
    private final long deadlineDurationTicks;
    private final Map<UUID, Long> contributions = new LinkedHashMap<>();
    private final java.util.Set<UUID> participants = new java.util.LinkedHashSet<>();
    private EconomyTypes.ContractState state;
    private UUID contractorCityId;
    private UUID acceptedByPlayerId;
    private long deliveredAmount;
    private long acceptedAt;
    private long deliveredAt;
    private long escrow;

    public IndustrialContract(UUID id, EconomyTypes.ContractKind kind, EconomyTypes.ContractMode mode,
                              UUID issuerCityId, UUID createdBy, String title, String description,
                              ResourceLocation itemId, long amount, long reward,
                              UUID destinationRegistrationId, UUID stationRegistrationId,
                              long createdAt, long deadlineDurationTicks) {
        this.id = Objects.requireNonNull(id);
        this.kind = Objects.requireNonNull(kind);
        this.mode = Objects.requireNonNull(mode);
        this.issuerCityId = Objects.requireNonNull(issuerCityId);
        this.createdBy = Objects.requireNonNull(createdBy);
        this.title = sanitize(title, 96);
        this.description = sanitize(description, 512);
        this.itemId = Objects.requireNonNull(itemId);
        this.amount = EconomyMath.checkedPositive(amount, 100_000_000L, "amount");
        this.reward = EconomyMath.checkedPositive(reward, Long.MAX_VALUE, "reward");
        this.destinationRegistrationId = Objects.requireNonNull(destinationRegistrationId);
        this.stationRegistrationId = stationRegistrationId;
        this.createdAt = Math.max(0L, createdAt);
        this.deadlineDurationTicks = Math.max(0L, deadlineDurationTicks);
        this.state = EconomyTypes.ContractState.OPEN;
        this.escrow = reward;
    }

    private static String sanitize(String value, int maximum) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() <= maximum ? normalized : normalized.substring(0, maximum);
    }

    public boolean accept(UUID cityId, UUID playerId, long now) {
        if (cityId == null || playerId == null || cityId.equals(issuerCityId)) return false;
        if (mode == EconomyTypes.ContractMode.COMMUNITY) {
            if (state != EconomyTypes.ContractState.OPEN && state != EconomyTypes.ContractState.ACCEPTED
                    && state != EconomyTypes.ContractState.IN_DELIVERY) return false;
            boolean added=participants.add(cityId);
            if(state==EconomyTypes.ContractState.OPEN){state=EconomyTypes.ContractState.ACCEPTED;acceptedByPlayerId=playerId;acceptedAt=Math.max(0L,now);}
            return added;
        }
        if (state != EconomyTypes.ContractState.OPEN) return false;
        contractorCityId = cityId;
        acceptedByPlayerId = playerId;
        participants.add(cityId);
        acceptedAt = Math.max(0L, now);
        state = EconomyTypes.ContractState.ACCEPTED;
        return true;
    }

    public long recordDelivery(UUID contributingCityId, ResourceLocation flowingItem, long flowedAmount, long now) {
        if (flowedAmount <= 0L || !itemId.equals(flowingItem) || state.terminal()
                || state == EconomyTypes.ContractState.DISPUTED || state == EconomyTypes.ContractState.DELIVERED) return 0L;
        if (mode == EconomyTypes.ContractMode.EXCLUSIVE) {
            if (contractorCityId == null || !contractorCityId.equals(contributingCityId)) return 0L;
        } else if (contributingCityId == null || !participants.contains(contributingCityId)) {
            return 0L;
        }
        long accepted = Math.min(flowedAmount, amount - deliveredAmount);
        if (accepted <= 0L) return 0L;
        deliveredAmount += accepted;
        contributions.merge(contributingCityId, accepted, Math::addExact);
        if (deliveredAmount == amount) {
            state = EconomyTypes.ContractState.DELIVERED;
            deliveredAt = Math.max(0L, now);
        } else {
            state = EconomyTypes.ContractState.IN_DELIVERY;
        }
        return accepted;
    }

    public boolean markShipment(UUID cityId) {
        if (state != EconomyTypes.ContractState.ACCEPTED && state != EconomyTypes.ContractState.IN_DELIVERY) return false;
        if (mode == EconomyTypes.ContractMode.EXCLUSIVE && !Objects.equals(contractorCityId, cityId)) return false;
        if (mode == EconomyTypes.ContractMode.COMMUNITY && (cityId == null || !participants.contains(cityId))) return false;
        state = EconomyTypes.ContractState.IN_DELIVERY;
        return true;
    }

    public boolean complete() {
        if (state != EconomyTypes.ContractState.DELIVERED || deliveredAmount != amount) return false;
        state = EconomyTypes.ContractState.COMPLETED;
        return true;
    }

    public boolean cancelOpen() {
        if (state != EconomyTypes.ContractState.OPEN) return false;
        state = EconomyTypes.ContractState.CANCELLED;
        return true;
    }

    public boolean fail() {
        if (state.terminal() || state == EconomyTypes.ContractState.DELIVERED || state == EconomyTypes.ContractState.DISPUTED) return false;
        state = EconomyTypes.ContractState.FAILED;
        return true;
    }

    public boolean dispute() {
        if (state != EconomyTypes.ContractState.DELIVERED) return false;
        state = EconomyTypes.ContractState.DISPUTED;
        return true;
    }

    public long takeEscrow() { long value = escrow; escrow = 0L; return value; }
    public long deadlineAt() { return acceptedAt <= 0L || deadlineDurationTicks <= 0L ? 0L : acceptedAt + deadlineDurationTicks; }
    public long remainingAmount() { return amount - deliveredAmount; }

    void restore(EconomyTypes.ContractState restoredState, UUID restoredContractor, UUID restoredAcceptedBy, long restoredDelivered,
                 long restoredAcceptedAt, long restoredDeliveredAt, long restoredEscrow,
                 Map<UUID, Long> restoredContributions, java.util.Collection<UUID> restoredParticipants) {
        state = restoredState == null ? EconomyTypes.ContractState.OPEN : restoredState;
        contractorCityId = restoredContractor;
        acceptedByPlayerId = restoredAcceptedBy;
        deliveredAmount = Math.max(0L, Math.min(amount, restoredDelivered));
        acceptedAt = Math.max(0L, restoredAcceptedAt);
        deliveredAt = Math.max(0L, restoredDeliveredAt);
        escrow = Math.max(0L, Math.min(reward, restoredEscrow));
        contributions.clear();
        participants.clear();
        if(restoredParticipants!=null)participants.addAll(restoredParticipants);
        if(restoredContractor!=null)participants.add(restoredContractor);
        long remaining = deliveredAmount;
        if (restoredContributions != null) {
            for (var entry : restoredContributions.entrySet()) {
                long value = Math.max(0L, Math.min(remaining, entry.getValue()));
                if (value > 0L) { contributions.put(entry.getKey(), value); remaining -= value; }
            }
        }
    }

    public UUID id() { return id; }
    public EconomyTypes.ContractKind kind() { return kind; }
    public EconomyTypes.ContractMode mode() { return mode; }
    public UUID issuerCityId() { return issuerCityId; }
    public UUID createdBy() { return createdBy; }
    public String title() { return title; }
    public String description() { return description; }
    public ResourceLocation itemId() { return itemId; }
    public long amount() { return amount; }
    public long reward() { return reward; }
    public UUID destinationRegistrationId() { return destinationRegistrationId; }
    public UUID stationRegistrationId() { return stationRegistrationId; }
    public long createdAt() { return createdAt; }
    public long deadlineDurationTicks() { return deadlineDurationTicks; }
    public EconomyTypes.ContractState state() { return state; }
    public UUID contractorCityId() { return contractorCityId; }
    public UUID acceptedByPlayerId() { return acceptedByPlayerId; }
    public long deliveredAmount() { return deliveredAmount; }
    public long acceptedAt() { return acceptedAt; }
    public long deliveredAt() { return deliveredAt; }
    public long escrow() { return escrow; }
    public Map<UUID, Long> contributions() { return Map.copyOf(contributions); }
    public java.util.Set<UUID> participants() { return java.util.Set.copyOf(participants); }
}
