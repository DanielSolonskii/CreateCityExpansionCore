package com.example.examplemod.electrical.bridge.mixin;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.ItemCapability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemCapability.class, remap = false)
abstract class ItemFeCapabilityMixin {
    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true)
    private void ccec$disableServerFe(ItemStack stack, Object context, CallbackInfoReturnable<Object> cir) {
        if ((FMLEnvironment.dist.isDedicatedServer() || EffectiveSide.get().isServer())
                && (Object) this == Capabilities.EnergyStorage.ITEM) cir.setReturnValue(null);
    }
}
