package com.example.examplemod.city;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongFunction;

/** Leases for individual views. One bounded rotating pass both expires leases and notices
 * published versions. An absent viewer therefore cannot retain dirty notifications. */
final class TerrainInterest {
    static final int PER_PLAYER = 8192, GLOBAL = 32768;
    static final long TTL = 1800;
    record Address(UUID player, UUID view, TerrainPyramid.Key tile) {}
    private record Lease(long until, long sent) {}
    interface Notify { void changed(Address address, long version); }

    private final LinkedHashMap<Address, Lease> leases = new LinkedHashMap<>();
    private final Map<UUID, LinkedHashSet<Address>> viewers = new HashMap<>();
    private final Map<TerrainPyramid.Key, Integer> watchedTiles = new HashMap<>();

    void watch(UUID player, UUID view, TerrainPyramid.Key tile, long tick, long version) {
        Address address = new Address(player, view, tile);
        Lease existing = leases.get(address);
        if (existing != null) {
            leases.put(address, new Lease(tick + TTL, Math.max(existing.sent, version)));
            return;
        }
        if (leases.size() >= GLOBAL) remove(leases.firstEntry().getKey());
        var keys = viewers.computeIfAbsent(player, ignored -> new LinkedHashSet<>());
        if (keys.size() >= PER_PLAYER) remove(keys.getFirst());
        keys.add(address);
        watchedTiles.merge(tile, 1, Integer::sum);
        leases.put(address, new Lease(tick + TTL, version));
    }

    void unwatch(UUID player, UUID view) {
        var keys = viewers.get(player);
        if (keys != null) for (Address address : java.util.List.copyOf(keys)) {
            if (address.view.equals(view)) remove(address);
        }
    }

    private void remove(Address address) {
        leases.remove(address);
        var keys = viewers.get(address.player);
        keys.remove(address);
        if (keys.isEmpty()) viewers.remove(address.player);
        watchedTiles.computeIfPresent(address.tile, (key, count) -> count == 1 ? null : count - 1);
    }

    boolean covers(TerrainPyramid.Key key) { return watchedTiles.containsKey(key); }
    int size() { return leases.size(); }

    void advance(long tick, int checks, int sends, ToLongFunction<TerrainPyramid.Key> versions, Notify notify) {
        int count = Math.min(leases.size(), checks);
        // Access order for this pass is independent of player iteration order and render rate.
        for (int i = 0; i < count && sends > 0 && !leases.isEmpty(); i++) {
            var entry = leases.firstEntry();
            var address = entry.getKey();
            var lease = entry.getValue();
            if (tick >= lease.until) {
                remove(address);
                continue;
            }
            long version = versions.applyAsLong(address.tile);
            if (version >= 0 && version > lease.sent) {
                leases.putLast(address, new Lease(lease.until, version));
                // The send-side access check can synchronously close this view.
                notify.changed(address, version);
                sends--;
            } else {
                leases.putLast(address, lease);
            }
        }
    }
}
