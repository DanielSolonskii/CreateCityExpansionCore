package com.example.examplemod.multiblock;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.List;

public class MineMultiblock {

    /*
     * Размер структуры.
     */
    public static final int WIDTH = 10;
    public static final int HEIGHT = 3;
    public static final int DEPTH = 10;

    /*
     * Положение Controller внутри структуры.
     *
     * X = 4
     * Y = 1
     * Z = 0
     */
    private static final int CONTROLLER_X = 4;
    private static final int CONTROLLER_Y = 1;
    private static final int CONTROLLER_Z = 0;

    /*
     * =========================================================
     * ОСНОВНАЯ ПРОВЕРКА
     * =========================================================
     */

    public static boolean isAssembled(
            ServerLevel level,
            BlockPos controllerPos
    ) {

        ValidationResult result =
                validate(
                        level,
                        controllerPos
                );

        return result.assembled();
    }

    /*
     * =========================================================
     * ПОЛНАЯ ПРОВЕРКА
     * =========================================================
     */

    public static ValidationResult validate(
            ServerLevel level,
            BlockPos controllerPos
    ) {

        /*
         * 1. Controller действительно существует.
         */
        BlockState controllerState =
                level.getBlockState(
                        controllerPos
                );

        if (!controllerState.is(
                CreateCityExpansionCore.MINE_CONTROLLER.get()
        )) {

            return ValidationResult.failed(
                    "Контроллер отсутствует."
            );
        }

        /*
         * 2. Получаем направление машины.
         */
        Direction facing =
                controllerState.getValue(
                        BlockStateProperties.HORIZONTAL_FACING
                );

        /*
         * 3. Находим начало структуры.
         */
        BlockPos origin =
                getOrigin(
                        controllerPos,
                        facing
                );

        /*
         * 4. Проверяем всю структуру.
         */
        int correctBlocks = 0;
        int requiredBlocks = 0;

        List<BlockPos> incorrectBlocks =
                new ArrayList<>();

        for (int y = 0; y < HEIGHT; y++) {

            for (int x = 0; x < WIDTH; x++) {

                for (int z = 0; z < DEPTH; z++) {

                    /*
                     * Controller уже находится
                     * внутри структуры.
                     */
                    BlockPos pos =
                            getWorldPosition(
                                    origin,
                                    facing,
                                    x,
                                    y,
                                    z
                            );

                    /*
                     * Сначала специальные
                     * сервисные клетки Power Port.
                     *
                     * Они должны быть пустыми.
                     */
                    if (isServiceSpace(
                            level,
                            origin,
                            facing,
                            x,
                            y,
                            z
                    )) {

                        continue;
                    }

                    requiredBlocks++;

                    BlockState actual =
                            level.getBlockState(pos);

                    /*
                     * Определяем, какой блок
                     * здесь должен быть.
                     */
                    ExpectedBlock expected =
                            getExpectedBlock(
                                    x,
                                    y,
                                    z
                            );

                    /*
                     * Специальные интерфейсы
                     * проверяем отдельно.
                     */
                    if (expected == ExpectedBlock.INTERFACE) {

                        /*
                         * Любой интерфейс здесь пока
                         * не считается автоматически.
                         *
                         * Проверка Power Port/Gateway
                         * будет ниже.
                         */
                        continue;
                    }

                    if (isCorrectBlock(
                            actual,
                            expected
                    )) {

                        correctBlocks++;

                    } else {

                        incorrectBlocks.add(pos);
                    }
                }
            }
        }

        /*
         * =====================================================
         * POWER PORT
         * =====================================================
         */

        List<BlockPos> powerPorts =
                findAllPowerPorts(
                        level,
                        origin,
                        facing
                );

        if (powerPorts.size() != 1) {

            return ValidationResult.failed(
                    "Необходим ровно один Power Port.",
                    correctBlocks,
                    requiredBlocks,
                    incorrectBlocks
            );
        }

        BlockPos powerPort =
                powerPorts.get(0);

        /*
         * Проверяем сервисную зону.
         */
        if (!isPowerPortServiceSpaceFree(
                level,
                origin,
                facing,
                powerPort
        )) {

            return ValidationResult.failed(
                    "Рядом с Power Port должно быть свободное пространство.",
                    correctBlocks,
                    requiredBlocks,
                    incorrectBlocks
            );
        }

        /*
         * =====================================================
         * GATEWAY
         * =====================================================
         */

        List<BlockPos> gateways =
                findAllGateways(
                        level,
                        origin,
                        facing
                );

        if (gateways.size() != 1) {

            return ValidationResult.failed(
                    "Необходим ровно один Gateway.",
                    correctBlocks,
                    requiredBlocks,
                    incorrectBlocks
            );
        }

        /*
         * =====================================================
         * ПРОВЕРКА КОНТРОЛЛЕРА
         * =====================================================
         */

        BlockPos expectedController =
                getWorldPosition(
                        origin,
                        facing,
                        CONTROLLER_X,
                        CONTROLLER_Y,
                        CONTROLLER_Z
                );

        if (!expectedController.equals(
                controllerPos
        )) {

            return ValidationResult.failed(
                    "Контроллер находится не в правильной позиции.",
                    correctBlocks,
                    requiredBlocks,
                    incorrectBlocks
            );
        }

        /*
         * =====================================================
         * ФИНАЛ
         * =====================================================
         */

        if (!incorrectBlocks.isEmpty()) {

            return ValidationResult.failed(
                    "В конструкции есть неправильные блоки.",
                    correctBlocks,
                    requiredBlocks,
                    incorrectBlocks
            );
        }

        return ValidationResult.success(
                correctBlocks,
                requiredBlocks,
                powerPort,
                gateways.get(0)
        );
    }

