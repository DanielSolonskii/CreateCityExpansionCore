package com.example.examplemod.railway;

import java.util.List;
import java.util.UUID;

/** Immutable agreed Create path plus the persisted cursor used by RouteGuard. */
public final class RouteReservation {
    public enum Status { ACTIVE, COMPLETED, CANCELLED, ROUTE_INVALIDATED }
    public enum Observation { ON_ROUTE, ADVANCED, REROUTED, TOPOLOGY_CHANGED, CLOSED }
    private final UUID id, tripId, graphId;
    private final List<RailwayEdgeReference> orderedEdges;
    private final List<RailwayTariffLeg> tariffSnapshot;
    private int cursor;
    private long closedAt;
    private Status status;

    public RouteReservation(UUID id,UUID tripId,UUID graphId,List<RailwayEdgeReference> edges,List<RailwayTariffLeg> tariffs){
        this(id,tripId,graphId,edges,tariffs,0,0,Status.ACTIVE);
    }
    RouteReservation(UUID id,UUID tripId,UUID graphId,List<RailwayEdgeReference> edges,List<RailwayTariffLeg> tariffs,int cursor,long closedAt,Status status){
        if(edges.isEmpty())throw new IllegalArgumentException("Route reservation needs an edge");
        this.id=id;this.tripId=tripId;this.graphId=graphId;this.orderedEdges=List.copyOf(edges);this.tariffSnapshot=List.copyOf(tariffs);
        this.cursor=Math.max(0,Math.min(edges.size()-1,cursor));this.closedAt=closedAt;this.status=status;
    }
    public UUID id(){return id;}public UUID tripId(){return tripId;}public UUID graphId(){return graphId;}
    public List<RailwayEdgeReference> orderedEdges(){return orderedEdges;}public List<RailwayTariffLeg> tariffSnapshot(){return tariffSnapshot;}
    public int cursor(){return cursor;}public long closedAt(){return closedAt;}public Status status(){return status;}public boolean active(){return status==Status.ACTIVE;}
    public Observation observe(RailwayEdgeReference actual,boolean remainingTopologyExists){
        if(!active())return Observation.CLOSED;if(!remainingTopologyExists)return Observation.TOPOLOGY_CHANGED;
        RailwayEdgeReference normalized=actual.normalized();if(orderedEdges.get(cursor).equals(normalized))return Observation.ON_ROUTE;
        for(int i=cursor+1;i<orderedEdges.size();i++)if(orderedEdges.get(i).equals(normalized)){cursor=i;return Observation.ADVANCED;}
        return Observation.REROUTED;
    }
    public boolean close(Status terminal,long now){if(!active()||terminal==Status.ACTIVE)return false;status=terminal;closedAt=now;return true;}
}
