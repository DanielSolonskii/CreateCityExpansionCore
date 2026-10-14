package com.example.examplemod.blast;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.fluid.ModFluids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

public final class BlastFurnaceOutputBlockEntity extends BlockEntity {
    public static final int CAPACITY_MB = 32_000;
    private final FluidTank tank = new FluidTank(CAPACITY_MB,
            stack -> stack.is(ModFluids.HOT_METAL.get())) {
        @Override protected void onContentsChanged() { setChanged(); }
    };
    private final IFluidHandler external = new ExtractOnlyHandler();

    public BlastFurnaceOutputBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCityExpansionCore.BLAST_FURNACE_OUTPUT_BLOCK_ENTITY.get(), pos, state);
    }

    public IFluidHandler fluidHandler() { return external; }
    public int amount() { return tank.getFluidAmount(); }
    int capacityForHotMetal() { return tank.getSpace(); }
    int insertHotMetal(int amount, IFluidHandler.FluidAction action) {
        return tank.fill(new FluidStack(ModFluids.HOT_METAL.get(), amount), action);
    }

    public CompoundTag saveTank(HolderLookup.Provider registries) {
        return tank.writeToNBT(registries, new CompoundTag());
    }

    public void restoreTank(HolderLookup.Provider registries, CompoundTag tag) {
        tank.readFromNBT(registries, tag);
        setChanged();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Tank", Tag.TAG_COMPOUND)) tank.readFromNBT(registries, tag.getCompound("Tank"));
    }

    private final class ExtractOnlyHandler implements IFluidHandler {
        @Override public int getTanks() { return tank.getTanks(); }
        @Override public FluidStack getFluidInTank(int tankIndex) { return tank.getFluidInTank(tankIndex); }
        @Override public int getTankCapacity(int tankIndex) { return tank.getTankCapacity(tankIndex); }
        @Override public boolean isFluidValid(int tankIndex, FluidStack stack) { return false; }
        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) { return tank.drain(resource, action); }
        @Override public FluidStack drain(int maxDrain, FluidAction action) { return tank.drain(maxDrain, action); }
    }
}
