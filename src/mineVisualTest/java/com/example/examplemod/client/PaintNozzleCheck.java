package com.example.examplemod.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PaintNozzleCheck {
    public static void main(String[] args) {
        int count = 0;
        for (float yaw : new float[]{-2, 0, 1.4f})
            for (float pitch : new float[]{-1.5f, 0, 1.5f})
                for (float fov : new float[]{60, 90, 110})
                    for (float side : new float[]{-1, 1})
                        for (boolean bob : new boolean[]{false, true}) {
                            var view = new Matrix4f().rotateX(pitch).rotateY(yaw);
                            var motion = new Matrix4f();
                            if (bob) motion.translate(.02f, -.03f, 0).rotateZ(.06f).rotateX(.03f);
                            var worldProjection = new Matrix4f().perspective((float)Math.toRadians(fov), 16f/9, .05f, 1000).mul(motion);
                            var handProjection = new Matrix4f().perspective((float)Math.toRadians(70), 16f/9, .05f, 1000);
                            var handPose = new Matrix4f(view).invert().mul(motion);
                            var point = handPose.transformPosition(new Vector3f(side*.4f, -.3f, -1.2f));
                            var worldClip = new Matrix4f(worldProjection).mul(view);
                            var offset = PaintNozzleMath.toWorldOffset(new Matrix4f(worldClip).invert(), handProjection, view, point);
                            var actual = worldClip.transformProject(new Vector3f(offset));
                            var expected = new Matrix4f(handProjection).mul(view).transformProject(new Vector3f(point));
                            if (actual.distance(expected) > .00002f) throw new AssertionError("Nozzle screen position mismatch");
                            count++;
                        }
        System.out.println("PASS: " + count + " nozzle projections across yaw/pitch, FOV, both hands and camera bob.");
    }
}
