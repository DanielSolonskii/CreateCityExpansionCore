package com.example.examplemod.economy.block;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.economy.EconomyTypes;
import com.example.examplemod.economy.ShipmentTokenItemData;
import com.example.examplemod.economy.ShippingManifest;
import com.example.examplemod.economy.ShippingManifestItemData;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrder;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ContractDispatchTerminalBlockEntity extends BlockEntity {
    private final ItemStackHandler manifestSlot=new ItemStackHandler(1){
        @Override public boolean isItemValid(int slot,ItemStack stack){return stack.is(CreateCityExpansionCore.SHIPPING_MANIFEST.get())&&ShippingManifestItemData.read(stack).isPresent();}
        @Override protected void onContentsChanged(int slot){setChanged();}
    };

    public ContractDispatchTerminalBlockEntity(BlockPos pos,BlockState state){super(CreateCityExpansionCore.CONTRACT_DISPATCH_TERMINAL_BLOCK_ENTITY.get(),pos,state);}
    public ItemStackHandler manifestSlot(){return manifestSlot;}

    public static void serverTick(ServerLevel level,BlockPos pos,BlockState state,ContractDispatchTerminalBlockEntity terminal){
        ItemStack manifestStack=terminal.manifestSlot.getStackInSlot(0);
        UUID manifestId=ShippingManifestItemData.read(manifestStack).orElse(null);if(manifestId==null)return;
        for(Direction direction:Direction.values()){
            if(!(level.getBlockEntity(pos.relative(direction)) instanceof PackagerBlockEntity packager))continue;
            terminal.tryStamp(level,manifestId,packager.heldBox);
            for(BigItemStack queued:packager.queuedExitingPackages)terminal.tryStamp(level,manifestId,queued.stack);
        }
    }

    private void tryStamp(ServerLevel level,UUID manifestId,ItemStack box){
        if(box.isEmpty()||!PackageItem.isPackage(box)||hasShipmentMarker(box))return;
        var contents=PackageItem.getContents(box);net.minecraft.world.item.Item item=null;long amount=0L;
        for(int slot=0;slot<contents.getSlots();slot++){
            ItemStack stack=contents.getStackInSlot(slot);if(stack.isEmpty())continue;
            if(item==null)item=stack.getItem();else if(item!=stack.getItem())return;
            amount=Math.addExact(amount,stack.getCount());
        }
        if(item==null||amount<=0L)return;
        var itemId=BuiltInRegistries.ITEM.getKey(item);
        var manifest=EconomyManager.get(level).manifest(manifestId);
        if(manifest!=null&&EconomyManager.get(level).freight().assignments.containsKey(manifest.contractId()))return;
        EconomyManager.get(level).authorizeExportPackage(manifestId,itemId,amount,level.getGameTime(),tokenId->addShipmentMarker(box,tokenId));
    }

    private static boolean hasShipmentMarker(ItemStack box){
        PackageOrderWithCrafts context=PackageItem.getOrderContext(box);if(context==null)return false;
        return context.stacks().stream().anyMatch(value->ShipmentTokenItemData.read(value.stack).isPresent());
    }

    private static void addShipmentMarker(ItemStack box,UUID tokenId){
        PackageOrderWithCrafts current=PackageItem.getOrderContext(box);
        List<BigItemStack> stacks=new ArrayList<>(current==null?List.of():current.stacks());
        ItemStack marker=new ItemStack(CreateCityExpansionCore.SHIPMENT_SEAL.get());ShipmentTokenItemData.write(marker,tokenId);
        stacks.add(new BigItemStack(marker,1));
        PackageItem.addOrderContext(box,new PackageOrderWithCrafts(new PackageOrder(stacks),current==null?List.of():current.orderedCrafts()));
    }

    public Component status(ServerLevel level){
        UUID id=ShippingManifestItemData.read(manifestSlot.getStackInSlot(0)).orElse(null);
        ShippingManifest manifest=id==null?null:EconomyManager.get(level).manifest(id);
        if(manifest==null)return Component.translatable("message.createcityexpansioncore.dispatch_terminal.invalid");
        var contract=EconomyManager.get(level).contract(manifest.contractId());
        if(contract==null)return Component.translatable("message.createcityexpansioncore.dispatch_terminal.invalid");
        return Component.translatable("message.createcityexpansioncore.dispatch_terminal.status",contract.id().toString().substring(0,8),
                BuiltInRegistries.ITEM.get(contract.itemId()).getDescription(),manifest.authorizedAmount(),manifest.dispatchedAmount(),manifest.acceptedAmount(),
                contract.remainingAmount(),manifest.state().name().toLowerCase());
    }

    public void dropContents(ServerLevel level){ItemStack stack=manifestSlot.getStackInSlot(0);if(!stack.isEmpty())net.minecraft.world.level.block.Block.popResource(level,worldPosition,stack.copy());manifestSlot.setStackInSlot(0,ItemStack.EMPTY);}
    @Override protected void saveAdditional(CompoundTag tag,HolderLookup.Provider registries){super.saveAdditional(tag,registries);tag.put("Manifest",manifestSlot.serializeNBT(registries));}
    @Override protected void loadAdditional(CompoundTag tag,HolderLookup.Provider registries){super.loadAdditional(tag,registries);if(tag.contains("Manifest",Tag.TAG_COMPOUND))manifestSlot.deserializeNBT(registries,tag.getCompound("Manifest"));}
}
