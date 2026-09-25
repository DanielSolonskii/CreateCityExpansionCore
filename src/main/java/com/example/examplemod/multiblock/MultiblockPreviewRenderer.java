package com.example.examplemod.multiblock;

import com.example.examplemod.CreateCityExpansionCore;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

public class MultiblockPreviewRenderer {

    private static BlockPos controllerPos = null;
    private static Direction facing = Direction.NORTH;

    /**
     * Включить отображение схемы.
     */
    public static void show(
            BlockPos pos,
            Direction direction
    ) {
        controllerPos = pos;
        facing = direction;
    }

    /**
     * Выключить отображение схемы.
     */
    public static void hide() {
        controllerPos = null;
    }

    /**
     * Проверка, отображается ли сейчас схема.
     */
    public static boolean isShowing() {
        return controllerPos != null;
    }

    /**
     * Главный рендер схемы.
     */
    @SubscribeEvent
    public static void onRenderLevel(
            RenderLevelStageEvent event
    ) {

        /*
         * Рисуем после прозрачных блоков.
         */
        if (event.getStage()
                != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {

            return;
        }

        /*
         * Схема выключена.
         */
        if (controllerPos == null) {
            return;
        }

        Minecraft minecraft =
                Minecraft.getInstance();

        /*
         * Мир отсутствует.
         */
        if (minecraft.level == null) {
            return;
        }

        /*
         * Получаем состояние контроллера.
         */
        BlockState controllerState =
                minecraft.level.getBlockState(
                        controllerPos
                );

        /*
         * Если контроллер сломали —
         * автоматически выключаем схему.
         */
        if (controllerState.getBlock()
                != CreateCityExpansionCore.MINE_CONTROLLER.get()) {

            hide();
            return;
        }

        /*
         * Всегда берём актуальный поворот
         * непосредственно из BlockState.
         */
        facing =
                controllerState.getValue(
                        BlockStateProperties.HORIZONTAL_FACING
                );

        /*
         * ========================================
         * НАЧАЛО МУЛЬТИБЛОКА
         * ========================================
         *
         * Контроллер находится:
         *
         * X = 4
         * Y = 1
         * Z = 0
         *
         * Поэтому начало структуры:
         *
         * controller
         *      ↓
         *      4 блока влево
         *      1 блок вниз
         */
        Direction right =
                facing.getClockWise();

        BlockPos origin =
                controllerPos
                        .below(1)
                        .relative(
                                right.getOpposite(),
                                4
                        );

        BlockPos powerPortPos = findPowerPort(minecraft, origin);

        /*
         * ========================================
         * РЕНДЕР
         * ========================================
         */

        PoseStack poseStack =
                event.getPoseStack();

        VertexConsumer buffer =
                minecraft
                        .renderBuffers()
                        .bufferSource()
                        .getBuffer(
                                RenderType.lines()
                        );

        /*
         * Позиция камеры.
         */
        var camera =
                minecraft
                        .gameRenderer
                        .getMainCamera();

        var cameraPos =
                camera.getPosition();

        poseStack.pushPose();

        /*
         * Minecraft рендерит мир
         * относительно камеры.
         */
        poseStack.translate(
                -cameraPos.x,
                -cameraPos.y,
                -cameraPos.z
        );

        /*
         * ========================================
         * 10 × 10 × 3
         * ========================================
         */

        for (int y = 0; y < 3; y++) {

            for (int x = 0; x < 10; x++) {

                for (int z = 0; z < 10; z++) {

                    /*
                     * Определяем, какой блок здесь
                     * должен находиться.
                     */
                    MinePreviewType expected =
                            getExpectedType(
                                    x,
                                    y,
                                    z
                            );

                    /*
                     * Пустая ячейка.
                     */
                    if (expected
                            == MinePreviewType.NONE) {

                        continue;
                    }

                    /*
                     * Мировая позиция.
                     */
                    BlockPos pos =
                            getWorldPosition(
                                    origin,
                                    facing,
                                    x,
                                    y,
                                    z
                            );

                    if (isPowerPortServiceSpace(
                            x, y, z, powerPortPos, origin
                    )) {
                        continue;
                    }

                    /*
                     * Определяем цвет:
                     *
                     * отсутствует → цвет схемы
                     * правильный → зелёный
                     * неправильный → красный
                     * интерфейс → специальный цвет
                     */
                    float[] color =
                            getStatusColor(
                                    minecraft,
                                    pos,
                                    expected
                            );

                    /*
                     * Рисуем контур блока.
                     */
                    LevelRenderer.renderLineBox(
                            poseStack,
                            buffer,

                            pos.getX(),
                            pos.getY(),
                            pos.getZ(),

                            pos.getX() + 1.0,
                            pos.getY() + 1.0,
                            pos.getZ() + 1.0,

                            color[0],
                            color[1],
                            color[2],
                            color[3]
                    );
                }
            }
        }

        poseStack.popPose();
    }

    /**
     * ========================================
     * СХЕМА МУЛЬТИБЛОКА
     * ========================================
     *
     * Возвращает тип блока,
     * который должен находиться
     * в локальной координате.
     */
    private static MinePreviewType getExpectedType(
            int x,
            int y,
            int z
    ) {

        /*
         * ========================================
         * НИЖНИЙ СЛОЙ
         * Y = 0
         * ========================================
         *
         * Центральный квадрат 4×4:
         *
         * Drill Unit
         *
         * Остальное:
         *
         * Frame
         */

        if (y == 0) {

            if (x >= 3 && x <= 6
                    && z >= 3 && z <= 6) {

                return MinePreviewType.DRILL_UNIT;
            }

            return MinePreviewType.FRAME;
        }

        /*
         * ========================================
         * СРЕДНИЙ СЛОЙ
         * Y = 1
         * ========================================
         */

        if (y == 1) {

            /*
             * Контроллер находится
             * строго здесь.
             */
            if (x == 4 && z == 0) {

                return MinePreviewType.CONTROLLER;
            }

            /*
             * Внутренние 8×8:
             *
             * Technical Block
             */
            if (x >= 1 && x <= 8
                    && z >= 1 && z <= 8) {

                return MinePreviewType.TECHNICAL_BLOCK;
            }

            /*
             * Внешний контур.
             *
             * Здесь по умолчанию Frame,
             * но Frame может быть заменён
             * Power Port или Gateway.
             */
            return MinePreviewType.FRAME;
        }

        /*
         * ========================================
         * ВЕРХНИЙ СЛОЙ
         * Y = 2
         * ========================================
         *
         * Внешний контур:
         *
         * Frame
         *
         * Внутри:
         *
         * Technical Block
         */

        if (y == 2) {

            if (x >= 1 && x <= 8
                    && z >= 1 && z <= 8) {

                return MinePreviewType.TECHNICAL_BLOCK;
            }

            return MinePreviewType.FRAME;
        }

        return MinePreviewType.NONE;
    }

    /**
     * ========================================
     * ЦВЕТ СХЕМЫ
     * ========================================
     *
     * Используется, когда блока ещё нет.
     */
    private static float[] getPreviewColor(
            MinePreviewType type
    ) {

        return switch (type) {

            /*
             * Frame
             */
            case FRAME -> new float[]{
                    0.10F,
                    0.55F,
                    1.00F,
                    0.18F
            };

            /*
             * Drill Unit
             */
            case DRILL_UNIT -> new float[]{
                    1.00F,
                    0.45F,
                    0.08F,
                    0.20F
            };

            /*
             * Technical Block
             */
            case TECHNICAL_BLOCK -> new float[]{
                    0.75F,
                    0.25F,
                    1.00F,
                    0.15F
            };

            /*
             * Controller
             */
            case CONTROLLER -> new float[]{
                    0.15F,
                    1.00F,
                    0.35F,
                    0.30F
            };

            /*
             * Power Port
             */
            case POWER_PORT -> new float[]{
                    0.05F,
                    0.90F,
                    1.00F,
                    0.22F
            };

            /*
             * Gateway
             */
            case GATEWAY -> new float[]{
                    1.00F,
                    0.75F,
                    0.05F,
                    0.22F
            };

            case NONE -> new float[]{
                    0.0F,
                    0.0F,
                    0.0F,
                    0.0F
            };
        };
    }

    /**
     * ========================================
     * ФАКТИЧЕСКИЙ СТАТУС
     * ========================================
     */
    private static float[] getStatusColor(
            Minecraft minecraft,
            BlockPos pos,
            MinePreviewType expected
    ) {

        BlockState actual =
                minecraft.level.getBlockState(pos);

        /*
         * ========================================
         * POWER PORT
         * ========================================
         */

        if (actual.is(
                CreateCityExpansionCore.MINE_POWER_PORT.get()
        )) {

            /*
             * Если Power Port стоит
             * на допустимой позиции —
             * голубой.
             */
            if (isPowerPortPosition(
                    pos,
                    minecraft
            )) {

                return new float[]{
                        0.05F,
                        0.90F,
                        1.00F,
                        0.42F
                };
            }

            /*
             * Power Port стоит там,
             * где его быть не должно.
             */
            return new float[]{
                    1.00F,
                    0.08F,
                    0.08F,
                    0.50F
            };
        }

        /*
         * ========================================
         * GATEWAY
         * ========================================
         */

        if (actual.is(
                CreateCityExpansionCore.MINE_GATEWAY.get()
        )) {

            /*
             * Gateway на внешнем контуре.
             */
            if (isGatewayPosition(
                    pos,
                    minecraft
            )) {

                return new float[]{
                        1.00F,
                        0.75F,
                        0.05F,
                        0.42F
                };
            }

            /*
             * Gateway в неправильном месте.
             */
            return new float[]{
                    1.00F,
                    0.08F,
                    0.08F,
                    0.50F
            };
        }

        /*
         * ========================================
         * ПУСТАЯ ЯЧЕЙКА
         * ========================================
         */

        if (actual.isAir()) {

            return getPreviewColor(
                    expected
            );
        }

        /*
         * ========================================
         * ПРАВИЛЬНЫЙ БЛОК
         * ========================================
         */

        if (isCorrectBlock(
                actual,
                expected
        )) {

            return new float[]{
                    0.10F,
                    1.00F,
                    0.30F,
                    0.30F
            };
        }

        /*
         * ========================================
         * НЕПРАВИЛЬНЫЙ БЛОК
         * ========================================
         */

        return new float[]{
                1.00F,
                0.08F,
                0.08F,
                0.40F
        };
    }

    /**
     * ========================================
     * ПРОВЕРКА ПРАВИЛЬНОГО БЛОКА
     * ========================================
     */
    private static boolean isCorrectBlock(
            BlockState state,
            MinePreviewType expected
    ) {

        return switch (expected) {

            /*
             * Frame-позиция может содержать:
             *
             * Frame
             * Power Port
             * Gateway
             */
            case FRAME ->

                    state.is(
                            CreateCityExpansionCore
                                    .MINE_FRAME
                                    .get()
                    )

                            || state.is(
                            CreateCityExpansionCore
                                    .MINE_POWER_PORT
                                    .get()
                    )

                            || state.is(
                            CreateCityExpansionCore
                                    .MINE_GATEWAY
                                    .get()
                    );

            /*
             * Drill Unit
             */
            case DRILL_UNIT ->

                    state.is(
                            CreateCityExpansionCore
                                    .DRILL_UNIT
                                    .get()
                    );

            /*
             * Technical Block
             */
            case TECHNICAL_BLOCK ->

                    state.is(
                            CreateCityExpansionCore
                                    .TECHNICAL_BLOCK
                                    .get()
                    );

            /*
             * Controller
             */
            case CONTROLLER ->

                    state.is(
                            CreateCityExpansionCore
                                    .MINE_CONTROLLER
                                    .get()
                    );

            /*
             * Интерфейсы обрабатываются
             * отдельно в getStatusColor().
             */
            case POWER_PORT ->
                    state.is(
                            CreateCityExpansionCore
                                    .MINE_POWER_PORT
                                    .get()
                    );

            case GATEWAY ->
                    state.is(
                            CreateCityExpansionCore
                                    .MINE_GATEWAY
                                    .get()
                    );

            case NONE ->
                    true;
        };
    }

    /**
     * ========================================
     * ПОИСК POWER PORT
     * ========================================
     */
    private static BlockPos findPowerPort(
            Minecraft minecraft,
            BlockPos origin
    ) {

        if (minecraft.level == null) {
            return null;
        }

        /*
         * Проверяем внешний контур
         * среднего слоя.
         */
        for (int x = 0; x < 10; x++) {

            for (int z = 0; z < 10; z++) {

                if (!isOuterPosition(
                        x,
                        z
                )) {

                    continue;
                }

                /*
                 * Контроллер — не Power Port.
                 */
                if (x == 4 && z == 0) {
                    continue;
                }

                BlockPos pos =
                        getWorldPosition(
                                origin,
                                facing,
                                x,
                                1,
                                z
                        );

                if (minecraft.level
                        .getBlockState(pos)
                        .is(
                                CreateCityExpansionCore
                                        .MINE_POWER_PORT
                                        .get()
                        )) {

                    return pos;
                }
            }
        }

        return null;
    }

    /**
     * ========================================
     * ВНЕШНИЙ КОНТУР
     * ========================================
     */
    private static boolean isOuterPosition(
            int x,
            int z
    ) {

        return x == 0
                || x == 9
                || z == 0
                || z == 9;
    }

    /**
     * ========================================
     * POWER PORT SERVICE SPACE
     * ========================================
     *
     * Два блока по сторонам Power Port
     * должны оставаться пустыми.
     */
    private static boolean isPowerPortServiceSpace(
            int x,
            int y,
            int z,
            BlockPos powerPortPos,
            BlockPos origin
    ) {

        if (powerPortPos == null) {
            return false;
        }

        /*
         * Сервисная зона только
         * на среднем слое.
         */
        if (y != 1) {
            return false;
        }

        int portX = -1;
        int portZ = -1;

        /*
         * Находим локальные координаты
         * Power Port.
         */
        for (int px = 0; px < 10; px++) {

            for (int pz = 0; pz < 10; pz++) {

                BlockPos test =
                        getWorldPosition(
                                origin,
                                facing,
                                px,
                                1,
                                pz
                        );

                if (test.equals(powerPortPos)) {

                    portX = px;
                    portZ = pz;
                }
            }
        }

        if (portX == -1
                || portZ == -1) {

            return false;
        }

        /*
         * Power Port на передней
         * или задней стене.
         *
         * Сервисные клетки идут
         * слева и справа.
         */
        if (portZ == 0
                || portZ == 9) {

            return z == portZ
                    && Math.abs(x - portX) == 1;
        }

        /*
         * Power Port на боковой стене.
         *
         * Сервисные клетки идут
         * вдоль Z.
         */
        if (portX == 0
                || portX == 9) {

            return x == portX
                    && Math.abs(z - portZ) == 1;
        }

        return false;
    }

    /**
     * ========================================
     * POWER PORT POSITION
     * ========================================
     */
    private static boolean isPowerPortPosition(
            BlockPos pos,
            Minecraft minecraft
    ) {

        if (minecraft.level == null) {
            return false;
        }

        Direction right =
                facing.getClockWise();

        BlockPos origin =
                controllerPos
                        .below(1)
                        .relative(
                                right.getOpposite(),
                                4
                        );

        for (int x = 0; x < 10; x++) {

            for (int z = 0; z < 10; z++) {

                if (!isOuterPosition(
                        x,
                        z
                )) {

                    continue;
                }

                /*
                 * Контроллер занимает
                 * свою позицию.
                 */
                if (x == 4 && z == 0) {
                    continue;
                }

                BlockPos test =
                        getWorldPosition(
                                origin,
                                facing,
                                x,
                                1,
                                z
                        );

                if (test.equals(pos)) {

                    if (!serviceSpaceIsFree(minecraft, origin, x, z)) {
                        return false;
                    }

                    return true;
                }
            }
        }

        return false;
    }

    /**
     * ========================================
     * ПРОВЕРКА SERVICE SPACE
     * ========================================
     */
    private static boolean serviceSpaceIsFree(
            Minecraft minecraft,
            BlockPos origin,
            int portX,
            int portZ
    ) {

        /*
         * Передняя / задняя стенка.
         */
        if (portZ == 0
                || portZ == 9) {

            for (int offset : new int[]{-1, 1}) {

                int x =
                        portX + offset;

                /*
                 * Если порт стоит слишком
                 * близко к краю — позиция
                 * недействительна.
                 */
                if (x < 0 || x > 9) {
                    return false;
                }

                BlockPos service =
                        getWorldPosition(
                                origin,
                                facing,
                                x,
                                1,
                                portZ
                        );

                if (!minecraft.level
                        .getBlockState(service)
                        .isAir()) {

                    return false;
                }
            }

            return true;
        }

        /*
         * Боковая стенка.
         */
        if (portX == 0
                || portX == 9) {

            for (int offset : new int[]{-1, 1}) {

                int z =
                        portZ + offset;

                if (z < 0 || z > 9) {
                    return false;
                }

                BlockPos service =
                        getWorldPosition(
                                origin,
                                facing,
                                portX,
                                1,
                                z
                        );

                if (!minecraft.level
                        .getBlockState(service)
                        .isAir()) {

                    return false;
                }
            }

            return true;
        }

        return false;
    }

    /**
     * ========================================
     * GATEWAY POSITION
     * ========================================
     *
     * Gateway разрешён на внешнем
     * контуре среднего слоя.
     */
    private static boolean isGatewayPosition(
            BlockPos pos,
            Minecraft minecraft
    ) {

        if (minecraft.level == null) {
            return false;
        }

        Direction right =
                facing.getClockWise();

        BlockPos origin =
                controllerPos
                        .below(1)
                        .relative(
                                right.getOpposite(),
                                4
                        );

        for (int x = 0; x < 10; x++) {

            for (int z = 0; z < 10; z++) {

                if (!isOuterPosition(
                        x,
                        z
                )) {

                    continue;
                }

                /*
                 * Контроллер.
                 */
                if (x == 4 && z == 0) {
                    continue;
                }

                BlockPos powerPort = findPowerPort(minecraft, origin);
                if (isPowerPortServiceSpace(x, 1, z, powerPort, origin)) {
                    continue;
                }

                BlockPos test =
                        getWorldPosition(
                                origin,
                                facing,
                                x,
                                1,
                                z
                        );

                if (test.equals(pos)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * ========================================
     * ЛОКАЛЬНЫЕ → МИРОВЫЕ КООРДИНАТЫ
     * ========================================
     */
    private static BlockPos getWorldPosition(
            BlockPos origin,
            Direction facing,
            int x,
            int y,
            int z
    ) {

        Direction right =
                facing.getClockWise();

        Direction backward =
                facing.getOpposite();

        BlockPos result =
                origin.above(y);

        result =
                result.relative(
                        right,
                        x
                );

        result =
                result.relative(
                        backward,
                        z
                );

        return result;
    }
}
