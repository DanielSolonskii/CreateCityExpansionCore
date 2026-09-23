package com.example.examplemod.electrical;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class MinePowerPortDevice extends SimpleElectricalDevice {

    /**
     * Сопротивление нагрузки шахты.
     *
     * При 24 V:
     *
     * P = V² / R
     * P = 24² / 4.8
     * P = 120 W
     */
    private static final double RESISTANCE = 4.8;

    private double voltage;
    private double current;
    private double power;

    public MinePowerPortDevice(
            Level level,
            BlockPos pos,
            DevicesSavedData deviceSD,
            SimulatedDeviceType<?> type
    ) {
        super(level, pos, deviceSD, type);
    }

    @Override
    public void preTick(BridgeCollector bridge) {

        bridge.builder(pos)
                .resistor(
                        0,
                        1,
                        RESISTANCE
                );
    }

    @Override
    public void postTick(SimulationResults results) {

        double voltage0 =
                results.getVoltageAt(pos, 0);

        double voltage1 =
                results.getVoltageAt(pos, 1);

        voltage =
                Math.abs(voltage0 - voltage1);

        current =
                Math.abs(
                        results.getCurrentThrough(
                                pos,
                                0,
                                1
                        )
                );

        power =
                voltage * current;
    }

    public double getVoltage() {
        return voltage;
    }

    public double getCurrent() {
        return current;
    }

    public double getPower() {
        return power;
    }
}