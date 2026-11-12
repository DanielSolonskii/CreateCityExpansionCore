package com.example.examplemod.cargo;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/** Real identity/evidence/receipt codecs and dependency bytecode. No Minecraft inventory insertion is simulated. */
public final class CargoDeliveryCheck {
    private static int checks;
    private static void require(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static ClassNode node(String path) throws Exception {
        try (var stream = CargoDeliveryCheck.class.getResourceAsStream("/" + path + ".class")) {
            if (stream == null) throw new AssertionError("Missing class: " + path);
            ClassNode node = new ClassNode(); new ClassReader(stream).accept(node, 0); return node;
        }
    }
    public static void main(String[] args) throws Exception {
        CompoundTag custom = new CompoundTag(); custom.putString("other_mod:data", "preserved");
        CargoParcelIdentity.ensure(custom);
        UUID parcel = CargoParcelIdentity.read(custom).orElseThrow(), finalPoint = UUID.randomUUID();
        var initial = custom.copy();
        for (int i = 0; i < 100; i++) CargoParcelIdentity.ensure(custom);
        require(custom.equals(initial), "identity remains stable through repeated processing");
        var journey = new CargoTransshipment.Journey(finalPoint, List.of(finalPoint), null, true);
        CargoTransshipment.write(custom, journey);
        CargoPackageAssignment.writeAssignment(custom, UUID.randomUUID(), UUID.randomUUID());
        require(CargoParcelIdentity.read(custom).orElseThrow().equals(parcel), "transshipment and train changes preserve parcel UUID");
        require(custom.getString("other_mod:data").equals("preserved"), "identity leaves other custom data intact");
        boolean badId = false;
        var malformed = new CompoundTag(); malformed.putString(CargoParcelIdentity.KEY, "bad");
        try { CargoParcelIdentity.ensure(malformed); } catch (IllegalArgumentException expected) { badId = true; }
        require(badId && malformed.getString(CargoParcelIdentity.KEY).equals("bad"), "malformed identity is never silently replaced");

        long[] expected = {64, 8}, before = {100, 20}, after = {164, 28};
        require(CargoUnpackingEvidence.complete(false, true, expected, before, after), "exact increase of every component-sensitive type proves full insertion");
        require(!CargoUnpackingEvidence.complete(true, true, expected, before, after), "simulation cannot confirm delivery");
        require(!CargoUnpackingEvidence.complete(false, false, expected, before, after), "native failure cannot confirm delivery");
        require(!CargoUnpackingEvidence.complete(false, true, expected, before, before), "boolean true with no stock change is insufficient");
        require(!CargoUnpackingEvidence.complete(false, true, expected, before, new long[]{164, 27}), "partial insertion denied");
        require(!CargoUnpackingEvidence.complete(false, true, expected, before, new long[]{164, 29}), "unexpected stock changes denied conservatively");
        require(!CargoUnpackingEvidence.complete(false, true, new long[]{}, new long[]{}, new long[]{}), "empty box is not evidence of received goods");
        require(!CargoUnpackingEvidence.complete(false, true, expected, before, new long[]{99, 28}), "consumption/transformation during insertion stays unconfirmed");

        var receipts = new CargoDeliveryReceipts();
        var receipt = new CargoDeliveryReceipts.Receipt(parcel, finalPoint, ResourceLocation.parse("minecraft:overworld"),
                new BlockPos(10, 64, 11), new BlockPos(9, 64, 11), 200);
        require(receipts.record(receipt), "first observed full insertion creates receipt");
        require(!receipts.record(receipt), "repeated event cannot count twice");
        var conflicting = new CargoDeliveryReceipts.Receipt(parcel, UUID.randomUUID(), receipt.dimension(), receipt.inventory(), receipt.packager(), 999);
        require(!receipts.record(conflicting) && receipts.receipt(parcel).orElseThrow().equals(receipt), "same UUID cannot overwrite previous final destination");
        var path = Files.createTempFile("ccec-delivery-receipts-", ".dat");
        try {
            CompoundTag checkpoint = new CompoundTag(); checkpoint.put("Receipts", receipts.save(new CompoundTag(), null)); checkpoint.put("Custom", custom);
            NbtIo.writeCompressed(checkpoint, path);
            var restored = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            var loaded = CargoDeliveryReceipts.load(restored.getCompound("Receipts"), null);
            require(loaded.receipt(parcel).orElseThrow().equals(receipt), "receipt and receiving inventory identity survive disk restart");
            require(!loaded.record(receipt) && !loaded.record(conflicting), "repeat/conflicting events remain idempotent after restart");
            require(CargoParcelIdentity.read(restored.getCompound("Custom")).orElseThrow().equals(parcel), "physical identity survives checkpoint");
            require(loaded.receipt(UUID.randomUUID()).isEmpty(), "moving/arrived parcel without receipt is not delivered");
        } finally { Files.deleteIfExists(path); }
        var duplicate = receipts.save(new CompoundTag(), null); var list = duplicate.getList("Receipts", 10); list.add(list.getCompound(0).copy());
        boolean rejected = false;
        try { CargoDeliveryReceipts.load(duplicate, null); } catch (IllegalArgumentException expectedFailure) { rejected = true; }
        require(rejected, "duplicate receipt UUID on disk fails closed");

        var packager = node("com/simibubi/create/content/logistics/packager/PackagerBlockEntity");
        var unwrap = packager.methods.stream().filter(method -> method.name.equals("unwrapBox")).findFirst().orElseThrow();
        int nativeCalls = 0;
        for (var instruction : unwrap.instructions) if (instruction instanceof MethodInsnNode call
                && call.owner.equals("com/simibubi/create/api/packager/unpacking/UnpackingHandler") && call.name.equals("unpack")) nativeCalls++;
        require(nativeCalls == 1, "pinned Create has exactly one unpack observation point");
        var pack = packager.methods.stream().filter(method -> method.name.equals("attemptToSend")
                && method.desc.equals("(Ljava/util/List;)V")).findFirst().orElseThrow();
        int addressWrites = 0;
        boolean readSign = false;
        for (var instruction : pack.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field && field.name.equals("signBasedAddress")) {
                readSign = true;
            }
            if (instruction instanceof MethodInsnNode call && call.owner.endsWith("/PackageItem")
                    && call.name.equals("addAddress")) {
                require(readSign == (addressWrites > 0), "order address write precedes the native sign address write");
                addressWrites++;
            }
        }
        require(addressWrites == 2, "pinned Create has distinct order and sign address writes");
        var mixin = node("com/example/examplemod/mixin/PackagerCargoReceiptMixin");
        var hook = mixin.methods.stream().filter(method -> method.name.equals("ccec$observeNativeUnpack")).findFirst().orElseThrow();
        require(hook.desc.endsWith("Lnet/minecraft/world/item/ItemStack;Z)Z"), "hook captures original box and enclosing simulate parameter");
        var observer = node("com/example/examplemod/cargo/CargoUnpackingObserver");
        var observe = observer.methods.stream().filter(method -> method.name.equals("unpack")).findFirst().orElseThrow();
        int delegated = 0;
        for (var instruction : observe.instructions) if (instruction instanceof MethodInsnNode call) {
            if (call.owner.equals("com/simibubi/create/api/packager/unpacking/UnpackingHandler") && call.name.equals("unpack")) delegated++;
            require(!call.name.equals("insertItem") && !call.name.equals("extractItem"), "observer does not move items");
        }
        require(delegated == 1, "native unpack delegated once without retry");
        System.out.println("PASS: " + checks + " parcel identity, conservative unpacking evidence, receipt persistence and deduplication checks");
    }
}
