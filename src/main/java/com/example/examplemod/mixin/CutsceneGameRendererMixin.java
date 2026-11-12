package com.example.examplemod.mixin;

import com.example.examplemod.client.cutscene.CutscenePlayback;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class CutsceneGameRendererMixin {
    @Inject(method = {"bobHurt", "bobView"}, at = @At("HEAD"), cancellable = true)
    private void ccec$suppressCameraBob(PoseStack poseStack, float partialTick, CallbackInfo ci) {
        if (CutscenePlayback.isPlaying()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
    private void ccec$suppressFirstPersonEffects(Camera camera, float partialTick, Matrix4f projectionMatrix,
                                                 CallbackInfo ci) {
        if (CutscenePlayback.isPlaying()) {
            ci.cancel();
        }
    }
}
