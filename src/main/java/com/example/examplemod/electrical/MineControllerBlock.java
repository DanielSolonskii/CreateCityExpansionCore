package com.example.examplemod.electrical;

import com.example.examplemod.city.CityManager;
import com.example.examplemod.mine.MineControllerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import com.example.examplemod.machine.MachinePanels;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;

public class MineControllerBlock extends Block implements EntityBlock {

    public MineControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);

        registerDefaultState(
                stateDefinition.any().setValue(
                        BlockStateProperties.HORIZONTAL_FACING,
                        Direction.NORTH
                ).setValue(MachinePanels.COLOR, MachinePanels.DEFAULT_COLOR)
        );
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, MachinePanels.COLOR);
    }

    @Override
    public BlockState getStateForPlacement(
            net.minecraft.world.item.context.BlockPlaceContext context
    ) {
        return defaultBlockState().setValue(
                BlockStateProperties.HORIZONTAL_FACING,
                context.getHorizontalDirection().getOpposite()
        );
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MineControllerBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()
                && level instanceof ServerLevel serverLevel
                && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof MineControllerBlockEntity controller) {
            com.example.examplemod.mine.MineInteraction.placed(serverLevel, controller, player);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof MineControllerBlockEntity controller) {
            CityManager manager = CityManager.get(serverLevel.getServer().overworld());
            controller.getProjectId().map(manager::getProject).ifPresent(project -> {
                if (project.dimensionId().equals(serverLevel.dimension().location())
                        && project.controllerPos().equals(pos)) {
                    var city = manager.getCity(project.cityId());
                    if (city != null) manager.removeProject(city, project.projectId());
                }
            });
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        if (level.isClientSide()) {
            return null;
        }
        return (tickerLevel, pos, blockState, blockEntity) -> {
            if (tickerLevel instanceof ServerLevel serverLevel
                    && blockEntity instanceof MineControllerBlockEntity controller) {
                MineControllerBlockEntity.serverTick(serverLevel, pos, blockState, controller);
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        if (hit.getDirection() != state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                || hit.getLocation().y - pos.getY() > .25) return InteractionResult.PASS;
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)
                || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos)
                instanceof MineControllerBlockEntity controller)) {
            serverPlayer.sendSystemMessage(Component.translatable(
                    "message.createcityexpansioncore.controller.invalid_state"
            ));
            return InteractionResult.SUCCESS;
        }

        com.example.examplemod.mine.MineInteraction.press(serverPlayer, pos);

        return InteractionResult.SUCCESS;
    }
}
