package com.example.examplemod.mine;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class MineGatewayBuffer {

    public static final int SLOT_COUNT = 9;
    private final ItemStackHandler handler;

    public MineGatewayBuffer(Runnable changedCallback) {
        handler = new ItemStackHandler(SLOT_COUNT) {
            @Override
            protected void onContentsChanged(int slot) {
                changedCallback.run();
            }
        };
    }

    public ItemStackHandler handler() {
        return handler;
    }

    public boolean canInsert(ItemStack stack) {
        return insertAcrossSlots(stack.copy(), true).isEmpty();
    }

    public void insertExact(ItemStack stack) {
        ItemStack remainder = insertAcrossSlots(stack.copy(), false);
        if (!remainder.isEmpty()) {
            throw new IllegalStateException(
                    "Gateway capacity changed during an atomic mine operation"
            );
        }
    }

    public int itemCount() {
        int count = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            count += handler.getStackInSlot(slot).getCount();
        }
        return count;
    }

    public int totalCapacity() {
        int capacity = 0;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            capacity += handler.getSlotLimit(slot);
        }
        return capacity;
    }

    public CompoundTag serialize(HolderLookup.Provider registries) {
        java.util.List<MineGatewayStoredStack> stacks = new java.util.ArrayList<>();
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                stacks.add(new MineGatewayStoredStack(
                        slot,
                        BuiltInRegistries.ITEM.getKey(stack.getItem()),
                        stack.getCount()
                ));
            }
        }
        return MineGatewayStorageCodec.save(stacks);
    }

    public void deserialize(
            HolderLookup.Provider registries,
            CompoundTag tag
    ) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            handler.setStackInSlot(slot, ItemStack.EMPTY);
        }
        for (MineGatewayStoredStack stored : MineGatewayStorageCodec.load(tag)) {
            BuiltInRegistries.ITEM.getOptional(stored.itemId()).ifPresent(item ->
                    handler.setStackInSlot(
                            stored.slot(),
                            new ItemStack(item, stored.count())
                    )
            );
        }
    }

    private ItemStack insertAcrossSlots(ItemStack input, boolean simulate) {
        ItemStack remainder = input;
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, simulate);
        }
        return remainder;
    }
}
