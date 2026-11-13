package com.example.examplemod.electrical.bridge.mixin;

import net.neoforged.fml.util.thread.EffectiveSide;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = {
        "com.drmangotea.tfmg.content.electricity.storage.AccumulatorBlockEntity",
        "com.drmangotea.tfmg.content.electricity.utilities.converter.ConverterBlockEntity"
}, remap = false)
abstract class TfmgFeBlocksMixin {
    @Inject(method = {"voltageGeneration", "powerGeneration", "getChargingRate"}, at = @At("HEAD"), cancellable = true)
    private void ccec$disableFeExchange(CallbackInfoReturnable<Integer> cir) {
        if (EffectiveSide.get().isServer()) cir.setReturnValue(0);
    }

    @Inject(method = "resistance", at = @At("HEAD"), cancellable = true)
    private void ccec$openFeCircuit(CallbackInfoReturnable<Float> cir) {
        if (EffectiveSide.get().isServer()) cir.setReturnValue(0f);
    }

    @Inject(method = "canPower", at = @At("HEAD"), cancellable = true)
    private void ccec$disableFeDischarge(CallbackInfoReturnable<Boolean> cir) {
        if (EffectiveSide.get().isServer()) cir.setReturnValue(false);
    }
}
