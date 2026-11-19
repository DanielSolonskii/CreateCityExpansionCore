package com.example.examplemod.navigation;

import com.example.examplemod.city.CityMapSavedData;
import com.example.examplemod.navigation.TripNetwork.*;
import com.example.examplemod.navigation.TripPlanner.*;
import com.example.examplemod.navigation.walk.WalkingRoutes;
import com.example.examplemod.passenger.PassengerService;
import com.example.examplemod.places.AtlasPlace;
import com.example.examplemod.places.PlaceService;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid="createcityexpansioncore")
public final class TripServer {
    private record Entry(UUID id,long version,List<BlockPos> feet) {}
    private record Destination(String title,List<Entry> entries) {
        List<BlockPos> points() { return entries.stream().flatMap(e->e.feet.stream()).distinct().limit(5).toList(); }
    }
    private static final class Offer {
        final Query query;
        Destination origin, goal;
        final long created;
        boolean pending=true;
        int starting=-1;
        List<Plan> options=List.of();
        Offer(Query query, Destination origin, Destination goal,long created) {
            this.query=query;this.origin=origin;this.goal=goal;this.created=created;
        }
    }
    private static final class Session {
        long lastPlan=-100,lastPoll=-100,lastPreviewPoll=-100,lastBegin=-100,renewed,lastSend,checked;
        long advanced=Long.MIN_VALUE;
        Offer preview, active;
        int option;
        PassengerService.Ride mounted, left;
        TripProgress guide;
        java.util.function.BiConsumer<ServerPlayer,Reply> deliver;
    }
    private static final LinkedHashMap<ServerPlayer,Session> sessions=new LinkedHashMap<>();
    private static int admissionTick, plansThisTick, startsThisTick;
    private static final int MAX_SESSIONS=256, MAX_PLANS_PER_TICK=4, MAX_SESSIONS_PER_TICK=48;
    private static final ThreadPoolExecutor search=new ThreadPoolExecutor(0,1,10,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(12),r->{var t=new Thread(r,"CCEC passenger itinerary");t.setDaemon(true);return t;});

    private static Destination resolve(ServerPlayer p,TripRef key) {
        if(key.kind()==TripRef.Kind.PLAYER)return new Destination(p.getName().getString(),List.of(new Entry(p.getUUID(),0,List.of(p.blockPosition()))));
        List<AtlasPlace> list;
        if(key.kind()==TripRef.Kind.PLACE){
            var place=PlaceService.resolve(p,key.id());
            list=place==null?List.of():List.of(place);
        }else list=PlaceService.entrancesFor(p,key.source(),key.id());
        var visible=list.stream().filter(a->a.dimension().equals(p.serverLevel().dimension().location()) && !a.entrances().isEmpty())
                .sorted(Comparator.comparing(AtlasPlace::id)).limit(4).toList();
        if(!visible.isEmpty())return new Destination(visible.getFirst().name(),visible.stream().map(a->new Entry(a.id(),a.version(),a.entrances())).toList());
        if(key.kind()==TripRef.Kind.STATION) {
            var platform=PassengerService.stop(p,key.id());
            if(platform!=null && platform.dimension().equals(p.serverLevel().dimension().location()))
                return station(platform.id(),platform.name(),platform.entrance());
        }
        return null;
    }
    private static Destination station(UUID id,String name,BlockPos entrance) {
        return new Destination(name,List.of(new Entry(id,0,List.of(entrance))));
    }
    private static long now(ServerPlayer p){return p.serverLevel().getServer().overworld().getGameTime();}
    private static boolean world(ServerPlayer p,Query q){return q.world().equals(CityMapSavedData.get(p.serverLevel()).worldId());}
    private static CompoundTag reply(Query q,String state) {
        var out=new CompoundTag();out.putUUID("Request",q.request());out.putUUID("World",q.world());out.putString("State",state);return out;
    }
    private static void send(ServerPlayer p,CompoundTag tag){
        var session=sessions.get(p);
        if(session!=null)session.deliver.accept(p,new Reply(tag));
    }
    private static void status(ServerPlayer p, Query q,String state){send(p,reply(q,state));}
    private static boolean admit(ServerPlayer p,boolean begin) {
        int tick=p.getServer().getTickCount();
        if(admissionTick!=tick){admissionTick=tick;plansThisTick=0;startsThisTick=0;}
        if(begin){if(startsThisTick>=MAX_PLANS_PER_TICK)return false;startsThisTick++;}
        else {if(plansThisTick>=MAX_PLANS_PER_TICK)return false;plansThisTick++;}
        return true;
    }

