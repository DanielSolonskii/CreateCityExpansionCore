package com.example.examplemod.electrical.bridge.mixin;

import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.util.thread.EffectiveSide;
import net.neoforged.neoforge.energy.EnergyStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EnergyStorage.class, remap = false)
abstract class FeStorageMixin {
    @Inject(method = {"receiveEnergy", "extractEnergy"}, at = @At("HEAD"), cancellable = true)
    private void ccec$disableServerStorage(int amount, boolean simulate, CallbackInfoReturnable<Integer> cir) {
        if (FMLEnvironment.dist.isDedicatedServer() || EffectiveSide.get().isServer()) cir.setReturnValue(0);
    }

    @Inject(method = {"canReceive", "canExtract"}, at = @At("HEAD"), cancellable = true)
    private void ccec$reportDisabledStorage(CallbackInfoReturnable<Boolean> cir) {
        if (FMLEnvironment.dist.isDedicatedServer() || EffectiveSide.get().isServer()) cir.setReturnValue(false);
    }
}
