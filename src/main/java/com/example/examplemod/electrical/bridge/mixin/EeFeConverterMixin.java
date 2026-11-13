package com.example.examplemod.electrical.bridge.mixin;

import com.george_vi.electroenergetics.content.converter.ConverterDevice;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ConverterDevice.class, remap = false)
abstract class EeFeConverterMixin {
    // EE's simulation runs only on the logical server.
    @Inject(method = {"preTick", "postTick"}, at = @At("HEAD"), cancellable = true)
    private void ccec$disableFeConversion(CallbackInfo ci) {
        ci.cancel();
    }
}
