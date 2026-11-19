package com.example.examplemod.client.map;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.UUID;

/** Per confirmed world/dimension, client thread only. Times and server versions live only for this connection. */
final class TerrainExchange {
    static final int LIMIT = 64;
    static final long TIMEOUT = 5000;
    record Request(UUID view, UUID id, long hash, long sent) {}
    private static final class State {
        long floor, checked, next, hash;
        boolean hasHash;
        int attempts;
        long publicationDelay;
        UUID publishingView;
        boolean failed;
        Request request;
    }
    private final LinkedHashMap<TerrainTileStore.Key, State> states = new LinkedHashMap<>(64, .75f, true);
    private final LinkedHashSet<State> pending = new LinkedHashSet<>();

    private State state(TerrainTileStore.Key key) {
        State value = states.get(key);
        if (value != null) return value;
        if (states.size() >= 8192) {
            var it = states.entrySet().iterator();
            while (it.hasNext()) if (it.next().getValue().request == null) { it.remove(); break; }
        }
        value = new State();
        states.put(key, value);
        return value;
    }
    private void finish(State s) {
        if (s.request != null) { s.request = null; pending.remove(s); }
    }
    void expire(long now) {
        var it = pending.iterator();
        while (it.hasNext()) {
            State s = it.next();
            if (now - s.request.sent < TIMEOUT) break;
            it.remove();
            s.request = null;
            s.failed = s.attempts >= 3;
            s.next = now + (s.failed ? 30_000 : 500L * s.attempts);
        }
    }
    void forget(UUID view) {
        for (State s : states.values()) {
            if (s.request != null && s.request.view.equals(view)) {
                finish(s); s.attempts = 0;
            }
            if (view.equals(s.publishingView)) {
                s.publishingView = null; s.publicationDelay = s.next = 0;
            }
        }
    }
    void retry(TerrainTileStore.Key key) {
        State s = states.get(key);
        if (s != null) { s.failed = false; s.next = s.checked = s.publicationDelay = 0; s.publishingView = null; s.attempts = 0; }
    }
    boolean due(TerrainTileStore.Key key, long now, long ttl) {
        State s = states.get(key);
        return s == null || s.request == null && now >= s.next && (s.checked == 0 || now-s.checked >= ttl);
    }
    Request start(TerrainTileStore.Key key, UUID view, long hash, long now) {
        State s = state(key);
        if (s.request != null || pending.size() >= LIMIT || now < s.next) return null;
        s.failed = false;
        s.attempts = Math.min(3, s.attempts + 1);
        s.request = new Request(view, UUID.randomUUID(), hash, now);
        pending.add(s);
        return s.request;
    }
    boolean invalidate(TerrainTileStore.Key key, long version) {
        State s = state(key);
        if (version < s.floor || version == s.floor && s.publishingView == null) return false;
        s.floor = version;
        s.publicationDelay = 0;
        s.publishingView = null;
        s.checked = s.next = 0;
        s.failed = false;
        s.attempts = 0;
        finish(s);
        return true;
    }
    boolean accept(TerrainTileStore.Key key, UUID view, UUID id, long version, boolean pending,
                   boolean unchanged, long hash, boolean matchingBase, long now) {
        State s = states.get(key);
        if (s == null || s.request == null || !s.request.view.equals(view) || !s.request.id.equals(id)) return false;
        Request request = s.request;
        finish(s);
        if (pending) {
            // A watched tile normally wakes on publication. Poll slowly in case that notice is lost.
            s.publishingView = view;
            s.publicationDelay = s.publicationDelay == 0 ? 1000 : Math.min(8000, s.publicationDelay * 2);
            s.checked = 0; s.next = now + s.publicationDelay;
            return false;
        }
        s.publishingView = null;
        s.publicationDelay = 0;
        if (version < s.floor || unchanged && (!matchingBase || request.hash != hash)) {
            s.checked = 0; s.next = now + 500;
            return false;
        }
        com.example.examplemod.client.ClientTelemetry.tile(now - request.sent());
        s.floor = version;
        s.hasHash = true; s.hash = hash;
        s.checked = now; s.next = 0; s.attempts = 0; s.failed = false;
        return true;
    }
    boolean acceptsCachedHash(TerrainTileStore.Key key, long hash) {
        State s = states.get(key);
        return s == null || !s.hasHash || s.hash == hash;
    }
    boolean pending(TerrainTileStore.Key key) { State s = states.get(key); return s != null && s.request != null; }
    boolean publishing(TerrainTileStore.Key key) { State s = states.get(key); return s != null && s.publishingView != null; }
    boolean invalidated(TerrainTileStore.Key key) { State s = states.get(key); return s != null && (s.floor > 0 || s.publishingView != null) && s.checked == 0; }
    boolean failed(TerrainTileStore.Key key) { State s = states.get(key); return s != null && s.failed; }
    boolean retrying(TerrainTileStore.Key key) { State s = states.get(key); return s != null && s.attempts > 0; }
    long checked(TerrainTileStore.Key key) { State s = states.get(key); return s == null ? 0 : s.checked; }
    int inflight() { return pending.size(); }
}
