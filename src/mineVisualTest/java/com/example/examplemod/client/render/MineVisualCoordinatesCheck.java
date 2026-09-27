package com.example.examplemod.client.render;

import com.example.examplemod.multiblock.MineMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;

/** Headless checks against actual canonical coordinate helpers (no test-library dependency). */
public final class MineVisualCoordinatesCheck {
    public static void main(String[] args) {
        AssembledBlockVisibility.registerModels(
                "mine_frame", "technical_block", "drill_unit", "mine_controller",
                "mine_power_port", "mine_gateway");
        require(AssembledBlockVisibility.shouldWrap(
                "createcityexpansioncore", "mine_frame", "normal"),
                "Vanilla blockstate registry path must receive the hiding wrapper");
        require(AssembledBlockVisibility.shouldWrap(
                "createcityexpansioncore", "block/mine_frame", "normal"),
                "Additional block model path must receive the hiding wrapper");
        require(AssembledBlockVisibility.shouldWrap(
                "createcityexpansioncore", "mine_controller", "facing=north"),
                "Controller state variants must receive the hiding wrapper");
        require(!AssembledBlockVisibility.shouldWrap(
                "createcityexpansioncore", "item/mine_frame", "inventory"),
                "Item model must remain visible");
        require(!AssembledBlockVisibility.shouldWrap(
                "minecraft", "block/stone", "normal"),
                "Foreign models must remain untouched");
        BlockPos controller = new BlockPos(-19, 73, 31); // Includes negative coordinates/chunk boundaries.
        int drills = 0, ports = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos origin = MineMultiblock.getOrigin(controller, facing);
            for (int x = 3; x <= 6; x++) {
                for (int z = 3; z <= 6; z++) {
                    Vec3 expected = Vec3.atLowerCornerOf(MineMultiblock.getWorldPosition(origin, facing, x, 0, z))
                            .add(.5, 1, .5);
                    Vec3 rendered = IndustrialMineAnchors.transform(controller, facing, new Vec3(x - 3.5, 0, z + .5));
                    require(expected.distanceToSqr(rendered) < 1e-12, "Drill shaft misses canonical Drill Unit");
                    drills++;
                }
            }
            for (int x = 0; x < 10; x++) {
                for (int z = 0; z < 10; z++) {
                    if (x != 0 && x != 9 && z != 0 && z != 9) continue;
                    BlockPos port = MineMultiblock.getWorldPosition(origin, facing, x, 1, z);
                    if (port.equals(controller)) continue;
                    Direction outward = MineMultiblock.interfaceOutward(controller, facing, port);
                    Vec3 localFace = IndustrialMineAnchors.transform(controller, facing,
                            new Vec3(x - 3.5, .5, z + .5));
                    require(Vec3.atCenterOf(port).distanceToSqr(localFace) < 1e-12, "Attachment center misses hitbox");
                    BlockPos next = port.relative(outward);
                    boolean outside = true;
                    for (int a = 0; a < 10; a++) {
                        for (int b = 0; b < 10; b++) {
                            if (MineMultiblock.getWorldPosition(origin, facing, a, 1, b).equals(next)) outside = false;
                        }
                    }
                    require(outside, "Interface points inward");
                    // Power candidates exclude corner positions and controller service neighbors.
                    if ((x == 0 || x == 9) && (z == 0 || z == 9)) continue;
                    if (z == 0 && (x == 3 || x == 5)) continue;
                    var members = MineMultiblock.visualMembers(controller, facing, port);
                    require(members.size() == 298 && new HashSet<>(members).size() == 298, "Member count / duplicates");
                    require(members.contains(controller) && members.contains(port), "Missing interaction block");
                    Direction tangent = outward.getClockWise();
                    require(!members.contains(port.relative(tangent)) && !members.contains(port.relative(tangent.getOpposite())),
                            "Power wire cells must stay visible and excluded from ownership");
                    ports++;
                }
            }
        }
        System.out.println("PASS: " + drills + " drill placements, " + ports
                + " power layouts, all perimeter gateway normals, all 4 orientations; 298 members each.");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
