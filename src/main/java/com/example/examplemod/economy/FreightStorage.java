package com.example.examplemod.economy;

import com.example.examplemod.economy.block.EconomyThroughputBlockEntity;
import com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import java.util.*;

/** Native inventories only. Transit restricts every participating inventory to one saved chunk. */
public final class FreightStorage {
    public record Buffer(IItemHandlerModifiable inventory,List<BlockEntity> owners){
        public Buffer{owners=List.copyOf(owners);}
        public void changed(){owners.forEach(BlockEntity::setChanged);}
    }
    private FreightStorage(){}
    public static boolean supported(ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos))return false;
        var block=level.getBlockState(pos).getBlock();
        return block==Blocks.CHEST||block==Blocks.BARREL||block instanceof com.example.examplemod.economy.block.ContractIntakeBlock
                ||level.getBlockEntity(pos) instanceof ItemVaultBlockEntity;
    }
    public static IItemHandlerModifiable intake(ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos))return null;var be=level.getBlockEntity(pos);
        if(be instanceof EconomyThroughputBlockEntity throughput)return throughput.buffer();
        if(be instanceof Container container)return new InvWrapper(container);
        if(be instanceof ItemVaultBlockEntity){var handler=level.getCapability(Capabilities.ItemHandler.BLOCK,pos,null);if(handler instanceof IItemHandlerModifiable inventory)return inventory;}
        return null;
    }
    public static List<BlockPos> vaultPositions(BlockPos controller,Direction.Axis axis,int radius,int length){
        if((axis!=Direction.Axis.X&&axis!=Direction.Axis.Z)||radius<1||radius>3||length<1||length>radius*3)return List.of();
        var positions=new ArrayList<BlockPos>();
        for(int along=0;along<length;along++)for(int x=0;x<radius;x++)for(int z=0;z<radius;z++)
            positions.add(axis==Direction.Axis.Z?controller.offset(x,z,along):controller.offset(along,x,z));
        return List.copyOf(positions);
    }
    public static Buffer transit(ServerLevel level,FreightPoint point){
        if(!level.dimension().location().equals(point.dimension)||!level.hasChunkAt(point.postbox)||point.hubBuffer==null&&!level.hasChunkAt(point.packager))return null;
        BlockPos position=FreightTransit.bufferPos(level,point);var chunk=new ChunkPos(point.postbox);
        if(!chunk.equals(new ChunkPos(position))||!level.hasChunkAt(position))return null;
        BlockEntity be=level.getBlockEntity(position);
        if(level.getBlockState(position).is(Blocks.BARREL)&&be instanceof Container container)return new Buffer(new InvWrapper(container),List.of(be));
        if(!(be instanceof ItemVaultBlockEntity member))return null;
        BlockPos controllerPos=member.getController();
        if(!chunk.equals(new ChunkPos(controllerPos))||!level.hasChunkAt(controllerPos)||!(level.getBlockEntity(controllerPos) instanceof ItemVaultBlockEntity controller))return null;
        var positions=vaultPositions(controllerPos,controller.getMainConnectionAxis(),controller.getWidth(),controller.getHeight());
        if(positions.isEmpty())return null;var owners=new ArrayList<BlockEntity>();
        for(BlockPos pos:positions){
            if(!chunk.equals(new ChunkPos(pos))||!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof ItemVaultBlockEntity part)||!part.getController().equals(controllerPos))return null;
            owners.add(part);
        }
        var handler=level.getCapability(Capabilities.ItemHandler.BLOCK,position,null);
        return handler instanceof IItemHandlerModifiable inventory?new Buffer(inventory,owners):null;
    }
}
