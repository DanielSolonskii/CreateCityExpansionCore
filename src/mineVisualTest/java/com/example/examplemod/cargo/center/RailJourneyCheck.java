package com.example.examplemod.cargo.center;

import com.simibubi.create.content.trains.entity.TravellingPoint;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.createmod.catnip.data.Couple;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Headless calls to native Create movement; deliberately does not claim a NeoForge/mixin runtime launch. */
public final class RailJourneyCheck {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static TrackNode node(TrackGraph graph,double x){var n=new TrackNode(new TrackNodeLocation(x,0,0).in(Level.OVERWORLD),0,new Vec3(0,1,0));graph.addNode(n);return n;}
    private static TrackNode bend(TrackGraph graph,double x,double z){var n=new TrackNode(new TrackNodeLocation(x,0,z).in(Level.OVERWORLD),0,new Vec3(0,1,0));graph.addNode(n);return n;}
    private static TrackEdge edge(TrackGraph graph,TrackNode a,TrackNode b){var e=new TrackEdge(a,b,null,null);graph.putConnection(a,b,e);graph.putConnection(b,a,new TrackEdge(b,a,null,null));return e;}
    private static GlobalStation station(TrackNode a,TrackNode b,double at){var s=new GlobalStation();s.id=UUID.randomUUID();s.name="Station "+s.id;s.setLocation(Couple.create(a.getLocation(),b.getLocation()),at);return s;}
    public static void main(String[] args){
        if(net.neoforged.fml.loading.LoadingModList.get()==null)net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var graph=new TrackGraph();var a=node(graph,0);var b=node(graph,10);var c=node(graph,20);var d=node(graph,30);var e=node(graph,40);var f=node(graph,50);var ab=edge(graph,a,b);var bc=edge(graph,b,c);var cd=edge(graph,c,d);edge(graph,d,e);edge(graph,e,f);
        var front=new TravellingPoint(c,d,cd,5,false);var rear=new TravellingPoint(b,c,bc,5,false);var pose=RailRoutes.pose(List.of(front,rear));var left=station(b,a,5);var right=station(e,f,5);var payer=OwnerRef.player(UUID.randomUUID());var roadOwner=OwnerRef.player(UUID.randomUUID());
        var book=new RailTollBook(new CargoCommerceBook.Accounts(){public long balance(OwnerRef o){return 0;}public boolean debit(OwnerRef o,long n){return n==0;}public boolean canCredit(OwnerRef o,long n){return true;}public void credit(OwnerRef o,long n){}public long maximum(){return Long.MAX_VALUE;}public void dirty(){}});
        book.register(UUID.randomUUID(),roadOwner,"Under fixed front meter",1000,List.of(RailGeometry.span(graph,cd,0,5)));
        require(RailRoutes.journeyGraph(graph,pose,left,null,true,false,book,payer,"FASTEST",0,new int[]{10000}).isEmpty(),"single-ended forward-only train cannot quote a backwards-only station leg");
        var reverse=RailRoutes.journeyGraph(graph,pose,left,null,true,true,book,payer,"FASTEST",0,new int[]{10000}).orElseThrow();
        require(reverse.path().backwards()&&Math.abs(reverse.path().distance()-10)<.001,"double-ended conductor permits actual reverse movement from rear head");
        require(Math.abs(reverse.arrival().front().getPosition(graph).x-15)<.001&&Math.abs(reverse.arrival().rear().getPosition(graph).x-5)<.001,"virtual train body retains its physical front/rear positions after reverse arrival");
        require(book.estimate(reverse.path().spans(),payer)==0&&book.estimate(reverse.meter().spans(),payer)==5,"reverse charge follows constant first-leading meter, not relocated rear navigation head");
        require(RailRoutes.journeyGraph(graph,pose,left,null,true,true,book,payer,"NO_TOLLS",0,new int[]{10000}).isEmpty(),"no-tolls mode also rejects paid reverse body prefix outside the navigation head path");
        require(Math.abs(reverse.meter().distance()-reverse.path().distance())<.001,"one reference point meters reverse distance exactly once");
        var forward=RailRoutes.journeyGraph(graph,reverse.arrival(),right,null,true,true,book,payer,"FASTEST",0,new int[]{10000}).orElseThrow();
        require(!forward.path().backwards()&&Math.abs(forward.path().distance()-30)<.001,"next leg starts from actual prior orientation and full train body offset");
        require(Math.abs(forward.arrival().front().getPosition(graph).x-45)<.001&&Math.abs(forward.arrival().rear().getPosition(graph).x-35)<.001,"chained delivery/return keeps correct train length");
        require(front.position==5&&front.node1==c&&rear.position==5&&rear.node1==b,"quote simulation never moves real source travelling points");
        require(book.debts().isEmpty()&&book.save().getList("Receipts",11).isEmpty(),"native quote scouts create no travel debts or movement receipts");
        boolean bounded=false;try{RailRoutes.journeyGraph(graph,pose,left,null,true,true,book,payer,"FASTEST",0,new int[]{1});}catch(IllegalArgumentException expected){bounded=expected.getMessage().equals("fleet_route_too_complex");}require(bounded,"path/body simulation is bounded before accepting payment");
        var fork=new TrackGraph();var fa=node(fork,0);var fb=node(fork,10);var fc=node(fork,20);var fd=node(fork,30);var fe=node(fork,40);var bypass=bend(fork,20,2);var fab=edge(fork,fa,fb);var tollEdge=edge(fork,fb,fc);edge(fork,fc,fd);edge(fork,fd,fe);edge(fork,fb,bypass);edge(fork,bypass,fd);book.register(UUID.randomUUID(),roadOwner,"Avoided branch",1000,List.of(RailGeometry.span(fork,tollEdge,0,tollEdge.getLength())));var forkPose=RailRoutes.pose(List.of(new TravellingPoint(fa,fb,fab,2,false),new TravellingPoint(fa,fb,fab,0,false)));var bypassTrip=RailRoutes.journeyGraph(fork,forkPose,station(fd,fe,5),null,true,false,book,payer,"CHEAPEST",0,new int[]{10000}).orElseThrow();require(bypassTrip.path().segments().stream().anyMatch(segment->segment.a().equals(RailRoutes.Node.of(bypass))||segment.b().equals(RailRoutes.Node.of(bypass))),"virtual leading point consumes selected native bypass switch");require(book.estimate(bypassTrip.meter().spans(),payer)==0&&Math.abs(bypassTrip.arrival().rear().getPosition(fork).x-33)<.01,"native following point keeps body on same free branch through the fork");
        System.out.println("PASS: "+checks+" native forward/reverse itinerary, fixed meter and quote isolation checks");
    }
}
