package com.example.examplemod.city;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.UUID;

public record CityProject(
        UUID projectId,
        UUID cityId,
        CityProjectType projectType,
        ResourceLocation dimensionId,
        BlockPos controllerPos,
        UUID creatorId,
        ResourceLocation depositId,
        ResourceLocation resourceId,
        double richness
) {
    public CityProject {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(cityId, "cityId");
        Objects.requireNonNull(projectType, "projectType");
        Objects.requireNonNull(dimensionId, "dimensionId");
        controllerPos = Objects.requireNonNull(controllerPos, "controllerPos").immutable();
        Objects.requireNonNull(creatorId, "creatorId");
        Objects.requireNonNull(depositId, "depositId");
        Objects.requireNonNull(resourceId, "resourceId");
        if (!Double.isFinite(richness) || richness <= 0.0 || richness > 1.0) {
            throw new IllegalArgumentException("richness must be finite and in (0, 1]");
        }
    }
}
