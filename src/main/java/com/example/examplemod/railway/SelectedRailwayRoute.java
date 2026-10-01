package com.example.examplemod.railway;
import java.util.List;
import java.util.UUID;
public record SelectedRailwayRoute(UUID selectionId,UUID fleetTrainId,RailwayRouteOption option,long selectedAt){
    public SelectedRailwayRoute{option=new RailwayRouteOption(option.id(),option.strategy(),option.graphId(),option.destinationStationId(),option.backwards(),List.copyOf(option.edges()),option.distance(),option.tariffCost(),option.estimatedTicks(),List.copyOf(option.contributions()),option.topologyFingerprint());}
}
