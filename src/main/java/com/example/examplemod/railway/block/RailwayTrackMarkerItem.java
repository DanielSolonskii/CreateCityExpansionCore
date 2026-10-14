package com.example.examplemod.railway.block;

import com.simibubi.create.content.trains.track.ITrackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;

public final class RailwayTrackMarkerItem extends BlockItem {
    public RailwayTrackMarkerItem(Block block,Properties properties){super(block,properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        var level=context.getLevel();var player=context.getPlayer();if(player==null)return InteractionResult.FAIL;ItemStack stack=context.getItemInHand();BlockPos clicked=context.getClickedPos();
        if(level.getBlockState(clicked).getBlock() instanceof ITrackBlock track){
            if(level.isClientSide)return InteractionResult.SUCCESS;var axes=track.getTrackAxes(level,clicked,level.getBlockState(clicked));if(axes.size()!=1){player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.marker_junction"),true);return InteractionResult.FAIL;}
            Vec3 look=player.getLookAngle();boolean front=track.getNearestTrackAxis(level,clicked,level.getBlockState(clicked),look).getSecond()==net.minecraft.core.Direction.AxisDirection.POSITIVE;
            CompoundTag data=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();data.put("Track",NbtUtils.writeBlockPos(clicked));data.putBoolean("Front",front);stack.set(DataComponents.CUSTOM_DATA,CustomData.of(data));player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.marker_track_selected"),true);return InteractionResult.SUCCESS;
        }
        CompoundTag selected=stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();if(!selected.contains("Track")){if(!level.isClientSide)player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.marker_select_track"),true);return InteractionResult.FAIL;}
        BlockPos placed=clicked.relative(context.getClickedFace(),level.getBlockState(clicked).canBeReplaced()?0:1);BlockPos track=NbtUtils.readBlockPos(selected,"Track").orElse(null);if(track==null||!track.closerThan(placed,16)){if(!level.isClientSide)player.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway.marker_too_far"),true);return InteractionResult.FAIL;}
        CompoundTag be=new CompoundTag();be.putString("id","createcityexpansioncore:railway_track_marker");be.put("TargetTrack",NbtUtils.writeBlockPos(track.subtract(placed)));be.putBoolean("TargetDirection",selected.getBoolean("Front"));stack.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(be));InteractionResult result=super.useOn(context);stack.remove(DataComponents.BLOCK_ENTITY_DATA);if(result.consumesAction())stack.remove(DataComponents.CUSTOM_DATA);return result;
    }
}
