package com.example.examplemod.client;

import com.example.examplemod.multiblock.BlueprintCatalog.Part;
import com.example.examplemod.multiblock.BlueprintCatalog.Plan;
import com.example.examplemod.multiblock.MineBlueprints;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.logistics.vault.ItemVaultBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/** Shared model states for the catalog and the oriented world projection. */
public final class BlueprintBlockModels {
    private BlueprintBlockModels() {}

    public static int topLayer(Plan plan) {
        return plan.parts().stream().mapToInt(part -> part.position().getY()).max().orElse(0);
    }

    public static BlockState state(Part part, int topY, Direction facing) {
        BlockState state = part.state();
        if (part.block() instanceof ItemVaultBlock) {
            return state.setValue(ItemVaultBlock.HORIZONTAL_AXIS, facing.getAxis());
        }
        if (part.block() instanceof FluidTankBlock) {
            BlockPos local = part.position();
            Direction right = facing.getClockWise();
            Direction back = facing.getOpposite();
            int x = (2 * local.getX() - 1) * right.getStepX() + (2 * local.getZ() - 1) * back.getStepX();
            int z = (2 * local.getX() - 1) * right.getStepZ() + (2 * local.getZ() - 1) * back.getStepZ();
            FluidTankBlock.Shape shape = x < 0
                    ? (z < 0 ? FluidTankBlock.Shape.WINDOW_NW : FluidTankBlock.Shape.WINDOW_SW)
                    : (z < 0 ? FluidTankBlock.Shape.WINDOW_NE : FluidTankBlock.Shape.WINDOW_SE);
            return state.setValue(FluidTankBlock.BOTTOM, local.getY() == 0)
                    .setValue(FluidTankBlock.TOP, local.getY() == topY)
                    .setValue(FluidTankBlock.SHAPE, shape);
        }
        return MineBlueprints.rotate(state, MineBlueprints.rotation(facing));
    }
}
