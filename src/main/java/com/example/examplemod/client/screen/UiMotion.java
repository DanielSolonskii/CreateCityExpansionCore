package com.example.examplemod.client.screen;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-screen animation state.
 *
 * <p>The profile and friends windows feel responsive because three separate things move at the
 * same time: the window eases into place on open, hover highlights lerp instead of snapping, and
 * a tab or page swap crossfades over the new content. Each of those needs two pieces of state --
 * when it started and where it was last frame -- so a screen that forgets one of them renders at a
 * different speed from every other window. Keeping all of it here means a screen owns one object
 * and cannot lose a piece of it.
 *
 * <p>Public because the controls that need it do not all live in this package: shared widgets in
 * {@code client.gui} animate against the same clock and would otherwise have to grow a second,
 * diverging copy of the easing.
 *
 * <p>Every method takes an explicit clock value rather than reading the time itself. Callers pass
 * {@code net.minecraft.Util.getMillis()} once per frame and thread that single value through, so
 * the entrance, the crossfade and the hover lerp all observe the same instant and agree.
 */
public final class UiMotion {
    /** How far the window still has to travel when the entrance starts, in logical pixels. */
    public static final int ENTRANCE_DISTANCE = 8;
    private static final float ENTRANCE_MS = 180f;
    private static final float CROSSFADE_MS = 150f;
    /**
     * A frame gap above this is treated as a stall -- a breakpoint hit, a window drag, an
     * autosave -- rather than as time the player actually watched. Unclamped, one such gap makes a
     * lerp overshoot and visibly snap back.
     */
    private static final float FRAME_MS = 95f;

    private final Map<Integer, Float> hover = new HashMap<>();
    private long openedAt;
    private long previousFrame;

    /**
     * Marks the instant the screen opened. Re-initialisation from a window resize must not replay
     * the entrance, so the timestamp is kept once and only ever set again by {@link #reopen()}.
     */
    public void opened(long now) {
        if (openedAt == 0) {
            openedAt = now;
            previousFrame = now;
        }
    }

    /** Restarts the entrance, for screens that re-enter themselves instead of reopening. */
    public void reopen() {
        openedAt = 0;
        previousFrame = 0;
    }

    /**
     * Fraction of a frame's length already elapsed, in the unit the lerps below expect.
     * Must be called once per frame; the first frame returns 1 so nothing animates in from an
     * undefined starting point.
     */
    public float delta(long now) {
        long previous = previousFrame;
        previousFrame = now;
        return previous == 0 ? 1f : Math.min(1f, (now - previous) / FRAME_MS);
    }

    /** Fraction of the entrance already played: 0 on the opening frame, 1 once settled. */
    public float entrance(long now) {
        return openedAt == 0 ? 1f : ease((now - openedAt) / ENTRANCE_MS);
    }

    /**
     * Remaining entrance slide in logical pixels. Paint the window at {@code +offset} and hit-test
     * at {@code -offset}: the two must cancel, or the buttons move out from under the cursor
     * during the first fifth of a second after opening.
     */
    public float entranceOffset(long now) {
        return ENTRANCE_DISTANCE * (1f - entrance(now));
    }

    /**
     * 1 the instant content is swapped, easing to 0 over {@link #CROSSFADE_MS}. Multiply into a
     * surface-coloured overlay to fade the new content in rather than cutting to it.
     */
    public float crossfade(long now, long switchedAt) {
        return 1f - ease((now - switchedAt) / CROSSFADE_MS);
    }

    /**
     * Smoothed hover for a stable id. The id must not depend on list position -- sorting a list
     * under the cursor would carry the highlight with the row rather than the pointer.
     */
    public float hover(int id, boolean over, float delta) {
        float current = hover.getOrDefault(id, 0f);
        float next = current + ((over ? 1f : 0f) - current) * delta;
        hover.put(id, next);
        return next;
    }

    /** Cubic ease-out: quick to leave the start, long to arrive. Matches the profile window. */
    public static float ease(float value) {
        float t = Math.max(0f, Math.min(1f, value));
        float inverted = 1f - t;
        return 1f - inverted * inverted * inverted;
    }

    /** Linear blend between two ARGB colours, clamped. Drives hover and progress feedback. */
    public static int mix(int from, int to, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        int red = (int) (((from >> 16) & 255) * (1 - clamped) + ((to >> 16) & 255) * clamped);
        int green = (int) (((from >> 8) & 255) * (1 - clamped) + ((to >> 8) & 255) * clamped);
        int blue = (int) ((from & 255) * (1 - clamped) + (to & 255) * clamped);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
