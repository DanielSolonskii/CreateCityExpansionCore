package com.example.examplemod.city;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import net.minecraft.world.level.ChunkPos;

/** Tracks coordinates only for naturally loaded chunks. The priority queue has a hard cap.
 * Overflow stays dirty on its loaded record and is serviced by a bounded rotating scan. */
final class TerrainSurfaceQueue {
    static final int PRIORITY_LIMIT = 4096;
    private enum Work { CLEAN, DISCOVER, RESAMPLE }
    private final LinkedHashMap<Long, Work> loaded = new LinkedHashMap<>();
    private final LinkedHashSet<Long> priority = new LinkedHashSet<>();
    private final LinkedHashSet<Long> visible = new LinkedHashSet<>();
    private final Map<TerrainPyramid.Key, Integer> pending = new HashMap<>();
    private final Map<TerrainPyramid.Key, Integer> discovering = new HashMap<>();

    void load(long chunk, boolean previouslySurveyed) {
        if (loaded.putIfAbsent(chunk, Work.CLEAN) == null)
            schedule(chunk, previouslySurveyed ? Work.RESAMPLE : Work.DISCOVER);
    }
    void unload(long chunk) {
        complete(chunk);
        loaded.remove(chunk);
    }
    boolean dirty(long chunk) {
        Work work = loaded.get(chunk);
        return work == Work.RESAMPLE || work == Work.DISCOVER;
    }

    void changed(long chunk) {
        if (loaded.get(chunk) == Work.CLEAN) schedule(chunk, Work.RESAMPLE);
    }

    private void schedule(long chunk, Work work) {
        loaded.put(chunk, work);
        if (priority.size() < PRIORITY_LIMIT) priority.add(chunk);
        adjust(work == Work.DISCOVER ? discovering : pending, chunk, 1);
    }

    void complete(long chunk) {
        if (!dirty(chunk)) return;
        Work work = loaded.put(chunk, Work.CLEAN);
        priority.remove(chunk);
        visible.remove(chunk);
        adjust(work == Work.DISCOVER ? discovering : pending, chunk, -1);
    }

    private static TerrainPyramid.Key base(long chunk) {
        return new TerrainPyramid.Key(0, Math.floorDiv(ChunkPos.getX(chunk), 2),
                Math.floorDiv(ChunkPos.getZ(chunk), 2));
    }

    private static void adjust(Map<TerrainPyramid.Key,Integer> counts, long chunk, int delta) {
        for (var key = base(chunk); ; key = key.parent()) {
            int count = counts.getOrDefault(key, 0) + delta;
            if (count > 0) counts.put(key, count);
            else counts.remove(key);
            if (key.level() == TerrainPyramid.MAX_LEVEL) return;
        }
    }

    boolean pending(TerrainPyramid.Key key, boolean hasSamples) {
        // New coverage is published incrementally with the real alpha mask. Pending
        // discovery must not become a confirmed "unknown" reply before any sample exists.
        if (!hasSamples && discovering.containsKey(key)) return true;
        if (pending.isEmpty()) return false;
        for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
            if (pending.containsKey(new TerrainPyramid.Key(key.level(),key.x()+dx,key.z()+dz))) return true;
        }
        return false;
    }

    int prioritySize() { return priority.size(); }

    void prioritize(TerrainPyramid.Key key, int focusChunkX, int focusChunkZ) {
        int side = 2 << key.level();
        // The request handler already validated the world bounds.
        int minX = key.x() * side, minZ = key.z() * side;
        int x = Math.clamp(focusChunkX, minX, minX + side - 1);
        int z = Math.clamp(focusChunkZ, minZ, minZ + side - 1);
        prioritizeChunk(x,z);
        for (int dz=-1; dz<=1; dz++) for (int dx=-1; dx<=1; dx++)
            if (dx!=0 || dz!=0) prioritizeChunk(x+dx,z+dz);
    }

    private void prioritizeChunk(int x, int z) {
        if (visible.size() >= 64) return;
        long chunk = ChunkPos.asLong(x,z);
        if (dirty(chunk)) visible.add(chunk);
    }

    void advance(long tick, boolean viewing, Predicate<TerrainPyramid.Key> watched, LongConsumer capture) {
        if (!viewing && pending.isEmpty() && discovering.isEmpty()) return;
        // Alternate priority and fair work. Even a flood of place events cannot starve the
        // overflow scan. Each tick samples at most one already-loaded chunk.
        if ((tick & 1) == 0 && !visible.isEmpty()) {
            capture.accept(visible.removeFirst());
            return;
        }
        if ((tick & 3) != 3 && !priority.isEmpty()) {
            capture.accept(priority.removeFirst());
            return;
        }
        int probes = Math.min(8, loaded.size());
        for (int i = 0; i < probes; i++) {
            var entry = loaded.firstEntry();
            long chunk = entry.getKey();
            loaded.putLast(chunk, entry.getValue());
            if (entry.getValue() != Work.CLEAN || (viewing && interested(chunk, watched))) {
                capture.accept(chunk);
                return;
            }
        }
    }

    private static boolean interested(long chunk, Predicate<TerrainPyramid.Key> watched) {
        for (var key = base(chunk); ; key = key.parent()) {
            // A neighbouring tile may use one of this tile's heights for its lighting.
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) {
                if (watched.test(new TerrainPyramid.Key(key.level(), key.x()+dx, key.z()+dz))) return true;
            }
            if (key.level() == TerrainPyramid.MAX_LEVEL) return false;
        }
    }
}
