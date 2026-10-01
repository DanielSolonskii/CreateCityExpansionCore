package com.example.examplemod.railway;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import com.example.examplemod.status.CcecStatus;

import java.util.UUID;

public record RailwayTrainSnapshot(UUID fleetTrainId, String name, RailwayTypes.TrainType type,
                                   @Nullable UUID activeLineId, String lineName, String statusKey,
                                   boolean cityOwned, boolean automationSuspended,
                                   @Nullable ResourceLocation dimension, double x, double z,
        String nextStation, double speed, CcecStatus uiStatus) {}
