package com.example.examplemod.railway;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record RailwayStationSnapshot(UUID registrationId, String name, RailwayTypes.StationType type,
                                     boolean available, boolean cityOwned, ResourceLocation dimension,
                                     BlockPos pos, @Nullable UUID linkedProjectId) {}
