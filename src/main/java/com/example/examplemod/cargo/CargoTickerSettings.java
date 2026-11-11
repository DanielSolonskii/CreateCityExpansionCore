package com.example.examplemod.cargo;

import net.minecraft.nbt.CompoundTag;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/** Explicit first hop belongs to the network on which it was configured; never inferred from final point. */
record CargoTickerSettings(UUID owner, UUID firstPoint, UUID network) {
    Optional<CargoAddress> address(UUID finalPoint, UUID currentNetwork, Predicate<UUID> exists) {
        if (firstPoint == null || network == null || !network.equals(currentNetwork) || finalPoint == null
                || !exists.test(firstPoint) || !exists.test(finalPoint)) return Optional.empty();
        return Optional.of(new CargoAddress(firstPoint, finalPoint));
    }
    CompoundTag save() {
        CompoundTag tag = new CompoundTag(); tag.putInt("Version", 1);
        if (owner != null) tag.putUUID("Owner", owner);
        if (firstPoint != null) { tag.putUUID("FirstPoint", firstPoint); tag.putUUID("Network", network); }
        return tag;
    }
    static CargoTickerSettings load(CompoundTag tag) {
        if (tag.getInt("Version") != 1 || tag.hasUUID("FirstPoint") != tag.hasUUID("Network"))
            throw new IllegalArgumentException("Invalid Cargo Ticker settings");
        return new CargoTickerSettings(tag.hasUUID("Owner") ? tag.getUUID("Owner") : null,
                tag.hasUUID("FirstPoint") ? tag.getUUID("FirstPoint") : null,
                tag.hasUUID("Network") ? tag.getUUID("Network") : null);
    }
}
