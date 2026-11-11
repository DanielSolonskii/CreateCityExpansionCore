package com.example.examplemod.cargo.center;

import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.nbt.*;
import java.util.*;

/** Read-only bounded traversal of Create's real directed edges. Never draws invented rail connections. */
public final class CargoRailConnections {
    public record Station(TrackGraph graph,GlobalStation station) {}
    public record Path(UUID graph,List<TrackNode> nodes) { public Path { nodes=List.copyOf(nodes); } }
    private record Key(UUID from,UUID to) {}
    private record Cache(long tick,Map<Key,Optional<Path>> paths) {}
    private static final Map<MinecraftServer,Cache> CACHES=new WeakHashMap<>();
    private CargoRailConnections() {}
    public static Station station(MinecraftServer s,UUID id){for(var graph:RailwaySavedData.load(s).getTrackNetworks().values()){var station=graph.getPoint(EdgePointType.STATION,id);if(station!=null)return new Station(graph,station);}return null;}
    public static Optional<Path> path(MinecraftServer s,UUID from,UUID to){
        long tick=s.overworld().getGameTime();var cache=CACHES.get(s);if(cache==null||cache.tick()!=tick){cache=new Cache(tick,new HashMap<>());CACHES.put(s,cache);}
        var key=new Key(from,to);var found=cache.paths().get(key);if(found!=null)return found;var result=traverse(s,from,to);if(cache.paths().size()<512)cache.paths().put(key,result);return result;
    }
    private static Optional<Path> traverse(MinecraftServer s,UUID from,UUID to){
        var a=station(s,from);var b=station(s,to);
        if(a==null||b==null||a.graph()!=b.graph()||a.station().assembling||b.station().assembling)return Optional.empty();
        var graph=a.graph();var first=graph.locateNode(a.station().edgeLocation.getFirst());var second=graph.locateNode(a.station().edgeLocation.getSecond());
        var targetA=graph.locateNode(b.station().edgeLocation.getFirst());var targetB=graph.locateNode(b.station().edgeLocation.getSecond());
        if(first==null||second==null||targetA==null||targetB==null)return Optional.empty();
        record Step(TrackNode previous,TrackNode current) {}
        var queue=new ArrayDeque<Step>();var before=new HashMap<Step,Step>();
        for(var step:List.of(new Step(first,second),new Step(second,first))){queue.add(step);before.put(step,null);}
        Step end=null;
        while(!queue.isEmpty()&&before.size()<12000){var step=queue.remove();
            if((step.current()==targetA||step.current()==targetB)&&b.station().canApproachFrom(step.current())){end=step;break;}
            var incoming=graph.getConnectionsFrom(step.previous()).get(step.current());if(incoming==null)continue;
            for(var edge:graph.getConnectionsFrom(step.current()).entrySet()){
                if(edge.getKey()==step.previous()||!incoming.canTravelTo(edge.getValue()))continue;
                var next=new Step(step.current(),edge.getKey());if(before.containsKey(next))continue;before.put(next,step);queue.add(next);
            }
        }
        if(end==null)return Optional.empty();var nodes=new ArrayList<TrackNode>();for(var step=end;step!=null;step=before.get(step))nodes.add(step.current());Collections.reverse(nodes);
        return Optional.of(new Path(graph.id,nodes));
    }
    /** Samples real Bezier edges rather than drawing straight lines between stations. */
    public static ListTag geometry(MinecraftServer s,Path path){var result=new ListTag();var graph=RailwaySavedData.load(s).getTrackNetworks().get(path.graph());if(graph==null)return result;
        for(int i=1;i<path.nodes().size()&&result.size()<512;i++){var a=path.nodes().get(i-1);var b=path.nodes().get(i);var edge=graph.getConnectionsFrom(a).get(b);if(edge==null)continue;
            if(!a.getLocation().dimension.equals(b.getLocation().dimension))continue;
            int samples=edge.isTurn()?Math.min(32,Math.max(2,(int)(edge.getLength()/4))):1;
            for(int j=0;j<samples&&result.size()<512;j++){var v=edge.getPosition(graph,(double)j/samples);var t=new CompoundTag();t.putString("Dimension",a.getLocation().dimension.location().toString());t.putDouble("X",v.x);t.putDouble("Z",v.z);var next=edge.getPosition(graph,(double)(j+1)/samples);t.putDouble("ToX",next.x);t.putDouble("ToZ",next.z);result.add(t);}}
        return result;
    }
}
