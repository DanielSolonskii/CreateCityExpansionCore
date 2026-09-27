package com.example.examplemod.coke;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public final class CokeBatteryInputBlockEntity extends BlockEntity {
    private final ItemStackHandler buffer = new ItemStackHandler(4) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.is(Items.COAL); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final IItemHandler external = new InsertOnlyHandler();

    public CokeBatteryInputBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCityExpansionCore.COKE_BATTERY_INPUT_BLOCK_ENTITY.get(), pos, state);
    }

    public IItemHandler itemHandler() { return external; }

    int coalCount() {
        int count = 0;
        for (int slot = 0; slot < buffer.getSlots(); slot++) count += buffer.getStackInSlot(slot).getCount();
        return count;
    }

    boolean consumeCoal(int amount) {
        if (coalCount() < amount) return false;
        int remaining = amount;
        for (int slot = 0; slot < buffer.getSlots() && remaining > 0; slot++) {
            remaining -= buffer.extractItem(slot, remaining, false).getCount();
        }
        return remaining == 0;
    }

    void restoreCoal(int amount) {
        ItemStack remainder = ItemHandlerHelper.insertItem(buffer, new ItemStack(Items.COAL, amount), false);
        if (!remainder.isEmpty() && level instanceof ServerLevel serverLevel) {
            Block.popResource(serverLevel, worldPosition, remainder);
        }
    }

    public void dropContents(ServerLevel level) {
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            ItemStack stack = buffer.getStackInSlot(slot);
            if (!stack.isEmpty()) Block.popResource(level, worldPosition, stack.copy());
            buffer.setStackInSlot(slot, ItemStack.EMPTY);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Buffer", buffer.serializeNBT(registries));
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Buffer", Tag.TAG_COMPOUND)) buffer.deserializeNBT(registries, tag.getCompound("Buffer"));
    }

    private final class InsertOnlyHandler implements IItemHandler {
        @Override public int getSlots() { return buffer.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return buffer.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack.is(Items.COAL) ? buffer.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return buffer.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.is(Items.COAL); }
    }
}
