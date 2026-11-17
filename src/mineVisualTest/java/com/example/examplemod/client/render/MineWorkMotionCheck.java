package com.example.examplemod.client.render;

import com.example.examplemod.mine.IndustrialMineState;
import com.example.examplemod.mine.MineElectricalState;

public final class MineWorkMotionCheck {
    public static void main(String[] args) {
        for (var state : IndustrialMineState.values()) for (var power : MineElectricalState.values()) {
            if (MineWorkMotion.running(false,state,power)) throw new AssertionError("Incomplete machine moving");
            if (MineWorkMotion.running(true,state,power)
                    != (state == IndustrialMineState.RUNNING && power == MineElectricalState.NORMAL))
                throw new AssertionError("Wrong working gate: " + state + "/" + power);
        }
        var motion = new MineWorkMotion();
        for (int i=0;i<121;i++) {
            motion.tick(true);
            if (Math.abs(motion.angle(1)-motion.angle(0)-6)>1e-8) throw new AssertionError("Phase wrap jumps");
            if (Math.abs(motion.angle(.5f)-motion.angle(0)-3)>1e-8) throw new AssertionError("Interpolation");
        }
        double stopped=motion.angle(1);
        for(int i=0;i<100;i++) {
            motion.tick(false);
            if (Math.abs(motion.angle(0)-stopped)>1e-8 || Math.abs(motion.angle(1)-stopped)>1e-8)
                throw new AssertionError("Stopped auger changes angle");
        }
        System.out.println("PASS: all mine/power states; pause, frame interpolation and phase wrap.");
    }
}
