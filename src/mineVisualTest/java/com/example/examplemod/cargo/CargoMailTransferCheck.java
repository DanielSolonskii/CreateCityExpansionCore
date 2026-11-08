package com.example.examplemod.cargo;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tests the production assignment codec/policy and exact Create bytecode, not a simulated Minecraft. */
public final class CargoMailTransferCheck {
    private static int checks;

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2)
            if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        throw new AssertionError("Missing annotation value: " + key);
    }

    private static ClassNode readClass(String name) throws Exception {
        try (var stream = CargoMailTransferCheck.class.getResourceAsStream("/" + name + ".class")) {
            if (stream == null) throw new AssertionError("Class unavailable: " + name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }

    private static void bytecodeContract() throws Exception {
        var station = readClass("com/simibubi/create/content/trains/station/GlobalStation");
        MethodNode transfer = station.methods.stream().filter(m -> m.name.equals("runMailTransfer") && m.desc.equals("()V"))
                .findFirst().orElseThrow();
        List<Integer> reads = new ArrayList<>(), writes = new ArrayList<>(), insertions = new ArrayList<>();
        String readTarget = "Lnet/neoforged/neoforge/items/IItemHandlerModifiable;getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;";
        boolean offline = false, loaded = false;
        for (int i = 0; i < transfer.instructions.size(); i++) {
            AbstractInsnNode instruction = transfer.instructions.get(i);
            if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field) {
                offline |= field.owner.endsWith("/GlobalPackagePort") && field.name.equals("offlineBuffer") && reads.isEmpty();
                loaded |= field.owner.endsWith("/PostboxBlockEntity") && field.name.equals("inventory") && reads.isEmpty();
            }
            if (!(instruction instanceof MethodInsnNode call)) continue;
            String target = "L" + call.owner + ";" + call.name + call.desc;
            if (target.equals(readTarget)) reads.add(i);
            if (call.owner.equals("net/neoforged/neoforge/items/IItemHandlerModifiable") && call.name.equals("setStackInSlot")) writes.add(i);
            if (call.owner.equals("net/neoforged/neoforge/items/ItemHandlerHelper") && call.name.equals("insertItemStacked")) insertions.add(i);
        }
        require(reads.size() == 2 && writes.size() == 2 && insertions.size() == 2, "pinned native transfer shape");
        require(offline && loaded, "outgoing read shares offline and loaded Postbox branches");
        require(reads.get(0) < insertions.get(0) && insertions.get(0) < writes.get(0)
                && writes.get(0) < reads.get(1) && reads.get(1) < insertions.get(1)
                && insertions.get(1) < writes.get(1), "ordinal zero is outbound; native unload remains separate");

        var mixin = readClass("com/example/examplemod/mixin/GlobalStationCargoMixin");
        var handler = mixin.methods.stream().filter(m -> m.name.equals("ccec$filterOutgoingPackage")).findFirst().orElseThrow();
        var redirect = handler.visibleAnnotations.stream().filter(a -> a.desc.endsWith("/Redirect;")).findFirst().orElseThrow();
        require(annotationValue(redirect, "method").equals(List.of("runMailTransfer")), "only mail transfer targeted");
        require(annotationValue(redirect, "require").equals(1) && annotationValue(redirect, "allow").equals(1), "fail on missing/extra injection target");
        AnnotationNode at = (AnnotationNode) annotationValue(redirect, "at");
        require(annotationValue(at, "target").equals(readTarget) && annotationValue(at, "ordinal").equals(0), "only outbound slot read redirected");
        require(annotationValue(redirect, "remap").equals(false), "external Create method is not remapped");
    }

    public static void main(String[] args) throws Exception {
        UUID train = UUID.randomUUID(), other = UUID.randomUUID(), owner = UUID.randomUUID();
        CompoundTag custom = new CompoundTag();
        custom.putString("other_mod:data", "preserved");
        require(CargoPackageAssignment.permits(custom, 1, null, null, false), "unmanaged mail needs no CCEC registration");
        CargoPackageAssignment.writeAssignment(custom, train, owner);
        var original = custom.copy();
        for (var mode : CargoTrainRegistry.AccessMode.values()) {
            var registration = new CargoTrainRegistry.Registration(owner, train, "Train", mode);
            require(CargoPackageAssignment.permits(custom, 1, train, registration, true), "assigned train allowed in " + mode);
            require(!CargoPackageAssignment.permits(custom, 1, other, registration, true), "foreign train denied in " + mode);
            require(!CargoPackageAssignment.permits(custom, 1, train, registration, false), "removed train denied");
            require(!CargoPackageAssignment.permits(custom, 2, train, registration, true), "invalid stacked packages denied to prevent partial-insert duplication");
            require(!CargoPackageAssignment.permits(custom, 1, train, null, true), "unregistered train denied");
            var changedOwner = new CargoTrainRegistry.Registration(other, train, "Train", mode);
            require(!CargoPackageAssignment.permits(custom, 1, train, changedOwner, true), "changed registration owner denied");
        }
        require(custom.equals(original), "refused/allowed reads never modify physical data");
        require(custom.getString("other_mod:data").equals("preserved"), "other custom data preserved");
        var path = Files.createTempFile("ccec-mail-assignment-", ".dat");
        try {
            NbtIo.writeCompressed(custom, path);
            var restored = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            require(restored.equals(custom), "assignment survives compressed disk round-trip");
            var registration = new CargoTrainRegistry.Registration(owner, train, "Same name", CargoTrainRegistry.AccessMode.PRIVATE);
            require(CargoPackageAssignment.permits(restored, 1, train, registration, true), "right train allowed after reload");
            require(!CargoPackageAssignment.permits(restored, 1, other, registration, true), "reassembled UUID never inherits assignment");
        } finally { Files.deleteIfExists(path); }
        for (String key : new String[]{"Train", "Owner", "Version"}) {
            var broken = custom.copy();
            broken.getCompound(CargoPackageAssignment.KEY).remove(key);
            require(!CargoPackageAssignment.permits(broken, 1, train,
                    new CargoTrainRegistry.Registration(owner, train, "Train", CargoTrainRegistry.AccessMode.PUBLIC), true), "missing " + key + " fails closed");
        }
        var malformed = new CompoundTag();
        malformed.putString(CargoPackageAssignment.KEY, "broken");
        require(!CargoPackageAssignment.permits(malformed, 1, train, null, true), "malformed marker cannot become unmanaged");
        bytecodeContract();
        System.out.println("PASS: " + checks + " cargo assignment, persistence and Create bytecode contract checks");
    }
}
