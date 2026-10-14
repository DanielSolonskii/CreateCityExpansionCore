package com.example.examplemod.steel;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.BiFunction;

public final class SteelConverterPortBlock extends Block implements EntityBlock {
    private final BiFunction<BlockPos,BlockState,BlockEntity> factory;
    public SteelConverterPortBlock(Properties properties, BiFunction<BlockPos,BlockState,BlockEntity> factory){super(properties);this.factory=factory;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return factory.apply(pos,state);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState next,boolean moved){
        if (!state.is(next.getBlock()) && level instanceof ServerLevel server) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof SteelConverterItemInputBlockEntity input) input.dropContents(server);
            if (blockEntity instanceof SteelConverterOutputBlockEntity output) output.dropContents(server);
        }
        super.onRemove(state,level,pos,next,moved);
    }
}
