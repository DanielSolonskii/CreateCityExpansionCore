package com.example.examplemod.cargo;

import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.function.BooleanSupplier;
import java.util.function.IntPredicate;

/** Capability view over the existing buffer: insert packages, extract only persisted final arrivals. */
final class CargoRouterInputHandler implements IItemHandler {
    private final IItemHandler buffer;
    private final BooleanSupplier active;
    private final IntPredicate extractable;

    CargoRouterInputHandler(IItemHandler buffer, BooleanSupplier active, IntPredicate extractable) {
        this.buffer = buffer;
        this.active = active;
        this.extractable = extractable;
    }

    @Override public int getSlots() { return buffer.getSlots(); }

    @Override public ItemStack getStackInSlot(int slot) { return buffer.getStackInSlot(slot).copy(); }

    @Override public int getSlotLimit(int slot) { return buffer.getSlotLimit(slot); }

    @Override public boolean isItemValid(int slot, ItemStack stack) {
        return active.getAsBoolean() && !stack.isEmpty() && PackageItem.isPackage(stack)
                && buffer.getStackInSlot(slot).isEmpty() && buffer.isItemValid(slot, stack);
    }

    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!isItemValid(slot, stack)) return stack;
        // Native insertion owns its copy, honors the one-item slot limit and returns all unaccepted items.
        // Only a real insertion triggers Router's existing callback: ready=false and setChanged().
        return buffer.insertItem(slot, stack, simulate);
    }

    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount <= 0 || !active.getAsBoolean() || !extractable.test(slot)) return ItemStack.EMPTY;
        // No unpacking, receipt or metadata edits here. Native extraction honors simulate and returns the full box.
        return buffer.extractItem(slot, Math.min(amount, 1), simulate);
    }
}
