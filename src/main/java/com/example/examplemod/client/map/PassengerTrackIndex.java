package com.example.examplemod.client.map;

import com.example.examplemod.passenger.PassengerRailMap.Segment;
import java.util.*;
import java.util.function.Consumer;

/** Consecutive samples usually belong to the same section of track. Cull whole groups before projecting. */
final class PassengerTrackIndex {
    private record Bounds(int from,int to,double minX,double minZ,double maxX,double maxZ) {}
    private final List<Segment> segments;
    private final List<Bounds> groups=new ArrayList<>();

    PassengerTrackIndex(List<Segment> segments) {
        this.segments=segments;
        for(int from=0;from<segments.size();from+=32) {
            int to=Math.min(segments.size(),from+32);
            double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=Double.NEGATIVE_INFINITY,maxZ=maxX;
            for(int i=from;i<to;i++) {
                var s=segments.get(i);
                minX=Math.min(minX,Math.min(s.x(),s.toX())); maxX=Math.max(maxX,Math.max(s.x(),s.toX()));
                minZ=Math.min(minZ,Math.min(s.z(),s.toZ())); maxZ=Math.max(maxZ,Math.max(s.z(),s.toZ()));
            }
            groups.add(new Bounds(from,to,minX,minZ,maxX,maxZ));
        }
    }
    void visit(double left,double top,double right,double bottom,Consumer<Segment> accept) {
        for(var group:groups) {
            if(group.maxX<left || group.minX>right || group.maxZ<top || group.minZ>bottom)continue;
            for(int i=group.from;i<group.to;i++) {
                var s=segments.get(i);
                if(Math.max(s.x(),s.toX())>=left && Math.min(s.x(),s.toX())<=right
                        && Math.max(s.z(),s.toZ())>=top && Math.min(s.z(),s.toZ())<=bottom) accept.accept(s);
            }
        }
    }
    /** At coincident tracks a second click cycles the lines whose strokes are within one pixel of the nearest. */
    UUID pick(double x,double z,double unitsPerPixel,UUID previous) {
        double radius=4*unitsPerPixel;
        var distances=new HashMap<UUID,Double>();
        visit(x-radius,z-radius,x+radius,z+radius,s->{
            double dx=s.toX()-s.x(),dz=s.toZ()-s.z(),length=dx*dx+dz*dz;
            double t=length==0?0:Math.clamp(((x-s.x())*dx+(z-s.z())*dz)/length,0,1);
            double distance=Math.hypot(x-s.x()-t*dx,z-s.z()-t*dz);
            if(distance<=radius) distances.merge(s.line(),distance,Math::min);
        });
        if(distances.isEmpty())return null;
        var ordered=new ArrayList<>(distances.keySet());
        ordered.sort(Comparator.<UUID>comparingDouble(distances::get).thenComparing(Comparator.naturalOrder()));
        double cutoff=distances.get(ordered.getFirst())+unitsPerPixel;
        ordered.removeIf(line->distances.get(line)>cutoff);
        int index=previous==null?-1:ordered.indexOf(previous);
        return ordered.get((index+1)%ordered.size());
    }
}
