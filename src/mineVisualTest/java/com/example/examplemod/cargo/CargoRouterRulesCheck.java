package com.example.examplemod.cargo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.nio.file.Files;
import java.util.UUID;

/** Real rule policy and codecs. Does not simulate Minecraft inventory insertion or claim train delivery. */
public final class CargoRouterRulesCheck {
    private static int checks;
    private static void require(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    private static void rejects(CompoundTag tag, String message) {
        boolean rejected = false;
        try { CargoRouterRules.load(tag); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, message);
    }
    public static void main(String[] args) throws Exception {
        UUID owner = UUID.randomUUID(), foreign = UUID.randomUUID(), next = UUID.randomUUID(), last = UUID.randomUUID();
        UUID train = UUID.randomUUID(), otherTrain = UUID.randomUUID();
        var rules = new CargoRouterRules();
        String address = new CargoAddress(next, last).toString();
        require(rules.match(address, id -> true, id -> true).isEmpty(), "missing rule leaves parcel waiting");
        require(!rules.put(foreign, owner, next, train, true, true), "foreign owner cannot add rule");
        require(!rules.put(null, null, next, train, true, true), "ownerless router cannot add rule");
        require(!rules.put(owner, owner, next, train, false, true), "unregistered point rejected");
        require(!rules.put(owner, owner, next, train, true, false), "unavailable/unauthorized train rejected");
        require(rules.put(owner, owner, next, train, true, true), "owner configures valid rule");
        require(rules.match(address, id -> true, id -> true).orElseThrow().equals(train), "next point selects assigned train");
        require(rules.match("ordinary-address", id -> true, id -> true).isEmpty(), "plain packages remain manual");
        require(rules.match("ccec/v1/broken", id -> true, id -> true).isEmpty(), "malformed address remains waiting");
        require(rules.match(new CargoAddress(last, last).toString(), id -> true, id -> true).isEmpty(), "does not match final point instead of next");
        require(rules.match(address, id -> !id.equals(next), id -> true).isEmpty(), "deleted next point blocks preparation");
        require(rules.match(address, id -> !id.equals(last), id -> true).isEmpty(), "deleted final point blocks preparation");
        require(rules.match(address, id -> true, id -> false).isEmpty(), "runtime loss of train or permission blocks preparation");
        require(rules.put(owner, owner, next, otherTrain, true, true) && rules.snapshot().size() == 1,
                "changing train replaces one mapping, never adds a second dispatch entry");
        require(rules.match(address, id -> true, id -> true).orElseThrow().equals(otherTrain), "new rule selected");
        boolean immutable = false;
        try { rules.snapshot().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        require(immutable, "read API cannot change rules");

        // Persist rules and automatic/manual origin alongside a full existing physical-buffer payload.
        CompoundTag packages = new CompoundTag(); packages.putInt("Size", CargoRouterData.SLOTS);
        ListTag contents = new ListTag();
        for (int slot = 0; slot < CargoRouterData.SLOTS; slot++) {
            CompoundTag box = new CompoundTag(); box.putInt("Slot", slot); box.putInt("count", 1);
            box.putString("id", "create:cardboard_package_12x12");
            CompoundTag components = new CompoundTag(); components.putString("create:package_address", address);
            CompoundTag nativeOrder = new CompoundTag(); nativeOrder.putInt("fragment_index", slot);
            components.put("create:package_order_data", nativeOrder);
            CompoundTag nativeContents = new CompoundTag(); nativeContents.putString("opaque_payload", "contents " + slot);
            components.put("create:package_contents", nativeContents); box.put("components", components); contents.add(box);
        }
        packages.put("Items", contents);
        boolean[] ready = new boolean[CargoRouterData.SLOTS]; ready[0] = ready[1] = ready[17] = true;
        rules.markAutomatic(0); rules.markAutomatic(17); // slot 1 is manually prepared
        CompoundTag checkpoint = new CompoundTag();
        checkpoint.put("CargoRouter", CargoRouterData.save(owner, packages, ready));
        checkpoint.put("CargoDispatchRules", rules.save());
        var path = Files.createTempFile("ccec-router-rules-", ".dat");
        try {
            NbtIo.writeCompressed(checkpoint, path);
            var saved = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            rules = CargoRouterRules.load(saved.getCompound("CargoDispatchRules"));
            var buffer = CargoRouterData.load(saved.getCompound("CargoRouter"));
            require(buffer.packages().equals(packages) && buffer.packages().getList("Items", 10).size() == 18,
                    "full buffer, count, address, contents and native data survive checkpoint unchanged");
            require(rules.isAutomatic(0) && rules.isAutomatic(17) && !rules.isAutomatic(1), "automatic and manual origins survive reload");
            require(buffer.ready()[0] && buffer.ready()[1] && buffer.ready()[17], "prepared states survive reload");
            require(rules.match(address, id -> true, id -> true).orElseThrow().equals(otherTrain), "rule survives restart");
            var before = rules.save();
            for (int i = 0; i < 100; i++) rules.match(address, id -> true, id -> true);
            require(rules.save().equals(before) && buffer.packages().equals(packages),
                    "repeated rule evaluation creates no dispatch queue entries and never mutates physical payload");
            require(CargoAddress.parse(address).orElseThrow().finalPoint().equals(last), "automatic matching preserves final UUID");
            rules.clearSlot(0);
            require(!rules.isAutomatic(0) && rules.isAutomatic(17), "clearing/reusing a slot clears its preparation origin only");
        } finally { Files.deleteIfExists(path); }
        require(!rules.remove(foreign, owner, next), "foreign owner cannot delete rule");
        require(rules.remove(owner, owner, next) && rules.match(address, id -> true, id -> true).isEmpty(), "owner removal leaves no rule");
        require(!rules.remove(owner, owner, next), "repeat removal does not mutate state");
        rules.put(owner, owner, next, train, true, true);
        var future = rules.save(); future.putInt("Version", 2); rejects(future, "future version rejected");
        var duplicate = rules.save(); var entries = duplicate.getList("Entries", 10); entries.add(entries.getCompound(0).copy());
        rejects(duplicate, "duplicate next point rejected on load");
        var missingTrain = rules.save(); missingTrain.getList("Entries", 10).getCompound(0).remove("Train");
        rejects(missingTrain, "missing train UUID rejected");
        var invalidSlot = rules.save(); invalidSlot.putIntArray("Automatic", new int[]{18});
        rejects(invalidSlot, "invalid preparation slot rejected");
        System.out.println("PASS: " + checks + " Cargo Router rule authorization, matching, persistence and replay checks");
    }
}
