package com.example.examplemod.mixin;

import com.example.examplemod.client.PaintRiflePose;
import com.example.examplemod.machine.PanelPainterItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.vertex.PoseStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandLayer.class)
public abstract class PaintRifleHeldItemMixin {
    @Inject(method="renderArmWithItem",at=@At("HEAD"),cancellable=true)
    private void ccec$renderAimedPainter(LivingEntity entity,ItemStack stack,ItemDisplayContext context,HumanoidArm arm,
                                       PoseStack pose,MultiBufferSource buffers,int light,CallbackInfo ci) {
        if (!(stack.getItem() instanceof PanelPainterItem) || !(entity instanceof AbstractClientPlayer player)) return;
        var aim=PaintRiflePose.get(player,arm);
        if (aim==null) return;
        pose.pushPose(); pose.mulPose(aim.item()); pose.translate(.5,.5,.5);
        Minecraft.getInstance().getEntityRenderDispatcher().getItemInHandRenderer()
                .renderItem(entity,stack,ItemDisplayContext.NONE,arm==HumanoidArm.LEFT,pose,buffers,light);
        pose.popPose(); ci.cancel();
    }
}
