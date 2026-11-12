package com.example.examplemod.devtools;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.multiblock.BlueprintCatalog.Part;
import com.example.examplemod.multiblock.BlueprintCatalog.Plan;
import com.example.examplemod.multiblock.BlueprintCatalog.Structure;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.multiblock.MineBlueprints;
import com.example.examplemod.multiblock.MineMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BlueprintEditorWorld {
    public static final int REACH = 32;
    private static final BlockPos CONTROLLER = new BlockPos(4, 1, 0);
    private static final int BLOCK_UPDATE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private BlueprintEditorWorld() {}

    public record Capture(BlockPos min, BlockPos max, Map<BlockPos, BlockState> blocks) {
        public Capture {
            blocks = Map.copyOf(blocks);
        }

        public Plan preview(Tier tier) {
            List<Part> parts = blocks.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey()).map(entry -> new Part(entry.getKey(), entry.getValue(), Tier.I))
                    .toList();
            return new Plan(Structure.MINE, tier, MineBlueprints.ANCHOR, parts);
        }
    }

    public static BlockPos aim(ServerPlayer player, boolean adjacent) {
        if (player.pick(REACH, 1.0F, false) instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            return adjacent ? hit.getBlockPos().relative(hit.getDirection()) : hit.getBlockPos();
        }
        throw new IllegalArgumentException("Aim at a block within 32 blocks.");
    }

    public static Capture capture(ServerLevel level, BlockPos first, BlockPos second) {
        BlockPos min = new BlockPos(Math.min(first.getX(), second.getX()), Math.min(first.getY(), second.getY()),
                Math.min(first.getZ(), second.getZ()));
        BlockPos max = new BlockPos(Math.max(first.getX(), second.getX()), Math.max(first.getY(), second.getY()),
                Math.max(first.getZ(), second.getZ()));
        if (max.getX() - min.getX() >= 24 || max.getY() - min.getY() >= 24 || max.getZ() - min.getZ() >= 24
                || level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max)) {
            throw new IllegalArgumentException("Select up to 24 blocks on each axis within the build height.");
        }
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                if (!level.hasChunkAt(new BlockPos(x << 4, min.getY(), z << 4))) {
                    throw new IllegalArgumentException("Part of the selection is in an unloaded chunk.");
                }
            }
        }
        BlockPos controller = null;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockState(pos).is(CreateCityExpansionCore.MINE_CONTROLLER.get())) {
                if (controller != null) {
                    throw new IllegalArgumentException("The selection must contain exactly one mine controller.");
                }
                controller = pos.immutable();
            }
        }
        if (controller == null) {
            throw new IllegalArgumentException("The selection must contain a mine controller.");
        }
        Direction facing = level.getBlockState(controller).getValue(BlockStateProperties.HORIZONTAL_FACING);
        Direction right = facing.getClockWise();
        Direction back = facing.getOpposite();
        BlockPos origin = MineMultiblock.getOrigin(controller, facing);
        Rotation inverse = switch (facing) {
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            case WEST -> Rotation.CLOCKWISE_90;
            default -> MineBlueprints.rotation(facing);
        };
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            BlockPos delta = pos.subtract(origin);
            BlockPos local = new BlockPos(delta.getX() * right.getStepX() + delta.getZ() * right.getStepZ(),
                    delta.getY(), delta.getX() * back.getStepX() + delta.getZ() * back.getStepZ());
            if (!MineBlueprints.inside(local)) {
                if (!state.isAir()) {
                    throw new IllegalArgumentException("Block outside the mine footprint: " + pos.toShortString());
                }
            } else if (local.getY() < MineMultiblock.HEIGHT || !state.isAir()) {
                blocks.put(local, MineBlueprints.rotate(state, inverse));
            }
        }
        long baseCells = blocks.keySet().stream().filter(pos -> pos.getY() < MineMultiblock.HEIGHT).count();
        if (baseCells != MineMultiblock.WIDTH * MineMultiblock.HEIGHT * MineMultiblock.DEPTH) {
            throw new IllegalArgumentException("Select the entire 10 × 3 × 10 mine base and its upgrades.");
        }
        return new Capture(min, max, blocks);
    }

    public static MineBlueprints.Layout merge(MineBlueprints.Layout layout, Tier tier, Capture capture) {
        Map<BlockPos, BlockState> additions = new HashMap<>(capture.blocks());
        if (tier != Tier.I) {
            for (Tier lower : Tier.values()) {
                if (lower == tier) {
                    break;
                }
                for (var entry : layout.blocks(lower).entrySet()) {
                    if (!entry.getValue().equals(additions.remove(entry.getKey()))) {
                        throw new IllegalArgumentException("Lower tier " + lower + " differs at "
                                + entry.getKey().toShortString());
                    }
                }
            }
        }
        return layout.replacing(tier, additions);
    }

    public static boolean canPlace(BlockState requested, BlockState actual) {
        return requested.isAir() ? actual.isAir()
                : actual.canBeReplaced() && actual.getFluidState().isEmpty() && !actual.hasBlockEntity();
    }

    private record Change(BlockPos pos, BlockState previous, BlockState next) {}

    public static void place(ServerLevel level, Plan plan, BlockPos target, Direction facing) {
        Rotation rotation = MineBlueprints.rotation(facing);
        List<Change> changes = new ArrayList<>();
        for (Part part : plan.parts()) {
            BlockPos pos = plan.worldPosition(part.position(), target, facing);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos)) {
                throw new IllegalArgumentException("Cannot place outside the loaded build area: " + pos.toShortString());
            }
            BlockState old = level.getBlockState(pos);
            if (!canPlace(part.state(), old)) {
                throw new IllegalArgumentException("Placement blocked at " + pos.toShortString());
            }
            if (!part.state().isAir()) {
                changes.add(new Change(pos, old, MineBlueprints.rotate(part.state(), rotation)));
            }
        }
        int written = 0;
        try {
            for (Change change : changes) {
                written++;
                if (!change.previous().equals(change.next()) && !level.setBlock(change.pos(), change.next(), BLOCK_UPDATE_FLAGS)) {
                    throw new IllegalStateException("Could not set " + change.pos().toShortString());
                }
            }
            var validation = MineMultiblock.validate(level, plan.worldPosition(CONTROLLER, target, facing));
            if (!validation.assembled()) {
                throw new IllegalStateException(validation.message());
            }
        } catch (RuntimeException exception) {
            for (int index = written - 1; index >= 0; index--) {
                Change change = changes.get(index);
                try {
                    level.setBlock(change.pos(), change.previous(), BLOCK_UPDATE_FLAGS);
                } catch (RuntimeException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
            }
            throw exception;
        }
        for (Change change : changes) {
            level.blockUpdated(change.pos(), change.next().getBlock());
        }
    }
}
