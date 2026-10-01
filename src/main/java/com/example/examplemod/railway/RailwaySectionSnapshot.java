package com.example.examplemod.railway;

import java.util.UUID;

public record RailwaySectionSnapshot(UUID sectionId,UUID networkId,double length,RailwayOwnershipTypes.SectionStatus status) {}
