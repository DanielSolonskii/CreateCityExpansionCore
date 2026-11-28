package com.example.examplemod.cutscene;

/** Timeline effects shared by the camera, HUD and server chunk tracking. All durations are ticks. */
public final class CutscenePresentation {
    public static final int SKIP_LOCK_TICKS = 400;
    public static final int RETURN_TICKS = 40;
    public static final int FADE_TICKS = 10;

    private CutscenePresentation() {
    }

    public static int endTick(Cutscene scene) {
        if (scene.camera().isEmpty()) {
            return scene.durationTicks();
        }
        return Math.max(scene.durationTicks(), scene.camera().getLast().tick() + RETURN_TICKS);
    }

    public static double smooth(double progress) {
        double clamped = Math.clamp(progress, 0, 1);
        return clamped * clamped * (3 - 2 * clamped);
    }

    public static double returnProgress(Cutscene scene, double tick) {
        if (scene.camera().isEmpty()) {
            return 0;
        }
        int start = scene.camera().getLast().tick();
        return smooth((tick - start) / (endTick(scene) - start));
    }

    public static double fadeAlpha(Cutscene scene, double tick) {
        double alpha = 0;
        for (var frame : scene.camera()) {
            double offset = tick - frame.tick();
            if (frame.fadeOut() && offset >= -FADE_TICKS && offset <= 0) {
                alpha = Math.max(alpha, smooth(1 + offset / FADE_TICKS));
            }
            if (frame.fadeIn() && offset >= 0 && offset <= FADE_TICKS) {
                alpha = Math.max(alpha, 1 - smooth(offset / FADE_TICKS));
            }
        }
        return alpha;
    }

    public static double barsAmount(Cutscene scene, double tick) {
        int start = scene.camera().isEmpty() ? Math.max(0, endTick(scene) - RETURN_TICKS)
                : scene.camera().getLast().tick();
        for (var frame : scene.camera()) {
            if (frame.hideBars()) {
                start = frame.tick();
                break;
            }
        }
        return 1 - smooth((tick - start) / (endTick(scene) - start));
    }

    public static boolean canSkip(double elapsedTicks) {
        return elapsedTicks >= SKIP_LOCK_TICKS;
    }
}