    static void handle(ServerPlayer p,Query q) {
        handle(p,q,PacketDistributor::sendToPlayer);
    }
    static void handle(ServerPlayer p,Query q,java.util.function.BiConsumer<ServerPlayer,Reply> deliver) {
        if(!p.isAlive() || p.isRemoved())return;
        if(!sessions.containsKey(p) && sessions.size()>=MAX_SESSIONS){deliver.accept(p,new Reply(reply(q,"limit")));return;}
        var s=sessions.computeIfAbsent(p,k->new Session());s.deliver=deliver;long t=now(p);
        if(q.action()==Action.CANCEL){
            if(s.preview!=null && q.request().equals(s.preview.query.request())){s.preview=null;WalkingRoutes.cancel(p);}
            if(s.active!=null && q.request().equals(s.active.query.request())){s.active=null;s.guide=null;s.mounted=null;s.left=null;}
            return;
        }
        if(q.action()==Action.POLL){
            if(s.preview!=null && s.preview.query.equals(q.action(Action.PLAN,0))){
                if(t-s.lastPreviewPoll<10)return;
                s.lastPreviewPoll=t;
                if(t-s.preview.created>600 || !current(p,s.preview) || s.preview.options.stream().anyMatch(option->!permitted(p,option))){s.preview=null;status(p,q,"expired");}
                else status(p,q,"valid");
                return;
            }
            if(t-s.lastPoll<10)return;
            s.lastPoll=t;
            if(s.active==null || !s.active.query.equals(q.action(Action.PLAN,0))) { status(p,q,"ended");return; }
            s.renewed=t;return;
        }
        if(q.action()==Action.BEGIN){
            if(t-s.lastBegin<5)return;
            s.lastBegin=t;
            if(s.active!=null && s.active.query.equals(q.action(Action.PLAN,0)) && q.option()==s.option){
                s.renewed=t;return;
            }
            var offer=s.preview;
            if(offer==null || !offer.query.equals(q.action(Action.PLAN,0)) || q.option()>=offer.options.size()
                    || t-offer.created>600 || !current(p,offer)) {status(p,q,"expired");return;}
            if(offer.starting==q.option())return;
            if(!admit(p,true)){status(p,q,"limit");return;}
            var plan=offer.options.get(q.option());
            if(!ready(p,plan,t)) {
                status(p,q,"start_changed");return;
            }
            offer.starting=q.option();
            WalkingRoutes.validate(p,plan.steps().stream().filter(Step::walking).map(Step::path).toList(),valid->{
                if(sessions.get(p)!=s || s.preview!=offer || offer.starting!=q.option())return;
                offer.starting=-1;
                long begin=now(p);
                if(!valid || begin-offer.created>600 || !current(p,offer) || !ready(p,plan,begin)){
                    status(p,q,"start_changed");return;
                }
                s.guide=new TripProgress(plan);s.active=offer;s.preview=null;s.renewed=begin;s.checked=begin;
                s.option=q.option();s.mounted=null;s.left=null;
                publish(p,s,begin);
            });
            return;
        }
        if(t-s.lastPlan<15)return;
        if(s.preview!=null && s.preview.query.equals(q) && t-s.preview.created<=600 && s.preview.pending)return;
        if(!admit(p,false)){s.lastPlan=t;status(p,q,"limit");return;}
        s.lastPlan=t;s.preview=null;WalkingRoutes.cancel(p);
        if(!world(p,q)||q.goal().kind()==TripRef.Kind.PLAYER || q.option()!=0){status(p,q,"entrance");return;}
        var origin=resolve(p,q.start());var goal=resolve(p,q.goal());
        if(origin==null || goal==null){status(p,q,"entrance");return;}
        var offer=new Offer(q,origin,goal,t);s.preview=offer;
        var services=q.mode()==Mode.WALK?List.<PassengerService.Ride>of():PassengerService.departures(p).stream()
                .filter(r->r.dimension().equals(p.serverLevel().dimension().location())).toList();
        var starts=origin.points();var goals=goal.points();
        var server=p.getServer();
        try {
            CompletableFuture.supplyAsync(()->TripPlanner.survey(starts,goals,services,q.mode()),search)
                    .whenComplete((survey,error)->server.execute(()->{
                        if(sessions.get(p)!=s || s.preview!=offer)return;
                        if(error!=null || survey.legs().isEmpty()){offer.pending=false;status(p,q,"limit");return;}
                        if(!current(p,offer)){s.preview=null;status(p,q,"entrance");return;}
                        findWalks(p,s,offer,survey);
                    }));
        } catch(RejectedExecutionException busy){offer.pending=false;status(p,q,"limit");}
    }
    private static void findWalks(ServerPlayer p,Session s,Offer offer,Survey survey) {
        var q=offer.query;
        WalkingRoutes.find(p,survey.legs(),results->{
            if(sessions.get(p)!=s || s.preview!=offer)return;
            if(results.size()!=survey.legs().size()){offer.pending=false;status(p,q,"limit");return;}
            var walks=new HashMap<WalkingRoutes.Leg,WalkingRoutes.Result>();
            for(int i=0;i<results.size();i++)walks.put(survey.legs().get(i),results.get(i));
            long departure=now(p);
            var server=p.serverLevel().getServer();
            try {
                CompletableFuture.supplyAsync(()->TripPlanner.solve(survey,walks,q.mode(),departure),search)
                        .whenComplete((options,error)->server.execute(()->{
                            if(sessions.get(p)!=s || s.preview!=offer)return;
                            offer.pending=false;
                            if(!current(p,offer)){status(p,q,"entrance");return;}
                            if(error!=null){status(p,q,"limit");return;}
                            offer.options=options.stream().filter(option->permitted(p,option)).toList();
                            var packet=reply(q,offer.options.isEmpty()?failure(q.mode(),survey,walks):"options");
                            boolean incomplete=walks.values().stream().anyMatch(w->w.status()!=WalkingRoutes.Status.FOUND &&
                                    w.status()!=WalkingRoutes.Status.UNREACHABLE);
                            packet.putBoolean("Limited",survey.limited() || incomplete || options.size()!=offer.options.size());
                            packet.putString("Goal",offer.goal.title());
                            packet.putString("Origin",offer.origin.title());packet.putLong("Now",departure);
                            if(offer.options.isEmpty()){
                                var partial=walks.entrySet().stream().filter(e->survey.starts().contains(e.getKey().from())
                                        && survey.goals().contains(e.getKey().to()) && e.getValue().status()==WalkingRoutes.Status.PARTIAL)
                                        .map(e->e.getValue().path()).filter(path->path.size()>1)
                                        .max(Comparator.comparingInt(List::size));
                                partial.ifPresent(path->{packet.putString("State","partial");packet.putLong("KnownTo",path.getLast().asLong());});
                            }
                            var list=new ListTag();
                            for(var plan:offer.options)list.add(describe(plan));packet.put("Options",list);
                            send(p,packet);
                        }));
            } catch(RejectedExecutionException busy){offer.pending=false;status(p,q,"limit");}
        });
    }

