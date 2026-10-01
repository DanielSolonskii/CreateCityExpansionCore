package com.example.examplemod.railway;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.track.ITrackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class RailwayTrackPathResolver {
    private RailwayTrackPathResolver() {}
    public record Path(RailwayEndpoint first,RailwayEndpoint second,List<RailwayEdgeReference> edges,double length){}

    @Nullable public static RailwayEndpoint endpoint(ServerLevel level, UUID markerId, ResourceLocation markerDimension,
                                                     BlockPos markerPos, BlockPos trackPos, boolean front) {
        var state=level.getBlockState(trackPos);if(!(state.getBlock() instanceof ITrackBlock track))return null;
        List<Vec3> axes=track.getTrackAxes(level,trackPos,state);if(axes.size()!=1)return null;
        TrackGraphLocation location=TrackGraphHelper.getGraphLocationAt(level,trackPos,
                front?AxisDirection.POSITIVE:AxisDirection.NEGATIVE,axes.getFirst());
        if(location==null||location.graph==null||location.edge==null)return null;
        return new RailwayEndpoint(markerId,markerDimension,markerPos,level.dimension().location(),trackPos,front,
                location.graph.id,new RailwayEdgeReference(RailwayNodeReference.of(location.edge.getFirst()),RailwayNodeReference.of(location.edge.getSecond())),location.position);
    }

    @Nullable public static Path path(RailwayEndpoint a,RailwayEndpoint b) {
        if(!a.graphId().equals(b.graphId()))return null;TrackGraph graph=graph(a.graphId());if(graph==null)return null;
        TrackNode a1=graph.locateNode(a.edge().first().resolve()),a2=graph.locateNode(a.edge().second().resolve());
        TrackNode b1=graph.locateNode(b.edge().first().resolve()),b2=graph.locateNode(b.edge().second().resolve());
        if(a1==null||a2==null||b1==null||b2==null)return null;
        TrackEdge ae=graph.getConnectionsFrom(a1).get(a2),be=graph.getConnectionsFrom(b1).get(b2);if(ae==null||be==null)return null;
        if(sameEdge(a.edge(),b.edge()))return new Path(a,b,List.of(a.edge().normalized()),Math.abs(a.position()-positionOn(a,b,ae.getLength())));
        Candidate best=null;
        TrackNode[] starts={a1,a2},ends={b1,b2};double[] startCost={a.position(),ae.getLength()-a.position()};
        double bp=positionOn(b,b,be.getLength());double[] endCost={bp,be.getLength()-bp};
        for(int i=0;i<2;i++)for(int j=0;j<2;j++){Candidate middle=dijkstra(graph,starts[i],ends[j]);if(middle==null)continue;double total=startCost[i]+middle.length+endCost[j];if(best==null||total<best.length)best=new Candidate(total,middle.edges);}
        if(best==null)return null;List<RailwayEdgeReference> edges=new ArrayList<>();edges.add(a.edge().normalized());for(var e:best.edges)if(!edges.contains(e))edges.add(e);var last=b.edge().normalized();if(!edges.contains(last))edges.add(last);return new Path(a,b,List.copyOf(edges),best.length);
    }
    private static double positionOn(RailwayEndpoint canonical,RailwayEndpoint value,double length){return canonical.edge().first().equals(value.edge().first())?value.position():length-value.position();}
    private static boolean sameEdge(RailwayEdgeReference a,RailwayEdgeReference b){return a.normalized().equals(b.normalized());}
    @Nullable private static Candidate dijkstra(TrackGraph graph,TrackNode start,TrackNode goal){
        record Step(TrackNode node,double distance){}Comparator<Step> by=Comparator.comparingDouble(Step::distance);PriorityQueue<Step> q=new PriorityQueue<>(by);Map<TrackNode,Double>d=new IdentityHashMap<>();Map<TrackNode,TrackNode>prev=new IdentityHashMap<>();d.put(start,0d);q.add(new Step(start,0));
        while(!q.isEmpty()){Step s=q.poll();if(s.distance()!=d.get(s.node()))continue;if(s.node()==goal)break;for(var entry:graph.getConnectionsFrom(s.node()).entrySet()){if(entry.getValue().isInterDimensional())continue;double next=s.distance()+entry.getValue().getLength();if(next>=d.getOrDefault(entry.getKey(),Double.POSITIVE_INFINITY))continue;d.put(entry.getKey(),next);prev.put(entry.getKey(),s.node());q.add(new Step(entry.getKey(),next));}}
        if(!d.containsKey(goal))return null;List<RailwayEdgeReference> edges=new ArrayList<>();for(TrackNode n=goal;n!=start;){TrackNode p=prev.get(n);if(p==null)return null;edges.add(new RailwayEdgeReference(RailwayNodeReference.of(p.getLocation()),RailwayNodeReference.of(n.getLocation())).normalized());n=p;}Collections.reverse(edges);return new Candidate(d.get(goal),List.copyOf(edges));
    }
    private record Candidate(double length,List<RailwayEdgeReference> edges){}
    @Nullable public static TrackGraph graph(UUID id){return Create.RAILWAYS.trackNetworks.values().stream().filter(g->g.id.equals(id)).findFirst().orElse(null);}
}
