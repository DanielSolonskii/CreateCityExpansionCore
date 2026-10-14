package com.example.examplemod.blast;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Canonical 11x13x11 pattern used by validation, guide and future Ponder scenes. */
public final class BlastFurnaceMultiblock {
    public static final int WIDTH = 11;
    public static final int HEIGHT = 13;
    public static final int DEPTH = 11;
    public static final int TOTAL_CELLS = WIDTH * HEIGHT * DEPTH;

    public static final LocalPos CONTROLLER = new LocalPos(5, 2, 0);
    public static final LocalPos ITEM_INPUT = new LocalPos(5, 10, 0);
    public static final LocalPos FLUID_OUTPUT = new LocalPos(7, 1, 0);
    public static final LocalPos POWER_PORT = new LocalPos(5, 1, 10);

    static {
        Map<ExpectedBlock, Integer> counts = blockCounts();
        int cells = counts.values().stream().mapToInt(Integer::intValue).sum();
        int placed = cells - counts.getOrDefault(ExpectedBlock.AIR, 0);
        if (cells != TOTAL_CELLS || placed != 915) {
            throw new IllegalStateException("Invalid Blast Furnace canonical layout: " + counts);
        }
    }

    private BlastFurnaceMultiblock() {
    }

    public static ExpectedBlock expectedAt(int x, int y, int z) {
        if (!inside(x, y, z)) return ExpectedBlock.AIR;
        LocalPos local = new LocalPos(x, y, z);
        if (local.equals(CONTROLLER)) return ExpectedBlock.CONTROLLER;
        if (local.equals(ITEM_INPUT)) return ExpectedBlock.ITEM_INPUT;
        if (local.equals(FLUID_OUTPUT)) return ExpectedBlock.FLUID_OUTPUT;
        if (local.equals(POWER_PORT)) return ExpectedBlock.POWER_PORT;

        if (y == 0) return ExpectedBlock.REINFORCED_CONCRETE;

        if (y == 1) {
            if (isPerimeter(x, z)) return ExpectedBlock.FRAME;
            if (insideSquare(x, z, 2, 8)) {
                return insideSquare(x, z, 4, 6) ? ExpectedBlock.CORE : ExpectedBlock.REFRACTORY;
            }
            return ExpectedBlock.AIR;
        }

        if (y >= 2 && y <= 9) {
            if (isPerimeter(x, z)) return ExpectedBlock.FRAME;
            if (insideSquare(x, z, 3, 7)) {
                return insideSquare(x, z, 4, 6) ? ExpectedBlock.CORE : ExpectedBlock.REFRACTORY;
            }
            return ExpectedBlock.AIR;
        }

        if (y == 10) {
            if (isPerimeter(x, z)) return ExpectedBlock.FRAME;
            if (insideSquare(x, z, 2, 8) && !insideSquare(x, z, 4, 6)) {
                return ExpectedBlock.REFRACTORY;
            }
            return ExpectedBlock.AIR;
        }

        if (y == 11) {
            if (isPerimeter(x, z)) return ExpectedBlock.FRAME;
            if (insideSquare(x, z, 3, 7) && !insideSquare(x, z, 4, 6)) {
                return ExpectedBlock.REFRACTORY;
            }
            return ExpectedBlock.AIR;
        }

        if (y == 12) {
            if (isPerimeter(x, z)) return ExpectedBlock.FRAME;
            return insideSquare(x, z, 4, 6) ? ExpectedBlock.REFRACTORY : ExpectedBlock.AIR;
        }
        return ExpectedBlock.AIR;
    }

