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
        result.putInt("InspectionVersion",1);
        result.putInt("TradeVersion",1);
        String dimension=request.getString("Dimension");boolean railsOnly=request.getBoolean("RailsOnly");
        UUID focus=request.hasUUID("Focus")?request.getUUID("Focus"):null;
        var shipments=CargoShipmentRegistry.get(s);
        boolean parcelLimit=false;
        if(!railsOnly){
            if (focus!=null) {
                var selected=shipments.forParcel(focus).orElse(null);
                if (ShipmentInspection.permits(actor,selected))
                    parcelMarker(actor,markers,dimension,visible,selected,selected.navigationObservation(focus));
            }
            var cargoStations=CargoLineRegistry.get(s).stations();
            var passengerStations=new HashSet<UUID>();
            for (var stop : com.example.examplemod.passenger.PassengerService.stops(actor)) {
                if (!dimension.isEmpty() && !dimension.equals(stop.dimension().toString())) continue;
                marker(markers,stop.id(),"station",stop.name(),"","CONNECTED",
                        new CargoInfrastructure.Location(stop.dimension(),stop.stationPos()));
                passengerStations.add(stop.id());
                var marker=markers.getCompound(markers.size()-1);
                marker.putBoolean("PassengerPlatform",true);
                UUID cargoPoint=cargoStations.get(stop.id());
                if (cargoPoint!=null && visible.contains(cargoPoint)) marker.putUUID("OpenId",cargoPoint);
            }
            for(UUID id:visible){var p=CargoPointRegistry.find(s,id).orElse(null);if(p==null)continue;p.routers().stream().findFirst().ifPresent(ref->marker(markers,p.id(),"point",p.name(),CargoOwners.point(s,p).name(s),CargoInfrastructure.router(s,ref)==null?"UNLOADED":"CONNECTED",new CargoInfrastructure.Location(ref.dimension(),ref.pos())));}
            for(var w:CargoWarehouseRegistry.get(s).all())if(CargoWarehouseRegistry.permits(actor,w))marker(markers,w.id(),"warehouse",w.name(),CargoWarehouseRegistry.access(s,w).owner().name(s),CargoWarehouseRegistry.ticker(s,w)==null?"UNLOADED":"CONNECTED",w.ticker());
            for(var ref:cargoStations.entrySet()) {
                if (markers.size()>=256) break;
                if (passengerStations.contains(ref.getKey()) || !visible.contains(ref.getValue())) continue;
                var station=CargoRailConnections.station(s,ref.getKey());
                if (station==null) continue;
                marker(markers,ref.getKey(),"station",station.station().name,"","CONNECTED",
                        new CargoInfrastructure.Location(station.station().getBlockEntityDimension().location(),station.station().getBlockEntityPos()));
                markers.getCompound(markers.size()-1).putUUID("OpenId",ref.getValue());
            }
            for(var registration:CargoTrainRegistry.get(s).registrations())if(markers.size()<256 && CargoOwners.train(s,requester,registration)){var train=RailwaySavedData.load(s).getTrains().get(registration.trainId());if(train==null)continue;for(var trainDimension:train.getPresentDimensions()){var pos=train.getPositionInDimension(trainDimension);if(pos.isPresent()){marker(markers,train.id,"train",registration.displayName(),CargoOwners.get(s).owner("train/"+train.id,registration.ownerId()).name(s),train.carriageWaitingForChunks>0?"UNLOADED":"AVAILABLE",new CargoInfrastructure.Location(trainDimension.location(),pos.get()));break;}}}
            int remainingParcels=512;
            var recent=shipments.recentMapShipments(512);
            parcelLimit=recent.size()==512;
            for (var shipment:recent) {
                if (remainingParcels==0 || markers.size()>=256) break;
                if (!ShipmentInspection.permits(actor,shipment)) continue;
                var parcels=shipment.parcelPositions(Math.min(256,remainingParcels));
                remainingParcels-=parcels.size();
                if (parcels.size()<shipment.parcelCount() || remainingParcels==0) parcelLimit=true;
                for (var parcel:parcels) {
                    if (markers.size()>=256) break;
                    if (!parcel.getUUID("Id").equals(focus)) parcelMarker(actor,markers,dimension,visible,shipment,parcel);
                }
            }
        }
        if(request.contains("MapRoute")){var route=request.getCompound("MapRoute");UUID from=CenterOperations.id(route,"SourcePoint"),to=CenterOperations.id(route,"DestinationPoint");if(visible.contains(from)&&visible.contains(to)){var found=CargoRoutePlanner.plan(s,requester,from,to,CargoRoutePlanner.Preference.valueOf(route.getString("Preference")),route.contains("Service")?route.getString("Service"):"ANY");if(found.isPresent()){result.putLong("Price",found.get().price());if(found.get().trip().getBoolean("Fleet")){for(var raw:PublicFleetRegistry.map(s,found.get().trip()).getList("Rails",10))if(rails.size()<2048){var sample=((CompoundTag)raw).copy();if(dimension.isEmpty()||sample.getString("Dimension").equals(dimension))rails.add(sample);}}for(var leg:found.get().legs())appendLeg(s,rails,seen,dimension,leg.line(),leg.from(),leg.next(),true);routeStops(s,result,found.get().legs());result.putBoolean("RouteFound",true);}else result.putBoolean("Blocked",true);}}
        else if(focus!=null){var line=CargoLineRegistry.get(s).find(focus).orElse(null);if(line!=null&&CargoOwners.permits(s,requester,CargoLineRegistry.access(s,line)))for(int i=1;i<line.stops().size();i++)appendLeg(s,rails,seen,dimension,line.id(),line.stops().get(i-1).point(),line.stops().get(i).point(),true);
            var shipment=shipments.find(focus).orElseGet(()->shipments.forParcel(focus).orElse(null));
            if(ShipmentInspection.permits(actor,shipment)) {
                boolean byParcel=!focus.equals(shipment.id());
                var parcel=byParcel ? shipment.navigationObservation(focus) : shipment.latestObservation();
                boolean available=!byParcel || ShipmentInspection.location(actor,parcel,visible::contains)!=null;
                if (!byParcel && parcel.contains("Location") && ShipmentInspection.location(actor,parcel,visible::contains)==null)
                    parcel=new CompoundTag();
                if (available) appendShipment(actor,dimension,shipment,parcel,visible,result,rails,seen);
            }
        }
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
        result.put("Markers",markers);result.put("Rails",rails);result.putBoolean("Limited",parcelLimit||markers.size()>=256||rails.size()>=2048);return result;
    }
    private static void appendLeg(net.minecraft.server.MinecraftServer s,ListTag rails,Set<RailKey> seen,String dimension,UUID id,UUID from,UUID next,boolean highlight){if(rails.size()>=2048)return;var line=CargoLineRegistry.get(s).find(id).orElse(null);if(line==null)return;for(int i=0;i<line.stops().size()-1;i++)if(line.stops().get(i).point().equals(from)&&line.stops().get(i+1).point().equals(next)){var path=CargoRailConnections.path(s,line.stops().get(i).station(),line.stops().get(i+1).station());if(path.isEmpty())return;for(Tag raw:CargoRailConnections.geometry(s,path.get())){if(rails.size()>=2048)return;var t=((CompoundTag)raw).copy();if(!dimension.isEmpty()&&!t.getString("Dimension").equals(dimension))continue;if(!seen.add(key(t)))continue;t.putUUID("Line",id);if(highlight)t.putBoolean("Focus",true);rails.add(t);}return;}}
    private static void parcelMarker(ServerPlayer actor,ListTag markers,String dimension,Set<UUID> visible,
                                     CargoShipmentRegistry.Shipment shipment,CompoundTag parcel) {
        var place=ShipmentInspection.location(actor,parcel,visible::contains);
        if (place==null || !dimension.isEmpty() && !dimension.equals(place.dimension().toString())) return;
        marker(markers,parcel.getUUID("Id"),"shipment",shipment.type().getHoverName().getString(),
                shipment.owner().name(actor.getServer()),"LAST_OBSERVED",place);
        markers.getCompound(markers.size()-1).putUUID("OpenId",shipment.id());
    }
    private static void appendShipment(ServerPlayer actor,String dimension,CargoShipmentRegistry.Shipment shipment,
            CompoundTag parcel,Set<UUID> visible,CompoundTag result,ListTag rails,Set<RailKey> seen) {
        var server=actor.getServer();
        var trace=ShipmentInspection.describe(actor,shipment,parcel,visible,System.currentTimeMillis());
        var stops=new ListTag();
        var legs=shipment.route();
        boolean fleet=shipment.trip().getBoolean("Fleet");
        int next=parcel.isEmpty() ? (shipment.stage()==CargoShipmentRegistry.Stage.REQUESTED?0:-1) : parcel.getInt("NextLeg");
        if (shipment.stage()==CargoShipmentRegistry.Stage.COMPLETED || shipment.stage()==CargoShipmentRegistry.Stage.CANCELLED
                || shipment.stage()==CargoShipmentRegistry.Stage.DELIVERED || "DELIVERED".equals(parcel.getString("Observation"))
                || "RECOVERED".equals(parcel.getString("Observation"))) next=-1;
        if (!fleet && next>=0 && next<legs.size() && ShipmentInspection.visibleLeg(legs.get(next),visible,id->ShipmentInspection.permitsLine(actor,id))) {
            var leg=legs.get(next);
            appendLeg(server,rails,seen,dimension,leg.line(),leg.from(),leg.next(),true);
            routeStop(server,stops,leg.from()); routeStop(server,stops,leg.next());
        } else if (fleet && next==0 && visible.contains(shipment.source()) && visible.contains(shipment.finalPoint())) {
            routeStop(server,stops,shipment.source()); routeStop(server,stops,shipment.finalPoint());
            var current=PublicFleetRegistry.get(server).currentTrip(shipment.id());
            var trip=current.isEmpty()?shipment.trip():current;
            var registration=trip.hasUUID("Train")?CargoTrainRegistry.find(server,trip.getUUID("Train")).orElse(null):null;
            if(registration!=null && CargoOwners.train(server,OwnerRef.player(actor.getUUID()),registration)) {
                // The customer's freight section is distinct from the carrier's approach and return.
                var phase=trip.getCompound("Phases").getCompound("DELIVERY");
                if(!phase.isEmpty()) {
                    var phases=new CompoundTag(); phases.put("DELIVERY",phase);
                    var delivery=new CompoundTag(); delivery.put("Phases",phases);
                    for(var raw:PublicFleetRegistry.map(server,delivery).getList("Rails",Tag.TAG_COMPOUND)) {
                        if(rails.size()>=2048) break;
                        var edge=(CompoundTag)raw;
                        if((dimension.isEmpty() || dimension.equals(edge.getString("Dimension"))) && seen.add(key(edge))) rails.add(edge);
                    }
                }
            }
        }
        trace.putString("Geometry",!rails.isEmpty()?"track":stops.size()==2?"schematic":"none");
        result.put("RouteStops",stops);
        trace.putString("Name",shipment.type().getHoverName().getString());
        var book=com.example.examplemod.economy.EconomyManager.get(server.overworld()).cargo().order(shipment.id()).orElse(null);
        if(book!=null) {
            trace.putString("OrderState",book.state().name());
            trace.putLong("TotalCr",Math.addExact(book.total(),shipment.trip().getLong("Tolls")));
            trace.putString("Dispatch",fleet?PublicFleetRegistry.get(server).progress(shipment.id()):CargoCharters.get(server).progress(shipment.id()));
        }
        result.put("Shipment",trace);
    }
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
