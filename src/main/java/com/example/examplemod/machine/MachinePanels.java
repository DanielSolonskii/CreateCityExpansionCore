package com.example.examplemod.machine;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

public final class MachinePanels {
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);
    public static final DyeColor DEFAULT_COLOR = DyeColor.ORANGE;

    private MachinePanels() {}

    public static boolean isPanel(BlockState state) {
        return state.getBlock().getStateDefinition().getProperty(COLOR.getName()) == COLOR;
    }

    public static BlockState withoutColor(BlockState state) {
        return isPanel(state) ? state.setValue(COLOR, DEFAULT_COLOR) : state;
    }
}
