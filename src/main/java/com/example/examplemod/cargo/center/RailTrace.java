package com.example.examplemod.cargo.center;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.TrackEdge;
import com.simibubi.create.content.trains.graph.TrackGraph;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.track.TrackMaterial.TrackType;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/** Resumable directed shortest path on native Create edges, including station offsets. */
public final class RailTrace {
    private RailTrace() {}
    public record Stroke(double x, double z, double toX, double toZ) {}
    private record Step(TrackEdge edge, double start, double end, double distance, Step parent, boolean terminal) {}

    public static final class Search {
        private final TrackGraph graph;
        private final GlobalStation source, target;
        private final ResourceLocation dimension;
        private final Set<TrackType> types;
        private final PriorityQueue<Step> queue = new PriorityQueue<>(Comparator.comparingDouble(Step::distance));
        private final Map<TrackEdge,Double> visited = new IdentityHashMap<>();
        private boolean done, found;
        private List<Stroke> strokes = List.of();

        public Search(TrackGraph graph, GlobalStation source, GlobalStation target, Train train, ResourceLocation dimension) {
            this.graph = graph;
            this.source = source;
            this.target = target;
            this.dimension = dimension;
            this.types = RailRoutes.types(train);
            if (source == null || target == null || source.assembling || target.assembling) { done=true; return; }
            var a = graph.locateNode(source.edgeLocation.getFirst());
            var b = graph.locateNode(source.edgeLocation.getSecond());
            if (a == null || b == null) { done=true; return; }
            // A single-ended train has to continue in the direction in which it arrived.
            var forward = graph.getConnectionsFrom(a).get(b);
            if (forward != null) offer(forward, source.getLocationOn(forward), null);
            if (train == null || train.doubleEnded && train.hasForwardConductor() && train.hasBackwardConductor()) {
                var reverse = graph.getConnectionsFrom(b).get(a);
                if (reverse != null) offer(reverse, source.getLocationOn(reverse), null);
            }
        }

        private void offer(TrackEdge edge, double start, Step parent) {
            if (types != null && !types.contains(edge.getTrackMaterial().trackType)) return;
            double distance = parent == null ? 0 : parent.distance();
            // A partial first edge may later be re-entered from its beginning on a loop.
            if (parent != null) {
                if (distance >= visited.getOrDefault(edge, Double.POSITIVE_INFINITY)) return;
                visited.put(edge, distance);
            }
            boolean terminal = target.edgeLocation.either(edge.node1.getLocation()::equals)
                    && target.edgeLocation.either(edge.node2.getLocation()::equals) && target.canApproachFrom(edge.node2)
                    && target.getLocationOn(edge) >= start - 1e-7;
            double end = terminal ? Math.max(start,target.getLocationOn(edge)) : edge.getLength();
            for (var point : edge.getEdgeData().getPoints()) {
                double position = point.getLocationOn(edge);
                if (position < start-1e-7 || position > end+1e-7 ||
                        parent == null && point == source || point == target && terminal) continue;
                if (!point.canNavigateVia(edge.node2)) return;
            }
            queue.add(new Step(edge,start,end,distance+end-start,parent,terminal));
        }

        public boolean advance(int budget) {
            while (!done && budget-- > 0) {
                var step = queue.poll();
                if (step == null) { done=true; break; }
                if (step.parent()!=null && step.parent().distance()>visited.getOrDefault(step.edge(),Double.POSITIVE_INFINITY)) continue;
                if (step.terminal()) {
                    var path = new ArrayDeque<Step>();
                    for (var current=step; current!=null; current=current.parent()) path.addFirst(current);
                    var result=new ArrayList<Stroke>();
                    for (var part:path) append(result,part);
                    strokes=List.copyOf(result);
                    done=found=true;
                    break;
                }
                for (var next : graph.getConnectionsFrom(step.edge().node2).values()) {
                    if (next.node2!=step.edge().node1 && step.edge().canTravelTo(next)) offer(next,0,step);
                }
            }
            return done;
        }

        private void append(ArrayList<Stroke> result,Step part) {
            var edge=part.edge();
            if (edge.isInterDimensional() || !edge.node1.getLocation().dimension.location().equals(dimension)
                    || edge.getLength()<=0 || part.end()-part.start()<1e-7) return;
            int samples=edge.isTurn()?Math.max(1,(int)Math.ceil((part.end()-part.start())/3)):1;
            var start=edge.getPosition(graph,part.start()/edge.getLength());
            for (int i=1;i<=samples;i++) {
                var end=edge.getPosition(graph,(part.start()+(part.end()-part.start())*i/samples)/edge.getLength());
                if (start.x!=end.x || start.z!=end.z) result.add(new Stroke(start.x,start.z,end.x,end.z));
                start=end;
            }
        }
        public boolean found() { return found; }
        public List<Stroke> strokes() { return strokes; }
    }
}
