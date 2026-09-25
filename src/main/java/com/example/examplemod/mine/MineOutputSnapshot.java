package com.example.examplemod.mine;

public record MineOutputSnapshot(
        boolean available,
        int itemCount,
        int totalCapacity
) {

    public MineOutputSnapshot {
        if (itemCount < 0 || totalCapacity < 0 || itemCount > totalCapacity) {
            throw new IllegalArgumentException("Invalid gateway output counts");
        }
    }
}