    private static String failure(Mode mode,Survey survey,Map<WalkingRoutes.Leg,WalkingRoutes.Result> walks) {
        if(survey.limited())return "limit";
        for(var state:List.of(WalkingRoutes.Status.LIMIT,WalkingRoutes.Status.PARTIAL,WalkingRoutes.Status.NO_DATA))
            if(walks.values().stream().anyMatch(r->r.status()==state))return state.name().toLowerCase(Locale.ROOT);
        boolean allDirect=survey.starts().stream().allMatch(a->survey.goals().stream().allMatch(b->walks.containsKey(new WalkingRoutes.Leg(a,b))));
        if(!allDirect)return "limit";
        return mode==Mode.WALK?"unreachable":"no_connection";
    }
    private static CompoundTag describe(Plan plan) {
        var data=new CompoundTag();data.putLong("Arrival",plan.arrival());data.putInt("Rides",plan.rides());
        var rows=new ListTag();
        for(var step:plan.steps()){
            var row=new CompoundTag();row.putLong("Start",step.start());row.putLong("End",step.end());
            if(step.walking()){row.putLong("To",step.path().getLast().asLong());}
            else {var ride=step.ride();row.putString("Train",ride.trainName());row.putString("From",ride.fromName());row.putString("Stop",ride.toName());row.putString("Line",ride.lineName());}
            rows.add(row);
        }
        data.put("Steps",rows);return data;
    }
    private static boolean current(ServerPlayer p,Offer offer) {
        if(!world(p,offer.query))return false;
        var goal=resolve(p,offer.query.goal());
        var origin=offer.query.start().kind()==TripRef.Kind.PLAYER?offer.origin:resolve(p,offer.query.start());
        if(!sameEntrances(goal,offer.goal) || !sameEntrances(origin,offer.origin))return false;
        offer.goal=goal;offer.origin=origin;return true;
    }
    private static boolean sameEntrances(Destination live,Destination old) {
        if(live==null || live.entries().size()!=old.entries().size())return false;
        for(int i=0;i<old.entries().size();i++){
            var a=live.entries().get(i);var b=old.entries().get(i);
            if(!a.id().equals(b.id()) || !a.feet().equals(b.feet()))return false;
        }
        return true;
    }
    private static BlockPos lastFeet(Plan plan){return plan.steps().getLast().path().getLast();}
    private static boolean ready(ServerPlayer p,Plan plan,long now) {
        var first=plan.steps().getFirst().path().getFirst();
        return first.equals(p.blockPosition()) && WalkingRoutes.currentlyWalkable(p,first)
                && !WalkingRoutes.confirmedBlocked(p,lastFeet(plan)) && plan.canBegin(now,ride->PassengerService.forecast(p,ride));
    }
    private static boolean permitted(ServerPlayer actor,Plan plan) {
        return plan.steps().stream().allMatch(s->s.walking() || PassengerService.accessible(actor,s.ride()));
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        int count=Math.min(MAX_SESSIONS_PER_TICK,sessions.size());
        long until=System.nanoTime()+2_000_000;
        for(int i=0;i<count && (i==0 || System.nanoTime()<until);i++){
            var pair=sessions.pollFirstEntry();
            if(pair==null)break;
            var p=pair.getKey();
            if(p.getServer()!=event.getServer() || p.isRemoved() || !p.isAlive()){WalkingRoutes.cancel(p);continue;}
            sessions.put(p,pair.getValue());
            advance(p);
        }
    }
    @SubscribeEvent public static void mount(EntityMountEvent e) {
        if(!(e.getEntityMounting() instanceof ServerPlayer p) || !(e.getEntityBeingMounted() instanceof CarriageContraptionEntity car))return;
        var s=sessions.get(p);
        if(s==null || s.guide==null || !s.guide.replan.isEmpty())return;
        var ride=s.guide.nextRide();
        if(e.isMounting()){s.mounted=null;s.left=null;} else s.left=null;
        if(ride==null || !ride.train().equals(car.trainId))return;
        var visit=PassengerService.visit(p.getServer(),ride);
        if(e.isMounting() && visit.number()==ride.fromVisit() && ride.from().equals(visit.station()) &&
                p.blockPosition().equals(ride.fromEntrance()))s.mounted=ride;
        if(e.isDismounting() && visit.number()==ride.toVisit() && ride.to().equals(visit.station()))s.left=ride;
    }
    static void advance(ServerPlayer p) {
        var s=sessions.get(p);if(s==null)return;
        long t=now(p);
        if(s.preview!=null && t-s.preview.created>600){s.preview=null;WalkingRoutes.cancel(p);}
        if(s.active==null){if(s.preview==null && t-s.lastPlan>600)sessions.remove(p);return;}
        if(t-s.renewed>120 || !world(p,s.active.query)){stop(p,s);return;}
        if(s.advanced!=Long.MIN_VALUE && t-s.advanced<4)return;
        s.advanced=t;
        boolean checkRemaining=t-s.checked>=20;
        if(checkRemaining){
            s.checked=t;
            var live=resolve(p,s.active.query.goal());
            if(live==null){stop(p,s);return;}
            if(!current(p,s.active)){s.active.goal=live;s.guide.replan="changed";}
            if(s.guide.replan.isEmpty() && WalkingRoutes.confirmedBlocked(p,lastFeet(s.active.options.get(s.option))))s.guide.replan="blocked";
        }
        String before=s.guide.phase();int stage=s.guide.stage, node=s.guide.node;
        var observation=new TripProgress.Observation(){
            public BlockPos feet(){return p.blockPosition();}
            public UUID vehicle(){return PassengerService.riding(p);}
            public PassengerService.Visit visit(PassengerService.Ride ride){return PassengerService.visit(p.getServer(),ride);}
            public boolean mountedAt(PassengerService.Ride ride){return ride.equals(s.mounted);}
            public boolean leftAt(PassengerService.Ride ride){return ride.equals(s.left);}
            public PassengerService.Boarding boarding(PassengerService.Ride ride){return PassengerService.boarding(p,ride);}
            public boolean valid(PassengerService.Ride ride,boolean aboard){
                return aboard ? PassengerService.operating(p,ride) : PassengerService.accessible(p,ride);
            }
            public boolean standing(BlockPos feet){return WalkingRoutes.currentlyWalkable(p,feet);}
            public boolean passable(BlockPos from,BlockPos to){return WalkingRoutes.currentlyPassable(p,from,to);}
        };
        s.guide.observe(observation,t);
        if(s.guide.finished && !current(p,s.active)){
            var live=resolve(p,s.active.query.goal());
            if(live==null){stop(p,s);return;}
            s.active.goal=live;s.guide.finished=false;s.guide.stage--;s.guide.replan="changed";
        }
        if(checkRemaining && !s.guide.finished && s.guide.replan.isEmpty())
            s.guide.replan=s.guide.remainingProblem(observation);
        if(t-s.lastSend>=30 || stage!=s.guide.stage || node!=s.guide.node || !before.equals(s.guide.phase()))publish(p,s,t);
    }
    private static void stop(ServerPlayer p,Session s){status(p,s.active.query,"ended");s.active=null;s.guide=null;}
    private static void publish(ServerPlayer p,Session s,long t){
        var guide=s.guide;var packet=reply(s.active.query,guide.finished?"done":"guide");
        if(!guide.finished){
            var destination=resolve(p,s.active.query.goal());
            if(destination==null){stop(p,s);return;}
            if(!sameEntrances(destination,s.active.goal))guide.replan="changed";
            s.active.goal=destination;
            packet.putString("Goal",s.active.goal.title());
            String phase=guide.phase();
            String legacyPhase=switch(phase){
                case "wait_run" -> "board";
                case "exit_walk" -> "walk";
                case "wrong_run" -> "wrong_train";
                case "no_data" -> "cancelled";
                default -> phase;
            };
            packet.putString("Phase",legacyPhase);
            if(!phase.equals(legacyPhase))packet.putString("PhaseDetail",phase);
            packet.putString("Dim",p.serverLevel().dimension().location().toString());
            packet.putInt("Stage",guide.stage+1);
            if(guide.replan.isEmpty())packet.putLong("Pos",guide.waypoint().asLong());
            else {
                packet.putLong("Pos",destination.points().getFirst().asLong());
            }
            packet.putBoolean("Replan",!guide.replan.isEmpty());
            var step=guide.step();
            if(!step.walking() && guide.replan.isEmpty()){var ride=step.ride();packet.putString("Train",ride.trainName());packet.putString("From",ride.fromName());packet.putString("Stop",ride.toName());}
            else if(guide.replan.isEmpty()) {
                packet.putLongArray("Ahead",step.path().stream().skip(guide.node).limit(48).mapToLong(BlockPos::asLong).toArray());
            }
        }
        send(p,packet);s.lastSend=t;
        if(guide.finished){s.active=null;s.guide=null;}
    }
    private static void discard(ServerPlayer p){sessions.remove(p);WalkingRoutes.cancel(p);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)discard(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)discard(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p)discard(p);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){sessions.clear();search.getQueue().clear();}
    private TripServer(){}
}
