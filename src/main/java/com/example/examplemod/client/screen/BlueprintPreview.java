package com.example.examplemod.client.screen;

import com.example.examplemod.client.BlueprintBlockModels;
import com.example.examplemod.multiblock.BlueprintCatalog.Plan;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;

/** Renders the game's baked block models for the selected construction plan. */
final class BlueprintPreview {
    private static final Quaternionf VIEW = Axis.XP.rotationDegrees(30).mul(Axis.YP.rotationDegrees(-135));
    private static final int MARGIN = 4;
    private static final int MODEL_DEPTH = 150;

    private record Cell(BlockPos position, BlockState state, boolean addition) {}

    private final List<Cell> cells;
    private final List<BlockPos> additions;
    private final float minX;
    private final float minY;
    private final float spanX;
    private final float spanY;

    BlueprintPreview(Plan plan) {
        int topY = BlueprintBlockModels.topLayer(plan);
        cells = plan.parts().stream()
                .filter(part -> !part.block().defaultBlockState().isAir())
                .map(part -> new Cell(part.position(), BlueprintBlockModels.state(part, topY, Direction.NORTH),
                        plan.tier() != Tier.I && part.addedIn() == plan.tier()))
                .sorted(Comparator.comparingDouble(cell -> project(cell.position(), 0.5f, 0.5f, 0.5f).z))
                .toList();
        additions = cells.stream().filter(Cell::addition).map(Cell::position).toList();

        float lowX = Float.POSITIVE_INFINITY;
        float highX = Float.NEGATIVE_INFINITY;
        float lowY = Float.POSITIVE_INFINITY;
        float highY = Float.NEGATIVE_INFINITY;
        for (Cell cell : cells) {
            for (int corner = 0; corner < 8; corner++) {
                Vector3f point = project(cell.position(), corner & 1, (corner >> 1) & 1, (corner >> 2) & 1);
                lowX = Math.min(lowX, point.x);
                highX = Math.max(highX, point.x);
                lowY = Math.min(lowY, -point.y);
                highY = Math.max(highY, -point.y);
            }
        }
        minX = lowX;
        minY = lowY;
        spanX = highX - lowX;
        spanY = highY - lowY;
    }

    void draw(GuiGraphics graphics, int x, int y, int width, int height) {
        if (cells.isEmpty() || width <= 2 * MARGIN || height <= 2 * MARGIN) {
            return;
        }
        float scale = Math.min((width - 2 * MARGIN) / spanX, (height - 2 * MARGIN) / spanY);
        float offsetX = x + (width - spanX * scale) / 2 - minX * scale;
        float offsetY = y + (height - spanY * scale) / 2 - minY * scale;

        graphics.flush();
        RenderSystem.enableDepthTest();
        Lighting.setupFor3DItems();
        var renderer = Minecraft.getInstance().getBlockRenderer();
        var buffers = graphics.bufferSource();
        var pose = graphics.pose();
        pose.pushPose();
        try {
            pose.translate(offsetX, offsetY, MODEL_DEPTH);
            pose.scale(scale, -scale, scale);
            pose.mulPose(VIEW);
            for (Cell cell : cells) {
                pose.pushPose();
                try {
                    BlockPos pos = cell.position();
                    pose.translate(pos.getX(), pos.getY(), pos.getZ());
                    renderer.renderSingleBlock(cell.state(), pose, buffers,
                            LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
                } finally {
                    pose.popPose();
                }
            }
            // GuiGraphics.flush disables depth testing, which is needed to occlude rear blocks.
            buffers.endBatch();
            if (!additions.isEmpty()) {
                var lines = buffers.getBuffer(RenderType.lines());
                for (BlockPos pos : additions) {
                    LevelRenderer.renderLineBox(pose, lines,
                            pos.getX() - 0.005, pos.getY() - 0.005, pos.getZ() - 0.005,
                            pos.getX() + 1.005, pos.getY() + 1.005, pos.getZ() + 1.005,
                            0.83f, 0.64f, 0.39f, 0.85f);
                }
                buffers.endBatch(RenderType.lines());
            }
        } finally {
            pose.popPose();
        }
    }

    private static Vector3f project(BlockPos position, float x, float y, float z) {
        return VIEW.transform(new Vector3f(position.getX() + x, position.getY() + y, position.getZ() + z));
    }

}
