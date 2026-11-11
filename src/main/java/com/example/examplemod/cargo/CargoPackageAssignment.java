package com.example.examplemod.cargo;

import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.entity.Train;
import com.example.examplemod.cargo.center.CargoOwners;
import com.example.examplemod.cargo.center.OwnerRef;
import com.example.examplemod.cargo.center.CargoNavigation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.UUID;

/** Assignment travels with the physical stack, including Create's persisted offline buffer. */
public final class CargoPackageAssignment {
    static final String KEY = "ccec:train_assignment";

    private CargoPackageAssignment() {}

    /**
     * Server API for the actual held package, never a client-supplied ItemStack.
     * No copies of the goods are stored in SavedData. An existing assignment cannot be replaced.
     */
    public static boolean assignHeldPackage(ServerPlayer player, InteractionHand hand, UUID trainId) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread() || trainId == null) return false;
        ItemStack stack = player.getItemInHand(hand);
        if (!assignToOwnedTrain(server, player.getUUID(), stack, trainId, false)) return false;
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }

    /** Trusted Router custody allows replacing the previous leg; no client ItemStack is accepted by Router API. */
    static boolean assignBufferedPackage(MinecraftServer server, UUID owner, ItemStack stack, UUID trainId) {
        return assignBufferedPackage(server, OwnerRef.player(owner), stack, trainId);
    }

    static boolean assignBufferedPackage(MinecraftServer server, OwnerRef requester, ItemStack stack, UUID trainId) {
        if (stack.getCount() != 1 || !PackageItem.isPackage(stack) || !mayAssignTrain(server, requester, trainId)) return false;
        var registration = CargoTrainRegistry.find(server, trainId).orElseThrow();
        CompoundTag custom = customData(stack);
        if (CargoAddress.parse(PackageItem.getAddress(stack)).isPresent()) {
            try { CargoParcelIdentity.ensure(custom); } catch (IllegalArgumentException invalid) { return false; }
        }
        writeAssignment(custom, trainId, registration.ownerId());
        custom.getCompound(KEY).put("Requester", requester.save());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        return true;
    }

    static boolean assignService(MinecraftServer server, ItemStack stack, CargoNavigation.Dispatch dispatch) {
        if (!CargoNavigation.serviceAllowed(server, dispatch.line(), dispatch.customer(), dispatch.carrier(), dispatch.train())
                || !assignBufferedPackage(server, dispatch.carrier(), stack, dispatch.train())) return false;
        var custom=customData(stack);custom.getCompound(KEY).putUUID("Line",dispatch.line());
        custom.getCompound(KEY).put("Customer",dispatch.customer().save());
        stack.set(DataComponents.CUSTOM_DATA,CustomData.of(custom));return true;
    }

    private static boolean assignToOwnedTrain(MinecraftServer server, UUID owner, ItemStack stack,
                                              UUID trainId, boolean replacePreviousLeg) {
        if (stack.getCount() != 1 || !PackageItem.isPackage(stack)) return false;
        var registered = assignableTrain(server, owner, trainId);
        if (registered == null) return false;
        CompoundTag custom = customData(stack);
        if (CargoAddress.parse(PackageItem.getAddress(stack)).isPresent()) {
            try { CargoParcelIdentity.ensure(custom); } catch (IllegalArgumentException invalidIdentity) { return false; }
        }
        if (custom.contains(KEY) && !replacePreviousLeg) {
            if (!permits(custom, stack.getCount(), trainId, registered, true)) return false;
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
            return true;
        }
        writeAssignment(custom, trainId, registered.ownerId());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        return true;
    }

    /** Same live-train and ownership checks for rule configuration and actual package assignment. */
    static boolean mayAssignTrain(MinecraftServer server, UUID owner, UUID trainId) {
        return mayAssignTrain(server, OwnerRef.player(owner), trainId);
    }

    public static boolean mayAssignTrain(MinecraftServer server, OwnerRef requester, UUID trainId) {
        if (server == null || !server.isSameThread() || requester == null || trainId == null) return false;
        var registered = CargoTrainRegistry.find(server, trainId).orElse(null);
        Train train = RailwaySavedData.load(server).getTrains().get(trainId);
        return registered != null && train != null && train.id.equals(trainId)
                && registered.ownerId().equals(train.owner) && CargoOwners.train(server, requester, registered);
    }

    private static CargoTrainRegistry.Registration assignableTrain(MinecraftServer server, UUID owner, UUID trainId) {
        if (server == null || !server.isSameThread() || owner == null || trainId == null) return null;
        var registered = CargoTrainRegistry.find(server, trainId).orElse(null);
        Train train = RailwaySavedData.load(server).getTrains().get(trainId);
        return registered != null && registered.ownerId().equals(owner) && train != null
                && trainId.equals(train.id) && registered.ownerId().equals(train.owner) ? registered : null;
    }

    static boolean readyForDispatch(MinecraftServer server, ItemStack stack) {
        CompoundTag custom = customData(stack);
        if (!custom.contains(KEY, Tag.TAG_COMPOUND)) return false;
        CompoundTag assignment = custom.getCompound(KEY);
        if (!assignment.hasUUID("Train")) return false;
        Train train = RailwaySavedData.load(server).getTrains().get(assignment.getUUID("Train"));
        return !outgoingStack(stack, train, server).isEmpty();
    }
    static UUID assignedTrain(ItemStack stack) {
        var assignment=customData(stack).getCompound(KEY);return assignment.hasUUID("Train")?assignment.getUUID("Train"):null;
    }

    /** Called only at the outbound slot read in GlobalStation.runMailTransfer. Does not mutate inventory. */
    public static ItemStack outgoingStack(ItemStack stack, Train presentTrain, MinecraftServer server) {
        CompoundTag custom = customData(stack);
        if (!custom.contains(KEY)) return stack;
        if (server == null || !server.isSameThread() || presentTrain == null) return ItemStack.EMPTY;
        var registered = CargoTrainRegistry.find(server, presentTrain.id).orElse(null);
        boolean exists = RailwaySavedData.load(server).getTrains().get(presentTrain.id) == presentTrain
                && registered != null && registered.ownerId().equals(presentTrain.owner);
        if (!permits(custom, stack.getCount(), presentTrain.id, registered, exists)) return ItemStack.EMPTY;
        try {
            CompoundTag assignment = custom.getCompound(KEY);
            OwnerRef requester = assignment.contains("Requester") ? OwnerRef.load(assignment.getCompound("Requester"))
                    : OwnerRef.player(assignment.getUUID("Owner"));
            if (assignment.contains("Line") && (!assignment.hasUUID("Line")
                    || !CargoNavigation.serviceAllowed(server, assignment.getUUID("Line"),
                    OwnerRef.load(assignment.getCompound("Customer")), requester, presentTrain.id))) return ItemStack.EMPTY;
            return CargoOwners.train(server, requester, registered) ? stack : ItemStack.EMPTY;
        } catch (IllegalArgumentException invalid) { return ItemStack.EMPTY; }
    }

    private static CompoundTag customData(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    // Shared codec/policy seam: headless tests use these exact methods without bootstrapping game registries.
    static void writeAssignment(CompoundTag custom, UUID trainId, UUID ownerId) {
        CompoundTag assignment = new CompoundTag();
        assignment.putInt("Version", 1);
        assignment.putUUID("Train", trainId);
        assignment.putUUID("Owner", ownerId);
        custom.put(KEY, assignment);
    }

    static boolean permits(CompoundTag custom, int count, UUID actualTrain,
                           CargoTrainRegistry.Registration registered, boolean exists) {
        if (!custom.contains(KEY)) return true;
        if (!custom.contains(KEY, Tag.TAG_COMPOUND) || count != 1 || actualTrain == null
                || registered == null || !exists) return false;
        CompoundTag assignment = custom.getCompound(KEY);
        return assignment.getInt("Version") == 1 && assignment.hasUUID("Train") && assignment.hasUUID("Owner")
                && actualTrain.equals(assignment.getUUID("Train")) && actualTrain.equals(registered.trainId())
                && registered.ownerId().equals(assignment.getUUID("Owner"));
    }
}
