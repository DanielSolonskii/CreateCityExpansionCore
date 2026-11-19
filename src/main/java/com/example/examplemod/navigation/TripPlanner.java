package com.example.examplemod.navigation;

import com.example.examplemod.navigation.walk.WalkingRoutes;
import com.example.examplemod.navigation.walk.WalkingRoutes.Leg;
import com.example.examplemod.navigation.walk.WalkingRoutes.Result;
import com.example.examplemod.passenger.PassengerService.Ride;
import com.example.examplemod.passenger.PassengerTimetable.Connection;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Time-dependent connection search. Only a confirmed footpath can connect independent platforms. */
public final class TripPlanner {
    public enum Mode { WALK, MIXED, FEWER }
    public static final int BOARD_TICKS = 60;
    private static final int WALK_RANGE = 96;
    public record Step(List<BlockPos> path, Ride ride, long start, long end) {
        public boolean walking() { return ride == null; }
    }
    public record Plan(List<Step> steps, long arrival, int rides) {
        boolean canBegin(long now,java.util.function.Function<Ride,Connection> forecast) {
            long at=now;
            for(var step:steps) {
                if(step.walking())at+=step.end()-step.start();
                else {
                    var latest=forecast.apply(step.ride());
                    if(latest==null || at+BOARD_TICKS>latest.departure())return false;
                    at=latest.arrival();
                }
            }
            return true;
        }
        Plan add(Step step) {
            var next = new ArrayList<>(steps);
            next.add(step);
            return new Plan(List.copyOf(next), step.end(), rides + (step.walking() ? 0 : 1));
        }
    }
    public record Survey(List<BlockPos> starts, List<BlockPos> goals, List<Ride> rides, List<Leg> legs, boolean limited) {}
    private static double distance(Leg leg) { return leg.from().distSqr(leg.to()); }

    public static Survey survey(List<BlockPos> origins, List<BlockPos> entrances, List<Ride> services, Mode mode) {
        var starts = origins.stream().distinct().limit(4).toList();
        var goals = entrances.stream().distinct().limit(4).toList();
        var rides = mode == Mode.WALK ? List.<Ride>of() : services.stream().limit(720).toList();
        var stops = new LinkedHashSet<BlockPos>();
        for (Ride r : rides) { stops.add(r.fromEntrance()); stops.add(r.toEntrance()); }
        var pairs = new LinkedHashSet<Leg>();
        boolean limited = starts.size() < origins.size() || goals.size() < entrances.size() ||
                mode != Mode.WALK && rides.size() < services.size();
        limited |= addPairs(pairs, starts, goals, rides.isEmpty() ? 16 : 6, false);
        limited |= addPairs(pairs, starts, stops, 8, false);
        limited |= addPairs(pairs, stops, goals, 8, false);
        limited |= addPairs(pairs, stops, stops, 32 - pairs.size(), true);
        return new Survey(starts, goals, rides, List.copyOf(pairs), limited);
    }

    private static boolean addPairs(Set<Leg> pairs, Collection<BlockPos> from, Collection<BlockPos> to,
                                    int limit, boolean transfers) {
        if (from.isEmpty() || to.isEmpty()) return false;
        var candidates = new ArrayList<Leg>();
        boolean outside = false;
        for (var a : from) for (var b : to) {
            if (transfers && a.equals(b)) continue;
            var leg = new Leg(a,b);
            if (pairs.contains(leg)) continue;
            if (distance(leg) <= WALK_RANGE * WALK_RANGE) candidates.add(leg);
            else outside = true;
        }
        candidates.sort(Comparator.comparingDouble(TripPlanner::distance).thenComparing(l -> l.from().asLong()).thenComparing(l -> l.to().asLong()));
        for (int i = 0; i < Math.min(limit, candidates.size()); i++) pairs.add(candidates.get(i));
        return outside || candidates.size() > limit;
    }

    private record Arrival(Plan plan, UUID station, UUID train) {}

