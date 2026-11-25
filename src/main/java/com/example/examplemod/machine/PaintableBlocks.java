package com.example.examplemod.machine;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class PaintableBlocks {
    private static final Map<Block, Map<DyeColor, Block>> FAMILIES = new HashMap<>();

    static {
        for (String family : new String[]{"wool", "carpet", "stained_glass", "stained_glass_pane",
                "terracotta", "glazed_terracotta", "concrete", "concrete_powder", "candle"}) {
            var colors = new EnumMap<DyeColor, Block>(DyeColor.class);
            for (var color : DyeColor.values()) {
                var id = ResourceLocation.withDefaultNamespace(color.getName() + "_" + family);
                colors.put(color, BuiltInRegistries.BLOCK.getOptional(id).orElseThrow());
            }
            colors.values().forEach(block -> FAMILIES.put(block, colors));
            switch (family) {
                case "stained_glass" -> FAMILIES.put(Blocks.GLASS, colors);
                case "stained_glass_pane" -> FAMILIES.put(Blocks.GLASS_PANE, colors);
                case "terracotta" -> FAMILIES.put(Blocks.TERRACOTTA, colors);
                case "candle" -> FAMILIES.put(Blocks.CANDLE, colors);
            }
        }
    }

    static BlockState recolor(BlockState state, DyeColor color) {
        if (MachinePanels.isPanel(state)) return state.setValue(MachinePanels.COLOR, color);
        var family = FAMILIES.get(state.getBlock());
        return family == null ? null : family.get(color).withPropertiesOf(state);
    }

    private PaintableBlocks() {}
}
