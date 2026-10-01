package com.example.examplemod.railway;

import com.example.examplemod.city.CityAccessPolicy;
import com.example.examplemod.city.CityManager;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.track.TrackMaterial;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import net.createmod.catnip.data.Couple;

import java.util.*;

/** Bounded simple-path planner using Create's directed edge travel and bogey compatibility rules. */
public final class RailwayRoutePlanner {
    private static final int MAX_EXPANSIONS=30_000,MAX_EDGES=256,MAX_CANDIDATES=24;
    private RailwayRoutePlanner(){}
    private record Candidate(boolean backwards,List<Couple<TrackNode>> nodes,List<RailwayEdgeReference> edges,double distance){}

    public static List<RailwayRouteOption> plan(Train train,GlobalStation destination,RailwayManager manager,
                                                 RailwayOwnershipTypes.OwnerType payerType,UUID payerId){
        return plan(train,destination,manager,payerType,payerId,null);
    }
    public static List<RailwayRouteOption> plan(Train train,GlobalStation destination,RailwayManager manager,
                                                 RailwayOwnershipTypes.OwnerType payerType,UUID payerId,CityManager cities){
        if(train.graph==null||destination==null)return List.of();Set<TrackMaterial.TrackType> valid=validTypes(train);if(valid.isEmpty())return List.of();
        List<Candidate> candidates=new ArrayList<>();if(train.hasForwardConductor()||train.runtime.paused)enumerate(train,destination,manager,payerType,payerId,cities,false,valid,candidates);
        if((train.doubleEnded&&train.hasBackwardConductor())||train.runtime.paused)enumerate(train,destination,manager,payerType,payerId,cities,true,valid,candidates);
        Map<String,RailwayRouteOption> unique=new LinkedHashMap<>();double speed=Math.max(1d/16d,train.maxSpeed());
        for(Candidate c:candidates){List<RailwayTariffLeg> legs=RailwayTripAccountingService.snapshotLegs(manager.ownedSections(),manager.ownedNetworks(),c.edges,c.distance,payerType,payerId);long tariff=legs.stream().map(RailwayTariffLeg::reservedAmount).reduce(0L,Math::addExact);long eta=Math.max(1,Math.round(c.distance/speed));long fingerprint=fingerprint(c.edges,train.graph);
            RailwayRouteOption raw=new RailwayRouteOption(UUID.randomUUID(),RailwayRouteStrategy.FASTEST,train.graph.id,destination.id,c.backwards,c.edges,c.distance,tariff,eta,legs,fingerprint);unique.putIfAbsent(raw.pathKey(),raw);}
        List<RailwayRouteOption> pool=new ArrayList<>(unique.values());if(pool.isEmpty())return List.of();
        RailwayRouteOption cheapest=pool.stream().min(Comparator.comparingLong(RailwayRouteOption::tariffCost).thenComparingLong(RailwayRouteOption::estimatedTicks)).orElseThrow();
        RailwayRouteOption fastest=pool.stream().min(Comparator.comparingLong(RailwayRouteOption::estimatedTicks).thenComparingLong(RailwayRouteOption::tariffCost)).orElseThrow();
        double minPrice=pool.stream().mapToLong(RailwayRouteOption::tariffCost).min().orElse(0),maxPrice=pool.stream().mapToLong(RailwayRouteOption::tariffCost).max().orElse(0),minTime=pool.stream().mapToLong(RailwayRouteOption::estimatedTicks).min().orElse(0),maxTime=pool.stream().mapToLong(RailwayRouteOption::estimatedTicks).max().orElse(0);
        RailwayRouteOption balanced=pool.stream().min(Comparator.comparingDouble(r->balancedScore(r,minPrice,maxPrice,minTime,maxTime))).orElseThrow();
        LinkedHashMap<String,RailwayRouteOption> selected=new LinkedHashMap<>();add(selected,cheapest,RailwayRouteStrategy.CHEAPEST);add(selected,fastest,RailwayRouteStrategy.FASTEST);add(selected,balanced,RailwayRouteStrategy.BALANCED);return List.copyOf(selected.values());
    }
    public static double balancedScore(RailwayRouteOption route,double minPrice,double maxPrice,double minTime,double maxTime){double price=maxPrice==minPrice?0:(route.tariffCost()-minPrice)/(maxPrice-minPrice);double time=maxTime==minTime?0:(route.estimatedTicks()-minTime)/(maxTime-minTime);return (price+time)/2d;}
    public static List<RailwayRouteOption> selectStrategies(List<RailwayRouteOption> pool){if(pool.isEmpty())return List.of();double minP=pool.stream().mapToLong(RailwayRouteOption::tariffCost).min().orElse(0),maxP=pool.stream().mapToLong(RailwayRouteOption::tariffCost).max().orElse(0),minT=pool.stream().mapToLong(RailwayRouteOption::estimatedTicks).min().orElse(0),maxT=pool.stream().mapToLong(RailwayRouteOption::estimatedTicks).max().orElse(0);var cheap=pool.stream().min(Comparator.comparingLong(RailwayRouteOption::tariffCost).thenComparingLong(RailwayRouteOption::estimatedTicks)).orElseThrow();var fast=pool.stream().min(Comparator.comparingLong(RailwayRouteOption::estimatedTicks).thenComparingLong(RailwayRouteOption::tariffCost)).orElseThrow();var bal=pool.stream().min(Comparator.comparingDouble(r->balancedScore(r,minP,maxP,minT,maxT))).orElseThrow();LinkedHashMap<String,RailwayRouteOption> out=new LinkedHashMap<>();add(out,cheap,RailwayRouteStrategy.CHEAPEST);add(out,fast,RailwayRouteStrategy.FASTEST);add(out,bal,RailwayRouteStrategy.BALANCED);return List.copyOf(out.values());}
    private static void add(Map<String,RailwayRouteOption> out,RailwayRouteOption r,RailwayRouteStrategy strategy){out.putIfAbsent(r.pathKey(),new RailwayRouteOption(r.id(),strategy,r.graphId(),r.destinationStationId(),r.backwards(),r.edges(),r.distance(),r.tariffCost(),r.estimatedTicks(),r.contributions(),r.topologyFingerprint()));}
    private static Set<TrackMaterial.TrackType> validTypes(Train train){return CreateNavigationAccess.validTrackTypes(train);}
    private static void enumerate(Train train,GlobalStation destination,RailwayManager manager,RailwayOwnershipTypes.OwnerType payerType,UUID payerId,CityManager cities,boolean backwards,Set<TrackMaterial.TrackType> valid,List<Candidate> out){TravellingPoint p=backwards?train.carriages.getLast().getTrailingPoint():train.carriages.getFirst().getLeadingPoint();TrackNode n1=backwards?p.node2:p.node1,n2=backwards?p.node1:p.node2;TrackEdge initial=train.graph.getConnectionsFrom(n1).get(n2);if(initial==null||blocked(initial,manager,payerType,payerId,cities))return;double initialDistance=backwards?p.position:initial.getLength()-p.position;List<Couple<TrackNode>> nodes=new ArrayList<>();nodes.add(Couple.create(n1,n2));List<RailwayEdgeReference> edges=new ArrayList<>();edges.add(ref(n1,n2));Set<RailwayEdgeReference> used=new HashSet<>(edges);int[] expansions={0};walk(train,destination,manager,payerType,payerId,cities,valid,n1,n2,initial,initialDistance,nodes,edges,used,out,expansions,backwards);}
    private static void walk(Train train,GlobalStation destination,RailwayManager manager,RailwayOwnershipTypes.OwnerType payerType,UUID payerId,CityManager cities,Set<TrackMaterial.TrackType> valid,TrackNode previous,TrackNode current,TrackEdge edge,double distance,List<Couple<TrackNode>> nodes,List<RailwayEdgeReference> edges,Set<RailwayEdgeReference> used,List<Candidate> out,int[] expansions,boolean backwards){if(++expansions[0]>MAX_EXPANSIONS||edges.size()>MAX_EDGES||out.size()>=MAX_CANDIDATES)return;if(same(edge,destination)&&destination.canApproachFrom(current)){double result=distance-(edge.getLength()-destination.getLocationOn(edge));if(result>0)out.add(new Candidate(backwards,List.copyOf(nodes),List.copyOf(edges),result));return;}for(var entry:train.graph.getConnectionsFrom(current).entrySet()){TrackNode next=entry.getKey();TrackEdge nextEdge=entry.getValue();if(next==previous||!edge.canTravelTo(nextEdge)||!valid.contains(nextEdge.getTrackMaterial().trackType)||blocked(nextEdge,manager,payerType,payerId,cities))continue;RailwayEdgeReference reference=ref(current,next);if(!used.add(reference))continue;if(!navigable(nextEdge,next)){used.remove(reference);continue;}nodes.add(Couple.create(current,next));edges.add(reference);walk(train,destination,manager,payerType,payerId,cities,valid,current,next,nextEdge,distance+nextEdge.getLength(),nodes,edges,used,out,expansions,backwards);edges.removeLast();nodes.removeLast();used.remove(reference);}}
    private static boolean navigable(TrackEdge edge,TrackNode via){if(!edge.getEdgeData().hasPoints())return true;for(TrackEdgePoint point:edge.getEdgeData().getPoints())if(!point.canNavigateVia(via))return false;return true;}
    private static boolean same(TrackEdge edge,GlobalStation destination){return destination.edgeLocation.either(location->location.equals(edge.node1.getLocation()))&&destination.edgeLocation.either(location->location.equals(edge.node2.getLocation()));}
    private static boolean blocked(TrackEdge edge,RailwayManager manager,RailwayOwnershipTypes.OwnerType payerType,UUID payerId,CityManager cities){RailwayEdgeReference ref=ref(edge.node1,edge.node2);for(OwnedRailwaySection section:manager.ownedSections())if(section.path().contains(ref)){OwnedRailwayNetwork n=manager.ownedNetwork(section.networkId());if(n!=null&&!allowsNetwork(n,payerType,payerId,cities))return true;}return false;}
    public static boolean allowsNetwork(OwnedRailwayNetwork network,RailwayOwnershipTypes.OwnerType payerType,UUID payerId){return network.access()==RailwayOwnershipTypes.Access.PUBLIC||network.ownerType()==payerType&&network.ownerId().equals(payerId);}
    public static boolean allowsNetwork(OwnedRailwayNetwork network,RailwayOwnershipTypes.OwnerType payerType,UUID payerId,CityManager cities){if(allowsNetwork(network,payerType,payerId))return true;return network.ownerType()==RailwayOwnershipTypes.OwnerType.CITY&&payerType==RailwayOwnershipTypes.OwnerType.CITY&&CityAccessPolicy.canAccess(cities,network.ownerId(),payerId,CityAccessPolicy.Service.RAILWAY_PRIVATE,true);}
    public static boolean selectionCurrent(RailwayRouteOption option,UUID graphId,UUID destinationId,long currentFingerprint){return option.graphId().equals(graphId)&&option.destinationStationId().equals(destinationId)&&option.topologyFingerprint()==currentFingerprint&&currentFingerprint!=Long.MIN_VALUE;}
    private static RailwayEdgeReference ref(TrackNode a,TrackNode b){return new RailwayEdgeReference(RailwayNodeReference.of(a.getLocation()),RailwayNodeReference.of(b.getLocation())).normalized();}
    public static long fingerprint(List<RailwayEdgeReference> edges,com.simibubi.create.content.trains.graph.TrackGraph graph){long hash=1125899906842597L;for(RailwayEdgeReference ref:edges){TrackNode a=graph.locateNode(ref.first().resolve()),b=graph.locateNode(ref.second().resolve());if(a==null||b==null||graph.getConnectionsFrom(a).get(b)==null)return Long.MIN_VALUE;hash=31*hash+ref.hashCode();}return hash;}
}
