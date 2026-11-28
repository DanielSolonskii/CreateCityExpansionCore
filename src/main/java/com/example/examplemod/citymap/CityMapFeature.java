package com.example.examplemod.citymap;

import com.example.examplemod.city.CityPlot;
import com.example.examplemod.projects.ProjectZone;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.*;

public record CityMapFeature(UUID id, UUID city, long revision, ResourceLocation dimension, Kind kind, Shape shape,
                             String name, String houseNumber, String description, List<BlockPos> vertices,
                             List<BlockPos> entrances, Links links, Icon icon) {
    public enum Icon { HOME, SHOP, FACTORY, WAREHOUSE, MINE, PUBLIC, OTHER, PERSONAL, STATION, TRAIN, CITY_PROJECT, SERVER_PROJECT }

    public CityMapFeature(UUID id, UUID city, long revision, ResourceLocation dimension, Kind kind, Shape shape,
                          String name, String houseNumber, String description, List<BlockPos> vertices, List<BlockPos> entrances, Links links) {
        this(id, city, revision, dimension, kind, shape, name, houseNumber, description, vertices, entrances, links, Icon.HOME);
    }
    public enum Kind { STATION, BUILDING, STREET, PATH }
    public enum Shape { POINT, LINE, POLYGON }
    public static final int MAX_VERTICES = 32, MAX_ENTRANCES = 4, NAME = 64, HOUSE_NUMBER = 16, DESCRIPTION = 180;

    /** A feature and its exact XYZ survive reordering vertices and entrances. */
    public record Endpoint(UUID feature, BlockPos point) {
        public Endpoint {
            Objects.requireNonNull(feature);
            point = CityMapFeature.point(point);
        }
    }

    public record Links(UUID street, Endpoint start, Endpoint end) {
        public static final Links EMPTY = new Links(null, null, null);
        public boolean references(UUID id) {
            return id.equals(street) || start != null && id.equals(start.feature) || end != null && id.equals(end.feature);
        }
    }

    public CityMapFeature(UUID id, UUID city, long revision, ResourceLocation dimension, Kind kind, Shape shape,
                          String name, String houseNumber, String description, List<BlockPos> vertices, List<BlockPos> entrances) {
        this(id, city, revision, dimension, kind, shape, name, houseNumber, description, vertices, entrances, Links.EMPTY);
    }

    public CityMapFeature {
        Objects.requireNonNull(id);
        Objects.requireNonNull(city);
        Objects.requireNonNull(dimension);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(shape);
        Objects.requireNonNull(links);
        Objects.requireNonNull(icon);
        if (kind != Kind.BUILDING && icon != Icon.HOME) throw new IllegalArgumentException("invalid");
        if (revision < 0 || dimension.toString().length() > 160) throw new IllegalArgumentException("invalid");
        name = text(name, NAME);
        houseNumber = text(houseNumber, HOUSE_NUMBER);
        description = text(description, DESCRIPTION);
        int minimum = switch (shape) { case POINT -> 1; case LINE -> 2; case POLYGON -> 3; };
        boolean address = kind == Kind.BUILDING && links.street != null && !houseNumber.isEmpty();
        if (name.isEmpty() && !address || vertices.size() < minimum || vertices.size() > MAX_VERTICES || entrances.size() > MAX_ENTRANCES
                || shape == Shape.POINT && vertices.size() != 1 || new HashSet<>(entrances).size() != entrances.size())
            throw new IllegalArgumentException("invalid");
        vertices = vertices.stream().map(CityMapFeature::point).toList();
        entrances = entrances.stream().map(CityMapFeature::point).toList();
        if (links.references(id) || links.street != null && kind != Kind.BUILDING
                || (links.start != null || links.end != null) && (kind != Kind.PATH || shape != Shape.LINE)
                || links.start != null && !links.start.point.equals(vertices.getFirst())
                || links.end != null && !links.end.point.equals(vertices.getLast())) throw new IllegalArgumentException("invalid");

        var xs = vertices.stream().mapToInt(BlockPos::getX).summaryStatistics();
        var zs = vertices.stream().mapToInt(BlockPos::getZ).summaryStatistics();
        if (xs.getMax() - xs.getMin() > 2048 || zs.getMax() - zs.getMin() > 2048) throw new IllegalArgumentException("limit");

        var horizontal = new HashSet<Long>();
        long area = 0;
        for (int i = 0; i < vertices.size(); i++) {
            var a = vertices.get(i);
            var b = vertices.get((i + 1) % vertices.size());
            if (!horizontal.add(BlockPos.asLong(a.getX(), 0, a.getZ()))) throw new IllegalArgumentException("invalid");
            area += (long) a.getX() * b.getZ() - (long) b.getX() * a.getZ();
        }
        if (shape == Shape.POLYGON && (area == 0 || selfIntersects(vertices))) throw new IllegalArgumentException("invalid");
    }

    private static boolean selfIntersects(List<BlockPos> vertices) {
        int count = vertices.size();
        for (int i = 0; i < count; i++) {
            var start = vertices.get(i);
            var end = vertices.get((i + 1) % count);
            for (int j = i + 2; j < count; j++) {
                if (i == 0 && j + 1 == count) continue;
                var a = vertices.get(j);
                var b = vertices.get((j + 1) % count);
                if (Line2D.linesIntersect(start.getX(), start.getZ(), end.getX(), end.getZ(),
                        a.getX(), a.getZ(), b.getX(), b.getZ())) return true;
            }
        }
        return false;
    }

    private static BlockPos point(BlockPos p) {
        if (Math.abs((long) p.getX()) > 29_999_984 || Math.abs((long) p.getZ()) > 29_999_984
                || p.getY() < -2048 || p.getY() > 2047) throw new IllegalArgumentException("invalid");
        return p.immutable();
    }

    private static String text(String raw, int limit) {
        String value = raw.strip();
        if (value.length() > limit || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0xa7))
            throw new IllegalArgumentException("invalid");
        return value;
    }

    public String label() { return name.isEmpty() ? houseNumber : houseNumber.isEmpty() ? name : name + " " + houseNumber; }
    public String label(java.util.function.Function<UUID, CityMapFeature> lookup) {
        if (links.street == null) return label();
        var street = lookup.apply(links.street);
        if (street == null || street.kind != Kind.STREET || !city.equals(street.city) || !dimension.equals(street.dimension)) return label();
        String address = houseNumber.isEmpty() ? street.name : street.name + " " + houseNumber;
        return name.isEmpty() ? address : name + " · " + address;
    }
    public BlockPos anchor() { return vertices.get(vertices.size() / 2); }

    public boolean containsXZ(int x, int z) {
        if (shape == Shape.POINT) return anchor().getX() == x && anchor().getZ() == z;
        var cell = new Rectangle2D.Double(x - .5, z - .5, 1, 1);
        for (int i = 0; i < edgeCount(); i++) {
            var a = vertices.get(i);
            var b = vertices.get((i + 1) % vertices.size());
            if (cell.intersectsLine(a.getX(), a.getZ(), b.getX(), b.getZ())) return true;
        }
        return shape == Shape.POLYGON && inside(x, z);
    }

    private int edgeCount() { return shape == Shape.POLYGON ? vertices.size() : vertices.size() - 1; }

    private boolean inside(double x, double z) {
        boolean contained = false;
        for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
            var a = vertices.get(i);
            var b = vertices.get(j);
            if ((a.getZ() > z) != (b.getZ() > z)
                    && x < (b.getX() - a.getX()) * (z - a.getZ()) / (b.getZ() - a.getZ()) + a.getX())
                contained = !contained;
        }
        return contained;
    }

    Set<CityPlot> footprint() {
        var touched = new HashSet<CityPlot>();
        vertices.forEach(p -> touched.add(CityPlot.at(dimension, p)));
        entrances.forEach(p -> touched.add(CityPlot.at(dimension, p)));
        for (int i = 0; i < edgeCount(); i++) {
            var a = vertices.get(i);
            var b = vertices.get((i + 1) % vertices.size());
            touched.addAll(new ProjectZone(dimension, ProjectZone.Shape.LINE, a.getX(), a.getZ(), b.getX(), b.getZ()).plots());
        }
        if (shape == Shape.POLYGON) {
            // After including every plot crossed by an edge, a centre test finds completely enclosed plots.
            var xs = vertices.stream().mapToInt(p -> CityPlot.plotXOf(p.getX())).summaryStatistics();
            var zs = vertices.stream().mapToInt(p -> CityPlot.plotZOf(p.getZ())).summaryStatistics();
            for (int x = xs.getMin(); x <= xs.getMax(); x++) {
                for (int z = zs.getMin(); z <= zs.getMax(); z++) {
                    var plot = new CityPlot(dimension, x, z);
                    if (inside(plot.centerX(), plot.centerZ())) touched.add(plot);
                }
            }
        }
        return Set.copyOf(touched);
    }

    CityMapFeature withRevision(long next) {
        return new CityMapFeature(id, city, next, dimension, kind, shape, name, houseNumber, description, vertices, entrances, links, icon);
    }

    CityMapFeature withIcon(Icon value) {
        return new CityMapFeature(id, city, revision, dimension, kind, shape, name, houseNumber, description, vertices, entrances, links, value);
    }

    CompoundTag save() {
        var tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putUUID("City", city);
        tag.putLong("Revision", revision);
        tag.putString("Dimension", dimension.toString());
        tag.putString("Kind", kind.name());
        tag.putString("Shape", shape.name());
        tag.putString("Name", name);
        if (icon != Icon.HOME) tag.putString("Icon", icon.name());
        tag.putString("HouseNumber", houseNumber);
        tag.putString("Description", description);
        tag.putLongArray("Vertices", vertices.stream().mapToLong(BlockPos::asLong).toArray());
        tag.putLongArray("Entrances", entrances.stream().mapToLong(BlockPos::asLong).toArray());
        if (links.street != null) tag.putUUID("Street", links.street);
        if (links.start != null) saveEndpoint(tag, "Start", links.start);
        if (links.end != null) saveEndpoint(tag, "End", links.end);
        return tag;
    }

    private static void saveEndpoint(CompoundTag tag, String key, Endpoint endpoint) {
        var target = new CompoundTag();
        target.putUUID("Feature", endpoint.feature);
        target.putLong("Point", endpoint.point.asLong());
        tag.put(key, target);
    }

    private static Endpoint loadEndpoint(CompoundTag tag, String key) {
        if (!tag.contains(key)) return null;
        var target = tag.getCompound(key);
        if (!target.contains("Point", net.minecraft.nbt.Tag.TAG_LONG)) throw new IllegalArgumentException("invalid");
        return new Endpoint(target.getUUID("Feature"), BlockPos.of(target.getLong("Point")));
    }

    static CityMapFeature load(CompoundTag tag) {
        return new CityMapFeature(tag.getUUID("Id"), tag.getUUID("City"), tag.getLong("Revision"),
                ResourceLocation.parse(tag.getString("Dimension")), Kind.valueOf(tag.getString("Kind")),
                Shape.valueOf(tag.getString("Shape")), tag.getString("Name"), tag.getString("HouseNumber"),
                tag.getString("Description"), readPositions(tag, "Vertices", MAX_VERTICES), readPositions(tag, "Entrances", MAX_ENTRANCES),
                new Links(tag.contains("Street") ? tag.getUUID("Street") : null, loadEndpoint(tag, "Start"), loadEndpoint(tag, "End")),
                tag.contains("Icon") ? Icon.valueOf(tag.getString("Icon")) : Icon.HOME);
    }

    private static List<BlockPos> readPositions(CompoundTag tag, String key, int max) {
        long[] values = tag.getLongArray(key);
        if (values.length > max) throw new IllegalArgumentException("limit");
        return Arrays.stream(values).mapToObj(BlockPos::of).toList();
    }

    static void write(RegistryFriendlyByteBuf buf, CityMapFeature feature) {
        writeBody(buf, feature, feature.name.isEmpty() ? feature.houseNumber : feature.name,
                feature.name.isEmpty() ? "" : feature.houseNumber);
    }

    static void writeLegacyRow(RegistryFriendlyByteBuf buf, CityMapFeature feature, Map<UUID, CityMapFeature> visible) {
        var street = visible.get(feature.links.street);
        if (feature.name.isEmpty() && street != null && street.kind == Kind.STREET) {
            writeBody(buf, feature, street.name, feature.houseNumber);
        } else {
            write(buf, feature);
        }
    }

    static void writeV2(RegistryFriendlyByteBuf buf, CityMapFeature feature) {
        var links = feature.links;
        buf.writeNullable(links.street, (b, id) -> b.writeUUID(id));
        buf.writeNullable(links.start, (b, e) -> { b.writeUUID(e.feature); b.writeBlockPos(e.point); });
        buf.writeNullable(links.end, (b, e) -> { b.writeUUID(e.feature); b.writeBlockPos(e.point); });
        writeBody(buf, feature, feature.name, feature.houseNumber);
    }

    private static void writeBody(RegistryFriendlyByteBuf buf, CityMapFeature feature, String wireName, String wireNumber) {
        buf.writeUUID(feature.id);
        buf.writeUUID(feature.city);
        buf.writeVarLong(feature.revision);
        buf.writeUtf(feature.dimension.toString(), 160);
        buf.writeEnum(feature.kind);
        buf.writeEnum(feature.shape);
        buf.writeUtf(wireName, NAME);
        buf.writeUtf(wireNumber, HOUSE_NUMBER);
        buf.writeUtf(feature.description, DESCRIPTION);
        buf.writeCollection(feature.vertices, (b, p) -> b.writeBlockPos(p));
        buf.writeCollection(feature.entrances, (b, p) -> b.writeBlockPos(p));
    }

    static CityMapFeature read(RegistryFriendlyByteBuf buf) {
        return readBody(buf, Links.EMPTY);
    }

    static CityMapFeature readV2(RegistryFriendlyByteBuf buf) {
        return readBody(buf, new Links(buf.readNullable(b -> b.readUUID()),
                buf.readNullable(b -> new Endpoint(b.readUUID(), b.readBlockPos())),
                buf.readNullable(b -> new Endpoint(b.readUUID(), b.readBlockPos()))));
    }

    private static CityMapFeature readBody(RegistryFriendlyByteBuf buf, Links links) {
        return new CityMapFeature(buf.readUUID(), buf.readUUID(), buf.readVarLong(), ResourceLocation.parse(buf.readUtf(160)),
                buf.readEnum(Kind.class), buf.readEnum(Shape.class), buf.readUtf(NAME), buf.readUtf(HOUSE_NUMBER),
                buf.readUtf(DESCRIPTION), readPositions(buf, MAX_VERTICES), readPositions(buf, MAX_ENTRANCES), links);
    }

    private static List<BlockPos> readPositions(RegistryFriendlyByteBuf buf, int max) {
        int count = buf.readVarInt();
        if (count < 0 || count > max) throw new IllegalArgumentException("limit");
        var points = new ArrayList<BlockPos>(count);
        for (int i = 0; i < count; i++) points.add(buf.readBlockPos());
        return points;
    }
}
