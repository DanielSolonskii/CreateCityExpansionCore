package com.example.examplemod.mine;

import com.example.examplemod.devtools.BlueprintEditorWorld;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.multiblock.MineBlueprints;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.fml.loading.LoadingModList;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MineBlueprintEditorCheck {
    public static void main(String[] args) throws Exception {
        checkPackagedLayouts();
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
        checkStatesAndCollisions();
        System.out.println("PASS: five packaged mine tiers, required parts and adjacent displays, SNBT block state round-trip, rotation and collision rules.");
    }

    private static void checkPackagedLayouts() throws Exception {
        for (Tier tier : Tier.values()) {
            String path = "/" + MineBlueprints.RESOURCE_DIRECTORY + "/" + MineBlueprints.fileName(tier);
            try (var stream = MineBlueprintEditorCheck.class.getResourceAsStream(path)) {
                require(stream != null, "Missing packaged tier " + tier);
                var rows = TagParser.parseTag(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                        .getList("blocks", Tag.TAG_COMPOUND);
                require(!rows.isEmpty(), "Empty packaged tier " + tier);
                var cells = new HashMap<BlockPos, CompoundTag>();
                for (Tag tag : rows) {
                    CompoundTag row = (CompoundTag) tag;
                    int[] p = row.getIntArray("pos");
                    require(p.length == 3, "Blueprint position needs three coordinates");
                    BlockPos pos = new BlockPos(p[0], p[1], p[2]);
                    CompoundTag state = row.getCompound("state");
                    String block = state.getString("Name");
                    require(!block.isEmpty() && ResourceLocation.tryParse(block) != null, "Invalid block ID at " + pos);
                    require(cells.putIfAbsent(pos, state) == null, "Repeated blueprint cell " + pos);
                }
                if (tier != Tier.I) continue;
                BlockPos controller = uniquePart(cells, "mine_controller");
                require(controller.equals(MineBlueprints.CONTROLLER), "Controller is at the reference position");
                require(cells.get(controller).getCompound("Properties").getString("facing").equals("north"),
                        "Controller faces north");
                require(cells.values().stream().filter(s -> s.getString("Name").equals("createcityexpansioncore:drill_unit")).count() >= 8,
                        "Base contains at least one auger's worth of blocks");
                requireAdjacentDisplay(cells, uniquePart(cells, "mine_power_port"), "energy");
                requireAdjacentDisplay(cells, uniquePart(cells, "mine_gateway"), "output");
            }
        }
    }

    private static BlockPos uniquePart(Map<BlockPos, CompoundTag> cells, String part) {
        var positions = cells.entrySet().stream()
                .filter(entry -> entry.getValue().getString("Name").equals("createcityexpansioncore:" + part))
                .map(Map.Entry::getKey).toList();
        require(positions.size() == 1, "Expected exactly one " + part);
        return positions.getFirst();
    }

    private static void requireAdjacentDisplay(Map<BlockPos, CompoundTag> cells, BlockPos port, String role) {
        for (Direction direction : Direction.values()) {
            CompoundTag state = cells.get(port.relative(direction));
            if (state != null && state.getString("Name").equals("createcityexpansioncore:technical_screen")
                    && state.getCompound("Properties").getString("role").equals(role)) return;
        }
        throw new AssertionError("Missing adjacent " + role + " display at " + port);
    }

    private static void checkStatesAndCollisions() throws Exception {
        var stairs = Blocks.OAK_STAIRS.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        var expected = Map.of(BlockPos.ZERO, stairs, new BlockPos(1, 0, 1), Blocks.AIR.defaultBlockState());
        String saved = MineBlueprints.encode(expected);
        require(MineBlueprints.decode(saved).equals(expected), "Air and block-state properties survive SNBT");
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var rotated = MineBlueprints.rotate(stairs, MineBlueprints.rotation(facing));
            require(rotated.getValue(BlockStateProperties.HORIZONTAL_FACING) == facing, "Placement facing " + facing);
        }
        require(MineBlueprints.rotate(MineBlueprints.rotate(stairs, Rotation.CLOCKWISE_90), Rotation.COUNTERCLOCKWISE_90)
                .equals(stairs), "Capture reverses world rotation");
        require(BlueprintEditorWorld.canPlace(stairs, Blocks.AIR.defaultBlockState()), "Can place in air");
        require(!BlueprintEditorWorld.canPlace(stairs, Blocks.STONE.defaultBlockState()), "Placed blocks cannot be overwritten");
        require(!BlueprintEditorWorld.canPlace(stairs, Blocks.WATER.defaultBlockState()), "Cannot replace water");
        require(!BlueprintEditorWorld.canPlace(Blocks.AIR.defaultBlockState(), Blocks.STONE.defaultBlockState()),
                "Service cells must be empty without clearing the world");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
