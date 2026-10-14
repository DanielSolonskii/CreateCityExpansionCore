package com.example.examplemod.coke;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Canonical 11x6x17 battery pattern shared by validation and the future CRE-55 guide. */
public final class CokeBatteryMultiblock {
    public static final int WIDTH = 11;
    public static final int HEIGHT = 6;
    public static final int DEPTH = 17;
    public static final int TOTAL_CELLS = WIDTH * HEIGHT * DEPTH;

    public static final LocalPos CONTROLLER = new LocalPos(5, 2, 0);
    public static final LocalPos INPUT_PORT = new LocalPos(3, 1, 0);
    public static final LocalPos OUTPUT_PORT = new LocalPos(7, 1, 0);
    public static final LocalPos POWER_PORT = new LocalPos(5, 1, 16);

    static {
        Map<ExpectedBlock, Integer> counts = blockCounts();
        int cells = counts.values().stream().mapToInt(Integer::intValue).sum();
        int placed = cells - counts.getOrDefault(ExpectedBlock.AIR, 0);
        if (cells != TOTAL_CELLS || placed != 843) {
            throw new IllegalStateException("Invalid Coke Battery canonical layout: " + counts);
        }
    }

    private CokeBatteryMultiblock() {
    }

    public static ExpectedBlock expectedAt(int x, int y, int z) {
        if (!inside(x, y, z)) {
            return ExpectedBlock.AIR;
        }
        LocalPos local = new LocalPos(x, y, z);
        if (local.equals(CONTROLLER)) return ExpectedBlock.CONTROLLER;
        if (local.equals(INPUT_PORT)) return ExpectedBlock.INPUT_PORT;
        if (local.equals(OUTPUT_PORT)) return ExpectedBlock.OUTPUT_PORT;
        if (local.equals(POWER_PORT)) return ExpectedBlock.POWER_PORT;

        if (y == 0) return ExpectedBlock.REINFORCED_CONCRETE;

        if (y >= 1 && y <= 3) {
            if (z >= 2 && z <= 13) {
                if (x >= 4 && x <= 6) return ExpectedBlock.CHAMBER;
                if (x == 3 || x == 7) return ExpectedBlock.REFRACTORY;
                if (x == 0 || x == 10) return ExpectedBlock.FRAME;
                return ExpectedBlock.AIR;
            }
            // Solid front/rear service heads: structural skin with refractory core.
            if (x == 0 || x == 10 || z == 0 || z == 16) return ExpectedBlock.FRAME;
            return ExpectedBlock.REFRACTORY;
        }

        if (y == 4) {
            // Full refractory roof, with four transverse ribs marking the oven sections.
            if (z == 2 || z == 5 || z == 8 || z == 11) return ExpectedBlock.FRAME;
            return ExpectedBlock.REFRACTORY;
        }

        if (y == 5) {
            return x == 0 || x == 10 || z == 0 || z == 16
                    ? ExpectedBlock.FRAME : ExpectedBlock.AIR;
        }
        return ExpectedBlock.AIR;
    }

