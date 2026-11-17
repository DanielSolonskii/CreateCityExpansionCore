package com.example.examplemod.electrical.bridge.mixin;

import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.EntityCapability;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EntityCapability.class, remap = false)
abstract class EntityFeCapabilityMixin {
    @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true)
    private void ccec$disableServerFe(Entity entity, Object context, CallbackInfoReturnable<Object> cir) {
        if (!entity.level().isClientSide && (Object) this == Capabilities.EnergyStorage.ENTITY) cir.setReturnValue(null);
    }
}
