package com.example.examplemod.cargo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Exercises the actual production journey state machine and codecs, without simulating train/item handlers. */
public final class CargoTransshipmentCheck {
    private static int checks;
    private static void require(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static void rejectsJourney(CompoundTag custom, String message) {
        boolean rejected = false;
        try { CargoTransshipment.read(custom); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, message);
    }
    public static void main(String[] args) throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        UUID owner = UUID.randomUUID(), foreign = UUID.randomUUID(), train = UUID.randomUUID();
        Set<UUID> points = Set.of(a, b, c, d);
        var rules = new CargoRouterRules();
        require(rules.putTransfer(owner, owner, a, d, b, points::contains), "owner configures final -> next");
        require(!rules.putTransfer(foreign, owner, a, d, c, points::contains), "foreign mutation refused");
        require(!rules.putTransfer(owner, owner, a, d, a, points::contains), "self next point rejected");
        require(!rules.putTransfer(owner, owner, a, a, b, points::contains), "final point does not need a departure rule");
        require(!rules.putTransfer(owner, owner, a, d, b, id -> !id.equals(b)), "unregistered next point refused");
        require(rules.put(owner, owner, b, train, true, true), "dispatch rule coexists with transfer rule");

        var start = new CargoAddress(a, d);
        var noPoint = CargoTransshipment.evaluate(start, null, null, b, points::contains);
        require(noPoint.status() == CargoTransshipment.Status.WAITING_POINT, "unbound router cannot silently choose current point");
        var waiting = CargoTransshipment.evaluate(start, a, null, null, points::contains);
        require(waiting.status() == CargoTransshipment.Status.WAITING_ROUTE && waiting.address().equals(start), "missing route leaves address unchanged");
        var departure = CargoTransshipment.evaluate(waiting.address(), a, waiting.journey(), rules.nextFor(d), points::contains);
        require(departure.address().equals(new CargoAddress(b, d)) && departure.status() == CargoTransshipment.Status.WAITING_TRAIN,
                "only next address changes on departure");
        require(rules.match(departure.address().toString(), points::contains, id -> id.equals(train)).orElseThrow().equals(train),
                "existing dispatch rules select train after transfer");
        require(rules.match(departure.address().toString(), points::contains, id -> false).isEmpty(), "no train permission means no dispatch");
        for (int i = 0; i < 100; i++) {
            var repeat = CargoTransshipment.evaluate(departure.address(), a, departure.journey(), c, points::contains);
            require(repeat.equals(departure), "same point/restart does not replan a prepared physical parcel");
        }
        var unavailable = CargoTransshipment.evaluate(departure.address(), a, departure.journey(), c, id -> !id.equals(b));
        require(unavailable.status() == CargoTransshipment.Status.WAITING_ROUTE && unavailable.journey().equals(departure.journey()),
                "deleted planned destination waits without discarding committed hop");
        require(CargoTransshipment.evaluate(unavailable.address(), a, unavailable.journey(), c, points::contains).equals(departure),
                "restored destination recovers original hop");
        var atB = CargoTransshipment.evaluate(departure.address(), b, departure.journey(), c, points::contains);
        require(atB.address().equals(new CargoAddress(c, d)) && atB.journey().visited().equals(List.of(a, b)), "physical arrival at B unlocks its next transition");
        var wrongPoint = CargoTransshipment.evaluate(departure.address(), c, departure.journey(), d, points::contains);
        require(wrongPoint.status() == CargoTransshipment.Status.BLOCKED, "arrival at unexpected point cannot advance history");
        var cycle = CargoTransshipment.evaluate(departure.address(), b, departure.journey(), a, points::contains);
        require(cycle.status() == CargoTransshipment.Status.BLOCKED && cycle.address().equals(departure.address()), "A -> B -> A cycle refused before rewriting address");
        require(CargoTransshipment.evaluate(cycle.address(), b, cycle.journey(), c, points::contains).address().equals(new CargoAddress(c, d)),
                "owner can fix uncommitted cyclic rule");
        var atC = CargoTransshipment.evaluate(atB.address(), c, atB.journey(), d, points::contains);
        var delivered = CargoTransshipment.evaluate(atC.address(), d, atC.journey(), a, points::contains);
        require(delivered.status() == CargoTransshipment.Status.ARRIVED && delivered.journey().arrived(), "arrival at final point is terminal but not warehouse delivery");
        require(CargoTransshipment.evaluate(delivered.address(), a, delivered.journey(), b, points::contains).status() == CargoTransshipment.Status.ARRIVED,
                "delivered parcel cannot be dispatched again even if physically carried elsewhere");
        boolean manualBlocked = false;
        try { CargoTransshipment.manualPlan(delivered.address(), new CargoAddress(b, d), d, delivered.journey()); }
        catch (IllegalArgumentException expected) { manualBlocked = true; }
        require(manualBlocked, "manual API cannot reopen final delivery");
        manualBlocked = false;
        try { CargoTransshipment.manualPlan(atB.address(), new CargoAddress(a, d), b, atB.journey()); }
        catch (IllegalArgumentException expected) { manualBlocked = true; }
        require(manualBlocked, "manual API cannot erase visit history or replan same stop");
        var manual = CargoTransshipment.manualPlan(departure.address(), departure.address(), a, departure.journey());
        require(manual.equals(departure.journey()), "manual train reassignment for same leg remains possible");
        var explicitSource = CargoTransshipment.evaluate(new CargoAddress(b, d), a, null, c, points::contains);
        require(explicitSource.address().nextPoint().equals(b), "Ticker's explicit source address is respected");
        var missingFinal = CargoTransshipment.evaluate(new CargoAddress(b, d), a, null, c, id -> !id.equals(d));
        require(CargoTransshipment.evaluate(missingFinal.address(), a, missingFinal.journey(), c, points::contains).address().nextPoint().equals(b),
                "temporary missing final point does not lose source hop");

        // Opaque physical payload is unchanged while CCEC history is added/persisted in custom_data.
        CompoundTag custom = new CompoundTag(); custom.putString("other_mod:data", "preserved");
        CargoTransshipment.write(custom, atB.journey());
        CompoundTag parcel = new CompoundTag(); parcel.putInt("count", 1); parcel.putString("address", atB.address().toString());
        CompoundTag contents = new CompoundTag(); contents.putString("native_contents", "unchanged contents");
        parcel.put("create:package_contents", contents); parcel.putString("create:package_order_data", "unchanged order");
        parcel.put("minecraft:custom_data", custom);
        CompoundTag checkpoint = new CompoundTag(); checkpoint.put("parcel", parcel); checkpoint.put("rules", rules.save());
        var path = Files.createTempFile("ccec-transshipment-", ".dat");
        try {
            NbtIo.writeCompressed(checkpoint, path);
            var restored = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            require(restored.getCompound("parcel").equals(parcel), "physical count/contents/order/address/custom data survive disk reload");
            require(CargoTransshipment.read(restored.getCompound("parcel").getCompound("minecraft:custom_data")).equals(atB.journey()), "visited/planned state survives restart");
            var loadedRules = CargoRouterRules.load(restored.getCompound("rules"));
            require(loadedRules.nextFor(d).equals(b) && loadedRules.snapshot().get(b).equals(train), "transfer and dispatch rules survive together");
        } finally { Files.deleteIfExists(path); }
        var finalCustom = new CompoundTag(); CargoTransshipment.write(finalCustom, delivered.journey());
        require(CargoTransshipment.read(finalCustom).arrived(), "terminal arrival persists in parcel data");
        require(custom.getString("other_mod:data").equals("preserved"), "unrelated custom components preserved");
        var corrupt = custom.copy(); corrupt.getCompound(CargoTransshipment.KEY).putInt("Version", 3);
        rejectsJourney(corrupt, "future journey version fails closed");
        corrupt = custom.copy(); var visited = corrupt.getCompound(CargoTransshipment.KEY).getList("Visited", 10);
        visited.add(visited.getCompound(0).copy()); rejectsJourney(corrupt, "duplicate visited point rejected");
        corrupt = custom.copy(); corrupt.getCompound(CargoTransshipment.KEY).putUUID("Next", a);
        rejectsJourney(corrupt, "planned return to visited point rejected on load");
        require(!rules.removeTransfer(foreign, owner, d) && rules.removeTransfer(owner, owner, d), "only owner may remove route");
        var legacy = rules.save(); legacy.remove("Transfers");
        require(CargoRouterRules.load(legacy).transferSnapshot().isEmpty(), "older rule saves remain readable");
        System.out.println("PASS: " + checks + " transfer lifecycle, cycle protection, terminal delivery and persistence checks");
    }
}
