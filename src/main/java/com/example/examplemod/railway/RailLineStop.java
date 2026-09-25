package com.example.examplemod.railway;

import java.util.Objects;
import java.util.UUID;

public record RailLineStop(UUID stationRegistrationId, RailwayTypes.StopMode mode, int waitSeconds) {
    public static final int MAX_WAIT_SECONDS = 86_400;

    public RailLineStop {
        Objects.requireNonNull(stationRegistrationId);
        Objects.requireNonNull(mode);
        waitSeconds = mode == RailwayTypes.StopMode.TIMED
                ? Math.clamp(waitSeconds, 0, MAX_WAIT_SECONDS) : 0;
    }
}
