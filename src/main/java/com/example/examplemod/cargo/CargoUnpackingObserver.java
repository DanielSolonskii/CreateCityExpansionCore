package com.example.examplemod.cargo;

import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Observes exactly one native unpack call. Does not insert, extract, retry or unpack any goods itself. */
public final class CargoUnpackingObserver {
    private static final int MAX_SLOTS = 4096;
    private record Observation(ServerLevel level, BlockEntity target, IItemHandler inventory, int slots,
                               UUID parcel, UUID finalPoint, List<ItemStack> types, long[] expected, long[] before) {}
    private CargoUnpackingObserver() {}

    public static boolean unpack(UnpackingHandler nativeHandler, Level level, BlockPos target, BlockState state, Direction side,
                                 List<ItemStack> items, PackageOrderWithCrafts context, boolean simulate, ItemStack box, BlockPos packager) {
        if(level instanceof ServerLevel server){
            UUID parcel;
            try{parcel=CargoParcelIdentity.id(box).orElse(null);}catch(IllegalArgumentException malformed){return false;}
            if(parcel!=null&&com.example.examplemod.cargo.center.CargoShipmentRegistry.get(server.getServer()).recoveredParcel(parcel))return false;
        }
        Observation observation = null;
        if (!simulate && nativeHandler == UnpackingHandler.DEFAULT) {
            try { observation = begin(level, target, state, side, items, box); }
            catch (RuntimeException unsupported) { /* Unknown observation contract: preserve native call, do not confirm. */ }
        }
        boolean result = nativeHandler.unpack(level, target, state, side, items, context, simulate);
        if (result && observation != null) {
            try { finish(observation, target, packager); }
            catch (RuntimeException unsupported) { /* Native goods already moved: never retry or fabricate a receipt. */ }
        }
        return result;
    }

    private static Observation begin(Level level, BlockPos pos, BlockState state, Direction side, List<ItemStack> items, ItemStack box) {
        if (!(level instanceof ServerLevel server) || !server.getServer().isSameThread() || box.getCount() != 1 || !PackageItem.isPackage(box)
                || !level.hasChunkAt(pos) || items.isEmpty() || items.size() > 9) return null;
        UUID parcel = CargoParcelIdentity.id(box).orElse(null);
        var address = PackageItem.getAddress(box);
        var route = CargoAddress.parse(address).orElse(null);
        var journey = CargoTransshipment.read(box.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
        if (parcel == null || route == null || journey == null || !journey.arrived()
                || !journey.finalPoint().equals(route.finalPoint()) || CargoPointRegistry.find(server.getServer(), route.finalPoint()).isEmpty()
                || CargoDeliveryReceipts.find(server.getServer(), parcel).isPresent()) return null;
        BlockEntity target = level.getBlockEntity(pos);
        // Deliberately exclude arbitrary mod inventories and custom UnpackingHandler implementations.
        if (target == null || target.isRemoved() || !(target.getClass() == ChestBlockEntity.class
                || target.getClass() == BarrelBlockEntity.class || target.getClass() == ItemVaultBlockEntity.class)) return null;
        IItemHandler inventory = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, target, side);
        if (inventory == null || inventory.getSlots() < 1 || inventory.getSlots() > MAX_SLOTS) return null;
        List<ItemStack> types = new ArrayList<>(); List<Long> amounts = new ArrayList<>();
        for (ItemStack item : items) {
            if (item.isEmpty()) continue;
            int type = -1;
            for (int i = 0; i < types.size(); i++) if (ItemStack.isSameItemSameComponents(types.get(i), item)) { type = i; break; }
            if (type < 0) { types.add(item.copyWithCount(1)); amounts.add((long) item.getCount()); }
            else amounts.set(type, Math.addExact(amounts.get(type), item.getCount()));
        }
        if (types.isEmpty()) return null;
        return new Observation(server, target, inventory, inventory.getSlots(), parcel, route.finalPoint(), types,
                amounts.stream().mapToLong(Long::longValue).toArray(), totals(inventory, types));
    }

    private static long[] totals(IItemHandler inventory, List<ItemStack> types) {
        long[] counts = new long[types.size()];
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            for (int i = 0; i < types.size(); i++) if (ItemStack.isSameItemSameComponents(types.get(i), stack))
                counts[i] = Math.addExact(counts[i], stack.getCount());
        }
        return counts;
    }
    private static void finish(Observation proof, BlockPos target, BlockPos packager) {
        if (!proof.level().hasChunkAt(target) || proof.level().getBlockEntity(target) != proof.target()
                || proof.target().isRemoved() || proof.inventory().getSlots() != proof.slots()) return;
        if (!CargoUnpackingEvidence.complete(false, true, proof.expected(), proof.before(), totals(proof.inventory(), proof.types()))) return;
        CargoDeliveryReceipts.get(proof.level().getServer()).record(new CargoDeliveryReceipts.Receipt(proof.parcel(),
                proof.finalPoint(), proof.level().dimension().location(), target, packager, proof.level().getGameTime()));
    }
}
