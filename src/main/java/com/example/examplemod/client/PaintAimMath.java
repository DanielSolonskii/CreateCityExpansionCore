package com.example.examplemod.client;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Both palms wrap the rear handle; the barrel stays parallel to the head's look vector. */
public final class PaintAimMath {
    private PaintAimMath() {}
    public record Aim(Matrix4f item, Vector3f grip, Vector3f foregrip, Vector3f mainShoulder, Vector3f supportShoulder) {}
    public static Aim solve(float side, boolean slim, float shoulderY, float yaw, float pitch) {
        var torso = new Quaternionf().rotationY(yaw);
        var orientation = new Quaternionf().rotationYXZ(yaw,pitch,0).rotateZ((float)Math.PI);
        var main = torso.transform(new Vector3f(-side*5/16,shoulderY,0));
        var support = torso.transform(new Vector3f(side*5/16,shoulderY,0));
        // The supporting palm holds the lower part of the same handle. A distant
        // barrel grip pulled the main hand back through the torso at steep angles.
        var offset = orientation.transform(new Vector3f(0,-1.5f/16,0).mul(.65f));
        var center2 = new Vector3f(support).sub(offset);
        var axis = new Vector3f(center2).sub(main);
        float distance = axis.length(); axis.div(distance);
        float palmX = (slim ? .5f : 1f)/16;
        float reachSquared = palmX*palmX + (8.5f/16)*(8.5f/16);
        float radius = (float)Math.sqrt(Math.max(0,reachSquared-distance*distance/4));
        var preferred = torso.transform(new Vector3f(0,.6f,-1));
        preferred.sub(new Vector3f(axis).mul(preferred.dot(axis))).normalize();
        var grip = new Vector3f(main).add(new Vector3f(axis).mul(distance/2)).add(preferred.mul(radius));
        var foregrip = new Vector3f(grip).add(offset);
        var item = new Matrix4f().translation(grip).rotate(orientation).scale(.65f)
                .translate(-8f/16,-3f/16,-14f/16);
        return new Aim(item,grip,foregrip,main,support);
    }
}
