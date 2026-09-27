package com.example.examplemod.blast;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class BlastFurnaceInputPortBlock extends Block implements EntityBlock {
    public BlastFurnaceInputPortBlock(Properties properties) { super(properties); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlastFurnaceInputBlockEntity(pos, state);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos,
                                      BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server
                && level.getBlockEntity(pos) instanceof BlastFurnaceInputBlockEntity input)
            input.dropContents(server);
        super.onRemove(state, level, pos, newState, moved);
    }
}
