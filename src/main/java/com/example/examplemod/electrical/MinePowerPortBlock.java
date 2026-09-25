package com.example.examplemod.electrical;

import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class MinePowerPortBlock
        extends SimpleElectricalDeviceBlock<MinePowerPortDevice> {

    public MinePowerPortBlock(Properties properties) {
        super(properties);

        registerDefaultState(
                stateDefinition.any().setValue(
                        BlockStateProperties.HORIZONTAL_FACING,
                        Direction.NORTH
                )
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                BlockStateProperties.HORIZONTAL_FACING,
                context.getHorizontalDirection().getOpposite()
        );
    }

    @Override
    public com.george_vi.electroenergetics.devices.device.SimulatedDeviceType<MinePowerPortDevice>
    getDevice() {

        return ModElectricalDevices.MINE_POWER_PORT.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(
            Level level,
            BlockPos pos,
            BlockState state
    ) {
        return Map.of(0, nodePosition(state, 0), 1, nodePosition(state, 1));
    }

    @Override
    public Vec3 getNodePosition(
            Level level,
            BlockPos pos,
            BlockState state,
            int node
    ) {

        return nodePosition(state, node);
    }

    private static Vec3 nodePosition(BlockState state, int node) {
        if (node != 0 && node != 1) {
            return Vec3.ZERO;
        }
        double first = node == 0 ? 0.25 : 0.75;
        double second = 1.0 - first;
        return switch (state.getValue(BlockStateProperties.HORIZONTAL_FACING)) {
            case NORTH -> new Vec3(first, 0.5, 0.0);
            case SOUTH -> new Vec3(second, 0.5, 1.0);
            case WEST -> new Vec3(0.0, 0.5, second);
            case EAST -> new Vec3(1.0, 0.5, first);
            default -> Vec3.ZERO;
        };
    }
}
