package com.example.examplemod.client.map;

/** A single interruptible camera transition. Time is monotonic, coordinates are in blocks. */
final class MapCameraMotion {
    record Pose(double x, double z, double zoom) {}
    private enum Mode { STILL, FOCUS, ZOOM, COAST }
    private static final double WHEEL_STEP = 1.25;
    private static final long ZOOM_TIME = 130_000_000L, FOCUS_TIME = 180_000_000L, COAST_TIME = 140_000_000L;
    private Mode mode = Mode.STILL;
    private Pose from, to;
    private long started, lastDrag;
    private double anchorX, anchorZ, offsetX, offsetY;
    private double velocityX, velocityZ, distanceX, distanceZ;

    void stop() {
        mode = Mode.STILL;
        velocityX = velocityZ = distanceX = distanceZ = 0;
        lastDrag = 0;
    }

    void focus(Pose current, Pose target, long now) {
        stop();
        from = current;
        to = target;
        started = now;
        mode = Mode.FOCUS;
    }

    static double followAxis(double center, double target, double margin, double blend) {
        double delta = target-center;
        return center + (delta-Math.clamp(delta,-margin,margin))*blend;
    }

    Pose scroll(Pose current, double worldX, double worldZ, double dx, double dy,
                double wheel, boolean animate, long now) {
        double base = mode == Mode.ZOOM ? to.zoom : current.zoom;
        double target = AtlasMapScale.clamp(base * Math.pow(WHEEL_STEP, wheel));
        stop();
        anchorX = worldX;
        anchorZ = worldZ;
        offsetX = dx;
        offsetY = dy;
        from = current;
        to = atZoom(target);
        if (!animate || target == current.zoom) return to;
        started = now;
        mode = Mode.ZOOM;
        return current;
    }

    private Pose atZoom(double zoom) {
        return new Pose(anchorX-offsetX/zoom, anchorZ-offsetY/zoom, zoom);
    }

    void beginDrag(long now) {
        stop();
        lastDrag = now;
    }

    void cancelCoast() {
        if (mode == Mode.COAST) stop();
    }

    Pose drag(Pose current, double dx, double dy, long now) {
        double x = -dx / current.zoom, z = -dy / current.zoom;
        distanceX += x;
        distanceZ += z;
        double seconds = (now-lastDrag) / 1e9;
        if (seconds > 0) {
            double blend = MapMotion.blend(seconds, 48);
            double limit = 1200 / current.zoom;
            velocityX += (Math.clamp(distanceX/seconds, -limit, limit)-velocityX)*blend;
            velocityZ += (Math.clamp(distanceZ/seconds, -limit, limit)-velocityZ)*blend;
            distanceX = distanceZ = 0;
            lastDrag = now;
        }
        return new Pose(current.x+x, current.z+z, current.zoom);
    }

    void release(Pose current, long now, boolean coast) {
        if (!coast || now-lastDrag > 80_000_000L || Math.hypot(velocityX,velocityZ)*current.zoom < 8) {
            stop();
            return;
        }
        from = current;
        started = now;
        mode = Mode.COAST;
    }

    Pose sample(long now) {
        if (mode == Mode.STILL) return null;
        long duration = switch (mode) {
            case ZOOM -> ZOOM_TIME;
            case COAST -> COAST_TIME;
            default -> FOCUS_TIME;
        };
        long elapsed = now-started;
        double t = MapMotion.ease(elapsed/(double)duration);
        Pose result;
        if (mode == Mode.COAST) {
            // Integral of v0*(1-t)^2. No frame delta or frame-count cut-off.
            double seconds = t * (COAST_TIME/3e9);
            result = new Pose(from.x+velocityX*seconds, from.z+velocityZ*seconds, from.zoom);
        } else if (elapsed >= duration) result = to;
        else {
            double zoom = from.zoom * Math.pow(to.zoom/from.zoom, t);
            result = mode == Mode.ZOOM ? atZoom(zoom)
                    : new Pose(from.x+(to.x-from.x)*t, from.z+(to.z-from.z)*t, zoom);
        }
        if (elapsed >= duration) stop();
        return result;
    }
}
