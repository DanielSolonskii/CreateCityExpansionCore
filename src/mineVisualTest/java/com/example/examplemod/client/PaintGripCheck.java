package com.example.examplemod.client;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class PaintGripCheck {
    public static void main(String[] args) {
        int samples = 0;
        for (int side : new int[]{-1, 1}) for (float swing : new float[]{0, .2f, .7f, 1})
                for (float scale : new float[]{.45f, .60f, .75f}) for (boolean slim : new boolean[]{false, true}) {
            var tool = new Matrix4f().translate(side*.54f,-.4f-swing*.6f,-.82f)
                    .rotateY(side*swing).rotateZ(-side*swing*.3f);
            var model = new Matrix4f().translate(0,3f/16,-3.6f/16).scale(scale).translate(-.5f,-.5f,-.5f);
            var palm = new Vector3f(side*(slim ? .42f : .45f),-.35f-swing*.4f,-.65f+swing*.2f);
            var offset = PaintGripMath.offset(tool, model, palm);
            var actual = new Matrix4f(tool).translate(offset).mul(model)
                    .transformPosition(new Vector3f(8f/16,3f/16,14f/16));
            if (actual.distance(palm) > 0.00001) throw new AssertionError("Grip detached: " + actual + " vs " + palm);
            samples++;
        }
        System.out.println("PASS: " + samples + " grip transforms; both hands, slim/wide, swing/equip and three scales.");
    }
}
