package com.example.examplemod.electrical;

import com.example.examplemod.network.DrillPlacementFeedback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;

public final class DrillUnitBlockItem extends BlockItem {
    public DrillUnitBlockItem(DrillUnitBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        var base = DrillUnitBlock.placementOrigin(context);
        InteractionResult result = super.place(context);
        if (!result.consumesAction()) {
            DrillPlacementFeedback.failed(context.getLevel(), context.getPlayer(), base);
        }
        return result;
    }
}
