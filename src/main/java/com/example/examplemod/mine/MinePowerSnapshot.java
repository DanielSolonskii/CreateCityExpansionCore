package com.example.examplemod.mine;

public record MinePowerSnapshot(
        boolean connected,
        boolean sufficient,
        double voltage,
        double current,
        double power,
        double requiredPower
) {

    public MinePowerSnapshot {
        if (!Double.isFinite(voltage) || voltage < 0.0
                || !Double.isFinite(current) || current < 0.0
                || !Double.isFinite(power) || power < 0.0
                || !Double.isFinite(requiredPower) || requiredPower <= 0.0) {
            throw new IllegalArgumentException("Power values must be finite and non-negative");
        }
        if (!connected && sufficient) {
            throw new IllegalArgumentException("Disconnected power cannot be sufficient");
        }
    }
}
