package com.example.examplemod.passenger;

import com.example.examplemod.block.TransportProtection;
import com.example.examplemod.cargo.CargoTrainRegistry;
import com.example.examplemod.cargo.center.*;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.citymap.CityMapFeature;
import com.example.examplemod.citymap.CityMapService;
import com.example.examplemod.policy.GameplayRestrictions;
import com.mojang.authlib.GameProfile;
import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

@EventBusSubscriber(modid = "createcityexpansioncore")
public final class PassengerService {
    public static final int MAX_DESCRIPTION = 180;
    public record Stop(UUID id, String name, ResourceLocation dimension, BlockPos entrance, BlockPos stationPos, UUID feature) {}
    public record MapLine(UUID id, String name, String description, int color, List<UUID> stops, UUID train) {
        public MapLine(UUID id, String name, int color, List<UUID> stops, UUID train) {
            this(id, name, "", color, stops, train);
        }
    }

    /** Map publication is independent of ETA calibration. Only an active, owned native schedule is public. */
    public static List<MapLine> publicLines(ServerPlayer actor, ResourceLocation dimension) {
        var server = actor.getServer();
        var data = PassengerData.get(server);
        var lines = new ArrayList<MapLine>();
        for (var line : data.lines.values()) {
            if (line.train == null || line.stops.size() < 2) continue;
            var first = data.platforms.get(line.stops.getFirst());
            if (first == null || !dimension.equals(first.dimension())) continue;
            var train = active(server, line);
            if (train == null || !publishedStops(server, data, line, train) || !matchingNames(data,line,train)) continue;
            lines.add(new MapLine(line.id, line.name, line.description, line.color.getTextureDiffuseColor() & 0xffffff,
                    List.copyOf(line.stops), line.train));
        }
        return List.copyOf(lines);
    }
    public record Ride(UUID line, UUID generation, UUID train, UUID from, UUID to, String lineName,
                       String trainName, String fromName, String toName, ResourceLocation dimension,
                       BlockPos fromEntrance, BlockPos toEntrance, long departTick, long arriveTick,
                       UUID run, long fromVisit, long toVisit) {}
    public record Visit(long number, UUID station) {}
    public enum Boarding { READY, BLOCKED, UNKNOWN, CANCELLED }
    public static final int MAX_RIDES = PassengerData.MAX_LINES * 2 * PassengerTimetable.MAX_STOPS * (PassengerTimetable.MAX_STOPS - 1);
    private PassengerService() {}

