package com.example.examplemod.city;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import java.util.UUID;

/** Public information only: never includes members, treasury, inventories or private infrastructure. */
public record CityPublicProfile(UUID id, String name, ResourceLocation dimension, BlockPos pos,
                                int colour, int residents, int plots, boolean own, boolean editable,
                                long revision, CityIdentity identity) {}
