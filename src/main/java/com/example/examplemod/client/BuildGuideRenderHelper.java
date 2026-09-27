package com.example.examplemod.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;

/** Shared visual semantics for lightweight line-box multiblock guides. */
public final class BuildGuideRenderHelper {
    private BuildGuideRenderHelper() {
    }

    public static float[] color(Status status) {
        return switch (status) {
            case MISSING -> new float[]{0.10F, 0.55F, 1.00F, 0.75F};
            case WRONG -> new float[]{1.00F, 0.08F, 0.08F, 0.95F};
            case OCCUPIED_AIR -> new float[]{1.00F, 0.45F, 0.05F, 0.95F};
            case FUNCTIONAL -> new float[]{0.05F, 0.90F, 1.00F, 0.95F};
        };
    }

    public static void renderOutline(
            PoseStack poseStack, VertexConsumer buffer, BlockPos pos, Status status
    ) {
        float[] color = color(status);
        LevelRenderer.renderLineBox(poseStack, buffer,
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0,
                color[0], color[1], color[2], color[3]);
    }

    public enum Status {
        MISSING,
        WRONG,
        OCCUPIED_AIR,
        FUNCTIONAL
    }
}
