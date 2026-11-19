package com.example.examplemod.city;

import java.util.*;

/** Derived terrain only. Never accesses chunks or generates world data. Server-thread owned. */
public final class TerrainPyramid {
    public static final int MAX_LEVEL=7, SIZE=32, COUNT=1024;
    public static final short NO_HEIGHT=Short.MIN_VALUE;
    public record Key(int level,int x,int z) {
        public Key { if(level<0||level>MAX_LEVEL)throw new IllegalArgumentException("terrain level"); }
        public int span(){return SIZE<<level;}
        public Key parent(){return new Key(level+1,Math.floorDiv(x,2),Math.floorDiv(z,2));}
    }
    public record Data(int[] colors,short[] heights) {
        public Data {if(colors.length!=COUNT||heights.length!=COUNT)throw new IllegalArgumentException("terrain data");colors=colors.clone();heights=heights.clone();}
    }
    /** Dirty flags live on the existing tree nodes. A full priority queue only delays work. */
    private static final class Node {
        Data data;
        boolean dirty;
        boolean hasSamples;
        long version;
        final long[] edges = new long[9];
    }

    public record Snapshot(int[] colors, short[] heights, boolean complete, long hash, long version) {}

    private final Map<Key, Node> tiles = new HashMap<>();
    private final List<Key> roots = new ArrayList<>();
    private final LinkedHashSet<Key> urgent = new LinkedHashSet<>();
    private final LinkedHashMap<Key, Snapshot> snapshots = new LinkedHashMap<>(128, .75f, true);
    private int cursor, dirtyNodes;
    private boolean scanNext;
    private long serial = 1;

    public void put(int x, int z, int[] colors, short[] heights) {
        Key key = new Key(0, x, z);
        Node leaf = tiles.computeIfAbsent(key, ignored -> new Node());
        if (!publish(leaf, new Data(colors, heights))) return;
        while (key.level < MAX_LEVEL) {
            key = key.parent();
            Node parent = tiles.get(key);
            if (parent == null) {
                parent = new Node();
                tiles.put(key, parent);
                if (key.level == MAX_LEVEL) roots.add(key);
            }
            if (!parent.dirty) {
                parent.dirty = true;
                dirtyNodes++;
            }
        }
        prioritize(key);
    }

    private void prioritize(Key root) {
        if (urgent.size() < 256) urgent.add(root);
    }

    public Data get(Key key) {
        Node node = tiles.get(key);
        return node == null ? null : node.data;
    }

    public int queuedRoots() { return urgent.size(); }
    public boolean hasSamples(Key key) {
        Node node = tiles.get(key);
        return node != null && node.hasSamples;
    }
    public int pendingTiles() { return dirtyNodes; }

    /** A round-robin pass guarantees progress outside the bounded priority queue.
     * Each step descends at most seven levels and reduces at most one tile. */
    public void advance(int maximum, long nanos) {
        long start = System.nanoTime();
        int work = 0;
        for (int probes = 0; probes < maximum * 4 && work < maximum && dirtyNodes > 0; probes++) {
            if (System.nanoTime() - start >= nanos) break;
            Key root;
            if (!scanNext && !urgent.isEmpty()) root = urgent.removeFirst();
            else {
                if (cursor >= roots.size()) cursor = 0;
                root = roots.get(cursor++);
            }
            scanNext = !scanNext;
            if (!tiles.get(root).dirty) continue;
            reduceOne(root);
            work++;
            if (tiles.get(root).dirty) prioritize(root);
        }
    }

