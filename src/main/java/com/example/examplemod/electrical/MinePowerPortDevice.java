package com.example.examplemod.electrical;

import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import com.george_vi.electroenergetics.devices.device.SimulatedDevice;
import com.george_vi.electroenergetics.devices.device.SimulatedDeviceType;
import com.george_vi.electroenergetics.foundation.device.SimpleElectricalDevice;
import com.george_vi.electroenergetics.simulation.BridgeCollector;
import com.george_vi.electroenergetics.simulation.SimulationResults;

import com.example.examplemod.mine.MinePowerAccess;
import com.example.examplemod.mine.MinePowerDemand;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

public class MinePowerPortDevice extends SimpleElectricalDevice {

    private final Level level;

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
        this.level = level;
    }

    @Override
    public void preTick(BridgeCollector bridge) {

        double requestedPower = MinePowerDemand.workingLoad(level, pos)
                ? MinePowerAccess.NOMINAL_POWER_WATTS
                : MinePowerAccess.STANDBY_POWER_WATTS;
        double resistance = MinePowerAccess.NOMINAL_VOLTAGE
                * MinePowerAccess.NOMINAL_VOLTAGE / requestedPower;
        bridge.builder(pos).resistor(0, 1, resistance);
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
