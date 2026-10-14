package com.example.examplemod.railway;

import com.example.examplemod.economy.EconomyManager;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.createmod.catnip.data.Couple;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative station-to-next-station freight trip accounting. */
public final class RailwayTripAccountingService {
    private RailwayTripAccountingService() {}

    public static void tick(MinecraftServer server) {
        ServerLevel level=server.overworld(); RailwayManager railway=RailwayManager.get(level); EconomyManager economy=EconomyManager.get(level);
        long now=level.getGameTime();
        for(RailwayTripLedger trip:railway.tripLedgers())if(trip.active())updateActive(railway,economy,trip,now);
        for(CityFleetTrain fleet:railway.trainsForCityIdsAndPersonal()){
            if(fleet.type()!=RailwayTypes.TrainType.FREIGHT&&fleet.type()!=RailwayTypes.TrainType.MIXED)continue;
            Train train=CreateRailwayAdapter.train(fleet.createTrainId());
            if(train==null||railway.activeTrip(fleet.createTrainId())!=null)continue;
            GlobalStation source=train.getCurrentStation(),destination=train.navigation.destination;
            if(source==null||destination==null||source.id.equals(destination.id))continue;
            SelectedRailwayRoute selected=railway.selectedRoute(fleet.fleetTrainId());
            DiscoveredPath discovered=selected==null?train.navigation.findPathTo(destination,Double.MAX_VALUE):selectedPath(train,destination,selected.option());
            if(selected!=null&&discovered==null){train.navigation.cancelNavigation();train.speed=0;train.targetSpeed=0;continue;}
            if(discovered==null)continue;
            double planned=Math.abs(train.navigation.distanceStartedAt);
            if(planned<=0)planned=Math.abs(discovered.distance);
            if(planned<=0)continue;
            RailwayOwnershipTypes.OwnerType payerType=fleet.cityId()==null?RailwayOwnershipTypes.OwnerType.PLAYER:RailwayOwnershipTypes.OwnerType.CITY;
            UUID payer=fleet.cityId()==null?fleet.registeredBy():fleet.cityId();
            TravellingPoint leading=discovered.distance<0?train.carriages.getLast().getTrailingPoint():train.carriages.getFirst().getLeadingPoint();
            List<RailwayEdgeReference> route=new ArrayList<>();
            route.add(edge(leading.node1,leading.node2));for(RailwayEdgeReference edge:route(discovered.path))if(!route.getLast().equals(edge))route.add(edge);
            List<RailwayTariffLeg> legs=selected==null?snapshotLegs(railway.ownedSections(),railway.ownedNetworks(),route,planned,payerType,payer):selected.option().contributions();
            long reserve;
            try{reserve=legs.stream().map(RailwayTariffLeg::reservedAmount).reduce(0L,Math::addExact);}
            catch(ArithmeticException ex){train.navigation.cancelNavigation();continue;}
            UUID tripId=UUID.randomUUID();
            if(!economy.reserveTransport(tripId,payerType==RailwayOwnershipTypes.OwnerType.CITY,payer,reserve)){
                train.navigation.cancelNavigation();
                continue;
            }
            railway.addTrip(new RailwayTripLedger(tripId,fleet.createTrainId(),fleet.fleetTrainId(),payerType,payer,
                    source.id,destination.id,planned,reserve,now,legs));
            railway.addReservation(new RouteReservation(UUID.randomUUID(),tripId,train.graph.id,route,legs));
            if(selected!=null)railway.consumeSelectedRoute(fleet.fleetTrainId());
            if(train.navigation.startNavigation(discovered)<0){
                RouteReservation reservation=railway.reservationForTrip(tripId);if(reservation!=null)reservation.close(RouteReservation.Status.CANCELLED,now);
                RailwayTripLedger failed=railway.activeTrip(fleet.createTrainId());if(failed!=null)settle(railway,economy,failed,RailwayTripLedger.Status.INTERRUPTED,now);
            }
        }
    }

    private static void updateActive(RailwayManager railway,EconomyManager economy,RailwayTripLedger trip,long now){
        Train train=CreateRailwayAdapter.train(trip.createTrainId());
        RouteReservation reservation=railway.reservationForTrip(trip.id());
        if(train==null){if(reservation!=null)reservation.close(RouteReservation.Status.CANCELLED,now);settle(railway,economy,trip,RailwayTripLedger.Status.INTERRUPTED,now);return;}
        if(reservation==null){train.navigation.cancelNavigation();settle(railway,economy,trip,RailwayTripLedger.Status.ROUTE_INVALIDATED,now);return;}
        double remaining=Math.max(0,Math.abs(train.navigation.distanceToDestination));
        if(trip.progress(trip.plannedDistance()-remaining))railway.markChanged();
        if(reservation.active()){
            TravellingPoint point=train.navigation.destinationBehindTrain?train.carriages.getLast().getTrailingPoint():train.carriages.getFirst().getLeadingPoint();
            RouteReservation.Observation observation=reservation.observe(edge(point.node1,point.node2),topologyExists(train,reservation));
            if(observation==RouteReservation.Observation.ADVANCED)railway.markChanged();
            if(observation==RouteReservation.Observation.REROUTED||observation==RouteReservation.Observation.TOPOLOGY_CHANGED){
                reservation.close(RouteReservation.Status.ROUTE_INVALIDATED,now);train.navigation.cancelNavigation();train.speed=0;train.targetSpeed=0;
                settle(railway,economy,trip,RailwayTripLedger.Status.ROUTE_INVALIDATED,now);return;
            }
        }
        GlobalStation current=train.getCurrentStation();
        if(current!=null&&current.id.equals(trip.destinationStationId())){
            trip.progress(trip.plannedDistance());reservation.close(RouteReservation.Status.COMPLETED,now);settle(railway,economy,trip,RailwayTripLedger.Status.COMPLETED,now);
        }else if(train.navigation.destination==null){
            reservation.close(RouteReservation.Status.CANCELLED,now);
            settle(railway,economy,trip,RailwayTripLedger.Status.INTERRUPTED,now);
        }
    }

