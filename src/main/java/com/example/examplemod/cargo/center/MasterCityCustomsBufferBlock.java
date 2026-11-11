package com.example.examplemod.cargo.center;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MasterCityCustomsBufferBlock extends BaseEntityBlock {
    public static final MapCodec<MasterCityCustomsBufferBlock> CODEC=simpleCodec(MasterCityCustomsBufferBlock::new);
    public MasterCityCustomsBufferBlock(Properties properties){super(properties);}
    @Override protected MapCodec<? extends BaseEntityBlock> codec(){return CODEC;}
    @Override public RenderShape getRenderShape(BlockState state){return RenderShape.MODEL;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new MasterCityCustomsBufferBlockEntity(pos,state);}
    @Override public void onRemove(BlockState state,net.minecraft.world.level.Level level,BlockPos pos,BlockState next,boolean moved){if(!state.is(next.getBlock())&&level.getBlockEntity(pos) instanceof MasterCityCustomsBufferBlockEntity buffer)buffer.dropContents();super.onRemove(state,level,pos,next,moved);}
}
