package com.example.examplemod.mine;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.WeakHashMap;

/** Runtime-only bridge between the controller state and the existing EE device. */
public final class MinePowerDemand {

    private static final long EXPIRY_TICKS = 20L;
    private static final Map<Level, Map<BlockPos, Demand>> DEMANDS = new WeakHashMap<>();

    private MinePowerDemand() {
    }

    public static synchronized void publish(Level level, BlockPos portPos, boolean workingLoad) {
        if (portPos == null) {
            return;
        }
        DEMANDS.computeIfAbsent(level, ignored -> new java.util.HashMap<>())
                .put(portPos.immutable(), new Demand(workingLoad, level.getGameTime()));
    }

    public static synchronized boolean workingLoad(Level level, BlockPos portPos) {
        Map<BlockPos, Demand> byPosition = DEMANDS.get(level);
        if (byPosition == null) {
            return false;
        }
        Demand demand = byPosition.get(portPos);
        if (demand == null || level.getGameTime() - demand.gameTime() > EXPIRY_TICKS) {
            byPosition.remove(portPos);
            return false;
        }
        return demand.workingLoad();
    }

    private record Demand(boolean workingLoad, long gameTime) {
    }
}
