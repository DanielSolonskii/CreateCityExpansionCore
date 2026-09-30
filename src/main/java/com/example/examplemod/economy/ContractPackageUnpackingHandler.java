package com.example.examplemod.economy;

import com.example.examplemod.economy.block.EconomyThroughputBlockEntity;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.minecraft.world.Container;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public enum ContractPackageUnpackingHandler implements UnpackingHandler {
    INSTANCE;

    @Override public boolean unpack(Level level,BlockPos pos,BlockState state,Direction side,List<ItemStack> stacks,
                                    PackageOrderWithCrafts orderContext,boolean simulate){
        if(!(level instanceof ServerLevel server))return false;
        EconomyManager manager=EconomyManager.get(server);
        FreightPoint point=manager.freight().receiver(server,pos,side);
        var be=level.getBlockEntity(pos);
        EconomyThroughputBlockEntity intake=be instanceof EconomyThroughputBlockEntity b?b:null;
        Set<UUID> ids=new LinkedHashSet<>();
        if(orderContext!=null)orderContext.stacks().forEach(value->ShipmentTokenItemData.read(value.stack).ifPresent(ids::add));
        stacks.forEach(stack->ShipmentTokenItemData.read(stack).ifPresent(ids::add));
        if(point==null&&intake==null)return ids.isEmpty()&&UnpackingHandler.DEFAULT.unpack(level,pos,state,side,stacks,orderContext,simulate);
        if(ids.size()!=1)return false;UUID tokenId=ids.iterator().next();
        if(point!=null&&!simulate)manager.freight().validate(server.getServer(),point);
        UUID receiverId=point!=null?point.id:intake.registrationId();if(receiverId==null)return false;
        IItemHandlerModifiable inventory=intake!=null?intake.buffer():be instanceof Container container?new InvWrapper(container):null;
        if(inventory==null)return false;
        net.minecraft.world.item.Item item=null;long amount=0L;List<ItemStack> clean=new ArrayList<>();
        for(ItemStack stack:stacks){if(stack.isEmpty())continue;if(item==null)item=stack.getItem();else if(item!=stack.getItem())return false;amount=Math.addExact(amount,stack.getCount());clean.add(ShipmentTokenItemData.withoutToken(stack));}
        if(item==null||amount<=0L)return false;ResourceLocation itemId=BuiltInRegistries.ITEM.getKey(item);
        BufferTransaction transaction=new BufferTransaction(inventory,clean);
        return manager.acceptShipmentPackage(receiverId,tokenId,itemId,amount,server.getGameTime(),simulate,transaction)==EconomyTypes.IntakeAttributionError.NONE;
    }

    private static final class BufferTransaction implements EconomyManager.PhysicalTransaction {
        private final IItemHandlerModifiable target;private final List<ItemStack> cargo;private final List<ItemStack> snapshot=new ArrayList<>();
        private BufferTransaction(IItemHandlerModifiable target,List<ItemStack> cargo){this.target=target;this.cargo=cargo;for(int i=0;i<target.getSlots();i++)snapshot.add(target.getStackInSlot(i).copy());}
        @Override public boolean prepare(){ItemStackHandler copy=new ItemStackHandler(target.getSlots());for(int i=0;i<snapshot.size();i++)copy.setStackInSlot(i,snapshot.get(i).copy());for(ItemStack stack:cargo)if(!ItemHandlerHelper.insertItemStacked(copy,stack.copy(),false).isEmpty())return false;return true;}
        @Override public boolean commit(){for(ItemStack stack:cargo)if(!ItemHandlerHelper.insertItemStacked(target,stack.copy(),false).isEmpty()){rollback();return false;}return true;}
        @Override public void rollback(){for(int i=0;i<snapshot.size();i++)target.setStackInSlot(i,snapshot.get(i).copy());}
    }
}
