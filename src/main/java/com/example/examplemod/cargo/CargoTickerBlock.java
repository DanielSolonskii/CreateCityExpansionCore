package com.example.examplemod.cargo;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** Native IBE ticker/creation and linked item placement, without Stock Ticker's keeper, payments or menus. */
public final class CargoTickerBlock extends HorizontalDirectionalBlock implements IBE<CargoTickerBlockEntity> {
    public static final MapCodec<CargoTickerBlock> CODEC = simpleCodec(CargoTickerBlock::new);

    public CargoTickerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    @Override protected MapCodec<CargoTickerBlock> codec() { return CODEC; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override public Class<CargoTickerBlockEntity> getBlockEntityClass() { return CargoTickerBlockEntity.class; }
    @Override public BlockEntityType<? extends CargoTickerBlockEntity> getBlockEntityType() { return CargoBlocks.TICKER_ENTITY.get(); }
    @Override public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof CargoTickerBlockEntity ticker) ticker.placedBy(player.getUUID());
    }
    @Override public void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        IBE.onRemove(state, level, pos, next);
    }
}
