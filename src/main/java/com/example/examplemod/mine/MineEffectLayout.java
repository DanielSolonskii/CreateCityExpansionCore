package com.example.examplemod.mine;

import com.example.examplemod.electrical.DrillUnitBlock;
import com.example.examplemod.electrical.TechnicalBlock;
import com.example.examplemod.multiblock.BlueprintCatalog.Tier;
import com.example.examplemod.multiblock.MineBlueprints;
import com.example.examplemod.multiblock.MineMultiblock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Authoritative effect anchors from the actual tier, including edited sparse blueprints. */
public final class MineEffectLayout {
    private MineEffectLayout() {}

    public static void publish(ServerLevel level, MineControllerBlockEntity controller, boolean assembled, Tier tier) {
        var drills = new java.util.TreeSet<BlockPos>();
        var vents = new java.util.TreeSet<BlockPos>();
        if (assembled) {
            var facing = controller.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING);
            var origin = MineMultiblock.getOrigin(controller.getBlockPos(), facing);
            MineBlueprints.current().blocks(tier).forEach((local, expected) -> {
                if (!(expected.getBlock() instanceof DrillUnitBlock) && !(expected.getBlock() instanceof TechnicalBlock)) return;
                var pos = MineMultiblock.getWorldPosition(origin, facing, local.getX(), local.getY(), local.getZ());
                if (!level.hasChunkAt(pos)) return;
                var state = level.getBlockState(pos);
                if (state.getBlock() instanceof DrillUnitBlock) {
                    drills.add(state.getValue(DrillUnitBlock.PART) == DrillUnitBlock.Part.SINGLE
                            ? pos : DrillUnitBlock.origin(pos, state));
                } else if (state.getBlock() instanceof TechnicalBlock) vents.add(pos);
            });
        }
        controller.publishEffectParts(java.util.List.copyOf(drills), java.util.List.copyOf(vents));
    }
}
