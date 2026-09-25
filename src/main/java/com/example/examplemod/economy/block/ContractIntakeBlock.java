package com.example.examplemod.economy.block;

import com.example.examplemod.CreateCityExpansionCore;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
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
import org.jetbrains.annotations.Nullable;

public final class ContractIntakeBlock extends Block implements EntityBlock {
    public ContractIntakeBlock(BlockBehaviour.Properties properties){super(properties);registerDefaultState(stateDefinition.any().setValue(BlockStateProperties.HORIZONTAL_FACING,Direction.NORTH));}
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(BlockStateProperties.HORIZONTAL_FACING);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext context){return defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,context.getHorizontalDirection().getOpposite());}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new EconomyThroughputBlockEntity(pos,state);}
    @Override public void setPlacedBy(Level level,BlockPos pos,BlockState state,net.minecraft.world.entity.LivingEntity placer,net.minecraft.world.item.ItemStack stack){super.setPlacedBy(level,pos,state,placer,stack);EconomyInfrastructureLifecycle.placed(level,pos,placer,stack);}
    @Override public BlockState playerWillDestroy(Level level,BlockPos pos,BlockState state,Player player){EconomyInfrastructureLifecycle.creativeBreak(level,pos,player);return super.playerWillDestroy(level,pos,state,player);}
    @Override public void playerDestroy(Level level,Player player,BlockPos pos,BlockState state,BlockEntity blockEntity,net.minecraft.world.item.ItemStack tool){super.playerDestroy(level,player,pos,state,blockEntity,tool);EconomyInfrastructureLifecycle.survivalBreak(level,player,pos,state,blockEntity);}
    @SuppressWarnings("unchecked") @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return !level.isClientSide&&type==CreateCityExpansionCore.ECONOMY_THROUGHPUT_BLOCK_ENTITY.get()?(l,p,s,b)->EconomyThroughputBlockEntity.serverTick((ServerLevel)l,p,s,(EconomyThroughputBlockEntity)b):null;}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){if(!level.isClientSide&&player instanceof net.minecraft.server.level.ServerPlayer serverPlayer){serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,p)->new com.example.examplemod.menu.EconomyInfrastructureMenu(id,inventory,pos),Component.translatable("block.createcityexpansioncore.contract_intake")),data->data.writeBlockPos(pos));}return InteractionResult.sidedSuccess(level.isClientSide);}
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState newState,boolean moved){if(!state.is(newState.getBlock())&&level instanceof ServerLevel server&&level.getBlockEntity(pos) instanceof EconomyThroughputBlockEntity be){be.dropContents(server);com.example.examplemod.economy.EconomyManager.get(server).markInfrastructureMissing(be.physicalInstanceId());}super.onRemove(state,level,pos,newState,moved);}
}
