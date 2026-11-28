package com.example.examplemod.navigation.walk;

import com.example.examplemod.citymap.CityMapFeature;
import net.minecraft.core.BlockPos;

import java.util.*;

/** A proposal from the city's drawing. WalkingRoutes checks every step in the live level before use. */
final class MappedWalks {
    private static final int APPROACH=4, MAX_LENGTH=WalkingRoutes.MAX_HORIZONTAL+127;
    private record Vertex(UUID feature,BlockPos point) {}
    private record Step(Vertex vertex,int cost) {}
    private final Map<Vertex,List<Vertex>> edges=new LinkedHashMap<>();

    MappedWalks(List<CityMapFeature> features) {
        for (var f:features) {
            if (f.shape()!=CityMapFeature.Shape.LINE ||
                    f.kind()!=CityMapFeature.Kind.PATH && f.kind()!=CityMapFeature.Kind.STREET) continue;
            var points=f.vertices();
            for(int i=1;i<points.size();i++) {
                if (distance(points.get(i-1),points.get(i))<=MAX_LENGTH)
                    connect(new Vertex(f.id(),points.get(i-1)),new Vertex(f.id(),points.get(i)));
            }
            attach(f,f.links().start(),points.getFirst());
            attach(f,f.links().end(),points.getLast());
        }
    }
    private void attach(CityMapFeature source, CityMapFeature.Endpoint target,BlockPos end) {
        if (target!=null && end.equals(target.point()))
            connect(new Vertex(source.id(),end),new Vertex(target.feature(),end));
    }
    private void connect(Vertex a,Vertex b) {
        edges.computeIfAbsent(a,v->new ArrayList<>()).add(b);
        edges.computeIfAbsent(b,v->new ArrayList<>()).add(a);
    }
    private static int distance(BlockPos a,BlockPos b) {
        return Math.abs(a.getX()-b.getX())+Math.abs(a.getZ()-b.getZ());
    }
    private static int approach(BlockPos a,BlockPos b) {
        return Math.abs(a.getY()-b.getY())<=1 && Math.abs(a.getY()-b.getY())<=distance(a,b)?distance(a,b):Integer.MAX_VALUE;
    }
    WalkingRoutes.Result propose(WalkingRoutes.Leg leg) {
        if (edges.isEmpty() || !leg.withinSearchRange() || distance(leg.from(),leg.to())<2) return null;
        var start=new ArrayList<Vertex>();
        var end=new HashSet<Vertex>();
        int nearestStart=APPROACH, nearestEnd=APPROACH;
        for (var v:edges.keySet()) {
            int a=approach(leg.from(),v.point()), b=approach(leg.to(),v.point());
            if (a<=nearestStart) {
                if (a<nearestStart) start.clear();
                nearestStart=a; start.add(v);
            }
            if (b<=nearestEnd) {
                if (b<nearestEnd) end.clear();
                nearestEnd=b; end.add(v);
            }
        }
        if (start.isEmpty() || end.isEmpty()) return null;
        var queue=new PriorityQueue<Step>(Comparator.comparingInt(Step::cost));
        var costs=new HashMap<Vertex,Integer>();
        var parent=new HashMap<Vertex,Vertex>();
        for(var v:start) { queue.add(new Step(v,nearestStart)); costs.put(v,nearestStart); }
        int limit=Math.min(MAX_LENGTH,Math.max(32,distance(leg.from(),leg.to())*2+16));
        while(!queue.isEmpty() && costs.size()<=2048) {
            var step=queue.remove(); var at=step.vertex();
            if (costs.get(at)!=step.cost()) continue;
            if (end.contains(at) && step.cost()>nearestStart) {
                var bends=new ArrayList<BlockPos>();
                for (var v=at;v!=null;v=parent.get(v)) bends.add(v.point());
                Collections.reverse(bends);
                bends.add(leg.to());
                var blocks=new ArrayList<BlockPos>();
                blocks.add(leg.from());
                for (var target:bends) if (!append(blocks,target)) return null;
                int ticks=0;
                for(int i=1;i<blocks.size();i++)
                    ticks+=5+WalkingSearch.heightCost(blocks.get(i).getY()-blocks.get(i-1).getY());
                return new WalkingRoutes.Result(WalkingRoutes.Status.FOUND,blocks,ticks/20d);
            }
            for(var neighbor:edges.get(at)) {
                int next=step.cost()+distance(at.point(),neighbor.point());
                if (next+nearestEnd>limit || next>=costs.getOrDefault(neighbor,Integer.MAX_VALUE)) continue;
                costs.put(neighbor,next); parent.put(neighbor,at); queue.add(new Step(neighbor,next));
            }
        }
        return null;
    }
    /** Cardinal rasterisation keeps the drawn bends, block coordinates and elevation. */
    private static boolean append(List<BlockPos> blocks,BlockPos target) {
        var from=blocks.getLast();
        int dx=target.getX()-from.getX(), dy=target.getY()-from.getY(), dz=target.getZ()-from.getZ();
        int length=Math.abs(dx)+Math.abs(dz);
        if (Math.abs(dy)>length || blocks.size()+length>MAX_LENGTH+1) return false;
        int x=0,z=0;
        for(int i=1;i<=length;i++) {
            if (Math.round((float)i*Math.abs(dx)/length)>Math.abs(x)) x+=Integer.signum(dx);
            else z+=Integer.signum(dz);
            blocks.add(from.offset(x,Math.round((float)i*dy/length),z));
        }
        return true;
    }
}
