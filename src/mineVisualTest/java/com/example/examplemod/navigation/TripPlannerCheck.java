package com.example.examplemod.navigation;

import com.example.examplemod.navigation.walk.WalkingRoutes.*;
import com.example.examplemod.passenger.PassengerService.Ride;
import com.example.examplemod.passenger.PassengerTimetableCheck;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

public final class TripPlannerCheck {
    private static final ResourceLocation DIM=ResourceLocation.parse("minecraft:overworld");
    private static BlockPos p(int x){return new BlockPos(x,65,0);}
    private static UUID id(int n){return new UUID(0,n);}
    private static Ride ride(int train,int from,int to,long departure,long arrival) {
        return new Ride(id(train),id(99),id(train),id(from),id(to),"Line","Train","Platform","Platform",DIM,p(from),p(to),departure,arrival,id(100),10,11);
    }
    private static void known(Map<Leg,Result> paths,int from,int to,double seconds) {
        var nodes=new ArrayList<BlockPos>();
        for(int x=from;;x+=Integer.compare(to,from)){nodes.add(p(x));if(x==to)break;}
        paths.put(new Leg(p(from),p(to)),new Result(Status.FOUND,nodes,seconds));
    }
    private static void require(boolean x,String message){if(!x)throw new AssertionError(message);}
    public static void main(String[] args) {
        PassengerTimetableCheck.run();
        var late=ride(11,4,150,15,145);
        var first=ride(12,8,160,120,300);
        var connector=ride(13,164,300,410,560);
        var last=ride(14,300,500,635,835);
        var later=ride(15,8,500,1500,1600);
        var candidates=List.of(late,first,connector,last,later);
        var survey=TripPlanner.survey(List.of(p(0)),List.of(p(504)),candidates,TripPlanner.Mode.MIXED);
        var walks=new HashMap<Leg,Result>();
        known(walks,0,4,1);known(walks,0,8,2);known(walks,164,160,1);
        known(walks,500,504,1);
        var options=TripPlanner.solve(survey,walks,TripPlanner.Mode.MIXED,0);
        require(options.size()==1 && options.getFirst().rides()==1,"near platforms require confirmed directional walking");
        known(walks,160,164,2);
        options=TripPlanner.solve(survey,walks,TripPlanner.Mode.MIXED,0);
        require(options.getFirst().rides()==3 && options.getFirst().arrival()==855,"two physical connections with boarding, waiting and final approach");
        require(!options.getFirst().canBegin(0,ride->new com.example.examplemod.passenger.PassengerTimetable.Connection(
                0,1,ride.departTick(),ride.arriveTick()+(ride==first?80:0),ride.fromVisit(),ride.toVisit())),
                "confirmed visits with a slower first arrival cannot silently retain an impossible transfer");
        require(TripPlanner.solve(survey,walks,TripPlanner.Mode.FEWER,0).getFirst().rides()==1,"transfer preference keeps the slower direct service");
        walks.put(new Leg(p(160),p(164)),new Result(Status.PARTIAL,List.of(p(160)),Double.NaN));
        require(TripPlanner.solve(survey,walks,TripPlanner.Mode.MIXED,0).size()==1,"unknown foot time cannot catch a connection");
        var miss=TripPlanner.survey(List.of(p(0)),List.of(p(150)),List.of(late),TripPlanner.Mode.MIXED);
        known(walks,150,150,0);
        require(TripPlanner.solve(miss,walks,TripPlanner.Mode.MIXED,0).isEmpty(),"insufficient time to board");
        var direct=TripPlanner.survey(List.of(p(0)),List.of(p(4)),candidates,TripPlanner.Mode.WALK);
        require(direct.rides().isEmpty() && TripPlanner.solve(direct,walks,TripPlanner.Mode.WALK,0).getFirst().rides()==0,"walking alone uses verified geometry");
        require(TripPlanner.survey(List.of(p(0)),List.of(p(110)),List.of(),TripPlanner.Mode.WALK).limited(),
                "an entrance outside the walking search is a budget limit");
        var feet=new MockObservation();
        var follow=new TripProgress(options.getFirst());
        for(int x=0;x<=8;x++){feet.feet=p(x);follow.observe(feet,x*5L);}
        require(follow.phase().equals("wait_run"),"first actual approach before waiting for the selected visit");
        feet.number=12;
        feet.feet=p(8);follow.observe(feet,300);
        require(follow.phase().equals("missed"),"missed departure preserves reason for recalculation");
        feet.number=10;
        var shortPlan=new TripPlanner.Plan(List.of(new TripPlanner.Step(List.of(),first,first.departTick(),first.arriveTick())),300,1);
        var closedBoarding=new TripProgress(shortPlan);
        feet.train=null;feet.station=first.from();
        feet.access=com.example.examplemod.passenger.PassengerService.Boarding.BLOCKED;
        closedBoarding.observe(feet,58);
        require(closedBoarding.phase().equals("blocked") && !closedBoarding.boarded,
                "an operating train at the booked visit cannot invite boarding through a newly closed carriage wall");
        feet.access=com.example.examplemod.passenger.PassengerService.Boarding.READY;
        var priorCycle=new TripProgress(shortPlan);
        feet.number=8;feet.train=first.train();feet.station=first.from();priorCycle.observe(feet,5);
        require(priorCycle.replan.equals("wrong_run"),"the same consist at the same platform one cycle early cannot board the selected run");
        var wrongTrain=new TripProgress(shortPlan);
        feet.number=10;feet.train=id(55);wrongTrain.observe(feet,60);
        require(wrongTrain.replan.equals("wrong_train"),"a different physical train at the right visit is rejected");
        var earlyExit=new TripProgress(shortPlan);
        feet.train=first.train();earlyExit.observe(feet,60);
        feet.train=null;feet.station=id(161);feet.feet=first.toEntrance();earlyExit.observe(feet,120);
        require(earlyExit.replan.equals("wrong_exit"),"dismount at a different native stop cannot finish, even at the destination coordinates");
        var missedExit=new TripProgress(shortPlan);
        feet.train=first.train();feet.station=first.from();missedExit.observe(feet,60);
        feet.number=13;feet.station=first.to();missedExit.observe(feet,700);
        require(missedExit.replan.equals("wrong_exit"),"same destination on a later circuit cannot be accepted as the chosen arrival");
        feet.number=10;feet.train=null;feet.station=null;
        var route=new TripProgress(options.getFirst());
        feet.unavailable=id(13);
        require(!route.remainingProblem(feet).isEmpty(),"withdrawal of a later connection invalidates guidance before first boarding");
        feet.unavailable=null;
        var quickBoard=new TripProgress(options.getFirst());
        for(int x=0;x<8;x++){feet.feet=p(x);quickBoard.observe(feet,x*5L);}
        feet.feet=p(8);feet.train=id(12);feet.station=id(8);quickBoard.observe(feet,40);
        require(quickBoard.phase().equals("ride"),"boarding during the last approach tick uses the observed train and station");
        feet.noForecast=id(12);feet.station=null;
        quickBoard.observe(feet,150);
        require(quickBoard.phase().equals("ride") && quickBoard.remainingProblem(feet).isEmpty(),"a late estimate cannot cancel a physical ride in service");
        feet.unavailable=id(12);quickBoard.observe(feet,155);
        require(quickBoard.phase().equals("cancelled"),"a withdrawn service ends guidance even aboard");
        feet.noForecast=null;feet.unavailable=null;
        feet.train=null;feet.station=null;
        for(int x=0;x<=8;x++){feet.feet=p(x);route.observe(feet,x*5L);}
        feet.train=id(12);feet.station=id(8);route.observe(feet,60);
        require(route.phase().equals("ride"),"boarding requires actual train at correct station");
        feet.station=id(160);feet.number=11;route.observe(feet,300);require(route.phase().equals("exit"),"stop event marks exit");
        feet.feet=p(160).above();feet.train=null;route.observe(feet,301);
        require(route.phase().equals("exit_walk"),"native release at the correct call still requires reaching the real platform height");
        feet.feet=p(161);route.observe(feet,302);require(route.phase().equals("exit_walk"),"neighboring entrance must not complete a transfer");
        feet.feet=p(160);feet.train=null;route.observe(feet,305);require(route.phase().equals("walk"),"actual dismount advances transfer");
        System.out.println("PASS verified walking, two transfers, timetable feasibility, passenger observations and missed departures");
    }
    private static final class MockObservation implements TripProgress.Observation {
        BlockPos feet;UUID train,station,unavailable,noForecast;long number=9;
        com.example.examplemod.passenger.PassengerService.Boarding access=com.example.examplemod.passenger.PassengerService.Boarding.READY;
        public com.example.examplemod.passenger.PassengerService.Boarding boarding(Ride ride){return access;}
        public BlockPos feet(){return feet;} public UUID vehicle(){return train;}
        public com.example.examplemod.passenger.PassengerService.Visit visit(Ride ride){return new com.example.examplemod.passenger.PassengerService.Visit(number,station);}
        public boolean mountedAt(Ride r){return r.from().equals(station) && number==r.fromVisit();}
        public boolean leftAt(Ride r){return r.to().equals(station) && number==r.toVisit();}
        public boolean valid(Ride ride,boolean aboard){return !ride.train().equals(unavailable) && (aboard || !ride.train().equals(noForecast));}
        public boolean standing(BlockPos p){return true;} public boolean passable(BlockPos a,BlockPos b){return true;}
    }
}
