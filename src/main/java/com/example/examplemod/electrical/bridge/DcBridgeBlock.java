package com.example.examplemod.electrical.bridge;

import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.base.SimpleElectricalDeviceBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public final class DcBridgeBlock extends SimpleElectricalDeviceBlock<EeBridgeDevice> implements EntityBlock {
    private static final ThreadLocal<Boolean> DISMANTLING = ThreadLocal.withInitial(() -> false);
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final net.minecraft.world.level.block.state.properties.BooleanProperty TFMG =
            net.minecraft.world.level.block.state.properties.BooleanProperty.create("tfmg");

    DcBridgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TFMG, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TFMG);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        var state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        var other = partner(context.getClickedPos(), state);
        var level = context.getLevel();
        var player = context.getPlayer();
        if (!level.isInWorldBounds(other) || !level.getWorldBorder().isWithinBounds(other)
                || !level.getBlockState(other).canBeReplaced(context)
                || (player != null && (!player.mayUseItemAt(other, context.getClickedFace(), context.getItemInHand())
                || !level.mayInteract(player, other)))
                || (player instanceof net.minecraft.server.level.ServerPlayer sp
                    && !com.example.examplemod.city.CityProtectionManager.canInteract(sp.serverLevel(), sp, other))
                || !level.isUnobstructed(state, other, player == null
                    ? net.minecraft.world.phys.shapes.CollisionContext.empty()
                    : net.minecraft.world.phys.shapes.CollisionContext.of(player))) return null;
        return state;
    }

    public static BlockPos partner(BlockPos pos, BlockState state) {
        Direction direction = state.getValue(FACING).getClockWise();
        return pos.relative(state.getValue(TFMG) ? direction.getOpposite() : direction);
    }

    public static boolean matches(BlockState state, BlockState other) {
        return other.getBlock() == state.getBlock() && other.getValue(FACING) == state.getValue(FACING)
                && other.getValue(TFMG) != state.getValue(TFMG);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && !state.getValue(TFMG)) {
            level.setBlock(partner(pos, state), state.setValue(TFMG, true), 3);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moving) {
        if (state.getBlock() != next.getBlock() && !level.isClientSide && !DISMANTLING.get()) {
            var other = partner(pos, state);
            if (level.hasChunkAt(other) && matches(state, level.getBlockState(other))) {
                DISMANTLING.set(true);
                try { level.destroyBlock(other, state.getValue(TFMG)); }
                finally { DISMANTLING.set(false); }
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative()) {
            var other = partner(pos, state);
            if (matches(state, level.getBlockState(other))) {
                DISMANTLING.set(true);
                try { level.setBlock(other, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 35); }
                finally { DISMANTLING.set(false); }
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return DcBridgeEntity.create(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != PowerBridge.ENTITY.get() ? null
                : (world, pos, blockState, entity) -> ((DcBridgeEntity) entity).tick();
    }

    @Override
    public SimulatedDeviceType<EeBridgeDevice> getDevice() {
        return PowerBridge.EE_DEVICE.get();
    }

    @Override
    public Map<Integer, Vec3> getNodePositions(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(TFMG)) return Map.of();
        return Map.of(0, getNodePosition(level, pos, state, 0), 1, getNodePosition(level, pos, state, 1));
    }

    @Override
    public Vec3 getNodePosition(Level level, BlockPos pos, BlockState state, int node) {
        Direction side = state.getValue(FACING).getClockWise();
        Direction front = state.getValue(FACING);
        double offset = node == 0 ? -.25 : .25;
        return new Vec3(.5 + side.getStepX() * offset - front.getStepX() * .12, .96,
                .5 + side.getStepZ() * offset - front.getStepZ() * .12);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof DcBridgeEntity bridge)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            bridge.controller().reverse();
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(Items.REDSTONE) || !(level.getBlockEntity(pos) instanceof DcBridgeEntity bridge)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide) {
            bridge = bridge.controller();
            bridge.cycleRedstone();
            player.displayClientMessage(Component.translatable(
                    "gui.createcityexpansioncore.powerbridge.redstone." + bridge.redstoneMode()), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }
}
