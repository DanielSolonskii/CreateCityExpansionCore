package com.example.examplemod.cargo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.util.UUID;

/** Production output eligibility, arrival migration/codec and exact native unpacking dependency contract. */
public final class CargoRouterOutputCheck {
    private static int checks;
    private static void require(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static ClassNode readClass(String path) throws Exception {
        try (var stream = CargoRouterOutputCheck.class.getResourceAsStream("/" + path + ".class")) {
            if (stream == null) throw new AssertionError("Missing dependency: " + path);
            ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
    public static void main(String[] args) throws Exception {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var outgoing = new CargoAddress(b, b);
        var transit = CargoTransshipment.evaluate(outgoing, a, null, null, id -> true);
        var arrival = CargoTransshipment.evaluate(outgoing, b, transit.journey(), null, id -> true);
        require(arrival.status() == CargoTransshipment.Status.ARRIVED && arrival.status() != CargoTransshipment.Status.DELIVERED,
                "final point arrival never claims warehouse delivery");
        require(CargoTransshipment.mayExtract(outgoing, b, arrival.journey(), false), "persisted final parcel may leave final router");
        require(!CargoTransshipment.mayExtract(outgoing, a, transit.journey(), false), "transit parcel unavailable");
        require(!CargoTransshipment.mayExtract(outgoing, b, transit.journey(), false), "arrival must first be persisted by normal processing");
        require(!CargoTransshipment.mayExtract(outgoing, b, arrival.journey(), true), "prepared sending slot cannot be extracted");
        require(!CargoTransshipment.mayExtract(outgoing, a, arrival.journey(), false), "terminal parcel at foreign point cannot be exported there");
        require(!CargoTransshipment.mayExtract(outgoing, null, arrival.journey(), false), "unbound router cannot export");
        require(!CargoTransshipment.mayExtract(outgoing, b, null, false), "unprocessed/waiting parcel cannot export");
        require(!CargoTransshipment.mayExtract(null, b, arrival.journey(), false), "ordinary/malformed address cannot export");
        require(!CargoTransshipment.mayExtract(new CargoAddress(b, a), b, arrival.journey(), false), "address/history final identity must agree");

        CompoundTag custom = new CompoundTag(); custom.putString("other_mod:data", "unchanged");
        CargoTransshipment.write(custom, arrival.journey());
        require(custom.getCompound(CargoTransshipment.KEY).getInt("Version") == 2, "arrival uses versioned state");
        require(custom.getCompound(CargoTransshipment.KEY).getBoolean("Arrived")
                && !custom.getCompound(CargoTransshipment.KEY).contains("Delivered"), "new data never writes an unverified delivery claim");
        CompoundTag parcel = new CompoundTag(); parcel.putInt("count", 1); parcel.putString("address", outgoing.toString());
        CompoundTag nativeContents = new CompoundTag(); nativeContents.putString("opaque_payload", "original contents");
        parcel.put("create:package_contents", nativeContents); parcel.putString("create:package_order_data", "original order");
        parcel.put("minecraft:custom_data", custom);
        var path = Files.createTempFile("ccec-arrived-output-", ".dat");
        try {
            NbtIo.writeCompressed(parcel, path);
            var restored = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            require(restored.equals(parcel), "full opaque parcel payload survives disk reload unchanged");
            var state = CargoTransshipment.read(restored.getCompound("minecraft:custom_data"));
            var before = restored.copy();
            for (int i = 0; i < 100; i++) require(CargoTransshipment.mayExtract(outgoing, b, state, false), "repeat eligibility read stays eligible");
            require(restored.equals(before), "eligibility/simulation policy has no payload writes or confirmation side effects");
        } finally { Files.deleteIfExists(path); }

        var legacy = custom.copy(); var tag = legacy.getCompound(CargoTransshipment.KEY);
        tag.putInt("Version", 1); tag.putBoolean("Delivered", true); tag.remove("Arrived");
        var migrated = CargoTransshipment.read(legacy);
        require(CargoTransshipment.evaluate(outgoing, b, migrated, null, id -> true).status() == CargoTransshipment.Status.ARRIVED,
                "old Delivered=true migrates to arrival, never a warehouse receipt");
        require(CargoTransshipment.mayExtract(outgoing, b, migrated, false), "legacy terminal packages are physically recoverable");
        var modern = legacy.copy(); CargoTransshipment.write(modern, migrated);
        require(!modern.getCompound(CargoTransshipment.KEY).contains("Delivered") && modern.getString("other_mod:data").equals("unchanged"),
                "migration drops old confirmation name without changing unrelated data");

        var handler = readClass("com/simibubi/create/content/logistics/packager/PackagerItemHandler");
        var insertion = handler.methods.stream().filter(method -> method.name.equals("insertItem")).findFirst().orElseThrow();
        int unwrapCalls = 0;
        for (var instruction : insertion.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.endsWith("/PackagerBlockEntity") && call.name.equals("unwrapBox")) unwrapCalls++;
        require(unwrapCalls == 2, "native packager item insertion retains simulate/execute unwrap passes");
        var packager = readClass("com/simibubi/create/content/logistics/packager/PackagerBlockEntity");
        var unwrap = packager.methods.stream().filter(method -> method.name.equals("unwrapBox")).findFirst().orElseThrow();
        boolean nativeUnpacking = false;
        for (var instruction : unwrap.instructions) if (instruction instanceof MethodInsnNode call)
            nativeUnpacking |= call.owner.equals("com/simibubi/create/api/packager/unpacking/UnpackingHandler") && call.name.equals("unpack");
        require(nativeUnpacking, "verified native unpacking hook remains present; CCEC need not unpack virtually");
        System.out.println("PASS: " + checks + " arrival-only output authorization, migration, persistence and native unpacking contract checks");
    }
}