    private void reduceOne(Key key) {
        Data[] children = new Data[4];
        for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) {
            Key child = new Key(key.level - 1, key.x * 2 + x, key.z * 2 + z);
            Node node = tiles.get(child);
            if (node == null) continue;
            if (node.dirty) {
                reduceOne(child);
                return;
            }
            children[2 * z + x] = node.data;
        }
        Node node = tiles.get(key);
        publish(node, reduce(children));
        node.dirty = false;
        dirtyNodes--;
    }

    private boolean publish(Node node, Data updated) {
        Data previous = node.data;
        if (previous != null && Arrays.equals(previous.colors, updated.colors)
                && Arrays.equals(previous.heights, updated.heights)) return false;
        node.data = updated;
        node.hasSamples = Arrays.stream(updated.colors).anyMatch(c -> (c >>> 24) != 0);
        node.version = ++serial;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (dx == 0 && dz == 0) continue;
            if (edgeChanged(previous, updated, dx, dz)) node.edges[(dz + 1) * 3 + dx + 1] = node.version;
        }
        return true;
    }

    private static boolean edgeChanged(Data before, Data after, int dx, int dz) {
        int minX = dx == 1 ? SIZE - 1 : 0, maxX = dx == -1 ? 0 : SIZE - 1;
        int minZ = dz == 1 ? SIZE - 1 : 0, maxZ = dz == -1 ? 0 : SIZE - 1;
        for (int z = minZ; z <= maxZ; z++) for (int x = minX; x <= maxX; x++) {
            int i = z * SIZE + x;
            if (after.heights[i] != (before == null ? NO_HEIGHT : before.heights[i])) return true;
        }
        return false;
    }

    /** A negative version means that contributing data is still being reduced. */
    public long version(Key key) {
        long result = 1;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            Node node = tiles.get(new Key(key.level, key.x + dx, key.z + dz));
            if (node == null) continue;
            if (node.dirty) return -1;
            long stamp = dx == 0 && dz == 0 ? node.version : node.edges[(1 - dz) * 3 + 1 - dx];
            result = Math.max(result, stamp);
        }
        return result;
    }

    /** Published arrays are owned by the server thread. Outgoing packet constructors copy them. */
    public Snapshot snapshot(Key key) {
        long version = version(key);
        if (version < 0) return null;
        Snapshot snapshot = snapshots.get(key);
        if (snapshot != null && snapshot.version == version) return snapshot;
        Data data = get(key);
        int[] colors = data == null ? new int[COUNT] : data.colors;
        short[] heights = halo(key);
        snapshot = new Snapshot(colors, heights, Arrays.stream(colors).allMatch(c -> c >>> 24 == 255),
                revision(colors, heights), version);
        snapshots.put(key, snapshot);
        if (snapshots.size() > 256) snapshots.pollFirstEntry();
        return snapshot;
    }

    public static Data reduce(Data[] children) {
        int[] colors=new int[COUNT];short[] heights=new short[COUNT];Arrays.fill(heights,NO_HEIGHT);
        for(int z=0;z<SIZE;z++)for(int x=0;x<SIZE;x++) {
            Data d=children[(z/16)*2+x/16];if(d==null)continue;
            int r=0,g=0,b=0,n=0,h=0,hn=0;
            for(int dz=0;dz<2;dz++)for(int dx=0;dx<2;dx++) {
                int at=((z%16)*2+dz)*SIZE+(x%16)*2+dx,c=d.colors[at];
                if((c>>>24)==0)continue;
                int weight=c>>>24;r+=(c>>16&255)*weight;g+=(c>>8&255)*weight;b+=(c&255)*weight;n+=weight;
                if(d.heights[at]!=NO_HEIGHT){h+=d.heights[at]*weight;hn+=weight;}
            }
            if(n>0)colors[z*SIZE+x]=Math.max(1,n/4)<<24|(r/n)<<16|(g/n)<<8|b/n;
            if(hn>0)heights[z*SIZE+x]=(short)Math.round((double)h/hn);
        }
        return new Data(colors,heights);
    }
    /** A one-sample halo provides continuous lighting at tile boundaries. */
    public short[] halo(Key key) {
        short[] result=new short[34*34];Arrays.fill(result,NO_HEIGHT);
        for(int z=-1;z<=32;z++)for(int x=-1;x<=32;x++) {
            Data d=get(new Key(key.level,key.x+Math.floorDiv(x,32),key.z+Math.floorDiv(z,32)));
            if(d!=null)result[(z+1)*34+x+1]=d.heights[Math.floorMod(z,32)*32+Math.floorMod(x,32)];
        }
        return result;
    }
    public static long revision(int[] colors,short[] heights) {
        long hash=0xcbf29ce484222325L;
        for(int c:colors)hash=(hash^c)*0x100000001b3L;
        for(short h:heights)hash=(hash^h)*0x100000001b3L;
        return hash==0?1:hash;
    }
}
