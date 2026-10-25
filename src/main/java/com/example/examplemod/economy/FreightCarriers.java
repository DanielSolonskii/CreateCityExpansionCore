package com.example.examplemod.economy;

import com.example.examplemod.city.*;
import com.example.examplemod.railway.*;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Opt-in commercial trains, pickup/delivery cycles and buyer-authorized road spending. */
public final class FreightCarriers {
    public record Plan(UUID fleet,RailwayRouteStrategy strategy,FreightTransportBudget budget){}
    private record Quote(UUID subject,UUID city,UUID fleet,RailwayRouteStrategy strategy,long expires){}
    private final Map<UUID,UUID> published=new LinkedHashMap<>();
    public final Map<UUID,UUID> saleSources=new LinkedHashMap<>();
    private final Map<UUID,Plan> plans=new LinkedHashMap<>();
    private final Map<UUID,FreightTransportBudget> budgets=new LinkedHashMap<>();
    private final Map<UUID,Quote> quotes=new HashMap<>();
    private final Map<UUID,String> phases=new HashMap<>();
    private final Map<UUID,long[]> loading=new HashMap<>();
    private final FreightLogistics freight;private final EconomyManager economy;
    public FreightCarriers(FreightLogistics freight,EconomyManager economy){this.freight=freight;this.economy=economy;}
    public UUID controller(FreightAssignment a){var c=economy.contract(a.subject);var source=freight.point(a.source);return a.buyerTransport&&c!=null?c.issuerCityId():source==null?null:source.cityId;}
    public boolean controls(FreightAssignment a,UUID city){return a!=null&&Objects.equals(controller(a),city);}
    private boolean live(UUID subject){var c=economy.contract(subject);return c!=null&&!c.state().terminal()&&c.state()!=EconomyTypes.ContractState.DELIVERED;}
    public boolean booked(UUID fleet,UUID except){return freight.assignments.values().stream().anyMatch(a->!a.subject.equals(except)&&fleet.equals(a.train)&&live(a.subject));}
    public String publish(RailwayManager railway,UUID city,UUID fleet,boolean value){var f=railway.train(fleet);if(f==null||!city.equals(f.cityId()))return "permission";if(!value&&booked(fleet,null))return "train_busy";if(value)published.put(fleet,city);else published.remove(fleet);freight.markChanged();return "ok";}
    public boolean eligible(FreightAssignment a,UUID city,CityFleetTrain fleet){var actual=fleet==null?null:CreateRailwayAdapter.train(fleet.createTrainId());var source=a==null?null:freight.point(a.source);return controls(a,city)&&fleet!=null&&actual!=null&&actual.runtime.getSchedule()==null&&fleet.activeLineId()==null&&(fleet.type()==RailwayTypes.TrainType.FREIGHT||fleet.type()==RailwayTypes.TrainType.MIXED)&&!booked(fleet.fleetTrainId(),a.subject)&&(city.equals(fleet.cityId())||source!=null&&source.cityId.equals(fleet.cityId())&&source.cityId.equals(published.get(fleet.fleetTrainId())));}
    public String budget(UUID subject,UUID city,long limit){var a=freight.assignments.get(subject);if(!controls(a,city)||!a.buyerTransport||!live(subject))return "permission";if(limit<0||limit>com.example.examplemod.Config.ECONOMY_MAX_TRANSACTION.get())return "invalid";if(!budgets.computeIfAbsent(subject,k->new FreightTransportBudget()).limit(limit))return "budget";freight.markChanged();return "ok";}
    public FreightTransportBudget budget(UUID subject){return budgets.computeIfAbsent(subject,k->new FreightTransportBudget());}
    public FreightTransportBudget budgetView(UUID subject){return budgets.getOrDefault(subject,new FreightTransportBudget());}
    private static RailwayRouteOption choose(List<RailwayRouteOption> routes,RailwayRouteStrategy strategy){return routes.stream().filter(r->r.strategy()==strategy).findFirst().orElse(routes.isEmpty()?null:routes.getFirst());}
    private boolean foreignParcels(Train train,UUID subject){for(var c:train.carriages){var inventory=c.storage.getAllItems();if(inventory==null)continue;for(int i=0;i<inventory.getSlots();i++){var box=inventory.getStackInSlot(i);if(!com.simibubi.create.content.logistics.box.PackageItem.isPackage(box))continue;var ids=FreightLogistics.packageTokens(box);if(ids.isEmpty()||ids.stream().anyMatch(id->{var t=economy.shipmentToken(id);return t==null||!subject.equals(t.subjectId())||t.state()!=EconomyTypes.ShipmentTokenState.ACTIVE;}))return true;}}return false;}
    public FreightLogistics.RoutePlan options(ServerLevel level,City city,UUID subject,UUID fleetId){
        var a=freight.assignments.get(subject);var railway=RailwayManager.get(level);var fleet=railway.train(fleetId);var source=a==null?null:freight.point(a.source);var dest=a==null?null:freight.point(a.destination);
        if(a==null||source==null||dest==null||!eligible(a,city.getId(),fleet))return new FreightLogistics.RoutePlan("","",List.of(),"train_not_owned");
        Train train=CreateRailwayAdapter.train(fleet.createTrainId());GlobalStation origin=CreateRailwayAdapter.station(source.station),target=CreateRailwayAdapter.station(dest.station);
        if(train==null||train.getCurrentStation()==null||train.navigation.destination!=null||railway.activeTrip(fleet.createTrainId())!=null)return new FreightLogistics.RoutePlan(source.name,dest.name,List.of(),"trip_active");
        if(foreignParcels(train,subject))return new FreightLogistics.RoutePlan(source.name,dest.name,List.of(),"cargo_conflict");
        if(origin==null||target==null)return new FreightLogistics.RoutePlan(source.name,dest.name,List.of(),"source_station_missing");
        var cities=CityManager.get(level);var type=RailwayOwnershipTypes.OwnerType.CITY;
        boolean atSource=origin.id.equals(train.getCurrentStation().id);
        var pickup=atSource?List.<RailwayRouteOption>of():RailwayRoutePlanner.plan(train,origin,railway,type,city.getId(),cities);
        var loaded=atSource?RailwayRoutePlanner.plan(train,target,railway,type,city.getId(),cities):RailwayRoutePlanner.planFromStation(train,origin,target,railway,type,city.getId(),cities);
        if(loaded.isEmpty()||!atSource&&pickup.isEmpty())return new FreightLogistics.RoutePlan(source.name,dest.name,List.of(),"no_route");
        var paths=new HashSet<List<RailwayEdgeReference>>();quotes.entrySet().removeIf(e->e.getValue().expires<net.minecraft.Util.getMillis());var out=new ArrayList<RailwayRouteOption>();
        for(var strategy:RailwayRouteStrategy.values()){
            var delivery=choose(loaded,strategy);var approach=choose(pickup,strategy);long cost=Math.addExact(delivery.tariffCost(),approach==null?0:approach.tariffCost()),eta=Math.addExact(delivery.estimatedTicks(),approach==null?0:approach.estimatedTicks());
            var edges=new ArrayList<RailwayEdgeReference>();if(approach!=null)edges.addAll(approach.edges());edges.addAll(delivery.edges());if(!paths.add(List.copyOf(edges)))continue;UUID id=UUID.randomUUID();
            out.add(new RailwayRouteOption(id,strategy,delivery.graphId(),delivery.destinationStationId(),delivery.backwards(),edges,delivery.distance()+(approach==null?0:approach.distance()),cost,eta,delivery.contributions(),delivery.topologyFingerprint()));quotes.put(id,new Quote(subject,city.getId(),fleetId,strategy,net.minecraft.Util.getMillis()+60000));
        }
        return new FreightLogistics.RoutePlan(source.name,dest.name,out,"");
    }
    public boolean select(ServerLevel level,City city,UUID subject,UUID fleetId,UUID optionId){
        var q=quotes.get(optionId);var a=freight.assignments.get(subject);var railway=RailwayManager.get(level);var fleet=railway.train(fleetId);
        if(q==null||q.expires<net.minecraft.Util.getMillis()||!q.subject.equals(subject)||!q.city.equals(city.getId())||!q.fleet.equals(fleetId)||!eligible(a,city.getId(),fleet)||a.running||freight.transit.activeCargo(subject))return false;
        var actual=CreateRailwayAdapter.train(fleet.createTrainId());var previous=a.train==null?null:railway.train(a.train);
        // A quote is not a lease: native controls may have moved either train since it was shown.
        if(actual==null||actual.getCurrentStation()==null||actual.navigation.destination!=null||railway.activeTrip(fleet.createTrainId())!=null||previous!=null&&railway.activeTrip(previous.createTrainId())!=null||foreignParcels(actual,subject))return false;
        quotes.remove(optionId);plans.put(subject,new Plan(fleetId,q.strategy,budget(subject)));a.train=fleetId;a.running=false;a.routeSelection=null;a.dispatchError="";phases.put(subject,"ready");freight.markChanged();return true;
    }

