package com.example.examplemod.hud;

import net.minecraft.world.BossEvent.BossBarColor;

/** Nearest vanilla bar hue in perceptual Lab space; never changes the stored city colour. */
public final class TerritoryBossColors {
    private TerritoryBossColors() {}
    // Bright foreground row sampled from Minecraft 1.21.1 boss_bar/*_progress.png, not text colours.
    private static final int[] RGB = {0xEC00B8, 0x00B7EC, 0xEC3500, 0x1DEC00, 0xE9EC00, 0x7B00EC, 0xECECEC};
    private static final double[][] LAB = java.util.Arrays.stream(RGB).mapToObj(TerritoryBossColors::lab).toArray(double[][]::new);
    public static BossBarColor nearest(int rgb) {
        double[] input = lab(rgb);
        int best = 0;
        double distance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < LAB.length; i++) {
            double delta = 0;
            for (int j = 0; j < 3; j++) delta += Math.pow(input[j] - LAB[i][j], 2);
            if (delta < distance) { best = i; distance = delta; }
        }
        return BossBarColor.values()[best];
    }
    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4);
    }
    private static double curve(double value) {
        return value > .008856 ? Math.cbrt(value) : 7.787 * value + 16.0 / 116.0;
    }
    private static double[] lab(int rgb) {
        double r = linear(rgb >> 16 & 255), g = linear(rgb >> 8 & 255), b = linear(rgb & 255);
        double x = curve((.4124564*r + .3575761*g + .1804375*b) / .95047);
        double y = curve(.2126729*r + .7151522*g + .0721750*b);
        double z = curve((.0193339*r + .1191920*g + .9503041*b) / 1.08883);
        return new double[]{116*y - 16, 500*(x-y), 200*(y-z)};
    }
}
