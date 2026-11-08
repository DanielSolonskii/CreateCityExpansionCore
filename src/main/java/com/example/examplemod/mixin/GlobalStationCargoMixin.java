package com.example.examplemod.mixin;

import com.example.examplemod.cargo.CargoPackageAssignment;
import com.example.examplemod.cargo.center.CargoShipmentRegistry;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Create 6.0.10: ordinal 0 is Postbox/offlineBuffer -> train; ordinal 1 is train -> Postbox. */
@Mixin(value = GlobalStation.class, remap = false)
public abstract class GlobalStationCargoMixin {
    @Shadow(remap = false)
    public abstract Train getPresentTrain();

    @Redirect(method = "runMailTransfer", at = @At(value = "INVOKE",
            target = "Lnet/neoforged/neoforge/items/IItemHandlerModifiable;getStackInSlot(I)Lnet/minecraft/world/item/ItemStack;",
            ordinal = 0, remap = false), remap = false, require = 1, allow = 1)
    private ItemStack ccec$filterOutgoingPackage(IItemHandlerModifiable inventory, int slot) {
        // EMPTY is only the view of a refused slot. Native code skips it before insertion/events/clearing.
        return CargoPackageAssignment.outgoingStack(inventory.getStackInSlot(slot), getPresentTrain(),
                ServerLifecycleHooks.getCurrentServer());
    }

    @Redirect(method="runMailTransfer",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/items/IItemHandlerModifiable;setStackInSlot(ILnet/minecraft/world/item/ItemStack;)V",ordinal=0,remap=false),remap=false,require=1,allow=1)
    private void ccec$observeBoarding(IItemHandlerModifiable source,int slot,ItemStack remainder){
        ItemStack box=source.getStackInSlot(slot).copy();source.setStackInSlot(slot,remainder);
        try{CargoShipmentRegistry.mailTransfer(ServerLifecycleHooks.getCurrentServer(),box,(GlobalStation)(Object)this,getPresentTrain(),true);}
        catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger("CCEC Cargo").warn("Boarding observation unavailable; payment remains unconfirmed",unavailable);}
    }
    @Redirect(method="runMailTransfer",at=@At(value="INVOKE",target="Lnet/neoforged/neoforge/items/IItemHandlerModifiable;setStackInSlot(ILnet/minecraft/world/item/ItemStack;)V",ordinal=1,remap=false),remap=false,require=1,allow=1)
    private void ccec$observeAlighting(IItemHandlerModifiable source,int slot,ItemStack remainder){
        ItemStack box=source.getStackInSlot(slot).copy();source.setStackInSlot(slot,remainder);
        try{CargoShipmentRegistry.mailTransfer(ServerLifecycleHooks.getCurrentServer(),box,(GlobalStation)(Object)this,getPresentTrain(),false);}
        catch(RuntimeException unavailable){org.slf4j.LoggerFactory.getLogger("CCEC Cargo").warn("Alighting observation unavailable; payment remains unconfirmed",unavailable);}
    }
}
