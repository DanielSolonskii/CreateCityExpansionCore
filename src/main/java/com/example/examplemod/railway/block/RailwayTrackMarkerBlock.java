package com.example.examplemod.railway.block;

import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.railway.RailwayManager;
import com.example.examplemod.railway.RailwayOwnershipTypes;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public final class RailwayTrackMarkerBlock extends Block implements EntityBlock {
    public static final MapCodec<RailwayTrackMarkerBlock> CODEC=simpleCodec(RailwayTrackMarkerBlock::new);
    private static final String SELECTED="ccecRailMarker",SELECTED_POS="ccecRailMarkerPos",SELECTED_DIM="ccecRailMarkerDim";
    public RailwayTrackMarkerBlock(BlockBehaviour.Properties properties){super(properties);}
    @Override protected MapCodec<? extends Block> codec(){return CODEC;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new RailwayTrackMarkerBlockEntity(pos,state);}
    @Override protected InteractionResult useWithoutItem(BlockState state,Level level,BlockPos pos,Player player,BlockHitResult hit){
        if(level.isClientSide)return InteractionResult.SUCCESS;if(!(player instanceof ServerPlayer serverPlayer)||!(level.getBlockEntity(pos) instanceof RailwayTrackMarkerBlockEntity marker))return InteractionResult.PASS;
        var data=player.getPersistentData();if(!data.hasUUID(SELECTED)){data.putUUID(SELECTED,marker.markerId());data.putLong(SELECTED_POS,pos.asLong());data.putString(SELECTED_DIM,level.dimension().location().toString());player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.marker_first_selected"),true);return InteractionResult.SUCCESS;}
        var firstId=data.getUUID(SELECTED);BlockPos firstPos=BlockPos.of(data.getLong(SELECTED_POS));String firstDim=data.getString(SELECTED_DIM);data.remove(SELECTED);data.remove(SELECTED_POS);data.remove(SELECTED_DIM);if(firstId.equals(marker.markerId())){player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.marker_selection_cleared"),true);return InteractionResult.SUCCESS;}
        if(!firstDim.equals(level.dimension().location().toString())){player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.marker_different_dimension"),true);return InteractionResult.SUCCESS;}
        City city=CityManager.get(serverPlayer.getServer().overworld()).getCityByMember(player.getUUID());
        RailwayOwnershipTypes.OwnerType type=player.isShiftKeyDown()?RailwayOwnershipTypes.OwnerType.CITY:RailwayOwnershipTypes.OwnerType.PLAYER;
        if(type==RailwayOwnershipTypes.OwnerType.CITY&&(city==null||!city.isAdmin(player.getUUID()))){player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.marker_city_permission"),true);return InteractionResult.SUCCESS;}
        UUIDOwner owner=new UUIDOwner(type,type==RailwayOwnershipTypes.OwnerType.CITY?city.getId():player.getUUID());
        String result=RailwayManager.get((ServerLevel)level).registerMarkedSection((ServerLevel)level,firstPos,pos,owner.type,owner.id);
        player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.marker_"+result),true);return InteractionResult.SUCCESS;
    }
    private record UUIDOwner(RailwayOwnershipTypes.OwnerType type,java.util.UUID id){}
}
