package com.example.examplemod.railway;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class CreateRailwayAdapter {
    private CreateRailwayAdapter() {}

    @Nullable
    public static GlobalStation station(UUID stationId) {
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            GlobalStation station = graph.getPoint(EdgePointType.STATION, stationId);
            if (station != null) return station;
        }
        return null;
    }

    @Nullable public static Train train(UUID trainId) { return Create.RAILWAYS.trains.get(trainId); }
    public static boolean hasUniqueScheduleName(UUID stationId, String name) {
        if (name.indexOf('*') >= 0) return false;
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                if (!station.id.equals(stationId) && station.name.equals(name)) return false;
            }
        }
        return true;
    }
    @Nullable
    public static GlobalStation stationAt(ResourceKey<Level> dimension, BlockPos pos) {
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            for (GlobalStation station : graph.getPoints(EdgePointType.STATION)) {
                if (station.getBlockEntityDimension().equals(dimension)
                        && station.getBlockEntityPos().equals(pos)) {
                    return station;
                }
            }
        }
        return null;
    }
    public static ResourceLocation dimension(GlobalStation station) {
        return station.getBlockEntityDimension().location();
    }
}
