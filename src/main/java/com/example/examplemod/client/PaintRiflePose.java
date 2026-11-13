package com.example.examplemod.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.WeakHashMap;

/** Applied after vanilla crouch, swing and bob transforms. */
public final class PaintRiflePose {
    public static final net.neoforged.fml.common.asm.enumextension.EnumProxy<HumanoidModel.ArmPose> HOLD =
            new net.neoforged.fml.common.asm.enumextension.EnumProxy<>(HumanoidModel.ArmPose.class,true,
                    (net.neoforged.neoforge.client.IArmPoseTransformer)(model,entity,arm)-> {});
    private record Held(HumanoidArm arm, PaintAimMath.Aim aim) {}
    private static final WeakHashMap<AbstractClientPlayer,Held> POSES = new WeakHashMap<>();
    public static PaintAimMath.Aim get(AbstractClientPlayer player,HumanoidArm arm) {
        var pose=POSES.get(player);
        return pose!=null && pose.arm==arm ? pose.aim : null;
    }
    public static void apply(HumanoidModel<?> model,AbstractClientPlayer player,HumanoidArm arm) {
        float side=arm==HumanoidArm.RIGHT ? 1 : -1;
        boolean slim=player.getSkin().model()==PlayerSkin.Model.SLIM;
        var main=side>0 ? model.rightArm : model.leftArm;
        var support=side>0 ? model.leftArm : model.rightArm;
        var aim=PaintAimMath.solve(side,slim,main.y/16,model.head.yRot,model.head.xRot);
        model.body.yRot=model.head.yRot;
        poseArm(main,aim.mainShoulder(),aim.grip(),-side*(slim ? .5f : 1f)/16);
        poseArm(support,aim.supportShoulder(),aim.foregrip(),side*(slim ? .5f : 1f)/16);
        POSES.put(player,new Held(arm,aim));
    }
    private static void poseArm(ModelPart arm,Vector3f shoulder,Vector3f target,float palmX) {
        arm.setPos(shoulder.x*16,shoulder.y*16,shoulder.z*16);
        var rotation=new Quaternionf().rotationTo(new Vector3f(palmX,8.5f/16,0),new Vector3f(target).sub(shoulder))
                .getEulerAnglesZYX(new Vector3f());
        arm.xRot=rotation.x; arm.yRot=rotation.y; arm.zRot=rotation.z;
    }
}
