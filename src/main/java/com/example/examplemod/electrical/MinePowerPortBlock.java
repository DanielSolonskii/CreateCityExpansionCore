package com.example.examplemod.electrical;

import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.item.context.BlockPlaceContext;
import com.example.examplemod.machine.MachinePanels;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumMap;
import java.util.Map;

public class MinePowerPortBlock
        extends SimpleElectricalDeviceBlock<MinePowerPortDevice> {
    public static final EnumProperty<Direction> VERTICAL_ROTATION =
            EnumProperty.create("vertical_rotation", Direction.class, direction -> direction.getAxis().isHorizontal());
    private static final Map<Direction, Map<Direction, VoxelShape>> OUTLINES = new EnumMap<>(Direction.class);

    static {
        for (Direction direction : Direction.values()) {
            var rotations = new EnumMap<Direction, VoxelShape>(Direction.class);
            for (Direction rotation : Direction.Plane.HORIZONTAL) {
                var shape = Shapes.block();
                for (double x : new double[]{.25, .75}) {
                    var a = orient(direction, rotation, x - .1125, .3125, -.5);
                    var b = orient(direction, rotation, x + .1125, .6875, 0);
                    shape = Shapes.or(shape, Shapes.box(Math.min(a.x, b.x), Math.min(a.y, b.y), Math.min(a.z, b.z),
                            Math.max(a.x, b.x), Math.max(a.y, b.y), Math.max(a.z, b.z)));
                }
                rotations.put(rotation, shape);
            }
            OUTLINES.put(direction, rotations);
        }
    }

    public MinePowerPortBlock(Properties properties) {
        super(properties);

        registerDefaultState(
                stateDefinition.any().setValue(
                        BlockStateProperties.FACING,
                        Direction.NORTH
                ).setValue(VERTICAL_ROTATION, Direction.NORTH).setValue(MachinePanels.COLOR, MachinePanels.DEFAULT_COLOR)
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(BlockStateProperties.FACING, VERTICAL_ROTATION, MachinePanels.COLOR);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getNearestLookingDirection().getOpposite();
        BlockState state = defaultBlockState().setValue(BlockStateProperties.FACING, face);
        return face.getAxis().isVertical()
                ? state.setValue(VERTICAL_ROTATION, context.getHorizontalDirection().getOpposite()) : state;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        Direction face = state.getValue(BlockStateProperties.FACING);
        return face.getAxis().isVertical()
                ? state.setValue(VERTICAL_ROTATION, rotation.rotate(state.getValue(VERTICAL_ROTATION)))
                : state.setValue(BlockStateProperties.FACING, rotation.rotate(face));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        Direction face = state.getValue(BlockStateProperties.FACING);
        return face.getAxis().isVertical()
                ? state.setValue(VERTICAL_ROTATION, mirror.mirror(state.getValue(VERTICAL_ROTATION)))
                : state.setValue(BlockStateProperties.FACING, mirror.mirror(face));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINES.get(state.getValue(BlockStateProperties.FACING)).get(state.getValue(VERTICAL_ROTATION));
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
        return orient(state.getValue(BlockStateProperties.FACING), state.getValue(VERTICAL_ROTATION),
                node == 0 ? .25 : .75, .5, -.5);
    }

    // Matches the blockstate model rotations of the north-facing port.
    private static Vec3 orient(Direction direction, Direction rotation, double x, double y, double z) {
        Vec3 front = switch (direction) {
            case NORTH -> new Vec3(x, y, z);
            case SOUTH -> new Vec3(1 - x, y, 1 - z);
            case WEST -> new Vec3(z, y, 1 - x);
            case EAST -> new Vec3(1 - z, y, x);
            case DOWN -> new Vec3(x, z, 1 - y);
            case UP -> new Vec3(x, 1 - z, y);
        };
        if (direction.getAxis().isHorizontal()) return front;
        return switch (rotation) {
            case EAST -> new Vec3(1 - front.z, front.y, front.x);
            case SOUTH -> new Vec3(1 - front.x, front.y, 1 - front.z);
            case WEST -> new Vec3(front.z, front.y, 1 - front.x);
            default -> front;
        };
    }
}
