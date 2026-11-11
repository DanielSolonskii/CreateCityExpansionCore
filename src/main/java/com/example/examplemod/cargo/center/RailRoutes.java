package com.example.examplemod.cargo.center;

import com.example.examplemod.mixin.CarriageBogeyAccessor;
import com.simibubi.create.content.trains.RailwaySavedData;
import com.simibubi.create.content.trains.entity.*;
import com.simibubi.create.content.trains.graph.*;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.track.TrackMaterial.TrackType;
import net.createmod.catnip.data.Couple;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Weighted directed-edge search, including station offsets, native restrictions and actual switch instructions. */
public final class RailRoutes {
    private RailRoutes() {}
    public record Node(String dimension,double x,double y,double z){
        static Node of(TrackNode n){var p=n.getLocation().getLocation();return new Node(n.getLocation().dimension.location().toString(),p.x,p.y,p.z);}
        TrackNode resolve(TrackGraph g){return g.locateNode(new TrackNodeLocation(x,y,z).in(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(dimension))));}
        CompoundTag save(){var t=new CompoundTag();t.putString("Dimension",dimension);t.putDouble("X",x);t.putDouble("Y",y);t.putDouble("Z",z);return t;}
        static Node load(CompoundTag t){return new Node(t.getString("Dimension"),t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"));}
    }
    public record Segment(Node a,Node b,double from,double to,RailGeometry.Span span){
        public Segment {if(from<0||to<from||!Double.isFinite(from)||!Double.isFinite(to))throw new IllegalArgumentException("rail_path_invalid");}
        public CompoundTag save(){var t=new CompoundTag();t.put("A",a.save());t.put("B",b.save());t.putDouble("From",from);t.putDouble("To",to);t.put("Span",span.save());return t;}
        static Segment load(CompoundTag t){return new Segment(Node.load(t.getCompound("A")),Node.load(t.getCompound("B")),t.getDouble("From"),t.getDouble("To"),RailGeometry.Span.load(t.getCompound("Span")));}
    }
    public record Path(UUID destination,boolean backwards,List<Segment> segments,double distance){
        public Path {segments=List.copyOf(segments);if(segments.size()>4096||distance<0||!Double.isFinite(distance))throw new IllegalArgumentException("rail_path_invalid");}
        public List<RailGeometry.Span> spans(){return segments.stream().map(Segment::span).toList();}
        public CompoundTag save(){var t=new CompoundTag();t.putUUID("Destination",destination);t.putBoolean("Backwards",backwards);t.putDouble("Distance",distance);var list=new ListTag();segments.forEach(x->list.add(x.save()));t.put("Segments",list);return t;}
        public static Path load(CompoundTag t){var list=new ArrayList<Segment>();for(var raw:t.getList("Segments",10))list.add(Segment.load((CompoundTag)raw));return new Path(t.getUUID("Destination"),t.getBoolean("Backwards"),list,t.getDouble("Distance"));}
        public UUID fingerprint(){return UUID.nameUUIDFromBytes(save().toString().getBytes(StandardCharsets.UTF_8));}
    }
    private record Start(TrackNode a,TrackNode b,double offset,boolean backwards){}
    private record State(TrackNode a,TrackNode b){}
    private record Candidate(State state,double offset,List<Segment> path,double distance,double cost,boolean backwards){}
    /** Quote-only wheel positions; no real Train, navigation, inventory or financial state is mutated. */
    public static final class Pose {
        private final List<TravellingPoint> wheels;
        private Pose(List<TravellingPoint> points){wheels=new ArrayList<>();for(int i=0;i<points.size();i++){var p=points.get(i);if(p==null||p.edge==null||p.node1==null||p.node2==null)throw new IllegalArgumentException("fleet_pose_unavailable");wheels.add(i==0?new MeterPoint(p):copy(p));}if(wheels.isEmpty()||wheels.size()>256)throw new IllegalArgumentException("fleet_pose_unavailable");}
        public TravellingPoint front(){return copy(wheels.getFirst());}
        public TravellingPoint rear(){return copy(wheels.getLast());}
    }
    public record Journey(Path path,Path meter,Pose arrival){}
    private static TravellingPoint copy(TravellingPoint point){return new TravellingPoint(point.node1,point.node2,point.edge,point.position,point.upsideDown);}
    public static Pose pose(Train train){var points=new ArrayList<TravellingPoint>();for(var carriage:train.carriages){var first=carriage.leadingBogey();var last=carriage.trailingBogey();if(first!=null){points.add(first.leading());points.add(first.trailing());}if(last!=null&&last!=first){points.add(last.leading());points.add(last.trailing());}}return new Pose(points);}
    static Pose pose(List<TravellingPoint> points){return new Pose(points);}
    private static final class MeterPoint extends TravellingPoint {
        private final List<Segment> travelled=new ArrayList<>();
        MeterPoint(TravellingPoint source){super(source.node1,source.node2,source.edge,source.position,source.upsideDown);}
        @Override protected Double edgeTraversedFrom(TrackGraph graph,boolean forward,IEdgePointListener points,ITurnListener turns,double previous,double distance){
            Double blocked=super.edgeTraversedFrom(graph,forward,points,turns,previous,distance);double length=edge.getLength(),from=Math.clamp(previous,0,length),to=Math.clamp(blocked==null?position:blocked,0,length);
            if(Math.abs(from-to)>1e-8){var a=Node.of(forward?node1:node2);var b=Node.of(forward?node2:node1);var span=RailGeometry.span(graph,edge,from,to);var next=new Segment(a,b,forward?from:length-from,forward?to:length-to,span);if(!travelled.isEmpty()){var last=travelled.getLast();if(last.a().equals(a)&&last.b().equals(b)&&Math.abs(last.to()-next.from())<.00001){travelled.set(travelled.size()-1,new Segment(a,b,last.from(),next.to(),new RailGeometry.Span(span.shape(),Math.min(last.span().from(),span.from()),Math.max(last.span().to(),span.to()))));return blocked;}}travelled.add(next);}
            return blocked;
        }
    }
    /** Every subsequent phase starts from the prior phase's actual simulated orientation and body position. */
    public static Optional<Journey> journey(MinecraftServer s,Train train,Pose pose,UUID destination,OwnerRef payer,String preference,long carrierRate,int[] work){
        var target=CargoRailConnections.station(s,destination);if(target==null||train.graph!=target.graph())return Optional.empty();return journeyGraph(train.graph,pose,target.station(),train,train.hasForwardConductor(),train.doubleEnded&&train.hasBackwardConductor(),com.example.examplemod.economy.EconomyManager.get(s.overworld()).roadTolls(),payer,preference,carrierRate,work);
    }
    static Optional<Journey> journeyGraph(TrackGraph graph,Pose pose,GlobalStation target,Train train,boolean forwardAllowed,boolean backwardAllowed,RailTollBook book,OwnerRef payer,String preference,long carrierRate,int[] work){
        var starts=new ArrayList<Start>();var front=pose.wheels.getFirst();var rear=pose.wheels.getLast();if(forwardAllowed)starts.add(new Start(front.node1,front.node2,front.position,false));if(backwardAllowed){var reverse=graph.getConnectionsFrom(rear.node2).get(rear.node1);if(reverse!=null)starts.add(new Start(rear.node2,rear.node1,reverse.getLength()-rear.position,true));}
        Journey best=null;double bestCost=Double.POSITIVE_INFINITY;for(var start:starts){var path=search(graph,List.of(start),target,train,payer,preference,carrierRate,book,null).orElse(null);if(path==null)continue;var moved=advance(graph,pose,path,work).orElse(null);if(moved==null)continue;if(preference.equals("NO_TOLLS")&&moved.meter().spans().stream().flatMap(span->book.charges(span).stream()).anyMatch(c->c.rate()>0&&!c.creditor().equals(payer)))continue;double cost=preference.equals("FASTEST")?path.distance():book.estimate(moved.meter().spans(),payer)+path.distance()*carrierRate/1000.0;if(best==null||cost<bestCost||cost==bestCost&&path.distance()<best.path().distance()){best=moved;bestCost=cost;}}return Optional.ofNullable(best);
    }
    private static Optional<Journey> advance(TrackGraph graph,Pose source,Path path,int[] work){
        var result=new Pose(source.wheels);boolean backwards=path.backwards();int leading=backwards?result.wheels.size()-1:0,step=backwards?-1:1;var lead=result.wheels.get(leading);var switches=new ArrayDeque<>(switches(graph,path));
        TravellingPoint.ITrackSelector select=(g,choices)->{if(switches.isEmpty())throw new IllegalArgumentException("fleet_route_changed");var expected=switches.removeFirst();var junction=choices.getFirst()?lead.node2:lead.node1;if(expected.getFirst()!=junction)throw new IllegalArgumentException("fleet_route_changed");return choices.getSecond().stream().filter(e->e.getKey()==expected.getSecond()).findFirst().orElseThrow(()->new IllegalArgumentException("fleet_route_changed"));};
        double remaining=path.distance();while(remaining>1e-7){double distance=Math.min(1,remaining)*(backwards?-1:1);TravellingPoint ahead=null;for(int i=leading;i>=0&&i<result.wheels.size();i+=step){if(work[0]--<=0)throw new IllegalArgumentException("fleet_route_too_complex");var wheel=result.wheels.get(i);double actual=wheel.travel(graph,distance,ahead==null?select:wheel.follow(ahead));if(wheel.blocked||Math.abs(actual-distance)>.025)return Optional.empty();ahead=wheel;}remaining-=Math.abs(distance);}
        if(!path.segments().isEmpty()){var last=path.segments().getLast();var a=last.a().resolve(graph);var b=last.b().resolve(graph);var edge=graph.getConnectionsFrom(a).get(b);if(edge==null||lead.edge.getPosition(graph,lead.position/lead.edge.getLength()).distanceTo(edge.getPosition(graph,last.to()/edge.getLength()))>.1)return Optional.empty();}
        var captured=((MeterPoint)result.wheels.getFirst()).travelled;double distance=captured.stream().mapToDouble(p->p.span().to()-p.span().from()).sum();if(Math.abs(distance-path.distance())>.15)return Optional.empty();return Optional.of(new Journey(path,new Path(path.destination(),backwards,captured,distance),result));
    }
    public static Optional<Path> between(MinecraftServer s,UUID from,UUID to,Train train,OwnerRef payer,String preference,long carrierRate){
        if(from.equals(to))return Optional.of(new Path(to,false,List.of(),0));var source=CargoRailConnections.station(s,from);var target=CargoRailConnections.station(s,to);
        if(source==null||target==null||source.graph()!=target.graph()||source.station().assembling||target.station().assembling)return Optional.empty();var g=source.graph();
        var a=g.locateNode(source.station().edgeLocation.getFirst());var b=g.locateNode(source.station().edgeLocation.getSecond());if(a==null||b==null)return Optional.empty();
        var starts=new ArrayList<Start>();var ab=g.getConnectionsFrom(a).get(b);var ba=g.getConnectionsFrom(b).get(a);
        if(ab!=null)starts.add(new Start(a,b,source.station().getLocationOn(ab),false));if(ba!=null)starts.add(new Start(b,a,source.station().getLocationOn(ba),true));
        return search(s,g,starts,target.station(),train,payer,preference,carrierRate,null);
    }
    public static Optional<Path> fromTrain(MinecraftServer s,Train train,UUID to,OwnerRef payer,String preference,long carrierRate,Path corridor){
        var target=CargoRailConnections.station(s,to);if(train==null||train.graph==null||target==null||train.graph!=target.graph()||train.carriages.isEmpty())return Optional.empty();
        if(train.getCurrentStation()!=null&&train.getCurrentStation().id.equals(to))return Optional.of(new Path(to,false,List.of(),0));
        var starts=new ArrayList<Start>();var front=train.carriages.getFirst().getLeadingPoint();
        if(train.hasForwardConductor()&&front.edge!=null&&(corridor==null||!corridor.backwards()))starts.add(new Start(front.node1,front.node2,front.position,false));
        var back=train.carriages.getLast().getTrailingPoint();if(train.doubleEnded&&train.hasBackwardConductor()&&back.edge!=null&&(corridor==null||corridor.backwards()))starts.add(new Start(back.node2,back.node1,back.edge.getLength()-back.position,true));
        return search(s,train.graph,starts,target.station(),train,payer,preference,carrierRate,corridor);
    }
    private static Set<TrackType> types(Train train){if(train==null)return null;Set<TrackType> valid=null;
        for(var carriage:train.carriages)for(var bogey:new CarriageBogey[]{carriage.leadingBogey(),carriage.trailingBogey()}){if(bogey==null)continue;var found=((CarriageBogeyAccessor)bogey).ccec$getType().getValidPathfindingTypes(bogey.getStyle());if(valid==null)valid=new HashSet<>(found);else valid.retainAll(found);}return valid==null?Set.of():valid;
    }
    private static Optional<Path> search(MinecraftServer s,TrackGraph graph,List<Start> starts,GlobalStation target,Train train,OwnerRef payer,String preference,long carrierRate,Path corridor){
        return search(graph,starts,target,train,payer,preference,carrierRate,com.example.examplemod.economy.EconomyManager.get(s.overworld()).roadTolls(),corridor);
    }
    static Optional<Path> planGraph(TrackGraph graph,TrackNode a,TrackNode b,double offset,GlobalStation target,RailTollBook book,OwnerRef payer,String preference){return search(graph,List.of(new Start(a,b,offset,false)),target,null,payer,preference,0,book,null);}
    private static Optional<Path> search(TrackGraph graph,List<Start> starts,GlobalStation target,Train train,OwnerRef payer,String preference,long carrierRate,RailTollBook book,Path corridor){
        var valid=types(train);var q=new PriorityQueue<Candidate>(Comparator.comparingDouble(Candidate::cost).thenComparingDouble(Candidate::distance));var best=new HashMap<State,Double>();Path winner=null;double winningCost=Double.POSITIVE_INFINITY;
        for(var start:starts)q.add(new Candidate(new State(start.a(),start.b()),start.offset(),List.of(),0,0,start.backwards()));int scanned=0;
        while(!q.isEmpty()&&scanned++<20000){var c=q.remove();if(c.cost()>winningCost)break;var state=c.state();var edge=graph.getConnectionsFrom(state.a()).get(state.b());if(edge==null||valid!=null&&!valid.contains(edge.getTrackMaterial().trackType))continue;
            if(best.getOrDefault(state,Double.POSITIVE_INFINITY)<c.cost())continue;double finish=edge.getLength();boolean reached=false;
            if(target.edgeLocation.either(loc->loc.equals(state.a().getLocation()))&&target.edgeLocation.either(loc->loc.equals(state.b().getLocation()))&&target.canApproachFrom(state.b())){
                double position=target.getLocationOn(edge);if(position>=c.offset()-1e-6&&position<=finish+1e-6){finish=position;reached=true;}}
            boolean forbidden=false;for(var point:edge.getEdgeData().getPoints()){double p=point.getLocationOn(edge);if(p>=c.offset()-1e-6&&p<=finish+1e-6&&!point.canNavigateVia(state.b())){forbidden=true;break;}}if(forbidden)continue;
            var span=RailGeometry.span(graph,edge,c.offset(),finish);if(corridor!=null&&!corridor.segments().isEmpty()&&corridor.segments().stream().noneMatch(x->x.a().equals(Node.of(state.a()))&&x.b().equals(Node.of(state.b()))&&x.span().overlap(span)>=span.to()-span.from()-0.05))continue;
            var charges=book.charges(span);if(preference.equals("NO_TOLLS")&&charges.stream().anyMatch(x->x.rate()>0&&!x.creditor().equals(payer)))continue;
            double length=span.to()-span.from();double step=preference.equals("FASTEST")?length:length*carrierRate/1000.0;
            if(!preference.equals("FASTEST"))for(var charge:charges)if(!charge.creditor().equals(payer))step+=(charge.span().to()-charge.span().from())*charge.rate()/1000.0;
            // Even zero-price routes prefer shorter travel and cannot improve by cycling.
            step+=length*1e-9;var path=new ArrayList<>(c.path());if(finish>c.offset()+1e-8)path.add(new Segment(Node.of(state.a()),Node.of(state.b()),c.offset(),finish,span));double distance=c.distance()+length,cost=c.cost()+step;
            if(reached){if(cost<winningCost){winningCost=cost;winner=new Path(target.id,c.backwards(),path,distance);}continue;}
            for(var next:graph.getConnectionsFrom(state.b()).entrySet()){if(next.getKey()==state.a()||!edge.canTravelTo(next.getValue()))continue;var key=new State(state.b(),next.getKey());if(cost>=best.getOrDefault(key,Double.POSITIVE_INFINITY))continue;best.put(key,cost);q.add(new Candidate(key,0,path,distance,cost,c.backwards()));}
        }return Optional.ofNullable(winner);
    }
    public static boolean valid(MinecraftServer s,Path path){var station=CargoRailConnections.station(s,path.destination());if(station==null)return false;for(var segment:path.segments()){
        var a=segment.a().resolve(station.graph());var b=segment.b().resolve(station.graph());if(a==null||b==null)return false;var e=station.graph().getConnectionsFrom(a).get(b);if(e==null||segment.to()>e.getLength()+.01||!RailGeometry.span(station.graph(),e,segment.from(),segment.to()).equals(segment.span()))return false;}return true;}
    public static DiscoveredPath nativePath(MinecraftServer s,Train train,Path approved){
        var actual=fromTrain(s,train,approved.destination(),OwnerRef.player(train.owner),"FASTEST",0,approved).orElse(null);if(actual==null)return null;
        return nativeInstructions(train.graph,actual,CargoRailConnections.station(s,approved.destination()).station());
    }
    static DiscoveredPath nativeInstructions(TrackGraph graph,Path actual,GlobalStation target){
        return new DiscoveredPath(actual.backwards()?-actual.distance():actual.distance(),actual.distance(),switches(graph,actual),target);
    }
    private static List<Couple<TrackNode>> switches(TrackGraph graph,Path actual){
        var switches=new ArrayList<Couple<TrackNode>>();TrackEdge prior=null;TrackNode previous=null;
        for(var segment:actual.segments()){var a=segment.a().resolve(graph);var b=segment.b().resolve(graph);var edge=graph.getConnectionsFrom(a).get(b);
            if(prior!=null){int options=0;for(var choice:graph.getConnectionsFrom(a).entrySet())if(choice.getKey()!=previous&&prior.canTravelTo(choice.getValue()))options++;if(options>1)switches.add(Couple.create(a,b));}prior=edge;previous=a;}
        return switches;
    }
    public static ListTag geometry(MinecraftServer s,Path path,String phase){var result=new ListTag();var station=CargoRailConnections.station(s,path.destination());if(station==null)return result;var graph=station.graph();var book=com.example.examplemod.economy.EconomyManager.get(s.overworld()).roadTolls();
        for(var segment:path.segments()){var a=segment.a().resolve(graph);var b=segment.b().resolve(graph);if(a==null||b==null||!a.getLocation().dimension.equals(b.getLocation().dimension))continue;var edge=graph.getConnectionsFrom(a).get(b);if(edge==null||edge.getLength()<=0)continue;
            int samples=Math.max(1,Math.min(64,(int)Math.ceil((segment.to()-segment.from())/4)));for(int i=0;i<samples&&result.size()<2048;i++){double from=segment.from()+(segment.to()-segment.from())*i/samples,to=segment.from()+(segment.to()-segment.from())*(i+1)/samples;var p=edge.getPosition(graph,from/edge.getLength());var n=edge.getPosition(graph,to/edge.getLength());var t=new CompoundTag();t.putString("Dimension",a.getLocation().dimension.location().toString());t.putDouble("X",p.x);t.putDouble("Z",p.z);t.putDouble("ToX",n.x);t.putDouble("ToZ",n.z);t.putBoolean("Focus",true);t.putString("Phase",phase);t.putBoolean("Toll",!book.charges(RailGeometry.span(graph,edge,from,to)).isEmpty());result.add(t);}}
        return result;
    }
}
