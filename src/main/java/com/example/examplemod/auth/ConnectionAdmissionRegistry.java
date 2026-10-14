package com.example.examplemod.auth;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Single-server, connection-owned admission state. No network or gameplay dependency. */
public final class ConnectionAdmissionRegistry<C> {
    public enum Phase { RESERVED, CHALLENGED, CONSUMING, VERIFIED }
    public interface Transport<C> {
        boolean alive(C connection);
        void disconnect(C connection);
    }
    public record Attempt<C>(UUID minecraftUuid, C connection, UUID generation) {}
    public record Verified(UUID platformUserId, UUID minecraftUuid, String joinName, String displayName) {
        public Verified {
            Objects.requireNonNull(platformUserId);
            Objects.requireNonNull(minecraftUuid);
            Objects.requireNonNull(joinName);
            Objects.requireNonNull(displayName);
        }
    }
    private static final class Entry<C> {
        final Attempt<C> attempt;
        final long reservedUntil;
        long challengeUntil;
        Phase phase = Phase.RESERVED;
        Verified identity;
        Entry(Attempt<C> attempt, long reservedUntil) { this.attempt = attempt; this.reservedUntil = reservedUntil; }
    }
    private final Map<UUID, Entry<C>> entries = new HashMap<>();
    private final Transport<C> transport;
    private final LongSupplier nanos;
    private final int capacity;
    private final long reservationNanos;
    private final long challengeNanos;

    public ConnectionAdmissionRegistry(Transport<C> transport, LongSupplier nanos, int capacity,
                                       Duration reservationTimeout, Duration challengeTimeout) {
        this.transport = Objects.requireNonNull(transport);
        this.nanos = Objects.requireNonNull(nanos);
        if (capacity < 1 || capacity > 4096 || reservationTimeout.isNegative() || reservationTimeout.isZero()
                || challengeTimeout.isNegative() || challengeTimeout.isZero()
                || reservationTimeout.compareTo(Duration.ofMinutes(5)) > 0
                || challengeTimeout.compareTo(reservationTimeout) > 0) throw new IllegalArgumentException("Invalid admission limits");
        this.capacity = capacity;
        this.reservationNanos = reservationTimeout.toNanos();
        this.challengeNanos = challengeTimeout.toNanos();
    }

    /** Run before vanilla duplicate eviction. Deny NEW; never evict a live OLD connection. */
    public synchronized Attempt<C> reserve(UUID minecraftUuid, C connection) {
        Objects.requireNonNull(minecraftUuid);
        Objects.requireNonNull(connection);
        sweep();
        Entry<C> existing = entries.get(minecraftUuid);
        if (existing != null) return existing.attempt.connection() == connection ? existing.attempt : null;
        if (!transport.alive(connection) || entries.size() >= capacity) return null;
        Attempt<C> attempt = new Attempt<>(minecraftUuid, connection, UUID.randomUUID());
        entries.put(minecraftUuid, new Entry<>(attempt, nanos.getAsLong() + reservationNanos));
        return attempt;
    }

    public synchronized boolean challenge(Attempt<C> attempt) {
        Entry<C> entry = live(attempt);
        if (entry == null || entry.phase != Phase.RESERVED) return false;
        entry.phase = Phase.CHALLENGED;
        entry.challengeUntil = nanos.getAsLong() + challengeNanos;
        return true;
    }

    /** Exactly one backend operation per challenge; client success is not an admission decision. */
    public synchronized boolean beginConsume(Attempt<C> attempt) {
        Entry<C> entry = live(attempt);
        if (entry == null || entry.phase != Phase.CHALLENGED) return false;
        entry.phase = Phase.CONSUMING;
        return true;
    }

    /** Late/disconnected/stale callbacks cannot authenticate a replacement connection. */
    public synchronized boolean complete(Attempt<C> attempt, Verified identity) {
        Objects.requireNonNull(identity);
        Entry<C> entry = live(attempt);
        if (entry == null || entry.phase != Phase.CONSUMING || !identity.minecraftUuid().equals(attempt.minecraftUuid())) return false;
        entry.identity = identity;
        entry.phase = Phase.VERIFIED;
        return true;
    }

    public synchronized Verified identity(UUID minecraftUuid, C connection) {
        Entry<C> entry = entries.get(minecraftUuid);
        return entry != null && entry.attempt.connection() == connection && entry.phase == Phase.VERIFIED
                && transport.alive(connection) ? entry.identity : null;
    }

    public synchronized Attempt<C> attempt(UUID minecraftUuid, C connection) {
        Entry<C> entry = entries.get(minecraftUuid);
        return entry != null && entry.attempt.connection() == connection && live(entry.attempt) != null ? entry.attempt : null;
    }

    /** Timeout closes the owner before releasing its UUID. Failed close keeps the reservation. */
    public synchronized void sweep() {
        for (Entry<C> entry : new java.util.ArrayList<>(entries.values())) {
            if (!transport.alive(entry.attempt.connection())) { entries.remove(entry.attempt.minecraftUuid(), entry); continue; }
            if (expired(entry)) close(entry);
        }
    }

    public synchronized void reject(Attempt<C> attempt) {
        Entry<C> entry = owned(attempt);
        if (entry != null) close(entry);
    }

    public synchronized void disconnected(Attempt<C> attempt) {
        Entry<C> entry = owned(attempt);
        if (entry != null && !transport.alive(attempt.connection())) entries.remove(attempt.minecraftUuid(), entry);
    }

    public synchronized int size() { return entries.size(); }

    private Entry<C> owned(Attempt<C> attempt) {
        if (attempt == null) return null;
        Entry<C> entry = entries.get(attempt.minecraftUuid());
        return entry != null && entry.attempt.connection() == attempt.connection()
                && entry.attempt.generation().equals(attempt.generation()) ? entry : null;
    }
    private Entry<C> live(Attempt<C> attempt) {
        Entry<C> entry = owned(attempt);
        if (entry == null) return null;
        if (!transport.alive(attempt.connection())) { entries.remove(attempt.minecraftUuid(), entry); return null; }
        if (expired(entry)) { close(entry); return null; }
        return entry;
    }
    private boolean expired(Entry<C> entry) {
        if (entry.phase == Phase.VERIFIED) return false;
        long now = nanos.getAsLong();
        return now - entry.reservedUntil >= 0 || (entry.phase != Phase.RESERVED && now - entry.challengeUntil >= 0);
    }
    private void close(Entry<C> entry) {
        transport.disconnect(entry.attempt.connection());
        if (!transport.alive(entry.attempt.connection())) entries.remove(entry.attempt.minecraftUuid(), entry);
    }
}
