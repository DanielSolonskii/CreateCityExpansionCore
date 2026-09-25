package com.example.examplemod.city;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

public record CityProjectSnapshot(
        UUID projectId,
        CityProjectType projectType,
        ResourceLocation dimensionId,
        BlockPos controllerPos,
        UUID creatorId,
        ResourceLocation resourceId,
        double richness,
        String status
) {
}
