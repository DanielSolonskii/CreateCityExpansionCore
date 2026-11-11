package com.example.examplemod.client.logistics;

import java.util.function.IntPredicate;

/** A physical key press may activate only one control, including across rebuilt screens. */
final class LogisticsActivationLatch {
    private int held;

    boolean press(int key) {
        int bit = bit(key);
        if (bit == 0 || (held & bit) != 0) return false;
        held |= bit;
        return true;
    }

    boolean release(int key) {
        int bit = bit(key);
        boolean wasHeld = (held & bit) != 0;
        held &= ~bit;
        return wasHeld;
    }

    /** Releases may occur while the game or another type of screen owns keyboard input. */
    void reconcile(IntPredicate physicallyDown) {
        if ((held & 1) != 0 && !physicallyDown.test(257)) held &= ~1;
        if ((held & 2) != 0 && !physicallyDown.test(335)) held &= ~2;
        if ((held & 4) != 0 && !physicallyDown.test(32)) held &= ~4;
    }

    private static int bit(int key) {
        return switch (key) { case 257 -> 1; case 335 -> 2; case 32 -> 4; default -> 0; };
    }
}
