package com.example.examplemod.client.logistics;

import java.util.Collection;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Landmarks from a single authorized map directory. A pair of landmarks makes no service claim. */
public final class StationDirections {
    public record Landmark(UUID id, String name, BlockPos pos, double distanceSquared) {
        public long distance() { return Math.round(Math.sqrt(distanceSquared)); }
    }
    public record Nearby(Landmark origin, Landmark destination) {}

    public static Nearby nearest(Collection<CompoundTag> directory, ResourceLocation dimension,
                                 BlockPos origin, BlockPos destination) {
        Landmark nearOrigin = null;
        Landmark nearDestination = null;
        int visited = 0;
        for (var marker : directory) {
            if (++visited > 256) break; // CargoMapView's existing directory budget
            if (!marker.hasUUID("Id") || !"station".equals(marker.getString("Kind"))) continue;
            var location = marker.getCompound("Location");
            if (!dimension.toString().equals(location.getString("Dimension")) || !location.contains("Pos", Tag.TAG_LONG)) continue;
            var pos = BlockPos.of(location.getLong("Pos"));
            var id = marker.getUUID("Id");
            var name = marker.getString("Name");
            if (origin != null) nearOrigin = nearest(nearOrigin, id, name, pos, origin);
            nearDestination = nearest(nearDestination, id, name, pos, destination);
        }
        return new Nearby(nearOrigin, nearDestination);
    }

    private static Landmark nearest(Landmark best, UUID id, String name, BlockPos station, BlockPos endpoint) {
        double dx = (double) station.getX() - endpoint.getX();
        double dz = (double) station.getZ() - endpoint.getZ();
        double distance = dx * dx + dz * dz;
        if (best != null && (distance > best.distanceSquared ||
                distance == best.distanceSquared && id.compareTo(best.id) >= 0)) return best;
        return new Landmark(id, name, station, distance);
    }

    private StationDirections() {}
}
