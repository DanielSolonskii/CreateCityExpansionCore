package com.example.examplemod.passenger;

import com.example.examplemod.cargo.center.RailTrace;
import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.graph.TrackGraph;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.*;

@EventBusSubscriber(modid="createcityexpansioncore")
public final class PassengerRailMap {
    private PassengerRailMap() {}
    private static final int LEGACY_LIMIT=4096;
    public record Segment(UUID line, int color, double x, double z, double toX, double toZ) {}
    public record StopInfo(UUID id,String name,BlockPos entrance) {}
    public record TrainLocation(double x,double y,double z) {}
    public record LineInfo(UUID id,String name,String description,int color,List<StopInfo> stops,UUID trainId,
                           String trainName,TrainLocation train,String status,int unavailableLegs) {
        public LineInfo { stops=List.copyOf(stops); }
    }
    public record Snapshot(List<Segment> segments,boolean pending,List<LineInfo> lines,long revision) {
        public Snapshot(List<Segment> segments,boolean pending) { this(segments,pending,List.of(),0); }
        public Snapshot { segments=List.copyOf(segments); lines=List.copyOf(lines); }
    }
    public static final Snapshot EMPTY = new Snapshot(List.of(),false);
    private record Pair(UUID train, UUID from, UUID to, ResourceLocation dimension) {}
    private static final class Leg {
        final TrackGraph graph;
        final int checksum;
        RailTrace.Search search;
        List<RailTrace.Stroke> strokes=List.of();
        boolean found, resolved;
        int refreshed, lastSeen;
        Leg(TrackGraph graph) { this.graph=graph; checksum=graph.getChecksum(); }
    }
    private record Part(UUID line,int color,List<RailTrace.Stroke> strokes) {}
    private record Drawing(List<Part> parts,List<Segment> segments,long revision) {}
    private static final class Cache {
        final Map<Pair,Leg> paths=new HashMap<>();
        final ArrayDeque<Leg> waiting=new ArrayDeque<>();
        final Map<ResourceLocation,Drawing> drawings=new HashMap<>();
        int lastWork=Integer.MIN_VALUE;
        long sequence;
    }
    private static final Map<MinecraftServer,Cache> caches=new WeakHashMap<>();

    public static Snapshot forAtlas(ServerPlayer player, ResourceLocation dimension) {
        var lines=PassengerService.publicLines(player,dimension);
        var server=player.serverLevel().getServer();
        var data=PassengerData.get(server);
        var cache=caches.computeIfAbsent(server,s -> new Cache());
        var parts=new ArrayList<Part>();
        var infos=new ArrayList<LineInfo>();
        boolean pending=false;
        int now=server.getTickCount();
        for (var line:lines) {
            var train=RailwaySavedData.load(server).getTrains().get(line.train());
            var stops=new ArrayList<StopInfo>();
            var saved=data.lines.get(line.id());
            int missing=0;
            for (int i=0;i<line.stops().size();i++) {
                var id=line.stops().get(i);
                var station=train.graph.getPoint(EdgePointType.STATION,id);
                stops.add(new StopInfo(id,station.name,data.platforms.get(id).entrance()));
                var pair=new Pair(train.id,id,line.stops().get((i+1)%line.stops().size()),dimension);
                var leg=cache.paths.get(pair);
                if (leg==null || leg.graph!=train.graph || leg.checksum!=train.graph.getChecksum()) {
                    if (leg!=null) cache.waiting.remove(leg);
                    leg=new Leg(train.graph);
                    cache.paths.put(pair,leg);
                }
                leg.lastSeen=now;
                if (leg.search==null && (!leg.resolved || now-leg.refreshed>(leg.found?1200:100))) {
                    leg.search=new RailTrace.Search(train.graph,station,train.graph.getPoint(EdgePointType.STATION,pair.to()),train,dimension);
                    cache.waiting.add(leg);
                }
                work(server,cache);
                pending|=!leg.resolved;
                if (leg.resolved && !leg.found) missing++;
                if (!leg.strokes.isEmpty()) parts.add(new Part(line.id(),line.color(),leg.strokes));
            }
            String status=saved.clock!=null && PassengerService.running(server,line.id(),train.id)?"active":saved.status;
            infos.add(new LineInfo(line.id(),line.name(),line.description(),line.color(),stops,train.id,
                    train.name.getString(96),position(train,dimension),status,missing));
        }
        var drawing=cache.drawings.get(dimension);
        if (drawing==null || !drawing.parts().equals(parts)) {
            var segments=new ArrayList<Segment>();
            for (var part:parts) for (var s:part.strokes())
                segments.add(new Segment(part.line(),part.color(),s.x(),s.z(),s.toX(),s.toZ()));
            drawing=new Drawing(List.copyOf(parts),List.copyOf(segments),++cache.sequence);
            cache.drawings.put(dimension,drawing);
        }
        return new Snapshot(drawing.segments(),pending,infos,drawing.revision());
    }

