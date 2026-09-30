package com.example.examplemod.steel;

import com.example.examplemod.CreateCityExpansionCore;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class SteelmakingBlowerPortBlock extends HorizontalKineticBlock implements IBE<SteelmakingBlowerBlockEntity> {
    public SteelmakingBlowerPortBlock(Properties properties){super(properties);}
    @Override public Direction.Axis getRotationAxis(BlockState state){return state.getValue(HORIZONTAL_FACING).getAxis();}
    @Override public boolean hasShaftTowards(LevelReader level, net.minecraft.core.BlockPos pos, BlockState state, Direction face){return face.getAxis()==getRotationAxis(state);}
    @Override public Class<SteelmakingBlowerBlockEntity> getBlockEntityClass(){return SteelmakingBlowerBlockEntity.class;}
    @Override public BlockEntityType<? extends SteelmakingBlowerBlockEntity> getBlockEntityType(){return CreateCityExpansionCore.STEELMAKING_BLOWER_BLOCK_ENTITY.get();}
}
