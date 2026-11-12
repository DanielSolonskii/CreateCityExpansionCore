package com.example.examplemod.multiblock;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.multiblock.BlueprintCatalog.Part;
import com.example.examplemod.multiblock.BlueprintCatalog.Plan;
import com.example.examplemod.multiblock.BlueprintCatalog.Structure;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MineBlueprints {
    public static final String RESOURCE_DIRECTORY = "data/createcityexpansioncore/blueprints/mine";
    public static final int MAX_HEIGHT = 16;
    public static final BlockPos ANCHOR = new BlockPos(4, 0, 4);
    private static final BlockPos CONTROLLER = new BlockPos(4, 1, 0);
    private static volatile Layout localOverride;

    private MineBlueprints() {}

    public static String fileName(Tier tier) {
        return "tier_" + tier.level() + ".snbt";
    }

    public static Layout current() {
        Layout override = localOverride;
        return override == null ? Packaged.LAYOUT : override;
    }

    /** The integrated server and its host client share the same immutable snapshot. */
    public static void setLocalOverride(Layout layout) {
        localOverride = layout;
    }

    public static void clearLocalOverride() {
        localOverride = null;
    }

    private static final class Packaged {
        static final Layout LAYOUT = load();

        private static Layout load() {
            Map<Tier, Map<BlockPos, BlockState>> tiers = new EnumMap<>(Tier.class);
            for (Tier tier : Tier.values()) {
                String path = "/" + RESOURCE_DIRECTORY + "/" + fileName(tier);
                try (var stream = MineBlueprints.class.getResourceAsStream(path)) {
                    if (stream == null) {
                        throw new IOException("Missing " + path);
                    }
                    tiers.put(tier, decode(new String(stream.readAllBytes(), StandardCharsets.UTF_8)));
                } catch (IOException | CommandSyntaxException exception) {
                    throw new IllegalStateException("Cannot read " + path, exception);
                }
            }
            return new Layout(tiers);
        }
    }

    public static final class Layout {
        private final Map<Tier, Map<BlockPos, BlockState>> tiers;

        public Layout(Map<Tier, Map<BlockPos, BlockState>> input) {
            Map<Tier, Map<BlockPos, BlockState>> ordered = new EnumMap<>(Tier.class);
            Map<BlockPos, BlockState> used = new HashMap<>();
            for (Tier tier : Tier.values()) {
                Map<BlockPos, BlockState> blocks = input.get(tier);
                if (blocks == null || blocks.isEmpty()) {
                    throw new IllegalArgumentException("Missing blocks for mine tier " + tier);
                }
                if (tier == Tier.I) {
                    validateBase(blocks);
                }
                Map<BlockPos, BlockState> layer = new LinkedHashMap<>();
                for (var entry : blocks.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                    BlockPos pos = entry.getKey().immutable();
                    BlockState state = entry.getValue();
                    if (!inside(pos) || (tier != Tier.I && (pos.getY() < MineMultiblock.HEIGHT
                            || state.isAir() || functional(state))) || used.putIfAbsent(pos, state) != null) {
                        throw new IllegalArgumentException("Invalid or overlapping mine tier " + tier + " cell " + pos.toShortString());
                    }
                    layer.put(pos, state);
                }
                ordered.put(tier, Collections.unmodifiableMap(layer));
            }
            tiers = Map.copyOf(ordered);
        }

        public Map<BlockPos, BlockState> blocks(Tier tier) {
            return tiers.get(tier);
        }

        public BlockState baseState(BlockPos pos) {
            return blocks(Tier.I).get(pos);
        }

        public Plan plan(Tier tier) {
            List<Part> parts = new ArrayList<>();
            for (Tier included : Tier.values()) {
                if (included.level() > tier.level()) {
                    break;
                }
                blocks(included).forEach((pos, state) -> parts.add(new Part(pos, state, included)));
            }
            return new Plan(Structure.MINE, tier, ANCHOR, parts);
        }

        public Layout replacing(Tier tier, Map<BlockPos, BlockState> blocks) {
            Map<Tier, Map<BlockPos, BlockState>> edited = new EnumMap<>(tiers);
            edited.put(tier, blocks);
            return new Layout(edited);
        }
    }

    private static void validateBase(Map<BlockPos, BlockState> blocks) {
        if (blocks.size() != MineMultiblock.WIDTH * MineMultiblock.HEIGHT * MineMultiblock.DEPTH) {
            throw new IllegalArgumentException("Tier I must describe every cell of the 10 × 3 × 10 base");
        }
        BlockPos power = null;
        BlockPos gateway = null;
        for (var entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            if (!inside(pos) || pos.getY() >= MineMultiblock.HEIGHT) {
                throw new IllegalArgumentException("Tier I cell outside the base: " + pos.toShortString());
            }
            boolean drill = pos.getY() == 0 && pos.getX() >= 3 && pos.getX() <= 6 && pos.getZ() >= 3 && pos.getZ() <= 6;
            boolean controller = state.is(CreateCityExpansionCore.MINE_CONTROLLER.get());
            if (drill != state.is(CreateCityExpansionCore.DRILL_UNIT.get()) || controller != pos.equals(CONTROLLER)
                    || (controller && state.getValue(BlockStateProperties.HORIZONTAL_FACING) != Direction.NORTH)) {
                throw new IllegalArgumentException("Invalid controller or drill at " + pos.toShortString());
            }
            if (state.is(CreateCityExpansionCore.MINE_POWER_PORT.get())) {
                if (power != null || !isInterface(pos)) {
                    throw new IllegalArgumentException("Tier I requires one power port on the perimeter");
                }
                power = pos;
            } else if (state.is(CreateCityExpansionCore.MINE_GATEWAY.get())) {
                if (gateway != null || !isInterface(pos)) {
                    throw new IllegalArgumentException("Tier I requires one gateway on the perimeter");
                }
                gateway = pos;
            }
        }
        if (power == null || gateway == null) {
            throw new IllegalArgumentException("Tier I needs a power port and a gateway");
        }
        for (var entry : blocks.entrySet()) {
            BlockPos pos = entry.getKey();
            if (!isInterface(pos)) {
                continue;
            }
            BlockState state = entry.getValue();
            if (MineMultiblock.isPowerServicePosition(pos.getX(), pos.getY(), pos.getZ(), power.getX(), power.getZ())) {
                if (!state.isAir()) {
                    throw new IllegalArgumentException("The power port needs two empty cells");
                }
            } else if (!pos.equals(power) && !pos.equals(gateway) && !state.is(CreateCityExpansionCore.MINE_FRAME.get())) {
                throw new IllegalArgumentException("Invalid perimeter cell at " + pos.toShortString());
            }
        }
        int lateral = power.getZ() == 0 || power.getZ() == MineMultiblock.DEPTH - 1 ? power.getX() : power.getZ();
        if (lateral == 0 || lateral == MineMultiblock.WIDTH - 1
                || MineMultiblock.isPowerServicePosition(CONTROLLER.getX(), CONTROLLER.getY(), CONTROLLER.getZ(),
                power.getX(), power.getZ())) {
            throw new IllegalArgumentException("The power port needs two free perimeter cells");
        }
    }

    public static boolean inside(BlockPos pos) {
        return pos.getX() >= 0 && pos.getX() < MineMultiblock.WIDTH
                && pos.getY() >= 0 && pos.getY() < MAX_HEIGHT
                && pos.getZ() >= 0 && pos.getZ() < MineMultiblock.DEPTH;
    }

    static boolean isInterface(BlockPos pos) {
        return pos.getY() == 1 && !pos.equals(CONTROLLER) && (pos.getX() == 0 || pos.getX() == MineMultiblock.WIDTH - 1
                || pos.getZ() == 0 || pos.getZ() == MineMultiblock.DEPTH - 1);
    }

    private static boolean functional(BlockState state) {
        return state.is(CreateCityExpansionCore.MINE_CONTROLLER.get()) || state.is(CreateCityExpansionCore.DRILL_UNIT.get())
                || state.is(CreateCityExpansionCore.MINE_GATEWAY.get()) || state.is(CreateCityExpansionCore.MINE_POWER_PORT.get());
    }

    public static Map<BlockPos, BlockState> decode(String text) throws CommandSyntaxException {
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (Tag tag : TagParser.parseTag(text).getList("blocks", Tag.TAG_COMPOUND)) {
            CompoundTag row = (CompoundTag) tag;
            int[] coords = row.getIntArray("pos");
            CompoundTag encoded = row.getCompound("state");
            ResourceLocation id = ResourceLocation.tryParse(encoded.getString("Name"));
            if (coords.length != 3 || id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
                throw new IllegalArgumentException("Invalid mine blueprint block: " + row);
            }
            BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), encoded);
            CompoundTag props = encoded.getCompound("Properties");
            CompoundTag actual = NbtUtils.writeBlockState(state).getCompound("Properties");
            for (String name : props.getAllKeys()) {
                if (!props.get(name).equals(actual.get(name))) {
                    throw new IllegalArgumentException("Invalid property " + name + " on " + id);
                }
            }
            BlockPos pos = new BlockPos(coords[0], coords[1], coords[2]);
            if (blocks.putIfAbsent(pos, state) != null) {
                throw new IllegalArgumentException("Duplicate mine blueprint cell " + pos.toShortString());
            }
        }
        return blocks;
    }

    public static String encode(Map<BlockPos, BlockState> blocks) {
        List<String> rows = new ArrayList<>();
        for (var entry : blocks.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            BlockPos pos = entry.getKey();
            rows.add("    {pos:[I;" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                    + "],state:" + NbtUtils.writeBlockState(entry.getValue()) + "}");
        }
        return "{\n  blocks:[\n" + String.join(",\n", rows) + "\n  ]\n}\n";
    }

    public static Rotation rotation(Direction facing) {
        return switch (facing) {
            case NORTH -> Rotation.NONE;
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException("A mine must face horizontally");
        };
    }

    public static BlockState rotate(BlockState state, Rotation rotation) {
        BlockState rotated = state.rotate(rotation);
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            rotated = rotated.setValue(BlockStateProperties.HORIZONTAL_FACING,
                    rotation.rotate(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
        }
        return rotated;
    }
}
