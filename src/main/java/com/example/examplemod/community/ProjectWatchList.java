package com.example.examplemod.community;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Stored with this world's workspace data. Each watch begins after the acknowledged revision. */
final class ProjectWatchList {
    private static final int PER_OWNER = 32, PER_PROJECT = 128, TOTAL = 4096;
    private final Map<UUID, Map<UUID, Long>> byProject = new HashMap<>();
    private final Map<UUID, Integer> byOwner = new HashMap<>();
    private int size;

    boolean contains(UUID owner, UUID project) {
        return byProject.getOrDefault(project, Map.of()).containsKey(owner);
    }

    boolean set(UUID owner, UUID project, long revision, boolean value) {
        var watchers = byProject.get(project);
        if (contains(owner, project) == value) return true;
        int count = byOwner.getOrDefault(owner, 0);
        if (value) {
            if (size >= TOTAL || count >= PER_OWNER || watchers != null && watchers.size() >= PER_PROJECT) return false;
            byProject.computeIfAbsent(project, ignored -> new HashMap<>()).put(owner, revision);
            byOwner.put(owner, count + 1);
            size++;
        } else {
            watchers.remove(owner);
            if (watchers.isEmpty()) byProject.remove(project);
            if (count == 1) byOwner.remove(owner); else byOwner.put(owner, count - 1);
            size--;
        }
        return true;
    }

    Set<UUID> recipients(UUID project, long revision) {
        Set<UUID> result = new HashSet<>();
        byProject.getOrDefault(project, Map.of()).forEach((owner, start) -> {
            if (revision > start) result.add(owner);
        });
        return result;
    }

    void finish(UUID project) {
        var watchers = byProject.get(project);
        if (watchers != null) for (UUID owner : List.copyOf(watchers.keySet())) set(owner, project, 0, false);
    }

    ListTag save() {
        var list = new ListTag();
        byProject.forEach((project, watchers) -> watchers.forEach((owner, revision) -> {
            var tag = new CompoundTag();
            tag.putUUID("Project", project);
            tag.putUUID("Owner", owner);
            tag.putLong("After", revision);
            list.add(tag);
        }));
        return list;
    }

    void load(ListTag list) {
        for (int i = 0; i < Math.min(list.size(), TOTAL); i++) {
            CompoundTag tag = list.getCompound(i);
            if (tag.hasUUID("Owner") && tag.hasUUID("Project") && tag.getLong("After") >= 0)
                set(tag.getUUID("Owner"), tag.getUUID("Project"), tag.getLong("After"), true);
        }
    }
}
