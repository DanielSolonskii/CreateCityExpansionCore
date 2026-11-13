package com.example.examplemod.machine;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public final class MachineDisplayBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<MachineDisplaySource.Role> ROLE = EnumProperty.create("role", MachineDisplaySource.Role.class);
    public MachineDisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ROLE, MachineDisplaySource.Role.ENERGY)
                .setValue(MachinePanels.COLOR, MachinePanels.DEFAULT_COLOR));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING, ROLE, MachinePanels.COLOR); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MachineDisplayBlockEntity(pos, state); }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        var level = context.getLevel();
        var pos = context.getClickedPos();
        var attached = level.getBlockState(pos.relative(context.getClickedFace().getOpposite()));
        var role = portRole(attached);
        if (role == null) {
            for (Direction side : Direction.values()) {
                role = portRole(level.getBlockState(pos.relative(side)));
                if (role != null) break;
            }
        }
        var facing = context.getHorizontalDirection().getOpposite();
        if (portRole(attached) != null && attached.hasProperty(BlockStateProperties.FACING)) {
            Direction portFacing = attached.getValue(BlockStateProperties.FACING);
            if (portFacing.getAxis().isHorizontal()) facing = portFacing;
        }
        if (role == null) role = MachineDisplaySource.Role.ENERGY;
        return defaultBlockState().setValue(FACING, facing).setValue(ROLE, role);
    }

    private static MachineDisplaySource.Role portRole(BlockState state) {
        if (state.is(CreateCityExpansionCore.MINE_GATEWAY.get())) return MachineDisplaySource.Role.OUTPUT;
        if (state.is(CreateCityExpansionCore.MINE_POWER_PORT.get())) return MachineDisplaySource.Role.ENERGY;
        return null;
    }

    public static BlockPos adjacentDisplay(BlockPos port, MachineDisplaySource.Role role,
                                          java.util.function.Function<BlockPos, BlockState> stateAt) {
        for (Direction side : Direction.values()) {
            BlockPos pos = port.relative(side);
            BlockState state = stateAt.apply(pos);
            if (state != null && state.getBlock() instanceof MachineDisplayBlock && state.getValue(ROLE) == role) {
                return pos;
            }
        }
        return null;
    }
    @Override protected BlockState rotate(BlockState state, Rotation r) { return state.setValue(FACING, r.rotate(state.getValue(FACING))); }
    @Override protected BlockState mirror(BlockState state, Mirror m) { return state.setValue(FACING, m.mirror(state.getValue(FACING))); }
}
