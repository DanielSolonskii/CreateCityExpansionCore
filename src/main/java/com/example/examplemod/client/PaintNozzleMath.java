package com.example.examplemod.client;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Converts the actual rendered hand position to a camera-relative world position. */
public final class PaintNozzleMath {
    private PaintNozzleMath() {}

    public static Vector3f toWorldOffset(Matrix4fc inverseWorldClip, Matrix4fc handProjection,
                                        Matrix4fc handView, Vector3f handPosition) {
        return new Matrix4f(inverseWorldClip).mul(handProjection).mul(handView)
                .transformProject(new Vector3f(handPosition));
    }
}
