package com.example.examplemod.cargo;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;

import java.nio.file.Files;
import java.util.UUID;

/** Uses the production SavedData mutations/codec and address policy, without a simulated game server. */
public final class CargoPointCheck {
    private static int checks;
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void rejects(CompoundTag root, String message) {
        boolean rejected = false;
        try { CargoPointRegistry.load(root, null); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, message);
    }
    public static void main(String[] args) throws Exception {
        UUID owner = UUID.randomUUID(), outsider = UUID.randomUUID();
        var data = new CargoPointRegistry();
        var a = data.create(owner, "  Северный склад  ");
        var b = data.create(owner, "Пристань");
        var foreign = data.create(outsider, "Чужой склад");
        require(a.name().equals("Северный склад") && !a.id().equals(b.id()), "trimmed display names and unique identities");
        var overworld = ResourceLocation.parse("minecraft:overworld");
        var nether = ResourceLocation.parse("minecraft:the_nether");
        var first = new CargoPointRegistry.RouterRef(overworld, new BlockPos(10, 70, -20), UUID.randomUUID());
        var second = new CargoPointRegistry.RouterRef(nether, new BlockPos(10, 70, -20), UUID.randomUUID());
        require(data.bind(owner, a.id(), first) && data.bind(owner, a.id(), second), "multiple routers, dimension-aware positions");
        require(!data.bind(owner, a.id(), first) && !data.bind(owner, b.id(), first), "duplicate and cross-point binding refused");
        require(!data.bind(outsider, a.id(), first) && !data.bind(owner, foreign.id(), first), "foreign point mutations refused");
        var replacement = new CargoPointRegistry.RouterRef(overworld, first.pos(), UUID.randomUUID());
        require(data.pointForRouter(replacement).isEmpty(), "replacement router does not inherit registration by coordinates");
        require(!data.bind(owner, b.id(), replacement), "stale location must be explicitly unbound first");
        require(!data.unbind(outsider, first), "only point owner may unbind");
        require(!data.rename(outsider, a.id(), "Hijacked") && !data.delete(outsider, a.id()), "foreign rename/delete refused");
        String address = new CargoAddress(a.id(), foreign.id()).toString();
        require(data.rename(owner, a.id(), "Новый склад") && CargoAddress.parse(address).orElseThrow().nextPoint().equals(a.id()),
                "rename does not change identity or parcel addresses");
        boolean immutable = false;
        try { data.point(a.id()).orElseThrow().routers().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        require(immutable, "read API cannot mutate bindings");
        var saved = data.save(new CompoundTag(), null);
        var path = Files.createTempFile("ccec-points-", ".dat");
        try {
            NbtIo.writeCompressed(saved, path);
            data = CargoPointRegistry.load(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()), null);
        } finally { Files.deleteIfExists(path); }
        require(data.point(a.id()).orElseThrow().name().equals("Новый склад"), "name survives disk reload");
        require(data.point(a.id()).orElseThrow().owner().equals(owner), "owner survives disk reload");
        require(data.point(a.id()).orElseThrow().routers().size() == 2
                && data.pointForRouter(first).orElseThrow().id().equals(a.id()), "all physical bindings survive disk reload");
        require(data.unbind(owner, first) && data.bind(owner, b.id(), replacement), "explicit rebind of replacement router");
        require(data.delete(owner, a.id()) && data.pointForRouter(second).isEmpty(), "deletion releases every logical binding");
        require(data.point(a.id()).isEmpty() && data.pointForRouter(replacement).orElseThrow().id().equals(b.id()), "other point unaffected");
        boolean invalidName = false;
        try { data.create(owner, "   "); } catch (IllegalArgumentException expected) { invalidName = true; }
        require(invalidName, "blank point name rejected");

        var route = new CargoAddress(a.id(), foreign.id());
        var onward = new CargoAddress(b.id(), foreign.id());
        require(CargoAddress.parse(route.toString()).orElseThrow().equals(route), "canonical address round-trip");
        require(CargoAddress.mayForward(route.toString(), onward.toString()), "next hop can change while final is preserved");
        require(!CargoAddress.mayForward(route.toString(), new CargoAddress(b.id(), b.id()).toString()), "final destination cannot change");
        require(!CargoAddress.mayForward(route.toString(), "plain-address"), "legacy API cannot erase CCEC final destination");
        require(CargoAddress.mayForward("old", "new") && CargoAddress.mayForward("old", route.toString()), "legacy and first CCEC assignments supported");
        for (String bad : new String[]{"ccec/v2/" + a.id() + "/" + b.id(), "ccec/v1/1-1-1-1-1/" + b.id(),
                route + "/", "ccec/v1/*/" + b.id(), "ccec/broken"}) {
            require(CargoAddress.parse(bad).isEmpty(), "malformed/reserved address rejected");
            require(!CargoAddress.mayForward(route.toString(), bad) && !CargoAddress.mayForward(bad, route.toString()), "malformed address cannot lose destination");
        }
        require(CargoAddress.postboxFilter(a.id()).equals("ccec/v1/" + a.id() + "/*"), "point-scoped native Postbox filter");
        var future = saved.copy(); future.putInt("Version", 2); rejects(future, "future schema rejected");
        var duplicate = saved.copy(); var points = duplicate.getList("Points", 10); points.add(points.getCompound(0).copy());
        rejects(duplicate, "duplicate point identity rejected on load");
        var duplicateRouter = saved.copy();
        for (var raw : duplicateRouter.getList("Points", 10)) {
            var point = (CompoundTag) raw; var routers = point.getList("Routers", 10);
            if (!routers.isEmpty()) { routers.add(routers.getCompound(0).copy()); break; }
        }
        rejects(duplicateRouter, "duplicate physical binding rejected on load");
        System.out.println("PASS: " + checks + " Cargo Point permissions, binding, persistence and address checks");
    }
}
