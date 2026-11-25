package com.example.examplemod.citymap;

import com.example.examplemod.city.CityPlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.function.BiPredicate;

final class CityMapBook extends SavedData {
    static final int MAX = 512, PER_CITY = 128;
    private record Entry(CityMapFeature feature, Set<CityPlot> footprint) {
        boolean ownedByCity(BiPredicate<UUID, CityPlot> owns) {
            return footprint.stream().allMatch(plot -> owns.test(feature.city(), plot));
        }
    }

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private long lastRevision;
    private CompoundTag unreadable;

    static CityMapBook get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(CityMapBook::new, CityMapBook::load, null), "ccec_city_cartography_v1");
    }

    boolean available() { return unreadable == null; }

    boolean canRevise() { return available() && lastRevision < Long.MAX_VALUE; }

    boolean hasLinks(UUID id) {
        var entry = entries.get(id);
        return entry != null && !entry.feature.links().equals(CityMapFeature.Links.EMPTY);
    }

    void platformsChanged(UUID station) {
        var entry = entries.get(station);
        if (entry == null || entry.feature.kind() != CityMapFeature.Kind.STATION || !canRevise()) return;
        entries.put(station, new Entry(entry.feature.withRevision(++lastRevision), entry.footprint));
        setDirty();
    }

    List<CityMapFeature> visible(ResourceLocation dimension, UUID city, BiPredicate<UUID, CityPlot> owns) {
        return entries.values().stream()
                .filter(e -> e.feature.dimension().equals(dimension) && (city == null || city.equals(e.feature.city())))
                .filter(e -> e.ownedByCity(owns)).map(Entry::feature).toList();
    }

    CityMapFeature resolve(UUID id, ResourceLocation dimension, BiPredicate<UUID, CityPlot> owns) {
        var entry = entries.get(id);
        return entry != null && entry.feature.dimension().equals(dimension) && entry.ownedByCity(owns) ? entry.feature : null;
    }

    private boolean accepts(CityMapFeature source, UUID targetId, CityMapFeature target) {
        if (target == null || !target.city().equals(source.city()) || !target.dimension().equals(source.dimension())) return false;
        var links = source.links();
        if (targetId.equals(links.street()) && target.kind() != CityMapFeature.Kind.STREET) return false;
        return acceptsEndpoint(links.start(), targetId, target) && acceptsEndpoint(links.end(), targetId, target);
    }

    private static boolean acceptsEndpoint(CityMapFeature.Endpoint endpoint, UUID targetId, CityMapFeature target) {
        if (endpoint == null || !endpoint.feature().equals(targetId)) return true;
        return switch (target.kind()) {
            case BUILDING, STATION -> target.entrances().contains(endpoint.point());
            case PATH, STREET -> target.shape() != CityMapFeature.Shape.POLYGON && target.vertices().contains(endpoint.point());
        };
    }

    private boolean validLinks(CityMapFeature source, BiPredicate<UUID, CityPlot> owns) {
        var links = source.links();
        var targets = new HashSet<UUID>();
        if (links.street() != null) targets.add(links.street());
        if (links.start() != null) targets.add(links.start().feature());
        if (links.end() != null) targets.add(links.end().feature());
        return targets.stream().allMatch(id -> accepts(source, id, resolve(id, source.dimension(), owns)));
    }

    String change(UUID city, ResourceLocation dimension, UUID id, long expected, CityMapFeature candidate,
                  BiPredicate<UUID, CityPlot> owns) {
        if (!available()) return "unavailable";
        var previous = entries.get(id);
        if (previous != null && (!previous.feature.city().equals(city) || !previous.feature.dimension().equals(dimension)))
            return "permission";
        if (expected != (previous == null ? 0 : previous.feature.revision()) || previous == null && candidate == null)
            return "stale";
        if (previous != null && entries.values().stream().map(Entry::feature)
                .anyMatch(other -> other.links().references(id) && !accepts(other, id, candidate))) return "in_use";
        if (candidate == null) {
            entries.remove(id);
        } else {
            if (!city.equals(candidate.city()) || !dimension.equals(candidate.dimension()) || !id.equals(candidate.id())
                    || expected != candidate.revision()) return "invalid";
            if (!validLinks(candidate, owns)) return "invalid_link";
            if (lastRevision == Long.MAX_VALUE || previous == null && (entries.size() >= MAX ||
                    entries.values().stream().filter(e -> e.feature.city().equals(city)).count() >= PER_CITY)) return "limit";
            var plots = candidate.footprint();
            var entry = new Entry(candidate.withRevision(lastRevision + 1), plots);
            if (!entry.ownedByCity(owns)) return "territory";
            entries.put(id, entry);
            ++lastRevision;
        }
        setDirty();
        return "ok";
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        if (!available()) return unreadable.copy();
        tag.putInt("Format", 2);
        tag.putLong("Revision", lastRevision);
        var list = new ListTag();
        entries.values().forEach(e -> list.add(e.feature.save()));
        tag.put("Features", list);
        return tag;
    }

    static CityMapBook load(CompoundTag tag, HolderLookup.Provider provider) {
        var book = new CityMapBook();
        try {
            book.lastRevision = tag.getLong("Revision");
            int version = tag.getInt("Format");
            if ((version != 1 && version != 2) || !tag.contains("Revision", Tag.TAG_LONG) || book.lastRevision < 0
                    || !(tag.get("Features") instanceof ListTag list) || list.size() > MAX
                    || !list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND) throw new IllegalArgumentException();
            var counts = new HashMap<UUID, Integer>();
            for (var element : list) {
                var feature = CityMapFeature.load((CompoundTag) element);
                if (feature.revision() <= 0 || feature.revision() > book.lastRevision || book.entries.containsKey(feature.id())
                        || counts.merge(feature.city(), 1, Integer::sum) > PER_CITY) throw new IllegalArgumentException();
                book.entries.put(feature.id(), new Entry(feature, feature.footprint()));
            }
            for (var entry : book.entries.values()) {
                if (!book.validLinks(entry.feature, (city, plot) -> true)) throw new IllegalArgumentException("invalid_link");
            }
        } catch (RuntimeException invalid) {
            book.entries.clear();
            book.unreadable = tag.copy();
        }
        return book;
    }
}
