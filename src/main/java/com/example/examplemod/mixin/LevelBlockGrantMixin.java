package com.example.examplemod.mixin;

import com.example.examplemod.event.CityProtectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Successful world edits only. Sable's assembly relocation uses its own notification method. */
@Mixin(Level.class)
public abstract class LevelBlockGrantMixin {
    @Inject(method = "markAndNotifyBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockState;II)V",
            at = @At("TAIL"))
    private void ccec$clearBlockGrants(BlockPos pos, LevelChunk chunk, BlockState previous, BlockState next,
                                      int flags, int recursionLeft, CallbackInfo ci) {
        if (!((Object) this instanceof ServerLevel level) || previous.getBlock() == next.getBlock()
                || level.restoringBlockSnapshots || level.getBlockState(pos) != next) return;
        CityProtectionEvents.onBlockChanged(level, pos, previous, next);
    }
}
