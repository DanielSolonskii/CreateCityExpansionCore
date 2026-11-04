package com.example.examplemod.cargo;

import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.entity.Train;
import com.example.examplemod.cargo.center.CargoOwners;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** World-wide CCEC registrations, independent of Create's own train persistence. */
public final class CargoTrainRegistry extends SavedData {
    public static final String DATA_NAME = "ccec_cargo_trains";

    /** Access lists and PLAYER/CITY ownership extend this registration through CargoOwners. */
    public enum AccessMode { PRIVATE, PUBLIC, RESTRICTED }

    /** Display name is a snapshot; identity is always the original Create Train.id. */
    public record Registration(UUID ownerId, UUID trainId, String displayName, AccessMode accessMode) {
        public Registration {
            Objects.requireNonNull(ownerId);
            Objects.requireNonNull(trainId);
            Objects.requireNonNull(displayName);
            Objects.requireNonNull(accessMode);
        }
    }

    private final Map<UUID, Registration> registrations = new HashMap<>();
    public java.util.List<Registration> registrations() { return java.util.List.copyOf(registrations.values()); }

    public static CargoTrainRegistry get(MinecraftServer server) {
        requireServerThread(server);
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(CargoTrainRegistry::new, CargoTrainRegistry::load, null), DATA_NAME);
    }

    /** Rejects missing trains, non-owners and duplicates; never replaces an existing registration. */
    public static boolean register(MinecraftServer server, UUID ownerId, UUID trainId, AccessMode accessMode) {
        requireServerThread(server);
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(trainId);
        Objects.requireNonNull(accessMode);
        Train train = RailwaySavedData.load(server).getTrains().get(trainId);
        if (train == null || !trainId.equals(train.id) || !ownerId.equals(train.owner)) return false;
        CargoTrainRegistry registry = get(server);
        if (registry.registrations.containsKey(trainId)) return false;
        registry.registrations.put(trainId, new Registration(ownerId, train.id, train.name.getString(), accessMode));
        registry.setDirty();
        return true;
    }

    /** The recorded owner may unregister even after Create has removed the train. */
    public static boolean unregister(MinecraftServer server, UUID ownerId, UUID trainId) {
        Objects.requireNonNull(ownerId);
        Objects.requireNonNull(trainId);
        CargoTrainRegistry registry = get(server);
        Registration registration = registry.registrations.get(trainId);
        if (registration == null || !CargoOwners.get(server).owner("train/" + trainId, registration.ownerId()).manages(server, ownerId)) return false;
        registry.registrations.remove(trainId);
        registry.setDirty();
        return true;
    }

    /** Changes only the CCEC display name; Create's physical train and schedule remain under Create control. */
    public static boolean rename(MinecraftServer server, UUID actor, UUID id, String name) {
        CargoTrainRegistry data = get(server);
        Registration old = data.registrations.get(id);
        if (old == null || name == null || name.isBlank() || name.strip().length() > 96
                || !CargoOwners.get(server).owner("train/" + id, old.ownerId()).manages(server, actor)) return false;
        data.registrations.put(id, new Registration(old.ownerId(), id, name.strip(), old.accessMode()));
        data.setDirty(); return true;
    }

    /** A registration can exist while its Create train is absent. */
    public static Optional<Registration> find(MinecraftServer server, UUID trainId) {
        return get(server).registration(trainId);
    }

    public static boolean trainExists(MinecraftServer server, UUID trainId) {
        requireServerThread(server);
        Objects.requireNonNull(trainId);
        Train train = RailwaySavedData.load(server).getTrains().get(trainId);
        return train != null && trainId.equals(train.id);
    }

    /** Immutable value for inspecting a loaded save; does not imply the train still exists. */
    public Optional<Registration> registration(UUID trainId) {
        return Optional.ofNullable(registrations.get(Objects.requireNonNull(trainId)));
    }

    private static void requireServerThread(MinecraftServer server) {
        if (!Objects.requireNonNull(server).isSameThread())
            throw new IllegalStateException("Cargo train registry must be accessed on the server thread");
    }

    public static CargoTrainRegistry load(CompoundTag root, HolderLookup.Provider lookup) {
        CargoTrainRegistry registry = new CargoTrainRegistry();
        int version = root.getInt("Version");
        if (version != 1) throw new IllegalArgumentException("Unsupported cargo train registry version: " + version);
        for (Tag raw : root.getList("Trains", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            if (!tag.hasUUID("Owner") || !tag.hasUUID("Train"))
                throw new IllegalArgumentException("Cargo train registration has no valid owner/train UUID");
            Registration entry = new Registration(tag.getUUID("Owner"), tag.getUUID("Train"),
                    tag.getString("Name"), AccessMode.valueOf(tag.getString("Access")));
            if (registry.registrations.putIfAbsent(entry.trainId(), entry) != null)
                throw new IllegalArgumentException("Duplicate cargo train UUID: " + entry.trainId());
        }
        // Missing Create trains are deliberately retained. Reassembly never transfers a registration by name.
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider lookup) {
        root.putInt("Version", 1);
        ListTag list = new ListTag();
        registrations.values().stream().sorted(java.util.Comparator.comparing(Registration::trainId)).forEach(entry -> {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Owner", entry.ownerId());
            tag.putUUID("Train", entry.trainId());
            tag.putString("Name", entry.displayName());
            tag.putString("Access", entry.accessMode().name());
            list.add(tag);
        });
        root.put("Trains", list);
        return root;
    }
}
