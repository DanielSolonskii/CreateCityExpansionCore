package com.example.examplemod.passenger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Only explicit publications and schedule ownership survive a restart. Observed timings require a fresh run. */
final class PassengerData extends SavedData {
    static final int MAX_LINES = 12, MAX_PLATFORMS = 96;
    record Platform(UUID id, UUID owner, ResourceLocation dimension, BlockPos entrance, BlockPos stationPos, UUID graph, UUID feature) {
        Platform withFeature(UUID feature) { return new Platform(id,owner,dimension,entrance,stationPos,graph,feature); }
    }
    static final class Line {
        final UUID id, owner;
        final String name;
        String description = "";
        final List<UUID> stops = new ArrayList<>();
        UUID train, generation, chosenTrain;
        UUID trainOperator;
        net.minecraft.world.item.DyeColor color = net.minecraft.world.item.DyeColor.ORANGE;
        CompoundTag schedule;
        PassengerTimetable clock;
        final long[] verifiedPlatforms = new long[PassengerTimetable.MAX_STOPS];
        String status = "checking";
        boolean verifiedDirection;
        boolean firstServicePending = true;
        Line(UUID id, UUID owner, String name) {
            if (name.isBlank() || name.length() > 64) throw new IllegalArgumentException("line_name");
            this.id = id;
            this.owner = owner;
            this.trainOperator = owner;
            this.name = name;
        }
        void changeDescription(String expected, String proposed) {
            if (!validDescription(proposed)) throw new IllegalArgumentException("invalid_description");
            String next = proposed.strip();
            if (!description.equals(expected) && !description.equals(next)) throw new IllegalArgumentException("stale");
            description = next;
        }
        private static boolean validDescription(String text) {
            return text != null && text.length() <= PassengerService.MAX_DESCRIPTION &&
                    text.codePoints().noneMatch(c -> Character.isISOControl(c) || c == 0xa7);
        }
        void release() {
            train = generation = null;
            trainOperator = owner;
            schedule = null;
            clock = null;
            Arrays.fill(verifiedPlatforms,0);
            status = "checking";
            verifiedDirection = false;
        }
    }

    final Map<UUID, Platform> platforms = new LinkedHashMap<>();
    final Map<UUID, Line> lines = new LinkedHashMap<>();
    final Set<UUID> retainedAssignments = new HashSet<>();
    // A newer format or a damaged document is preserved verbatim and never overwritten.
    CompoundTag unavailable;
    static PassengerData get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Passenger data belongs to the server thread");
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PassengerData::new, PassengerData::load, null), "ccec_passenger_v1");
    }
    static PassengerData load(CompoundTag root, HolderLookup.Provider lookup) {
        var data = new PassengerData();
        try {
            if (root.getInt("Format") != 1) throw new IllegalArgumentException();
            for (var raw : list(root, "Platforms", Tag.TAG_COMPOUND, MAX_PLATFORMS)) {
                var t = (CompoundTag) raw;
                var p = new Platform(t.getUUID("Station"), t.getUUID("Owner"), ResourceLocation.parse(t.getString("Dimension")),
                        BlockPos.of(t.getLong("Entrance")), BlockPos.of(t.getLong("StationPos")), t.getUUID("Graph"),
                        t.hasUUID("Feature") ? t.getUUID("Feature") : null);
                if (!t.contains("Entrance",Tag.TAG_LONG) || !t.contains("StationPos",Tag.TAG_LONG) ||
                        data.platforms.putIfAbsent(p.id(), p) != null) throw new IllegalArgumentException();
            }
            var assigned = new HashSet<UUID>();
            for (var raw : list(root, "Lines", Tag.TAG_COMPOUND, MAX_LINES)) {
                var t = (CompoundTag) raw;
                var line = new Line(t.getUUID("Id"), t.getUUID("Owner"), t.getString("Name"));
                line.firstServicePending = t.getBoolean("FirstServicePending");
                String description = t.getString("Description");
                if (Line.validDescription(description)) line.description = description.strip();
                line.color = net.minecraft.world.item.DyeColor.byName(t.getString("Color"), line.color);
                if (t.hasUUID("ChosenTrain")) line.chosenTrain=t.getUUID("ChosenTrain");
                for (var node : list(t, "Stops", Tag.TAG_INT_ARRAY, PassengerTimetable.MAX_STOPS)) {
                    var id = NbtUtils.loadUUID(node);
                    if (!data.platforms.containsKey(id) || line.stops.contains(id)) throw new IllegalArgumentException();
                    line.stops.add(id);
                }
                if (t.contains("Train")) {
                    line.train = t.getUUID("Train");
                    if (t.hasUUID("TrainOperator")) line.trainOperator = t.getUUID("TrainOperator");
                    line.generation = t.getUUID("Generation");
                    line.schedule = t.getCompound("Schedule").copy();
                    line.clock = new PassengerTimetable(line.stops.size());
                    if (!assigned.add(line.train) || line.schedule.isEmpty()) throw new IllegalArgumentException();
                }
                if (data.lines.putIfAbsent(line.id, line) != null) throw new IllegalArgumentException();
            }
        } catch (RuntimeException invalid) {
            data.platforms.clear();
            data.lines.clear();
            var rows = root.getList("Lines",Tag.TAG_COMPOUND);
            for (int i = 0; i < Math.min(rows.size(),MAX_LINES); i++) {
                var row = rows.getCompound(i);
                if (row.hasUUID("Train")) data.retainedAssignments.add(row.getUUID("Train"));
            }
            data.unavailable = root.copy();
        }
        return data;
    }
    private static ListTag list(CompoundTag t, String key, int type, int max) {
        if (!(t.get(key) instanceof ListTag list) || list.size() > max || !list.isEmpty() && list.getElementType() != type)
            throw new IllegalArgumentException();
        return list;
    }
    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider lookup) {
        if (unavailable != null) return unavailable.copy();
        root.putInt("Format", 1);
        var places = new ListTag();
        for (var p : platforms.values()) {
            var tag = new CompoundTag();
            tag.putUUID("Station", p.id()); tag.putUUID("Owner", p.owner());
            tag.putString("Dimension", p.dimension().toString()); tag.putLong("Entrance", p.entrance().asLong());
            tag.putLong("StationPos",p.stationPos().asLong()); tag.putUUID("Graph",p.graph());
            if (p.feature() != null) tag.putUUID("Feature",p.feature());
            places.add(tag);
        }
        root.put("Platforms", places);
        var services = new ListTag();
        for (var line : lines.values()) {
            var tag = new CompoundTag();
            tag.putBoolean("FirstServicePending", line.firstServicePending);
            tag.putUUID("Id", line.id); tag.putUUID("Owner", line.owner); tag.putString("Name", line.name);
            if (!line.description.isEmpty()) tag.putString("Description", line.description);
            tag.putString("Color", line.color.getName());
            if (line.chosenTrain!=null) tag.putUUID("ChosenTrain",line.chosenTrain);
            var stops = new ListTag();
            for (var stop : line.stops) stops.add(NbtUtils.createUUID(stop));
            tag.put("Stops", stops);
            if (line.train != null) {
                tag.putUUID("Train", line.train); tag.putUUID("Generation", line.generation);
                tag.putUUID("TrainOperator",line.trainOperator);
                tag.put("Schedule", line.schedule.copy());
            }
            services.add(tag);
        }
        root.put("Lines", services);
        return root;
    }
}
