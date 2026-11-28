package com.example.examplemod.city;

import java.util.*;
import net.minecraft.world.level.ChunkPos;

/** Exercises the publishing tree, source queue and view leases used by CityMapSavedData. */
public final class TerrainFreshnessCheck {
    private TerrainFreshnessCheck() {}

    private static void require(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    public static void run() {
        publishing();
        queuePressure();
        subscriptions();
        surfaceRotation();
        coldViewport();
    }

    public static void main(String[] args) { run(); }

    private static void publishing() {
        var tree = new TerrainPyramid();
        int[] colors = new int[1024];
        short[] heights = new short[1024];
        Arrays.fill(colors, 0xff339933);
        Arrays.fill(heights, (short)75);
        var leaf = new TerrainPyramid.Key(0, -1, -1);
        var east = new TerrainPyramid.Key(0, 0, -1);
        var diagonal = new TerrainPyramid.Key(0, 0, 0);
        tree.put(-1, -1, colors, heights);
        settle(tree);
        long old = tree.snapshot(leaf).version();
        long neighbor = tree.snapshot(east).version();
        long corner = tree.snapshot(diagonal).version();
        var parent = leaf.parent();
        var overview = tree.snapshot(parent);
        colors[100] ^= 0xff;
        tree.put(-1, -1, colors, heights);
        require(tree.snapshot(leaf).version() > old, "new leaf publication has an increasing sequence");
        require(tree.snapshot(east).version() == neighbor, "interior change does not invalidate adjacent halo");
        require(tree.snapshot(parent) == null, "unreduced overview cannot be acknowledged");
        int depth = tree.pendingTiles();
        for (int i=0; i<64; i++) {
            colors[110] ^= 0xff;
            tree.put(-1, -1, colors, heights);
        }
        require(tree.pendingTiles() == depth && depth == TerrainPyramid.MAX_LEVEL, "repeated edits coalesce at every level");
        tree.advance(1, 1_000_000_000L);
        require(tree.pendingTiles() == depth - 1, "one step reduces exactly one tile");
        require(tree.snapshot(leaf.parent()).version() > overview.version(), "ready parent published after real reduction");
        heights[1023] += 15;
        tree.put(-1, -1, colors, heights);
        require(tree.snapshot(east).version() > neighbor && tree.snapshot(diagonal).version() > corner,
                "east edge and southeast corner independently contribute to neighbouring versions");
        require(tree.snapshot(diagonal).heights()[0] == heights[1023], "diagonal halo uses new corner");
        settle(tree);
        var reference = new TerrainPyramid();
        reference.put(-1, -1, colors, heights);
        settle(reference);
        for (var key=leaf; ;key=key.parent()) {
            require(tree.snapshot(key).hash() == reference.snapshot(key).hash(), "settled LOD equals fresh tree");
            require(Arrays.equals(tree.snapshot(key).colors(), reference.snapshot(key).colors()), "settled LOD colours");
            require(Arrays.equals(tree.snapshot(key).heights(), reference.snapshot(key).heights()), "settled LOD halo");
            require(tree.snapshot(key) == tree.snapshot(key), "repeat viewers share published raster input");
            if (key.level() == TerrainPyramid.MAX_LEVEL) break;
        }
    }

    private static void settle(TerrainPyramid tree) {
        for (int i=0; i<2000 && tree.pendingTiles()>0; i++) tree.advance(64, 100_000_000L);
        require(tree.pendingTiles()==0, "bounded passes converge");
    }

    private static void queuePressure() {
        var tree = new TerrainPyramid();
        int[] colors = new int[1024];
        short[] heights = new short[1024];
        Arrays.fill(colors, 0xff667788);
        for (int i = 0; i < 270; i++) tree.put(i * 128, 0, colors, heights);
        require(tree.queuedRoots() <= 256, "priority queue is bounded");
        settle(tree);
        require(tree.snapshot(new TerrainPyramid.Key(7,269,0)) != null, "overflow root eventually published");
    }

    private static void subscriptions() {
        var interest = new TerrainInterest();
        var key = new TerrainPyramid.Key(4,-5,3);
        UUID player=UUID.randomUUID(), first=UUID.randomUUID(), second=UUID.randomUUID();
        interest.watch(player,first,key,0,7);
        interest.watch(player,second,key,0,7);
        var sent = new ArrayList<TerrainInterest.Address>();
        interest.advance(1,256,16,k -> -1,(address,version) -> sent.add(address));
        require(sent.isEmpty(), "dirty parent doesn't notify");
        for(int i=0;i<5;i++) {
            int before = sent.size();
            interest.advance(i+2,256,1,k -> 10,(address,version) -> sent.add(address));
            require(sent.size()-before <= 1, "fan-out respects global budget");
        }
        require(sent.size()==2 && !sent.get(0).view().equals(sent.get(1).view()), "each interested view receives version once");
        interest.unwatch(player,first);
        require(interest.size()==1 && interest.covers(key), "closing one consumer keeps another consumer's interest");
        interest.advance(TerrainInterest.TTL,256,16,k -> 11,(address,version) -> sent.add(address));
        require(interest.size()==0 && !interest.covers(key) && sent.size()==2, "idle lease expires without another mapper");
        interest.watch(player, first, key, 2000, 2);
        interest.watch(player, first, key.parent(), 2000, 2);
        interest.advance(2001, 256, 16, k -> 3, (address,version) -> interest.unwatch(player, first));
        require(interest.size()==0 && !interest.covers(key), "access revoked during send cannot resurrect a watch");
    }

    private static void surfaceRotation() {
        var queue = new TerrainSurfaceQueue();
        int count = TerrainSurfaceQueue.PRIORITY_LIMIT + 24;
        for (int i=0; i<count; i++) {
            long chunk=ChunkPos.asLong(i,0);
            queue.load(chunk, true);
            queue.changed(chunk);
            queue.changed(chunk);
        }
        require(queue.prioritySize() == TerrainSurfaceQueue.PRIORITY_LIMIT, "source queue has hard cap");
        long last = ChunkPos.asLong(count-1,0);
        var parent = new TerrainPyramid.Key(7,(count-1)/256,0);
        require(queue.pending(parent,true) && queue.dirty(last), "overflow remains pending on all ancestors");
        for (int i=0; i<count*3; i++) queue.advance(i,false,k->false,queue::complete);
        require(!queue.dirty(last) && !queue.pending(parent,true), "fair pass recovers overflow, including the last chunk");
        var small = new TerrainSurfaceQueue();
        small.load(ChunkPos.asLong(-8,0), false);
        small.complete(ChunkPos.asLong(-8,0));
        var seen = new HashSet<Long>();
        small.advance(1,true,k->k.equals(new TerrainPyramid.Key(6,-1,0)),seen::add);
        require(seen.size()==1 && seen.contains(ChunkPos.asLong(-8,0)), "overview interest revisits a naturally loaded source");
        small.unload(ChunkPos.asLong(-8,0));
        small.advance(2,true,k->true,v->{throw new AssertionError("sampled unloaded chunk");});
    }

    private static void coldViewport() {
        var tree = new TerrainPyramid();
        var sources = new TerrainSurfaceQueue();
        for (int x=0; x<TerrainSurfaceQueue.PRIORITY_LIMIT+50; x++)
            sources.load(ChunkPos.asLong(x,10), false);
        var key = new TerrainPyramid.Key(0,-1,-1);
        long focus = ChunkPos.asLong(-1,-1);
        sources.load(focus,false);
        sources.load(ChunkPos.asLong(-2,-1),false);
        require(sources.pending(key,tree.hasSamples(key)), "empty queued survey is still loading");
        sources.prioritize(key,-1,-1);
        sources.advance(0,true,k -> true,c -> {
            require(c==focus, "visible naturally loaded source jumps the full discovery queue");
            int[] colors = new int[1024];
            colors[1023]=0xffbb9922;
            tree.put(-1,-1,colors,new short[1024]);
            sources.complete(c);
        });
        require(!sources.pending(key,tree.hasSamples(key)) && !tree.snapshot(key).complete(),
                "surveyed pixels can draw while the neighbouring chunk is still being discovered");
        require(tree.snapshot(key).colors()[0]==0 && (tree.snapshot(key).colors()[1023]>>>24)==255,
                "unobserved pixels remain transparent");
        settle(tree);
        var overview=key.parent();
        require(!sources.pending(overview,tree.hasSamples(overview)) && !tree.snapshot(overview).complete(),
                "partially surveyed overview is available before the rest of spawn");
        sources.changed(focus);
        require(sources.pending(key,true) && sources.pending(overview,true),
                "a confirmed edit to an already sampled chunk still gates its previous publication");
    }
}
