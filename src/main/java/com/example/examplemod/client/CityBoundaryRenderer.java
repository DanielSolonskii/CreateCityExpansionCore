package com.example.examplemod.client;

import com.example.examplemod.city.CityClaims;
import com.example.examplemod.city.CityColors;
import com.example.examplemod.city.CityPlot;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;
import java.util.Set;/**
 * Граница территории города в мире.
 *
 * <p><b>По участкам, а не по квадрату.</b> Раньше рендерер рисовал
 * четыре линии вокруг пилона по {@code territorySize}, то есть одну и ту
 * же геометрию, что и карта, но вычисленную отдельно. Сейчас оба
 * изображения рисуют один и тот же источник — заявленные участки
 * 50×50, — поэтому расходиться они больше не могут.
 *
 * <p>Рисуется только внешний контур: ребро участка попадает в линию,
 * когда соседний участок городом не занят. Внутренние границы не
 * рисуются, поэтому большая территория выглядит одной землёй, а не
 * сеткой. Линия идёт по рельефу, как и раньше: граница должна
 * прослеживаться по холмам, а не провисать под землёй.
 *
 * <p>Состояние приходит из кеша City Pylon и не пересчитывается в
 * кадре: ни CityManager, ни NBT здесь не читаются.
 */
public class CityBoundaryRenderer {

    /** Шаг выборки высоты вдоль ребра, в блоках. */
    private static final int HEIGHT_STEP = 2;

    private static boolean visible = false;

    private static Set<CityPlot> plots = Set.of();
    private static int colorRgb = CityColors.PALETTE.get(0);

    /**
     * Показывает или скрывает границу.
     *
     * <p>Пересчёт контура происходит только когда данные действительно
     * изменились. Экран City Pylon дёргает этот метод каждый тик, а
     * территория между тиками обычно та же, поэтому сравнение отсекает
     * и копирование списка, и пересборку контура впустую.
     *
     * @param territory реальные участки города; {@code null} или пустой список ничего не рисует
     * @param cityColor  собственный цвет города {@code 0xRRGGBB}
     */
    public static void setVisible(
            boolean visible,
            Set<CityPlot> territory,
            int cityColor
    ) {
        CityBoundaryRenderer.visible = visible;
        Set<CityPlot> incoming = territory == null ? Set.of() : Set.copyOf(territory);
        int color = CityColors.normalize(cityColor);
        if (incoming.equals(plots) && color == colorRgb) {
            return;
        }
        CityBoundaryRenderer.plots = Set.copyOf(incoming);
        CityBoundaryRenderer.colorRgb = color;
    }

    public static void hide() {
        visible = false;
        plots = Set.of();
    }

    public static boolean isVisible() {
        return visible;
    }

    @SubscribeEvent
    public static void onRenderLevel(
            RenderLevelStageEvent event
    ) {
        if (!visible || plots.isEmpty()) {
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

        /*
         * Контур считается из участков при каждой смене данных, а не
         * каждый кадр: операция маленькая, но она не зависит от кадра
         * и потому уходит из горячего пути рендера.
         */
        List<int[]> outline = CityClaims.outline(plots);
        if (outline.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();

        MultiBufferSource.BufferSource bufferSource =
                minecraft.renderBuffers().bufferSource();

        VertexConsumer buffer =
                bufferSource.getBuffer(RenderType.lines());

        var cameraPos =
                event.getCamera().getPosition();

        float red = ((colorRgb >> 16) & 0xFF) / 255.0F;
        float green = ((colorRgb >> 8) & 0xFF) / 255.0F;
        float blue = (colorRgb & 0xFF) / 255.0F;

        poseStack.pushPose();

        poseStack.translate(
                -cameraPos.x,
                -cameraPos.y,
                -cameraPos.z
        );

        for (int[] edge : outline) {
            int x1 = edge[0];
            int z1 = edge[1];
            int x2 = edge[2];
            int z2 = edge[3];
            if (x1 == x2) {
                renderVerticalEdge(minecraft, poseStack, buffer, x1, z1, z2, red, green, blue);
            } else {
                renderHorizontalEdge(minecraft, poseStack, buffer, z1, x1, x2, red, green, blue);
            }
        }

        poseStack.popPose();

        bufferSource.endBatch(RenderType.lines());
    }

    /** Ребро вдоль X на постоянном Z. */
    private static void renderHorizontalEdge(
            Minecraft minecraft,
            PoseStack poseStack,
            VertexConsumer buffer,
            int z,
            int startX,
            int endX,
            float red,
            float green,
            float blue
    ) {
        for (int x = startX; x < endX; x += HEIGHT_STEP) {

            int nextX =
                    Math.min(x + HEIGHT_STEP, endX);

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
                    red,
                    green,
                    blue,
                    0.9f
            );
        }
    }

    /** Ребро вдоль Z на постоянном X. */
    private static void renderVerticalEdge(
            Minecraft minecraft,
            PoseStack poseStack,
            VertexConsumer buffer,
            int x,
            int startZ,
            int endZ,
            float red,
            float green,
            float blue
    ) {
        for (int z = startZ; z < endZ; z += HEIGHT_STEP) {

            int nextZ =
                    Math.min(z + HEIGHT_STEP, endZ);

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
                    red,
                    green,
                    blue,
                    0.9f
            );
        }
    }
}