    /*
     * =========================================================
     * EXPECTED BLOCK
     * =========================================================
     */

    private static ExpectedBlock getExpectedBlock(
            int x,
            int y,
            int z
    ) {

        /*
         * ========================================
         * Y = 0
         * ========================================
         */

        if (y == 0) {

            /*
             * Центральные 4×4 —
             * Drill Unit.
             */
            if (x >= 3 && x <= 6
                    && z >= 3 && z <= 6) {

                return ExpectedBlock.DRILL_UNIT;
            }

            return ExpectedBlock.FRAME;
        }

        /*
         * ========================================
         * Y = 1
         * ========================================
         */

        if (y == 1) {

            /*
             * Controller.
             */
            if (x == CONTROLLER_X
                    && z == CONTROLLER_Z) {

                return ExpectedBlock.CONTROLLER;
            }

            /*
             * Внутренние 8×8 —
             * Technical Block.
             */
            if (x >= 1 && x <= 8
                    && z >= 1 && z <= 8) {

                return ExpectedBlock.TECHNICAL;
            }

            /*
             * Внешний контур.
             *
             * Здесь:
             * Frame
             * Power Port
             * Gateway
             */
            return ExpectedBlock.INTERFACE;
        }

        /*
         * ========================================
         * Y = 2
         * ========================================
         */

        if (y == 2) {

            if (x >= 1 && x <= 8
                    && z >= 1 && z <= 8) {

                return ExpectedBlock.TECHNICAL;
            }

            return ExpectedBlock.FRAME;
        }

        return ExpectedBlock.NONE;
    }

    /*
     * =========================================================
     * BLOCK CHECK
     * =========================================================
     */

    private static boolean isCorrectBlock(
            BlockState state,
            ExpectedBlock expected
    ) {

        return switch (expected) {

            case FRAME ->

                    state.is(
                            CreateCityExpansionCore
                                    .MINE_FRAME
                                    .get()
                    );

            case DRILL_UNIT ->

                    state.is(
                            CreateCityExpansionCore
                                    .DRILL_UNIT
                                    .get()
                    );

            case TECHNICAL ->

                    state.is(
                            CreateCityExpansionCore
                                    .TECHNICAL_BLOCK
                                    .get()
                    );

            case CONTROLLER ->

                    state.is(
                            CreateCityExpansionCore
                                    .MINE_CONTROLLER
                                    .get()
                    );

            case INTERFACE, NONE ->
                    false;
        };
    }

    /*
     * =========================================================
     * POWER PORT
     * =========================================================
     */

