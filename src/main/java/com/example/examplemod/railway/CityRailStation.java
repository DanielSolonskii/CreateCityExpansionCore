package com.example.examplemod.railway;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

public final class CityRailStation {
    private final UUID registrationId;
    private UUID createStationId;
    private final UUID registeredBy;
    @Nullable private UUID cityId;
    private RailwayTypes.StationType type;
    private String lastKnownName;
    private ResourceLocation lastKnownDimension;
    private BlockPos lastKnownPos;

    public CityRailStation(UUID registrationId, UUID createStationId, UUID registeredBy,
                           @Nullable UUID cityId, RailwayTypes.StationType type,
                           String lastKnownName, ResourceLocation lastKnownDimension,
                           BlockPos lastKnownPos) {
        this.registrationId = Objects.requireNonNull(registrationId);
        this.createStationId = Objects.requireNonNull(createStationId);
        this.registeredBy = Objects.requireNonNull(registeredBy);
        this.cityId = cityId;
        this.type = Objects.requireNonNull(type);
        this.lastKnownName = Objects.requireNonNullElse(lastKnownName, "");
        this.lastKnownDimension = Objects.requireNonNull(lastKnownDimension);
        this.lastKnownPos = Objects.requireNonNull(lastKnownPos).immutable();
    }

    public UUID registrationId() { return registrationId; }
    public UUID createStationId() { return createStationId; }
    public UUID registeredBy() { return registeredBy; }
    @Nullable public UUID cityId() { return cityId; }
    public RailwayTypes.StationType type() { return type; }
    public String lastKnownName() { return lastKnownName; }
    public ResourceLocation lastKnownDimension() { return lastKnownDimension; }
    public BlockPos lastKnownPos() { return lastKnownPos; }
    public void setCityId(@Nullable UUID cityId) { this.cityId = cityId; }
    public void setType(RailwayTypes.StationType type) { this.type = Objects.requireNonNull(type); }
    public void updateSnapshot(String name, ResourceLocation dimension, BlockPos pos) {
        this.lastKnownName = Objects.requireNonNullElse(name, "");
        this.lastKnownDimension = Objects.requireNonNull(dimension);
        this.lastKnownPos = Objects.requireNonNull(pos).immutable();
    }
    public void rebind(UUID createStationId, String name, ResourceLocation dimension, BlockPos pos) {
        this.createStationId = Objects.requireNonNull(createStationId);
        updateSnapshot(name, dimension, pos);
    }
}
