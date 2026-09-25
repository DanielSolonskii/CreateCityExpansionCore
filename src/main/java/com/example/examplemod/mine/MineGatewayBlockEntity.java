package com.example.examplemod.mine;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.world.item.ItemStack;

public final class MineGatewayBlockEntity extends BlockEntity {

    private static final String OUTPUT_TAG = "Output";
    private final MineGatewayBuffer output = new MineGatewayBuffer(this::setChanged);
    private final IItemHandler externalOutput = new IItemHandler() {
        @Override
        public int getSlots() {
            return output.handler().getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return output.handler().getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return output.handler().extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return output.handler().getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    };

    public MineGatewayBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCityExpansionCore.MINE_GATEWAY_BLOCK_ENTITY.get(), pos, state);
    }

    public MineGatewayBuffer output() {
        return output;
    }

    public IItemHandler itemHandler() {
        return externalOutput;
    }

    @Override
    protected void saveAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.saveAdditional(tag, registries);
        tag.put(OUTPUT_TAG, output.serialize(registries));
    }

    @Override
    protected void loadAdditional(
            CompoundTag tag,
            HolderLookup.Provider registries
    ) {
        super.loadAdditional(tag, registries);
        if (tag.contains(OUTPUT_TAG)) {
            output.deserialize(registries, tag.getCompound(OUTPUT_TAG));
        }
    }
}
