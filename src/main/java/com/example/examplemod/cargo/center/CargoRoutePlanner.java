package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.*;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import net.minecraft.server.MinecraftServer;
import java.util.*;

/** Permission-aware shortest paths across declared, physically connected Create lines. No schedule control or ETA guesses. */
public final class CargoRoutePlanner {
    public enum Preference { CHEAPEST, FEWEST_TRANSFERS, NO_TOLLS, FASTEST }
    public record Leg(UUID line,UUID from,UUID next,UUID train,long tariff) {}
    public record Route(List<Leg> legs,long price,net.minecraft.nbt.CompoundTag trip){public Route{legs=List.copyOf(legs);trip=trip.copy();}public Route(List<Leg> legs,long price){this(legs,price,new net.minecraft.nbt.CompoundTag());}@Override public net.minecraft.nbt.CompoundTag trip(){return trip.copy();}}
    private CargoRoutePlanner() {}
    public static boolean equipment(MinecraftServer s,UUID point,UUID station){var p=CargoPointRegistry.find(s,point).orElse(null);var real=CargoRailConnections.station(s,station);if(p==null||real==null)return false;
        for(var ref:p.routers()){var router=CargoInfrastructure.router(s,ref);if(router==null)continue;
            var above=new CargoInfrastructure.Location(ref.dimension(),ref.pos().above()).loaded(s);
            if(above instanceof PostboxBlockEntity box&&box.acceptsPackages&&CargoAddress.postboxFilter(point).equals(box.getFilterString())&&real.station().connectedPorts.containsKey(ref.pos().above()))return true;}
        return false;
    }
    public static Optional<Route> plan(MinecraftServer s,OwnerRef requester,UUID from,UUID destination,Preference preference){
        if(from.equals(destination))return Optional.of(new Route(List.of(),0));
        return plan(s,requester,from,destination,preference,"ANY");
    }
    public static Optional<Route> plan(MinecraftServer s,OwnerRef requester,UUID from,UUID destination,Preference preference,String service){
        return plan(s,requester,from,destination,preference,service,1,net.minecraft.world.item.ItemStack.EMPTY);
    }
    public static Optional<Route> plan(MinecraftServer s,OwnerRef requester,UUID from,UUID destination,Preference preference,String service,int quantity,net.minecraft.world.item.ItemStack item){
        if(from.equals(destination))return Optional.of(new Route(List.of(),0));
        if(!Set.of("ANY","REGULAR","CHARTER").contains(service))throw new IllegalArgumentException("delivery_route_unavailable");
        var registry=CargoLineRegistry.get(s);var edges=new ArrayList<Leg>();
        for(var line:registry.all())if(service.equals("ANY")||line.mode().name().equals(service))for(int i=0;i<line.stops().size()-1;i++){var a=line.stops().get(i);var b=line.stops().get(i+1);
            if(!equipment(s,a.point(),a.station())||!equipment(s,b.point(),b.station()))continue;
            UUID train=registry.availableTrain(s,line,requester,a.point(),b.point());if(train!=null)edges.add(new Leg(line.id(),a.point(),b.point(),train,line.tariff()));}
        var regular=preference==Preference.NO_TOLLS?Optional.<Route>empty():shortest(edges,from,destination,preference);
        var fleet=service.equals("REGULAR")?Optional.<Route>empty():PublicFleetRegistry.get(s).plan(s,requester,from,destination,quantity,item,preference.name());
        if(preference==Preference.FASTEST){if(fleet.isPresent()){var trip=fleet.get().trip();if(regular.isPresent())trip.putBoolean("RegularEtaUnknown",true);return Optional.of(new Route(fleet.get().legs(),fleet.get().price(),trip));}if(regular.isPresent()){var trip=regular.get().trip();trip.putString("Preference",preference.name());trip.putBoolean("RegularEtaUnknown",true);return Optional.of(new Route(regular.get().legs(),regular.get().price(),trip));}}
        if(fleet.isEmpty())return preference(regular,preference);if(regular.isEmpty())return fleet;if(preference==Preference.FEWEST_TRANSFERS){int a=transfers(fleet.get()),b=transfers(regular.get());if(a!=b)return preference(a<b?fleet:regular,preference);}return preference(fleet.get().price()<regular.get().price()?fleet:regular,preference);
    }
    private static Optional<Route> preference(Optional<Route> route,Preference preference){return route.map(r->{var trip=r.trip();trip.putString("Preference",preference.name());return new Route(r.legs(),r.price(),trip);});}
    private static int transfers(Route route){int result=0;for(int i=1;i<route.legs().size();i++){var a=route.legs().get(i-1);var b=route.legs().get(i);if(!a.line().equals(b.line())||!a.train().equals(b.train()))result++;}return result;}
    /** Bounded Dijkstra. A transfer means changing service/train, not passing another station. */
    public static Optional<Route> shortest(List<Leg> edges,UUID from,UUID destination,Preference preference){
        record Candidate(UUID point,List<Leg> path,long price,int transfers) {}
        record Key(UUID point,UUID line,UUID train,int hops) {}
        Comparator<Candidate> order=preference==Preference.CHEAPEST?Comparator.comparingLong(Candidate::price).thenComparingInt(c->c.path().size()):Comparator.<Candidate>comparingInt(Candidate::transfers).thenComparingLong(Candidate::price).thenComparingInt(c->c.path().size());
        var outgoing=new HashMap<UUID,List<Leg>>();for(var edge:edges)if(edge.tariff()>=0)outgoing.computeIfAbsent(edge.from(),p->new ArrayList<>()).add(edge);
        // The state is Markov: no path-specific visited-set is discarded by best[]. With
        // nonnegative costs/transfers and hops as final tie-break, a cycle cannot improve a winner.
        var queue=new PriorityQueue<>(order);queue.add(new Candidate(from,List.of(),0,0));var best=new HashMap<Key,Candidate>();int scanned=0;
        while(!queue.isEmpty()&&scanned++<12000){var current=queue.remove();if(current.point().equals(destination))return Optional.of(new Route(current.path(),current.price()));if(current.path().size()>=32)continue;
            var last=current.path().isEmpty()?null:current.path().getLast();var key=new Key(current.point(),last==null?null:last.line(),last==null?null:last.train(),current.path().size());
            var previous=best.get(key);if(previous!=null&&order.compare(previous,current)<=0)continue;best.put(key,current);
            for(var edge:outgoing.getOrDefault(current.point(),List.of())){long cost;try{cost=Math.addExact(current.price(),edge.tariff());}catch(ArithmeticException overflow){continue;}
                int transfers=current.transfers()+(last!=null&&(!last.line().equals(edge.line())||!last.train().equals(edge.train()))?1:0);
                var path=new ArrayList<>(current.path());path.add(edge);queue.add(new Candidate(edge.next(),path,cost,transfers));}}
        return Optional.empty();
    }
}
