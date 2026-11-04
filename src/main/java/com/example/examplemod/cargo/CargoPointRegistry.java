package com.example.examplemod.cargo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import com.example.examplemod.cargo.center.CargoOwners;

/** Independent world-wide logical points; bindings exist here only, never as a second Router-side cache. */
public final class CargoPointRegistry extends SavedData {
    public static final String DATA_NAME = "ccec_cargo_points";
    public record RouterRef(ResourceLocation dimension, BlockPos pos, UUID instanceId) {
        public RouterRef {
            Objects.requireNonNull(dimension); pos = Objects.requireNonNull(pos).immutable(); Objects.requireNonNull(instanceId);
        }
        boolean sameLocation(RouterRef other) { return dimension.equals(other.dimension) && pos.equals(other.pos); }
    }
    public record Point(UUID id, UUID owner, String name, Set<RouterRef> routers) {
        public Point { routers = Set.copyOf(routers); }
    }
    private final Map<UUID, Point> points = new HashMap<>();
    public java.util.List<Point> points() { return java.util.List.copyOf(points.values()); }

    public static CargoPointRegistry get(MinecraftServer server) {
        if (!Objects.requireNonNull(server).isSameThread()) throw new IllegalStateException("Cargo points require server thread");
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(CargoPointRegistry::new, CargoPointRegistry::load, null), DATA_NAME);
    }
    public static Point create(MinecraftServer server, UUID owner, String name) { return get(server).create(owner, name); }
    public static boolean rename(MinecraftServer server, UUID actor, UUID id, String name) {
        Point point = get(server).points.get(id);
        return point != null && CargoOwners.point(server, point).manages(server, actor) && get(server).rename(point.owner(), id, name);
    }
    public static boolean delete(MinecraftServer server, UUID actor, UUID id) {
        Point point = get(server).points.get(id);
        return point != null && CargoOwners.point(server, point).manages(server, actor) && get(server).delete(point.owner(), id);
    }
    public static Optional<Point> find(MinecraftServer server, UUID id) { return get(server).point(id); }

    public static boolean bindRouter(ServerPlayer actor, UUID pointId, ServerLevel level, BlockPos pos) {
        if (!level.getServer().isSameThread() || actor.getServer() != level.getServer() || !level.hasChunkAt(pos)
                || !(level.getBlockEntity(pos) instanceof CargoRouterBlockEntity router) || router.isRemoved()
                || router.ownerId() == null || !router.logisticsOwner().manages(level.getServer(), actor.getUUID())) return false;
        Point point = get(level.getServer()).points.get(pointId);
        if (point == null || !CargoOwners.point(level.getServer(), point).manages(level.getServer(), actor.getUUID())) return false;
        boolean bound = get(level.getServer()).bind(point.owner(), pointId, router.reference());
        if (bound) router.setChanged(); // persist new instance UUID, including Routers created before Cargo Point v1
        return bound;
    }
    public static boolean unbindRouter(MinecraftServer server, UUID actor, RouterRef router) {
        Point point = get(server).pointForRouter(router).orElse(null);
        return point != null && CargoOwners.point(server, point).manages(server, actor) && get(server).unbind(point.owner(), router);
    }
    static void routerRemoved(MinecraftServer server, RouterRef router) {
        CargoPointRegistry data = get(server);
        data.pointForRouter(router).ifPresent(point -> data.unbind(point.owner(), router));
    }

    public Optional<Point> point(UUID id) { return Optional.ofNullable(points.get(Objects.requireNonNull(id))); }
    public Optional<Point> pointForRouter(RouterRef router) {
        return points.values().stream().filter(point -> point.routers().contains(router)).findFirst();
    }

    // Shared production mutations are package-private so headless tests exercise ownership and persistence directly.
    Point create(UUID owner, String name) {
        Objects.requireNonNull(owner);
        String normalized = validName(name);
        UUID id; do { id = UUID.randomUUID(); } while (points.containsKey(id));
        Point point = new Point(id, owner, normalized, Set.of()); points.put(id, point); setDirty(); return point;
    }
    boolean rename(UUID actor, UUID id, String name) {
        Point point = points.get(id);
        if (point == null || !point.owner().equals(actor)) return false;
        String normalized = validName(name);
        if (!point.name().equals(normalized)) { points.put(id, new Point(id, point.owner(), normalized, point.routers())); setDirty(); }
        return true;
    }
    boolean delete(UUID actor, UUID id) {
        Point point = points.get(id);
        if (point == null || !point.owner().equals(actor)) return false;
        points.remove(id); setDirty(); return true;
    }
    boolean bind(UUID actor, UUID id, RouterRef router) {
        Point point = points.get(id);
        if (point == null || !point.owner().equals(actor)) return false;
        for (Point existing : points.values()) for (RouterRef ref : existing.routers())
            if (ref.instanceId().equals(router.instanceId()) || ref.sameLocation(router)) return false;
        Set<RouterRef> refs = new HashSet<>(point.routers()); refs.add(router);
        points.put(id, new Point(id, point.owner(), point.name(), refs)); setDirty(); return true;
    }
    boolean unbind(UUID actor, RouterRef router) {
        Point point = pointForRouter(router).orElse(null);
        if (point == null || !point.owner().equals(actor)) return false;
        Set<RouterRef> refs = new HashSet<>(point.routers()); refs.remove(router);
        points.put(point.id(), new Point(point.id(), point.owner(), point.name(), refs)); setDirty(); return true;
    }
    private static String validName(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 96) throw new IllegalArgumentException("Invalid Cargo Point name");
        return name.strip();
    }

    public static CargoPointRegistry load(CompoundTag root, HolderLookup.Provider lookup) {
        if (root.getInt("Version") != 1) throw new IllegalArgumentException("Unsupported Cargo Point registry version");
        CargoPointRegistry data = new CargoPointRegistry();
        for (Tag raw : root.getList("Points", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            if (!tag.hasUUID("Id") || !tag.hasUUID("Owner")) throw new IllegalArgumentException("Invalid Cargo Point identity");
            UUID id = tag.getUUID("Id"), owner = tag.getUUID("Owner");
            if (data.points.putIfAbsent(id, new Point(id, owner, validName(tag.getString("Name")), Set.of())) != null)
                throw new IllegalArgumentException("Duplicate Cargo Point UUID");
            for (Tag value : tag.getList("Routers", Tag.TAG_COMPOUND)) {
                CompoundTag ref = (CompoundTag) value;
                ResourceLocation dimension = ResourceLocation.tryParse(ref.getString("Dimension"));
                if (dimension == null || !ref.hasUUID("Instance") || !ref.contains("Pos", Tag.TAG_LONG)
                        || !data.bind(owner, id, new RouterRef(dimension, BlockPos.of(ref.getLong("Pos")), ref.getUUID("Instance"))))
                    throw new IllegalArgumentException("Invalid or duplicate Cargo Router binding");
            }
        }
        data.setDirty(false);
        return data;
    }
    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider lookup) {
        root.putInt("Version", 1);
        ListTag entries = new ListTag();
        points.values().stream().sorted(Comparator.comparing(Point::id)).forEach(point -> {
            CompoundTag tag = new CompoundTag(); tag.putUUID("Id", point.id()); tag.putUUID("Owner", point.owner()); tag.putString("Name", point.name());
            ListTag routers = new ListTag();
            point.routers().stream().sorted(Comparator.comparing(RouterRef::instanceId)).forEach(router -> {
                CompoundTag ref = new CompoundTag(); ref.putString("Dimension", router.dimension().toString());
                ref.putLong("Pos", router.pos().asLong()); ref.putUUID("Instance", router.instanceId()); routers.add(ref);
            });
            tag.put("Routers", routers); entries.add(tag);
        });
        root.put("Points", entries); return root;
    }
}
