package com.example.examplemod.railway;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A Create train that exists in the world but has no CCEC registration yet.
 *
 * <p>Until a train is registered it is invisible to routes, tariffs, ownership and freight
 * dispatch, so the UI surfaces these as an explicit "register" action list rather than hiding
 * them behind a management page the player has to discover.
 *
 * @param createTrainId Create-side identity; this is also the map selection id.
 * @param stationName   station the train currently sits at, empty when none.
 * @param canManage     the viewer holds city build rights (server-computed).
 */
public record RailwayTrainCandidate(UUID createTrainId, String name,
                                    @Nullable ResourceLocation dimension, double x, double z,
                                    String stationName, boolean canManage) {}
