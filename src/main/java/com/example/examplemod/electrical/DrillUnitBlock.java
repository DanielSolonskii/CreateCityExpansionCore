package com.example.examplemod.electrical;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;

/** One item occupies a 2 x 2 x 2 volume. Parts use world-space corners, FACING points at the tip. */
public class DrillUnitBlock extends Block {
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final BooleanProperty UPPER = BooleanProperty.create("upper");
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    public enum Part implements StringRepresentable {
        // Saves without properties retain the original independent block until rebuilt.
        SINGLE("single", 0, 0),
        NORTH_WEST("north_west", 0, 0), NORTH_EAST("north_east", 1, 0),
        SOUTH_EAST("south_east", 1, 1), SOUTH_WEST("south_west", 0, 1);

        private final String name;
        public final int x;
        public final int z;

        Part(String name, int x, int z) { this.name = name; this.x = x; this.z = z; }
        @Override public String getSerializedName() { return name; }
        public static Part at(int x, int z) {
            return z == 0 ? (x == 0 ? NORTH_WEST : NORTH_EAST) : (x == 0 ? SOUTH_WEST : SOUTH_EAST);
        }
    }

    public DrillUnitBlock(BlockBehaviour.Properties properties) {
        super(properties.noOcclusion().pushReaction(PushReaction.BLOCK));
        registerDefaultState(stateDefinition.any().setValue(PART, Part.SINGLE).setValue(UPPER, false).setValue(FACING, Direction.DOWN));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PART, UPPER, FACING);
    }

    public static boolean isItemPart(BlockState state) {
        return !state.getValue(UPPER) && (state.getValue(PART) == Part.SINGLE || state.getValue(PART) == Part.NORTH_WEST);
    }

    public static BlockPos origin(BlockPos pos, BlockState state) {
        Part part = state.getValue(PART);
        return pos.offset(-part.x, state.getValue(UPPER) ? -1 : 0, -part.z);
    }

    public BlockState partState(int x, int y, int z) {
        return partState(x, y, z, Direction.DOWN);
    }

    public BlockState partState(int x, int y, int z, Direction facing) {
        return defaultBlockState().setValue(PART, Part.at(x, z)).setValue(UPPER, y == 1).setValue(FACING, facing);
    }

    /** Extend out of the supporting face; on the face choose the quadrant nearest the hit. */
    public static BlockPos placementOrigin(BlockPlaceContext context) {
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        var hit = context.getClickLocation();
        return clicked.offset(
                placementOffset(Direction.Axis.X, face, hit.x - Math.floor(hit.x)),
                placementOffset(Direction.Axis.Y, face, hit.y - Math.floor(hit.y)),
                placementOffset(Direction.Axis.Z, face, hit.z - Math.floor(hit.z)));
    }

    private static int placementOffset(Direction.Axis axis, Direction face, double fraction) {
        if (face.getAxis() == axis) return face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 0 : -1;
        return fraction < .5 ? -1 : 0;
    }

    public static Direction placementFacing(BlockPlaceContext context) {
        Direction facing = context.getPlayer() == null ? context.getClickedFace().getOpposite()
                : Direction.getNearest(context.getPlayer().getLookAngle().x, context.getPlayer().getLookAngle().y,
                        context.getPlayer().getLookAngle().z);
        return context.getPlayer() != null && context.getPlayer().isShiftKeyDown() ? facing.getOpposite() : facing;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos base = placementOrigin(context);
        Direction facing = placementFacing(context);
        Level level = context.getLevel();
        for (BlockPos offset : BlockPos.betweenClosed(0, 0, 0, 1, 1, 1)) {
            BlockPos target = base.offset(offset);
            BlockState part = partState(offset.getX(), offset.getY(), offset.getZ(), facing);
            if (level.isOutsideBuildHeight(target) || !level.getWorldBorder().isWithinBounds(target)
                    || !level.hasChunkAt(target) || !level.getBlockState(target).canBeReplaced()
                    || !level.isUnobstructed(part, target, CollisionContext.empty())
                    || (context.getPlayer() != null && (!level.mayInteract(context.getPlayer(), target)
                    || !context.getPlayer().mayUseItemAt(target, context.getClickedFace(), context.getItemInHand())))) {
                return null;
            }
        }
        BlockPos offset = context.getClickedPos().subtract(base);
        return partState(offset.getX(), offset.getY(), offset.getZ(), facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        if (state.getValue(PART) == Part.SINGLE) return;
        BlockPos base = origin(pos, state);
        // NeoForge captures these changes along with BlockItem's first cell for atomic event rollback.
        for (BlockPos offset : BlockPos.betweenClosed(0, 0, 0, 1, 1, 1)) {
            BlockPos target = base.offset(offset);
            if (!target.equals(pos)) level.setBlock(target,
                    partState(offset.getX(), offset.getY(), offset.getZ(), state.getValue(FACING)), UPDATE_ALL);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (!next.is(this) && !level.isClientSide && !level.restoringBlockSnapshots && state.getValue(PART) != Part.SINGLE) {
            BlockPos base = origin(pos, state);
            // The removed cell is already absent. Recursive callbacks only remove remaining cells;
            // only the lower NW cell has loot, yielding one item regardless of the broken part.
            for (BlockPos offset : BlockPos.betweenClosed(0, 0, 0, 1, 1, 1)) {
                BlockPos target = base.offset(offset);
                BlockState other = level.getBlockState(target);
                if (!target.equals(pos) && other == partState(offset.getX(), offset.getY(), offset.getZ(), state.getValue(FACING))) {
                    level.destroyBlock(target, isItemPart(other));
                }
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) != Part.SINGLE) {
            level.setBlock(origin(pos, state), Blocks.AIR.defaultBlockState(), UPDATE_ALL);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        state = state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        Part part = state.getValue(PART);
        if (part == Part.SINGLE) return state;
        return state.setValue(PART, switch (rotation) {
            case NONE -> part;
            case CLOCKWISE_90 -> Part.at(1 - part.z, part.x);
            case CLOCKWISE_180 -> Part.at(1 - part.x, 1 - part.z);
            case COUNTERCLOCKWISE_90 -> Part.at(part.z, 1 - part.x);
        });
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        state = state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
        Part part = state.getValue(PART);
        if (part == Part.SINGLE) return state;
        return state.setValue(PART, switch (mirror) {
            case NONE -> part;
            case LEFT_RIGHT -> Part.at(part.x, 1 - part.z);
            case FRONT_BACK -> Part.at(1 - part.x, part.z);
        });
    }
}
