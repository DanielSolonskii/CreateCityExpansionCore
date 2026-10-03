package com.example.examplemod.geology;

/**
 * Детерминированный шум и стабильное хеширование геологии.
 *
 * Все значения выводятся только из world seed, идентификатора
 * измерения и координат macro-cell. Никакого обращения к
 * Minecraft-миру, чанкам или сохранённому состоянию: один и тот же
 * seed всегда даёт одну и ту же геологию, независимо от того,
 * какая часть мира уже была посещена.
 */
final class GeologyNoise {

    private GeologyNoise() {
    }

    /** SplitMix64-подобное перемешивание, устойчивое к переполнению. */
    static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    static long mix(long seed, long salt) {
        return mix(seed ^ (salt * 0x9E3779B97F4A7C15L));
    }

    static long mix(long seed, long a, long b) {
        return mix(mix(seed, a), b);
    }

    static long mix(long seed, long a, long b, long c) {
        return mix(mix(seed, a, b), c);
    }

    /** Стабильное 64-битное значение решётки. */
    static long lattice(long seed, int x, int y) {
        return mix(mix(seed, x), y);
    }

    /** Равномерное значение [0, 1) из 64-битного хеша. */
    static double unit01(long hash) {
        return (hash >>> 11) * 0x1.0p-53;
    }

    static double range(long hash, double min, double max) {
        return min + (max - min) * unit01(hash);
    }

    /**
     * Гладкий двумерный value-noise на кубической решётке
     * с билинейной интерполяцией и сглаживанием step 3t^2-2t^3
     * по обеим осям.
     *
     * Функция непрерывна вместе с первой производной, поэтому
     * потенциал месторождения меняется плавно: у соседних блоков
     * не может быть скачка 8000 -> 500.
     *
     * Ограничение по производной: |d value / d block| <= 1.5 / scale.
     */
    static double value(long seed, double x, double y) {
        int x0 = (int) Math.floor(x);
        int y0 = (int) Math.floor(y);
        double fx = x - x0;
        double fy = y - y0;

        double ux = fx * fx * (3.0 - 2.0 * fx);
        double uy = fy * fy * (3.0 - 2.0 * fy);

        double v00 = unit01(lattice(seed, x0, y0));
        double v10 = unit01(lattice(seed, x0 + 1, y0));
        double v01 = unit01(lattice(seed, x0, y0 + 1));
        double v11 = unit01(lattice(seed, x0 + 1, y0 + 1));

        double top = v00 + (v10 - v00) * ux;
        double bottom = v01 + (v11 - v01) * ux;
        return top + (bottom - top) * uy;
    }
}