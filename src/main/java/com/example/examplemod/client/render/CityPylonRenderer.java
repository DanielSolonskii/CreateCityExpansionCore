package com.example.examplemod.client.render;

import com.example.examplemod.block.CityPylonBlockEntity;
import com.example.examplemod.client.sound.PylonSoundController;
import com.example.examplemod.client.screen.CityPylonScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Renderer анимируемых частей City Pylon.
 *
 * <p>Корпус ({@code static_body}) рисует обычный blockstate, поэтому здесь
 * отрисовываются только восемь отдельных частей модели. Каждая часть —
 * обычная vanilla block model, повёрнутая вокруг собственного pivot
 * из integration_manifest.json.
 *
 * <p>Зависимостей кроме vanilla NeoForge нет: {@code net.createmod.catnip}
 * и Flywheel недоступны на compile classpath, потому что Create подключён
 * с {@code transitive = false}. Используется
 * {@link ModelBlockRenderer#tesselateBlock} — штатный vanilla-путь.
 *
 * <p>В игровом цикле нет аллокаций: модели кэшируются, каждый кадр
 * меняются только трансформации и lightmap.
 */
@OnlyIn(Dist.CLIENT)
public class CityPylonRenderer implements BlockEntityRenderer<CityPylonBlockEntity> {

    /*
     * =========================================
     * СКОРОСТИ И ТАЙМИНГИ
     * =========================================
     *
     * Взяты из Blockbench reference-клипов (art/city_pylon/city_pylon.bbmodel):
     *   idle_mechanical_reference (60 с, loop):
     *     top_ring  0..360°  ->  6°/с
     *     small_gear 0..-720° -> -12°/с
     *   open_mechanical_reference (1.5 с, once):
     *     top_ring  0..24°   ->  16°/с
     *   level_up_mechanical_reference (1.6 с, once):
     *     selector_knob 0..-22.5° за 0.4 с
     */

    /** Медленное постоянное вращение верхнего механизма, градусов/с. */
    private static final double TOP_RING_IDLE_SPEED = 6.0D;

    /** Скорость верхнего механизма при открытом GUI, градусов/с. */
    private static final double TOP_RING_OPEN_SPEED = 16.0D;

    /** Вращение малой шестерни, градусов/с. */
    private static final double GEAR_SPEED = -12.0D;

    /** Полный поворот ручки селектора, градусов. */
    private static final double SELECTOR_KNOB_SWEEP = 22.5D;

    /** Длительность поворота ручки селектора, секунды. */
    private static final double SELECTOR_KNOB_SWEEP_SECONDS = 0.4D;

    /** Период цикла активности ручки селектора, секунды. */
    private static final double SELECTOR_KNOB_CYCLE = 6.0D;

    /** Период редкой вспышки amber-лампы, секунды. */
    private static final double AMBER_BLINK_PERIOD = 4.0D;

    /** Длительность вспышки amber-лампы, секунды. */
    private static final double AMBER_BLINK_ON = 0.3D;

    /** Скорость сглаживания перехода «GUI открыт», 1/с. */
    private static final float OPEN_BLEND_SPEED = 4.0F;

    /** Уровень свечения экрана в idle. */
    private static final float SCREEN_IDLE_LIGHT = 0.35F;

    /** Уровень свечения экрана при открытом GUI. */
    private static final float SCREEN_OPEN_LIGHT = 0.65F;

    /** Квадрат дистанции отсечения. */
    private static final double MAX_RENDER_DISTANCE_SQ = 64.0D * 64.0D;

    /** Фиксированный источник: детерминирован, аллокаций нет. */
    private static final RandomSource RANDOM = RandomSource.create(0L);

    private final ModelBlockRenderer modelRenderer;

    public CityPylonRenderer(BlockEntityRendererProvider.Context context) {
        this.modelRenderer =
                Minecraft.getInstance().getBlockRenderer().getModelRenderer();
    }

    @Override
    public void render(
            CityPylonBlockEntity be,
            float partialTicks,
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            int overlay
    ) {

        var level = be.getLevel();
        if (level == null || !level.isClientSide) {
            return;
        }

        final BlockPos pos = be.getBlockPos();
        final BlockState state = be.getBlockState();

        /*
         * Отсечение по дистанции. Дорогие части не нужны издалека,
         * кэш геометрии при этом остаётся валидным.
         */
        Vec3 camera = Minecraft.getInstance()
                .gameRenderer
                .getMainCamera()
                .getPosition();
        if (camera.distanceToSqr(
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D
        ) > MAX_RENDER_DISTANCE_SQ) {
            return;
        }

        final double time = level.getGameTime() + partialTicks;
        final boolean active = be.isActive();

        /*
         * =========================================
         * ЗВУК
         * =========================================
         *
         * Тихая петля гулкого idle и редкий щелчок реле.
         *
         * ВАЖНО: это не должно работать как «каждый кадр ->
         * playSound». LoopSoundManager держит не более одной петли
         * на позицию, а реле ограничено по времени. Поэтому
         * вызовы безвредны и не плодят дубликаты.
         *
         * Тишина важнее эффектности: гул звучит только у
         * активного пилона и только вблизи.
         */

        PylonSoundController.update(pos, active, time);

        /*
         * =========================================
         * GUI OPEN
         * =========================================
         *
         * Полностью клиентская проверка, без сетевых пакетов:
         * у открытого Menu есть BlockPos пилона.
         */

        final boolean guiOpen = isGuiOpenFor(pos);
        final float blend = approach(be.getOpenBlend(), guiOpen ? 1.0F : 0.0F,
                OPEN_BLEND_SPEED * partialTicks);
        be.setOpenBlend(blend);

        /*
         * =========================================
         * ВРАЩЕНИЕ
         * =========================================
         *
         * Фаза накапливается, поэтому смена скорости при открытии GUI
         * не вызывает скачка угла.
         */

        be.advanceRingPhase(
                Mth.lerp(blend, TOP_RING_IDLE_SPEED, TOP_RING_OPEN_SPEED) * partialTicks
        );
        be.advanceGearPhase(GEAR_SPEED * partialTicks);

        final VertexConsumer solid = buffers.getBuffer(RenderType.solid());
        final VertexConsumer cutout = buffers.getBuffer(RenderType.cutout());
        final int fullBright = LightTexture.FULL_BRIGHT;

        pose.pushPose();
        applyFacingRotation(pose, state);

        /*
         * =========================================
         * ВЕРХНЕЕ КОЛЬЦО
         * =========================================
         */

        pose.pushPose();
        rotateAround(pose,
                CityPylonModels.PIVOT_TOP_RING_X,
                CityPylonModels.PIVOT_TOP_RING_Y,
                CityPylonModels.PIVOT_TOP_RING_Z,
                Axis.YP,
                be.getRingPhase());
        render(be, state, pos, pose, solid, light, CityPylonModels.topRing());
        pose.popPose();

        /*
         * =========================================
         * МАЛАЯ ШЕСТЕРНЯ
         * =========================================
         */

        pose.pushPose();
        rotateAround(pose,
                CityPylonModels.PIVOT_SMALL_GEAR_X,
                CityPylonModels.PIVOT_SMALL_GEAR_Y,
                CityPylonModels.PIVOT_SMALL_GEAR_Z,
                Axis.ZP,
                be.getGearPhase());
        render(be, state, pos, pose, solid, light, CityPylonModels.smallGear());
        pose.popPose();

        /*
         * =========================================
         * РУЧКА СЕЛЕКТОРА
         * =========================================
         */

        final double knobAngle = selectorKnobAngle(time, blend);
        pose.pushPose();
        if (Math.abs(knobAngle) > 0.001D) {
            rotateAround(pose,
                    CityPylonModels.PIVOT_SELECTOR_KNOB_X,
                    CityPylonModels.PIVOT_SELECTOR_KNOB_Y,
                    CityPylonModels.PIVOT_SELECTOR_KNOB_Z,
                    Axis.ZP,
                    knobAngle);
        }
        render(be, state, pos, pose, solid, light, CityPylonModels.selectorKnob());
        pose.popPose();

        /*
         * =========================================
         * ЛАМПЫ
         * =========================================
         *
         * Зелёная: активный пилон, горит постоянно.
         * Amber:   редкая косметическая вспышка активности, плюс
         *          постоянный свет при открытом GUI.
         * Красная: реального warning-состояния в проекте нет, поэтому
         *          лампа остаётся выключенной. Новые gameplay-состояния
         *          ради ламп не вводятся.
         */

        renderLamp(be, state, pos, pose, solid, cutout, light, fullBright,
                CityPylonModels.lampGreen(),
                CityPylonModels.lampGreenEmissive(),
                active);

        renderLamp(be, state, pos, pose, solid, cutout, light, fullBright,
                CityPylonModels.lampAmber(),
                CityPylonModels.lampAmberEmissive(),
                active && (amberBlink(time, pos) || blend > 0.5F));

        renderLamp(be, state, pos, pose, solid, cutout, light, fullBright,
                CityPylonModels.lampRed(),
                CityPylonModels.lampRedEmissive(),
                false);

        /*
         * =========================================
         * ЭКРАН
         * =========================================
         *
         * Базовая тёмная поверхность рисуется обычным материалом,
         * поверх — emissive с full-bright. Яркость слегка «дышит»
         * и усиливается при открытом GUI.
         */

        render(be, state, pos, pose, solid, light, CityPylonModels.screenSurface());
        if (active) {
            float breathe = 0.92F
                    + 0.08F * (float) Math.sin(time * 1.4D);
            render(be, state, pos, pose, cutout, fullBright,
                    CityPylonModels.screenSurfaceEmissive());
        }

        /*
         * Верхний индикатор повторяет состояние зелёной лампы.
         */
        renderLamp(be, state, pos, pose, solid, cutout, light, fullBright,
                CityPylonModels.topIndicator(),
                CityPylonModels.topIndicatorEmissive(),
                active);

        pose.popPose();
    }

    /*
     * =========================================
     * ВСПОМОГАТЕЛЬНОЕ
     * =========================================
     */

    private static float approach(float current, float target, float step) {
        return Mth.clamp(current + (target - current) * step, 0.0F, 1.0F);
    }

    /**
     * Открыт ли GUI именно этого пилона.
     * У Menu есть BlockPos, дополнительная синхронизация не нужна.
     */
    private static boolean isGuiOpenFor(BlockPos pos) {
        if (!(Minecraft.getInstance().screen instanceof CityPylonScreen screen)) {
            return false;
        }
        return screen.getMenu().getPylonPos().equals(pos);
    }

    /**
     * Редкая косметическая вспышка активности.
     * Смещение вычисляется из позиции, поэтому соседние пилоны
     * не мигают синхронно. Ни сети, ни SavedData не требуется.
     */
    private static boolean amberBlink(double time, BlockPos pos) {
        double offset = ((pos.getX() * 7L + pos.getZ() * 31L) % 977L) / 977.0D
                * AMBER_BLINK_PERIOD;
        double t = (time + offset) % AMBER_BLINK_PERIOD;
        return t < AMBER_BLINK_ON;
    }

    /**
     * Короткий поворот ручки селектора и возврат.
     * Пока GUI открыт, движение идёт с плавной привязкой к blend.
     */
    private static double selectorKnobAngle(double time, float blend) {
        if (blend <= 0.01F) {
            return 0.0D;
        }
        double t = time % SELECTOR_KNOB_CYCLE;
        double sweep = SELECTOR_KNOB_SWEEP_SECONDS / SELECTOR_KNOB_CYCLE;
        if (t >= sweep) {
            return 0.0D;
        }
        double k = t / sweep;
        return SELECTOR_KNOB_SWEEP * k * blend;
    }

    /**
     * Поворачивает анимированные части так же, как blockstate поворачивает корпус.
     *
     * <p>Корпус рисует чанковый мешер по blockstate, где поворот задан
     * полем {@code y}. Vanilla применяет его как
     * {@code BlockModelRotation}, то есть {@code rotateYXZ(-y, -x, 0)} —
     * знак МИНУС.
     *
     * <p>Раньше здесь ставился {@code rotationDegrees(+y)}. Статичный корпус
     * и анимированные детали поворачивались в противоположные стороны,
     * из-за чего верхний механизм визуально «уезжал» относительно корпуса,
     * а при facing != north разъезжались ещё и лампы с экраном.
     *
     * <p>Знак обязан быть отрицательным, иначе части снова разойдутся.
     * Угол поворота тот же, что и в blockstates/city_pylon.json.
     *
     * <p>Поворот вокруг центра блока (8/16, 0, 8/16), как указано
     * в integration_manifest.json.
     */
    private static void applyFacingRotation(PoseStack pose, BlockState state) {
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        float degrees = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        if (degrees == 0.0F) {
            return;
        }
        pose.translate(0.5D, 0.0D, 0.5D);
        // Минус: совпадает с BlockModelRotation.rotateYXZ(-y, -x, 0).
        pose.mulPose(Axis.YP.rotationDegrees(-degrees));
        pose.translate(-0.5D, 0.0D, -0.5D);
    }

    private static void rotateAround(
            PoseStack pose,
            double pivotX,
            double pivotY,
            double pivotZ,
            Axis axis,
            double degrees
    ) {
        pose.translate(pivotX, pivotY, pivotZ);
        pose.mulPose(axis.rotationDegrees((float) degrees));
        pose.translate(-pivotX, -pivotY, -pivotZ);
    }

    private void renderLamp(
            CityPylonBlockEntity be,
            BlockState state,
            BlockPos pos,
            PoseStack pose,
            VertexConsumer solid,
            VertexConsumer cutout,
            int light,
            int fullBright,
            BakedModel base,
            BakedModel emissive,
            boolean lit
    ) {
        render(be, state, pos, pose, solid, light, base);
        if (lit) {
            render(be, state, pos, pose, cutout, fullBright, emissive);
        }
    }

    private void render(
            CityPylonBlockEntity be,
            BlockState state,
            BlockPos pos,
            PoseStack pose,
            VertexConsumer consumer,
            int light,
            BakedModel model
    ) {
        if (model == null) {
            return;
        }
        modelRenderer.tesselateBlock(
                be.getLevel(),
                model,
                state,
                pos,
                pose,
                consumer,
                false,
                RANDOM,
                0L,
                OverlayTexture.NO_OVERLAY
        );
    }
}
