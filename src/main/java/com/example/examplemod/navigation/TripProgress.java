package com.example.examplemod.navigation;

import com.example.examplemod.navigation.TripPlanner.Plan;
import com.example.examplemod.navigation.TripPlanner.Step;
import com.example.examplemod.passenger.PassengerService.Ride;
import com.example.examplemod.passenger.PassengerService.Visit;
import com.example.examplemod.passenger.PassengerService.Boarding;
import net.minecraft.core.BlockPos;
import java.util.UUID;

/** Each transition consumes an observation of this player, on the server thread. */
public final class TripProgress {
    public interface Observation {
        BlockPos feet();
        UUID vehicle();
        Visit visit(Ride ride);
        boolean mountedAt(Ride ride);
        boolean leftAt(Ride ride);
        Boarding boarding(Ride ride);
        boolean valid(Ride ride, boolean aboard);
        boolean standing(BlockPos feet);
        boolean passable(BlockPos from, BlockPos to);
    }
    private final Plan plan;
    int stage, node;
    boolean boarded, ready, sawExit, dismounted, finished;
    String replan = "";
    public TripProgress(Plan plan) { this.plan=plan; }
    public Step step() { return plan.steps().get(Math.min(stage,plan.steps().size()-1)); }
    String remainingProblem(Observation actor) {
        for(int i=stage;i<plan.steps().size();i++) {
            var step=plan.steps().get(i);
            if(step.walking())continue;
            var ride=step.ride();
            if(i==stage && !boarded && ready && actor.vehicle()==null) {
                var problem=boardingProblem(actor.boarding(ride));
                if(!problem.isEmpty())return problem;
                continue;
            }
            if(!actor.valid(ride,i==stage && boarded)){
                var visit=actor.visit(ride);
                if(!(i==stage && boarded) && (visit.number()>ride.fromVisit() ||
                        visit.number()==ride.fromVisit() && !ride.from().equals(visit.station())))return "missed";
                return "cancelled";
            }
        }
        return "";
    }
    private static String boardingProblem(Boarding state) {
        return switch(state){case READY->"";case BLOCKED->"blocked";case UNKNOWN->"no_data";case CANCELLED->"cancelled";};
    }
    private void advance() {
        node=0; boarded=false; ready=false; sawExit=false; dismounted=false;
        if (++stage >= plan.steps().size()) finished=true;
    }
    public String phase() {
        if(!replan.isEmpty())return replan;
        if(finished)return "done";
        if(step().walking())return "walk";
        if(!boarded)return ready?"board":"wait_run";
        if(dismounted)return "exit_walk";
        return sawExit?"exit":"ride";
    }
    public BlockPos waypoint() {
        var step=step();
        return step.walking()?step.path().get(Math.min(node,step.path().size()-1)):
                boarded?step.ride().toEntrance():step.ride().fromEntrance();
    }
    public void observe(Observation actor,long now) {
        if (finished || !replan.isEmpty()) return;
        Step step=step();
        if (step.walking()) {
            var path=step.path();
            var feet=actor.feet();
            if (actor.vehicle()!=null) {
                if(stage+1<plan.steps().size() && node>=path.size()-2) {
                    var ride=plan.steps().get(stage+1).ride();
                    if(ride!=null && actor.vehicle().equals(ride.train())) {
                        if(!actor.mountedAt(ride)){replan="wrong_run";return;}
                        if(!actor.valid(ride,true)){replan="cancelled";return;}
                        advance();boarded=true;return;
                    }
                }
                replan="wrong_train";return;
            }
            int reached=-1;
            for(int i=node;i<Math.min(path.size(),node+5);i++)
                if (feet.equals(path.get(i))) reached=i;
            if(reached>=0 && actor.standing(feet)) node=reached+1;
            if(node==path.size()){advance();return;}
            var next=path.get(node);
            if(!actor.standing(next) || node>0 && !actor.passable(path.get(node-1),next)){replan="blocked";return;}
            if(feet.distSqr(next)>36)replan="deviated";
            return;
        }
        var ride=step.ride();
        UUID aboard=actor.vehicle();
        var visit=actor.visit(ride);
        ready=visit.number()==ride.fromVisit() && ride.from().equals(visit.station());
        boolean atExit=visit.number()==ride.toVisit() && ride.to().equals(visit.station());
        if(!boarded && aboard==null && (visit.number()>ride.fromVisit() || visit.number()==ride.fromVisit() && !ready)){
            replan="missed";return;
        }
        if(!boarded && ready && aboard==null){
            replan=boardingProblem(actor.boarding(ride));
            if(!replan.isEmpty())return;
        }else if(!actor.valid(ride,boarded || ride.train().equals(aboard) && actor.mountedAt(ride))){replan="cancelled";return;}
        if(!boarded){
            if(aboard!=null){
                if(!ride.train().equals(aboard)){replan="wrong_train";return;}
                if(!actor.mountedAt(ride)){replan="wrong_run";return;}
                boarded=true;
            }else if(actor.feet().distSqr(ride.fromEntrance())>64)replan="deviated";
        }else{
            if(aboard==null){
                if(!dismounted && !actor.leftAt(ride)){replan="wrong_exit";return;}
                dismounted=true;
                if(!actor.standing(ride.toEntrance()))replan="blocked";
                else if(actor.feet().equals(ride.toEntrance()))advance();
                else if(actor.feet().distSqr(ride.toEntrance())>64)replan="wrong_exit";
            }else if(!ride.train().equals(aboard) || dismounted || visit.number()>ride.toVisit()
                    || visit.number()==ride.toVisit() && !atExit)replan="wrong_exit";
            else if(atExit)sawExit=true;
        }
    }

    Ride nextRide() {
        for(int i=stage;i<plan.steps().size();i++)
            if(!plan.steps().get(i).walking())return plan.steps().get(i).ride();
        return null;
    }
}
