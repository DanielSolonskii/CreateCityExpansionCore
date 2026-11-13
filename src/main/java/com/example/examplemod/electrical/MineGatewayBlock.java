package com.example.examplemod.electrical;

import com.example.examplemod.machine.MachinePanels;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import com.example.examplemod.mine.MineGatewayBlockEntity;

public class MineGatewayBlock extends Block implements EntityBlock {

    public MineGatewayBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MachinePanels.COLOR, MachinePanels.DEFAULT_COLOR));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MachinePanels.COLOR);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MineGatewayBlockEntity(pos, state);
    }
}
