package com.example.examplemod.mixin;

import com.example.examplemod.client.cutscene.CutscenePlayback;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CutsceneCameraMixin {
    @Shadow private boolean detached;

    @Shadow protected abstract void setPosition(double x, double y, double z);

    @Shadow protected abstract void setRotation(float yaw, float pitch, float roll);

    @Inject(method = "setup", at = @At("TAIL"))
    private void ccec$applyCutscenePose(BlockGetter level, Entity entity, boolean thirdPerson, boolean mirrored,
                                        float partialTick, CallbackInfo ci) {
        CutscenePlayback.CameraPose pose = CutscenePlayback.cameraPose((Camera) (Object) this);
        if (pose != null) {
            setPosition(pose.x(), pose.y(), pose.z());
            setRotation(pose.yaw(), pose.pitch(), 0);
            // Keep the viewer's model out of the shot when a keyframe starts at their eyes.
            detached = entity.getEyePosition(partialTick).distanceToSqr(pose.x(), pose.y(), pose.z()) > 0.64;
        }
    }
}