    public static UUID riding(ServerPlayer player) {
        return player.getRootVehicle() instanceof CarriageContraptionEntity carriage ? carriage.trainId : null;
    }
    public static UUID stoppedAt(MinecraftServer server, UUID trainId) {
        var train = RailwaySavedData.load(server).getTrains().get(trainId);
        var stop = train == null || Math.abs(train.speed) > .001 ? null : train.getCurrentStation();
        return stop == null ? null : stop.id;
    }
    public static boolean assigned(MinecraftServer server, UUID trainId) {
        var data = PassengerData.get(server);
        return data.retainedAssignments.contains(trainId) || data.lines.values().stream().anyMatch(line -> trainId.equals(line.train));
    }
    public static boolean running(MinecraftServer server, UUID lineId, UUID trainId) {
        var line = PassengerData.get(server).lines.get(lineId);
        if (line == null || !trainId.equals(line.train) || line.clock == null) return false;
        long now = server.overworld().getGameTime();
        var train = active(server, line);
        return line.clock.measured(now) && platformsFresh(line,now) && train != null &&
                publishedStops(server,PassengerData.get(server),line,train);
    }
    public static boolean stillServes(MinecraftServer server, Ride ride) {
        var line = PassengerData.get(server).lines.get(ride.line());
        long t=server.overworld().getGameTime();
        if (line == null || line.clock == null || !line.clock.measured(t) || !platformsFresh(line,t) ||
                !operating(server,ride)) return false;
        var current = visit(server,ride);
        return current.number() >= 0 && (current.number() < ride.fromVisit() ||
                current.number() == ride.fromVisit() && ride.from().equals(current.station()));
    }
    public static Visit visit(MinecraftServer server, Ride ride) {
        var line = PassengerData.get(server).lines.get(ride.line());
        if (line == null || line.clock == null || !ride.run().equals(line.clock.run()) ||
                !ride.generation().equals(line.generation) || !ride.train().equals(line.train)) return new Visit(-1,null);
        UUID dock = stoppedAt(server,ride.train());
        int index = dock == null ? -1 : line.stops.indexOf(dock);
        return new Visit(dock != null && index < 0 ? -1 : line.clock.visitAt(index),dock);
    }
    public static boolean accessible(ServerPlayer actor, Ride ride) {
        // Passenger access is public. Running also revalidates who is allowed to publish every stop.
        return stillServes(actor.getServer(), ride);
    }
    /** Recheck just the selected visits when the traveler accepts an offer. */
    public static PassengerTimetable.Connection forecast(ServerPlayer actor, Ride ride) {
        if(!accessible(actor,ride))return null;
        var server=actor.getServer();
        var line=PassengerData.get(server).lines.get(ride.line());
        for(var c:line.clock.departures(server.overworld().getGameTime()))
            if(c.fromVisit()==ride.fromVisit() && c.toVisit()==ride.toVisit())return c;
        return null;
    }
    /** After actual boarding, release of an ETA or temporary loss of a boardable seat does not cancel the train. */
    public static boolean operating(ServerPlayer actor, Ride ride) {
        return operating(actor.getServer(),ride);
    }
    /** Imminent boarding is a physical check at this call, independent of ETA calibration. */
    public static Boarding boarding(ServerPlayer actor,Ride ride) {
        var server=actor.getServer();
        var visit=visit(server,ride);
        if(visit.number()!=ride.fromVisit() || !ride.from().equals(visit.station()) ||
                !actor.serverLevel().dimension().location().equals(ride.dimension()) || !operating(server,ride))
            return Boarding.CANCELLED;
        return Boarding.READY;
    }
    private static boolean operating(MinecraftServer server, Ride ride) {
        var data = PassengerData.get(server);
        var line = data.lines.get(ride.line());
        if (line == null || line.clock == null || !ride.run().equals(line.clock.run()) ||
                !line.verifiedDirection || !ride.generation().equals(line.generation) ||
                !ride.train().equals(line.train) || !line.stops.contains(ride.from()) || !line.stops.contains(ride.to())) return false;
        var train = active(server,line);
        if (train == null || !publishedStops(server,data,line,train)) return false;
        var from = data.platforms.get(ride.from());
        var to = data.platforms.get(ride.to());
        return ride.dimension().equals(from.dimension()) && ride.dimension().equals(to.dimension()) &&
                ride.fromEntrance().equals(from.entrance()) && ride.toEntrance().equals(to.entrance()) &&
                ride.fromName().equals(train.graph.getPoint(EdgePointType.STATION,from.id()).name) &&
                ride.toName().equals(train.graph.getPoint(EdgePointType.STATION,to.id()).name);
    }
    /** Public boarding points, including stations whose direction is in calibration or presently suspended. */
    public static Stop stop(ServerPlayer actor, UUID id) {
        var server = actor.getServer();
        return publicStop(server,PassengerData.get(server).platforms.get(id));
    }
    public static List<Stop> stops(ServerPlayer actor) {
        var server = actor.getServer();
        var entries = new ArrayList<Stop>();
        for (var p : PassengerData.get(server).platforms.values()) {
            var stop = publicStop(server,p);
            if (stop != null) entries.add(stop);
        }
        return List.copyOf(entries);
    }
    private static Stop publicStop(MinecraftServer server, PassengerData.Platform p) {
        if (p == null) return null;
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION,p.dimension()));
        if (level == null) return null;
        GlobalStation station;
        if (level.hasChunkAt(p.stationPos())) {
            var block = level.getBlockEntity(p.stationPos());
            station = block instanceof StationBlockEntity be ? be.getStation() : null;
        } else {
            var graph = RailwaySavedData.load(server).getTrackNetworks().get(p.graph());
            station = graph == null ? null : graph.getPoint(EdgePointType.STATION,p.id());
        }
        if (!published(server,p,station) || station.name.isBlank() || station.name.length() > 96) return null;
        return new Stop(p.id(),station.name,p.dimension(),p.entrance(),p.stationPos(),p.feature());
    }
    private static boolean publishedStops(MinecraftServer server, PassengerData data, PassengerData.Line line, Train train) {
        for (UUID stop : line.stops) {
            if (!published(server,data.platforms.get(stop),train.graph.getPoint(EdgePointType.STATION,stop))) return false;
        }
        return true;
    }
    private static boolean published(MinecraftServer server, PassengerData.Platform p, GlobalStation station) {
        if (p == null || station == null || station.assembling || !p.id().equals(station.id) ||
                !p.dimension().equals(station.getBlockEntityDimension().location()) ||
                !p.stationPos().equals(station.getBlockEntityPos())) return false;
        var level = server.getLevel(station.getBlockEntityDimension());
        var pos = station.getBlockEntityPos();
        if (level == null || level.hasChunkAt(pos) &&
                (!(level.getBlockEntity(pos) instanceof StationBlockEntity be) || be.getStation() == null ||
                        !station.id.equals(be.getStation().id))) return false;
        UUID owner = p.owner();
        var cities = CityManager.get(server.overworld());
        var stationCity = cities.getCityAt(p.dimension(),pos);
        var entranceCity = cities.getCityAt(p.dimension(),p.entrance());
        if (stationCity != null && stationCity.canBuild(owner) && (entranceCity == null || entranceCity.canBuild(owner))) return true;
        var host = server.getSingleplayerProfile();
        var profile = host != null && owner.equals(host.getId()) ? host : new GameProfile(owner,"PassengerOperator");
        return server.getProfilePermissions(profile) >= 2;
    }
    public static List<Ride> departures(ServerPlayer actor) {
        var server = actor.getServer();
        var data = PassengerData.get(server);
        var result = new ArrayList<Ride>();
        long now = server.overworld().getGameTime();
        for (var line : data.lines.values()) {
            if (line.clock == null || !line.clock.measured(now) || !platformsFresh(line,now)) continue;
            var train = active(server, line);
            if (train == null || !publishedStops(server,data,line,train)) continue;
            for (var c : line.clock.departures(now)) {
                var a = data.platforms.get(line.stops.get(c.from()));
                var b = data.platforms.get(line.stops.get(c.to()));
                var sa = train.graph.getPoint(EdgePointType.STATION, a.id());
                var sb = train.graph.getPoint(EdgePointType.STATION, b.id());
                if (sa != null && sb != null)
                    result.add(new Ride(line.id,line.generation,train.id,a.id(),b.id(),line.name,train.name.getString(),
                            sa.name,sb.name,a.dimension(),a.entrance(),b.entrance(),c.departure(),c.arrival(),
                            line.clock.run(),c.fromVisit(),c.toVisit()));
            }
        }
        return List.copyOf(result);
    }
    private static boolean platformsFresh(PassengerData.Line line, long now) {
        for (int i = 0; i < line.stops.size(); i++)
            if (line.verifiedPlatforms[i] <= 0 || now - line.verifiedPlatforms[i] > 24_000) return false;
        return true;
    }

    public static void platform(ServerPlayer player, boolean remove) {
        var data = writable(player);
        var station = lookedAt(player);
        if (remove) {
            withdrawPlatform(player,station.id);
            return;
        }
        var level = player.serverLevel();
        var pos = player.blockPosition();
        if (player.isPassenger() || !player.onGround() || !walkable(level, pos) ||
                !GameplayRestrictions.trainsAllowedIn(level) || !level.mayInteract(player, pos)) throw fail("platform_ground");
        var binding = CargoRailConnections.station(player.getServer(), station.id);
        if (binding == null) throw fail("look");
        publishPlatform(player, data, binding, pos);
    }

    /** Registers the physical rail stop at the saved map entrance without bringing any chunks into memory. */
    public static UUID registerCityStation(ServerPlayer actor, CityMapFeature selected) {
        var data = writable(actor);
        var feature = CityMapService.resolve(actor, selected.id());
        if (feature == null || feature.revision() != selected.revision()) throw fail("stale");
        if (feature.kind() != CityMapFeature.Kind.STATION || feature.entrances().isEmpty()) throw fail("station_entrance");
        var entrance = feature.entrances().getFirst();
        var level = actor.serverLevel();
        if (!feature.dimension().equals(level.dimension().location()) || !GameplayRestrictions.trainsAllowedIn(level))
            throw fail("unavailable");
        if (!walkable(level, entrance) || !level.mayInteract(actor, entrance)) throw fail("station_entrance");
        CargoRailConnections.Station binding = null;
        for (var graph : RailwaySavedData.load(actor.getServer()).getTrackNetworks().values()) {
            for (var station : graph.getPoints(EdgePointType.STATION)) {
                if (station.assembling || !level.dimension().equals(station.getBlockEntityDimension())) continue;
                var pos = station.getBlockEntityPos();
                if (Math.abs(pos.getY() - entrance.getY()) > 3 || pos.distSqr(entrance) > 64
                        || !feature.containsXZ(pos.getX(), pos.getZ())) continue;
                if (binding != null) throw fail("station_ambiguous");
                binding = new CargoRailConnections.Station(graph, station);
            }
        }
        if (binding == null) throw fail("station_missing");
        var station = binding.station();
        var pos = station.getBlockEntityPos();
        if (!level.hasChunkAt(pos) || !level.mayInteract(actor, pos)
                || !(level.getBlockEntity(pos) instanceof StationBlockEntity be) || be.getStation() == null
                || !station.id.equals(be.getStation().id) || station.name.isBlank() || station.name.length() > 96
                || station.name.contains("*") || station.name.contains("?")) throw fail("station_missing");
        CityMapService.stationForBinding(actor, feature.id(), feature.revision(), pos, entrance);
        var existing = data.platforms.get(station.id);
        if (existing != null && existing.feature() != null && !feature.id().equals(existing.feature())) throw fail("platform_bound");
        publishPlatform(actor, data, binding, entrance);
        if (existing == null || existing.feature() == null) {
            data.platforms.put(station.id, data.platforms.get(station.id).withFeature(feature.id()));
            data.setDirty();
            CityMapService.platformsChanged(actor,feature.id());
        }
        return station.id;
    }

    public static void bindCityPlatform(ServerPlayer actor, UUID feature, long revision, UUID stopId) {
        var data = writable(actor);
        var p = data.platforms.get(stopId);
        if (p == null || !p.dimension().equals(actor.level().dimension().location()) || publicStop(actor.getServer(),p) == null)
            throw fail("platform");
        CityMapService.stationForBinding(actor,feature,revision,p.stationPos(),p.entrance());
        if (feature.equals(p.feature())) return;
        if (p.feature()!=null) throw fail("platform_bound");
        data.platforms.put(stopId, p.withFeature(feature));
        data.setDirty();
        CityMapService.platformsChanged(actor,feature);
    }
    public static void unbindCityPlatform(ServerPlayer actor, UUID feature, long revision, UUID stopId) {
        var data = writable(actor);
        var p = data.platforms.get(stopId);
        var saved = CityMapService.resolve(actor,feature);
        if (saved == null || saved.revision() != revision) throw fail("stale");
        if (p == null || !feature.equals(p.feature())) throw fail("platform");
        data.platforms.put(stopId, p.withFeature(null));
        data.setDirty();
        CityMapService.platformsChanged(actor,feature);
    }
    public static boolean hasCityPlatforms(MinecraftServer server, UUID feature) {
        return PassengerData.get(server).platforms.values().stream().anyMatch(p -> feature.equals(p.feature()));
    }

    private static void publishPlatform(ServerPlayer actor, PassengerData data, CargoRailConnections.Station binding, BlockPos entrance) {
        var station = binding.station();
        var old = data.platforms.get(station.id);
        var platform = new PassengerData.Platform(station.id, actor.getUUID(), actor.level().dimension().location(),
                entrance.immutable(), station.getBlockEntityPos().immutable(), binding.graph().id, old == null ? null : old.feature());
        if (old != null && old.graph().equals(platform.graph()) && old.dimension().equals(platform.dimension())
                && old.stationPos().equals(platform.stationPos()) && old.entrance().equals(entrance)
                && publicStop(actor.getServer(), old) != null) return;
        if (old != null && old.feature() != null) throw fail("platform_bound");
        if (data.lines.values().stream().anyMatch(line -> line.stops.contains(station.id))) throw fail("in_use");
        if (old == null && data.platforms.size() >= PassengerData.MAX_PLATFORMS) throw fail("limit");
        data.platforms.put(station.id, platform);
        data.setDirty();
    }
    /** Stable-ID withdrawal remains available after the station block itself has disappeared. */
    static void withdrawPlatform(ServerPlayer actor, UUID id) {
        var data = writable(actor);
        var old = data.platforms.get(id);
        if (old == null || !old.owner().equals(actor.getUUID()) && !actor.hasPermissions(2)) throw fail("permission");
        for (var line : data.lines.values()) {
            if (line.stops.contains(id)) {
                release(actor.getServer(),line);
                line.stops.remove(id);
            }
        }
        data.platforms.remove(id);
        data.setDirty();
        if (old.feature()!=null) CityMapService.platformsChanged(actor,old.feature());
    }
    private static boolean walkable(ServerLevel level, BlockPos pos) {
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos) || level.isOutsideBuildHeight(pos)
                || level.isOutsideBuildHeight(pos.above())) return false;
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP) &&
                level.getBlockState(pos).getCollisionShape(level,pos).isEmpty() && level.getFluidState(pos).isEmpty() &&
                level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty() && level.getFluidState(pos.above()).isEmpty();
    }
    public static UUID create(ServerPlayer player, String name) {
        var data = writable(player);
        if (data.lines.size() >= PassengerData.MAX_LINES) throw fail("limit");
        var line = new PassengerData.Line(UUID.randomUUID(),player.getUUID(),name.strip());
        data.lines.put(line.id,line); data.setDirty();
        return line.id;
    }
    static void setColor(ServerPlayer player,UUID id,String name) {
        var data=writable(player);
        var line=managed(player,data,id);
        var color=net.minecraft.world.item.DyeColor.byName(name,null);
        if (color==null) throw fail("unavailable");
        line.color=color;
        data.setDirty();
    }
    public static void setDescription(ServerPlayer player, UUID id, String expected, String description) {
        var data = writable(player);
        var line = managed(player, data, id);
        line.changeDescription(expected, description);
        data.setDirty();
    }
    static void chooseTrain(ServerPlayer player,UUID lineId,UUID trainId) {
        var data=writable(player);
        var line=managed(player,data,lineId);
        if (line.train!=null) throw fail("in_use");
        if (trainId!=null) {
            var train=RailwaySavedData.load(player.getServer()).getTrains().get(trainId);
            if (!RailwayBinder.selected(player,RailwayBinder.TRAINS).contains(trainId) || train==null ||
                    !TransportProtection.canModifyTrain(player,train)) throw fail("train_changed");
        }
        line.chosenTrain=trainId;
        data.setDirty();
    }
    public static void append(ServerPlayer player, UUID id) {
        append(player, id, lookedAt(player).id, false);
    }

    static void append(ServerPlayer player, UUID id, UUID stationId, boolean registerHere) {
        var data = writable(player);
        var line = managed(player,data,id);
        if (line.train != null) throw fail("in_use");
        if (stationId == null) {
            var aimed = lookedAt(player);
            if (aimed.name.isBlank() || aimed.name.length() > 96 || aimed.name.contains("*") || aimed.name.contains("?"))
                throw fail("ambiguous");
            stationId = aimed.id;
        }
        if (line.stops.contains(stationId)) throw fail("duplicate_stop");
        if (line.stops.size() >= PassengerTimetable.MAX_STOPS) throw fail("stops");
        var dimension = player.level().dimension().location();
        if (!line.stops.isEmpty() && !data.platforms.get(line.stops.getFirst()).dimension().equals(dimension))
            throw fail("other_dimension");
        if (!line.stops.isEmpty()) {
            var first = CargoRailConnections.station(player.getServer(),line.stops.getFirst());
            if (first == null) throw fail("platform");
            if (first.graph().getPoint(EdgePointType.STATION,stationId) == null) throw fail("different_network");
        }
        if (registerHere && !data.platforms.containsKey(stationId)) platform(player,false);
        var stop = publicStop(player.getServer(), data.platforms.get(stationId));
        if (stop == null || !dimension.equals(stop.dimension())) throw fail("platform");
        line.stops.add(stationId);
        data.setDirty();
    }

    static void clearStops(ServerPlayer player, UUID id) {
        var data = writable(player);
        var line = managed(player,data,id);
        release(player.getServer(),line);
        line.stops.clear();
        line.chosenTrain=null;
        data.setDirty();
    }
    static void pop(ServerPlayer player, UUID id) {
        var data = writable(player); var line = managed(player,data,id);
        if (line.train != null || line.stops.isEmpty()) throw fail("stops");
        line.stops.removeLast(); data.setDirty();
    }
    public static void start(ServerPlayer actor, UUID id, int dwellSeconds) {
        start(actor,id,dwellSeconds,null);
    }

    static GlobalStation departureStation(ServerPlayer actor, PassengerData data, PassengerData.Line line) {
        if (line.stops.isEmpty()) return null;
        var platform = data.platforms.get(line.stops.getFirst());
        if (platform == null) return null;
        var binding = CargoRailConnections.station(actor.getServer(),platform.id());
        GlobalStation station = binding == null ? null : binding.station();
        return station == null || station.assembling ? null : station;
    }

    static void start(ServerPlayer actor, UUID id, int dwellSeconds, UUID expectedTrain) {
        var server = actor.getServer();
        var data = writable(actor);
        var line = managed(actor,data,id);
        if (line.train != null || line.stops.size() < 2 || dwellSeconds < 10 || dwellSeconds > 120) throw fail("stops");
        var origin = departureStation(actor,data,line);
        if (origin == null || origin.getPresentTrain() == null) throw fail("no_docked_train");
        var train = origin.getPresentTrain();
        if (expectedTrain != null && (train == null || !expectedTrain.equals(train.id))) throw fail("train_changed");
        if (line.chosenTrain != null && !line.chosenTrain.equals(train.id)) throw fail("train_changed");
        if (!line.stops.getFirst().equals(origin.id) || train == null || !origin.id.equals(stoppedAt(server, train.id)) ||
                train.invalid || train.derailed || train.manualTick || train.graph == null || train.carriages.size() > 32) throw fail("train");
        if (!(train.hasForwardConductor() || train.doubleEnded && train.hasBackwardConductor())) throw fail("no_driver");
        UUID operator = trainManager(server,actor.getUUID(),train) ? actor.getUUID() : line.owner;
        if (!trainManager(server,operator,train) || !TransportProtection.canModifyTrain(actor,train)) throw fail("permission");
        if (assigned(server,train.id) || CargoCharters.get(server).reservesTrain(server,train.id) ||
                PublicFleetRegistry.get(server).hasJob(train.id) || PublicFleetRegistry.get(server).service(train.id).filter(PublicFleetRegistry.Service::enabled).isPresent() ||
                CargoShipmentRegistry.get(server).inUse("train",train.id)) throw fail("in_use");
        if (train.runtime.getSchedule() != null) throw fail("schedule");
        if (!publishedStops(server,data,line,train)) throw fail("platform");
        var names = names(data,line,train);
        if (names.isEmpty()) throw fail("ambiguous");
        var schedule = CargoSchedules.build(server.registryAccess(), names, dwellSeconds);
        train.runtime.setSchedule(schedule,true);
        line.train = train.id; line.generation = UUID.randomUUID();
        line.trainOperator = operator;
        line.schedule = CargoSchedulePlan.shape(schedule.write(server.registryAccess()));
        line.clock = new PassengerTimetable(line.stops.size());
        line.verifiedPlatforms[0] = server.overworld().getGameTime();
        line.clock.observe(0,line.verifiedPlatforms[0]);
        data.setDirty(); RailwaySavedData.load(server).setDirty();
    }
    private static boolean trainManager(MinecraftServer server, UUID operator, Train train) {
        if (train.owner == null) return false;
        var registration = CargoTrainRegistry.find(server,train.id).orElse(null);
        return registration == null ? train.owner.equals(operator) : registration.ownerId().equals(train.owner) &&
                CargoOwners.get(server).owner("train/" + train.id,train.owner).manages(server,operator);
    }
    static void close(ServerPlayer player, UUID id, boolean delete) {
        var data = writable(player);
        var line = managed(player,data,id);
        release(player.getServer(),line);
        if (delete) data.lines.remove(id);
        data.setDirty();
    }
    private static void release(MinecraftServer server, PassengerData.Line line) {
        if (line.train != null) {
            var train = RailwaySavedData.load(server).getTrains().get(line.train);
            if (train != null && trainManager(server,line.trainOperator,train) && owns(server,line,train)) {
                train.runtime.discardSchedule();
                RailwaySavedData.load(server).setDirty();
            }
            line.release();
        }
    }
    private static PassengerData writable(ServerPlayer player) {
        if (!player.hasPermissions(2)) throw fail("op_required");
        var data = PassengerData.get(player.getServer());
        if (data.unavailable != null || player.isSpectator()) throw fail("unavailable");
        return data;
    }
    private static PassengerData.Line managed(ServerPlayer player, PassengerData data, UUID id) {
        var line = data.lines.get(id);
        if (line == null || !(line.owner.equals(player.getUUID()) || player.hasPermissions(2))) throw fail("permission");
        return line;
    }
    private static GlobalStation lookedAt(ServerPlayer player) {
        if (CargoInfrastructure.lookedAt(player) instanceof StationBlockEntity be && be.getStation() != null &&
                !be.getStation().assembling) return be.getStation();
        throw fail("look");
    }
    private static IllegalArgumentException fail(String key) { return new IllegalArgumentException(key); }
    private static boolean owns(MinecraftServer server, PassengerData.Line line, Train train) {
        var schedule = train.runtime.getSchedule();
        // Validate entry count before serializing an unrelated replacement.
        return schedule != null && schedule.cyclic && train.runtime.isAutoSchedule &&
                schedule.entries.size() == line.stops.size() &&
                line.schedule.equals(CargoSchedulePlan.shape(schedule.write(server.registryAccess())));
    }
    private static Train active(MinecraftServer server, PassengerData.Line line) {
        var train = RailwaySavedData.load(server).getTrains().get(line.train);
        return train != null && !train.invalid && !train.derailed && !train.manualTick && train.graph != null &&
                !train.runtime.paused && !train.runtime.completed && (train.hasForwardConductor() || train.doubleEnded && train.hasBackwardConductor()) &&
                trainManager(server,line.trainOperator,train) && owns(server,line,train) ? train : null;
    }
    /** Native destinations match names, while our identities remain station UUIDs. Ambiguity withdraws the ride. */
    private static List<String> names(PassengerData data, PassengerData.Line line, Train train) {
        var stations = train.graph.getPoints(EdgePointType.STATION);
        if (stations.size() > 2048) return List.of();
        var names = new ArrayList<String>();
        for (UUID id : line.stops) {
            var station = train.graph.getPoint(EdgePointType.STATION,id);
            var platform = data.platforms.get(id);
            if (station == null || station.assembling || platform == null || !platform.dimension().equals(station.getBlockEntityDimension().location()) ||
                    station.name.isBlank() || station.name.length() > 96 || station.name.contains("*") || station.name.contains("?")) return List.of();
            names.add(station.name);
        }
        var wanted = new HashSet<>(names);
        if (wanted.size() != names.size()) return List.of();
        var seen = new HashSet<String>();
        for (var station : stations) if (wanted.contains(station.name) && !seen.add(station.name)) return List.of();
        return names;
    }
    private static boolean matchingNames(PassengerData data, PassengerData.Line line, Train train) {
        var names=names(data,line,train);
        var entries=train.runtime.getSchedule().entries;
        if (names.isEmpty() || names.size()!=entries.size()) return false;
        for(int i=0;i<names.size();i++) {
            if(!CargoSchedulePlan.filter(names.get(i)).equals(entries.get(i).instruction.getData().getString("Text")))return false;
        }
        return true;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var server = event.getServer(); long now = server.overworld().getGameTime();
        var data = PassengerData.get(server);
        int slot = 0;
        for (var line : data.lines.values()) {
            if (line.clock == null || slot++ % 5 != now % 5) continue;
            line.verifiedDirection = false;
            var train = active(server,line);
            if (train == null) { line.clock.reset(); line.status = "changed"; continue; }
            if (!publishedStops(server,data,line,train)) { line.clock.reset(); line.status = "permission"; continue; }
            if (!matchingNames(data,line,train)) { line.clock.reset(); line.status = "invalid_names"; continue; }
            line.verifiedDirection = true;
            UUID docked = stoppedAt(server,train.id);
            int index = docked == null ? -1 : line.stops.indexOf(docked);
            if (docked != null) {
                var p = data.platforms.get(docked);
                var level = p == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION,p.dimension()));
                if (index < 0 || level == null) { line.clock.reset(); line.verifiedDirection = false; line.status = "invalid_names"; continue; }
                line.verifiedPlatforms[index] = now;
            }
            line.clock.observe(index,now);
            if (line.firstServicePending && index == -1 && line.clock.inTransitFromOrigin() && Math.abs(train.speed) > .001) {
                var origin = train.graph.getPoint(EdgePointType.STATION,line.stops.getFirst());
                var terminus = train.graph.getPoint(EdgePointType.STATION,line.stops.getLast());
                com.example.examplemod.city.CityGazette.get(server).publish(com.example.examplemod.city.CityGazette.Kind.FIRST_SERVICE,
                        null,line.id,line.name,origin.name,terminus.name,System.currentTimeMillis());
                line.firstServicePending = false;
                data.setDirty();
            }
            line.status = line.clock.calibrated() ? "late" : "checking";
            if(line.clock.calibrated() && !platformsFresh(line,now)) {
                line.status="checking";
            }
        }
    }
}
