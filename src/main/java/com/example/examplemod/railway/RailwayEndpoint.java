package com.example.examplemod.railway;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

public record RailwayEndpoint(UUID markerId, ResourceLocation markerDimension, BlockPos markerPos,
                              ResourceLocation trackDimension, BlockPos trackPos, boolean front,
                              UUID graphId, RailwayEdgeReference edge, double position) {}
