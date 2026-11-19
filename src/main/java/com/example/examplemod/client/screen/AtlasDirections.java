package com.example.examplemod.client.screen;

import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.journey.JourneyClient;
import com.example.examplemod.client.logistics.LogisticsMapOverlay;
import com.example.examplemod.client.logistics.StationDirections;
import com.example.examplemod.client.map.CityMapPanel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Transient mode of a pinned card. Journey still owns the sole navigation objective. */
final class AtlasDirections {
    private final UUID target;
    private final CityMapPanel.SelectionKind kind;
    private final ResourceLocation dimension;
    private final UUID world;
    private long revision = -1;
    private long nextMoveUpdate;
    private BlockPos origin, destination;
    private StationDirections.Nearby nearby;

    AtlasDirections(CityMapPanel.Selection selection, ResourceLocation dimension) {
        this.target = selection.id();
        this.kind = selection.kind();
        this.dimension = dimension;
        this.world = CityPublicDirectory.worldId(dimension);
    }

    boolean matches(CityMapPanel.Selection selection, ResourceLocation dim) {
        return selection != null && world != null && world.equals(CityPublicDirectory.worldId(dim))
                && dimension.equals(dim) && kind == selection.kind() && target != null && target.equals(selection.id());
    }

    void update(BlockPos player, BlockPos goal, long now) {
        long next = LogisticsMapOverlay.directoryRevision();
        if (!LogisticsMapOverlay.hasDirectory(dimension)) { nearby = null; return; }
        if (nearby != null && next == revision && (origin == null) == (player == null) &&
                (now < nextMoveUpdate || Objects.equals(origin, player) && goal.equals(destination))) return;
        nearby = LogisticsMapOverlay.nearbyStations(dimension, player, goal);
        origin = player;
        destination = goal;
        revision = next;
        nextMoveUpdate = now + 500;
    }

    private StationDirections.Nearby current() {
        return revision == LogisticsMapOverlay.directoryRevision() && LogisticsMapOverlay.hasDirectory(dimension) ? nearby : null;
    }

    Component description() {
        var options = current();
        String message = options == null ? "directions_wait" : options.destination() == null ? "directions_empty"
                : origin == null ? "directions_other_dimension" : "directions_known";
        return PolishedWorldMapScreen.atlasText(message);
    }

    List<AtlasObjectCard.Row> rows(CityMapPanel panel, CityMapPanel.Selection value) {
        var rows = new ArrayList<AtlasObjectCard.Row>(4);
        rows.add(new AtlasObjectCard.Row(AtlasObjectCard.Action.DIRECT,
                JourneyClient.canTrack(panel, dimension, value) ? "" : "target_unavailable"));
        var options = current();
        if (options != null) {
            addStation(rows, panel, AtlasObjectCard.Action.ORIGIN_STATION, options.origin());
            addStation(rows, panel, AtlasObjectCard.Action.DESTINATION_STATION, options.destination());
        }
        rows.add(new AtlasObjectCard.Row(AtlasObjectCard.Action.BACK_TO_OBJECT, ""));
        return List.copyOf(rows);
    }

    private void addStation(List<AtlasObjectCard.Row> rows, CityMapPanel panel, AtlasObjectCard.Action action,
                            StationDirections.Landmark station) {
        if (station == null) return;
        var selection = LogisticsMapOverlay.resolveMapSelection(station.id(), dimension);
        rows.add(new AtlasObjectCard.Row(action,
                JourneyClient.canTrack(panel, dimension, selection) ? "" : "target_unavailable", station));
    }
}
