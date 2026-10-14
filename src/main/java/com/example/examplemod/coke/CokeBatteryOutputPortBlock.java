package com.example.examplemod.coke;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class CokeBatteryOutputPortBlock extends Block implements EntityBlock {
    public CokeBatteryOutputPortBlock(Properties properties) { super(properties); }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CokeBatteryOutputBlockEntity(pos, state);
    }

    @Override protected void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved
    ) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof CokeBatteryOutputBlockEntity output) {
            output.dropContents(serverLevel);
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
