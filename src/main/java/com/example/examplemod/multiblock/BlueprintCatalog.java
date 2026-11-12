package com.example.examplemod.multiblock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BlueprintCatalog {
    private BlueprintCatalog() {}

    public enum Structure {
        MINE("mine", Tier.V), VAULT("vault", Tier.III), TANK("tank", Tier.III);

        private final String key;
        private final Tier maxTier;

        Structure(String key, Tier maxTier) {
            this.key = key;
            this.maxTier = maxTier;
        }

        public String key() {
            return key;
        }

        public Tier maxTier() {
            return maxTier;
        }

        public Tier tierFromLevel(int level) {
            return level > 0 && level <= maxTier.level() ? Tier.values()[level - 1] : Tier.I;
        }
    }

    public enum Tier {
        I(10, 100), II(25, 300), III(50, 650), IV(75, 1050), V(100, 1500);

        private final int outputPercent;
        private final int powerPercent;

        Tier(int outputPercent, int powerPercent) {
            this.outputPercent = outputPercent;
            this.powerPercent = powerPercent;
        }

        public int level() {
            return ordinal() + 1;
        }

        public int outputPercent() {
            return outputPercent;
        }

        public int powerPercent() {
            return powerPercent;
        }
    }

    public record Part(BlockPos position, BlockState state, Tier addedIn) {
        public Part(BlockPos position, Block block, Tier addedIn) {
            this(position, block.defaultBlockState(), addedIn);
        }

        public Block block() {
            return state.getBlock();
        }
    }

    public record Plan(Structure structure, Tier tier, BlockPos anchor, List<Part> parts) {
        public Plan {
            parts = List.copyOf(parts);
        }

        public BlockPos worldPosition(BlockPos local, BlockPos target, Direction facing) {
            BlockPos offset = local.subtract(anchor);
            return MineMultiblock.getWorldPosition(target, facing,
                    offset.getX(), offset.getY(), offset.getZ());
        }

        public Map<Item, Integer> materials(boolean additionsOnly) {
            Map<Item, Integer> counts = new LinkedHashMap<>();
            for (Part part : parts) {
                if (!part.state().isAir() && (!additionsOnly || part.addedIn() == tier)) {
                    counts.merge(part.block().asItem(), 1, Integer::sum);
                }
            }
            return Collections.unmodifiableMap(counts);
        }
    }

    public static Plan plan(Structure structure, Tier tier) {
        if (tier.level() > structure.maxTier().level()) {
            throw new IllegalArgumentException("Unsupported blueprint tier for " + structure);
        }
        return switch (structure) {
            case MINE -> minePlan(tier);
            case VAULT -> createPlan(structure, tier, createBlock("item_vault"));
            case TANK -> createPlan(structure, tier, createBlock("fluid_tank"));
        };
    }

    private static Block createBlock(String path) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("create", path));
    }

    private static Plan minePlan(Tier tier) {
        return MineBlueprints.current().plan(tier);
    }

    /** Only fully built, cumulative roof extensions affect server-side throughput. */
    public static Tier mineTier(Level level, BlockPos controller, Direction facing, Tier unlocked) {
        if (unlocked == Tier.I) {
            return Tier.I;
        }
        BlockPos origin = MineMultiblock.getOrigin(controller, facing);
        MineBlueprints.Layout layout = MineBlueprints.current();
        Tier active = Tier.I;
        for (Tier tier : Tier.values()) {
            if (tier == Tier.I) {
                continue;
            }
            if (tier.level() > unlocked.level()) {
                break;
            }
            for (var entry : layout.blocks(tier).entrySet()) {
                BlockPos offset = entry.getKey();
                BlockPos position = MineMultiblock.getWorldPosition(origin, facing,
                        offset.getX(), offset.getY(), offset.getZ());
                if (!level.hasChunkAt(position) || !level.getBlockState(position).is(entry.getValue().getBlock())) {
                    return active;
                }
            }
            active = tier;
        }
        return active;
    }

    private static Plan createPlan(Structure structure, Tier tier, Block block) {
        List<Part> parts = new ArrayList<>();
        for (Tier extension : Tier.values()) {
            if (extension.ordinal() > tier.ordinal()) {
                break;
            }
            for (int slice = 2 * extension.ordinal(); slice < 2 * (extension.ordinal() + 1); slice++) {
                for (int x = 0; x < 2; x++) {
                    for (int cross = 0; cross < 2; cross++) {
                        BlockPos position = structure == Structure.TANK
                                ? new BlockPos(x, slice, cross) : new BlockPos(x, cross, slice);
                        parts.add(new Part(position, block, extension));
                    }
                }
            }
        }
        BlockPos anchor = structure == Structure.VAULT ? new BlockPos(0, 0, tier.ordinal()) : BlockPos.ZERO;
        return new Plan(structure, tier, anchor, parts);
    }
}
