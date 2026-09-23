package com.example.examplemod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public class CityBoundaryRenderer {

    private static boolean visible = false;

    private static BlockPos pylonPos = null;
    private static int territorySize = 100;

    public static void setVisible(
            boolean visible,
            BlockPos pylonPos,
            int territorySize
    ) {
        CityBoundaryRenderer.visible = visible;
        CityBoundaryRenderer.pylonPos = pylonPos;
        CityBoundaryRenderer.territorySize = territorySize;
    }

    public static void hide() {
        visible = false;
        pylonPos = null;
    }
    public static boolean isVisible() {
        return visible;
    }
    @SubscribeEvent
    public static void onRenderLevel(
            RenderLevelStageEvent event
    ) {
        if (!visible || pylonPos == null) {
            return;
        }

        if (event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();

        MultiBufferSource.BufferSource bufferSource =
                minecraft.renderBuffers().bufferSource();

        VertexConsumer buffer =
                bufferSource.getBuffer(RenderType.lines());

        var cameraPos =
                event.getCamera().getPosition();

        double halfSize =
                territorySize / 2.0;

        double minX =
                pylonPos.getX() - halfSize;

        double maxX =
                pylonPos.getX() + halfSize;

        double minZ =
                pylonPos.getZ() - halfSize;

        double maxZ =
                pylonPos.getZ() + halfSize;

        poseStack.pushPose();

        poseStack.translate(
                -cameraPos.x,
                -cameraPos.y,
                -cameraPos.z
        );

        /*
         * =====================================
         * ВЕРХНЯЯ ГРАНИЦА
         * =====================================
         */

        renderEdge(
                minecraft,
                poseStack,
                buffer,
                (int) minX,
                (int) maxX,
                (int) minZ,
                true
        );

        /*
         * =====================================
         * НИЖНЯЯ ГРАНИЦА
         * =====================================
         */

        renderEdge(
                minecraft,
                poseStack,
                buffer,
                (int) minX,
                (int) maxX,
                (int) maxZ,
                true
        );

        /*
         * =====================================
         * ЛЕВАЯ ГРАНИЦА
         * =====================================
         */

        renderEdgeZ(
                minecraft,
                poseStack,
                buffer,
                (int) minZ,
                (int) maxZ,
                (int) minX
        );

        /*
         * =====================================
         * ПРАВАЯ ГРАНИЦА
         * =====================================
         */

        renderEdgeZ(
                minecraft,
                poseStack,
                buffer,
                (int) minZ,
                (int) maxZ,
                (int) maxX
        );

        poseStack.popPose();

        bufferSource.endBatch(RenderType.lines());
    }

    private static void renderEdge(
            Minecraft minecraft,
            PoseStack poseStack,
            VertexConsumer buffer,
            int startX,
            int endX,
            int z,
            boolean horizontal
    ) {
        int step = 2;

        for (int x = startX; x < endX; x += step) {

            int nextX =
                    Math.min(x + step, endX);

            int y1 =
                    minecraft.level.getHeight(
                            Heightmap.Types.WORLD_SURFACE,
                            x,
                            z
                    );

            int y2 =
                    minecraft.level.getHeight(
                            Heightmap.Types.WORLD_SURFACE,
                            nextX,
                            z
                    );

            LevelRenderer.renderLineBox(
                    poseStack,
                    buffer,
                    x,
                    y1 + 0.05,
                    z,
                    nextX,
                    y2 + 0.05,
                    z,
                    1.0f,
                    0.65f,
                    0.1f,
                    0.9f
            );
        }
    }

    private static void renderEdgeZ(
            Minecraft minecraft,
            PoseStack poseStack,
            VertexConsumer buffer,
            int startZ,
            int endZ,
            int x
    ) {
        int step = 2;

        for (int z = startZ; z < endZ; z += step) {

            int nextZ =
                    Math.min(z + step, endZ);

            int y1 =
                    minecraft.level.getHeight(
                            Heightmap.Types.WORLD_SURFACE,
                            x,
                            z
                    );

            int y2 =
                    minecraft.level.getHeight(
                            Heightmap.Types.WORLD_SURFACE,
                            x,
                            nextZ
                    );

            LevelRenderer.renderLineBox(
                    poseStack,
                    buffer,
                    x,
                    y1 + 0.05,
                    z,
                    x,
                    y2 + 0.05,
                    nextZ,
                    1.0f,
                    0.65f,
                    0.1f,
                    0.9f
            );
        }
    }
}