package com.example.examplemod.client.render;

import com.example.examplemod.mine.IndustrialMineState;
import com.example.examplemod.mine.MineElectricalState;

/** Tick-based phase: render frame rate never changes the speed or the stopped position. */
public final class MineWorkMotion {
    private double angle, previous;
    public static boolean running(boolean assembled, IndustrialMineState state, MineElectricalState power) {
        return assembled && state == IndustrialMineState.RUNNING && power == MineElectricalState.NORMAL;
    }
    public void tick(boolean running) {
        // Normalize both endpoints together, preserving interpolation through 360 degrees.
        angle %= 360;
        previous = angle;
        if (running) angle += 6;
    }
    public double angle(float partial) { return previous + (angle - previous) * partial; }
}
