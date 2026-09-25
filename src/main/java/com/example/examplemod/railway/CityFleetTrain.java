package com.example.examplemod.railway;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class CityFleetTrain {
    private final UUID fleetTrainId;
    private UUID createTrainId;
    private final UUID registeredBy;
    @Nullable private UUID cityId;
    @Nullable private UUID activeLineId;
    private RailwayTypes.TrainType type;
    private String lastKnownName;
    private int compiledLineRevision;
    private String compiledScheduleFingerprint;
    private boolean automationSuspended;

    public CityFleetTrain(UUID fleetTrainId, UUID createTrainId, UUID registeredBy,
                          @Nullable UUID cityId, @Nullable UUID activeLineId,
                          RailwayTypes.TrainType type, String lastKnownName,
                          int compiledLineRevision, String fingerprint,
                          boolean automationSuspended) {
        this.fleetTrainId = Objects.requireNonNull(fleetTrainId);
        this.createTrainId = Objects.requireNonNull(createTrainId);
        this.registeredBy = Objects.requireNonNull(registeredBy);
        this.cityId = cityId;
        this.activeLineId = activeLineId;
        this.type = Objects.requireNonNull(type);
        this.lastKnownName = Objects.requireNonNullElse(lastKnownName, "");
        this.compiledLineRevision = Math.max(0, compiledLineRevision);
        this.compiledScheduleFingerprint = Objects.requireNonNullElse(fingerprint, "");
        this.automationSuspended = automationSuspended;
    }

    public UUID fleetTrainId() { return fleetTrainId; }
    public UUID createTrainId() { return createTrainId; }
    public UUID registeredBy() { return registeredBy; }
    @Nullable public UUID cityId() { return cityId; }
    @Nullable public UUID activeLineId() { return activeLineId; }
    public RailwayTypes.TrainType type() { return type; }
    public String lastKnownName() { return lastKnownName; }
    public int compiledLineRevision() { return compiledLineRevision; }
    public String compiledScheduleFingerprint() { return compiledScheduleFingerprint; }
    public boolean automationSuspended() { return automationSuspended; }
    public void setCityId(@Nullable UUID cityId) { this.cityId = cityId; }
    public void setType(RailwayTypes.TrainType type) { this.type = Objects.requireNonNull(type); }
    public void setLastKnownName(String name) { this.lastKnownName = Objects.requireNonNullElse(name, ""); }
    public void rebind(UUID createTrainId, String name) { this.createTrainId = Objects.requireNonNull(createTrainId); setLastKnownName(name); }
    public void assign(UUID lineId, int revision, String fingerprint) {
        this.activeLineId = lineId; this.compiledLineRevision = revision;
        this.compiledScheduleFingerprint = fingerprint; this.automationSuspended = false;
    }
    public void unassign() {
        activeLineId = null; compiledLineRevision = 0;
        compiledScheduleFingerprint = ""; automationSuspended = false;
    }
    public void setAutomationSuspended(boolean value) { automationSuspended = value; }
    public void updateCompilation(int revision, String fingerprint) {
        compiledLineRevision = revision; compiledScheduleFingerprint = fingerprint;
    }
}