    public static ValidationResult validate(ServerLevel level, BlockPos controllerPos) {
        BlockState controllerState = level.getBlockState(controllerPos);
        if (!controllerState.is(CreateCityExpansionCore.COKE_BATTERY_CONTROLLER.get())) {
            return ValidationResult.failed("Controller missing", List.of(controllerPos));
        }
        Direction facing = controllerState.getValue(BlockStateProperties.HORIZONTAL_FACING);
        BlockPos origin = getOrigin(controllerPos, facing);
        List<Mismatch> mismatches = new ArrayList<>();
        int correct = 0;
        int required = 0;

        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < DEPTH; z++) {
                for (int x = 0; x < WIDTH; x++) {
                    ExpectedBlock expected = expectedAt(x, y, z);
                    BlockPos worldPos = getWorldPosition(origin, facing, x, y, z);
                    BlockState actual = level.getBlockState(worldPos);
                    if (expected != ExpectedBlock.AIR) required++;
                    if (matches(actual, expected)) {
                        if (expected != ExpectedBlock.AIR) correct++;
                    } else {
                        mismatches.add(new Mismatch(worldPos, new LocalPos(x, y, z), expected));
                    }
                }
            }
        }

        BlockPos input = getWorldPosition(origin, facing, INPUT_PORT);
        BlockPos output = getWorldPosition(origin, facing, OUTPUT_PORT);
        BlockPos power = getWorldPosition(origin, facing, POWER_PORT);
        return new ValidationResult(
                mismatches.isEmpty(), correct, required, List.copyOf(mismatches),
                controllerPos.immutable(), input, output, power,
                mismatches.isEmpty() ? "Complete" : "Incorrect or occupied cells: " + mismatches.size()
        );
    }

    public static Map<ExpectedBlock, Integer> blockCounts() {
        Map<ExpectedBlock, Integer> counts = new EnumMap<>(ExpectedBlock.class);
        for (int y = 0; y < HEIGHT; y++) {
            for (int z = 0; z < DEPTH; z++) {
                for (int x = 0; x < WIDTH; x++) {
                    counts.merge(expectedAt(x, y, z), 1, Integer::sum);
                }
            }
        }
        return Map.copyOf(counts);
    }

    public static BlockPos getOrigin(BlockPos controllerPos, Direction facing) {
        return controllerPos.below(CONTROLLER.y())
                .relative(facing.getClockWise().getOpposite(), CONTROLLER.x());
    }

    public static BlockPos getWorldPosition(BlockPos origin, Direction facing, LocalPos local) {
        return getWorldPosition(origin, facing, local.x(), local.y(), local.z());
    }

    public static BlockPos getWorldPosition(
            BlockPos origin, Direction facing, int x, int y, int z
    ) {
        return origin.above(y)
                .relative(facing.getClockWise(), x)
                .relative(facing.getOpposite(), z);
    }

    public static boolean matches(BlockState state, ExpectedBlock expected) {
        return expected == ExpectedBlock.AIR ? state.isAir() : state.is(requiredBlock(expected));
    }

    public static Block requiredBlock(ExpectedBlock expected) {
        return switch (expected) {
            case AIR -> throw new IllegalArgumentException("AIR has no required block");
            case REINFORCED_CONCRETE -> CreateCityExpansionCore.REINFORCED_CONCRETE.get();
            case FRAME -> CreateCityExpansionCore.MINE_FRAME.get();
            case REFRACTORY -> CreateCityExpansionCore.REFRACTORY_BLOCK.get();
            case CHAMBER -> CreateCityExpansionCore.COKE_OVEN_CHAMBER.get();
            case CONTROLLER -> CreateCityExpansionCore.COKE_BATTERY_CONTROLLER.get();
            case INPUT_PORT -> CreateCityExpansionCore.COKE_BATTERY_INPUT_PORT.get();
            case OUTPUT_PORT -> CreateCityExpansionCore.COKE_BATTERY_OUTPUT_PORT.get();
            case POWER_PORT -> CreateCityExpansionCore.MINE_POWER_PORT.get();
        };
    }

    private static boolean inside(int x, int y, int z) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT && z >= 0 && z < DEPTH;
    }

    public enum ExpectedBlock {
        AIR,
        REINFORCED_CONCRETE,
        FRAME,
        REFRACTORY,
        CHAMBER,
        CONTROLLER,
        INPUT_PORT,
        OUTPUT_PORT,
        POWER_PORT
    }

    public record LocalPos(int x, int y, int z) {
    }

    public record Mismatch(BlockPos worldPos, LocalPos localPos, ExpectedBlock expected) {
    }

    public record ValidationResult(
            boolean assembled,
            int correctBlocks,
            int requiredBlocks,
            List<Mismatch> mismatches,
            BlockPos controller,
            BlockPos inputPort,
            BlockPos outputPort,
            BlockPos powerPort,
            String message
    ) {
        private static ValidationResult failed(String message, List<BlockPos> positions) {
            List<Mismatch> failures = positions.stream()
                    .map(pos -> new Mismatch(pos, CONTROLLER, ExpectedBlock.CONTROLLER)).toList();
            return new ValidationResult(false, 0, 843, failures, null, null, null, null, message);
        }
    }
}
