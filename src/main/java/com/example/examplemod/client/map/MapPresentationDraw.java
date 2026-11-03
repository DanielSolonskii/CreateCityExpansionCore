package com.example.examplemod.client.map;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

/** Flush at opacity boundaries so one layer cannot tint buffered text or the next layer. */
public final class MapPresentationDraw {
    private MapPresentationDraw() {}

    public static void opacity(GuiGraphics graphics, double opacity, Runnable draw) {
        if(opacity>=1) {draw.run();return;} // No extra flushes during the steady state.
        graphics.flush();
        float[] before = RenderSystem.getShaderColor().clone();
        RenderSystem.setShaderColor(before[0], before[1], before[2], before[3] * (float) Math.clamp(opacity, 0, 1));
        try { draw.run(); }
        finally {
            try { graphics.flush(); }
            finally { RenderSystem.setShaderColor(before[0], before[1], before[2], before[3]); }
        }
    }
}
