package com.example.examplemod.cargo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;

import java.nio.file.Files;
import java.util.UUID;

/** Real SavedData codec and compressed disk round-trip; no Minecraft server is simulated. */
public final class CargoTrainRegistryCheck {
    private static int checks;

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejects(CompoundTag root, String message) {
        boolean rejected = false;
        try { CargoTrainRegistry.load(root, null); }
        catch (IllegalArgumentException failure) { rejected = true; }
        require(rejected, message);
    }

    public static void main(String[] args) throws Exception {
        UUID owner = UUID.randomUUID();
        CompoundTag root = new CompoundTag();
        root.putInt("Version", 1);
        ListTag trains = new ListTag();
        UUID[] ids = new UUID[CargoTrainRegistry.AccessMode.values().length];
        for (var mode : CargoTrainRegistry.AccessMode.values()) {
            UUID id = ids[mode.ordinal()] = UUID.randomUUID();
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Owner", owner);
            entry.putUUID("Train", id);
            entry.putString("Name", "Грузовой состав №1");
            entry.putString("Access", mode.name());
            trains.add(entry);
        }
        root.put("Trains", trains);
        var data = CargoTrainRegistry.load(root, null);
        var path = Files.createTempFile("ccec-cargo-registry-", ".dat");
        try {
            NbtIo.writeCompressed(data.save(new CompoundTag(), null), path);
            var restored = CargoTrainRegistry.load(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()), null);
            for (var mode : CargoTrainRegistry.AccessMode.values()) {
                var entry = restored.registration(ids[mode.ordinal()]).orElseThrow();
                require(entry.ownerId().equals(owner), "owner survives disk reload");
                require(entry.trainId().equals(ids[mode.ordinal()]), "Create identity survives disk reload");
                require(entry.accessMode() == mode, "access mode survives disk reload");
                require(entry.displayName().equals("Грузовой состав №1"), "Unicode name survives disk reload");
            }
            require(restored.registration(UUID.randomUUID()).isEmpty(), "new UUID never inherits same-name registration");
            require(restored.registration(ids[0]).isPresent(), "offline records retained without querying live trains during load");
        } finally { Files.deleteIfExists(path); }
        var invalidMode = root.copy();
        invalidMode.getList("Trains", 10).getCompound(0).putString("Access", "UNKNOWN");
        rejects(invalidMode, "unknown mode cannot silently become public");
        var noOwner = root.copy();
        noOwner.getList("Trains", 10).getCompound(0).remove("Owner");
        rejects(noOwner, "missing owner rejected");
        var duplicate = root.copy();
        duplicate.getList("Trains", 10).add(trains.getCompound(0).copy());
        rejects(duplicate, "duplicate UUID cannot overwrite owner");
        var future = root.copy();
        future.putInt("Version", 2);
        rejects(future, "unknown save version rejected");
        var empty = new CargoTrainRegistry();
        require(CargoTrainRegistry.load(empty.save(new CompoundTag(), null), null).registration(ids[0]).isEmpty(), "empty save reload");
        System.out.println("PASS: " + checks + " cargo train persistence and identity checks");
    }
}
