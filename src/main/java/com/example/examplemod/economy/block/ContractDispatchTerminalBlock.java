package com.example.examplemod.economy.block;

import com.example.examplemod.CreateCityExpansionCore;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public final class ContractDispatchTerminalBlock extends Block implements EntityBlock {
    public static final MapCodec<ContractDispatchTerminalBlock> CODEC=simpleCodec(ContractDispatchTerminalBlock::new);
    public ContractDispatchTerminalBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ContractDispatchTerminalBlockEntity(pos,state);}
    @SuppressWarnings("unchecked") @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level,BlockState state,BlockEntityType<T> type){return !level.isClientSide&&type==CreateCityExpansionCore.CONTRACT_DISPATCH_TERMINAL_BLOCK_ENTITY.get()?(l,p,s,b)->ContractDispatchTerminalBlockEntity.serverTick((ServerLevel)l,p,s,(ContractDispatchTerminalBlockEntity)b):null;}
    @Override protected ItemInteractionResult useItemOn(ItemStack stack,BlockState state,Level level,BlockPos pos,Player player,InteractionHand hand,BlockHitResult hit){
        if(level.isClientSide)return ItemInteractionResult.SUCCESS;if(!(level.getBlockEntity(pos) instanceof ContractDispatchTerminalBlockEntity terminal))return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if(stack.is(CreateCityExpansionCore.SHIPPING_MANIFEST.get())&&terminal.manifestSlot().getStackInSlot(0).isEmpty()){
            terminal.manifestSlot().setStackInSlot(0,stack.copyWithCount(1));if(!player.getAbilities().instabuild)stack.shrink(1);return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(level.isClientSide)return InteractionResult.SUCCESS;if(!(level.getBlockEntity(pos) instanceof ContractDispatchTerminalBlockEntity terminal))return InteractionResult.PASS;
        ItemStack stored=terminal.manifestSlot().getStackInSlot(0);if(!stored.isEmpty()&&player.isShiftKeyDown()&&player instanceof ServerPlayer serverPlayer){serverPlayer.displayClientMessage(terminal.status((ServerLevel)level),true);return InteractionResult.SUCCESS;}
        if(!stored.isEmpty()){terminal.manifestSlot().setStackInSlot(0,ItemStack.EMPTY);if(!player.addItem(stored))player.drop(stored,false);return InteractionResult.SUCCESS;}
        if(player instanceof ServerPlayer serverPlayer)serverPlayer.displayClientMessage(terminal.status((ServerLevel)level),true);return InteractionResult.SUCCESS;
    }
    @Override protected void onRemove(BlockState state,Level level,BlockPos pos,BlockState newState,boolean moved){if(!state.is(newState.getBlock())&&level instanceof ServerLevel server&&level.getBlockEntity(pos) instanceof ContractDispatchTerminalBlockEntity terminal)terminal.dropContents(server);super.onRemove(state,level,pos,newState,moved);}
}
