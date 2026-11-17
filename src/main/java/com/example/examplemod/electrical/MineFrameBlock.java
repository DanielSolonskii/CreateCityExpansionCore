package com.example.examplemod.electrical;

import com.example.examplemod.machine.MachinePanels;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;

public class MineFrameBlock extends Block {

    public MineFrameBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MachinePanels.COLOR, MachinePanels.DEFAULT_COLOR));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MachinePanels.COLOR);
    }
}