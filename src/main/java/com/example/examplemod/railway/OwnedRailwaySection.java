package com.example.examplemod.railway;

import java.util.List;
import java.util.UUID;

public final class OwnedRailwaySection {
    private final UUID id, networkId;
    private RailwayEndpoint first, second;
    private List<RailwayEdgeReference> path;
    private double length;
    private RailwayOwnershipTypes.SectionStatus status;
    public OwnedRailwaySection(UUID id, UUID networkId, RailwayEndpoint first, RailwayEndpoint second,
                               List<RailwayEdgeReference> path, double length, RailwayOwnershipTypes.SectionStatus status) {
        this.id=id;this.networkId=networkId;this.first=first;this.second=second;this.path=List.copyOf(path);this.length=length;this.status=status;
    }
    public UUID id(){return id;} public UUID networkId(){return networkId;} public RailwayEndpoint first(){return first;}
    public RailwayEndpoint second(){return second;} public List<RailwayEdgeReference> path(){return path;}
    public double length(){return length;} public RailwayOwnershipTypes.SectionStatus status(){return status;}
    public void reconciled(RailwayEndpoint a,RailwayEndpoint b,List<RailwayEdgeReference> p,double l,RailwayOwnershipTypes.SectionStatus s){first=a;second=b;path=List.copyOf(p);length=l;status=s;}
    public void status(RailwayOwnershipTypes.SectionStatus value){status=value;}
}
