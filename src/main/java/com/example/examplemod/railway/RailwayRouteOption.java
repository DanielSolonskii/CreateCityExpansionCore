package com.example.examplemod.railway;
import java.util.List;
import java.util.UUID;
public record RailwayRouteOption(UUID id,RailwayRouteStrategy strategy,UUID graphId,UUID destinationStationId,
                                 boolean backwards,List<RailwayEdgeReference> edges,double distance,long tariffCost,
                                 long estimatedTicks,List<RailwayTariffLeg> contributions,long topologyFingerprint){
    public RailwayRouteOption{edges=List.copyOf(edges);contributions=List.copyOf(contributions);}
    public String pathKey(){return edges.toString();}
}
