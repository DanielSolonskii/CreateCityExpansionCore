package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.CargoAddress;
import com.example.examplemod.cargo.CargoParcelIdentity;
import com.example.examplemod.economy.EconomyManager;
import com.simibubi.create.content.logistics.box.PackageItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

/** Redeems the actual held parcel to its seller, never locating or teleporting distant cargo. */
public final class CargoParcelRecovery {
    private CargoParcelRecovery() {}

    public static void recover(ServerPlayer player){
        if(player instanceof FakePlayer)throw new IllegalArgumentException("shipment_cancel_denied");
        ItemStack box=player.getMainHandItem();
        if(box.getCount()!=1||!PackageItem.isPackage(box))throw new IllegalArgumentException("shipment_recover_hold");
        var address=CargoAddress.parse(PackageItem.getAddress(box)).orElseThrow(()->new IllegalArgumentException("shipment_recover_invalid"));
        var parcel=CargoParcelIdentity.id(box).orElseThrow(()->new IllegalArgumentException("shipment_recover_invalid"));
        if(address.shipment()==null)throw new IllegalArgumentException("shipment_recover_invalid");
        var registry=CargoShipmentRegistry.get(player.getServer());
        var shipment=registry.find(address.shipment()).orElseThrow();
        var order=EconomyManager.get(player.serverLevel()).cargo().order(shipment.id()).orElseThrow();
        // A buyer cannot receive both returned goods and a refund. Recovery returns goods to
        // the seller's real inventory; administrators can physically secure a parcel as well.
        if(!player.hasPermissions(2)&&!order.seller().manages(player.getServer(),player.getUUID()))
            throw new IllegalArgumentException("shipment_recover_seller");
        if(order.state()!=CargoCommerceBook.State.CANCELLING||!shipment.finalPoint().equals(address.finalPoint()))
            throw new IllegalArgumentException("shipment_recover_not_cancelled");
        registry.reconcile(player.getServer());
        if(registry.recoverableParcel(player.getServer(),shipment.id(),parcel,box)==null)
            throw new IllegalArgumentException("shipment_recover_invalid");
        var inventory=player.getInventory();int selected=inventory.selected;
        if(selected<0||selected>=9)throw new IllegalArgumentException("shipment_recover_invalid");
        var before=new ItemStack[36];var after=new ItemStack[36];
        var plan=new ItemStackHandler(36){
            @Override public boolean isItemValid(int slot,ItemStack stack){return inventory.canPlaceItem(slot,stack);}
            @Override public int getSlotLimit(int slot){return inventory.getMaxStackSize();}
        };
        for(int i=0;i<36;i++){before[i]=inventory.getItem(i).copy();plan.setStackInSlot(i,i==selected?ItemStack.EMPTY:before[i].copy());}
        var contents=PackageItem.getContents(box);
        for(int i=0;i<contents.getSlots();i++)if(!ItemHandlerHelper.insertItemStacked(plan,contents.getStackInSlot(i).copy(),false).isEmpty())
            throw new IllegalArgumentException("shipment_recover_full");
        for(int i=0;i<36;i++)after[i]=plan.getStackInSlot(i).copy();
        if(inventory.selected!=selected)throw new IllegalArgumentException("shipment_recover_invalid");
        for(int i=0;i<36;i++)if(!ItemStack.matches(before[i],inventory.getItem(i)))throw new IllegalArgumentException("shipment_recover_invalid");
        try{
            for(int i=0;i<36;i++)inventory.setItem(i,after[i].copy());
            registry.recovered(shipment.id(),parcel);
        }catch(RuntimeException failure){
            for(int i=0;i<36;i++)inventory.setItem(i,before[i].copy());
            throw failure;
        }
        inventory.setChanged();player.inventoryMenu.broadcastChanges();
        if(player.containerMenu!=player.inventoryMenu)player.containerMenu.broadcastChanges();
    }
}
