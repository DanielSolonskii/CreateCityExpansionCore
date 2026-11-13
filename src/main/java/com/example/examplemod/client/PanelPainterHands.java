package com.example.examplemod.client;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.machine.PanelPainterItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.content.equipment.zapper.ShootableGadgetRenderHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import net.minecraft.world.entity.HumanoidArm;

/** Uses Create's gadget hand rendering, including the player's own skin and slim arms. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT)
public final class PanelPainterHands {
    private static final Hands HANDS = new Hands();
    private static final Vec3[] NOZZLES = new Vec3[2];
    private static final long[] NOZZLE_TICKS = new long[2];
    private static ClientLevel nozzleLevel;
    private static boolean renderingHand;
    public static boolean isRenderingHand() { return renderingHand; }
    private static final Matrix4f inverseWorldClip = new Matrix4f();
    private static boolean hasWorldProjection;

    @SubscribeEvent
    public static void worldProjection(net.neoforged.neoforge.client.event.RenderLevelStageEvent event) {
        if (event.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            inverseWorldClip.set(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).invert();
            hasWorldProjection = true;
        }
    }

    public static Vec3 firstPersonNozzle(HumanoidArm arm) {
        var level = Minecraft.getInstance().level;
        int index = arm == HumanoidArm.RIGHT ? 0 : 1;
        return level != null && level == nozzleLevel && level.getGameTime() - NOZZLE_TICKS[index] <= 2
                ? NOZZLES[index] : null;
    }

    @SubscribeEvent
    public static void renderHand(RenderHandEvent event) {
        var player = Minecraft.getInstance().player;
        if (player != null && !player.isInvisible()) {
            renderingHand = true;
            try { HANDS.render(event); } finally { renderingHand = false; }
        }
    }

    private static final class Hands extends ShootableGadgetRenderHandler {
        private final Matrix4f handPose = new Matrix4f();
        void render(RenderHandEvent event) { super.onRenderPlayerHand(event); }
        @Override protected boolean appliesTo(ItemStack stack) { return stack.getItem() instanceof PanelPainterItem; }
        @Override protected void playSound(InteractionHand hand, Vec3 position) {}
        @Override protected void transformTool(PoseStack pose, float side, float equip, float recoil, float partial) {
            var mc = Minecraft.getInstance();
            var player = mc.player;
            if (player == null || mc.level == null) return;
            boolean left = side < 0;
            var arm = left ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
            var renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
            var armPart = left ? renderer.getModel().leftArm : renderer.getModel().rightArm;
            var palmPose = new PoseStack();
            palmPose.last().pose().set(handPose);
            // Create has just rendered the hand: use its actual animated part transform.
            armPart.translateAndRotate(palmPose);
            float palmX = player.getSkin().model() == PlayerSkin.Model.SLIM ? .5f : 1f;
            var palm = palmPose.last().pose().transformPosition(new Vector3f(-side * palmX / 16, 8.5f / 16, 0));
            var hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
            var stack = player.getItemInHand(hand);
            var model = mc.getItemRenderer().getModel(stack, mc.level, player, 0);
            var modelPose = new PoseStack();
            model.getTransforms().getTransform(left ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND).apply(left, modelPose);
            modelPose.translate(-.5, -.5, -.5);
            var offset = PaintGripMath.offset(pose.last().pose(), modelPose.last().pose(), palm);
            pose.translate(offset.x, offset.y, offset.z);

            var nozzle = new Matrix4f(pose.last().pose()).mul(modelPose.last().pose())
                    .transformPosition(new Vector3f(8f/16, 9.25f/16, -7.43f/16));
            // The hand pose includes inverse camera rotation. Use the full render
            // matrices (including FOV and view bob), rather than rotating it twice.
            if (!hasWorldProjection) return;
            nozzle = PaintNozzleMath.toWorldOffset(inverseWorldClip,
                    com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix(),
                    com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix(), nozzle);
            if (nozzleLevel != mc.level) {
                NOZZLES[0] = null; NOZZLES[1] = null; nozzleLevel = mc.level;
            }
            int index = left ? 1 : 0;
            NOZZLES[index] = new Vec3(nozzle.x, nozzle.y, nozzle.z);
            NOZZLE_TICKS[index] = mc.level.getGameTime();
        }
        @Override protected void transformHand(PoseStack pose, float side, float equip, float recoil, float partial) {
            pose.translate(side * -.09, -.275, -.25);
            pose.mulPose(Axis.ZP.rotationDegrees(side * -10));
            handPose.set(pose.last().pose());
        }
    }

    @EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Extensions {
        @SubscribeEvent
        public static void register(RegisterClientExtensionsEvent event) {
            event.registerItem(new IClientItemExtensions() {
                @Override public HumanoidModel.ArmPose getArmPose(LivingEntity player, InteractionHand hand, ItemStack stack) {
                    return renderingHand ? HumanoidModel.ArmPose.CROSSBOW_HOLD : PaintRiflePose.HOLD.getValue();
                }
            }, CreateCityExpansionCore.PANEL_PAINTER.get());
        }
    }
}
