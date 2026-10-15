package com.example.examplemod.client.map;

/** Frame-rate independent presentation math; never changes world data. */
public final class MapMotion {
    private MapMotion() {}
    public static double blend(double seconds, double response) {
        return 1 - Math.exp(-Math.clamp(seconds, 0, .05) * response);
    }
    public static double angle(double current, double target, double seconds) {
        double delta = ((target - current) % 360 + 540) % 360 - 180;
        return current + delta * blend(seconds, 24);
    }
    public static double ease(double progress) {
        double t = Math.clamp(progress, 0, 1);
        return 1 - Math.pow(1 - t, 3);
    }
}
