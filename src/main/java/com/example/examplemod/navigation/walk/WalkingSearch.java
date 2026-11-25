package com.example.examplemod.navigation.walk;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import static com.example.examplemod.navigation.walk.WalkingRoutes.*;
import static com.example.examplemod.navigation.walk.WalkingSnapshot.Passage;

/** Incremental A* over cardinal walking steps and single-block climbs/descents. */
final class WalkingSearch {
    private static final int MAX_NODES = 2_048, MAX_PATH = MAX_HORIZONTAL + 128, DETOUR = 12;
    private record Node(BlockPos pos, Node parent, int cost, int remaining) {}
    private final PriorityQueue<Node> open = new PriorityQueue<>(Comparator
            .comparingInt((Node n) -> n.cost()+n.remaining()).thenComparingInt(Node::remaining));
    private final Map<BlockPos,Integer> costs = new HashMap<>();
    private final Leg leg;
    private Node best;
    private boolean unknown, goalUnknown, clipped;
    Result result;

    WalkingSearch(Leg leg) { this.leg = leg; }

    void step(WalkingSnapshot terrain) {
        if (best == null) {
            if (!leg.withinSearchRange()) {
                finish(Status.LIMIT); return;
            }
            var start = terrain.stand(leg.from());
            var end = terrain.stand(leg.to());
            if (start == Passage.UNKNOWN) { finish(Status.NO_DATA); return; }
            if (start == Passage.BLOCKED || end == Passage.BLOCKED) { finish(Status.UNREACHABLE); return; }
            if (end == Passage.UNKNOWN) {unknown = true;goalUnknown = true;}
            best = new Node(leg.from(),null,0,estimate(leg.from()));
            open.add(best); costs.put(best.pos(),0);
        }
        if (terrain.full || costs.size() >= MAX_NODES) { finish(Status.LIMIT); return; }
        var current = open.poll();
        if (current == null) {
            finish(unknown ? (best.parent()!=null ? Status.PARTIAL : Status.NO_DATA)
                    : clipped ? Status.LIMIT : Status.UNREACHABLE);
            return;
        }
        if (costs.get(current.pos()) != current.cost()) return;
        if (current.remaining() < best.remaining()) best = current;
        if (leg.to().equals(current.pos())) { best = current; finish(Status.FOUND); return; }
        for (var direction : WalkingSnapshot.HORIZONTAL) {
            var ahead = current.pos().relative(direction);
            if (ahead.getX() < Math.min(leg.from().getX(),leg.to().getX()) - DETOUR
                    || ahead.getX() > Math.max(leg.from().getX(),leg.to().getX()) + DETOUR
                    || ahead.getZ() < Math.min(leg.from().getZ(),leg.to().getZ()) - DETOUR
                    || ahead.getZ() > Math.max(leg.from().getZ(),leg.to().getZ()) + DETOUR) { clipped = true; continue; }
            boolean missingForward = false, canContinue = false;
            for (int dy : HEIGHTS) {
                var pos = dy == 0 ? ahead : ahead.above(dy);
                if (pos.getY() < Math.min(leg.from().getY(),leg.to().getY())-8
                        || pos.getY() > Math.max(leg.from().getY(),leg.to().getY())+8) { clipped=true; continue; }
                int next = current.cost() + 5 + heightCost(dy);
                if (costs.getOrDefault(pos,Integer.MAX_VALUE) <= next) { canContinue=true; continue; }
                var passage = terrain.cross(current.pos(),pos,direction);
                if (passage == Passage.UNKNOWN) {unknown = true;missingForward = true;}
                if (passage != Passage.OPEN) continue;
                canContinue = true;
                costs.put(pos,next);
                open.add(new Node(pos,current,next,estimate(pos)));
            }
            if (goalUnknown && missingForward && !canContinue && current.parent()!=null
                    && horizontal(ahead,leg.to()) < horizontal(current.pos(),leg.to())
                    && current.remaining()<estimate(leg.from())) {
                best=current;finish(Status.PARTIAL);return;
            }
        }
    }

    // Estimated normal walking time in ticks. No sprint, potion bonuses or waiting for other entities.
    private static final int[] HEIGHTS = {0,1,-1};
    static int heightCost(int dy) { return dy > 0 ? 5 : dy < 0 ? 2 : 0; }
    private int estimate(BlockPos pos) {
        return 5 * horizontal(pos,leg.to()) + heightCost(Integer.compare(leg.to().getY(),pos.getY()))
                * Math.abs(pos.getY()-leg.to().getY());
    }
    private static int horizontal(BlockPos a, BlockPos b) {
        return Math.abs(a.getX()-b.getX()) + Math.abs(a.getZ()-b.getZ());
    }
    private void finish(Status status) {
        if (status != Status.FOUND && status != Status.PARTIAL) { result=Result.empty(status); return; }
        List<BlockPos> path = new ArrayList<>();
        for (var n=best;n!=null;n=n.parent()) {
            if (path.size() >= MAX_PATH) { result=Result.empty(Status.LIMIT); return; }
            path.add(n.pos());
        }
        Collections.reverse(path);
        result = new Result(status,path,status==Status.FOUND?best.cost()/20.0:Double.NaN);
    }
}
