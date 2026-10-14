package com.example.examplemod.railway;

public final class RailwayOwnershipTypes {
    private RailwayOwnershipTypes() {}
    public enum OwnerType { PLAYER, CITY }
    public enum Access { PUBLIC, PRIVATE }
    public enum SectionStatus { ONLINE, TRACK_MISSING, MARKER_MISSING, GRAPH_CHANGED }
}
