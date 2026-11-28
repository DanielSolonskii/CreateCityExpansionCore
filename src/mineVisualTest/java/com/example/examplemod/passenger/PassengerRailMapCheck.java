package com.example.examplemod.passenger;

import com.example.examplemod.cargo.center.RailTrace;
import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.station.GlobalStation;
import io.netty.buffer.Unpooled;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public final class PassengerRailMapCheck {
    private static void check(boolean value,String reason) { if (!value) throw new AssertionError(reason); }
    private static GlobalStation station(TrackNode a,TrackNode b,double offset) {
        var station=new GlobalStation();
        station.id=UUID.randomUUID();
        station.setLocation(Couple.create(a.getLocation(),b.getLocation()),offset);
        return station;
    }

    public static void run() {
        var dim=Level.OVERWORLD.location();
        var graph=new TrackGraph();
        var nodes=new ArrayList<TrackNode>();
        for (int i=0;i<4300;i++) {
            var location=new TrackNodeLocation(i,0,0).in(Level.OVERWORLD);
            if (i==120) location.yOffsetPixels=2;
            var node=new TrackNode(location,i,new Vec3(0,1,0));
            graph.addNode(node);
            if (i>0) {
                var previous=nodes.getLast();
                graph.putConnection(previous,node,new TrackEdge(previous,node,null,null));
                graph.putConnection(node,previous,new TrackEdge(node,previous,null,null));
            }
            nodes.add(node);
        }
        var offsetNode=nodes.get(120).getLocation();
        check(graph.locateNode(new TrackNodeLocation(offsetNode.getLocation()).in(Level.OVERWORLD))==null,"offset fixture reproduces missing legacy node");
        var wheel=new TravellingPoint(nodes.get(20),nodes.get(21),graph.getConnectionsFrom(nodes.get(20)).get(nodes.get(21)),.125,false);
        var first=PassengerRailMap.position(graph,wheel,dim);
        wheel.position=.875;
        var second=PassengerRailMap.position(graph,wheel,dim);
        check(first!=null && second!=null && first.x()==20.125 && second.x()==20.875,"map train position follows the native wheel at sub-block precision");
        check(PassengerRailMap.position(graph,wheel,Level.NETHER.location())==null,"train in another dimension has no map position");
        check(PassengerRailMap.position(graph,new TravellingPoint(),dim)==null,"disconnected wheel has no map position");
        var a=station(nodes.get(1),nodes.get(0),.25);
        var b=station(nodes.get(4298),nodes.get(4299),.5);
        var line=UUID.randomUUID();
        var segments=new ArrayList<PassengerRailMap.Segment>();
        for (var pair:List.of(List.of(a,b),List.of(b,a))) {
            var search=new RailTrace.Search(graph,pair.getFirst(),pair.getLast(),null,dim);
            check(!search.advance(32),"search defers a long leg");
            int batches=1;
            while (!search.advance(32)) if (++batches>1000) throw new AssertionError("search never finished");
            check(search.found() && search.strokes().size()>4096,"both directed legs survive legacy geometry limit and vertical node offset");
            for (var s:search.strokes()) segments.add(new PassengerRailMap.Segment(line,0x0a864c,s.x(),s.z(),s.toX(),s.toZ()));
        }
        check(segments.getFirst().x()==.75 && segments.getLast().toX()==.75,"scheduled return reaches physical station offset");
        var noPath=new RailTrace.Search(graph,a,station(nodes.get(4299),nodes.get(4298),.5),null,dim);
        while (!noPath.advance(256)) {}
        check(!noPath.found(),"no straight line to a platform facing the wrong way");

        var stopList=List.of(new PassengerRailMap.StopInfo(a.id,"А",BlockPos.ZERO),new PassengerRailMap.StopInfo(b.id,"Б",new BlockPos(4298,0,0)));
        var info=new PassengerRailMap.LineInfo(line,"Route","Description",0x0a864c,stopList,UUID.randomUUID(),"Train",
                new PassengerRailMap.TrainLocation(.75,2.3,.125),"checking",0);
        var snapshot=new PassengerRailMap.Snapshot(segments,false,List.of(info),7);
        var received=new ArrayList<PassengerRailMap.Segment>();
        long rev=0; int offset=0;
        for (;;) {
            var ask=new PassengerRailNetwork.Request(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),dim,rev,offset);
            var page=PassengerRailNetwork.page(ask,snapshot);
            var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
            PassengerRailNetwork.Reply.CODEC.encode(buf,page);
            var reply=PassengerRailNetwork.Reply.CODEC.decode(buf);
            check(buf.readableBytes()==0 && reply.equals(page) && reply.lines().equals(List.of(info)),"v3 page binary round trip");
            buf.release();
            check(reply.offset()==received.size(),"contiguous rail page");
            received.addAll(reply.segments()); rev=reply.revision(); offset=received.size();
            if (reply.last()) break;
        }
        check(received.equals(segments),"all segments including return delivered through continuation");
        var unchanged=new PassengerRailNetwork.Request(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),dim,rev,-1);
        check(PassengerRailNetwork.page(unchanged,snapshot).segments().isEmpty(),"unchanged geometry needs only a live summary");
        check(PassengerRailNetwork.page(unchanged,new PassengerRailMap.Snapshot(segments,false,List.of(info),8)).offset()==0,
                "geometry revision change restarts from first page");
        var legacy=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
        PassengerRailMap.write(legacy,snapshot);
        check(PassengerRailMap.read(legacy).segments().equals(segments.subList(0,4096)) && legacy.readableBytes()==0,"old v2 bounds and layout");
        legacy.release();
        System.out.println("PASS: long passenger line, native offset nodes, two directed legs, unreachable platform, "+segments.size()+" paged segments, old v2 wire");
    }
}
