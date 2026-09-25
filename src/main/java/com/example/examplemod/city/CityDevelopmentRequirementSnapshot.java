package com.example.examplemod.city;

import net.minecraft.resources.ResourceLocation;

public record CityDevelopmentRequirementSnapshot(
        CityDevelopmentRequirements.RequirementGroup group,
        ResourceLocation itemId,
        long current,
        long required
) {
    public CityDevelopmentRequirementSnapshot {
        if (group == null || itemId == null || current < 0L || required <= 0L) {
            throw new IllegalArgumentException("Invalid development requirement snapshot");
        }
        current = Math.min(current, required);
    }

    public boolean complete() {
        return current >= required;
    }

    public double progress() {
        return Math.min(1.0, (double) current / required);
    }
}