    public static List<Plan> solve(Survey survey, Map<Leg,Result> walks, Mode mode, long now) {
        Plan empty = new Plan(List.of(), now, 0);
        Plan direct = null;
        for (var start : survey.starts()) for (var goal : survey.goals()) {
            Plan option = walk(empty, walks.get(new Leg(start, goal)));
            if (option != null && (direct == null || direct.arrival() > option.arrival())) direct = option;
        }
        Map<BlockPos, List<Arrival>> previous = Map.of();
        Map<BlockPos, List<Map.Entry<Leg,Result>>> transfers = new HashMap<>();
        for (var entry : walks.entrySet()) if (entry.getValue().status() == WalkingRoutes.Status.FOUND)
            transfers.computeIfAbsent(entry.getKey().to(), k -> new ArrayList<>()).add(entry);
        List<Plan> options = new ArrayList<>();
        if (direct != null) options.add(direct);
        // One boarding plus at most two transfers. At a platform an earlier arrival dominates a later one.
        for (int round = 1; round <= 3 && !survey.rides().isEmpty(); round++) {
            Map<BlockPos, List<Arrival>> next = new HashMap<>();
            for (Ride ride : survey.rides()) {
                if (ride.departTick() < now + BOARD_TICKS || ride.arriveTick() <= ride.departTick()) continue;
                Plan ready = null;
                if (round == 1) {
                    for (var start : survey.starts()) ready = earlier(ready,walk(empty,walks.get(new Leg(start,ride.fromEntrance()))));
                } else {
                    for (Arrival prior : previous.getOrDefault(ride.fromEntrance(), List.of()))
                        if (prior.station().equals(ride.from()) && !prior.train().equals(ride.train()))
                            ready = earlier(ready, prior.plan());
                    for (var entry : transfers.getOrDefault(ride.fromEntrance(), List.of()))
                        for (Arrival prior : previous.getOrDefault(entry.getKey().from(), List.of()))
                            if (!prior.train().equals(ride.train())) ready = earlier(ready,walk(prior.plan(),entry.getValue()));
                }
                if (ready == null || ready.arrival() + BOARD_TICKS > ride.departTick()) continue;
                var arrivals = next.computeIfAbsent(ride.toEntrance(), k -> new ArrayList<>());
                // Retain distinct service identities to avoid treating a through train as a transfer.
                int existing = 0;
                while (existing < arrivals.size() && !(arrivals.get(existing).station().equals(ride.to())
                        && arrivals.get(existing).train().equals(ride.train()))) existing++;
                if (existing < arrivals.size() && arrivals.get(existing).plan().arrival() <= ride.arriveTick()) continue;
                var arrival = new Arrival(ready.add(new Step(List.of(),ride,ride.departTick(),ride.arriveTick())),ride.to(),ride.train());
                if (existing < arrivals.size()) arrivals.set(existing,arrival); else arrivals.add(arrival);
            }
            Plan best = null;
            for (var entry : next.entrySet()) for (Arrival arrival : entry.getValue())
                for (var goal : survey.goals()) best = earlier(best, walk(arrival.plan(),walks.get(new Leg(entry.getKey(),goal))));
            if (best != null) options.add(best);
            if (next.isEmpty()) break;
            previous = next;
        }
        Comparator<Plan> order = Comparator.comparingLong(Plan::arrival);
        if (mode == Mode.FEWER) order = Comparator.comparingInt((Plan p) -> Math.max(0,p.rides()-1)).thenComparing(order);
        options.sort(order.thenComparingInt(Plan::rides));
        return List.copyOf(options);
    }

    private static Plan earlier(Plan a, Plan b) {
        return b != null && (a == null || b.arrival() < a.arrival()) ? b : a;
    }
    private static Plan walk(Plan base, Result foot) {
        if (foot == null || foot.status() != WalkingRoutes.Status.FOUND || foot.path().isEmpty()
                || !Double.isFinite(foot.seconds()) || foot.seconds() < 0) return null;
        long arrival = base.arrival() + (long)Math.ceil(foot.seconds() * 20);
        return base.add(new Step(foot.path(),null,base.arrival(),arrival));
    }
    private TripPlanner() {}
}