    private static List<BlockPos> findAllPowerPorts(
            ServerLevel level,
            BlockPos origin,
            Direction facing
    ) {

        List<BlockPos> result =
                new ArrayList<>();

        for (int x = 0; x < WIDTH; x++) {

            for (int z = 0; z < DEPTH; z++) {

                if (!isOuterPosition(
                        x,
                        z
                )) {

                    continue;
                }

                /*
                 * Controller position.
                 */
                if (x == CONTROLLER_X
                        && z == CONTROLLER_Z) {

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

                if (level.getBlockState(pos)
                        .is(
                                CreateCityExpansionCore
                                        .MINE_POWER_PORT
                                        .get()
                        )) {

                    result.add(pos);
                }
            }
        }

        return result;
    }

    /*
     * =========================================================
     * GATEWAY
     * =========================================================
     */

    private static List<BlockPos> findAllGateways(
            ServerLevel level,
            BlockPos origin,
            Direction facing
    ) {

        List<BlockPos> result =
                new ArrayList<>();

        for (int x = 0; x < WIDTH; x++) {

            for (int z = 0; z < DEPTH; z++) {

                if (!isOuterPosition(
                        x,
                        z
                )) {

                    continue;
                }

                /*
                 * Controller.
                 */
                if (x == CONTROLLER_X
                        && z == CONTROLLER_Z) {

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

                if (level.getBlockState(pos)
                        .is(
                                CreateCityExpansionCore
                                        .MINE_GATEWAY
                                        .get()
                        )) {

                    result.add(pos);
                }
            }
        }

        return result;
    }

    /*
     * =========================================================
     * OUTER POSITION
     * =========================================================
     */

    private static boolean isOuterPosition(
            int x,
            int z
    ) {

        return x == 0
                || x == WIDTH - 1
                || z == 0
                || z == DEPTH - 1;
    }

    /*
     * =========================================================
     * POWER PORT SERVICE SPACE
     * =========================================================
     */

    private static boolean isServiceSpace(
            ServerLevel level,
            BlockPos origin,
            Direction facing,
            int x,
            int y,
            int z
    ) {

        /*
         * Только средний слой.
         */
        if (y != 1) {
            return false;
        }

        /*
         * Ищем Power Port.
         */
        List<BlockPos> powerPorts =
                findAllPowerPorts(
                        level,
                        origin,
                        facing
                );

        /*
         * Если Power Port ещё не поставлен,
         * сервисных клеток нет.
         */
        if (powerPorts.size() != 1) {
            return false;
        }

        BlockPos powerPort =
                powerPorts.get(0);

        /*
         * Переводим Power Port
         * в локальные координаты.
         */
        int portX = -1;
        int portZ = -1;

        for (int px = 0; px < WIDTH; px++) {

            for (int pz = 0; pz < DEPTH; pz++) {

                BlockPos test =
                        getWorldPosition(
                                origin,
                                facing,
                                px,
                                1,
                                pz
                        );

                if (test.equals(powerPort)) {

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
         * Передняя / задняя стенка.
         */
        if (portZ == 0
                || portZ == DEPTH - 1) {

            return z == portZ
                    && Math.abs(x - portX) == 1;
        }

        /*
         * Левая / правая стенка.
         */
        if (portX == 0
                || portX == WIDTH - 1) {

            return x == portX
                    && Math.abs(z - portZ) == 1;
        }

        return false;
    }

    /*
     * =========================================================
     * ПРОВЕРКА СЕРВИСНОЙ ЗОНЫ
     * =========================================================
     */

    private static boolean isPowerPortServiceSpaceFree(
            ServerLevel level,
            BlockPos origin,
            Direction facing,
            BlockPos powerPort
    ) {

        int portX = -1;
        int portZ = -1;

        /*
         * Ищем локальные координаты.
         */
        for (int x = 0; x < WIDTH; x++) {

            for (int z = 0; z < DEPTH; z++) {

                BlockPos test =
                        getWorldPosition(
                                origin,
                                facing,
                                x,
                                1,
                                z
                        );

                if (test.equals(powerPort)) {

                    portX = x;
                    portZ = z;
                }
            }
        }

        if (portX == -1
                || portZ == -1) {

            return false;
        }

        /*
         * Передняя / задняя стена.
         */
        if (portZ == 0
                || portZ == DEPTH - 1) {

            for (int offset : new int[]{-1, 1}) {

                int x =
                        portX + offset;

                if (x < 0
                        || x >= WIDTH) {

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

                if (!level.getBlockState(
                        service
                ).isAir()) {

                    return false;
                }
            }

            return true;
        }

        /*
         * Боковая стена.
         */
        if (portX == 0
                || portX == WIDTH - 1) {

            for (int offset : new int[]{-1, 1}) {

                int z =
                        portZ + offset;

                if (z < 0
                        || z >= DEPTH) {

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

                if (!level.getBlockState(
                        service
                ).isAir()) {

                    return false;
                }
            }

            return true;
        }

        return false;
    }

    /*
     * =========================================================
     * ORIGIN
     * =========================================================
     */

    public static BlockPos getOrigin(
            BlockPos controllerPos,
            Direction facing
    ) {

        Direction right =
                facing.getClockWise();

        return controllerPos
                .below(CONTROLLER_Y)
                .relative(
                        right.getOpposite(),
                        CONTROLLER_X
                );
    }

    /*
     * =========================================================
     * WORLD POSITION
     * =========================================================
     */

    public static BlockPos getWorldPosition(
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

    /*
     * =========================================================
     * PROGRESS
     * =========================================================
     */

    public static float getAssemblyProgress(
            ServerLevel level,
            BlockPos controllerPos
    ) {

        BlockState controller =
                level.getBlockState(
                        controllerPos
                );

        if (!controller.is(
                CreateCityExpansionCore
                        .MINE_CONTROLLER
                        .get()
        )) {

            return 0.0F;
        }

        Direction facing =
                controller.getValue(
                        BlockStateProperties.HORIZONTAL_FACING
                );

        BlockPos origin =
                getOrigin(
                        controllerPos,
                        facing
                );

        int correct = 0;
        int total = 0;

        for (int y = 0; y < HEIGHT; y++) {

            for (int x = 0; x < WIDTH; x++) {

                for (int z = 0; z < DEPTH; z++) {

                    if (isServiceSpace(
                            level,
                            origin,
                            facing,
                            x,
                            y,
                            z
                    )) {

                        continue;
                    }

                    ExpectedBlock expected =
                            getExpectedBlock(
                                    x,
                                    y,
                                    z
                            );

                    if (expected
                            == ExpectedBlock.INTERFACE
                            || expected
                            == ExpectedBlock.NONE) {

                        continue;
                    }

                    total++;

                    BlockPos pos =
                            getWorldPosition(
                                    origin,
                                    facing,
                                    x,
                                    y,
                                    z
                            );

                    if (isCorrectBlock(
                            level.getBlockState(pos),
                            expected
                    )) {

                        correct++;
                    }
                }
            }
        }

        /*
         * Интерфейсы учитываем отдельно.
         */
        if (findAllPowerPorts(
                level,
                origin,
                facing
        ).size() == 1) {

            correct++;
        }

        if (findAllGateways(
                level,
                origin,
                facing
        ).size() == 1) {

            correct++;
        }

        total += 2;

        return total == 0
                ? 0.0F
                : (float) correct / total;
    }

    /*
     * =========================================================
     * VALIDATION RESULT
     * =========================================================
     */

    public record ValidationResult(
            boolean assembled,
            String message,
            int correctBlocks,
            int requiredBlocks,
            BlockPos powerPort,
            BlockPos gateway,
            List<BlockPos> incorrectBlocks
    ) {

        public static ValidationResult success(
                int correctBlocks,
                int requiredBlocks,
                BlockPos powerPort,
                BlockPos gateway
        ) {

            return new ValidationResult(
                    true,
                    "Мультиблок собран.",
                    correctBlocks,
                    requiredBlocks,
                    powerPort,
                    gateway,
                    List.of()
            );
        }

        public static ValidationResult failed(
                String message
        ) {

            return new ValidationResult(
                    false,
                    message,
                    0,
                    0,
                    null,
                    null,
                    List.of()
            );
        }

        public static ValidationResult failed(
                String message,
                int correctBlocks,
                int requiredBlocks,
                List<BlockPos> incorrectBlocks
        ) {

            return new ValidationResult(
                    false,
                    message,
                    correctBlocks,
                    requiredBlocks,
                    null,
                    null,
                    List.copyOf(
                            incorrectBlocks
                    )
            );
        }

        public float progress() {

            if (requiredBlocks <= 0) {
                return 0.0F;
            }

            return (float) correctBlocks
                    / requiredBlocks;
        }
    }

    /*
     * =========================================================
     * EXPECTED BLOCK ENUM
     * =========================================================
     */

    private enum ExpectedBlock {

        FRAME,

        DRILL_UNIT,

        TECHNICAL,

        CONTROLLER,

        INTERFACE,

        NONE
    }
}