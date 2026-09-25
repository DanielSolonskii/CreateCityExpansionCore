package com.example.examplemod.electrical;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import com.example.examplemod.mine.MineGatewayBlockEntity;

public class MineGatewayBlock extends Block implements EntityBlock {

    public MineGatewayBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MineGatewayBlockEntity(pos, state);
    }
}
