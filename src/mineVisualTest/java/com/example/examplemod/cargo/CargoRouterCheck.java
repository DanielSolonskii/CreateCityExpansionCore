package com.example.examplemod.cargo;

import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.ZipFile;

/** Exercises the production storage envelope with opaque component payloads and the shipped assets. */
public final class CargoRouterCheck {
    private static int checks;
    private static void require(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static void rejects(CompoundTag tag, String message) {
        boolean rejected = false;
        try { CargoRouterData.load(tag); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, message);
    }

    public static void main(String[] args) throws Exception {
        UUID owner = UUID.randomUUID();
        CompoundTag inventory = new CompoundTag();
        inventory.putInt("Size", CargoRouterData.SLOTS);
        ListTag parcels = new ListTag();
        for (int slot = 0; slot < CargoRouterData.SLOTS; slot++) {
            CompoundTag parcel = new CompoundTag();
            parcel.putInt("Slot", slot);
            parcel.putString("id", "create:cardboard_package_12x12");
            parcel.putInt("count", 1);
            CompoundTag components = new CompoundTag();
            components.putString("create:package_address", "Адрес " + slot);
            CompoundTag custom = new CompoundTag();
            CargoPackageAssignment.writeAssignment(custom, UUID.randomUUID(), owner);
            custom.putString("other_mod:opaque", "keep me");
            components.put("minecraft:custom_data", custom);
            CompoundTag contents = new CompoundTag();
            contents.putString("fixture", "opaque native contents " + slot);
            components.put("create:package_contents", contents);
            CompoundTag order = new CompoundTag();
            order.putInt("order_id", 12345);
            order.putInt("fragment_index", slot);
            components.put("create:package_order_data", order);
            parcel.put("components", components);
            parcels.add(parcel);
        }
        inventory.put("Items", parcels);
        boolean[] ready = new boolean[CargoRouterData.SLOTS];
        ready[0] = ready[17] = true;
        var saved = CargoRouterData.save(owner, inventory, ready);
        var path = Files.createTempFile("ccec-router-", ".dat");
        try {
            NbtIo.writeCompressed(saved, path);
            var loaded = CargoRouterData.load(NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap()));
            require(owner.equals(loaded.owner()), "owner survives disk reload");
            require(inventory.equals(loaded.packages()), "all slots and opaque native components survive disk reload");
            for (int slot = 0; slot < CargoRouterData.SLOTS; slot++)
                require(loaded.ready()[slot] == ready[slot], "dispatch state survives for slot " + slot);
            require(!loaded.ready()[1], "previous train assignment alone does not make received parcel ready");
            loaded.packages().getList("Items", 10).getCompound(0).putInt("count", 999);
            require(saved.getCompound("Packages").equals(inventory), "loaded inspection cannot mutate saved inventory");
        } finally { Files.deleteIfExists(path); }
        inventory.getList("Items", 10).getCompound(0).putInt("count", 99);
        require(saved.getCompound("Packages").getList("Items", 10).getCompound(0).getInt("count") == 1,
                "serialized save owns a snapshot of native item payload");
        var invalid = saved.copy(); invalid.putInt("Version", 2);
        rejects(invalid, "future version fails closed");
        invalid = saved.copy(); invalid.getCompound("Packages").putInt("Size", 1);
        rejects(invalid, "wrong buffer size cannot silently discard parcels");
        invalid = saved.copy(); invalid.putIntArray("Ready", new int[]{-1, CargoRouterData.SLOTS});
        rejects(invalid, "invalid dispatch slots rejected");
        invalid = saved.copy(); invalid.remove("Ready");
        var waiting = CargoRouterData.load(invalid);
        require(!waiting.ready()[0] && !waiting.ready()[17], "absent dispatch state leaves parcels waiting");
        invalid.remove("Owner");
        require(CargoRouterData.load(invalid).owner() == null, "ownerless router can be loaded inert");

        try (var zip = new ZipFile(Path.of(args[0]).toFile())) {
            for (String entry : new String[]{
                    "com/example/examplemod/cargo/CargoBlocks.class",
                    "com/example/examplemod/cargo/CargoRouterBlock.class",
                    "com/example/examplemod/cargo/CargoRouterBlockEntity.class",
                    "assets/createcityexpansioncore/blockstates/cargo_router.json",
                    "assets/createcityexpansioncore/models/block/cargo_router.json",
                    "assets/createcityexpansioncore/models/item/cargo_router.json",
                    "data/createcityexpansioncore/loot_table/blocks/cargo_router.json",
                    "data/create/tags/block/non_movable.json"}) {
                require(zip.getEntry(entry) != null, "production artifact includes " + entry);
                if (entry.endsWith(".json")) try (var stream = zip.getInputStream(zip.getEntry(entry))) {
                    require(JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).isJsonObject(), "valid JSON " + entry);
                }
            }
            for (String lang : new String[]{"ru_ru", "en_us"}) try (var stream = zip.getInputStream(zip.getEntry(
                    "assets/createcityexpansioncore/lang/" + lang + ".json"))) {
                require(!JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject()
                        .get("block.createcityexpansioncore.cargo_router").getAsString().isBlank(), "router translated in " + lang);
            }
        }
        System.out.println("PASS: " + checks + " Cargo Router persistence and packaged asset checks");
    }
}
