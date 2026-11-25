package com.example.examplemod.city;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** Public news in chronological order. Membership columns belong to their local edition. */
public final class CityGazette extends SavedData {
    public enum Kind { FOUNDED, JOINED, ALLIANCE, LEFT, NEWCOMER, FIRST_SERVICE, LEVEL, LEADER, ALLY, CITY_REGISTER }
    public record Story(Kind kind, String subject, String place, String detail, long time) {
        public Story(Kind kind, String subject, String place, long time) { this(kind, subject, place, "", time); }
    }
    private record Entry(UUID city, UUID actor, Story story) {}
    private static final int ARCHIVE = 1024;
    public static final int EDITION = 24;
    private final List<Entry> entries = new ArrayList<>();
    private long revision;
    private boolean imported;

    public static CityGazette get(MinecraftServer server) {
        var paper = server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(CityGazette::new, CityGazette::load, null), "ccec_city_gazette");
        if (!paper.imported) paper.importArchive(server);
        return paper;
    }

    public long revision() { return revision; }

    public void newcomer(UUID player, String name, long time) {
        publish(Kind.NEWCOMER, null, player, name, "", time);
    }

    public void publish(Kind kind, UUID city, UUID actor, String subject, String place, long time) {
        publish(kind,city,actor,subject,place,"",time);
    }

    public void publish(Kind kind, UUID city, UUID actor, String subject, String place, String detail, long time) {
        entries.add(new Entry(city, actor, new Story(kind, bounded(subject), bounded(place), bounded(detail), time)));
        if (entries.size() > ARCHIVE) entries.removeFirst();
        revision++;
        setDirty();
    }

    private void importArchive(MinecraftServer server) {
        var cities = CityManager.get(server.overworld());
        var names = new java.util.HashMap<UUID,String>();
        java.util.function.Function<UUID,String> name = id -> names.computeIfAbsent(id,
                key -> com.example.examplemod.auth.PlayerNames.name(server,key));
        importArchive(cities,com.example.examplemod.community.CommunityData.get(server).audit,name);
    }

    void importArchive(CityManager cities, List<CompoundTag> audit, java.util.function.Function<UUID,String> name) {
        if (imported) return;
        var historical = new ArrayList<Entry>();
        for (var row : audit) {
            Kind kind = switch (row.getString("Action")) {
                case "joined" -> Kind.JOINED;
                case "left", "kicked" -> Kind.LEFT;
                case "ownership_transferred" -> Kind.LEADER;
                default -> null;
            };
            if (kind == null || !row.hasUUID("City") || !row.hasUUID("Actor") || row.getLong("Time") <= 0) continue;
            var city = cities.getCity(row.getUUID("City"));
            if (city == null) continue;
            var actor = row.getUUID("Actor");
            var target = row.hasUUID("Target") ? row.getUUID("Target") : actor;
            if (kind == Kind.LEADER && !row.hasUUID("Target")) continue;
            var subject = kind == Kind.LEFT ? target : actor;
            long time = row.getLong("Time");
            if (entries.stream().anyMatch(e -> city.getId().equals(e.city) && e.actor.equals(subject) &&
                    e.story.kind == kind && Math.abs(e.story.time-time) < 5000)) continue;
            historical.add(new Entry(city.getId(),subject,new Story(kind,bounded(name.apply(subject)),bounded(city.getName()),
                    kind == Kind.LEADER ? bounded(name.apply(target)) : "",time)));
        }
        entries.addAll(historical);
        for (var city : cities.getCities()) {
            if (entries.stream().noneMatch(e -> city.getId().equals(e.city))) {
                entries.add(new Entry(city.getId(),city.getOwner(),new Story(Kind.CITY_REGISTER,bounded(city.getName()),
                        bounded(name.apply(city.getOwner())),Integer.toString(city.getLevel()),0)));
            }
        }
        entries.sort(java.util.Comparator.comparingLong(e -> e.story.time));
        if (entries.size() > ARCHIVE) entries.subList(0,entries.size()-ARCHIVE).clear();
        imported = true;
        revision++;
        setDirty();
    }

    private static String bounded(String name) {
        return name.substring(0, Math.min(128, name.length()));
    }

    public List<Story> edition(UUID city) {
        return entries.reversed().stream().filter(e -> e.city == null || city.equals(e.city) || e.story.kind == Kind.ALLY ||
                e.story.kind == Kind.FOUNDED || e.story.kind == Kind.ALLIANCE)
                .limit(EDITION).map(Entry::story).toList();
    }

    public static CityGazette load(CompoundTag root, HolderLookup.Provider registries) {
        var paper = new CityGazette();
        paper.imported = root.getBoolean("ArchiveImported");
        var rows = root.getList("Stories", Tag.TAG_COMPOUND);
        for (int i = Math.max(0, rows.size()-ARCHIVE); i < rows.size(); i++) {
            var t = rows.getCompound(i);
            if (!t.hasUUID("Actor")) continue;
            Kind kind;
            try { kind = Kind.valueOf(t.getString("Kind")); }
            catch (IllegalArgumentException unknown) { continue; }
            if (kind != Kind.NEWCOMER && kind != Kind.FIRST_SERVICE && !t.hasUUID("City")) continue;
            paper.entries.add(new Entry(t.hasUUID("City") ? t.getUUID("City") : null, t.getUUID("Actor"), new Story(kind,
                    bounded(t.getString("Subject")), bounded(t.getString("Place")), bounded(t.getString("Detail")), t.getLong("Time"))));
        }
        return paper;
    }

    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putBoolean("ArchiveImported", imported);
        var rows = new ListTag();
        for (var e : entries) {
            var t = new CompoundTag();
            if (e.city != null) t.putUUID("City", e.city);
            t.putUUID("Actor", e.actor);
            t.putString("Kind", e.story.kind.name());
            t.putString("Subject", e.story.subject);
            t.putString("Place", e.story.place);
            t.putString("Detail", e.story.detail);
            t.putLong("Time", e.story.time);
            rows.add(t);
        }
        root.put("Stories", rows);
        return root;
    }
}
