package com.example.examplemod.cargo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Durable idempotent receipts, not orders or balances. Recorded only by verified native unpack observation. */
public final class CargoDeliveryReceipts extends SavedData {
    public static final String DATA_NAME = "ccec_cargo_delivery_receipts";
    public record Receipt(UUID parcelId, UUID finalPoint, ResourceLocation dimension, BlockPos inventory,
                          BlockPos packager, long gameTime) {
        public Receipt {
            Objects.requireNonNull(parcelId); Objects.requireNonNull(finalPoint); Objects.requireNonNull(dimension);
            inventory = Objects.requireNonNull(inventory).immutable(); packager = Objects.requireNonNull(packager).immutable();
        }
    }
    private final Map<UUID, Receipt> receipts = new HashMap<>();
    public static CargoDeliveryReceipts get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Cargo receipts require server thread");
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(CargoDeliveryReceipts::new, CargoDeliveryReceipts::load, null), DATA_NAME);
    }
    public static Optional<Receipt> find(MinecraftServer server, UUID parcel) { return get(server).receipt(parcel); }
    public Optional<Receipt> receipt(UUID parcel) { return Optional.ofNullable(receipts.get(Objects.requireNonNull(parcel))); }
    boolean record(Receipt receipt) {
        if (receipts.putIfAbsent(receipt.parcelId(), receipt) != null) return false;
        setDirty(); return true;
    }
    public static CargoDeliveryReceipts load(CompoundTag root, HolderLookup.Provider lookup) {
        if (root.getInt("Version") != 1) throw new IllegalArgumentException("Unsupported cargo receipt version");
        CargoDeliveryReceipts data = new CargoDeliveryReceipts();
        for (Tag raw : root.getList("Receipts", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
            if (!tag.hasUUID("Parcel") || !tag.hasUUID("Final") || dimension == null
                    || !tag.contains("Inventory", Tag.TAG_LONG) || !tag.contains("Packager", Tag.TAG_LONG))
                throw new IllegalArgumentException("Invalid cargo receipt");
            Receipt receipt = new Receipt(tag.getUUID("Parcel"), tag.getUUID("Final"), dimension,
                    BlockPos.of(tag.getLong("Inventory")), BlockPos.of(tag.getLong("Packager")), tag.getLong("Time"));
            if (!data.record(receipt)) throw new IllegalArgumentException("Duplicate cargo receipt UUID");
        }
        data.setDirty(false); return data;
    }
    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider lookup) {
        root.putInt("Version", 1); ListTag entries = new ListTag();
        receipts.values().stream().sorted(Comparator.comparing(Receipt::parcelId)).forEach(receipt -> {
            CompoundTag tag = new CompoundTag(); tag.putUUID("Parcel", receipt.parcelId()); tag.putUUID("Final", receipt.finalPoint());
            tag.putString("Dimension", receipt.dimension().toString()); tag.putLong("Inventory", receipt.inventory().asLong());
            tag.putLong("Packager", receipt.packager().asLong()); tag.putLong("Time", receipt.gameTime()); entries.add(tag);
        });
        root.put("Receipts", entries); return root;
    }
}
