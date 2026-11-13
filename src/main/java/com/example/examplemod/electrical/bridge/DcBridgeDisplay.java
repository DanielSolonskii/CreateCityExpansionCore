package com.example.examplemod.electrical.bridge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = PowerBridge.MOD_ID, dist = Dist.CLIENT)
public final class DcBridgeDisplay implements BlockEntityRenderer<DcBridgeEntity> {
    public DcBridgeDisplay(IEventBus bus) {
        bus.addListener((EntityRenderersEvent.RegisterRenderers event) ->
                event.registerBlockEntityRenderer(PowerBridge.ENTITY.get(), DcBridgeDisplay::new));
    }

    private DcBridgeDisplay(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(DcBridgeEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int light, int overlay) {
        if (PowerBridge.tfmgSupported()) TfmgTerminalDisplay.render(entity,pose,buffers);
        Direction front = entity.getBlockState().getValue(DcBridgeBlock.FACING);
        boolean tfmg = entity.isTfmgSection();
        DcBridgeEntity root = entity.controller();
        boolean input = tfmg != root.eeToTfmg();
        pose.pushPose();
        pose.translate(.5, (tfmg ? 5.75 : 6.75) / 16, .5);
        pose.mulPose(Axis.YP.rotationDegrees(switch (front) {
            case EAST -> 90;
            case SOUTH -> 0;
            case WEST -> 270;
            default -> 180;
        }));
        pose.translate(0, 0, .5 - .89 / 16);
        pose.scale(1 / 200f, -1 / 200f, 1 / 200f);
        text(pose, buffers, (tfmg ? "TFMG" : "EE") + (input ? " / IN" : " / OUT"), -30, 0xff8cdb79);
        text(pose, buffers, String.format(java.util.Locale.ROOT, "%.0f V   %.1f A",
                root.readingVolts(input), root.readingAmps(input)), -16, 0xffa4f18a);
        text(pose, buffers, String.format(java.util.Locale.ROOT, "%.0f W", root.readingWatts(input)), -3, 0xffa4f18a);
        double fraction = Math.clamp(root.readingWatts(input) / PowerBridge.MAX_WATTS.get(), 0, 1);
        String bar = "|".repeat((int)Math.round(fraction * 20));
        text(pose, buffers, bar.isEmpty() ? "--------------------" : bar, 10, bar.isEmpty() ? 0xff35513a : 0xff68db55);
        text(pose, buffers, root.assembled() ? root.status().getString() : "2x1x1 ?", 25, 0xffd1b879);
        pose.popPose();
    }

    private static void text(PoseStack pose, MultiBufferSource buffers, String value, int y, int color) {
        Font font = Minecraft.getInstance().font;
        float scale = Math.min(1, 116f / Math.max(1, font.width(value)));
        pose.pushPose();
        pose.translate(0, y, 0);
        pose.scale(scale, scale, 1);
        font.drawInBatch(value, -font.width(value) / 2f, 0, color, false, pose.last().pose(), buffers,
                Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
