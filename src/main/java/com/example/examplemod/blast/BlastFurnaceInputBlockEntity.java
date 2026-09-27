package com.example.examplemod.blast;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.item.IndustrialMaterials;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

public final class BlastFurnaceInputBlockEntity extends BlockEntity {
    public static final int SLOT_COUNT = 9;
    private final ItemStackHandler buffer = new ItemStackHandler(SLOT_COUNT) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return accepts(stack); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final IItemHandler external = new InsertOnlyHandler();

    public BlastFurnaceInputBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCityExpansionCore.BLAST_FURNACE_INPUT_BLOCK_ENTITY.get(), pos, state);
    }

    public IItemHandler itemHandler() { return external; }
    public int ironCount() { return count(Items.IRON_INGOT); }
    public int cokeCount() { return count(IndustrialMaterials.INDUSTRIAL_COKE.get()); }
    public int calciteCount() { return count(Items.CALCITE); }
    public boolean hasRecipe() { return ironCount() >= 8 && cokeCount() >= 4 && calciteCount() >= 2; }

    boolean consumeRecipe() {
        if (!hasRecipe()) return false;
        return consume(Items.IRON_INGOT, 8)
                && consume(IndustrialMaterials.INDUSTRIAL_COKE.get(), 4)
                && consume(Items.CALCITE, 2);
    }

    void restoreRecipe() {
        restore(Items.IRON_INGOT, 8);
        restore(IndustrialMaterials.INDUSTRIAL_COKE.get(), 4);
        restore(Items.CALCITE, 2);
    }

    private int count(Item item) {
        int count = 0;
        for (int slot = 0; slot < buffer.getSlots(); slot++)
            if (buffer.getStackInSlot(slot).is(item)) count += buffer.getStackInSlot(slot).getCount();
        return count;
    }

    private boolean consume(Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < buffer.getSlots() && remaining > 0; slot++) {
            if (buffer.getStackInSlot(slot).is(item))
                remaining -= buffer.extractItem(slot, remaining, false).getCount();
        }
        return remaining == 0;
    }

    private void restore(Item item, int amount) {
        ItemStack remainder = ItemHandlerHelper.insertItem(buffer, new ItemStack(item, amount), false);
        if (!remainder.isEmpty() && level instanceof ServerLevel serverLevel)
            Block.popResource(serverLevel, worldPosition, remainder);
    }

    private static boolean accepts(ItemStack stack) {
        return stack.is(Items.IRON_INGOT) || stack.is(Items.CALCITE)
                || stack.is(IndustrialMaterials.INDUSTRIAL_COKE.get());
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
            return accepts(stack) ? buffer.insertItem(slot, stack, simulate) : stack;
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return ItemStack.EMPTY; }
        @Override public int getSlotLimit(int slot) { return buffer.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return accepts(stack); }
    }
}
