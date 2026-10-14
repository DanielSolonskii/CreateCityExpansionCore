package com.example.examplemod.client.render;

import com.example.examplemod.mine.MineControllerBlockEntity;
import com.example.examplemod.multiblock.MineMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

public final class IndustrialMineAnchors {

    public enum Anchor {
        DRILL_GROUP_NW(0, -.4, 4),
        DRILL_GROUP_NE(2, -.4, 4),
        DRILL_GROUP_SW(0, -.4, 6),
        DRILL_GROUP_SE(2, -.4, 6),
        MAIN_DRIVE(1, 2.88, 5),
        CONTROL_PANEL(.5, .65, -.08),
        POWER_PORT(null), OUTPUT_GATE(null), POWER(null), OUTPUT(null);

        private final Vec3 local;

        Anchor(double x, double y, double z) {
            local = new Vec3(x, y, z);
        }
        Anchor(Vec3 local) { this.local = local; }
    }

    private IndustrialMineAnchors() {
    }

    public static Vec3 world(BlockPos controllerPos, Direction facing, Anchor anchor) {
        if (anchor.local == null) throw new IllegalArgumentException("Dynamic anchor requires controller state");
        return transform(controllerPos, facing, anchor.local);
    }

    /** Same block-center pivot as the renderer, including EAST/SOUTH/WEST translations. */
    public static Vec3 transform(BlockPos controllerPos, Direction facing, Vec3 local) {
        Direction right = facing.getClockWise();
        Direction backward = facing.getOpposite();
        double x = local.x - .5, z = local.z - .5;
        return Vec3.atLowerCornerOf(controllerPos)
                .add(.5 + right.getStepX() * x + backward.getStepX() * z,
                        local.y, .5 + right.getStepZ() * x + backward.getStepZ() * z);
    }

    /** Null when no authoritative interface is present; never fall back to a fixed position. */
    public static Vec3 world(MineControllerBlockEntity controller, Direction facing, Anchor anchor) {
        if (anchor.local != null) return world(controller.getBlockPos(), facing, anchor);
        BlockPos port = anchor == Anchor.POWER || anchor == Anchor.POWER_PORT
                ? controller.getVisualPowerPort() : controller.getVisualGateway();
        if (!controller.isVisualAssembled() || port == null) return null;
        Direction outward = MineMultiblock.interfaceOutward(controller.getBlockPos(), facing, port);
        return Vec3.atCenterOf(port).add(outward.getStepX() * .66, .05, outward.getStepZ() * .66);
    }
}
