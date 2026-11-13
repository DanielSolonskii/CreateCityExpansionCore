package com.example.examplemod.mixin;

import com.example.examplemod.client.PanelPainterHands;
import com.example.examplemod.client.PaintRiflePose;
import com.example.examplemod.machine.PanelPainterItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class PaintRifleModelPoseMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void ccec$aimPainter(LivingEntity entity,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci) {
        if (PanelPainterHands.isRenderingHand() || !(entity instanceof AbstractClientPlayer player)) return;
        var arm=player.getMainArm();
        if (!(player.getMainHandItem().getItem() instanceof PanelPainterItem)) {
            if (!(player.getOffhandItem().getItem() instanceof PanelPainterItem)) return;
            arm=arm.getOpposite();
        }
        PaintRiflePose.apply((HumanoidModel<?>)(Object)this,player,arm);
    }
}
