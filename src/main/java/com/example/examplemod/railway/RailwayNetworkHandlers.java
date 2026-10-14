package com.example.examplemod.railway;

import com.example.examplemod.block.CityPylonBlock;
import com.example.examplemod.city.City;
import com.example.examplemod.city.CityManager;
import com.example.examplemod.economy.EconomyManager;
import com.example.examplemod.menu.CityPylonMenu;
import com.example.examplemod.network.RailwayCommandPayload;
import com.example.examplemod.network.RailwayMapPayload;
import com.example.examplemod.network.RailwayStatePayload;
import com.example.examplemod.network.RailwayRouteOptionsPayload;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.condition.IdleCargoCondition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public final class RailwayNetworkHandlers {
    private static final long MAP_CACHE_MILLIS = 30_000L;
    /** Unregistered Create trains are a discovery list, not a world dump. */
    private static final int MAX_TRAIN_CANDIDATES = 32;
    private static final Map<net.minecraft.server.MinecraftServer, Map<MapKey, CachedRailMap>> MAP_CACHE =
            new WeakHashMap<>();
    private RailwayNetworkHandlers() {}

    public static void handle(ServerPlayer player, RailwayCommandPayload payload) {
        Context context = context(player, payload.pylonPos());
        if (context == null) return;
        if (payload.action() == RailwayCommandPayload.Action.REQUEST_STATE) { sendState(player, context); return; }
        if (payload.action() == RailwayCommandPayload.Action.REQUEST_MAP) { sendMap(player, context); return; }
        if (payload.action() == RailwayCommandPayload.Action.REQUEST_ROUTES) { sendRouteOptions(player,context,payload.primaryId()); return; }
        if (!context.city.canBuild(player.getUUID())) { feedback(player, "permission_denied"); return; }
        if ((payload.action() == RailwayCommandPayload.Action.REMOVE_LINE
                || payload.action() == RailwayCommandPayload.Action.REMOVE_TRAIN
                || payload.action() == RailwayCommandPayload.Action.REMOVE_STATION)
                && !context.city.isAdmin(player.getUUID())) {
            feedback(player, "permission_denied");
            return;
        }
        RailwayManager manager = context.railway;
        boolean success = switch (payload.action()) {
            case ATTACH_STATION -> payload.primaryId() != null && manager.attachStationToCity(payload.primaryId(), player.getUUID(), context.city.getId());
            case ATTACH_TRAIN -> payload.primaryId() != null && manager.attachTrainToCity(payload.primaryId(), player.getUUID(), context.city.getId());
            case REGISTER_TRAIN -> registerTrain(context, payload.primaryId());
            case CREATE_LINE -> createLine(context, payload);
            case UPDATE_LINE -> updateLine(context, payload);
            case REMOVE_LINE -> removeLine(context, payload.primaryId());
            case ASSIGN_LINE -> assign(context, payload.primaryId(), payload.secondaryId());
            case UNASSIGN_LINE -> unassign(context, payload.primaryId());
            case RESTORE_SCHEDULE -> restore(context, payload.primaryId());
            case REMOVE_TRAIN -> removeTrain(context, payload.primaryId());
            case REMOVE_STATION -> removeStation(context, payload.primaryId());
            case SET_STATION_TYPE -> setStationType(context, payload.primaryId(), payload.typeOrdinal());
            case SET_TRAIN_TYPE -> setTrainType(context, payload.primaryId(), payload.typeOrdinal());
            case REBIND_STATION -> rebindStation(context, payload.primaryId(), payload.secondaryId());
            case REBIND_TRAIN -> rebindTrain(context, payload.primaryId(), payload.secondaryId());
            case LINK_PROJECT -> linkProject(context, payload.primaryId(), payload.secondaryId());
            case SET_NETWORK_ACCESS -> configureNetwork(context,payload,true);
            case SET_NETWORK_TARIFF -> configureNetwork(context,payload,false);
            case SELECT_ROUTE -> selectRoute(context,payload.primaryId(),payload.secondaryId());
            default -> false;
        };
        if(success)com.example.examplemod.auth.ServerTelemetryBridge.emit("RAILWAY_ACTION",player.getUUID(),context.city.getId(),payload.action().name());
        feedback(player, success ? "operation_complete" : "operation_failed");
        sendState(player, context);
    }

    private static boolean createLine(Context c, RailwayCommandPayload p) {
        if (!validStops(c, p.stops())) return false;
        RailwayTypes.LineType type = enumAt(RailwayTypes.LineType.values(), p.typeOrdinal(), RailwayTypes.LineType.MIXED);
        c.railway.createLine(c.city.getId(), p.name(), type, p.cyclic(), p.stops()); return true;
    }
    private static boolean updateLine(Context c, RailwayCommandPayload p) {
        RailLine line = c.railway.line(p.primaryId());
        if (line == null || !line.cityId().equals(c.city.getId()) || !validStops(c, p.stops())) return false;
        return line.update(p.name(), enumAt(RailwayTypes.LineType.values(), p.typeOrdinal(), RailwayTypes.LineType.MIXED), p.cyclic(), p.stops()) && mark(c);
    }
    private static boolean validStops(Context c, List<RailLineStop> stops) {
        return stops.size() >= 2 && stops.size() <= 64 && stops.stream().allMatch(stop -> {
            CityRailStation station = c.railway.station(stop.stationRegistrationId());
            return station != null && c.city.getId().equals(station.cityId());
        });
    }
    private static boolean removeLine(Context c, UUID id) { RailLine line=c.railway.line(id);if(line==null||!line.cityId().equals(c.city.getId()))return false;for(CityFleetTrain train:c.railway.trainsForCity(c.city.getId()))if(id.equals(train.activeLineId()))RailwayOperations.unassign(c.railway,train);c.railway.removeLine(id);return true; }
    private static boolean assign(Context c, UUID trainId, UUID lineId) { CityFleetTrain t=c.railway.train(trainId);RailLine l=c.railway.line(lineId);return t!=null&&l!=null&&c.city.getId().equals(t.cityId())&&c.city.getId().equals(l.cityId())&&RailwayOperations.assign(c.player.serverLevel(),c.railway,t,l); }
    private static boolean unassign(Context c, UUID id) { CityFleetTrain t=c.railway.train(id);return t!=null&&c.city.getId().equals(t.cityId())&&RailwayOperations.unassign(c.railway,t); }
    private static boolean restore(Context c, UUID id) { CityFleetTrain t=c.railway.train(id);return t!=null&&c.city.getId().equals(t.cityId())&&RailwayOperations.restore(c.player.serverLevel(),c.railway,t); }
    private static boolean removeTrain(Context c, UUID id) { CityFleetTrain t=c.railway.train(id);if(t==null||!c.city.getId().equals(t.cityId()))return false;if(c.railway.activeTrip(t.createTrainId())!=null)return false;RailwayOperations.unassign(c.railway,t);c.railway.removeTrain(id);return true; }
    private static boolean removeStation(Context c, UUID id) { CityRailStation s=c.railway.station(id);if(s==null||!c.city.getId().equals(s.cityId()))return false;if(c.railway.linesForCity(c.city.getId()).stream().anyMatch(line->line.stops().stream().anyMatch(stop->stop.stationRegistrationId().equals(id))))return false;if(c.railway.isStationLinkedToProject(id))return false;c.railway.removeStationFromCity(id);return true; }
    private static boolean setStationType(Context c,UUID id,int ordinal){CityRailStation s=c.railway.station(id);if(s==null||!c.city.getId().equals(s.cityId()))return false;s.setType(enumAt(RailwayTypes.StationType.values(),ordinal,RailwayTypes.StationType.MIXED));return mark(c);}
    private static boolean setTrainType(Context c,UUID id,int ordinal){CityFleetTrain t=c.railway.train(id);if(t==null||!c.city.getId().equals(t.cityId()))return false;t.setType(enumAt(RailwayTypes.TrainType.values(),ordinal,RailwayTypes.TrainType.MIXED));return mark(c);}
    private static boolean linkProject(Context c,UUID projectId,UUID stationId){if(c.city.getProject(projectId)==null)return false;if(stationId==null){c.railway.linkProject(projectId,null);return true;}CityRailStation s=c.railway.station(stationId);if(s==null||!c.city.getId().equals(s.cityId()))return false;c.railway.linkProject(projectId,stationId);return true;}
    private static boolean rebindStation(Context c,UUID targetId,UUID replacementId){CityRailStation target=c.railway.station(targetId);return target!=null&&c.city.getId().equals(target.cityId())&&c.railway.rebindStation(targetId,replacementId,c.player.getUUID());}
    private static boolean rebindTrain(Context c,UUID targetId,UUID replacementId){CityFleetTrain target=c.railway.train(targetId);return target!=null&&c.city.getId().equals(target.cityId())&&c.railway.rebindTrain(targetId,replacementId,c.player.getUUID());}
    private static boolean mark(Context c){c.railway.markChanged();return true;}
    /**
     * Registers a live Create train into CCEC from a city pylon context. Registration is the
     * gate for routes, tariffs, ownership and freight dispatch, so it immediately attaches the
     * train to the pylon's city: the existing city-scoped type/line/remove operations then apply
     * to it unchanged instead of needing a second personal-train code path.
     */
    private static boolean registerTrain(Context c, UUID createId) {
        if (createId == null) return false;
        Train actual = CreateRailwayAdapter.train(createId);
        if (actual == null) return false;
        if (c.railway.trainByCreateId(createId) != null) return false;
        CityFleetTrain created = c.railway.registerTrain(createId, c.player.getUUID(), actual.name.getString());
        return c.railway.attachTrainToCity(created.fleetTrainId(), c.player.getUUID(), c.city.getId());
    }
    private static boolean configureNetwork(Context c,RailwayCommandPayload p,boolean access){
        OwnedRailwayNetwork network=c.railway.ownedNetwork(p.primaryId());if(network==null)return false;
        UUID owner=network.ownerType()==RailwayOwnershipTypes.OwnerType.CITY?c.city.getId():c.player.getUUID();
        if(network.ownerType()==RailwayOwnershipTypes.OwnerType.CITY&&!c.city.isAdmin(c.player.getUUID()))return false;
        Long tariff=null;if(!access)try{tariff=Math.max(0L,Long.parseLong(p.name()));}catch(NumberFormatException ex){return false;}
        return c.railway.configureNetwork(network.id(),network.ownerType(),owner,
                access?enumAt(RailwayOwnershipTypes.Access.values(),p.typeOrdinal(),network.access()):null,
                tariff,null);
    }
    private static void sendRouteOptions(ServerPlayer player,Context c,UUID fleetId){
        CityFleetTrain fleet=c.railway.train(fleetId);Train train=fleet==null?null:CreateRailwayAdapter.train(fleet.createTrainId());
        if(fleet==null||train==null||!c.city.getId().equals(fleet.cityId())||train.getCurrentStation()==null||train.navigation.destination==null){PacketDistributor.sendToPlayer(player,new RailwayRouteOptionsPayload(c.pylonPos,fleetId==null?new UUID(0,0):fleetId,"","",List.of(),"not_ready"));return;}
        var source=train.getCurrentStation();var destination=train.navigation.destination;train.navigation.cancelNavigation();train.speed=0;train.targetSpeed=0;
        var routes=RailwayRoutePlanner.plan(train,destination,c.railway,RailwayOwnershipTypes.OwnerType.CITY,c.city.getId(),CityManager.get(c.player.serverLevel()));c.railway.proposeRoutes(fleetId,routes);
        PacketDistributor.sendToPlayer(player,new RailwayRouteOptionsPayload(c.pylonPos,fleetId,source.name,destination.name,routes,routes.isEmpty()?"no_route":""));
    }
    private static boolean selectRoute(Context c,UUID fleetId,UUID optionId){CityFleetTrain fleet=c.railway.train(fleetId);if(fleet==null||!c.city.getId().equals(fleet.cityId())||!c.railway.selectRoute(fleetId,optionId,c.player.serverLevel().getGameTime()))return false;Train train=CreateRailwayAdapter.train(fleet.createTrainId());SelectedRailwayRoute selected=c.railway.selectedRoute(fleetId);if(train==null||selected==null){c.railway.consumeSelectedRoute(fleetId);return false;}var destination=CreateRailwayAdapter.station(selected.option().destinationStationId());var path=destination==null?null:RailwayTripAccountingService.selectedPath(train,destination,selected.option());if(path==null){return false;}return train.navigation.startNavigation(path)>=0;}

    private static void sendState(ServerPlayer player, Context c) {
        List<RailwayStationSnapshot> stations=new ArrayList<>();
        for(CityRailStation s:c.railway.stationsForCity(c.city.getId()))stations.add(stationSnapshot(s,true,c.railway));
        for(CityRailStation s:c.railway.personalStations(player.getUUID()))stations.add(stationSnapshot(s,false,c.railway));
        List<RailwayTrainSnapshot> trains=new ArrayList<>();
        for(CityFleetTrain t:c.railway.trainsForCity(c.city.getId()))trains.add(trainSnapshot(t,c,true));
        for(CityFleetTrain t:c.railway.personalTrains(player.getUUID()))trains.add(trainSnapshot(t,c,false));
        List<RailwayTrainCandidate> candidates=trainCandidates(c);
        List<RailwayLineSnapshot> lines=new ArrayList<>();
        for(RailLine line:c.railway.linesForCity(c.city.getId())){
            int assigned=(int)c.railway.trainsForCity(c.city.getId()).stream().filter(t->line.lineId().equals(t.activeLineId())).count();
            boolean missing=line.stops().stream().anyMatch(stop->{var s=c.railway.station(stop.stationRegistrationId());return s==null||!c.city.getId().equals(s.cityId())||CreateRailwayAdapter.station(s.createStationId())==null;});
            long healthy=c.railway.trainsForCity(c.city.getId()).stream().filter(t->{Train actual=CreateRailwayAdapter.train(t.createTrainId());return line.lineId().equals(t.activeLineId())&&actual!=null&&!t.automationSuspended()&&isHealthyStatus(trainStatus(actual,t));}).count();
            String status=missing?"route_broken":assigned==0?"unserved":healthy==assigned?"running":healthy>0?"partial":"attention";
            lines.add(new RailwayLineSnapshot(line.lineId(),line.name(),line.type(),line.cyclic(),line.revision(),line.stops(),status,assigned));
        }
        List<RailwayOwnershipSnapshot> ownership=new ArrayList<>();for(OwnedRailwayNetwork network:c.railway.ownedNetworksFor(c.city.getId(),player.getUUID())){var sections=c.railway.sectionsForNetwork(network.id());ownership.add(new RailwayOwnershipSnapshot(network.id(),network.name(),network.ownerType(),network.access(),network.freightTariff(),sections.size(),(int)sections.stream().filter(s->s.status()==RailwayOwnershipTypes.SectionStatus.ONLINE).count(),sections.stream().mapToDouble(OwnedRailwaySection::length).sum()));}
        Set<UUID> visibleNetworkIds=ownership.stream().map(RailwayOwnershipSnapshot::networkId).collect(java.util.stream.Collectors.toSet());List<RailwaySectionSnapshot> ownedSections=c.railway.ownedSections().stream().filter(s->visibleNetworkIds.contains(s.networkId())).map(s->new RailwaySectionSnapshot(s.id(),s.networkId(),s.length(),s.status())).toList();
        PacketDistributor.sendToPlayer(player,new RailwayStatePayload(c.city.getPylonPos(),stations,trains,lines,ownership,ownedSections,candidates));
    }
    /** Create trains that exist but have no CCEC registration: the "register me" discovery list. */
    private static List<RailwayTrainCandidate> trainCandidates(Context c){
        List<RailwayTrainCandidate> result=new ArrayList<>();
        boolean canBuild=c.city.canBuild(c.player.getUUID());
        for(var entry:Create.RAILWAYS.trains.entrySet()){
            if(result.size()>=MAX_TRAIN_CANDIDATES)break;
            if(c.railway.trainByCreateId(entry.getKey())!=null)continue;
            Train actual=entry.getValue();
            var pos=trainPosition(actual);
            if(pos.dimension()==null)continue;
            var station=actual.getCurrentStation();
            result.add(new RailwayTrainCandidate(entry.getKey(),actual.name.getString(),pos.dimension(),
                    pos.x,pos.z,station==null?"":station.name,canBuild));
        }
        return List.copyOf(result);
    }
    /** Token of the oldest shipment this train still carries, empty when it carries none. */
    private static String activeShipment(ServerPlayer player,CityFleetTrain f){
        for(var shipment: EconomyManager.get(player.serverLevel()).shipments()){
            if(!f.fleetTrainId().equals(shipment.fleetTrainId()))continue;
            if(!shipment.active())continue;
            return shipment.status().name().toLowerCase(Locale.ROOT);
        }
        return "";
    }
    private static RailwayTrainSnapshot trainSnapshot(CityFleetTrain f,Context c,boolean city){
        RailwayManager manager=c.railway;
        Train t=CreateRailwayAdapter.train(f.createTrainId());
        String line=f.activeLineId()==null?"":Optional.ofNullable(manager.line(f.activeLineId())).map(RailLine::name).orElse("");
        String owner=city?c.city.getName():com.example.examplemod.auth.PlayerNames.name(c.player);
        RailwayTripLedger trip=manager.activeTrip(f.createTrainId());
        String tripToken=trip==null?"":trip.status().name().toLowerCase(Locale.ROOT);
        String shipment=activeShipment(c.player,f);
        boolean canManage=city?c.city.canBuild(c.player.getUUID()):true;
        if(t==null)return new RailwayTrainSnapshot(f.fleetTrainId(),f.lastKnownName(),f.type(),f.activeLineId(),line,"missing",city,f.automationSuspended(),null,0,0,"",0,
                com.example.examplemod.status.CcecStatusResolver.railway(manager,f,null,"missing",!shipment.isEmpty()),
                f.createTrainId(),owner,"",tripToken,shipment,canManage);
        String status=trainStatus(t,f);String next=t.navigation.destination==null?"":t.navigation.destination.name;var pos=trainPosition(t);
        var current=t.getCurrentStation();
        return new RailwayTrainSnapshot(f.fleetTrainId(),t.name.getString(),f.type(),f.activeLineId(),line,status,city,f.automationSuspended(),pos.dimension,pos.x,pos.z,next,t.speed*20.0,
                com.example.examplemod.status.CcecStatusResolver.railway(manager,f,t,status,!shipment.isEmpty()),
                f.createTrainId(),owner,current==null?"":current.name,tripToken,shipment,canManage);
    }
    private static RailwayStationSnapshot stationSnapshot(CityRailStation s,boolean city,RailwayManager manager){var actual=CreateRailwayAdapter.station(s.createStationId());return new RailwayStationSnapshot(s.registrationId(),actual==null?s.lastKnownName():actual.name,s.type(),actual!=null,city,s.lastKnownDimension(),s.lastKnownPos(),manager.projectForStation(s.registrationId()));}
    private static String trainStatus(Train t,CityFleetTrain f){if(t.derailed)return"derailed";if(f.automationSuspended())return"schedule_changed";if(t.status.conductor)return"missing_conductor";if(t.status.track)return"track_issue";if(t.status.navigation)return"no_route";if(t.runtime.getSchedule()==null)return"no_schedule";if(t.runtime.paused)return"manual";if(t.getCurrentStation()!=null&&t.runtime.state== ScheduleRuntime.State.POST_TRANSIT){try{var e=t.runtime.getSchedule().entries.get(t.runtime.currentEntry);if(!e.conditions.isEmpty()&&!e.conditions.getFirst().isEmpty()&&e.conditions.getFirst().get(Math.min(t.runtime.conditionProgress.getFirst(),e.conditions.getFirst().size()-1)) instanceof IdleCargoCondition)return"cargo";}catch(RuntimeException ignored){}return"waiting";}if(t.getCurrentStation()!=null)return"at_station";return"in_transit";}
    private static boolean isHealthyStatus(String status){return status.equals("in_transit")||status.equals("at_station")||status.equals("waiting")||status.equals("cargo");}
    private record Position(net.minecraft.resources.ResourceLocation dimension,double x,double z){}
    private static Position trainPosition(Train t){if(t.graph==null||t.carriages.isEmpty())return new Position(null,0,0);var p=t.carriages.getFirst().getLeadingPoint();if(p==null||p.node1==null||p.edge==null||p.edge.isInterDimensional())return new Position(null,0,0);Vec3 v=p.getPosition(t.graph);return new Position(p.node1.getLocation().getDimension().location(),v.x,v.z);}

    private static void sendMap(ServerPlayer player, Context c) {
        var serverCache = MAP_CACHE.computeIfAbsent(player.getServer(), ignored -> new HashMap<>());
        MapKey key = new MapKey(c.city.getId(), player.serverLevel().dimension().location());
        CachedRailMap cached = serverCache.get(key);
        long now = System.currentTimeMillis();
        if (cached == null || !cached.center().equals(c.city.getPylonPos())
                || now - cached.createdAt() >= MAP_CACHE_MILLIS) {
            cached = new CachedRailMap(c.city.getPylonPos().immutable(), now,
                    buildMapSegments(player, c.city.getPylonPos()));
            serverCache.put(key, cached);
        }
        PacketDistributor.sendToPlayer(player, new RailwayMapPayload(
                c.city.getPylonPos(), player.serverLevel().dimension().location(), cached.segments(),
                mapNetworks(player,c),mapSections(player,c),mapRoutes(player,c)));
    }

    private static List<RailwayMapPayload.NetworkView> mapNetworks(ServerPlayer player,Context c){
        CityManager cities=CityManager.get(player.getServer().overworld());List<RailwayMapPayload.NetworkView> result=new ArrayList<>();
        for(OwnedRailwayNetwork network:c.railway.ownedNetworks()){if(result.size()>=RailwayMapPayload.MAX_NETWORKS)break;var sections=c.railway.sectionsForNetwork(network.id());String owner=network.ownerType()==RailwayOwnershipTypes.OwnerType.CITY?Optional.ofNullable(cities.getCity(network.ownerId())).map(City::getName).orElse(shortId(network.ownerId())):com.example.examplemod.auth.PlayerNames.name(player.getServer(), network.ownerId());result.add(new RailwayMapPayload.NetworkView(network.id(),network.name(),network.ownerType(),network.ownerId(),owner,network.access(),network.freightTariff(),sections.stream().mapToDouble(OwnedRailwaySection::length).sum(),sections.size(),(int)sections.stream().filter(s->s.status()==RailwayOwnershipTypes.SectionStatus.ONLINE).count()));}
        return List.copyOf(result);
    }
    private static List<RailwayMapPayload.SectionView> mapSections(ServerPlayer player,Context c){
        ResourceLocation dimension=player.serverLevel().dimension().location();List<RailwayMapPayload.SectionView> result=new ArrayList<>();int[] budget={RailwayMapPayload.MAX_SEMANTIC_SEGMENTS};
        for(OwnedRailwaySection section:c.railway.ownedSections()){if(result.size()>=RailwayMapPayload.MAX_SECTIONS)break;List<RailwayMapPayload.TrackSegment> geometry=edgeSegments(section.first().graphId(),section.path(),dimension,budget);if(!geometry.isEmpty())result.add(new RailwayMapPayload.SectionView(section.id(),section.networkId(),section.length(),section.status(),geometry));if(budget[0]<=0)break;}
        return List.copyOf(result);
    }
    private static List<RailwayMapPayload.RouteView> mapRoutes(ServerPlayer player,Context c){
        ResourceLocation dimension=player.serverLevel().dimension().location();List<RailwayMapPayload.RouteView> result=new ArrayList<>();int[] budget={RailwayMapPayload.MAX_SEMANTIC_SEGMENTS};
        for(CityFleetTrain fleet:c.railway.trainsForCity(c.city.getId())){if(result.size()>=RailwayMapPayload.MAX_ROUTES)break;SelectedRailwayRoute selected=c.railway.selectedRoute(fleet.fleetTrainId());if(selected!=null){var option=selected.option();var geometry=edgeSegments(option.graphId(),option.edges(),dimension,budget);if(!geometry.isEmpty())result.add(new RailwayMapPayload.RouteView(selected.selectionId(),fleet.fleetTrainId(),true,"selected",option.distance(),option.tariffCost(),option.estimatedTicks(),geometry));}}
        for(RailwayTripLedger trip:c.railway.tripLedgers()){if(result.size()>=RailwayMapPayload.MAX_ROUTES)break;if(!trip.payerId().equals(c.city.getId()))continue;RouteReservation reservation=c.railway.reservationForTrip(trip.id());if(reservation==null)continue;var geometry=edgeSegments(reservation.graphId(),reservation.orderedEdges(),dimension,budget);if(!geometry.isEmpty())result.add(new RailwayMapPayload.RouteView(reservation.id(),trip.fleetTrainId(),false,reservation.status().name().toLowerCase(Locale.ROOT),trip.plannedDistance(),trip.reservedAmount(),0,geometry));if(budget[0]<=0)break;}
        return List.copyOf(result);
    }
    private static List<RailwayMapPayload.TrackSegment> edgeSegments(UUID graphId,List<RailwayEdgeReference> refs,ResourceLocation dimension,int[] budget){TrackGraph graph=Create.RAILWAYS.trackNetworks.get(graphId);if(graph==null||budget[0]<=0)return List.of();List<RailwayMapPayload.TrackSegment> result=new ArrayList<>();for(RailwayEdgeReference ref:refs){if(budget[0]<=0)break;if(!ref.first().dimension().equals(dimension)||!ref.second().dimension().equals(dimension))continue;TrackNode a=graph.locateNode(ref.first().resolve()),b=graph.locateNode(ref.second().resolve());if(a==null||b==null)continue;TrackEdge edge=graph.getConnectionsFrom(a).get(b);if(edge==null||edge.isInterDimensional())continue;int steps=edge.isTurn()?Math.max(2,(int)Math.ceil(edge.getLength()/2d)):1;Vec3 previous=edge.getPosition(graph,0);for(int i=1;i<=steps&&budget[0]>0;i++){Vec3 next=edge.getPosition(graph,i/(double)steps);result.add(new RailwayMapPayload.TrackSegment((float)previous.x,(float)previous.z,(float)next.x,(float)next.z));previous=next;budget[0]--;}}return List.copyOf(result);}
    private static String shortId(UUID id){return id.toString().substring(0,8);}

    private static List<RailwayMapPayload.TrackSegment> buildMapSegments(
            ServerPlayer player, net.minecraft.core.BlockPos center
    ) {
        List<RailwayMapPayload.TrackSegment> segments = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        double radius = 768;
        graphLoop:
        for (TrackGraph graph : Create.RAILWAYS.trackNetworks.values()) {
            for (TrackNodeLocation loc : graph.getNodes()) {
                if (!loc.getDimension().location().equals(player.serverLevel().dimension().location())) continue;
                TrackNode node = graph.locateNode(loc);
                var connections = graph.getConnectionsFrom(node);
                if (connections == null) continue;
                for (var entry : connections.entrySet()) {
                    TrackNode other = entry.getKey();
                    if (!other.getLocation().getDimension().equals(loc.getDimension())) continue;
                    String edgeKey = Math.min(node.getNetId(), other.getNetId()) + ":"
                            + Math.max(node.getNetId(), other.getNetId());
                    if (!seen.add(graph.id + ":" + edgeKey)) continue;
                    TrackEdge edge = entry.getValue();
                    if (edge.isInterDimensional()) continue;
                    Vec3 a = loc.getLocation(), z = other.getLocation().getLocation();
                    if (Math.min(Math.abs(a.x - center.getX()), Math.abs(z.x - center.getX())) > radius
                            || Math.min(Math.abs(a.z - center.getZ()), Math.abs(z.z - center.getZ())) > radius) continue;
                    int steps = edge.isTurn() ? Math.max(2, (int) Math.ceil(edge.getLength() / 2.0)) : 1;
                    Vec3 previous = edge.getPosition(graph, 0);
                    for (int i = 1; i <= steps; i++) {
                        Vec3 next = edge.getPosition(graph, i / (double) steps);
                        segments.add(new RailwayMapPayload.TrackSegment(
                                (float) previous.x, (float) previous.z, (float) next.x, (float) next.z));
                        previous = next;
                        if (segments.size() >= 20_000) break graphLoop;
                    }
                }
            }
        }
        return List.copyOf(segments);
    }

    private record MapKey(UUID cityId, net.minecraft.resources.ResourceLocation dimension) {}
    private record CachedRailMap(net.minecraft.core.BlockPos center, long createdAt,
                                 List<RailwayMapPayload.TrackSegment> segments) {}

    private static Context context(ServerPlayer p,net.minecraft.core.BlockPos pos){if(!(p.containerMenu instanceof CityPylonMenu menu)||!menu.getPylonPos().equals(pos)||p.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>64||!(p.serverLevel().getBlockState(pos).getBlock() instanceof CityPylonBlock))return null;CityManager cm=CityManager.get(p.getServer().overworld());City city=cm.getCityByPylon(p.serverLevel().dimension().location(),pos);return city==null||!city.hasMember(p.getUUID())?null:new Context(p,city,RailwayManager.get(p.serverLevel()),pos);}

    /**
     * Sends railway state and map for the player's own city, with no pylon in reach.
     *
     * <p>The pylon-bound {@link #context} checks an open container, a distance limit
     * and the block itself. None of that can hold when the map is opened from a
     * key binding anywhere on the map, and none of it is the property that grants
     * access: membership is. So the context is rebuilt from the member's city and
     * the city pylon is used purely as the anchor the client already expects.
     *
     * <p>The distance and block checks are dropped, not relaxed: no command is
     * reachable through this path, only the two read-only snapshots.
     */
    public static boolean sendMemberView(ServerPlayer player) {
        City city = CityManager.get(player.getServer().overworld()).getMemberCity(player.getUUID());
        if (city == null) {
            return false;
        }
        Context c = new Context(player, city, RailwayManager.get(player.serverLevel()), city.getPylonPos());
        sendState(player, c);
        sendMap(player, c);
        return true;
    }
    private static void feedback(ServerPlayer p,String key){p.displayClientMessage(Component.translatable("message.createcityexpansioncore.railway."+key),true);}
    private static <T>T enumAt(T[]v,int i,T f){return i>=0&&i<v.length?v[i]:f;}
    private record Context(ServerPlayer player,City city,RailwayManager railway,net.minecraft.core.BlockPos pylonPos){}
}