    private static void settle(RailwayManager railway,EconomyManager economy,RailwayTripLedger trip,RailwayTripLedger.Status status,long now){
        if(!trip.active())return;
        Map<Account,Long> totals=new LinkedHashMap<>();
        for(RailwayTariffLeg leg:trip.legs())totals.merge(new Account(leg.ownerType(),leg.ownerId()),leg.charge(leg.usedLength(trip.actualDistance())),Math::addExact);
        List<EconomyManager.TransportPayout> payouts=totals.entrySet().stream().map(e->new EconomyManager.TransportPayout(e.getKey().type==RailwayOwnershipTypes.OwnerType.CITY,e.getKey().id,e.getValue())).toList();
        if(economy.settleTransport(trip.id(),trip.payerType()==RailwayOwnershipTypes.OwnerType.CITY,trip.payerId(),trip.reservedAmount(),payouts)
                &&trip.close(status,now))railway.markChanged();
    }

    private record Account(RailwayOwnershipTypes.OwnerType type,UUID id){}

    static List<RailwayEdgeReference> route(List<Couple<TrackNode>> path){
        return path.stream().map(pair->edge(pair.getFirst(),pair.getSecond())).toList();
    }

    private static RailwayEdgeReference edge(TrackNode first,TrackNode second){return new RailwayEdgeReference(RailwayNodeReference.of(first.getLocation()),RailwayNodeReference.of(second.getLocation())).normalized();}
    @org.jetbrains.annotations.Nullable public static DiscoveredPath selectedPath(Train train,GlobalStation destination,RailwayRouteOption option){
        if(train.graph==null||!RailwayRoutePlanner.selectionCurrent(option,train.graph.id,destination.id,RailwayRoutePlanner.fingerprint(option.edges(),train.graph)))return null;
        TravellingPoint point=option.backwards()?train.carriages.getLast().getTrailingPoint():train.carriages.getFirst().getLeadingPoint();TrackNode current=option.backwards()?point.node1:point.node2;
        if(option.edges().isEmpty()||!edge(point.node1,point.node2).equals(option.edges().getFirst()))return null;
        List<Couple<TrackNode>> path=new ArrayList<>();for(int i=1;i<option.edges().size();i++){RailwayEdgeReference ref=option.edges().get(i);TrackNode a=train.graph.locateNode(ref.first().resolve()),b=train.graph.locateNode(ref.second().resolve());if(a==null||b==null)return null;TrackNode next;if(a==current)next=b;else if(b==current)next=a;else return null;TrackEdge actual=train.graph.getConnectionsFrom(current).get(next);if(actual==null)return null;path.add(Couple.create(current,next));current=next;}
        return new DiscoveredPath(option.backwards()?-option.distance():option.distance(),0,path,destination);
    }
    static boolean topologyExists(Train train,RouteReservation reservation){
        if(train.graph==null||!train.graph.id.equals(reservation.graphId()))return false;
        for(int i=reservation.cursor();i<reservation.orderedEdges().size();i++){
            RailwayEdgeReference edge=reservation.orderedEdges().get(i);TrackNode first=train.graph.locateNode(edge.first().resolve()),second=train.graph.locateNode(edge.second().resolve());
            if(first==null||second==null||train.graph.getConnectionsFrom(first).get(second)==null)return false;
        }
        return true;
    }

    /** Builds immutable tariff snapshots. Private and self-owned infrastructure is intentionally free in this pass. */
    public static List<RailwayTariffLeg> snapshotLegs(List<OwnedRailwaySection> sections,List<OwnedRailwayNetwork> networks,
                                                       List<RailwayEdgeReference> route,double planned,
                                                       RailwayOwnershipTypes.OwnerType payerType,UUID payerId){
        Map<UUID,OwnedRailwayNetwork> byId=new LinkedHashMap<>();networks.forEach(n->byId.put(n.id(),n));
        List<RailwayTariffLeg> result=new ArrayList<>();int edgeCount=Math.max(1,route.size());
        for(OwnedRailwaySection section:sections){
            OwnedRailwayNetwork network=byId.get(section.networkId());
            if(network==null||network.access()!=RailwayOwnershipTypes.Access.PUBLIC||network.freightTariff()==0||
                    network.ownerType()==payerType&&network.ownerId().equals(payerId))continue;
            int first=Integer.MAX_VALUE,last=-1;
            for(RailwayEdgeReference edge:section.path()){int index=route.indexOf(edge.normalized());if(index<0){first=Integer.MAX_VALUE;last=-1;break;}first=Math.min(first,index);last=Math.max(last,index);}
            if(last<0)continue;
            double start=planned*first/edgeCount;
            double end=Math.min(planned,start+section.length());
            if(end<=start)continue;
            RailwayTariffLeg draft=new RailwayTariffLeg(network.id(),network.ownerType(),network.ownerId(),network.freightTariff(),start,end,0);
            result.add(new RailwayTariffLeg(network.id(),network.ownerType(),network.ownerId(),network.freightTariff(),start,end,draft.charge(end-start)));
        }
        return List.copyOf(result);
    }
}
