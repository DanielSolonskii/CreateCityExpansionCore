package com.example.examplemod.railway;

import java.util.List;
import java.util.UUID;

public final class RailwayTripLedger {
    public enum Status { ACTIVE, COMPLETED, INTERRUPTED, ROUTE_INVALIDATED }
    private final UUID id, createTrainId, fleetTrainId, payerId, sourceStationId, destinationStationId;
    private final RailwayOwnershipTypes.OwnerType payerType;
    private final double plannedDistance;
    private final long reservedAmount, startedAt;
    private final List<RailwayTariffLeg> legs;
    private double actualDistance;
    private long settledAt;
    private Status status;

    public RailwayTripLedger(UUID id, UUID createTrainId, UUID fleetTrainId,
                             RailwayOwnershipTypes.OwnerType payerType, UUID payerId,
                             UUID sourceStationId, UUID destinationStationId, double plannedDistance,
                             long reservedAmount, long startedAt, List<RailwayTariffLeg> legs) {
        this(id, createTrainId, fleetTrainId, payerType, payerId, sourceStationId, destinationStationId,
                plannedDistance, reservedAmount, startedAt, legs, 0, 0, Status.ACTIVE);
    }

    RailwayTripLedger(UUID id, UUID createTrainId, UUID fleetTrainId,
                      RailwayOwnershipTypes.OwnerType payerType, UUID payerId,
                      UUID sourceStationId, UUID destinationStationId, double plannedDistance,
                      long reservedAmount, long startedAt, List<RailwayTariffLeg> legs,
                      double actualDistance, long settledAt, Status status) {
        this.id=id;this.createTrainId=createTrainId;this.fleetTrainId=fleetTrainId;this.payerType=payerType;this.payerId=payerId;
        this.sourceStationId=sourceStationId;this.destinationStationId=destinationStationId;
        this.plannedDistance=Math.max(0,plannedDistance);this.reservedAmount=Math.max(0,reservedAmount);
        this.startedAt=startedAt;this.legs=List.copyOf(legs);this.actualDistance=Math.max(0,Math.min(this.plannedDistance,actualDistance));
        this.settledAt=settledAt;this.status=status;
    }
    public UUID id(){return id;} public UUID createTrainId(){return createTrainId;} public UUID fleetTrainId(){return fleetTrainId;}
    public RailwayOwnershipTypes.OwnerType payerType(){return payerType;} public UUID payerId(){return payerId;}
    public UUID sourceStationId(){return sourceStationId;} public UUID destinationStationId(){return destinationStationId;}
    public double plannedDistance(){return plannedDistance;} public double actualDistance(){return actualDistance;}
    public long reservedAmount(){return reservedAmount;} public long startedAt(){return startedAt;} public long settledAt(){return settledAt;}
    public List<RailwayTariffLeg> legs(){return legs;} public Status status(){return status;} public boolean active(){return status==Status.ACTIVE;}
    public boolean progress(double value){if(!active())return false;double next=Math.max(actualDistance,Math.min(plannedDistance,Math.max(0,value)));if(next==actualDistance)return false;actualDistance=next;return true;}
    public boolean close(Status terminal,long now){if(!active()||terminal==Status.ACTIVE)return false;status=terminal;settledAt=now;return true;}
    public long actualCharge(){long total=0;for(RailwayTariffLeg leg:legs)total=Math.addExact(total,leg.charge(leg.usedLength(actualDistance)));return total;}
}
