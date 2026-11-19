package com.example.examplemod.journey;

import com.example.examplemod.cargo.*;
import com.example.examplemod.cargo.center.*;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.city.CityMapSavedData;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.projects.ProjectManager;
import com.example.examplemod.projects.ProjectNetworkHandlers;
import com.simibubi.create.content.trains.RailwaySavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.UUID;

/** A single authorized observation, independent of open containers, camera and map subscriptions. */
final class JourneyObjectLookup {
    private JourneyObjectLookup() {}

    static CompoundTag read(ServerPlayer player, NavigationLookup query) {
        var reply = new CompoundTag();
        reply.putString("NavigationQuery", query.token());
        var server = player.getServer();
        var id = query.id();
        switch (query.kind()) {
            case CITY -> {
                var city = CityManager.get(server.overworld()).getCity(id);
                if (city != null) position(player, reply, city.getPylonDimension(), city.getPylonPos(), city.getName());
            }
            case INDUSTRY -> {
                var city = CityManager.get(server.overworld()).getMemberCity(player.getUUID());
                if (city != null) city.getProjects().stream().filter(p -> p.projectId().equals(id)).findFirst()
                        .ifPresent(p -> position(player, reply, p.dimensionId(), p.controllerPos(), ""));
            }
            case PROJECT -> {
                var cities = CityManager.get(server.overworld());
                var actor = ProjectNetworkHandlers.actor(player, cities, cities.getMemberCity(player.getUUID()));
                var manager = EconomyManager.get(server.overworld()).projects();
                var project = manager.get(id);
                if (project != null && ProjectManager.visibleTo(actor, project)) {
                    var zone = project.zone();
                    position(player, reply, zone.dimension(), new BlockPos(Math.floorDiv(zone.x1() + zone.x2(), 2), 0,
                            Math.floorDiv(zone.z1() + zone.z2(), 2)), project.title());
                }
            }
            case POINT -> {
                var point = CargoPointRegistry.find(server, id).orElse(null);
                if (point != null && CenterView.visiblePoint(player, id))
                    point.routers().stream().findFirst().ifPresent(r -> position(player, reply, r.dimension(), r.pos(), point.name()));
            }
            case STATION -> {
                var platform=com.example.examplemod.passenger.PassengerService.stop(player,id);
                if(platform!=null){
                    position(player,reply,platform.dimension(),platform.entrance(),platform.name());
                    break;
                }
                var point = CargoLineRegistry.get(server).stations().get(id);
                if (point != null && CenterView.visiblePoint(player, point)) {
                    var place = CargoRailConnections.station(server, id);
                    if (place != null) position(player, reply, place.station().getBlockEntityDimension().location(),
                            place.station().getBlockEntityPos(), place.station().name);
                }
            }
            case WAREHOUSE -> CargoWarehouseRegistry.get(server).find(id)
                    .filter(w -> CargoWarehouseRegistry.permits(player, w))
                    .ifPresent(w -> position(player, reply, w.ticker().dimension(), w.ticker().pos(), w.name()));
            case TRAIN -> CargoTrainRegistry.find(server, id)
                    .filter(t -> CargoOwners.train(server, OwnerRef.player(player.getUUID()), t))
                    .ifPresent(t -> trainPosition(player, reply, id, t.displayName()));
            case SHIPMENT -> {
                var shipment = CargoShipmentRegistry.get(server).forParcel(id).orElse(null);
                if (ShipmentInspection.permits(player,shipment)) {
                    var parcel = shipment.navigationObservation(id);
                    if (parcel.hasUUID("Id")) {
                        var name = shipment.type().getHoverName().getString();
                        var place = ShipmentInspection.location(player,parcel,point -> CenterView.visiblePoint(player,point));
                        if (place != null) {
                            position(player, reply, place.dimension(), place.pos(), name);
                            reply.putBoolean("LastKnown", true);
                        }
                        if (reply.getBoolean("Visible") && parcel.getInt("TotalLegs") > 0
                                && ShipmentInspection.mayShowProgress(player,shipment)) {
                            reply.putInt("TotalLegs", parcel.getInt("TotalLegs"));
                            reply.putInt("CompletedLegs", parcel.getInt("CompletedLegs"));
                        }
                    }
                }
            }
            case PLACE -> {
                var place=com.example.examplemod.places.PlaceService.resolve(player,id);
                if(place!=null) position(player,reply,place.dimension(),place.icon(),place.name());
            }
            case OFFER, DEMAND, INTAKE -> {
                String kind=switch(query.kind()){case OFFER->"offer"; case DEMAND->"trade_contract"; default->"intake";};
                var row=PublicTradeMap.lookup(player,kind,id);
                if(row!=null && row.contains("Location")){
                    var loc=CargoInfrastructure.Location.load(row.getCompound("Location"));
                    position(player,reply,loc.dimension(),loc.pos(),row.getString("Name"));
                }
            }
            case ROAD -> EconomyManager.get(server.overworld()).roadTolls().road(id).filter(RailTollBook.Road::active)
                    .ifPresent(road -> { reply.putBoolean("Visible", true); reply.putString("Title", road.name()); });
        }
        return reply;
    }

    private static void trainPosition(ServerPlayer player, CompoundTag reply, UUID id, String name) {
        var train = RailwaySavedData.load(player.getServer()).getTrains().get(id);
        if (train == null) return;
        for (var dimension : train.getPresentDimensions()) {
            var pos = train.getPositionInDimension(dimension);
            if (pos.isPresent()) {
                position(player, reply, dimension.location(), pos.get(), name);
                return;
            }
        }
    }

    private static void position(ServerPlayer player, CompoundTag reply, ResourceLocation dim, BlockPos pos, String title) {
        if (Math.abs((long)pos.getX()) > 30_000_000 || Math.abs((long)pos.getZ()) > 30_000_000) return;
        var level = player.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dim));
        if (level == null) return;
        reply.putBoolean("Visible", true);
        reply.putString("Dimension", dim.toString());
        reply.putUUID("World", CityMapSavedData.get(level).worldId());
        reply.putLong("Position", pos.asLong());
        reply.putString("Title", title);
    }
}
