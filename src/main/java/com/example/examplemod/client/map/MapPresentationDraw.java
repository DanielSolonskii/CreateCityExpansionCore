package com.example.examplemod.client.map;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;

/** Flush at opacity boundaries so one layer cannot tint buffered text or the next layer. */
public final class MapPresentationDraw {
    private MapPresentationDraw() {}

    /** GUI text and geometry use different buffers. Finish both sides of a floating surface at its own depth. */
    public static void foreground(GuiGraphics graphics, int depth, double alpha, Runnable draw) {
        graphics.flush();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0,0,depth);
            opacity(graphics,alpha,draw);
            graphics.flush();
        } finally {
            graphics.pose().popPose();
        }
    }

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
