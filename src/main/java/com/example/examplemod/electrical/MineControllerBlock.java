package com.example.examplemod.electrical;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class MineControllerBlock extends Block {

    public MineControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);

        registerDefaultState(
                stateDefinition.any()
                        .setValue(
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

    /**
     * Контроллер всегда смотрит наружу.
     */
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
    protected net.minecraft.world.InteractionResult useWithoutItem(
            BlockState state,
            net.minecraft.world.level.Level level,
            BlockPos pos,
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.phys.BlockHitResult hit
    ) {

        if (!level.isClientSide()
                && level instanceof net.minecraft.server.level.ServerLevel serverLevel
                && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {

            com.example.examplemod.multiblock.MineMultiblock.ValidationResult result =
                    com.example.examplemod.multiblock.MineMultiblock.validate(
                            serverLevel,
                            pos
                    );

            if (result.assembled()) {

                serverPlayer.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                "§a[ШАХТА] МУЛЬТИБЛОК СОБРАН."
                        )
                );

                serverPlayer.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                "§7Power Port: §bOK §7| Gateway: §eOK"
                        )
                );

            } else {

                serverPlayer.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                "§c[ШАХТА] МУЛЬТИБЛОК НЕ СОБРАН."
                        )
                );

                serverPlayer.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                "§7" + result.message()
                        )
                );

                serverPlayer.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal(
                                "§7Прогресс: §f"
                                        + Math.round(
                                        result.progress() * 100
                                )
                                        + "%"
                        )
                );
            }
        }

        return net.minecraft.world.InteractionResult.SUCCESS;
    }
}