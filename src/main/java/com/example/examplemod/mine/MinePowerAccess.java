package com.example.examplemod.mine;

import com.example.examplemod.electrical.MinePowerPortDevice;
import com.george_vi.electroenergetics.devices.device.DevicesSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public final class MinePowerAccess {

    public static final double NOMINAL_VOLTAGE = 500.0;
    public static final double MIN_VOLTAGE = 500.0;
    public static final double MAX_VOLTAGE = 600.0;
    public static final double NOMINAL_POWER_WATTS = 10_000.0;
    public static final double STANDBY_POWER_WATTS = 120.0;
    public static final double MIN_CURRENT = 17.0;
    public static final double MAX_CURRENT = 26.0;
    public static final double MIN_WORKING_POWER_WATTS = 8_500.0;
    public static final double MAX_SAFE_POWER_WATTS = 15_000.0;

    private MinePowerAccess() {
    }

    static MinePowerSnapshot read(
            ServerLevel level,
            BlockPos powerPortPos,
        boolean workingLoad
    ) {
        if (powerPortPos == null) {
            return unavailable(workingLoad);
        }

        MinePowerPortDevice device = DevicesSavedData.load(level).getDevice(
                powerPortPos,
                MinePowerPortDevice.class
        );
        if (device == null || !device.isValid()) {
            return unavailable(workingLoad);
        }

        double voltage = finiteNonNegative(device.getVoltage());
        double current = finiteNonNegative(device.getCurrent());
        double power = finiteNonNegative(device.getPower());
        MineElectricalState electricalState = classify(
                true,
                voltage,
                current,
                power,
                workingLoad
        );
        return new MinePowerSnapshot(
                true,
                electricalState == MineElectricalState.NORMAL,
                voltage,
                current,
                power,
                workingLoad ? NOMINAL_POWER_WATTS : STANDBY_POWER_WATTS
        );
    }

    static MineElectricalState classify(MinePowerSnapshot power, boolean workingLoad) {
        return classify(
                power.connected(),
                power.voltage(),
                power.current(),
                power.power(),
                workingLoad
        );
    }

    private static MineElectricalState classify(
            boolean connected,
            double voltage,
            double current,
            double power,
            boolean workingLoad
    ) {
        if (!connected || voltage <= 0.01) {
            return MineElectricalState.NO_POWER;
        }
        if (voltage > MAX_VOLTAGE) {
            return MineElectricalState.OVERVOLTAGE;
        }
        if (current > MAX_CURRENT || power > MAX_SAFE_POWER_WATTS) {
            return MineElectricalState.OVERLOAD;
        }
        if (voltage < MIN_VOLTAGE) {
            return MineElectricalState.LOW_VOLTAGE;
        }
        if (workingLoad
                && (current < MIN_CURRENT || power < MIN_WORKING_POWER_WATTS)) {
            return MineElectricalState.INSUFFICIENT_POWER;
        }
        return MineElectricalState.NORMAL;
    }

    static boolean isUnsafe(MineElectricalState state) {
        return state == MineElectricalState.OVERVOLTAGE
                || state == MineElectricalState.OVERLOAD;
    }

    static MineEnergyGate gate(MinePowerSnapshot power) {
        if (!power.connected()) {
            return () -> MineEnergyGate.EnergyDecision.denied(
                    IndustrialMineState.NO_POWER,
                    "Электрический порт не подключён к сети Electro Energetics."
            );
        }
        if (!power.sufficient()) {
            return () -> MineEnergyGate.EnergyDecision.denied(
                    IndustrialMineState.NO_POWER,
                    "Недостаточная мощность Electro Energetics: %.1f / %.1f Вт"
                            .formatted(power.power(), power.requiredPower())
            );
        }
        return MineEnergyGate.allowed(
                "Питание Electro Energetics подтверждено."
        );
    }

    private static MinePowerSnapshot unavailable(boolean workingLoad) {
        return new MinePowerSnapshot(
                false,
                false,
                0.0,
                0.0,
                0.0,
                workingLoad ? NOMINAL_POWER_WATTS : STANDBY_POWER_WATTS
        );
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }
}
