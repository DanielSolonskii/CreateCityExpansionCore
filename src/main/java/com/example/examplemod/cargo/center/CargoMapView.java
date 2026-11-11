package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.*;
import com.simibubi.create.content.trains.RailwaySavedData;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import java.util.*;

/** Logistics overlay data for the existing atlas. Coordinates always come from server registrations/Create. */
public final class CargoMapView {
    /** Shared track edges are sent once even when several cargo lines use them. Direction is canonical. */
    private record RailKey(String dimension,double x1,double z1,double x2,double z2) {}
    private CargoMapView() {}
    public static CompoundTag snapshot(ServerPlayer actor,CompoundTag request,ListTag pointChoices){
        var s=actor.getServer();var requester=OwnerRef.player(actor.getUUID());var result=new CompoundTag();var markers=new ListTag();var rails=new ListTag();var seen=new HashSet<RailKey>();var roadMarkers=new HashSet<UUID>();
        var visible=new HashSet<UUID>();for(Tag raw:pointChoices)visible.add(UUID.fromString(((CompoundTag)raw).getString("Value")));
        String dimension=request.getString("Dimension");boolean railsOnly=request.getBoolean("RailsOnly");
        if(!railsOnly){
            for(UUID id:visible){var p=CargoPointRegistry.find(s,id).orElse(null);if(p==null)continue;p.routers().stream().findFirst().ifPresent(ref->marker(markers,p.id(),"point",p.name(),CargoOwners.point(s,p).name(s),CargoInfrastructure.router(s,ref)==null?"UNLOADED":"CONNECTED",new CargoInfrastructure.Location(ref.dimension(),ref.pos())));}
            for(var w:CargoWarehouseRegistry.get(s).all())if(CargoWarehouseRegistry.permits(actor,w))marker(markers,w.id(),"warehouse",w.name(),CargoWarehouseRegistry.access(s,w).owner().name(s),CargoWarehouseRegistry.ticker(s,w)==null?"UNLOADED":"CONNECTED",w.ticker());
            for(var ref:CargoLineRegistry.get(s).stations().entrySet())if(visible.contains(ref.getValue())){var nativeStation=CargoRailConnections.station(s,ref.getKey());if(nativeStation!=null)marker(markers,ref.getKey(),"station",nativeStation.station().name,"","CONNECTED",new CargoInfrastructure.Location(nativeStation.station().getBlockEntityDimension().location(),nativeStation.station().getBlockEntityPos()));}
            for(var registration:CargoTrainRegistry.get(s).registrations())if(CargoOwners.train(s,requester,registration)){var train=RailwaySavedData.load(s).getTrains().get(registration.trainId());if(train==null)continue;for(var trainDimension:train.getPresentDimensions()){var pos=train.getPositionInDimension(trainDimension);if(pos.isPresent()){marker(markers,train.id,"train",registration.displayName(),CargoOwners.get(s).owner("train/"+train.id,registration.ownerId()).name(s),train.carriageWaitingForChunks>0?"UNLOADED":"AVAILABLE",new CargoInfrastructure.Location(trainDimension.location(),pos.get()));break;}}}
            for(var shipment:CargoShipmentRegistry.get(s).all())if(shipment.owner().operates(s,actor.getUUID())||shipment.creator().equals(actor.getUUID()))for(var parcel:shipment.parcelPositions()){
                CargoInfrastructure.Location location=null;String status="LAST_OBSERVED";
                if(parcel.hasUUID("Train")){var train=RailwaySavedData.load(s).getTrains().get(parcel.getUUID("Train"));if(train!=null)for(var parcelDimension:train.getPresentDimensions()){var pos=train.getPositionInDimension(parcelDimension);if(pos.isPresent()){location=new CargoInfrastructure.Location(parcelDimension.location(),pos.get());status="IN_TRANSIT";break;}}}
                if(location==null&&parcel.contains("Location"))location=CargoInfrastructure.Location.load(parcel.getCompound("Location"));
                if(location!=null&&markers.size()<256){marker(markers,parcel.getUUID("Id"),"shipment",shipment.type().getHoverName().getString(),shipment.owner().name(s),status,location);markers.getCompound(markers.size()-1).putUUID("OpenId",shipment.id());}
            }
        }
        UUID focus=request.hasUUID("Focus")?request.getUUID("Focus"):null;
        if(request.contains("MapRoute")){var route=request.getCompound("MapRoute");UUID from=CenterOperations.id(route,"SourcePoint"),to=CenterOperations.id(route,"DestinationPoint");if(visible.contains(from)&&visible.contains(to)){var found=CargoRoutePlanner.plan(s,requester,from,to,CargoRoutePlanner.Preference.valueOf(route.getString("Preference")),route.contains("Service")?route.getString("Service"):"ANY");if(found.isPresent()){result.putLong("Price",found.get().price());if(found.get().trip().getBoolean("Fleet")){for(var raw:PublicFleetRegistry.map(s,found.get().trip()).getList("Rails",10))if(rails.size()<2048){var sample=((CompoundTag)raw).copy();if(dimension.isEmpty()||sample.getString("Dimension").equals(dimension))rails.add(sample);}}for(var leg:found.get().legs())appendLeg(s,rails,seen,dimension,leg.line(),leg.from(),leg.next(),true);routeStops(s,result,found.get().legs());result.putBoolean("RouteFound",true);}else result.putBoolean("Blocked",true);}}
        else if(focus!=null){var line=CargoLineRegistry.get(s).find(focus).orElse(null);if(line!=null&&CargoOwners.permits(s,requester,CargoLineRegistry.access(s,line)))for(int i=1;i<line.stops().size();i++)appendLeg(s,rails,seen,dimension,line.id(),line.stops().get(i-1).point(),line.stops().get(i).point(),true);
            var shipment=CargoShipmentRegistry.get(s).find(focus).orElse(null);if(shipment!=null&&(shipment.owner().operates(s,actor.getUUID())||shipment.creator().equals(actor.getUUID()))){if(shipment.trip().getBoolean("Fleet")){for(var raw:PublicFleetRegistry.map(s,displayTrip(s,shipment)).getList("Rails",10))if(rails.size()<2048){var sample=((CompoundTag)raw).copy();if(dimension.isEmpty()||sample.getString("Dimension").equals(dimension))rails.add(sample);}}for(var leg:shipment.route())appendLeg(s,rails,seen,dimension,leg.line(),leg.from(),leg.next(),true);routeStops(s,result,shipment.route());var summary=new CompoundTag();summary.putString("Name",shipment.type().getHoverName().getString());summary.putString("Status",shipment.stage().name());summary.putInt("Quantity",shipment.quantity());summary.putInt("Packaged",shipment.packaged());summary.putInt("Delivered",shipment.delivered());var book=com.example.examplemod.economy.EconomyManager.get(s.overworld()).cargo().order(shipment.id()).orElse(null);if(book!=null){summary.putString("OrderState",book.state().name());summary.putLong("TotalCr",Math.addExact(book.total(),shipment.trip().getLong("Tolls")));summary.putString("Dispatch",shipment.trip().getBoolean("Fleet")?PublicFleetRegistry.get(s).progress(shipment.id()):CargoCharters.get(s).progress(shipment.id()));}result.put("Shipment",summary);}}
        /*
         * Cargo lines are physical infrastructure, not a detail of the selected object. Send every
         * line the requester may see so an atlas without focus can still draw the real network.
         * Focused/route rails were added first, so the 2048 sample budget always favours them.
         */
        var allLines=new ArrayList<>(CargoLineRegistry.get(s).all());
        allLines.sort(Comparator.comparing((CargoLineRegistry.Line line)->line.name(),String.CASE_INSENSITIVE_ORDER).thenComparing(line->line.id().toString()));
        for(var line:allLines){
            if(!CargoOwners.permits(s,requester,CargoLineRegistry.access(s,line)))continue;
            for(int i=1;i<line.stops().size();i++)appendLeg(s,rails,seen,dimension,line.id(),line.stops().get(i-1).point(),line.stops().get(i).point(),false);
        }
        // Atlas rails are physical Create infrastructure, independent of cargo line registration.
        for(var graph:RailwaySavedData.load(s).getTrackNetworks().values())for(var nodeLocation:graph.getNodes()){var node=graph.locateNode(nodeLocation);if(node==null)continue;
            if(rails.size()>=2048)break;
            for(var entry:graph.getConnectionsFrom(node).entrySet()){
                if(rails.size()>=2048)break;
                var other=entry.getKey();if(!node.getLocation().dimension.equals(other.getLocation().dimension))continue;
                var samples=CargoRailConnections.geometry(s,new CargoRailConnections.Path(graph.id,List.of(node,other)));int sampleIndex=0;
                for(Tag raw:samples){
                    if(rails.size()>=2048)break;var sample=((CompoundTag)raw).copy();
                    var edge=entry.getValue();double from=edge.getLength()*sampleIndex/samples.size(),to=edge.getLength()*(++sampleIndex)/samples.size();
                    if(!dimension.isEmpty()&&!sample.getString("Dimension").equals(dimension))continue;
                    var tolls=com.example.examplemod.economy.EconomyManager.get(s.overworld()).roadTolls().charges(RailGeometry.span(graph,edge,from,to));
                    if(!tolls.isEmpty()){sample.putBoolean("Toll",true);sample.putUUID("Claim",tolls.getFirst().road());for(var toll:tolls)if(!railsOnly&&markers.size()<256&&roadMarkers.add(toll.road())){var road=com.example.examplemod.economy.EconomyManager.get(s.overworld()).roadTolls().road(toll.road()).orElseThrow();var position=edge.getPosition(graph,(from+to)/2/Math.max(.0001,edge.getLength()));marker(markers,road.id(),"road",road.name(),road.owner().name(s),"ACTIVE",new CargoInfrastructure.Location(node.getLocation().dimension.location(),net.minecraft.core.BlockPos.containing(position)));markers.getCompound(markers.size()-1).putLong("Rate",road.rate());}}
                    if(seen.add(key(sample)))rails.add(sample);
                }
            }
        }
        result.put("Markers",markers);result.put("Rails",rails);result.putBoolean("Limited",markers.size()>=256||rails.size()>=2048);return result;
    }
    private static void appendLeg(net.minecraft.server.MinecraftServer s,ListTag rails,Set<RailKey> seen,String dimension,UUID id,UUID from,UUID next,boolean highlight){if(rails.size()>=2048)return;var line=CargoLineRegistry.get(s).find(id).orElse(null);if(line==null)return;for(int i=0;i<line.stops().size()-1;i++)if(line.stops().get(i).point().equals(from)&&line.stops().get(i+1).point().equals(next)){var path=CargoRailConnections.path(s,line.stops().get(i).station(),line.stops().get(i+1).station());if(path.isEmpty())return;for(Tag raw:CargoRailConnections.geometry(s,path.get())){if(rails.size()>=2048)return;var t=((CompoundTag)raw).copy();if(!dimension.isEmpty()&&!t.getString("Dimension").equals(dimension))continue;if(!seen.add(key(t)))continue;t.putUUID("Line",id);if(highlight)t.putBoolean("Focus",true);rails.add(t);}return;}}
    private static CompoundTag displayTrip(net.minecraft.server.MinecraftServer s,CargoShipmentRegistry.Shipment shipment){var actual=PublicFleetRegistry.get(s).currentTrip(shipment.id());return actual.isEmpty()?shipment.trip():actual;}
    private static RailKey key(CompoundTag t){double x1=t.getDouble("X"),z1=t.getDouble("Z"),x2=t.getDouble("ToX"),z2=t.getDouble("ToZ");boolean swap=x1>x2||(x1==x2&&z1>z2);return new RailKey(t.getString("Dimension"),swap?x2:x1,swap?z2:z1,swap?x1:x2,swap?z1:z2);}
    private static void routeStops(net.minecraft.server.MinecraftServer server,CompoundTag result,List<CargoRoutePlanner.Leg> legs){
        var stops=new ListTag();if(!legs.isEmpty()){routeStop(server,stops,legs.getFirst().from());for(var leg:legs)routeStop(server,stops,leg.next());}result.put("RouteStops",stops);
    }
    private static void routeStop(net.minecraft.server.MinecraftServer server,ListTag stops,UUID pointId){
        if(stops.size()>=33)return;var point=CargoPointRegistry.find(server,pointId).orElse(null);if(point==null)return;
        var stop=new CompoundTag();stop.putUUID("Id",pointId);stop.putString("Name",point.name());stop.putInt("Index",stops.size()+1);
        point.routers().stream().sorted(Comparator.comparing(ref->ref.dimension()+"/"+ref.pos().asLong())).findFirst().ifPresent(ref->stop.put("Location",new CargoInfrastructure.Location(ref.dimension(),ref.pos()).save()));stops.add(stop);
    }
    private static void marker(ListTag list,UUID id,String kind,String name,String owner,String status,CargoInfrastructure.Location pos){if(list.size()>=256)return;var t=CenterService.row(id,name,owner,status);t.putString("Kind",kind);t.put("Location",pos.save());list.add(t);}
}
