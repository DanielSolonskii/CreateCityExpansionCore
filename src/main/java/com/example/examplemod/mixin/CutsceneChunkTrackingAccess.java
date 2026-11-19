package com.example.examplemod.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChunkMap.class)
public interface CutsceneChunkTrackingAccess {
    @Invoker("updateChunkTracking")
    void ccec$updateChunkTracking(ServerPlayer player);
}
