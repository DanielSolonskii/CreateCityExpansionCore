package com.example.examplemod.client.render;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Checks continuity on all faces, including negative chunks and the world border. */
public final class PanelTextureCoordinatesCheck {
    public static void main(String[] args) {
        int checked = 0;
        for (int coordinate : new int[]{-29999999, -33, -17, -3, -1, 0, 1, 2, 15, 16, 29999998}) {
            BlockPos origin = new BlockPos(coordinate, coordinate, coordinate);
            for (Direction face : Direction.values()) {
                for (Direction neighbor : new Direction[]{Direction.EAST, Direction.UP, Direction.SOUTH}) {
                    if (face.getAxis() == neighbor.getAxis()) continue;
                    for (float along : new float[]{0, .0625f, .5f, .9375f, 1}) {
                        float[] a = {along, along, along};
                        float[] b = a.clone();
                        int axis = neighbor.getAxis().ordinal();
                        a[axis] = 1; b[axis] = 0;
                        same(PanelTextureCoordinates.u(face, origin, a[0], a[1], a[2]),
                                PanelTextureCoordinates.u(face, origin.relative(neighbor), b[0], b[1], b[2]));
                        same(PanelTextureCoordinates.v(face, origin, a[0], a[1], a[2]),
                                PanelTextureCoordinates.v(face, origin.relative(neighbor), b[0], b[1], b[2]));
                        checked++;
                    }
                }
                float start = PanelTextureCoordinates.u(face, origin, 0, 0, 0);
                float end = PanelTextureCoordinates.u(face, origin, 1, 1, 1);
                if (Math.abs(Math.abs(end - start) - 1f / 3) > .00001f)
                    throw new AssertionError("A block must span one third of the sheet");
            }
        }
        System.out.println("Panel UV continuity: " + checked + " shared-edge samples passed on all six faces.");
    }

    private static void same(float a, float b) {
        if (a < 0 || a > 1 || b < 0 || b > 1) throw new AssertionError("UV outside sprite");
        float difference = Math.abs(a - b);
        if (difference > .00001f && Math.abs(difference - 1) > .00001f)
            throw new AssertionError("Discontinuous panel seam: " + a + " / " + b);
    }
}
