package com.example.examplemod.cargo.center;

import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.createmod.catnip.data.Couple;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Uses actual Create TrackGraph, directed weighted search, native switch control and TravellingPoint movement. */
public final class RailNativeCheck {
    private static int checks;
    private static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static TrackNode node(TrackGraph graph,double x,double z){var n=new TrackNode(new TrackNodeLocation(x,0,z).in(Level.OVERWORLD),0,new Vec3(0,1,0));graph.addNode(n);return n;}
    private static TrackEdge connect(TrackGraph graph,TrackNode a,TrackNode b){var edge=new TrackEdge(a,b,null,null);graph.putConnection(a,b,edge);graph.putConnection(b,a,new TrackEdge(b,a,null,null));return edge;}
    private static void binaryCompatibility()throws ReflectiveOperationException{
        var carriage=Carriage.class.getDeclaredMethod("travel",Level.class,TrackGraph.class,double.class,TravellingPoint.class,TravellingPoint.class,int.class);
        require(carriage.getReturnType()==double.class&&java.lang.reflect.Modifier.isPublic(carriage.getModifiers())&&!java.lang.reflect.Modifier.isStatic(carriage.getModifiers()),"pinned Carriage.travel mixin target is a public instance method returning primitive double");
        var edge=TravellingPoint.class.getDeclaredMethod("edgeTraversedFrom",TrackGraph.class,boolean.class,TravellingPoint.IEdgePointListener.class,TravellingPoint.ITurnListener.class,double.class,double.class);
        require(edge.getReturnType()==Double.class&&java.lang.reflect.Modifier.isProtected(edge.getModifiers())&&!java.lang.reflect.Modifier.isStatic(edge.getModifiers()),"pinned edgeTraversedFrom mixin target returns nullable boxed Double, not primitive double");
        var navigation=Navigation.class.getDeclaredMethod("findPathTo",ArrayList.class,double.class);
        require(navigation.getReturnType()==DiscoveredPath.class&&java.lang.reflect.Modifier.isPublic(navigation.getModifiers())&&!java.lang.reflect.Modifier.isStatic(navigation.getModifiers()),"pinned Navigation.findPathTo(ArrayList,double) mixin target returns DiscoveredPath");
        var train=Navigation.class.getDeclaredField("train");
        require(train.getType()==Train.class&&java.lang.reflect.Modifier.isPublic(train.getModifiers())&&!java.lang.reflect.Modifier.isStatic(train.getModifiers()),"pinned Navigation.train shadow has the public instance Train type");
    }
    public static void main(String[] args)throws ReflectiveOperationException{
        binaryCompatibility();
        // This isolated graph fixture has no loaded mod resources. Establish the real loader's empty
        // discovery list before vanilla FeatureFlags asks NeoForge for mod-provided feature files.
        if(net.neoforged.fml.loading.LoadingModList.get()==null)net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var graph=new TrackGraph();var a=node(graph,0,0);var b=node(graph,10,0);var c=node(graph,20,0);var d=node(graph,30,0);var e=node(graph,40,0);var f=node(graph,20,2);var ab=connect(graph,a,b);var paid=connect(graph,b,c);connect(graph,c,d);var de=connect(graph,d,e);var bf=connect(graph,b,f);var fd=connect(graph,f,d);require(ab.canTravelTo(bf)&&bf.canTravelTo(fd)&&fd.canTravelTo(de),"bypass fixture obeys native track tangent constraints at every junction");
        var target=new GlobalStation();target.id=UUID.randomUUID();target.name="Destination";target.setLocation(Couple.create(d.getLocation(),e.getLocation()),5);
        var owner=OwnerRef.player(UUID.randomUUID());var payer=OwnerRef.player(UUID.randomUUID());var book=new RailTollBook(new CargoCommerceBook.Accounts(){public long balance(OwnerRef o){return 0;}public boolean debit(OwnerRef o,long n){return n==0;}public boolean canCredit(OwnerRef o,long n){return true;}public void credit(OwnerRef o,long n){}public long maximum(){return Long.MAX_VALUE;}public void dirty(){}});
        book.register(UUID.randomUUID(),owner,"Paid shortcut",1000,List.of(RailGeometry.span(graph,paid,0,paid.getLength())));
        var cheapest=RailRoutes.planGraph(graph,a,b,0,target,book,payer,"CHEAPEST").orElseThrow();var fastest=RailRoutes.planGraph(graph,a,b,0,target,book,payer,"FASTEST").orElseThrow();require(cheapest.distance()>fastest.distance(),"weighted search prefers physically longer free bypass; cheap="+cheapest.save()+"; fast="+fastest.save());require(book.estimate(cheapest.spans(),payer)==0&&book.estimate(fastest.spans(),payer)>0,"tolls use actual selected path");
        var nativePath=RailRoutes.nativeInstructions(graph,cheapest,target);require(nativePath.path.size()==1&&nativePath.path.getFirst().getFirst()==b&&nativePath.path.getFirst().getSecond()==f,"native switch instructions encode approved bypass, not merely destination");
        var train=new Train(UUID.randomUUID(),payer.id(),graph,new ArrayList<>(),List.of(),false,Component.literal("Fixture"),null,0);train.runtime.paused=true;require(train.navigation.startNavigation(nativePath)>=0&&train.navigation.destination==target,"actual Create navigation consumes selected DiscoveredPath");
        var point=new TravellingPoint(a,b,ab,0,false);var chosen=new ArrayList<TrackNode>();var control=train.navigation.control(point);double moved=point.travel(graph,cheapest.distance(),(g,options)->{var choice=control.apply(g,options);chosen.add(choice.getKey());return choice;});require(Math.abs(moved-cheapest.distance())<.001&&chosen.contains(f)&&!chosen.contains(c),"actual TravellingPoint follows approved fork");
        require(point.node1==d&&point.node2==e&&Math.abs(point.position-5)<.001,"native movement reaches real station offset");
        var split=node(graph,15,0);var left=new TrackEdge(b,split,null,null);var right=new TrackEdge(split,c,null,null);var original=RailGeometry.span(graph,paid,0,paid.getLength());require(Math.abs(original.overlap(RailGeometry.span(graph,left,0,left.getLength()))+original.overlap(RailGeometry.span(graph,right,0,right.getLength()))-paid.getLength())<.0001,"spatial claim survives straight-edge subdivision");
        System.out.println("PASS: "+checks+" actual Create graph/navigation/movement integration checks");
    }
}
