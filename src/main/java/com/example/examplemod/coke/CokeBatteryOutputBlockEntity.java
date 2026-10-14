package com.example.examplemod.coke;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.item.IndustrialMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class CokeBatteryOutputBlockEntity extends BlockEntity {
    private final ItemStackHandler buffer = new ItemStackHandler(4) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(IndustrialMaterials.INDUSTRIAL_COKE.get());
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final IItemHandler external = new ExtractOnlyHandler();

    public CokeBatteryOutputBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCityExpansionCore.COKE_BATTERY_OUTPUT_BLOCK_ENTITY.get(), pos, state);
    }

    public IItemHandler itemHandler() { return external; }

    int cokeCount() {
        int count = 0;
        for (int slot = 0; slot < buffer.getSlots(); slot++) count += buffer.getStackInSlot(slot).getCount();
        return count;
    }

    int insertCapacity(int maximum) {
        ItemStack candidate = new ItemStack(IndustrialMaterials.INDUSTRIAL_COKE.get(), maximum);
        return maximum - ItemHandlerHelper.insertItem(buffer, candidate, true).getCount();
    }

    boolean insertCoke(int amount) {
        ItemStack remainder = ItemHandlerHelper.insertItem(
                buffer, new ItemStack(IndustrialMaterials.INDUSTRIAL_COKE.get(), amount), false
        );
        return remainder.isEmpty();
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

    private final class ExtractOnlyHandler implements IItemHandler {
        @Override public int getSlots() { return buffer.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return buffer.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return buffer.extractItem(slot, amount, simulate);
        }
        @Override public int getSlotLimit(int slot) { return buffer.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    }
}
