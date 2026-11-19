package com.example.examplemod.mixin;

import com.example.examplemod.client.cutscene.CutscenePlayback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Vanilla's render-section grid follows the player even when Camera has a detached pose. */
@Mixin(LevelRenderer.class)
public abstract class CutsceneLevelRendererMixin {
    @Redirect(method = "setupRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getX()D"))
    private double ccec$cameraX(LocalPlayer player) {
        var pose = CutscenePlayback.cameraPose(Minecraft.getInstance().gameRenderer.getMainCamera());
        return pose == null ? player.getX() : pose.x();
    }

    @Redirect(method = "setupRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"))
    private double ccec$cameraY(LocalPlayer player) {
        var pose = CutscenePlayback.cameraPose(Minecraft.getInstance().gameRenderer.getMainCamera());
        return pose == null ? player.getY() : pose.y();
    }

    @Redirect(method = "setupRender", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getZ()D"))
    private double ccec$cameraZ(LocalPlayer player) {
        var pose = CutscenePlayback.cameraPose(Minecraft.getInstance().gameRenderer.getMainCamera());
        return pose == null ? player.getZ() : pose.z();
    }
}
