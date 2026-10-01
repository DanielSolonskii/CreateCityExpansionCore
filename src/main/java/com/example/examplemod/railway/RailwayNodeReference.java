package com.example.examplemod.railway;

import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record RailwayNodeReference(ResourceLocation dimension, int x, int y, int z, int yOffsetPixels) {
    public static RailwayNodeReference of(TrackNodeLocation value) {
        return new RailwayNodeReference(value.getDimension().location(), value.getX(), value.getY(), value.getZ(), value.yOffsetPixels);
    }
    public TrackNodeLocation resolve() {
        TrackNodeLocation value = new TrackNodeLocation(new Vec3(x / 2.0, y / 2.0, z / 2.0));
        value.yOffsetPixels = yOffsetPixels;
        return value.in(ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, dimension));
    }
}
