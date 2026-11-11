package com.example.examplemod.cargo.center;

import com.simibubi.create.content.trains.graph.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Spatial identities survive Create graph UUID changes and straight-edge subdivision. */
public final class RailGeometry {
    private RailGeometry() {}
    public record Span(String shape,double from,double to) {
        public Span { if(shape==null||shape.isBlank()||!Double.isFinite(from)||!Double.isFinite(to)||from>to)throw new IllegalArgumentException("rail_geometry_invalid"); }
        public double overlap(Span other){return shape.equals(other.shape)?Math.max(0,Math.min(to,other.to)-Math.max(from,other.from)):0;}
        public CompoundTag save(){var t=new CompoundTag();t.putString("Shape",shape);t.putDouble("From",from);t.putDouble("To",to);return t;}
        public static Span load(CompoundTag t){return new Span(t.getString("Shape"),t.getDouble("From"),t.getDouble("To"));}
    }
    private static String number(double d){return Long.toString(Math.round(d*1_000_000));}
    private static String vector(Vec3 v){return number(v.x)+","+number(v.y)+","+number(v.z);}
    public static Span span(TrackGraph graph,TrackEdge edge,double from,double to){
        double length=edge.getLength();if(length<=0)return new Span("portal",0,0);
        from=Math.clamp(from,0,length);to=Math.clamp(to,0,length);
        var start=edge.getPosition(graph,0);var end=edge.getPosition(graph,1);
        String dimension=edge.node1.getLocation().dimension.location().toString();
        if(!edge.isTurn()){
            var direction=end.subtract(start).normalize();if(direction.x< -1e-9||Math.abs(direction.x)<1e-9&&(direction.y< -1e-9||Math.abs(direction.y)<1e-9&&direction.z<0))direction=direction.scale(-1);
            var moment=start.cross(direction);String key=dimension+"/line/"+vector(direction)+"/"+vector(moment);
            double a=start.lerp(end,from/length).dot(direction),b=start.lerp(end,to/length).dot(direction);
            return new Span(key,Math.min(a,b),Math.max(a,b));
        }
        boolean reverse=vector(start).compareTo(vector(end))>0;var key=new StringBuilder(dimension+"/curve/");
        for(int i=0;i<=8;i++)key.append(vector(edge.getPosition(graph,reverse?1-i/8.0:i/8.0))).append(';');
        double a=arc(graph,edge,from/length),b=arc(graph,edge,to/length);
        if(reverse){a=length-a;b=length-b;}return new Span(key.toString(),Math.min(a,b),Math.max(a,b));
    }
    private static double arc(TrackGraph graph,TrackEdge edge,double t){
        double total=0,partial=0;var previous=edge.getPosition(graph,0);
        for(int i=1;i<=64;i++){double nextT=i/64.0;var next=edge.getPosition(graph,nextT);double distance=previous.distanceTo(next);total+=distance;
            double low=(i-1)/64.0;if(t>=nextT)partial+=distance;else if(t>low)partial+=previous.distanceTo(edge.getPosition(graph,t));previous=next;}
        return total==0?0:partial/total*edge.getLength();
    }
}
