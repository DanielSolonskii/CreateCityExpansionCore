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
            return new Plan(Structure.MINE, tier, MineBlueprints.anchorOf(blocks), parts);
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
        if (level.isOutsideBuildHeight(min) || level.isOutsideBuildHeight(max)
                || !level.getWorldBorder().isWithinBounds(min) || !level.getWorldBorder().isWithinBounds(max)) {
            throw new IllegalArgumentException("Selection must be within the build height and world border.");
        }
        Map<BlockPos, BlockState> selected = new HashMap<>();
        BlockPos controller = null;
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) {
                var chunk = level.getChunkSource().getChunkNow(x, z);
                if (chunk == null) {
                    throw new IllegalArgumentException("Part of the selection is in an unloaded chunk.");
                }
                for (int y = min.getY() >> 4; y <= max.getY() >> 4; y++) {
                    var section = chunk.getSection(chunk.getSectionIndex(y << 4));
                    if (section.hasOnlyAir()) continue;
                    for (BlockPos pos : BlockPos.betweenClosed(
                            Math.max(min.getX(), x << 4), Math.max(min.getY(), y << 4), Math.max(min.getZ(), z << 4),
                            Math.min(max.getX(), (x << 4) + 15), Math.min(max.getY(), (y << 4) + 15), Math.min(max.getZ(), (z << 4) + 15))) {
                        BlockState state = section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
                        if (state.isAir()) continue;
                        BlockPos cell = pos.immutable();
                        if (state.is(CreateCityExpansionCore.MINE_CONTROLLER.get())) {
                            if (controller != null) {
                                throw new IllegalArgumentException("The selection must contain exactly one mine controller.");
                            }
                            controller = cell;
                        }
                        selected.put(cell, state);
                    }
                }
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
        for (var entry : selected.entrySet()) {
            BlockPos delta = entry.getKey().subtract(origin);
            BlockPos local = new BlockPos(delta.getX() * right.getStepX() + delta.getZ() * right.getStepZ(),
                    delta.getY(), delta.getX() * back.getStepX() + delta.getZ() * back.getStepZ());
            blocks.put(local, MineBlueprints.rotate(entry.getValue(), inverse));
        }
        // Preserve explicitly empty cells of existing blueprints when they are in the selection.
        for (var entry : MineBlueprints.current().blocks(Tier.I).entrySet()) {
            if (!entry.getValue().isAir() || blocks.containsKey(entry.getKey())) continue;
            BlockPos local = entry.getKey();
            BlockPos pos = MineMultiblock.getWorldPosition(origin, facing, local.getX(), local.getY(), local.getZ());
            if (pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ()) {
                blocks.put(local, entry.getValue());
            }
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
                    BlockState captured = additions.remove(entry.getKey());
                    if (captured == null || com.example.examplemod.machine.MachinePanels.withoutColor(entry.getValue())
                            != com.example.examplemod.machine.MachinePanels.withoutColor(captured)) {
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
            var validation = MineMultiblock.validate(level, plan.worldPosition(MineBlueprints.CONTROLLER, target, facing));
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
