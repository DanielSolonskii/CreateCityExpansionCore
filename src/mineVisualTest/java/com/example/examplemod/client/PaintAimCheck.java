package com.example.examplemod.client;

import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class PaintAimCheck {
    public static void main(String[] args) {
        int count=0;
        for(float side:new float[]{-1,1}) for(boolean slim:new boolean[]{false,true})
            for(float y:new float[]{2f/16,5.2f/16}) for(float yaw:new float[]{-1.5f,-.7f,0,.7f,1.5f})
                for(float pitch:new float[]{-1.5707963f,-.7f,0,.7f,1.5707963f}) {
                    var a=PaintAimMath.solve(side,slim,y,yaw,pitch);
                    float reach=new Vector3f((slim ? .5f : 1f)/16,8.5f/16,0).length();
                    check(Math.abs(a.grip().distance(a.mainShoulder())-reach)<.00001,"Main palm unreachable");
                    check(Math.abs(a.foregrip().distance(a.supportShoulder())-reach)<.00001,"Support palm unreachable");
                    check(a.item().transformPosition(new Vector3f(8f/16,3f/16,14f/16)).distance(a.grip())<.00001,"Grip detached");
                    check(a.item().transformPosition(new Vector3f(8f/16,1.5f/16,14f/16)).distance(a.foregrip())<.00001,"Support hand detached from handle");
                    var inverseTorso=new Quaternionf().rotationY(-yaw);
                    check(inverseTorso.transform(new Vector3f(a.grip())).z<-.25f,"Main hand enters torso");
                    check(inverseTorso.transform(new Vector3f(a.foregrip())).z<-.25f,"Support hand enters torso");
                    var barrel=a.item().transformDirection(new Vector3f(0,0,-1)).normalize();
                    var look=new Quaternionf().rotationYXZ(yaw,pitch,0).transform(new Vector3f(0,0,-1));
                    check(barrel.distance(look)<.00001,"Barrel is not parallel to look");
                    count++;
                }
        System.out.println("PASS: "+count+" aim/dual-grip poses, including both hands, slim/wide, crouch and vertical look.");
    }
    private static void check(boolean value,String message) { if(!value) throw new AssertionError(message); }
}
