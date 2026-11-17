package com.example.examplemod.mixin;

import com.example.examplemod.client.BlueprintPlacement;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Jade skips its API callbacks when its ray trace has no real-world target. */
@Pseudo
@Mixin(targets = "snownee.jade.overlay.RayTracing", remap = false)
public abstract class JadeProjectionRayTraceMixin {
    @Inject(method = "getTarget", at = @At("RETURN"), cancellable = true)
    private void ccec$pickProjectionInAir(CallbackInfoReturnable<HitResult> callback) {
        if (callback.getReturnValue() != null) return;
        var projection = BlueprintPlacement.pickProjection();
        if (projection != null) {
            callback.setReturnValue(projection.hit());
        }
    }
}
