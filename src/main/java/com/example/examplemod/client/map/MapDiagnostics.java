package com.example.examplemod.client.map;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import org.slf4j.LoggerFactory;

/** Optional, local-only counters. Mutated on the client thread and reported on disconnect. */
public final class MapDiagnostics {
    private static final boolean ENABLED = Boolean.getBoolean("ccec.map.diagnostics");
    private static final ThreadMXBean THREAD = ENABLED ? ManagementFactory.getThreadMXBean() : null;
    private static long creations, suspensions, releases, requests, retries, hits, discarded;
    private static long openedAt, firstDrawCount, sumFirstNanos, unknownDraws, cpuNanos;
    private static int peakInflight, peakRaster;

    private MapDiagnostics() {}

    public static void opened() { if (ENABLED) openedAt = System.nanoTime(); }
    public static void created() { if (ENABLED) creations++; }
    public static void released() { if (ENABLED) releases++; }
    public static void suspended() { if (ENABLED) suspensions++; }
    public static void request(boolean retry) {
        if (!ENABLED) return;
        requests++;
        if (retry) retries++;
    }
    public static void cacheHit() { if (ENABLED) hits++; }
    public static void stale() { if (ENABLED) discarded++; }
    public static void queues(int inflight, int raster) {
        if (!ENABLED) return;
        peakInflight = Math.max(peakInflight, inflight);
        peakRaster = Math.max(peakRaster, raster);
    }
    static boolean awaitingFirstDraw() { return ENABLED && openedAt != 0; }
    public static void terrainDrawn(boolean known) {
        if (!ENABLED || openedAt == 0) return;
        if (!known) unknownDraws++;
        firstDrawCount++;
        sumFirstNanos += System.nanoTime() - openedAt;
        openedAt = 0;
    }
    public static long beginDraw() {
        return ENABLED && THREAD.isCurrentThreadCpuTimeSupported() && THREAD.isThreadCpuTimeEnabled()
                ? THREAD.getCurrentThreadCpuTime() : -1;
    }
    public static void endDraw(long start) {
        if (start >= 0) cpuNanos += Math.max(0, THREAD.getCurrentThreadCpuTime() - start);
    }
    private static long nextSample;
    static void sampleResources(long now, int activeHandles) {
        if (!ENABLED || now < nextSample) return;
        nextSample = now + 30_000;
        LoggerFactory.getLogger(MapDiagnostics.class).info(
                "Map resources rawTiles={} viewHandles={} pooledHandles={} atlasSlots={} atlasPages={} inflight={} rasterQueue={} ioQueue={}",
                TerrainCache.cachedTiles(), activeHandles, TerrainCache.pooledTextures(), TerrainAtlas.occupied(),
                TerrainAtlas.pages(), TerrainCache.inflight(), TerrainTileTexture.queueSize(), TerrainTileStore.ioQueueSize());
    }
    public static void report() {
        if (!ENABLED) return;
        LoggerFactory.getLogger(MapDiagnostics.class).info(
                "Map diagnostics panelsCreated={} suspended={} released={} requests={} retries={} cacheHits={} discarded={} peakInflight={} peakRaster={} firstDraws={} firstUnknown={} meanFirstDrawMs={} drawCpuMs={}",
                creations, suspensions, releases, requests, retries, hits, discarded, peakInflight, peakRaster,
                firstDrawCount, unknownDraws, firstDrawCount == 0 ? 0 : sumFirstNanos / firstDrawCount / 1_000_000.0, cpuNanos / 1_000_000.0);
        creations = suspensions = releases = requests = retries = hits = discarded = 0;
        openedAt = firstDrawCount = sumFirstNanos = unknownDraws = cpuNanos = 0;
        peakInflight = peakRaster = 0;
    }
}
