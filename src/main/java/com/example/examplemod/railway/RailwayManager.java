package com.example.examplemod.railway;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;

public final class RailwayManager extends SavedData {
    private static final String DATA_NAME = "createcityexpansioncore_railway";
    private static final int FORMAT_VERSION = 5;

    private final Map<UUID, CityRailStation> stations = new LinkedHashMap<>();
    private final Map<UUID, CityFleetTrain> trains = new LinkedHashMap<>();
    private final Map<UUID, RailLine> lines = new LinkedHashMap<>();
    private final Map<UUID, UUID> primaryProjectStations = new LinkedHashMap<>();
    private final Map<UUID, OwnedRailwayNetwork> ownedNetworks = new LinkedHashMap<>();
    private final Map<UUID, OwnedRailwaySection> ownedSections = new LinkedHashMap<>();
    private final Map<UUID, RailwayTripLedger> tripLedgers = new LinkedHashMap<>();
    private final Map<UUID, RouteReservation> routeReservations = new LinkedHashMap<>();
    private final Map<UUID,SelectedRailwayRoute> selectedRoutes = new LinkedHashMap<>();
    private final transient Map<UUID,List<RailwayRouteOption>> proposedRoutes = new HashMap<>();

    public static RailwayManager get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(RailwayManager::new, RailwayManager::load), DATA_NAME);
    }

    public RailwayManager() {}

    public List<CityRailStation> stationsForCity(UUID cityId) {
        return stations.values().stream().filter(value -> cityId.equals(value.cityId())).toList();
    }
    public List<CityRailStation> stationsForCityIdsAndPersonal() { return List.copyOf(stations.values()); }
    public List<CityRailStation> personalStations(UUID playerId) {
        return stations.values().stream()
                .filter(value -> value.cityId() == null && value.registeredBy().equals(playerId)).toList();
    }
    public List<CityFleetTrain> trainsForCity(UUID cityId) {
        return trains.values().stream().filter(value -> cityId.equals(value.cityId())).toList();
    }
    public List<CityFleetTrain> trainsForCityIdsAndPersonal() { return List.copyOf(trains.values()); }
    public List<CityFleetTrain> personalTrains(UUID playerId) {
        return trains.values().stream()
                .filter(value -> value.cityId() == null && value.registeredBy().equals(playerId)).toList();
    }
    public List<RailLine> linesForCity(UUID cityId) {
        return lines.values().stream().filter(value -> value.cityId().equals(cityId)).toList();
    }
    @Nullable public CityRailStation station(UUID id) { return stations.get(id); }
    @Nullable public CityFleetTrain train(UUID id) { return trains.get(id); }
    @Nullable public RailLine line(UUID id) { return lines.get(id); }
    public List<OwnedRailwayNetwork> ownedNetworks(){return List.copyOf(ownedNetworks.values());}
    public List<OwnedRailwaySection> ownedSections(){return List.copyOf(ownedSections.values());}
    public List<RailwayTripLedger> tripLedgers(){return List.copyOf(tripLedgers.values());}
    @Nullable public RailwayTripLedger activeTrip(UUID createTrainId){return tripLedgers.values().stream().filter(t->t.active()&&t.createTrainId().equals(createTrainId)).findFirst().orElse(null);}
    public void addTrip(RailwayTripLedger trip){if(activeTrip(trip.createTrainId())!=null)throw new IllegalStateException("Train already has an active trip");tripLedgers.put(trip.id(),trip);setDirty();}
    @Nullable public RouteReservation reservationForTrip(UUID tripId){return routeReservations.values().stream().filter(r->r.tripId().equals(tripId)).findFirst().orElse(null);}
    public List<RouteReservation> routeReservations(){return List.copyOf(routeReservations.values());}
    public void addReservation(RouteReservation reservation){if(reservationForTrip(reservation.tripId())!=null)throw new IllegalStateException("Trip already has a reservation");routeReservations.put(reservation.id(),reservation);setDirty();}
    public void proposeRoutes(UUID fleetTrainId,List<RailwayRouteOption> routes){proposedRoutes.put(fleetTrainId,List.copyOf(routes));}
    public List<RailwayRouteOption> proposedRoutes(UUID fleetTrainId){return proposedRoutes.getOrDefault(fleetTrainId,List.of());}
    public boolean selectRoute(UUID fleetTrainId,UUID optionId,long now){RailwayRouteOption option=proposedRoutes(fleetTrainId).stream().filter(r->r.id().equals(optionId)).findFirst().orElse(null);if(option==null)return false;selectedRoutes.put(fleetTrainId,new SelectedRailwayRoute(UUID.randomUUID(),fleetTrainId,option,now));setDirty();return true;}
    @Nullable public SelectedRailwayRoute selectedRoute(UUID fleetTrainId){return selectedRoutes.get(fleetTrainId);}
    public void consumeSelectedRoute(UUID fleetTrainId){if(selectedRoutes.remove(fleetTrainId)!=null)setDirty();proposedRoutes.remove(fleetTrainId);}
    public List<OwnedRailwayNetwork> ownedNetworksFor(UUID cityId,UUID playerId){return ownedNetworks.values().stream().filter(n->n.ownerType()==RailwayOwnershipTypes.OwnerType.CITY?n.ownerId().equals(cityId):n.ownerId().equals(playerId)).toList();}
    public List<OwnedRailwaySection> sectionsForNetwork(UUID networkId){return ownedSections.values().stream().filter(s->s.networkId().equals(networkId)).toList();}
    @Nullable public OwnedRailwayNetwork ownedNetwork(UUID id){return ownedNetworks.get(id);}
    @Nullable public CityRailStation stationByCreateId(UUID id) {
        return stations.values().stream().filter(value -> value.createStationId().equals(id)).findFirst().orElse(null);
    }
    @Nullable public CityFleetTrain trainByCreateId(UUID id) {
        return trains.values().stream().filter(value -> value.createTrainId().equals(id)).findFirst().orElse(null);
    }

    public CityRailStation registerStation(UUID createId, UUID playerId, String name,
                                           ResourceLocation dimension, BlockPos pos) {
        CityRailStation existing = stationByCreateId(createId);
        if (existing != null) return existing;
        CityRailStation created = new CityRailStation(UUID.randomUUID(), createId, playerId, null,
                RailwayTypes.StationType.MIXED, name, dimension, pos);
        stations.put(created.registrationId(), created);
        setDirty();
        return created;
    }

    public CityFleetTrain registerTrain(UUID createId, UUID playerId, String name) {
        CityFleetTrain existing = trainByCreateId(createId);
        if (existing != null) return existing;
        CityFleetTrain created = new CityFleetTrain(UUID.randomUUID(), createId, playerId, null, null,
                RailwayTypes.TrainType.MIXED, name, 0, "", false);
        trains.put(created.fleetTrainId(), created);
        setDirty();
        return created;
    }

    public boolean attachStationToCity(UUID registrationId, UUID playerId, UUID cityId) {
        CityRailStation station = stations.get(registrationId);
        if (station == null || station.cityId() != null || !station.registeredBy().equals(playerId)) return false;
        station.setCityId(cityId); setDirty(); return true;
    }

    public boolean attachTrainToCity(UUID fleetTrainId, UUID playerId, UUID cityId) {
        CityFleetTrain train = trains.get(fleetTrainId);
        if (train == null || train.cityId() != null || !train.registeredBy().equals(playerId)) return false;
        train.setCityId(cityId); setDirty(); return true;
    }

    public RailLine createLine(UUID cityId, String name, RailwayTypes.LineType type,
                               boolean cyclic, List<RailLineStop> stops) {
        if (stops.size() < 2 || stops.size() > 64) throw new IllegalArgumentException("RailLine needs 2..64 stops");
        RailLine line = new RailLine(UUID.randomUUID(), cityId, name, type, cyclic, 1, stops);
        lines.put(line.lineId(), line); setDirty(); return line;
    }

    public void removeLine(UUID lineId) {
        if (lines.remove(lineId) == null) return;
        trains.values().stream().filter(value -> lineId.equals(value.activeLineId())).forEach(CityFleetTrain::unassign);
        setDirty();
    }

    public void removeTrain(UUID fleetTrainId) { if (trains.remove(fleetTrainId) != null) setDirty(); }
    public boolean rebindStation(UUID targetId, UUID replacementId, UUID playerId) {
        CityRailStation target = stations.get(targetId), replacement = stations.get(replacementId);
        if (target == null || replacement == null || target.cityId() == null || replacement.cityId() != null
                || !replacement.registeredBy().equals(playerId)
                || stationByCreateId(replacement.createStationId()) != replacement) return false;
        target.rebind(replacement.createStationId(), replacement.lastKnownName(), replacement.lastKnownDimension(), replacement.lastKnownPos());
        stations.remove(replacementId);
        lines.values().stream()
                .filter(line -> line.stops().stream()
                        .anyMatch(stop -> stop.stationRegistrationId().equals(targetId)))
                .forEach(line -> line.update(line.name(), line.type(), line.cyclic(), line.stops()));
        setDirty(); return true;
    }
    public boolean rebindTrain(UUID targetId, UUID replacementId, UUID playerId) {
        CityFleetTrain target = trains.get(targetId), replacement = trains.get(replacementId);
        if (target == null || replacement == null || target.cityId() == null || replacement.cityId() != null
                || !replacement.registeredBy().equals(playerId)
                || trainByCreateId(replacement.createTrainId()) != replacement) return false;
        target.rebind(replacement.createTrainId(), replacement.lastKnownName());
        target.setAutomationSuspended(target.activeLineId() != null); trains.remove(replacementId); setDirty(); return true;
    }
    public void removeStationFromCity(UUID registrationId) {
        CityRailStation station = stations.get(registrationId);
        if (station != null && station.cityId() != null) { station.setCityId(null); setDirty(); }
    }
    public void linkProject(UUID projectId, @Nullable UUID stationRegistrationId) {
        if (stationRegistrationId == null) primaryProjectStations.remove(projectId);
        else primaryProjectStations.put(projectId, stationRegistrationId);
        setDirty();
    }
    @Nullable public UUID projectStation(UUID projectId) { return primaryProjectStations.get(projectId); }
    public boolean isStationLinkedToProject(UUID stationId) {
        return primaryProjectStations.containsValue(stationId);
    }
    @Nullable public UUID projectForStation(UUID stationId) {
        return primaryProjectStations.entrySet().stream()
                .filter(entry -> entry.getValue().equals(stationId))
                .map(Map.Entry::getKey).findFirst().orElse(null);
    }
    public void markChanged() { setDirty(); }

    public synchronized String registerMarkedSection(ServerLevel level,BlockPos firstPos,BlockPos secondPos,
                                                     RailwayOwnershipTypes.OwnerType ownerType,UUID ownerId){
        if(firstPos.equals(secondPos))return "same_marker";
        if(!(level.getBlockEntity(firstPos) instanceof com.example.examplemod.railway.block.RailwayTrackMarkerBlockEntity first)
                ||!(level.getBlockEntity(secondPos) instanceof com.example.examplemod.railway.block.RailwayTrackMarkerBlockEntity second))return "marker_missing";
        RailwayEndpoint a=RailwayTrackPathResolver.endpoint(level,first.markerId(),level.dimension().location(),firstPos,first.trackPos(),first.front());
        RailwayEndpoint b=RailwayTrackPathResolver.endpoint(level,second.markerId(),level.dimension().location(),secondPos,second.trackPos(),second.front());
        if(a==null||b==null)return "track_missing";RailwayTrackPathResolver.Path path=RailwayTrackPathResolver.path(a,b);if(path==null)return "no_path";
        Set<RailwayEdgeReference> claimed=new HashSet<>();ownedSections.values().forEach(s->claimed.addAll(s.path()));
        if(path.edges().stream().map(RailwayEdgeReference::normalized).anyMatch(claimed::contains))return "already_owned";
        OwnedRailwayNetwork network=ownedNetworks.values().stream().filter(n->n.ownerType()==ownerType&&n.ownerId().equals(ownerId)).findFirst().orElseGet(()->{
            OwnedRailwayNetwork created=new OwnedRailwayNetwork(UUID.randomUUID(),ownerType,ownerId,ownerType==RailwayOwnershipTypes.OwnerType.CITY?"City Railway":"Player Railway",RailwayOwnershipTypes.Access.PUBLIC,0);ownedNetworks.put(created.id(),created);return created;});
        OwnedRailwaySection section=new OwnedRailwaySection(UUID.randomUUID(),network.id(),path.first(),path.second(),path.edges(),path.length(),RailwayOwnershipTypes.SectionStatus.ONLINE);ownedSections.put(section.id(),section);setDirty();return "registered";
    }
    public synchronized boolean configureNetwork(UUID networkId,RailwayOwnershipTypes.OwnerType ownerType,UUID ownerId,
                                                 @Nullable RailwayOwnershipTypes.Access access,@Nullable Long tariff,@Nullable String name){
        OwnedRailwayNetwork network=ownedNetworks.get(networkId);if(network==null||network.ownerType()!=ownerType||!network.ownerId().equals(ownerId))return false;
        if(access!=null)network.access(access);if(tariff!=null)network.freightTariff(tariff);if(name!=null)network.rename(name);setDirty();return true;
    }
    public synchronized boolean reconcileOwnedSections(MinecraftServer server){boolean changed=false;for(OwnedRailwaySection section:ownedSections.values()){
        RailwayOwnershipTypes.SectionStatus status=validateMarker(server,section.first())&&validateMarker(server,section.second())?RailwayOwnershipTypes.SectionStatus.ONLINE:RailwayOwnershipTypes.SectionStatus.MARKER_MISSING;
        if(status==RailwayOwnershipTypes.SectionStatus.ONLINE){ServerLevel level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,section.first().trackDimension()));RailwayEndpoint a=level==null?null:resolveEndpoint(level,section.first());RailwayEndpoint b=level==null?null:resolveEndpoint(level,section.second());if(a==null||b==null)status=RailwayOwnershipTypes.SectionStatus.TRACK_MISSING;else{var path=RailwayTrackPathResolver.path(a,b);if(path==null||!path.edges().equals(section.path()))status=RailwayOwnershipTypes.SectionStatus.GRAPH_CHANGED;else if(!a.equals(section.first())||!b.equals(section.second())||Math.abs(path.length()-section.length())>.001){section.reconciled(a,b,section.path(),path.length(),RailwayOwnershipTypes.SectionStatus.ONLINE);changed=true;}}}
        if(section.status()!=status){section.status(status);changed=true;}}
        if(changed)setDirty();return changed;}
    private static boolean validateMarker(MinecraftServer server,RailwayEndpoint endpoint){ServerLevel level=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,endpoint.markerDimension()));return level!=null&&level.getBlockEntity(endpoint.markerPos()) instanceof com.example.examplemod.railway.block.RailwayTrackMarkerBlockEntity marker&&marker.markerId().equals(endpoint.markerId());}
    @Nullable private static RailwayEndpoint resolveEndpoint(ServerLevel level,RailwayEndpoint endpoint){return RailwayTrackPathResolver.endpoint(level,endpoint.markerId(),endpoint.markerDimension(),endpoint.markerPos(),endpoint.trackPos(),endpoint.front());}

    public void removeCity(UUID cityId) {
        Set<UUID> removedNetworks=new HashSet<>();ownedNetworks.values().stream().filter(n->n.ownerType()==RailwayOwnershipTypes.OwnerType.CITY&&n.ownerId().equals(cityId)).map(OwnedRailwayNetwork::id).forEach(removedNetworks::add);ownedNetworks.keySet().removeAll(removedNetworks);ownedSections.values().removeIf(section->removedNetworks.contains(section.networkId()));
        lines.values().removeIf(line -> line.cityId().equals(cityId));
        stations.values().removeIf(station -> cityId.equals(station.cityId()));
        trains.values().removeIf(train -> cityId.equals(train.cityId()));
        primaryProjectStations.entrySet().removeIf(entry -> stations.get(entry.getValue()) == null);
        setDirty();
    }

    private static RailwayManager load(CompoundTag root, HolderLookup.Provider registries) {
        RailwayManager manager = new RailwayManager();
        if (root.getInt("FormatVersion") > FORMAT_VERSION) return manager;
        for (Tag raw : root.getList("Stations", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag = (CompoundTag) raw;
                ResourceLocation dimension = ResourceLocation.tryParse(tag.getString("Dimension"));
                if (dimension == null) continue;
                CityRailStation station = new CityRailStation(tag.getUUID("RegistrationId"), tag.getUUID("CreateId"),
                        tag.getUUID("RegisteredBy"), tag.contains("CityId") ? tag.getUUID("CityId") : null,
                        readEnum(tag, "Type", RailwayTypes.StationType.class, RailwayTypes.StationType.MIXED),
                        tag.getString("Name"), dimension,
                        NbtUtils.readBlockPos(tag, "Pos").orElse(BlockPos.ZERO));
                manager.stations.put(station.registrationId(), station);
            } catch (RuntimeException ignored) {}
        }
        for (Tag raw : root.getList("Trains", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag = (CompoundTag) raw;
                CityFleetTrain train = new CityFleetTrain(tag.getUUID("FleetId"), tag.getUUID("CreateId"),
                        tag.getUUID("RegisteredBy"), tag.contains("CityId") ? tag.getUUID("CityId") : null,
                        tag.contains("LineId") ? tag.getUUID("LineId") : null,
                        readEnum(tag, "Type", RailwayTypes.TrainType.class, RailwayTypes.TrainType.MIXED),
                        tag.getString("Name"), tag.getInt("Revision"), tag.getString("Fingerprint"),
                        tag.getBoolean("Suspended"));
                manager.trains.put(train.fleetTrainId(), train);
            } catch (RuntimeException ignored) {}
        }
        for (Tag raw : root.getList("Lines", Tag.TAG_COMPOUND)) {
            try {
                CompoundTag tag = (CompoundTag) raw;
                List<RailLineStop> stops = new ArrayList<>();
                for (Tag stopRaw : tag.getList("Stops", Tag.TAG_COMPOUND)) {
                    CompoundTag stop = (CompoundTag) stopRaw;
                    stops.add(new RailLineStop(stop.getUUID("Station"),
                            readEnum(stop, "Mode", RailwayTypes.StopMode.class, RailwayTypes.StopMode.IMMEDIATE),
                            stop.getInt("WaitSeconds")));
                }
                if (stops.size() < 2) continue;
                RailLine line = new RailLine(tag.getUUID("LineId"), tag.getUUID("CityId"), tag.getString("Name"),
                        readEnum(tag, "Type", RailwayTypes.LineType.class, RailwayTypes.LineType.MIXED),
                        tag.getBoolean("Cyclic"), tag.getInt("Revision"), stops);
                manager.lines.put(line.lineId(), line);
            } catch (RuntimeException ignored) {}
        }
        for (Tag raw : root.getList("ProjectStations", Tag.TAG_COMPOUND)) {
            CompoundTag tag = (CompoundTag) raw;
            if (tag.hasUUID("Project") && tag.hasUUID("Station"))
                manager.primaryProjectStations.put(tag.getUUID("Project"), tag.getUUID("Station"));
        }
        for(Tag raw:root.getList("OwnedNetworks",Tag.TAG_COMPOUND))try{CompoundTag tag=(CompoundTag)raw;OwnedRailwayNetwork network=new OwnedRailwayNetwork(tag.getUUID("Id"),readEnum(tag,"OwnerType",RailwayOwnershipTypes.OwnerType.class,RailwayOwnershipTypes.OwnerType.PLAYER),tag.getUUID("Owner"),tag.getString("Name"),readEnum(tag,"Access",RailwayOwnershipTypes.Access.class,RailwayOwnershipTypes.Access.PUBLIC),tag.getLong("FreightTariff"));manager.ownedNetworks.put(network.id(),network);}catch(RuntimeException ignored){}
        for(Tag raw:root.getList("OwnedSections",Tag.TAG_COMPOUND))try{CompoundTag tag=(CompoundTag)raw;RailwayEndpoint a=readEndpoint(tag.getCompound("First")),b=readEndpoint(tag.getCompound("Second"));List<RailwayEdgeReference> path=new ArrayList<>();for(Tag edgeRaw:tag.getList("Path",Tag.TAG_COMPOUND))path.add(readEdge((CompoundTag)edgeRaw));OwnedRailwaySection section=new OwnedRailwaySection(tag.getUUID("Id"),tag.getUUID("Network"),a,b,path,tag.getDouble("Length"),readEnum(tag,"Status",RailwayOwnershipTypes.SectionStatus.class,RailwayOwnershipTypes.SectionStatus.GRAPH_CHANGED));if(manager.ownedNetworks.containsKey(section.networkId()))manager.ownedSections.put(section.id(),section);}catch(RuntimeException ignored){}
        for(Tag raw:root.getList("TripLedgers",Tag.TAG_COMPOUND))try{CompoundTag tag=(CompoundTag)raw;List<RailwayTariffLeg> legs=new ArrayList<>();for(Tag legRaw:tag.getList("Legs",Tag.TAG_COMPOUND)){CompoundTag leg=(CompoundTag)legRaw;legs.add(new RailwayTariffLeg(leg.getUUID("Network"),readEnum(leg,"OwnerType",RailwayOwnershipTypes.OwnerType.class,RailwayOwnershipTypes.OwnerType.PLAYER),leg.getUUID("Owner"),leg.getLong("Tariff"),leg.getDouble("Start"),leg.getDouble("End"),leg.getLong("Reserved")));}RailwayTripLedger trip=new RailwayTripLedger(tag.getUUID("Id"),tag.getUUID("CreateTrain"),tag.getUUID("FleetTrain"),readEnum(tag,"PayerType",RailwayOwnershipTypes.OwnerType.class,RailwayOwnershipTypes.OwnerType.PLAYER),tag.getUUID("Payer"),tag.getUUID("Source"),tag.getUUID("Destination"),tag.getDouble("Planned"),tag.getLong("Reserved"),tag.getLong("StartedAt"),legs,tag.getDouble("Actual"),tag.getLong("SettledAt"),readEnum(tag,"TripStatus",RailwayTripLedger.Status.class,RailwayTripLedger.Status.INTERRUPTED));manager.tripLedgers.put(trip.id(),trip);}catch(RuntimeException ignored){}
        for(Tag raw:root.getList("RouteReservations",Tag.TAG_COMPOUND))try{CompoundTag tag=(CompoundTag)raw;List<RailwayEdgeReference> edges=new ArrayList<>();for(Tag edge:tag.getList("Edges",Tag.TAG_COMPOUND))edges.add(readEdge((CompoundTag)edge));List<RailwayTariffLeg> tariffs=new ArrayList<>();for(Tag rawLeg:tag.getList("Tariffs",Tag.TAG_COMPOUND))tariffs.add(readTariffLeg((CompoundTag)rawLeg));RouteReservation reservation=new RouteReservation(tag.getUUID("Id"),tag.getUUID("Trip"),tag.getUUID("Graph"),edges,tariffs,tag.getInt("Cursor"),tag.getLong("ClosedAt"),readEnum(tag,"ReservationStatus",RouteReservation.Status.class,RouteReservation.Status.ROUTE_INVALIDATED));if(manager.tripLedgers.containsKey(reservation.tripId()))manager.routeReservations.put(reservation.id(),reservation);}catch(RuntimeException ignored){}
        for(Tag raw:root.getList("SelectedRoutes",Tag.TAG_COMPOUND))try{CompoundTag tag=(CompoundTag)raw;UUID fleet=tag.getUUID("FleetTrain");RailwayRouteOption option=readRouteOption(tag.getCompound("Option"));manager.selectedRoutes.put(fleet,new SelectedRailwayRoute(tag.getUUID("Selection"),fleet,option,tag.getLong("SelectedAt")));}catch(RuntimeException ignored){}
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("FormatVersion", FORMAT_VERSION);
        ListTag stationTags = new ListTag();
        for (CityRailStation station : stations.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("RegistrationId", station.registrationId()); tag.putUUID("CreateId", station.createStationId());
            tag.putUUID("RegisteredBy", station.registeredBy()); if (station.cityId() != null) tag.putUUID("CityId", station.cityId());
            tag.putInt("Type", station.type().ordinal()); tag.putString("Name", station.lastKnownName());
            tag.putString("Dimension", station.lastKnownDimension().toString()); tag.put("Pos", NbtUtils.writeBlockPos(station.lastKnownPos()));
            stationTags.add(tag);
        }
        root.put("Stations", stationTags);
        ListTag trainTags = new ListTag();
        for (CityFleetTrain train : trains.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("FleetId", train.fleetTrainId()); tag.putUUID("CreateId", train.createTrainId());
            tag.putUUID("RegisteredBy", train.registeredBy()); if (train.cityId() != null) tag.putUUID("CityId", train.cityId());
            if (train.activeLineId() != null) tag.putUUID("LineId", train.activeLineId());
            tag.putInt("Type", train.type().ordinal()); tag.putString("Name", train.lastKnownName());
            tag.putInt("Revision", train.compiledLineRevision()); tag.putString("Fingerprint", train.compiledScheduleFingerprint());
            tag.putBoolean("Suspended", train.automationSuspended()); trainTags.add(tag);
        }
        root.put("Trains", trainTags);
        ListTag lineTags = new ListTag();
        for (RailLine line : lines.values()) {
            CompoundTag tag = new CompoundTag(); tag.putUUID("LineId", line.lineId()); tag.putUUID("CityId", line.cityId());
            tag.putString("Name", line.name()); tag.putInt("Type", line.type().ordinal()); tag.putBoolean("Cyclic", line.cyclic());
            tag.putInt("Revision", line.revision()); ListTag stopTags = new ListTag();
            for (RailLineStop stop : line.stops()) {
                CompoundTag value = new CompoundTag(); value.putUUID("Station", stop.stationRegistrationId());
                value.putInt("Mode", stop.mode().ordinal()); value.putInt("WaitSeconds", stop.waitSeconds()); stopTags.add(value);
            }
            tag.put("Stops", stopTags); lineTags.add(tag);
        }
        root.put("Lines", lineTags);
        ListTag links = new ListTag();
        primaryProjectStations.forEach((project, station) -> { CompoundTag tag = new CompoundTag(); tag.putUUID("Project", project); tag.putUUID("Station", station); links.add(tag); });
        root.put("ProjectStations", links);
        ListTag networkTags=new ListTag();for(OwnedRailwayNetwork network:ownedNetworks.values()){CompoundTag tag=new CompoundTag();tag.putUUID("Id",network.id());tag.putInt("OwnerType",network.ownerType().ordinal());tag.putUUID("Owner",network.ownerId());tag.putString("Name",network.name());tag.putInt("Access",network.access().ordinal());tag.putLong("FreightTariff",network.freightTariff());networkTags.add(tag);}root.put("OwnedNetworks",networkTags);
        ListTag sectionTags=new ListTag();for(OwnedRailwaySection section:ownedSections.values()){CompoundTag tag=new CompoundTag();tag.putUUID("Id",section.id());tag.putUUID("Network",section.networkId());tag.put("First",writeEndpoint(section.first()));tag.put("Second",writeEndpoint(section.second()));tag.putDouble("Length",section.length());tag.putInt("Status",section.status().ordinal());ListTag path=new ListTag();section.path().forEach(edge->path.add(writeEdge(edge)));tag.put("Path",path);sectionTags.add(tag);}root.put("OwnedSections",sectionTags);
        ListTag tripTags=new ListTag();for(RailwayTripLedger trip:tripLedgers.values()){CompoundTag tag=new CompoundTag();tag.putUUID("Id",trip.id());tag.putUUID("CreateTrain",trip.createTrainId());tag.putUUID("FleetTrain",trip.fleetTrainId());tag.putInt("PayerType",trip.payerType().ordinal());tag.putUUID("Payer",trip.payerId());tag.putUUID("Source",trip.sourceStationId());tag.putUUID("Destination",trip.destinationStationId());tag.putDouble("Planned",trip.plannedDistance());tag.putDouble("Actual",trip.actualDistance());tag.putLong("Reserved",trip.reservedAmount());tag.putLong("StartedAt",trip.startedAt());tag.putLong("SettledAt",trip.settledAt());tag.putInt("TripStatus",trip.status().ordinal());ListTag legs=new ListTag();for(RailwayTariffLeg value:trip.legs()){CompoundTag leg=new CompoundTag();leg.putUUID("Network",value.networkId());leg.putInt("OwnerType",value.ownerType().ordinal());leg.putUUID("Owner",value.ownerId());leg.putLong("Tariff",value.tariffPerThousandBlocks());leg.putDouble("Start",value.startDistance());leg.putDouble("End",value.endDistance());leg.putLong("Reserved",value.reservedAmount());legs.add(leg);}tag.put("Legs",legs);tripTags.add(tag);}root.put("TripLedgers",tripTags);
        ListTag reservationTags=new ListTag();for(RouteReservation reservation:routeReservations.values()){CompoundTag tag=new CompoundTag();tag.putUUID("Id",reservation.id());tag.putUUID("Trip",reservation.tripId());tag.putUUID("Graph",reservation.graphId());tag.putInt("Cursor",reservation.cursor());tag.putLong("ClosedAt",reservation.closedAt());tag.putInt("ReservationStatus",reservation.status().ordinal());ListTag edges=new ListTag();reservation.orderedEdges().forEach(edge->edges.add(writeEdge(edge)));tag.put("Edges",edges);ListTag tariffs=new ListTag();reservation.tariffSnapshot().forEach(value->tariffs.add(writeTariffLeg(value)));tag.put("Tariffs",tariffs);reservationTags.add(tag);}root.put("RouteReservations",reservationTags);
        ListTag selections=new ListTag();for(SelectedRailwayRoute selected:selectedRoutes.values()){CompoundTag tag=new CompoundTag();tag.putUUID("Selection",selected.selectionId());tag.putUUID("FleetTrain",selected.fleetTrainId());tag.putLong("SelectedAt",selected.selectedAt());tag.put("Option",writeRouteOption(selected.option()));selections.add(tag);}root.put("SelectedRoutes",selections);
        return root;
    }

    private static CompoundTag writeNode(RailwayNodeReference n){CompoundTag t=new CompoundTag();t.putString("Dimension",n.dimension().toString());t.putInt("X",n.x());t.putInt("Y",n.y());t.putInt("Z",n.z());t.putInt("YOffset",n.yOffsetPixels());return t;}
    private static RailwayNodeReference readNode(CompoundTag t){ResourceLocation d=ResourceLocation.tryParse(t.getString("Dimension"));if(d==null)throw new IllegalArgumentException("dimension");return new RailwayNodeReference(d,t.getInt("X"),t.getInt("Y"),t.getInt("Z"),t.getInt("YOffset"));}
    private static CompoundTag writeEdge(RailwayEdgeReference e){CompoundTag t=new CompoundTag();t.put("First",writeNode(e.first()));t.put("Second",writeNode(e.second()));return t;}
    private static RailwayEdgeReference readEdge(CompoundTag t){return new RailwayEdgeReference(readNode(t.getCompound("First")),readNode(t.getCompound("Second"))).normalized();}
    private static CompoundTag writeTariffLeg(RailwayTariffLeg value){CompoundTag leg=new CompoundTag();leg.putUUID("Network",value.networkId());leg.putInt("OwnerType",value.ownerType().ordinal());leg.putUUID("Owner",value.ownerId());leg.putLong("Tariff",value.tariffPerThousandBlocks());leg.putDouble("Start",value.startDistance());leg.putDouble("End",value.endDistance());leg.putLong("Reserved",value.reservedAmount());return leg;}
    private static RailwayTariffLeg readTariffLeg(CompoundTag leg){return new RailwayTariffLeg(leg.getUUID("Network"),readEnum(leg,"OwnerType",RailwayOwnershipTypes.OwnerType.class,RailwayOwnershipTypes.OwnerType.PLAYER),leg.getUUID("Owner"),leg.getLong("Tariff"),leg.getDouble("Start"),leg.getDouble("End"),leg.getLong("Reserved"));}
    private static CompoundTag writeRouteOption(RailwayRouteOption option){CompoundTag tag=new CompoundTag();tag.putUUID("Id",option.id());tag.putInt("Strategy",option.strategy().ordinal());tag.putUUID("Graph",option.graphId());tag.putUUID("Destination",option.destinationStationId());tag.putBoolean("Backwards",option.backwards());tag.putDouble("Distance",option.distance());tag.putLong("TariffCost",option.tariffCost());tag.putLong("EstimatedTicks",option.estimatedTicks());tag.putLong("Fingerprint",option.topologyFingerprint());ListTag edges=new ListTag();option.edges().forEach(e->edges.add(writeEdge(e)));tag.put("Edges",edges);ListTag contributions=new ListTag();option.contributions().forEach(v->contributions.add(writeTariffLeg(v)));tag.put("Contributions",contributions);return tag;}
    private static RailwayRouteOption readRouteOption(CompoundTag tag){List<RailwayEdgeReference> edges=new ArrayList<>();for(Tag raw:tag.getList("Edges",Tag.TAG_COMPOUND))edges.add(readEdge((CompoundTag)raw));List<RailwayTariffLeg> contributions=new ArrayList<>();for(Tag raw:tag.getList("Contributions",Tag.TAG_COMPOUND))contributions.add(readTariffLeg((CompoundTag)raw));return new RailwayRouteOption(tag.getUUID("Id"),readEnum(tag,"Strategy",RailwayRouteStrategy.class,RailwayRouteStrategy.BALANCED),tag.getUUID("Graph"),tag.getUUID("Destination"),tag.getBoolean("Backwards"),edges,tag.getDouble("Distance"),tag.getLong("TariffCost"),tag.getLong("EstimatedTicks"),contributions,tag.getLong("Fingerprint"));}
    private static CompoundTag writeEndpoint(RailwayEndpoint e){CompoundTag t=new CompoundTag();t.putUUID("MarkerId",e.markerId());t.putString("MarkerDimension",e.markerDimension().toString());t.put("MarkerPos",NbtUtils.writeBlockPos(e.markerPos()));t.putString("TrackDimension",e.trackDimension().toString());t.put("TrackPos",NbtUtils.writeBlockPos(e.trackPos()));t.putBoolean("Front",e.front());t.putUUID("Graph",e.graphId());t.put("Edge",writeEdge(e.edge()));t.putDouble("Position",e.position());return t;}
    private static RailwayEndpoint readEndpoint(CompoundTag t){ResourceLocation md=ResourceLocation.tryParse(t.getString("MarkerDimension")),td=ResourceLocation.tryParse(t.getString("TrackDimension"));if(md==null||td==null)throw new IllegalArgumentException("dimension");return new RailwayEndpoint(t.getUUID("MarkerId"),md,NbtUtils.readBlockPos(t,"MarkerPos").orElseThrow(),td,NbtUtils.readBlockPos(t,"TrackPos").orElseThrow(),t.getBoolean("Front"),t.getUUID("Graph"),readEdge(t.getCompound("Edge")),t.getDouble("Position"));}

    private static <T extends Enum<T>> T readEnum(CompoundTag tag, String key, Class<T> type, T fallback) {
        int ordinal = tag.getInt(key); T[] values = type.getEnumConstants();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : fallback;
    }
}
