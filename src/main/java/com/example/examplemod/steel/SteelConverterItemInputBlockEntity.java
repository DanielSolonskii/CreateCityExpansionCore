package com.example.examplemod.steel;

import com.example.examplemod.CreateCityExpansionCore;
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
import net.neoforged.neoforge.items.ItemStackHandler;

public final class SteelConverterItemInputBlockEntity extends BlockEntity {
    private final ItemStackHandler buffer = new ItemStackHandler(4) {
        @Override public boolean isItemValid(int slot, ItemStack stack) { return stack.is(CreateCityExpansionCore.QUICKLIME.get()); }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    public SteelConverterItemInputBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCityExpansionCore.STEEL_CONVERTER_ITEM_INPUT_BLOCK_ENTITY.get(), pos, state);
    }
    public IItemHandler itemHandler() { return buffer; }
    public int count() { int n=0; for(int i=0;i<buffer.getSlots();i++) n += buffer.getStackInSlot(i).getCount(); return n; }
    public boolean canConsume(int amount) { return count() >= amount; }
    public boolean consume(int amount) {
        if (!canConsume(amount)) return false;
        int left=amount; for(int i=0;i<buffer.getSlots() && left>0;i++) left -= buffer.extractItem(i,left,false).getCount();
        return left == 0;
    }
    public void restore(int amount) {
        ItemStack left = new ItemStack(CreateCityExpansionCore.QUICKLIME.get(), amount);
        for(int i=0;i<buffer.getSlots() && !left.isEmpty();i++) left = buffer.insertItem(i,left,false);
        if (!left.isEmpty() && level instanceof ServerLevel server) Block.popResource(server, worldPosition, left);
    }
    public void dropContents(ServerLevel level) {
        for(int i=0;i<buffer.getSlots();i++){ ItemStack s=buffer.getStackInSlot(i); if(!s.isEmpty()) Block.popResource(level,worldPosition,s.copy()); buffer.setStackInSlot(i,ItemStack.EMPTY); }
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.saveAdditional(tag,registries); tag.put("Buffer",buffer.serializeNBT(registries)); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) { super.loadAdditional(tag,registries); if(tag.contains("Buffer",Tag.TAG_COMPOUND)) buffer.deserializeNBT(registries,tag.getCompound("Buffer")); }
}
