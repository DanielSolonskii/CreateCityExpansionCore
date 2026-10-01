package com.example.examplemod.railway;

import java.util.UUID;

public final class OwnedRailwayNetwork {
    private final UUID id;
    private final RailwayOwnershipTypes.OwnerType ownerType;
    private final UUID ownerId;
    private String name;
    private RailwayOwnershipTypes.Access access;
    private long freightTariff;
    public OwnedRailwayNetwork(UUID id,RailwayOwnershipTypes.OwnerType ownerType,UUID ownerId,String name,RailwayOwnershipTypes.Access access,long freightTariff){
        this.id=id;this.ownerType=ownerType;this.ownerId=ownerId;this.name=name;this.access=access;this.freightTariff=Math.max(0,freightTariff);
    }
    public UUID id(){return id;} public RailwayOwnershipTypes.OwnerType ownerType(){return ownerType;} public UUID ownerId(){return ownerId;}
    public String name(){return name;} public RailwayOwnershipTypes.Access access(){return access;} public long freightTariff(){return freightTariff;}
    public void rename(String value){if(value!=null&&!value.isBlank())name=value.substring(0,Math.min(64,value.length()));}
    public void access(RailwayOwnershipTypes.Access value){access=value;} public void freightTariff(long value){freightTariff=Math.max(0,value);}
}
