package com.example.examplemod.cargo.center;

import com.example.examplemod.cargo.CargoTrainRegistry;
import com.example.examplemod.economy.EconomyManager;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import java.util.*;

/** Observes actual carriage movement, never navigation scouts or all bogeys. No position polling/delta inference. */
public final class RailTravelObserver {
    private RailTravelObserver(){}
    private record Scope(MinecraftServer server,Train train,Targets<TravellingPoint> targets){}
    /** Exact physical object identity, shared by native scopes and headless sensor checks. */
    public record Targets<T>(T meter,T proof){
        public boolean meters(T observed){return observed!=null&&observed==meter;}
        public boolean proves(T observed){return observed!=null&&observed==proof;}
        public boolean observes(T observed){return meters(observed)||proves(observed);}
        public Targets<T> inScope(boolean primaryCarriage,Collection<T> realPoints){boolean hasMeter=false,hasProof=false;for(T point:realPoints){hasMeter|=meters(point);hasProof|=proves(point);}return new Targets<>(primaryCarriage&&hasMeter?meter:null,hasProof?proof:null);}
    }
    private static final ThreadLocal<Scope> ACTIVE=new ThreadLocal<>();
    private static final UUID SESSION=UUID.randomUUID();
    private static long sequence;
    private record Motion(long tick,boolean translating){}
    private static final Map<Train,Motion> MOTION=new WeakHashMap<>();
    public static void begin(Carriage carriage,Level level){
        ACTIVE.remove();var server=level.getServer();var train=carriage.train;if(server==null||!server.isSameThread()||train==null||train.carriages.isEmpty()||train.invalid||train.derailed)return;
        long tick=server.overworld().getGameTime();var old=MOTION.get(train);boolean translating=train.speed!=0||old!=null&&old.tick()==tick&&old.translating();MOTION.put(train,new Motion(tick,translating));if(!translating)return;
        var first=train.carriages.getFirst();var meter=first.leadingBogey()==null?null:first.leadingBogey().leading();var proof=PublicFleetRegistry.get(server).proofHead(train);var points=new ArrayList<TravellingPoint>();
        for(var bogey:new CarriageBogey[]{carriage.leadingBogey(),carriage.trailingBogey()})if(bogey!=null){points.add(bogey.leading());points.add(bogey.trailing());}
        var targets=new Targets<>(meter,proof).inScope(carriage==first,points);if(targets.meter()==null&&targets.proof()==null)return;ACTIVE.set(new Scope(server,train,targets));
    }
    public static void end(){ACTIVE.remove();}
    public static void moved(TravellingPoint point,TrackGraph graph,double from,Double blocked){
        var scope=ACTIVE.get();if(scope==null||!scope.server().isSameThread()||!scope.targets().observes(point)||point.edge==null||point.node1==null||point.node2==null||graph!=scope.train().graph)return;
        double to=blocked==null?point.position:blocked,length=point.edge.getLength();from=Math.clamp(from,0,length);to=Math.clamp(to,0,length);if(Math.abs(to-from)<1e-8)return;
        var s=scope.server();var train=scope.train();var span=RailGeometry.span(graph,point.edge,from,to);var fleet=PublicFleetRegistry.get(s);
        if(scope.targets().proves(point))fleet.observeHead(s,train,RailRoutes.Node.of(point.node1),RailRoutes.Node.of(point.node2),from,to,span);
        if(!scope.targets().meters(point))return; // A proof-only head never enters the money ledger.
        var book=EconomyManager.get(s.overworld()).roadTolls();UUID stream=RailTollBook.component(SESSION,train.id.toString());long movement=++sequence;UUID receipt=RailTollBook.component(stream,Long.toString(movement));if(book.metered(stream,movement))return;
        OwnerRef payer=train.owner==null?null:OwnerRef.player(train.owner);var registration=CargoTrainRegistry.find(s,train.id).orElse(null);if(registration!=null&&registration.ownerId().equals(train.owner))payer=CargoOwners.get(s).owner("train/"+train.id,registration.ownerId());if(payer!=null)book.resolveTechnical(train.id,payer);
        if(fleet.bill(s,train,receipt,RailRoutes.Node.of(point.node1),RailRoutes.Node.of(point.node2),from,to,span)){book.completeMeter(stream,movement,receipt);return;}
        if(!book.legacyCaptured())book.captureLegacy(EconomyManager.get(s.overworld()).cargo().orders());
        var current=book.charges(span);if(current.isEmpty()){book.passage(train.id,List.of());return;}
        if(legacyPassage(s,train,span,book))return;
        var frozen=book.passage(train.id,current);if(frozen.isEmpty())return;
        if(payer==null){book.meterUnowned(stream,movement,train.id,train.name.getString(),frozen);return;}
        book.meterTravel(stream,movement,payer,frozen);
    }
    private static boolean legacyPassage(MinecraftServer s,Train train,RailGeometry.Span actual,RailTollBook book){return LegacyRailLeases.covers(s,train,actual,book);}
}
