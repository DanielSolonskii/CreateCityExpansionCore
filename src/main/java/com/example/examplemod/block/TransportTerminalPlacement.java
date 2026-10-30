package com.example.examplemod.block;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Optional dependency bridge. Do not load without Simulated. */
public final class TransportTerminalPlacement {
    public static boolean hasOtherTerminal(ServerLevel level, BlockPos placed) {
        var raw = Sable.HELPER.getContaining(level, new ChunkPos(placed));
        if (!(raw instanceof ServerSubLevel sub) || sub.getPlot() == null) return false;
        // The new block can extend beyond the previous plot bounding box.
        for (var holder : sub.getPlot().getLoadedChunks()) {
            if (holder == null || holder.getChunk() == null) continue;
            for (var be : holder.getChunk().getBlockEntities().values()) {
                if (!be.getBlockPos().equals(placed) && !be.isRemoved()
                        && be instanceof TransportRegistrationTerminalBlockEntity) return true;
            }
        }
        return false;
    }
    public static void invalidate(ServerLevel level, BlockPos pos) {
        TransportSubLevelProtection.invalidateTerminalCache(level, pos);
    }
}