    public void clearPlan(UUID subject){plans.remove(subject);loading.remove(subject);phases.remove(subject);freight.markChanged();}
    public boolean configured(UUID subject){return plans.containsKey(subject);}
    public String phase(UUID subject){return phases.getOrDefault(subject,plans.containsKey(subject)?"ready":"route");}
    public UUID payer(UUID fleet,UUID destination,RailwayManager railway){for(var a:freight.assignments.values())if(a.buyerTransport&&fleet.equals(a.train)&&live(a.subject)&&plans.containsKey(a.subject)){var selected=railway.selectedRoute(fleet);if(!a.running&&(selected==null||!Objects.equals(a.routeSelection,selected.selectionId())))continue;var s=freight.point(a.source);var d=freight.point(a.destination);if(s!=null&&d!=null&&(destination.equals(s.station)||destination.equals(d.station)))return controller(a);}return null;}
    public void tick(MinecraftServer server){
        if(server.getTickCount()%5!=0)return;
        var railway=RailwayManager.get(server.overworld());
        for(var a:freight.assignments.values()){
            if(!a.buyerTransport||!a.running||freight.transit.routes.regional(a.subject))continue;var plan=plans.get(a.subject);if(plan==null){a.running=false;continue;}
            var fleet=railway.train(plan.fleet);Train train=fleet==null?null:CreateRailwayAdapter.train(fleet.createTrainId());var source=freight.point(a.source);var dest=freight.point(a.destination);
            if(!live(a.subject)){a.running=false;continue;}
            if(train==null||source==null||dest==null||!eligible(a,controller(a),fleet)){stop(a,"train_busy");continue;}
            if(railway.activeTrip(fleet.createTrainId())!=null||train.navigation.destination!=null){phases.put(a.subject,"transit");continue;}
            if(foreignParcels(train,a.subject)){stop(a,"cargo_conflict");continue;}
            if(!FreightTransit.availableOrUnloaded(source)||!FreightTransit.availableOrUnloaded(dest)||!FreightLogistics.route(source,dest).equals("connected")){phases.put(a.subject,!FreightTransit.availableOrUnloaded(source)?"source_offline":"destination_offline");continue;}
            var station=train.getCurrentStation();if(station==null){stop(a,"train_not_at_source");continue;}
            boolean atSource=station.id.equals(source.station),atDestination=station.id.equals(dest.station);long cargo=freight.loadedAmount(train,a.subject);
            if(atDestination&&cargo>0){phases.put(a.subject,"unloading");continue;}
            var contract=economy.contract(a.subject);if(contract==null)continue;
            if(atSource){
                phases.put(a.subject,"loading");if(cargo<=0)continue;
                var observed=loading.computeIfAbsent(a.subject,k->new long[]{cargo,server.getTickCount()});if(observed[0]!=cargo){observed[0]=cargo;observed[1]=server.getTickCount();}
                if(cargo<contract.remainingAmount()&&server.getTickCount()-observed[1]<100)continue;
            }
            var target=CreateRailwayAdapter.station(atSource?dest.station:source.station);if(target==null){stop(a,"destination_station_missing");continue;}
            var routes=RailwayRoutePlanner.plan(train,target,railway,RailwayOwnershipTypes.OwnerType.CITY,controller(a),CityManager.get(server.overworld()));var selected=choose(routes,plan.strategy);
            if(selected==null){stop(a,"no_route");continue;}if(!budget(a.subject).canReserve(selected.tariffCost())){stop(a,"transport_budget");continue;}
            if(economy.treasury(controller(a))<selected.tariffCost()){stop(a,"insufficient_funds");continue;}
            railway.proposeRoutes(fleet.fleetTrainId(),List.of(selected));if(!railway.selectRoute(fleet.fleetTrainId(),selected.id(),server.overworld().getGameTime())){stop(a,"no_route");continue;}
            a.routeSelection=railway.selectedRoute(fleet.fleetTrainId()).selectionId();var path=RailwayTripAccountingService.selectedPath(train,target,selected);
            if(path==null||train.navigation.startNavigation(path)<0){stop(a,"route_invalidated");continue;}
            phases.put(a.subject,atSource?"delivery":"pickup");loading.remove(a.subject);freight.markChanged();
        }
    }
    private void stop(FreightAssignment a,String why){a.running=false;a.dispatchError=why;phases.put(a.subject,why);freight.markChanged();}
    public CompoundTag snapshot(ServerLevel level,City city){var out=new CompoundTag();var list=new ListTag();var railway=RailwayManager.get(level);for(var fleet:railway.trainsForCityIdsAndPersonal())if(city.getId().equals(fleet.cityId())){var t=new CompoundTag();t.putUUID("Id",fleet.fleetTrainId());t.putString("Name",fleet.lastKnownName());t.putBoolean("Published",city.getId().equals(published.get(fleet.fleetTrainId())));t.putBoolean("Busy",booked(fleet.fleetTrainId(),null));list.add(t);}out.put("Fleet",list);return out;}
    public CompoundTag save(){var root=new CompoundTag();root.putInt("Version",1);var pub=new ListTag();published.forEach((id,city)->{var t=new CompoundTag();t.putUUID("Train",id);t.putUUID("City",city);pub.add(t);});root.put("Published",pub);var entries=new ListTag();budgets.forEach((id,b)->{var t=b.save();t.putUUID("Subject",id);var p=plans.get(id);if(p!=null){t.putUUID("Train",p.fleet);t.putString("Strategy",p.strategy.name());}entries.add(t);});root.put("Deliveries",entries);var sales=new ListTag();saleSources.forEach((sale,source)->{var t=new CompoundTag();t.putUUID("Sale",sale);t.putUUID("Source",source);sales.add(t);});root.put("Sources",sales);return root;}
    public void load(CompoundTag root){if(root.getInt("Version")>1)throw new IllegalStateException("New freight carrier format");for(Tag raw:root.getList("Published",10)){var t=(CompoundTag)raw;published.put(t.getUUID("Train"),t.getUUID("City"));}for(Tag raw:root.getList("Deliveries",10)){var t=(CompoundTag)raw;var b=FreightTransportBudget.load(t);UUID id=t.getUUID("Subject");budgets.put(id,b);if(t.hasUUID("Train"))plans.put(id,new Plan(t.getUUID("Train"),RailwayRouteStrategy.valueOf(t.getString("Strategy")),b));}for(Tag raw:root.getList("Sources",10)){var t=(CompoundTag)raw;saleSources.put(t.getUUID("Sale"),t.getUUID("Source"));}}
}
