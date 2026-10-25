package com.example.examplemod.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.IdentityHashMap;
import java.util.Map;

/** Existing forms can also live inside the Pylon workspace. */
public abstract class CityPylonPageScreen extends Screen {

    private final UiMotion motion = new UiMotion();
    /**
     * Resting Y of every widget, captured before the first slide is applied.
     *
     * <p>Identity keys, not indices: {@link #clearWidgets()} rebuilds the list on every resize, and
     * an index-keyed map would hand a new widget the position of whatever used to sit there.
     */
    private final Map<AbstractWidget, Integer> restingY = new IdentityHashMap<>();
    /** Frame {@link #frameDelta()} was last sampled in, and the step that sample produced. */
    private long frameStamp;
    private float frameStep = 1f;

    protected CityPylonPageScreen(Component title) {
        super(title);
    }

    /**
     * How far this frame's content still has to fall into place, in logical pixels.
     *
     * <p>Call once per frame and thread the value through: the painter adds it, the cursor read
     * subtracts it, and the widgets are moved to it. Asking twice produces two different numbers
     * for the same frame and the controls visibly detach from their panel.
     */
    protected final float entranceOffset() {
        long now = net.minecraft.Util.getMillis();
        motion.opened(now);
        return motion.entranceOffset(now);
    }

    /**
     * Restarts the entrance.
     *
     * <p>Workspace tabs are cached and reused rather than rebuilt on every visit, so without this
     * the second visit to a tab would find {@code openedAt} already stamped and show the page
     * already settled -- the animation would play exactly once per session.
     */
    protected final void replayEntrance() {
        motion.reopen();
    }

    /**
     * Lerp step for this frame, capped so a stall cannot make a highlight overshoot.
     *
     * <p>Sampled at most once per frame: the underlying motion clock advances when it is read, so
     * a second read inside the same frame would report zero and freeze every highlight that had
     * not been sampled yet. Callers can therefore ask freely, wherever they happen to paint.
     */
    protected final float frameDelta() {
        long now = net.minecraft.Util.getMillis();
        if (now != frameStamp) {
            frameStamp = now;
            frameStep = motion.delta(now);
        }
        return frameStep;
    }

    /**
     * Smoothed hover for a control, keyed by whatever identifies it.
     *
     * @param key a stable identity -- a row's subject id, or a fixed control's position. Never a
     *            list index: re-sorting under the cursor would carry the highlight to a new row
     */
    protected final float hover(int key, boolean over, float delta) {
        return motion.hover(key, over, delta);
    }

    /**
     * Moves the vanilla widgets onto a panel that is sliding in.
     *
     * <p>Widgets are rendered by {@link Screen#render} in plain screen coordinates, outside any
     * pose the screen pushes for its own drawing. Adding the slide to their Y is what keeps the
     * buttons on the panel instead of leaving them behind for the length of the entrance. Their
     * own hit-testing uses these same coordinates, so clicking stays correct for free.
     */
    protected final void slideWidgets(float offset) {
        int shift = (int) offset;
        for (Renderable renderable : this.renderables) {
            if (renderable instanceof AbstractWidget widget) {
                Integer resting = this.restingY.get(widget);
                if (resting == null) {
                    resting = widget.getY();
                    this.restingY.put(widget, resting);
                }
                widget.setY(resting + shift);
            }
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!CityPylonWorkspace.isEmbedded(this)) super.renderBackground(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
