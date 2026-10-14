package com.example.examplemod.blast;

import com.example.examplemod.network.BlastFurnaceGuidePayload;
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

public final class BlastFurnaceControllerBlock extends Block implements EntityBlock {
    public BlastFurnaceControllerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,
                context.getHorizontalDirection().getOpposite());
    }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BlastFurnaceControllerBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (tickerLevel, pos, blockState, blockEntity) -> {
            if (tickerLevel instanceof ServerLevel serverLevel
                    && blockEntity instanceof BlastFurnaceControllerBlockEntity controller)
                BlastFurnaceControllerBlockEntity.serverTick(serverLevel, pos, blockState, controller);
        };
    }
    @Override protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof BlastFurnaceControllerBlockEntity controller))
            return InteractionResult.PASS;
        BlastFurnaceMultiblock.ValidationResult validation = controller.validateNow(serverLevel);
        BlastFurnaceControllerBlockEntity.InputSummary input = controller.inputSummary(serverLevel);
        player.sendSystemMessage(Component.translatable(
                validation.assembled()
                        ? "message.createcityexpansioncore.blast_furnace.structure_complete"
                        : "message.createcityexpansioncore.blast_furnace.structure_incomplete",
                validation.correctBlocks(), validation.requiredBlocks(), validation.mismatches().size()));
        player.sendSystemMessage(Component.translatable(
                "message.createcityexpansioncore.blast_furnace.status",
                Component.translatable("status.createcityexpansioncore.blast_furnace."
                        + controller.state().name().toLowerCase()), input.iron(), input.coke(), input.quicklime(),
                controller.outputAmount(serverLevel), BlastFurnaceOutputBlockEntity.CAPACITY_MB));
        player.sendSystemMessage(Component.translatable(
                "message.createcityexpansioncore.blast_furnace.power_progress",
                controller.power() / 1000.0, BlastFurnaceControllerBlockEntity.REQUIRED_POWER_WATTS / 1000.0,
                controller.progress() * 100.0, BlastFurnaceControllerBlockEntity.OUTPUT_PER_BATCH_MB,
                BlastFurnaceControllerBlockEntity.PROCESS_DURATION_TICKS / 20.0));
        if (player instanceof ServerPlayer serverPlayer)
            PacketDistributor.sendToPlayer(serverPlayer, new BlastFurnaceGuidePayload(pos));
        return InteractionResult.SUCCESS;
    }
}
