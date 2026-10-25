package com.example.examplemod.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Shared world, screen and Ponder geometry; the warehouse is behind the Packager. */
public final class FreightGuideGeometry {
    public static final int MAX_PAIR_DISTANCE=16;
    private FreightGuideGeometry() {}
    public static BlockPos warehouse(BlockPos packager, Direction facing) { return packager.relative(facing.getOpposite()); }
    public static boolean inRange(BlockPos packager, BlockPos postbox) { return packager.distSqr(postbox)<=MAX_PAIR_DISTANCE*MAX_PAIR_DISTANCE; }
}
