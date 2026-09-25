package com.example.examplemod.railway;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RailwayManager extends SavedData {
    private static final String DATA_NAME = "createcityexpansioncore_railway";
    private static final int FORMAT_VERSION = 1;

    private final Map<UUID, CityRailStation> stations = new LinkedHashMap<>();
    private final Map<UUID, CityFleetTrain> trains = new LinkedHashMap<>();
    private final Map<UUID, RailLine> lines = new LinkedHashMap<>();
    private final Map<UUID, UUID> primaryProjectStations = new LinkedHashMap<>();

    public static RailwayManager get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RailwayManager::new, RailwayManager::load), DATA_NAME);
    }

    public RailwayManager() {}

    public List<CityRailStation> stationsForCity(UUID cityId) {
        return stations.values().stream().filter(value -> cityId.equals(value.cityId())).toList();
    }
    public List<CityRailStation> stationsForCityIdsAndPersonal() { return List.copyOf(stations.values()); }
    public List<CityRailStation> personalStations(UUID playerId) {
        return stations.values().stream()
                .filter(value -> value.cityId() == null && value.registeredBy().equals(playerId)).toList();
    }
    public List<CityFleetTrain> trainsForCity(UUID cityId) {
        return trains.values().stream().filter(value -> cityId.equals(value.cityId())).toList();
    }
    public List<CityFleetTrain> trainsForCityIdsAndPersonal() { return List.copyOf(trains.values()); }
    public List<CityFleetTrain> personalTrains(UUID playerId) {
        return trains.values().stream()
                .filter(value -> value.cityId() == null && value.registeredBy().equals(playerId)).toList();
    }
    public List<RailLine> linesForCity(UUID cityId) {
        return lines.values().stream().filter(value -> value.cityId().equals(cityId)).toList();
    }
    @Nullable public CityRailStation station(UUID id) { return stations.get(id); }
    @Nullable public CityFleetTrain train(UUID id) { return trains.get(id); }
    @Nullable public RailLine line(UUID id) { return lines.get(id); }
    @Nullable public CityRailStation stationByCreateId(UUID id) {
        return stations.values().stream().filter(value -> value.createStationId().equals(id)).findFirst().orElse(null);
    }
    @Nullable public CityFleetTrain trainByCreateId(UUID id) {
        return trains.values().stream().filter(value -> value.createTrainId().equals(id)).findFirst().orElse(null);
    }

    public CityRailStation registerStation(UUID createId, UUID playerId, String name,
                                           ResourceLocation dimension, BlockPos pos) {
        CityRailStation existing = stationByCreateId(createId);
        if (existing != null) return existing;
        CityRailStation created = new CityRailStation(UUID.randomUUID(), createId, playerId, null,
                RailwayTypes.StationType.MIXED, name, dimension, pos);
        stations.put(created.registrationId(), created);
        setDirty();
        return created;
    }

    public CityFleetTrain registerTrain(UUID createId, UUID playerId, String name) {
        CityFleetTrain existing = trainByCreateId(createId);
        if (existing != null) return existing;
        CityFleetTrain created = new CityFleetTrain(UUID.randomUUID(), createId, playerId, null, null,
                RailwayTypes.TrainType.MIXED, name, 0, "", false);
        trains.put(created.fleetTrainId(), created);
        setDirty();
        return created;
    }

    public boolean attachStationToCity(UUID registrationId, UUID playerId, UUID cityId) {
        CityRailStation station = stations.get(registrationId);
        if (station == null || station.cityId() != null || !station.registeredBy().equals(playerId)) return false;
        station.setCityId(cityId); setDirty(); return true;
    }

    public boolean attachTrainToCity(UUID fleetTrainId, UUID playerId, UUID cityId) {
        CityFleetTrain train = trains.get(fleetTrainId);
        if (train == null || train.cityId() != null || !train.registeredBy().equals(playerId)) return false;
        train.setCityId(cityId); setDirty(); return true;
    }

    public RailLine createLine(UUID cityId, String name, RailwayTypes.LineType type,
                               boolean cyclic, List<RailLineStop> stops) {
        if (stops.size() < 2 || stops.size() > 64) throw new IllegalArgumentException("RailLine needs 2..64 stops");
        RailLine line = new RailLine(UUID.randomUUID(), cityId, name, type, cyclic, 1, stops);
        lines.put(line.lineId(), line); setDirty(); return line;
    }

    public void removeLine(UUID lineId) {
        if (lines.remove(lineId) == null) return;
        trains.values().stream().filter(value -> lineId.equals(value.activeLineId())).forEach(CityFleetTrain::unassign);
        setDirty();
    }

    public void removeTrain(UUID fleetTrainId) { if (trains.remove(fleetTrainId) != null) setDirty(); }
    public boolean rebindStation(UUID targetId, UUID replacementId, UUID playerId) {
        CityRailStation target = stations.get(targetId), replacement = stations.get(replacementId);
        if (target == null || replacement == null || target.cityId() == null || replacement.cityId() != null
                || !replacement.registeredBy().equals(playerId)
                || stationByCreateId(replacement.createStationId()) != replacement) return false;
        target.rebind(replacement.createStationId(), replacement.lastKnownName(), replacement.lastKnownDimension(), replacement.lastKnownPos());
        stations.remove(replacementId);
        lines.values().stream()
                .filter(line -> line.stops().stream()
                        .anyMatch(stop -> stop.stationRegistrationId().equals(targetId)))
                .forEach(line -> line.update(line.name(), line.type(), line.cyclic(), line.stops()));
        setDirty(); return true;
    }
    public boolean rebindTrain(UUID targetId, UUID replacementId, UUID playerId) {
        CityFleetTrain target = trains.get(targetId), replacement = trains.get(replacementId);
        if (target == null || replacement == null || target.cityId() == null || replacement.cityId() != null
                || !replacement.registeredBy().equals(playerId)
                || trainByCreateId(replacement.createTrainId()) != replacement) return false;
        target.rebind(replacement.createTrainId(), replacement.lastKnownName());
        target.setAutomationSuspended(target.activeLineId() != null); trains.remove(replacementId); setDirty(); return true;
    }
    public void removeStationFromCity(UUID registrationId) {
        CityRailStation station = stations.get(registrationId);
        if (station != null && station.cityId() != null) { station.setCityId(null); setDirty(); }
    }
    public void linkProject(UUID projectId, @Nullable UUID stationRegistrationId) {
        if (stationRegistrationId == null) primaryProjectStations.remove(projectId);
        else primaryProjectStations.put(projectId, stationRegistrationId);
        setDirty();
    }
    @Nullable public UUID projectStation(UUID projectId) { return primaryProjectStations.get(projectId); }
    public boolean isStationLinkedToProject(UUID stationId) {
        return primaryProjectStations.containsValue(stationId);
    }
    @Nullable public UUID projectForStation(UUID stationId) {
        return primaryProjectStations.entrySet().stream()
                .filter(entry -> entry.getValue().equals(stationId))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }
    public void markChanged() { setDirty(); }

    public void removeCity(UUID cityId) {
        lines.values().removeIf(line -> line.cityId().equals(cityId));
        stations.values().removeIf(station -> cityId.equals(station.cityId()));
        trains.values().removeIf(train -> cityId.equals(train.cityId()));
        primaryProjectStations.entrySet().removeIf(entry -> stations.get(entry.getValue()) == null);
        setDirty();
    }

    private static RailwayManager load(CompoundTag root, HolderLookup.Provider registries) {
        RailwayManager manager = new RailwayManager();
        if (root.getInt("FormatVersion") > FORMAT_VERSION) return manager;
        for (Tag raw : root.getList("Stations", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag = (CompoundTag) raw;
                ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
                if (dimension == null) continue;
                CityRailStation station = new CityRailStation(tag.getUUID("RegistrationId"), tag.getUUID("CreateId"),
                        tag.getUUID("RegisteredBy"), tag.contains("CityId") ? tag.getUUID("CityId") : null,
                        readEnum(tag, "Type", RailwayTypes.StationType.class, RailwayTypes.StationType.MIXED),
                        tag.getString("Name"), dimension,
                        NbtUtils.readBlockPos(tag, "Pos").orElse(BlockPos.ZERO));
                manager.stations.put(station.registrationId(), station);
            } catch (RuntimeException ignored) {}
        }
        for (Tag raw : root.getList("Trains", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag = (CompoundTag) raw;
                CityFleetTrain train = new CityFleetTrain(tag.getUUID("FleetId"), tag.getUUID("CreateId"),
                        tag.getUUID("RegisteredBy"), tag.contains("CityId") ? tag.getUUID("CityId") : null,
                        tag.contains("LineId") ? tag.getUUID("LineId") : null,
                        readEnum(tag, "Type", RailwayTypes.TrainType.class, RailwayTypes.TrainType.MIXED),
                        tag.getString("Name"), tag.getInt("Revision"), tag.getString("Fingerprint"),
                        tag.getBoolean("Suspended"));
                manager.trains.put(train.fleetTrainId(), train);
            } catch (RuntimeException ignored) {}
        }
        for (Tag raw : root.getList("Lines", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag = (CompoundTag) raw;
                List<RailLineStop> stops = new ArrayList<>();
                for (Tag stopRaw : tag.getList("Stops", Tag.TAG_COMPOUND)) {
                    CompoundTag stop = (CompoundTag) stopRaw;
                    stops.add(new RailLineStop(stop.getUUID("Station"),
                            readEnum(stop, "Mode", RailwayTypes.StopMode.class, RailwayTypes.StopMode.IMMEDIATE),
                            stop.getInt("WaitSeconds")));
                }
                if (stops.size() < 2) continue;
                RailLine line = new RailLine(tag.getUUID("LineId"), tag.getUUID("CityId"), tag.getString("Name"),
                        readEnum(tag, "Type", RailwayTypes.LineType.class, RailwayTypes.LineType.MIXED),
                        tag.getBoolean("Cyclic"), tag.getInt("Revision"), stops);
                manager.lines.put(line.lineId(), line);
            } catch (RuntimeException ignored) {}
        }
        for (Tag raw : root.getList("ProjectStations", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            if (tag.hasUUID("Project") && tag.hasUUID("Station"))
                manager.primaryProjectStations.put(tag.getUUID("Project"), tag.getUUID("Station"));
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("FormatVersion", FORMAT_VERSION);
        ListTag stationTags = new ListTag();
        for (CityRailStation station : stations.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("RegistrationId", station.registrationId()); tag.putUUID("CreateId", station.createStationId());
            tag.putUUID("RegisteredBy", station.registeredBy()); if (station.cityId() != null) tag.putUUID("CityId", station.cityId());
            tag.putInt("Type", station.type().ordinal()); tag.putString("Name", station.lastKnownName());
            tag.putString("Dimension", station.lastKnownDimension().toString()); tag.put("Pos", NbtUtils.writeBlockPos(station.lastKnownPos()));
            stationTags.add(tag);
        }
        root.put("Stations", stationTags);
        ListTag trainTags = new ListTag();
        for (CityFleetTrain train : trains.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("FleetId", train.fleetTrainId()); tag.putUUID("CreateId", train.createTrainId());
            tag.putUUID("RegisteredBy", train.registeredBy()); if (train.cityId() != null) tag.putUUID("CityId", train.cityId());
            if (train.activeLineId() != null) tag.putUUID("LineId", train.activeLineId());
            tag.putInt("Type", train.type().ordinal()); tag.putString("Name", train.lastKnownName());
            tag.putInt("Revision", train.compiledLineRevision()); tag.putString("Fingerprint", train.compiledScheduleFingerprint());
            tag.putBoolean("Suspended", train.automationSuspended()); trainTags.add(tag);
        }
        root.put("Trains", trainTags);
        ListTag lineTags = new ListTag();
        for (RailLine line : lines.values()) {
            CompoundTag tag = new CompoundTag(); tag.putUUID("LineId", line.lineId()); tag.putUUID("CityId", line.cityId());
            tag.putString("Name", line.name()); tag.putInt("Type", line.type().ordinal()); tag.putBoolean("Cyclic", line.cyclic());
            tag.putInt("Revision", line.revision()); ListTag stopTags = new ListTag();
            for (RailLineStop stop : line.stops()) {
                CompoundTag value = new CompoundTag(); value.putUUID("Station", stop.stationRegistrationId());
                value.putInt("Mode", stop.mode().ordinal()); value.putInt("WaitSeconds", stop.waitSeconds()); stopTags.add(value);
            }
            tag.put("Stops", stopTags); lineTags.add(tag);
        }
        root.put("Lines", lineTags);
        ListTag links = new ListTag();
        primaryProjectStations.forEach((project, station) -> { CompoundTag tag = new CompoundTag(); tag.putUUID("Project", project); tag.putUUID("Station", station); links.add(tag); });
        root.put("ProjectStations", links);
        return root;
    }

    private static <T extends Enum<T>> T readEnum(CompoundTag tag, String key, Class<T> type, T fallback) {
        int ordinal = tag.getInt(key); T[] values = type.getEnumConstants();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }
}
