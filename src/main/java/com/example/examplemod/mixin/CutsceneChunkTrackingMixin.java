package com.example.examplemod.mixin;

import com.example.examplemod.cutscene.CutsceneChunks;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkMap.class)
public abstract class CutsceneChunkTrackingMixin {
    @Shadow
    protected abstract void applyChunkTrackingView(ServerPlayer player, ChunkTrackingView view);

    @Inject(method = "updateChunkTracking", at = @At("HEAD"), cancellable = true)
    private void ccec$trackCamera(ServerPlayer player, CallbackInfo ci) {
        ChunkPos center = CutsceneChunks.center(player);
        if (center != null) {
            applyChunkTrackingView(player, ChunkTrackingView.of(center, CutsceneChunks.radius(player)));
            ci.cancel();
        }
    }
}
