package com.example.examplemod.mine;

import com.example.examplemod.CreateCityExpansionCore;
import com.example.examplemod.devtools.BlueprintEditorWorld;
import com.example.examplemod.electrical.MinePowerPortBlock;
import com.example.examplemod.machine.MachineDisplayBlock;
import com.example.examplemod.machine.MachineDisplaySource.Role;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.multiblock.MineBlueprints;
import com.example.examplemod.multiblock.MineMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

@GameTestHolder(CreateCityExpansionCore.MODID)
@PrefixGameTestTemplate(false)
public final class FlexibleMineBlueprintGameTest {
    @GameTest(template = "auger_empty", timeoutTicks = 200)
    public static void largerIrregularMineCanBeCapturedAndRotated(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(4, 30, 4)).offset(0, 0, 96);
        var base = new HashMap<BlockPos, BlockState>();
        base.put(MineBlueprints.CONTROLLER, CreateCityExpansionCore.MINE_CONTROLLER.get().defaultBlockState());
        BlockPos power = new BlockPos(-3, 2, 3);
        BlockPos gateway = new BlockPos(5, -1, 4);
        var port = CreateCityExpansionCore.MINE_POWER_PORT.get();
        var upward = port.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP);
        base.put(power, upward);
        base.put(gateway, CreateCityExpansionCore.MINE_GATEWAY.get().defaultBlockState());
        var screen = CreateCityExpansionCore.TECHNICAL_SCREEN.get().defaultBlockState();
        BlockPos energyScreen = power.east();
        BlockPos outputScreen = gateway.below();
        base.put(energyScreen, screen.setValue(MachineDisplayBlock.ROLE, Role.ENERGY));
        base.put(outputScreen, screen.setValue(MachineDisplayBlock.ROLE, Role.OUTPUT));
        var drill = CreateCityExpansionCore.DRILL_UNIT.get();
        for (BlockPos offset : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(1, 1, 1))) {
            base.put(new BlockPos(1, -1, 2).offset(offset),
                    drill.partState(offset.getX(), offset.getY(), offset.getZ(), Direction.EAST));
        }
        BlockPos far = new BlockPos(31, 25, 6);
        base.put(far, Blocks.COPPER_BLOCK.defaultBlockState());

        var previous = MineBlueprints.current();
        var tiers = new EnumMap<Tier, Map<BlockPos, BlockState>>(Tier.class);
        for (Tier tier : Tier.values()) tiers.put(tier, Map.of());
        tiers.put(Tier.I, base);
        var original = new MineBlueprints.Layout(tiers);
        MineBlueprints.setLocalOverride(original);
        try {
            var plan = original.plan(Tier.I);
        BlockPos min = origin.offset(-3, -2, 0);
            BlockPos max = origin.offset(far);
            loadRectangle(level, min, max);
            BlueprintEditorWorld.place(level, plan, origin.offset(plan.anchor()), Direction.NORTH);
            var capture = BlueprintEditorWorld.capture(level, min, max);
            var merged = BlueprintEditorWorld.merge(original, Tier.I, capture);
            helper.assertTrue(merged.blocks(Tier.I).equals(base),
                    "Capture preserves negative and distant coordinates; differences: "
                            + merged.blocks(Tier.I).entrySet().stream()
                            .filter(entry -> entry.getValue() != base.get(entry.getKey())).limit(8).toList());
            helper.assertTrue(MineBlueprints.decode(MineBlueprints.encode(merged.blocks(Tier.I))).equals(base),
                    "Irregular layout round-trips through SNBT");
            MineBlueprints.setLocalOverride(merged);

            BlockPos target = origin.offset(48, 0, 48);
            var rotated = merged.plan(Tier.I);
            for (var part : rotated.parts()) level.getChunkAt(rotated.worldPosition(part.position(), target, Direction.WEST));
            BlueprintEditorWorld.place(level, rotated, target, Direction.WEST);
            BlockPos controller = rotated.worldPosition(MineBlueprints.CONTROLLER, target, Direction.WEST);
            var validation = MineMultiblock.validate(level, controller);
            helper.assertTrue(validation.assembled()
                            && validation.powerPort().equals(rotated.worldPosition(power, target, Direction.WEST))
                            && validation.gateway().equals(rotated.worldPosition(gateway, target, Direction.WEST)),
                    "Validation resolves ports from the rotated layout");
            helper.assertTrue(level.getBlockState(validation.powerPort()).getValue(BlockStateProperties.FACING) == Direction.UP,
                    "Horizontal rotation preserves an upward port");
            helper.assertTrue(level.getBlockState(validation.powerPort()).getValue(MinePowerPortBlock.VERTICAL_ROTATION)
                    == Direction.WEST, "Rotating the blueprint turns the vertical port's contacts");
            for (Direction face : java.util.List.of(Direction.UP, Direction.DOWN)) {
                var state = upward.setValue(BlockStateProperties.FACING, face);
                var nodePositions = new java.util.HashSet<Vec3>();
                for (Direction heading : Direction.Plane.HORIZONTAL) {
                    var oriented = state.setValue(MinePowerPortBlock.VERTICAL_ROTATION, heading);
                    var node = port.getNodePosition(level, validation.powerPort(), oriented, 0);
                    nodePositions.add(node);
                    var rotatedState = oriented.rotate(Rotation.CLOCKWISE_90);
                    var rotatedNode = port.getNodePosition(level, validation.powerPort(), rotatedState, 0);
                    helper.assertTrue(rotatedNode.equals(new Vec3(1 - node.z, node.y, node.x)),
                            "Wire endpoints follow a quarter turn");
                }
                helper.assertTrue(nodePositions.size() == 4, "Vertical port has four distinct contact orientations");
            }
            for (Direction side : Direction.values()) {
                var state = port.defaultBlockState().setValue(BlockStateProperties.FACING, side);
                var nodes = port.getNodePositions(level, validation.powerPort(), state);
                var midpoint = nodes.get(0).add(nodes.get(1)).scale(.5);
                helper.assertTrue(midpoint.distanceTo(new Vec3(.5 + side.getStepX(), .5 + side.getStepY(),
                        .5 + side.getStepZ())) < .0001, "Both contacts sit half a block outside the " + side + " face");
                helper.assertTrue(Math.abs(nodes.get(0).distanceTo(nodes.get(1)) - .5) < .0001,
                        "Contacts are distinct and keep their spacing");
            }
            var controllerEntity = (MineControllerBlockEntity) level.getBlockEntity(controller);
            IndustrialMineService.inspectSnapshot(level, controller, controllerEntity);
            for (BlockPos local : java.util.List.of(energyScreen, outputScreen)) {
                var display = (com.example.examplemod.machine.MachineDisplayBlockEntity)
                        level.getBlockEntity(rotated.worldPosition(local, target, Direction.WEST));
                helper.assertTrue(display.telemetry().assembled(), "Adjacent screen receives controller readings");
            }
            level.removeBlock(rotated.worldPosition(far, target, Direction.WEST), false);
            helper.assertTrue(!MineMultiblock.validate(level, controller).assembled(),
                    "Validation includes cells beyond the old footprint");
            helper.succeed();
        } finally {
            MineBlueprints.setLocalOverride(previous);
        }
    }

    private static void loadRectangle(net.minecraft.server.level.ServerLevel level, BlockPos min, BlockPos max) {
        for (int x = min.getX() >> 4; x <= max.getX() >> 4; x++) {
            for (int z = min.getZ() >> 4; z <= max.getZ() >> 4; z++) level.getChunk(x, z);
        }
    }
}
