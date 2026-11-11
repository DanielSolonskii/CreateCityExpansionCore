package com.example.examplemod.cargo.center;

import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import java.util.*;
import java.util.function.Supplier;

/** Bounded, derived leases for the unfinished service already paid before physical road billing. */
public final class LegacyRailLeases {
    private static final int MAX_LEASES=1024,MAX_FROZEN=512,MAX_SPANS=32768;
    private LegacyRailLeases(){}
    private record Key(UUID order,UUID train,String phase,int hop,int cycle,UUID from,UUID to,UUID graph) {}
    private record Request(Key key,CargoShipmentRegistry.Shipment shipment,CargoRoutePlanner.Leg leg,CompoundTag job) {}
    private record Geometry(UUID train,UUID graph,String phase,UUID from,UUID to,UUID charterOrder) {}
    private static final class State {
        final PhaseCache<Key> phases=new PhaseCache<>();
        Map<Key,Request> requests=Map.of();
        final Map<Key,Boolean> permitted=new HashMap<>();
        final Map<Geometry,List<RailGeometry.Span>> geometry=new HashMap<>();
    }
    private static final Map<MinecraftServer,State> STATES=new WeakHashMap<>();
    public static boolean retire(CargoCommerceBook.State financial,boolean activeCharter){return financial!=null&&(financial==CargoCommerceBook.State.SETTLED||financial==CargoCommerceBook.State.REFUNDED||financial==CargoCommerceBook.State.CANCELLED)&&!activeCharter;}
    public static boolean covers(MinecraftServer s,Train train,RailGeometry.Span actual,RailTollBook tolls){
        if(train.manualTick||train.invalid||train.derailed||train.graph==null||PublicFleetRegistry.get(s).hasJob(train.id))return false;
        var state=STATES.computeIfAbsent(s,key->new State());long tick=s.overworld().getGameTime();
        state.phases.refresh(tick,()->snapshot(s,tolls,state));boolean covered=false;
        for(var key:state.phases.keys(train.id)){
            var request=state.requests.get(key);if(request==null||!key.graph().equals(train.graph.id))continue;
            boolean permitted=state.permitted.computeIfAbsent(key,k->permitted(s,train,request));if(!permitted)continue;
            var geometry=new Geometry(key.train(),key.graph(),key.phase(),key.from(),key.to(),key.phase().equals("REGULAR")?null:key.order());
            covered|=state.phases.consume(key,()->state.geometry.computeIfAbsent(geometry,k->{var spans=corridor(s,train,request,actual);return spans.size()+state.geometry.values().stream().mapToInt(List::size).sum()>MAX_SPANS?List.of():spans;}),actual);
        }
        return covered;
    }
    private static Map<UUID,List<Key>> snapshot(MinecraftServer s,RailTollBook tolls,State state){
        var commerce=com.example.examplemod.economy.EconomyManager.get(s.overworld()).cargo();var registry=CargoShipmentRegistry.get(s);var charters=CargoCharters.get(s);var jobs=charters.activeJobs();var activeOrders=new HashSet<UUID>();jobs.values().forEach(j->activeOrders.add(j.getUUID("Shipment")));var trains=RailwaySavedData.load(s).getTrains();var requests=new LinkedHashMap<Key,Request>();var index=new LinkedHashMap<UUID,List<Key>>();
        for(UUID id:tolls.legacyOrders()){
            var order=commerce.order(id).orElse(null);if(order==null)continue; // Unresolved half-save: no free passage and no presumed completion.
            if(retire(order.state(),activeOrders.contains(id))){tolls.retireLegacy(id);continue;}
            var shipment=registry.find(id).orElse(null);if(shipment==null||shipment.trip().getBoolean("Fleet"))continue;
            for(int hop=0;hop<shipment.route().size()&&requests.size()<MAX_LEASES;hop++){
                var leg=shipment.route().get(hop);var train=trains.get(leg.train());var line=CargoLineRegistry.get(s).find(leg.line()).orElse(null);if(train==null||train.graph==null||line==null||!line.enabled())continue;
                UUID from=null,to=null;for(int i=1;i<line.stops().size();i++)if(line.stops().get(i-1).point().equals(leg.from())&&line.stops().get(i).point().equals(leg.next())){from=line.stops().get(i-1).station();to=line.stops().get(i).station();break;}if(from==null)continue;
                String phase="REGULAR";int cycle=shipment.hopUnits(hop);CompoundTag job=null;
                if(line.mode()==CargoLineRegistry.Mode.CHARTER){
                    job=jobs.get(leg.train());if(job==null||!id.equals(job.getUUID("Shipment"))||job.getInt("Hop")!=hop)continue;phase=job.getString("Phase");if(!Set.of("PICKUP","DELIVERY","RETURN").contains(phase)||retire(order.state(),false)&&!phase.equals("RETURN"))continue;
                    if(phase.equals("PICKUP"))to=from;else if(phase.equals("RETURN")){from=to;to=job.getUUID("Home");}
                }else if(retire(order.state(),false)||shipment.boardedUnits(leg.train(),from,hop)<=cycle)continue;
                var key=new Key(id,leg.train(),phase,hop,phase.equals("REGULAR")?cycle:0,from,to,train.graph.id);requests.put(key,new Request(key,shipment,leg,job));index.computeIfAbsent(leg.train(),ignored->new ArrayList<>()).add(key);
            }
        }
        state.requests=Map.copyOf(requests);state.permitted.clear();var liveGeometry=new HashSet<Geometry>();for(var key:requests.keySet())liveGeometry.add(new Geometry(key.train(),key.graph(),key.phase(),key.from(),key.to(),key.phase().equals("REGULAR")?null:key.order()));state.geometry.keySet().retainAll(liveGeometry);return index;
    }
    private static boolean permitted(MinecraftServer s,Train train,Request request){
        if(request.job()==null)return !train.manualTick;
        var schedule=train.runtime.getSchedule();return schedule!=null&&request.job().contains("Schedule")&&CargoSchedulePlan.shape(schedule.write(s.registryAccess())).equals(request.job().getCompound("Schedule"))&&CargoCharters.get(s).assigned(s,request.shipment(),request.leg())&&train.navigation.destination!=null&&train.navigation.destination.id.equals(request.key().to());
    }
    private static List<RailGeometry.Span> corridor(MinecraftServer s,Train train,Request request,RailGeometry.Span firstActual){
        var key=request.key();try{var path=key.phase().equals("PICKUP")?RailRoutes.fromTrain(s,train,key.to(),request.shipment().owner(),"FASTEST",0,null).orElse(null):RailRoutes.between(s,key.from(),key.to(),train,request.shipment().owner(),"FASTEST",0).orElse(null);if(path==null)return List.of();var spans=new ArrayList<>(path.spans());if(key.phase().equals("PICKUP"))spans.add(firstActual);return List.copyOf(spans);}catch(IllegalArgumentException|NoSuchElementException unavailable){return List.of();}
    }
    /** Pure tick/phase cache. A repeated loop cannot repeatedly consume an old prepaid corridor. */
    public static final class PhaseCache<K> {
        private long tick=Long.MIN_VALUE;
        private Map<UUID,List<K>> index=Map.of();
        private Set<K> active=Set.of();
        private final Map<K,Coverage> frozen=new LinkedHashMap<>();
        private int spanCount;
        public void refresh(long now,Supplier<Map<UUID,List<K>>> rebuild){if(tick==now)return;var next=new LinkedHashMap<UUID,List<K>>();var live=new HashSet<K>();int count=0;for(var e:rebuild.get().entrySet()){var keys=new ArrayList<K>();for(var key:e.getValue()){if(count++>=MAX_LEASES)break;keys.add(key);live.add(key);}if(!keys.isEmpty())next.put(e.getKey(),List.copyOf(keys));}index=Map.copyOf(next);active=Set.copyOf(live);frozen.keySet().retainAll(active);spanCount=frozen.values().stream().mapToInt(Coverage::size).sum();tick=now;}
        public List<K> keys(UUID train){return index.getOrDefault(train,List.of());}
        public int frozenCount(){return frozen.size();}
        public boolean consume(K key,Supplier<List<RailGeometry.Span>> build,RailGeometry.Span actual){if(!active.contains(key))return false;var coverage=frozen.get(key);if(coverage==null){if(frozen.size()>=MAX_FROZEN)return false;var spans=build.get();if(spans.size()>4096||spans.size()+spanCount>MAX_SPANS)return false;coverage=new Coverage(spans);frozen.put(key,coverage);spanCount+=coverage.size();}int before=coverage.size();boolean result=coverage.consume(actual,MAX_SPANS-spanCount);spanCount+=coverage.size()-before;return result;}
    }
    private static final class Coverage {
        private final Map<String,List<RailGeometry.Span>> remaining=new HashMap<>();
        Coverage(List<RailGeometry.Span> spans){for(var span:spans)if(span.to()>span.from())remaining.computeIfAbsent(span.shape(),ignored->new ArrayList<>()).add(span);for(var entry:remaining.entrySet()){var sorted=entry.getValue();sorted.sort(Comparator.comparingDouble(RailGeometry.Span::from));var merged=new ArrayList<RailGeometry.Span>();for(var span:sorted){if(!merged.isEmpty()&&merged.getLast().to()>=span.from()){var old=merged.removeLast();span=new RailGeometry.Span(span.shape(),old.from(),Math.max(old.to(),span.to()));}merged.add(span);}entry.setValue(merged);}}
        int size(){return remaining.values().stream().mapToInt(List::size).sum();}
        boolean consume(RailGeometry.Span actual,int expansionAllowance){var intervals=remaining.get(actual.shape());if(intervals==null||actual.to()<=actual.from())return false;double epsilon=Math.min(1e-7,(actual.to()-actual.from())*1e-6),position=actual.from();for(var span:intervals){if(span.to()<=position)continue;if(span.from()>position+epsilon)break;position=Math.max(position,span.to());if(position>=actual.to()-epsilon)break;}if(position<actual.to()-epsilon)return false;var next=new ArrayList<RailGeometry.Span>();for(var span:intervals){if(span.overlap(actual)==0){next.add(span);continue;}if(span.from()<actual.from())next.add(new RailGeometry.Span(span.shape(),span.from(),actual.from()));if(span.to()>actual.to())next.add(new RailGeometry.Span(span.shape(),actual.to(),span.to()));}if(next.size()-intervals.size()>expansionAllowance)return false;remaining.put(actual.shape(),next);return true;}
    }
}
