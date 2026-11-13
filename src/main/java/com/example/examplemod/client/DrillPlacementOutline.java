package com.example.examplemod.client;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.network.DrillPlacementFeedback;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.OptionalDouble;

@EventBusSubscriber(modid = CreateCityExpansionCore.MODID, value = Dist.CLIENT)
public final class DrillPlacementOutline {
    private static ClientLevel level;
    private static BlockPos origin;
    private static long expires;

    @SubscribeEvent
    public static void failed(DrillPlacementFeedback.Failed event) {
        if (event.level != Minecraft.getInstance().level) return;
        level = (ClientLevel) event.level;
        origin = event.origin;
        expires = level.getGameTime() + 40;
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (level != Minecraft.getInstance().level || level == null || level.getGameTime() >= expires) {
            level = null;
            origin = null;
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || origin == null
                || level != Minecraft.getInstance().level) return;
        var camera = event.getCamera().getPosition();
        var pose = event.getPoseStack();
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(origin.getX() - camera.x, origin.getY() - camera.y, origin.getZ() - camera.z);
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(OutlineLayer.TYPE),
                -.005, -.005, -.005, 2.005, 2.005, 2.005, 1F, .08F, .08F, .95F);
        buffers.endBatch(OutlineLayer.TYPE);
        pose.popPose();
    }

    /** A depth-independent outline remains visible when the rejected volume is inside a wall. */
    private static final class OutlineLayer extends RenderType {
        private static final RenderType TYPE = create("ccec_drill_placement_failure", DefaultVertexFormat.POSITION_COLOR_NORMAL,
                VertexFormat.Mode.LINES, 256, false, false, CompositeState.builder()
                        .setShaderState(RENDERTYPE_LINES_SHADER)
                        .setLineState(new LineStateShard(OptionalDouble.of(2.5)))
                        .setDepthTestState(NO_DEPTH_TEST).setWriteMaskState(COLOR_WRITE)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL)
                        .createCompositeState(false));

        private OutlineLayer() {
            super("unused", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 256,
                    false, false, () -> {}, () -> {});
        }
    }
}
