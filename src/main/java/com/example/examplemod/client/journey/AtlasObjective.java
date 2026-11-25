package com.example.examplemod.client.journey;

import com.example.examplemod.client.CityPublicDirectory;
import com.example.examplemod.client.logistics.LogisticsMapOverlay;
import com.example.examplemod.client.map.CityMapPanel;
import com.example.examplemod.journey.JourneyModel;
import com.example.examplemod.journey.NavigationLookup;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import java.util.Locale;
import java.util.UUID;

/** The single map objective contains detached values. No screen, panel, level or menu is retained. */
final class AtlasObjective {
    final CityMapPanel.SelectionKind kind;
    final UUID id;
    final long selectedAt = Util.getMillis();
    final NavigationLease lease;
    final JourneyModel.Marker marker;
    JourneyModel.Point itineraryDestination;
    ResourceLocation dimension;
    UUID world;
    boolean lastKnown;
    com.example.examplemod.navigation.TripNetwork.Query trip;
    long observed;
    net.minecraft.network.chat.Component instruction;
    boolean recalculate;
    int completedLegs, totalLegs;

    private AtlasObjective(CityMapPanel.Selection selection, ResourceLocation dim, UUID world,
                           NavigationLookup.Kind lookup) {
        kind = selection.kind();
        id = selection.id();
        this.world = world;
        dimension = dim;
        var trade = kind==CityMapPanel.SelectionKind.LOGISTICS ? com.example.examplemod.client.logistics.AtlasTradeDirectory.row(id,dim) : null;
        lease = lookup == null ? null : new NavigationLease(lookup, trade==null?id:trade.object(), selectedAt);
        marker = new JourneyModel.Marker("atlas_goal", point(dim, selection.pos()));
        marker.title = selection.title().getString();
        marker.kind = JourneyModel.Kind.LANDMARK;
        marker.inWorld = false;
        lastKnown = kind == CityMapPanel.SelectionKind.LOGISTICS
                && "LAST_OBSERVED".equals(LogisticsMapOverlay.markerStatus(id, dim));
    }

    static CityMapPanel.Selection resolve(CityMapPanel panel, ResourceLocation dim, CityMapPanel.Selection candidate) {
        if (panel == null || candidate == null || dim == null || !dim.equals(panel.dimensionId())
                || CityPublicDirectory.worldId(dim) == null || candidate.kind() == CityMapPanel.SelectionKind.PLAYER)
            return null;
        var value = panel.resolveSelection(candidate.kind(), candidate.id(), candidate.pos());
        return value != null && value.id() != null && Math.abs((long)value.pos().getX()) <= 30_000_000
                && Math.abs((long)value.pos().getZ()) <= 30_000_000 && (value.kind() == CityMapPanel.SelectionKind.WAYPOINT
                || source(value, dim) != null) ? value : null;
    }

    static AtlasObjective create(CityMapPanel.Selection value, ResourceLocation dim) {
        return new AtlasObjective(value, dim, CityPublicDirectory.worldId(dim), source(value, dim));
    }

    static NavigationLookup.Kind source(CityMapPanel.Selection value, ResourceLocation dim) {
        return switch (value.kind()) {
            case CARTOGRAPHY -> NavigationLookup.Kind.CARTOGRAPHY;
            case PLACE -> NavigationLookup.Kind.PLACE;
            case CITY -> NavigationLookup.Kind.CITY;
            case PROJECT -> NavigationLookup.Kind.PROJECT;
            case INDUSTRY -> NavigationLookup.Kind.INDUSTRY;
            case LOGISTICS -> {
                String source = LogisticsMapOverlay.markerKind(value.id(), dim);
                NavigationLookup.Kind found = null;
                if (source != null && !source.isEmpty()) {
                    try { found = "trade_contract".equals(source)?NavigationLookup.Kind.DEMAND:NavigationLookup.Kind.valueOf(source.toUpperCase(Locale.ROOT)); }
                    catch (IllegalArgumentException unknown) { /* Unsupported marker has no lookup. */ }
                }
                yield found;
            }
            default -> null;
        };
    }

    boolean matches(ResourceLocation dim, CityMapPanel.Selection selection) {
        return selection != null && kind == selection.kind() && id.equals(selection.id()) && dimension.equals(dim);
    }

    static AtlasObjective itinerary(CityMapPanel.Selection value, ResourceLocation dim,
                                    com.example.examplemod.navigation.TripNetwork.Query query) {
        var goal=new AtlasObjective(value,dim,query.world(),null);
        goal.trip=query;
        goal.itineraryDestination=goal.marker.point;
        return goal;
    }

    boolean readable() {
        if (trip != null) return Util.getMillis() - observed < 5000;
        return lease == null || lease.confirmed(Util.getMillis());
    }

    boolean apply(CompoundTag reply) {
        if (lease == null || !lease.accept(reply.getString("NavigationQuery"), Util.getMillis(), reply.getBoolean("Visible")))
            return true;
        if (!reply.getBoolean("Visible")) return false;
        if (reply.hasUUID("World")) {
            var dim = ResourceLocation.tryParse(reply.getString("Dimension"));
            if (dim == null || dimension.equals(dim) && !world.equals(reply.getUUID("World"))) return false;
            dimension = dim;
            world = reply.getUUID("World");
            marker.point = point(dim, BlockPos.of(reply.getLong("Position")));
        }
        if (!reply.getString("Title").isBlank()) marker.title = reply.getString("Title");
        lastKnown = reply.getBoolean("LastKnown");
        totalLegs = reply.getInt("TotalLegs");
        completedLegs = reply.getInt("CompletedLegs");
        return true;
    }

    private static JourneyModel.Point point(ResourceLocation dim, BlockPos pos) {
        return new JourneyModel.Point(dim.toString(), pos.getX(), pos.getY(), pos.getZ());
    }
}