    /** Observe real bogeys in the requested dimension, including carriages outside loaded chunks. */
    static TrainLocation position(Train train,ResourceLocation dimension) {
        for (int i=0;i<train.carriages.size();i++) {
            var carriage=train.carriages.get(train.currentlyBackwards?train.carriages.size()-1-i:i);
            var points=train.currentlyBackwards?List.of(carriage.getTrailingPoint(),carriage.getLeadingPoint()):
                    List.of(carriage.getLeadingPoint(),carriage.getTrailingPoint());
            for (var point:points) {
                var location=position(train.graph,point,dimension);
                if (location!=null) return location;
            }
        }
        return null;
    }
    static TrainLocation position(TrackGraph graph,TravellingPoint point,ResourceLocation dimension) {
        if (point.edge==null || point.node1==null || point.node2==null || point.edge.isInterDimensional()
                || !point.node1.getLocation().dimension.location().equals(dimension)
                || graph.getConnectionsFrom(point.node1).get(point.node2)!=point.edge) return null;
        var p=point.getPosition(graph);
        return Double.isFinite(p.x) && Double.isFinite(p.y) && Double.isFinite(p.z)?new TrainLocation(p.x,p.y,p.z):null;
    }
    private static void work(MinecraftServer server,Cache cache) {
        int now=server.getTickCount();
        if (cache.waiting.isEmpty() || cache.lastWork==now) return;
        cache.lastWork=now;
        var leg=cache.waiting.removeFirst();
        if (leg.search.advance(512)) {
            var strokes=leg.search.strokes();
            if (!strokes.equals(leg.strokes)) leg.strokes=strokes;
            leg.found=leg.search.found();
            leg.resolved=true;
            leg.refreshed=now;
            leg.search=null;
        } else cache.waiting.addLast(leg);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) {
        var server=e.getServer();
        var cache=caches.get(server);
        if (cache==null) return;
        work(server,cache);
        if (server.getTickCount()%1200==0) cache.paths.values().removeIf(leg -> {
            if (server.getTickCount()-leg.lastSeen<2400) return false;
            cache.waiting.remove(leg);
            return true;
        });
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e) { caches.remove(e.getServer()); }

    /** The original v2 city reply keeps its exact byte layout and bounds. */
    public static void write(RegistryFriendlyByteBuf buf,Snapshot map) {
        buf.writeBoolean(map.pending());
        var byLine=new LinkedHashMap<UUID,List<Segment>>();
        for (var s:map.segments().subList(0,Math.min(LEGACY_LIMIT,map.segments().size())))
            byLine.computeIfAbsent(s.line(),id->new ArrayList<>()).add(s);
        buf.writeVarInt(byLine.size());
        for (var entry:byLine.entrySet()) {
            buf.writeUUID(entry.getKey());
            buf.writeInt(entry.getValue().getFirst().color());
            buf.writeVarInt(entry.getValue().size());
            for (var s:entry.getValue()) {
                buf.writeDouble(s.x()); buf.writeDouble(s.z()); buf.writeDouble(s.toX()); buf.writeDouble(s.toZ());
            }
        }
    }
    public static Snapshot read(RegistryFriendlyByteBuf buf) {
        boolean pending=buf.readBoolean();
        int count=buf.readVarInt();
        if (count<0 || count>PassengerData.MAX_LINES) throw new IllegalArgumentException("limit");
        var segments=new ArrayList<Segment>();
        for (int i=0;i<count;i++) {
            UUID id=buf.readUUID(); int color=buf.readInt() & 0xffffff, size=buf.readVarInt();
            if (size<0 || size>LEGACY_LIMIT-segments.size()) throw new IllegalArgumentException("limit");
            for (int j=0;j<size;j++) segments.add(new Segment(id,color,buf.readDouble(),buf.readDouble(),buf.readDouble(),buf.readDouble()));
        }
        return new Snapshot(segments,pending);
    }
}
