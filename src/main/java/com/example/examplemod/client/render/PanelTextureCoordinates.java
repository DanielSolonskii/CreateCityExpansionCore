package com.example.examplemod.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** World-aligned 3x3 sheet coordinates, independent of block facing and model UV rotation. */
public final class PanelTextureCoordinates {
    private PanelTextureCoordinates() {}

    public static float u(Direction face, BlockPos pos, float x, float y, float z) {
        return switch (face) {
            case NORTH -> coordinate(pos.getX(), x, true);
            case EAST -> coordinate(pos.getZ(), z, true);
            case WEST -> coordinate(pos.getZ(), z, false);
            default -> coordinate(pos.getX(), x, false);
        };
    }

    public static float v(Direction face, BlockPos pos, float x, float y, float z) {
        return switch (face) {
            case UP -> coordinate(pos.getZ(), z, false);
            case DOWN -> coordinate(pos.getZ(), z, true);
            default -> coordinate(pos.getY(), y, true);
        };
    }

    private static float coordinate(int block, float local, boolean reversed) {
        // Reduce integers before converting to floats: stable even at the world border.
        int phase = Math.floorMod(block, 3);
        return (reversed ? 2 - phase + 1 - local : phase + local) / 3f;
    }
}
