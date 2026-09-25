package com.example.examplemod.railway;

public final class RailwayTypes {
    private RailwayTypes() {}

    public enum StationType { PASSENGER, FREIGHT, INDUSTRIAL, MIXED, DEPOT }
    public enum TrainType { FREIGHT, PASSENGER, SERVICE, MIXED }
    public enum LineType { FREIGHT, PASSENGER, MIXED }
    public enum StopMode { CARGO_IDLE, TIMED, IMMEDIATE }
}
