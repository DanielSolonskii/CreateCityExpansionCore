package com.example.examplemod.steel;

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

public final class SteelConverterFluidInputBlockEntity extends BlockEntity {
    public static final int CAPACITY_MB = 16_000;
    private final FluidTank tank = new FluidTank(CAPACITY_MB, s -> s.is(ModFluids.HOT_METAL.get())) {
        @Override protected void onContentsChanged() { setChanged(); }
    };
    public SteelConverterFluidInputBlockEntity(BlockPos pos, BlockState state) {
        super(CreateCityExpansionCore.STEEL_CONVERTER_FLUID_INPUT_BLOCK_ENTITY.get(), pos, state);
    }
    public IFluidHandler fluidHandler() { return tank; }
    public int amount() { return tank.getFluidAmount(); }
    public boolean canDrain(int amount) { return tank.drain(amount, IFluidHandler.FluidAction.SIMULATE).getAmount() == amount; }
    public FluidStack drain(int amount) { return tank.drain(amount, IFluidHandler.FluidAction.EXECUTE); }
    public int restore(FluidStack stack) { return tank.fill(stack, IFluidHandler.FluidAction.EXECUTE); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries); tag.put("Tank", tank.writeToNBT(registries, new CompoundTag()));
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries); if (tag.contains("Tank", Tag.TAG_COMPOUND)) tank.readFromNBT(registries, tag.getCompound("Tank"));
    }
}
