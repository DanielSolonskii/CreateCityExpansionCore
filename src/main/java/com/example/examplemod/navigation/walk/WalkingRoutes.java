package com.example.examplemod.navigation.walk;

import com.example.examplemod.CreateCityExpansionCore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Server-side walking legs for already authorized endpoints in the player's dimension. */
@EventBusSubscriber(modid = CreateCityExpansionCore.MODID)
public final class WalkingRoutes {
    public enum Status { FOUND, PARTIAL, NO_DATA, LIMIT, UNREACHABLE }
    public record Leg(BlockPos from, BlockPos to) {
        public Leg { from=from.immutable(); to=to.immutable(); }
    }
    public record Result(Status status, List<BlockPos> path, double seconds) {
        public Result { path=List.copyOf(path); }
        static Result empty(Status status) { return new Result(status,List.of(),Double.NaN); }
    }
    public static final int MAX_LEGS = 32;
    private static final int MAX_BATCHES = 24, LIFETIME_TICKS = 160;
    private static final LinkedHashMap<UUID,Batch> batches = new LinkedHashMap<>();
    private WalkingRoutes() {}

    /** Replaces the prior request for this player. Completion and all world sampling run on the server thread. */
    public static void find(ServerPlayer actor, List<Leg> legs, Consumer<List<Result>> onServerThread) {
        if (!actor.serverLevel().getServer().isSameThread()) throw new IllegalStateException("Server thread required");
        cancel(actor);
        if (legs.isEmpty() || legs.size() > MAX_LEGS || batches.size() >= MAX_BATCHES) {
            onServerThread.accept(Collections.nCopies(legs.size(),Result.empty(Status.LIMIT))); return;
        }
        batches.put(actor.getUUID(),new Batch(actor,List.copyOf(legs),onServerThread));
    }
    /** Recheck only the chosen route against current collisions, sharing the ordinary server tick budget. */
    public static void validate(ServerPlayer actor,List<List<BlockPos>> paths,Consumer<Boolean> done) {
        if(!actor.getServer().isSameThread())throw new IllegalStateException("Server thread required");
        cancel(actor);
        if(paths.isEmpty() || paths.size()>MAX_LEGS || batches.size()>=MAX_BATCHES){done.accept(false);return;}
        var batch=new Batch(actor,List.of(),results->done.accept(results.stream().allMatch(r->r.status()==Status.FOUND)));
        for(var path:paths){
            if(path.isEmpty()){done.accept(false);return;}
            batch.results.add(new Result(Status.FOUND,path,Double.NaN));
        }
        batches.put(actor.getUUID(),batch);
    }
    public static void cancel(ServerPlayer actor) {
        var old = batches.get(actor.getUUID());
        if (old != null && old.actor == actor) batches.remove(actor.getUUID());
    }
    public static boolean currentlyWalkable(ServerPlayer actor, BlockPos feet) {
        return new WalkingSnapshot(actor).stand(feet) == WalkingSnapshot.Passage.OPEN;
    }
    public static boolean confirmedBlocked(ServerPlayer actor, BlockPos feet) {
        return new WalkingSnapshot(actor).stand(feet) == WalkingSnapshot.Passage.BLOCKED;
    }
    public static boolean currentlyPassable(ServerPlayer actor, BlockPos from, BlockPos to) {
        return passable(new WalkingSnapshot(actor),from,to);
    }
    private static boolean passable(WalkingSnapshot snapshot, BlockPos from, BlockPos to) {
        int dx=to.getX()-from.getX(), dz=to.getZ()-from.getZ();
        if (Math.abs(dx)+Math.abs(dz)!=1 || Math.abs(to.getY()-from.getY())>1) return false;
        Direction d=dx!=0?(dx>0?Direction.EAST:Direction.WEST):(dz>0?Direction.SOUTH:Direction.NORTH);
        return snapshot.stand(from)==WalkingSnapshot.Passage.OPEN
                && snapshot.cross(from,to,d)==WalkingSnapshot.Passage.OPEN;
    }

    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) cancel(p);
    }
    @SubscribeEvent public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) cancel(p);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { batches.clear(); }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e) {
        long until = System.nanoTime()+2_000_000;
        // Rotate after each slice. One busy viewer cannot hold the whole tick budget or starve the rest.
        for (int slices=0; slices<16 && !batches.isEmpty() && System.nanoTime()<until; slices++) {
            var entry=batches.pollFirstEntry();
            var b=entry.getValue();
            if (b.actor.isRemoved() || b.actor.serverLevel()!=b.level || b.level.getServer()!=e.getServer()) continue;
            if (e.getServer().getTickCount()-b.started >= LIFETIME_TICKS) {
                b.expire(); continue;
            }
            for (int work=0; work<8 && System.nanoTime()<until && !b.finished; work++) b.advance();
            if (!b.finished) batches.put(entry.getKey(),b);
        }
    }

    private static final class Batch {
        final ServerPlayer actor;
        final ServerLevel level;
        final int started;
        final List<Leg> legs;
        final List<Result> results = new ArrayList<>();
        final Consumer<List<Result>> sink;
        final WalkingSnapshot terrain;
        WalkingSearch search;
        int validate, node;
        boolean finished;

        Batch(ServerPlayer actor,List<Leg> legs,Consumer<List<Result>> sink) {
            this.actor=actor;level=actor.serverLevel();started=level.getServer().getTickCount();
            this.legs=legs;this.sink=sink;terrain=new WalkingSnapshot(actor);
        }
        void advance() {
            if (validate==results.size() && results.size()<legs.size()) {
                var leg=legs.get(results.size());
                if (search==null) {
                    if (!level.isInWorldBounds(leg.from()) || !level.isInWorldBounds(leg.to())) {
                        results.add(Result.empty(Status.NO_DATA)); return;
                    }
                    search=new WalkingSearch(leg);
                }
                search.step(terrain);
                if (search.result!=null) { results.add(search.result); search=null; }
                return;
            }
            if (validate==results.size()) {
                finished=true; sink.accept(List.copyOf(results)); return;
            }
            var path=results.get(validate).path();
            if (node>=path.size()) { validate++;node=0;return; }
            // Discard results if the sampled door, floor, chunk or access changed during the search.
            boolean valid=node==0 ? currentlyWalkable(actor,path.getFirst())
                    : currentlyPassable(actor,path.get(node-1),path.get(node));
            if (!valid) {results.set(validate,Result.empty(Status.NO_DATA));validate++;node=0;}
            else node++;
        }
        void expire() {
            // A verified early leg can still be used if another candidate exhausted the shared time limit.
            for (int i=validate; i<results.size(); i++)
                if (!results.get(i).path().isEmpty()) results.set(i,Result.empty(Status.LIMIT));
            while (results.size()<legs.size()) results.add(Result.empty(Status.LIMIT));
            finished=true;sink.accept(List.copyOf(results));
        }
    }
}
