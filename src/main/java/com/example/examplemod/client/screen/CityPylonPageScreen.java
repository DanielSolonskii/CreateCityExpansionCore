package com.example.examplemod.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Existing forms can also live inside the Pylon workspace. */
public abstract class CityPylonPageScreen extends Screen {
    protected CityPylonPageScreen(Component title) { super(title); }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!CityPylonWorkspace.isEmbedded(this)) super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}
