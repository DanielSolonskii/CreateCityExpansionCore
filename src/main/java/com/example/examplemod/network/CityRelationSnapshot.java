package com.example.examplemod.network;

import java.util.UUID;

public record CityRelationSnapshot(UUID cityId, String cityName, Status status, boolean territoryAccess, boolean incomingTerritoryAccess) {
    public CityRelationSnapshot(UUID cityId,String cityName,Status status){this(cityId,cityName,status,false,false);}
    public enum Status { NONE, TRUSTED, TRUSTS_US, PARTNER }
}
