package com.example.examplemod.mine;

import java.util.Objects;

@FunctionalInterface
public interface MineEnergyGate {

    EnergyDecision evaluate();

    static MineEnergyGate phaseBManualTest() {
        return () -> EnergyDecision.allowedPendingIntegration(
                "Ручная добыча Phase B: фактическое потребление EE ещё не подключено."
        );
    }

    static MineEnergyGate allowed(String message) {
        return () -> new EnergyDecision(
                true,
                IndustrialMineState.NO_POWER,
                false,
                message
        );
    }

    record EnergyDecision(
            boolean allowed,
            IndustrialMineState deniedState,
            boolean integrationPending,
            String message
    ) {
        public EnergyDecision {
            Objects.requireNonNull(deniedState, "deniedState");
            Objects.requireNonNull(message, "message");

            if (!allowed
                    && deniedState != IndustrialMineState.NO_POWER
                    && deniedState != IndustrialMineState.POWER_INTEGRATION_PENDING
                    && deniedState != IndustrialMineState.ERROR) {
                throw new IllegalArgumentException(
                        "A denied energy decision requires an energy-related state"
                );
            }
        }

        public static EnergyDecision allowedPendingIntegration(String message) {
            return new EnergyDecision(
                    true,
                    IndustrialMineState.POWER_INTEGRATION_PENDING,
                    true,
                    message
            );
        }

        public static EnergyDecision denied(
                IndustrialMineState state,
                String message
        ) {
            return new EnergyDecision(false, state, false, message);
        }
    }
}
