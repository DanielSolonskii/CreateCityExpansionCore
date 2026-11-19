package com.example.examplemod.client;

import com.example.examplemod.CreateCityExpansionCore;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT)
public final class FieldInstrumentHands {
    private FieldInstrumentHands() {}

    @SubscribeEvent
    public static void render(RenderHandEvent event) {
        var mc = Minecraft.getInstance();
        var stack = event.getItemStack();
        boolean tablet = stack.is(CreateCityExpansionCore.BLUEPRINT_TERMINAL.get());
        if (mc.player == null || (!tablet && !stack.is(CreateCityExpansionCore.MAGNETIC_ANALYZER.get()))) return;

        event.setCanceled(true);
        var player = mc.player;
        boolean left = (event.getHand() == InteractionHand.MAIN_HAND) == (player.getMainArm() == HumanoidArm.LEFT);
        int side = left ? -1 : 1;
        var context = left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        try {
            float swing = Mth.sin(Mth.sqrt(event.getSwingProgress()) * Mth.PI);
            pose.translate(side * (tablet ? .31 : .34), -.21 - .42 * event.getEquipProgress(), -.92);
            pose.mulPose(Axis.XP.rotationDegrees(-8 * swing));
            mc.gameRenderer.itemInHandRenderer.renderItem(player, stack, context, left, pose,
                    event.getMultiBufferSource(), event.getPackedLight());
            if (player.isInvisible()) return;

            // Locate the grip in the displayed model, so either hand stays attached as the item moves.
            var grip = tablet ? new Vector3f(left ? 1.3F : 14.45F, 5.55F, 7.9F) : new Vector3f(8, 3.55F, 7.7F);
            var local = new PoseStack();
            mc.getItemRenderer().getModel(stack, mc.level, player, 0).getTransforms().getTransform(context).apply(left, local);
            local.last().pose().transformPosition(grip.div(16).sub(.5F, .5F, .5F));
            pose.translate(grip.x + side * .045F, grip.y - .045F, grip.z);
            pose.mulPose(Axis.ZP.rotationDegrees(side * 24));
            pose.mulPose(Axis.XP.rotationDegrees(-56));
            float palmX = player.getSkin().model() == PlayerSkin.Model.SLIM ? 5.5F : 6;
            pose.translate(side * palmX / 16, -10F / 16, 0);
            var renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
            if (left) renderer.renderLeftHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
            else renderer.renderRightHand(pose, event.getMultiBufferSource(), event.getPackedLight(), player);
        } finally {
            pose.popPose();
        }
    }
}
