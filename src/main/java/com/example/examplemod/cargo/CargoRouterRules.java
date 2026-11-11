package com.example.examplemod.cargo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.IntStream;

/** Rules and preparation origin only; never stores a second copy of any package. */
final class CargoRouterRules {
    private final Map<UUID, UUID> trains = new HashMap<>();
    private final Map<UUID, UUID> transfers = new HashMap<>();
    private final boolean[] automatic = new boolean[CargoRouterData.SLOTS];

    Map<UUID, UUID> snapshot() { return Map.copyOf(trains); }
    Map<UUID, UUID> transferSnapshot() { return Map.copyOf(transfers); }
    UUID nextFor(UUID finalPoint) { return transfers.get(finalPoint); }
    boolean putTransfer(UUID actor, UUID owner, UUID current, UUID finalPoint, UUID next, Predicate<UUID> exists) {
        if (owner == null || !owner.equals(actor) || current == null || finalPoint == null || next == null
                || current.equals(next) || current.equals(finalPoint) || !exists.test(current)
                || !exists.test(finalPoint) || !exists.test(next)) return false;
        transfers.put(finalPoint, next); return true;
    }
    boolean removeTransfer(UUID actor, UUID owner, UUID finalPoint) {
        return owner != null && owner.equals(actor) && transfers.remove(finalPoint) != null;
    }
    boolean put(UUID actor, UUID owner, UUID point, UUID train, boolean pointExists, boolean trainAllowed) {
        if (owner == null || !owner.equals(actor) || point == null || train == null || !pointExists || !trainAllowed) return false;
        trains.put(point, train); return true;
    }
    boolean remove(UUID actor, UUID owner, UUID point) {
        return owner != null && owner.equals(actor) && trains.remove(point) != null;
    }
    Optional<UUID> match(String address, Predicate<UUID> pointExists, Predicate<UUID> trainAllowed) {
        return CargoAddress.parse(address)
                .filter(route -> pointExists.test(route.nextPoint()) && pointExists.test(route.finalPoint()))
                .map(route -> trains.get(route.nextPoint())).filter(trainAllowed);
    }
    boolean isAutomatic(int slot) { return automatic[slot]; }
    void markAutomatic(int slot) { automatic[slot] = true; }
    void clearSlot(int slot) { automatic[slot] = false; }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putInt("Version", 1);
        ListTag list = new ListTag();
        trains.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag rule = new CompoundTag(); rule.putUUID("NextPoint", entry.getKey());
            rule.putUUID("Train", entry.getValue()); list.add(rule);
        });
        tag.put("Entries", list);
        ListTag routes = new ListTag();
        transfers.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            CompoundTag route = new CompoundTag(); route.putUUID("FinalPoint", entry.getKey());
            route.putUUID("NextPoint", entry.getValue()); routes.add(route);
        });
        tag.put("Transfers", routes);
        tag.putIntArray("Automatic", IntStream.range(0, automatic.length).filter(slot -> automatic[slot]).toArray());
        return tag;
    }
    static CargoRouterRules load(CompoundTag tag) {
        if (tag.getInt("Version") != 1) throw new IllegalArgumentException("Unsupported Cargo Router rules version");
        CargoRouterRules rules = new CargoRouterRules();
        for (Tag raw : tag.getList("Entries", Tag.TAG_COMPOUND)) {
            CompoundTag rule = (CompoundTag) raw;
            if (!rule.hasUUID("NextPoint") || !rule.hasUUID("Train")
                    || rules.trains.putIfAbsent(rule.getUUID("NextPoint"), rule.getUUID("Train")) != null)
                throw new IllegalArgumentException("Invalid or duplicate Cargo Router rule");
        }
        for (int slot : tag.getIntArray("Automatic")) {
            if (slot < 0 || slot >= rules.automatic.length) throw new IllegalArgumentException("Invalid automatic preparation slot");
            rules.automatic[slot] = true;
        }
        for (Tag raw : tag.getList("Transfers", Tag.TAG_COMPOUND)) {
            CompoundTag route = (CompoundTag) raw;
            if (!route.hasUUID("FinalPoint") || !route.hasUUID("NextPoint")
                    || rules.transfers.putIfAbsent(route.getUUID("FinalPoint"), route.getUUID("NextPoint")) != null)
                throw new IllegalArgumentException("Invalid or duplicate transfer rule");
        }
        return rules;
    }
}
