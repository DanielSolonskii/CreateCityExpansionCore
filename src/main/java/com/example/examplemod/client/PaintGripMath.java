package com.example.examplemod.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Align the authored grip center (8,3,14 model pixels) with the already-rendered palm. */
public final class PaintGripMath {
    private PaintGripMath() {}
    public static Vector3f offset(Matrix4f toolPose, Matrix4f modelPose, Vector3f palm) {
        Vector3f localPalm = new Matrix4f(toolPose).invert().transformPosition(new Vector3f(palm));
        Vector3f grip = modelPose.transformPosition(new Vector3f(8f/16, 3f/16, 14f/16));
        return localPalm.sub(grip);
    }
}
