package com.example.examplemod.railway;

public record RailwayEdgeReference(RailwayNodeReference first, RailwayNodeReference second) {
    public RailwayEdgeReference normalized() {
        return key(first).compareTo(key(second)) <= 0 ? this : new RailwayEdgeReference(second, first);
    }
    private static String key(RailwayNodeReference n) {
        return n.dimension()+":"+n.x()+":"+n.y()+":"+n.z()+":"+n.yOffsetPixels();
    }
}
