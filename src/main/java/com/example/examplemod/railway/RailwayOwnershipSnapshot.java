package com.example.examplemod.railway;

import java.util.UUID;

public record RailwayOwnershipSnapshot(UUID networkId,String name,RailwayOwnershipTypes.OwnerType ownerType,
                                       RailwayOwnershipTypes.Access access,long freightTariff,int sections,
                                       int onlineSections,double totalLength) {}
