package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.CargoBlocks;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import java.util.UUID;

/** A dedicated customs inventory exposes no contents to Create/hoppers until its receipt is accepted. */
public final class MasterCityCustomsBufferBlockEntity extends BlockEntity {
    private UUID instance=UUID.randomUUID();
    private final MasterCityImportBuffer buffer=new MasterCityImportBuffer(27,this::setChanged);
    public MasterCityCustomsBufferBlockEntity(BlockPos pos,BlockState state){super(CargoBlocks.CUSTOMS_BUFFER_ENTITY.get(),pos,state);}
    public UUID instanceId(){return instance;} public MasterCityImportBuffer buffer(){return buffer;} public IItemHandler inventory(){return buffer;}
    public void dropContents(){if(level==null||level.isClientSide)return;for(var item:buffer.drainUnlocked())net.minecraft.world.Containers.dropItemStack(level,worldPosition.getX()+.5,worldPosition.getY()+.5,worldPosition.getZ()+.5,item);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.putUUID("Instance",instance);tag.put("ImportBuffer",buffer.save(registries));}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);instance=tag.hasUUID("Instance")?tag.getUUID("Instance"):UUID.randomUUID();buffer.load(tag.getCompound("ImportBuffer"),registries);}
}
