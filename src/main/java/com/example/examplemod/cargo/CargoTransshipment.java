package com.example.examplemod.cargo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

/** Per-physical-parcel history; no global order, item copy or optimal-route search. */
public final class CargoTransshipment {
    static final String KEY = "ccec:transshipment";
    static final int MAX_POINTS = 64;
    /** The route state machine emits ARRIVED; DELIVERED is resolved separately through verified CargoDeliveryReceipts. */
    public enum Status { ORDINARY, WAITING_POINT, WAITING_ROUTE, WAITING_TRAIN, READY, ARRIVED, DELIVERED, BLOCKED }
    // Legacy record component name retained for source compatibility; true means terminal ARRIVED, not warehouse delivery.
    record Journey(UUID finalPoint, List<UUID> visited, UUID plannedNext, boolean delivered) {
        Journey {
            Objects.requireNonNull(finalPoint); visited = List.copyOf(visited);
            if (visited.isEmpty() || visited.size() > MAX_POINTS || new HashSet<>(visited).size() != visited.size()
                    || plannedNext != null && visited.contains(plannedNext) || delivered && plannedNext != null)
                throw new IllegalArgumentException("Invalid cargo journey");
        }
        UUID lastPoint() { return visited.getLast(); }
        boolean arrived() { return delivered; }
    }
    record Outcome(CargoAddress address, Journey journey, Status status) {}
    private CargoTransshipment() {}

    static Outcome evaluate(CargoAddress address, UUID current, Journey previous, UUID nextRule, Predicate<UUID> exists) {
        if (previous != null && !previous.finalPoint().equals(address.finalPoint()))
            return new Outcome(address, previous, Status.BLOCKED);
        if (previous != null && previous.arrived()) return new Outcome(address, previous, Status.ARRIVED);
        if (current == null || !exists.test(current)) return new Outcome(address, previous, Status.WAITING_POINT);
        Journey state = previous == null ? new Journey(address.finalPoint(), List.of(current),
                address.nextPoint().equals(current) ? null : address.nextPoint(), false) : previous;
        if (current.equals(address.finalPoint())) {
            List<UUID> visited = new ArrayList<>(state.visited());
            if (!visited.contains(current) && visited.size() < MAX_POINTS) visited.add(current);
            return new Outcome(address, new Journey(address.finalPoint(), visited, null, true), Status.ARRIVED);
        }
        if (!exists.test(address.finalPoint())) return new Outcome(address, state, Status.WAITING_ROUTE);
        if (!state.lastPoint().equals(current)) {
            if (!current.equals(state.plannedNext()) || !current.equals(address.nextPoint())
                    || state.visited().contains(current) || state.visited().size() == MAX_POINTS)
                return new Outcome(address, state, Status.BLOCKED);
            List<UUID> visited = new ArrayList<>(state.visited()); visited.add(current);
            state = new Journey(state.finalPoint(), visited, null, false);
        }
        if (state.plannedNext() != null) {
            if (!state.plannedNext().equals(address.nextPoint())) return new Outcome(address, state, Status.BLOCKED);
            return new Outcome(address, state, exists.test(address.nextPoint()) ? Status.WAITING_TRAIN : Status.WAITING_ROUTE);
        }
        // Initial physical source may already carry an explicit outgoing address from Cargo Ticker.
        UUID next = previous == null && !address.nextPoint().equals(current) ? address.nextPoint() : nextRule;
        if (previous != null && !address.nextPoint().equals(current)) return new Outcome(address, state, Status.BLOCKED);
        if (next == null || !exists.test(next)) return new Outcome(address, state, Status.WAITING_ROUTE);
        if (state.visited().contains(next)) return new Outcome(address, state, Status.BLOCKED);
        return new Outcome(address.withNext(next),
                new Journey(state.finalPoint(), state.visited(), next, false), Status.WAITING_TRAIN);
    }

    /** Manual train selection stays available, but cannot erase final delivery/history or replan the same visit. */
    static Journey manualPlan(CargoAddress before, CargoAddress requested, UUID current, Journey previous) {
        if (current == null || requested.finalPoint().equals(current) || previous != null && previous.arrived())
            throw new IllegalArgumentException("Parcel cannot leave this Cargo Point");
        if (previous == null) return new Journey(requested.finalPoint(), List.of(current), requested.nextPoint(), false);
        if (!previous.finalPoint().equals(requested.finalPoint())) throw new IllegalArgumentException("Final point changed");
        Journey state = previous;
        if (!current.equals(state.lastPoint())) {
            if (before == null || !current.equals(before.nextPoint()) || !current.equals(state.plannedNext())
                    || state.visited().contains(current) || state.visited().size() == MAX_POINTS)
                throw new IllegalArgumentException("Unexpected point or cycle");
            List<UUID> visited = new ArrayList<>(state.visited()); visited.add(current);
            state = new Journey(state.finalPoint(), visited, null, false);
        }
        if (state.plannedNext() != null && !state.plannedNext().equals(requested.nextPoint()))
            throw new IllegalArgumentException("This point already planned a departure");
        return new Journey(state.finalPoint(), state.visited(), requested.nextPoint(), false);
    }

    static Journey read(CompoundTag custom) {
        if (!custom.contains(KEY)) return null;
        if (!custom.contains(KEY, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid journey marker");
        CompoundTag tag = custom.getCompound(KEY);
        int version = tag.getInt("Version");
        if ((version != 1 && version != 2) || !tag.hasUUID("Final")) throw new IllegalArgumentException("Invalid journey version/identity");
        List<UUID> visited = new ArrayList<>();
        for (Tag raw : tag.getList("Visited", Tag.TAG_COMPOUND)) {
            CompoundTag point = (CompoundTag) raw;
            if (!point.hasUUID("Point")) throw new IllegalArgumentException("Invalid visited point");
            visited.add(point.getUUID("Point"));
        }
        if (tag.contains("Next") && !tag.hasUUID("Next")) throw new IllegalArgumentException("Invalid planned point");
        // v1's Delivered only proved point arrival. It must never become a warehouse receipt.
        return new Journey(tag.getUUID("Final"), visited, tag.hasUUID("Next") ? tag.getUUID("Next") : null,
                version == 1 ? tag.getBoolean("Delivered") : tag.getBoolean("Arrived"));
    }
    static void write(CompoundTag custom, Journey journey) {
        CompoundTag tag = new CompoundTag(); tag.putInt("Version", 2); tag.putUUID("Final", journey.finalPoint());
        ListTag visited = new ListTag();
        for (UUID id : journey.visited()) { CompoundTag point = new CompoundTag(); point.putUUID("Point", id); visited.add(point); }
        tag.put("Visited", visited); tag.putBoolean("Arrived", journey.arrived());
        if (journey.plannedNext() != null) tag.putUUID("Next", journey.plannedNext());
        custom.put(KEY, tag);
    }

    /** Export only physically terminal, persisted parcels at their final point, never merely ready/transit boxes. */
    static boolean mayExtract(CargoAddress address, UUID current, Journey journey, boolean ready) {
        return !ready && address != null && current != null && journey != null && journey.arrived()
                && current.equals(address.finalPoint()) && current.equals(journey.finalPoint());
    }
}