    public static ValidationResult validate(ServerLevel level, BlockPos controllerPos) {
        BlockState controllerState = level.getBlockState(controllerPos);
        if (!controllerState.is(CreateCityExpansionCore.BLAST_FURNACE_CONTROLLER.get())) {
            return ValidationResult.failed(controllerPos);
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
                    BlockPos world = getWorldPosition(origin, facing, x, y, z);
                    BlockState actual = level.getBlockState(world);
                    if (expected != ExpectedBlock.AIR) required++;
                    if (matches(actual, expected)) {
                        if (expected != ExpectedBlock.AIR) correct++;
                    } else {
                        MismatchKind kind = expected == ExpectedBlock.AIR
                                ? MismatchKind.OCCUPIED_AIR
                                : actual.isAir() ? MismatchKind.MISSING : MismatchKind.WRONG;
                        mismatches.add(new Mismatch(world, new LocalPos(x, y, z), expected, kind));
                    }
                }
            }
        }
        return new ValidationResult(mismatches.isEmpty(), correct, required, List.copyOf(mismatches),
                controllerPos.immutable(), getWorldPosition(origin, facing, ITEM_INPUT),
                getWorldPosition(origin, facing, FLUID_OUTPUT), getWorldPosition(origin, facing, POWER_PORT),
                mismatches.isEmpty() ? "Complete" : "Incorrect or occupied cells: " + mismatches.size());
    }

    public static boolean matches(BlockState state, ExpectedBlock expected) {
        return expected == ExpectedBlock.AIR ? state.isAir() : state.is(requiredBlock(expected));
    }

    public static Block requiredBlock(ExpectedBlock expected) {
        return switch (expected) {
            case AIR -> throw new IllegalArgumentException("AIR has no required block");
            case REINFORCED_CONCRETE -> CreateCityExpansionCore.REINFORCED_CONCRETE.get();
            case REFRACTORY -> CreateCityExpansionCore.REFRACTORY_BLOCK.get();
            case CORE -> CreateCityExpansionCore.BLAST_FURNACE_CORE.get();
            case FRAME -> CreateCityExpansionCore.MINE_FRAME.get();
            case CONTROLLER -> CreateCityExpansionCore.BLAST_FURNACE_CONTROLLER.get();
            case ITEM_INPUT -> CreateCityExpansionCore.BLAST_FURNACE_INPUT_PORT.get();
            case FLUID_OUTPUT -> CreateCityExpansionCore.BLAST_FURNACE_OUTPUT_PORT.get();
            case POWER_PORT -> CreateCityExpansionCore.MINE_POWER_PORT.get();
        };
    }

    public static Map<ExpectedBlock, Integer> blockCounts() {
        Map<ExpectedBlock, Integer> counts = new EnumMap<>(ExpectedBlock.class);
        for (int y = 0; y < HEIGHT; y++) for (int z = 0; z < DEPTH; z++) for (int x = 0; x < WIDTH; x++)
            counts.merge(expectedAt(x, y, z), 1, Integer::sum);
        return Map.copyOf(counts);
    }

    public static BlockPos getOrigin(BlockPos controllerPos, Direction facing) {
        return controllerPos.below(CONTROLLER.y())
                .relative(facing.getClockWise().getOpposite(), CONTROLLER.x());
    }

    public static BlockPos getWorldPosition(BlockPos origin, Direction facing, LocalPos local) {
        return getWorldPosition(origin, facing, local.x(), local.y(), local.z());
    }

    public static BlockPos getWorldPosition(BlockPos origin, Direction facing, int x, int y, int z) {
        return origin.above(y).relative(facing.getClockWise(), x).relative(facing.getOpposite(), z);
    }

    private static boolean isPerimeter(int x, int z) { return x == 0 || x == 10 || z == 0 || z == 10; }
    private static boolean insideSquare(int x, int z, int min, int max) {
        return x >= min && x <= max && z >= min && z <= max;
    }
    private static boolean inside(int x, int y, int z) {
        return x >= 0 && x < WIDTH && y >= 0 && y < HEIGHT && z >= 0 && z < DEPTH;
    }

    public enum ExpectedBlock { AIR, REINFORCED_CONCRETE, REFRACTORY, CORE, FRAME,
        CONTROLLER, ITEM_INPUT, FLUID_OUTPUT, POWER_PORT }
    public enum MismatchKind { MISSING, WRONG, OCCUPIED_AIR }
    public record LocalPos(int x, int y, int z) {}
    public record Mismatch(BlockPos worldPos, LocalPos localPos, ExpectedBlock expected, MismatchKind kind) {}
    public record ValidationResult(boolean assembled, int correctBlocks, int requiredBlocks,
                                   List<Mismatch> mismatches, BlockPos controller, BlockPos itemInput,
                                   BlockPos fluidOutput, BlockPos powerPort, String message) {
        private static ValidationResult failed(BlockPos controller) {
            return new ValidationResult(false, 0, 915,
                    List.of(new Mismatch(controller, CONTROLLER, ExpectedBlock.CONTROLLER, MismatchKind.MISSING)),
                    controller, null, null, null, "Controller missing");
        }
    }
}
