package com.example.examplemod.city;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The colours a city may paint its territory with.
 *
 * <p><b>A fixed palette, not a random RGB.</b> An arbitrary {@code 0x000000..0xFFFFFF} pick
 * produces territories that are invisible (near-black on dark terrain), blinding (near-white),
 * or acid (full-saturation green/purple) — and the player has no way to tell which until they open
 * the map. A curated list of mid-luminance, clearly separated hues keeps every territory readable
 * and keeps neighbouring cities distinguishable at a glance.
 *
 * <p><b>Defaults are derived, never rolled.</b> {@link #defaultColor(UUID)} hashes the city UUID
 * into the palette, so a legacy city that has never chosen a colour keeps the same colour across
 * every restart. Anything using {@code UUID.randomUUID()} here would repaint the whole world on
 * each launch.
 */
public final class CityColors {

    private CityColors() {
    }

    /**
     * Preset territory colours, {@code 0xRRGGBB}, in the order the palette UI shows them.
     *
     * <p>Picked for contrast against the dark map terrain rather than for maximum saturation: each
     * one lands in a comfortable mid-luminance band, and consecutive entries differ in hue rather
     * than in brightness so the order does not look like a gradient.
     */
    public static final List<Integer> PALETTE = List.of(
            0xD0483C, // brick red
            0xD0793A, // amber
            0xD6B23C, // gold
            0x9BB53F, // olive
            0x4FA845, // green
            0x2FA37C, // teal green
            0x2E9BB5, // cyan
            0x3A78C4, // blue
            0x4A5CC0, // indigo
            0x6B4FB5, // violet
            0x9346A8, // purple
            0xC0437E, // raspberry
            0x8C5A3C, // brown
            0x5C7C99, // steel blue
            0x4C7F6B, // sea green
            0xB08A3E  // bronze
    );

    /** Read-only view; the palette must not be mutable at runtime. */
    public static final List<Integer> PRESETS = Collections.unmodifiableList(PALETTE);

    /**
     * Colour a city gets when its save carries none.
     *
     * <p>Derived from the city UUID, so it is stable across restarts and identical for every tool
     * that asks. Unsigned remainder keeps the index in range for negative hashes.
     */
    public static int defaultColor(UUID cityId) {
        if (cityId == null) {
            return PALETTE.get(0);
        }
        long mixed = cityId.getMostSignificantBits() ^ cityId.getLeastSignificantBits();
        return PALETTE.get((int) Long.remainderUnsigned(mixed, PALETTE.size()));
    }

    /**
     * Тот же выбор из палитры по произвольному стабильному ключу.
     *
     * <p>Нужен там, где города ещё нет — например для показа цвета на
     * пустом пилоне, чтобы вкладка «Территория» не выглядела поломанной.
     * Ключом служат координаты участка, поэтому и тут цвет не прыгает
     * от открытия к открытию.
     */
    public static int defaultColor(long stableKey) {
        return PALETTE.get((int) Long.remainderUnsigned(stableKey, PALETTE.size()));
    }

    /** Whether {@code rgb} is one of the offered presets. The only colours the server accepts. */
    public static boolean isPreset(int rgb) {
        return PALETTE.contains(normalize(rgb));
    }

    /**
     * Drops any alpha byte and clamps to the 24-bit range.
     *
     * <p>Applied on every read so a malformed save or a hand-edited packet cannot put an out of
     * range int into a renderer.
     */
    public static int normalize(int rgb) {
        return rgb & 0xFFFFFF;
    }

    /** {@code 0xRRGGBB} as {@code 0xAARRGGBB} with the supplied alpha. */
    public static int argb(int rgb, int alpha) {
        return ((alpha & 0xFF) << 24) | normalize(rgb);
    }

    /**
     * Blend a colour towards white so a border stays visible on a light fill of the same hue.
     *
     * <p>A border drawn in the exact same RGB as the fill disappears against it; lifting the border
     * keeps the outline readable without introducing a second data colour per city.
     */
    public static int lift(int rgb, int amount) {
        int red = normalize(rgb) >> 16 & 0xFF;
        int green = normalize(rgb) >> 8 & 0xFF;
        int blue = normalize(rgb) & 0xFF;
        int clamped = Math.max(0, Math.min(255, amount));
        red = red + (255 - red) * clamped / 255;
        green = green + (255 - green) * clamped / 255;
        blue = blue + (255 - blue) * clamped / 255;
        return (red << 16) | (green << 8) | blue;
    }

    /** Black-outlined version of a colour, for text drawn on top of a fill. */
    public static int darken(int rgb, int amount) {
        int red = normalize(rgb) >> 16 & 0xFF;
        int green = normalize(rgb) >> 8 & 0xFF;
        int blue = normalize(rgb) & 0xFF;
        int clamped = Math.max(0, Math.min(255, amount));
        return ((red - red * clamped / 255) << 16)
                | ((green - green * clamped / 255) << 8)
                | (blue - blue * clamped / 255);
    }
}
