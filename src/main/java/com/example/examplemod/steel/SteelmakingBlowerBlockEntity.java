package com.example.examplemod.steel;

import com.example.examplemod.CreateCityExpansionCore;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public final class SteelmakingBlowerBlockEntity extends KineticBlockEntity {
    public SteelmakingBlowerBlockEntity(BlockPos pos, BlockState state) { super(CreateCityExpansionCore.STEELMAKING_BLOWER_BLOCK_ENTITY.get(),pos,state); }
    public float rpm(){ return Math.abs(getSpeed()); }
}
