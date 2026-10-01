package com.example.examplemod.railway.block;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

public final class RailwayTrackMarkerBlockEntity extends BlockEntity {
    private UUID markerId=UUID.randomUUID();private BlockPos targetTrack=BlockPos.ZERO;private boolean front;
    public RailwayTrackMarkerBlockEntity(BlockPos pos,BlockState state){super(CreateCityExpansionCore.RAILWAY_TRACK_MARKER_BLOCK_ENTITY.get(),pos,state);}
    public UUID markerId(){return markerId;} public BlockPos trackPos(){return worldPosition.offset(targetTrack);} public boolean front(){return front;}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putUUID("MarkerId",markerId);tag.put("TargetTrack",NbtUtils.writeBlockPos(targetTrack));tag.putBoolean("TargetDirection",front);}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);if(tag.hasUUID("MarkerId"))markerId=tag.getUUID("MarkerId");targetTrack=NbtUtils.readBlockPos(tag,"TargetTrack").orElse(BlockPos.ZERO);front=tag.getBoolean("TargetDirection");}
}
