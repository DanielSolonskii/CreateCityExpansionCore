package com.example.examplemod.mine;

import com.example.examplemod.devtools.BlueprintEditorWorld;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.multiblock.MineBlueprints;
import com.example.examplemod.multiblock.MineMultiblock;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class MineBlueprintEditorCheck {
    public static void main(String[] args) throws Exception {
        checkPackagedLayouts();
        SharedConstants.tryDetectVersion();
        LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        Bootstrap.bootStrap();
        checkStatesAndCollisions();
        System.out.println("PASS: five packaged mine tiers, required footprint and interfaces, SNBT block state round-trip, rotation and collision rules.");
    }

    private static void checkPackagedLayouts() throws Exception {
        var used = new HashSet<BlockPos>();
        for (Tier tier : Tier.values()) {
            String path = "/" + MineBlueprints.RESOURCE_DIRECTORY + "/" + MineBlueprints.fileName(tier);
            try (var stream = MineBlueprintEditorCheck.class.getResourceAsStream(path)) {
                require(stream != null, "Missing packaged tier " + tier);
                var rows = TagParser.parseTag(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                        .getList("blocks", Tag.TAG_COMPOUND);
                int baseSize = MineMultiblock.WIDTH * MineMultiblock.HEIGHT * MineMultiblock.DEPTH;
                require(tier == Tier.I ? rows.size() == baseSize : !rows.isEmpty(), "Cell count for tier " + tier);
                int powerPorts = 0;
                int gateways = 0;
                for (Tag tag : rows) {
                    CompoundTag row = (CompoundTag) tag;
                    int[] p = row.getIntArray("pos");
                    require(p.length == 3, "Blueprint position needs three coordinates");
                    BlockPos pos = new BlockPos(p[0], p[1], p[2]);
                    require(MineBlueprints.inside(pos) && used.add(pos), "Out-of-bounds or repeated blueprint cell " + pos);
                    require((tier == Tier.I) == (pos.getY() < MineMultiblock.HEIGHT), "Wrong tier height at " + pos);
                    CompoundTag state = row.getCompound("state");
                    String block = state.getString("Name");
                    require(!block.isEmpty() && ResourceLocation.tryParse(block) != null, "Invalid block ID at " + pos);
                    boolean controller = block.equals("createcityexpansioncore:mine_controller");
                    require(controller == pos.equals(new BlockPos(4, 1, 0)), "Controller position at " + pos);
                    if (controller) {
                        require(state.getCompound("Properties").getString("facing").equals("north"), "Controller faces north");
                    }
                    boolean drillCell = pos.getY() == 0 && pos.getX() >= 3 && pos.getX() <= 6
                            && pos.getZ() >= 3 && pos.getZ() <= 6;
                    require(drillCell == block.equals("createcityexpansioncore:drill_unit"), "Drill position at " + pos);
                    if (block.equals("createcityexpansioncore:mine_power_port")) {
                        require(isPerimeter(pos), "Power port must be on the perimeter");
                        powerPorts++;
                    }
                    if (block.equals("createcityexpansioncore:mine_gateway")) {
                        require(isPerimeter(pos), "Gateway must be on the perimeter");
                        gateways++;
                    }
                }
                int requiredInterfaces = tier == Tier.I ? 1 : 0;
                require(powerPorts == requiredInterfaces && gateways == requiredInterfaces, "Required interfaces for " + tier);
            }
        }
    }

    private static boolean isPerimeter(BlockPos pos) {
        return pos.getY() == 1 && (pos.getX() == 0 || pos.getX() == MineMultiblock.WIDTH - 1
                || pos.getZ() == 0 || pos.getZ() == MineMultiblock.DEPTH - 1);
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
