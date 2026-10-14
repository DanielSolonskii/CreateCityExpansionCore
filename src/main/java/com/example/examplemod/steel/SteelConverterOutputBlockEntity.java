package com.example.examplemod.steel;

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

public final class SteelConverterOutputBlockEntity extends BlockEntity {
    public static final int CAPACITY = 64;

    private final ItemStackHandler buffer = new ItemStackHandler(1) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(IndustrialMaterials.STEEL_BILLET.get());
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final IItemHandler external = new ExtractOnlyHandler();

    public SteelConverterOutputBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCityExpansionCore.STEEL_CONVERTER_OUTPUT_BLOCK_ENTITY.get(), pos, state);
    }

    public IItemHandler itemHandler() { return external; }
    public int amount() { return buffer.getStackInSlot(0).getCount(); }

    public boolean canAccept(int amount) {
        ItemStack remainder = ItemHandlerHelper.insertItem(
                buffer, new ItemStack(IndustrialMaterials.STEEL_BILLET.get(), amount), true);
        return remainder.isEmpty();
    }

    public boolean insert(int amount) {
        int before = this.amount();
        ItemStack remainder = ItemHandlerHelper.insertItem(
                buffer, new ItemStack(IndustrialMaterials.STEEL_BILLET.get(), amount), false);
        if (remainder.isEmpty()) return true;
        int inserted = this.amount() - before;
        if (inserted > 0) rollback(inserted);
        return false;
    }

    public void rollback(int amount) {
        buffer.extractItem(0, amount, false);
    }

    public void dropContents(ServerLevel level) {
        ItemStack stack = buffer.getStackInSlot(0);
        if (!stack.isEmpty()) Block.popResource(level, worldPosition, stack.copy());
        buffer.setStackInSlot(0, ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Buffer", buffer.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Buffer", Tag.TAG_COMPOUND)) {
            buffer.deserializeNBT(registries, tag.getCompound("Buffer"));
        }
    }

    private final class ExtractOnlyHandler implements IItemHandler {
        @Override public int getSlots() { return buffer.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return buffer.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return buffer.extractItem(slot, amount, simulate);
        }
        @Override public int getSlotLimit(int slot) { return CAPACITY; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    }
}
