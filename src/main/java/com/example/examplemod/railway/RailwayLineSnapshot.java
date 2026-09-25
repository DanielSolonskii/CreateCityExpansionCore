package com.example.examplemod.railway;

import java.util.List;
import java.util.UUID;

public record RailwayLineSnapshot(UUID lineId, String name, RailwayTypes.LineType type,
                                  boolean cyclic, int revision, List<RailLineStop> stops,
                                  String statusKey, int assignedTrains) {
    public RailwayLineSnapshot { stops = List.copyOf(stops); }
}
