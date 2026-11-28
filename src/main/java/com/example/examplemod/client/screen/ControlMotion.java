package com.example.examplemod.client.screen;

import com.example.examplemod.client.map.DispatchMotion;
import net.minecraft.client.gui.GuiGraphics;

/** Color transitions for controls whose artwork and input must share the same pixel bounds. */
final class ControlMotion {
    private final DispatchMotion.Transition hover = new DispatchMotion.Transition(0);
    private final DispatchMotion.Transition press = new DispatchMotion.Transition(0);
    private final DispatchMotion.Transition selection = new DispatchMotion.Transition(0);
    private boolean seen;
    private long lastFrame;
    double hovered;
    double pressed;
    double selected;

    void update(long now, boolean reduced, boolean over, boolean down, boolean active) {
        // A control reappearing after a popover closes starts in its current selection state.
        boolean fresh = !seen || now - lastFrame > 250_000_000L;
        seen = true;
        lastFrame = now;
        hovered = hover.value(over ? 1 : 0, now, reduced, over ? 110 : 140);
        pressed = press.value(down ? 1 : 0, now, reduced, down ? 70 : 120);
        selected = selection.value(active ? 1 : 0, now, reduced || fresh, 160);
    }

    static final class TabIndicator {
        private final DispatchMotion.Transition progress = new DispatchMotion.Transition(1);
        private int targetLeft, targetRight;
        private double fromLeft, fromRight;
        private long lastFrame;
        private boolean seen;

        void paint(GuiGraphics graphics, WorldMapUiLayout.Rect surface,
                   int left, int right, int bottom, long now, boolean reduced) {
            if (reduced || !seen || now - lastFrame > 250_000_000L) {
                fromLeft = targetLeft = left;
                fromRight = targetRight = right;
                progress.value(1,now,true,0);
            } else if (left != targetLeft || right != targetRight) {
                double current = progress.sample(now);
                fromLeft += (targetLeft - fromLeft) * current;
                fromRight += (targetRight - fromRight) * current;
                targetLeft = left;
                targetRight = right;
                progress.value(0,now,true,0);
                progress.value(1,now,false,200);
            }
            lastFrame = now;
            seen = true;
            double current = progress.sample(now);
            int x1 = Math.max(surface.x(), (int)Math.round(fromLeft + (targetLeft - fromLeft) * current));
            int x2 = Math.min(surface.right(), (int)Math.round(fromRight + (targetRight - fromRight) * current));
            if (x2 > x1) graphics.fill(x1,bottom-2,x2,bottom,WorldMapChrome.ACCENT);
        }
    }
}
