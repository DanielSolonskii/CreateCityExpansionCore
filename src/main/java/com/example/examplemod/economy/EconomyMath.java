package com.example.examplemod.economy;

public final class EconomyMath {
    private EconomyMath() {}

    public static long checkedPositive(long value, long maximum, String field) {
        if (value <= 0L || value > maximum) {
            throw new IllegalArgumentException(field + " is outside 1.." + maximum);
        }
        return value;
    }

    public static long addExactNonNegative(long current, long delta) {
        if (current < 0L || delta < 0L) throw new IllegalArgumentException("negative money");
        return Math.addExact(current, delta);
    }

    public static long multiplyExactPositive(long left, long right) {
        if (left <= 0L || right <= 0L) throw new IllegalArgumentException("non-positive multiplication");
        return Math.multiplyExact(left, right);
    }

    public static long applyBasisPoints(long value, int basisPoints) {
        if (value < 0L || basisPoints < 0) throw new IllegalArgumentException("negative basis points input");
        long quotient = value / 10_000L;
        long remainder = value % 10_000L;
        return Math.addExact(Math.multiplyExact(quotient, basisPoints),
                Math.multiplyExact(remainder, basisPoints) / 10_000L);
    }

    public static long saturatingAdd(long current, long delta, long cap) {
        if (current < 0L || delta < 0L || cap < 0L) return 0L;
        if (current >= cap || delta > cap - current) return cap;
        return current + delta;
    }
}
