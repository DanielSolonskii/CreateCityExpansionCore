package com.example.examplemod.coke;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import com.example.examplemod.network.CokeBatteryGuidePayload;

public final class CokeBatteryControllerBlock extends Block implements EntityBlock {
    public CokeBatteryControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }

    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite()
        );
    }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CokeBatteryControllerBlockEntity(pos, state);
    }

    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type
    ) {
        if (level.isClientSide()) return null;
        return (tickerLevel, pos, blockState, blockEntity) -> {
            if (tickerLevel instanceof ServerLevel serverLevel
                    && blockEntity instanceof CokeBatteryControllerBlockEntity controller) {
                CokeBatteryControllerBlockEntity.serverTick(serverLevel, pos, blockState, controller);
            }
        };
    }

    @Override protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit
    ) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof CokeBatteryControllerBlockEntity controller)) {
            return InteractionResult.PASS;
        }
        CokeBatteryMultiblock.ValidationResult validation = controller.validateNow(serverLevel);
        player.sendSystemMessage(Component.translatable(
                validation.assembled()
                        ? "message.createcityexpansioncore.coke_battery.structure_complete"
                        : "message.createcityexpansioncore.coke_battery.structure_incomplete",
                validation.correctBlocks(), validation.requiredBlocks(), validation.mismatches().size()
        ));
        player.sendSystemMessage(Component.translatable(
                "message.createcityexpansioncore.coke_battery.status",
                Component.translatable("status.createcityexpansioncore.coke_battery."
                        + controller.state().name().toLowerCase()),
                controller.inputCount(serverLevel), controller.outputCount(serverLevel)
        ));
        player.sendSystemMessage(Component.translatable(
                "message.createcityexpansioncore.coke_battery.power_progress",
                controller.power() / 1000.0, CokeBatteryControllerBlockEntity.REQUIRED_POWER_WATTS / 1000.0,
                controller.progress() * 100.0,
                CokeBatteryControllerBlockEntity.BATCH_SIZE,
                CokeBatteryControllerBlockEntity.PROCESS_DURATION_TICKS / 20.0
        ));
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new CokeBatteryGuidePayload(pos));
        }
        return InteractionResult.SUCCESS;
    }
}
