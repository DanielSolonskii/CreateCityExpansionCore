package com.example.examplemod.economy;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public final class EconomyInfrastructureRegistration {
    private final UUID registrationId;
    private final UUID physicalInstanceId;
    private final UUID registeredBy;
    private final EconomyTypes.InfrastructureType type;
    private UUID cityId;
    private ResourceLocation dimension;
    private BlockPos pos;
    private boolean available;
    private UUID stationRegistrationId;
    private EconomyTypes.IntakeAttributionError lastAttributionError=EconomyTypes.IntakeAttributionError.NONE;
    private long lastAttributionErrorAt;

    public EconomyInfrastructureRegistration(UUID registrationId, UUID physicalInstanceId, UUID registeredBy,
                                             EconomyTypes.InfrastructureType type, ResourceLocation dimension,
                                             BlockPos pos) {
        this.registrationId = Objects.requireNonNull(registrationId);
        this.physicalInstanceId = Objects.requireNonNull(physicalInstanceId);
        this.registeredBy = Objects.requireNonNull(registeredBy);
        this.type = Objects.requireNonNull(type);
        this.dimension = Objects.requireNonNull(dimension);
        this.pos = Objects.requireNonNull(pos).immutable();
        this.available = true;
    }

    public boolean bind(UUID bindingCity) {
        if (bindingCity == null || cityId != null) return false;
        cityId = bindingCity;
        return true;
    }

    public void locate(ResourceLocation newDimension, BlockPos newPos) {
        dimension = Objects.requireNonNull(newDimension);
        pos = Objects.requireNonNull(newPos).immutable();
        available = true;
    }

    public void setAvailable(boolean value) { available = value; }
    public void bindStation(UUID value){stationRegistrationId=value;lastAttributionError=EconomyTypes.IntakeAttributionError.NONE;lastAttributionErrorAt=0L;}
    public void attributionError(EconomyTypes.IntakeAttributionError value,long now){lastAttributionError=Objects.requireNonNull(value);lastAttributionErrorAt=Math.max(0L,now);}

    void restore(UUID restoredCity, boolean restoredAvailable, UUID stationId,
                 EconomyTypes.IntakeAttributionError error,long errorAt) {
        cityId = restoredCity;
        available = restoredAvailable;
        stationRegistrationId=stationId;
        lastAttributionError=error==null?EconomyTypes.IntakeAttributionError.NONE:error;
        lastAttributionErrorAt=Math.max(0L,errorAt);
    }

    public UUID registrationId() { return registrationId; }
    public UUID physicalInstanceId() { return physicalInstanceId; }
    public UUID registeredBy() { return registeredBy; }
    public EconomyTypes.InfrastructureType type() { return type; }
    public UUID cityId() { return cityId; }
    public ResourceLocation dimension() { return dimension; }
    public BlockPos pos() { return pos; }
    public boolean available() { return available; }
    public UUID stationRegistrationId(){return stationRegistrationId;}
    public EconomyTypes.IntakeAttributionError lastAttributionError(){return lastAttributionError;}
    public long lastAttributionErrorAt(){return lastAttributionErrorAt;}
}
