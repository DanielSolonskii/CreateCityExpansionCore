package com.example.examplemod.cargo;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;
import java.util.stream.IntStream;

/** Wraps the FULL native ItemStackHandler NBT, including every package component. */
final class CargoRouterData {
    static final int SLOTS = 18;
    record Loaded(UUID owner, CompoundTag packages, boolean[] ready) {}

    private CargoRouterData() {}

    static CompoundTag save(UUID owner, CompoundTag packages, boolean[] ready) {
        if (ready.length != SLOTS || packages.getInt("Size") != SLOTS)
            throw new IllegalArgumentException("Invalid cargo router buffer size");
        CompoundTag tag = new CompoundTag();
        tag.putInt("Version", 1);
        if (owner != null) tag.putUUID("Owner", owner);
        tag.put("Packages", packages.copy());
        tag.putIntArray("Ready", IntStream.range(0, SLOTS).filter(slot -> ready[slot]).toArray());
        return tag;
    }

    static Loaded load(CompoundTag tag) {
        if (tag.getInt("Version") != 1 || tag.getCompound("Packages").getInt("Size") != SLOTS)
            throw new IllegalArgumentException("Unsupported cargo router save version or buffer size");
        boolean[] ready = new boolean[SLOTS];
        for (int slot : tag.getIntArray("Ready")) {
            if (slot < 0 || slot >= SLOTS) throw new IllegalArgumentException("Invalid cargo router ready slot");
            ready[slot] = true;
        }
        return new Loaded(tag.hasUUID("Owner") ? tag.getUUID("Owner") : null,
                tag.getCompound("Packages").copy(), ready);
    }
}
