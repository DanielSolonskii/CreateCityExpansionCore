package com.example.examplemod.client.map;

import java.util.Objects;

/** Local presentation timelines. Callers supply monotonic time; no game state is interpolated. */
public final class DispatchMotion {
    private DispatchMotion() {}

    public static final class Transition {
        private double from, target;
        private long started, duration;

        public Transition(double initial) { from = target = Math.clamp(initial, 0, 1); }

        public double value(double next, long now, boolean reduced, long millis) {
            next = Math.clamp(next, 0, 1);
            if (reduced || millis <= 0) { from = target = next; duration = 0; return next; }
            if (target != next) {
                from = sample(now); target = next; started = now; duration = millis * 1_000_000L;
            }
            return sample(now);
        }

        public double sample(long now) {
            if (duration == 0) return target;
            return from + (target - from) * MapMotion.ease((now - started) / (double) duration);
        }
    }

    /** A changed value flashes once. Initial data and repeated identical snapshots never flash. */
    public static final class Change {
        private Object previous;
        private boolean initialized;
        private long changedAt;
        private boolean changed;

        public double observe(Object value, long now, boolean reduced, long millis) {
            if (initialized && !Objects.equals(previous, value)) { changedAt = now; changed = true; }
            previous = value; initialized = true;
            if (reduced || millis <= 0) { changed = false; return 0; }
            if (!changed) return 0;
            double progress = (now - changedAt) / (millis * 1_000_000.0);
            if (progress >= 1) { changed = false; return 0; }
            return 1 - MapMotion.ease(progress);
        }
    }

    public static int tint(int from, int to, double amount) {
        return MapPalette.argb(MapPalette.mix(MapPalette.red(from), MapPalette.red(to), amount) << 16
                | MapPalette.mix(MapPalette.green(from), MapPalette.green(to), amount) << 8
                | MapPalette.mix(MapPalette.blue(from), MapPalette.blue(to), amount),
                MapPalette.mix(MapPalette.alpha(from), MapPalette.alpha(to), amount));
    }
}
