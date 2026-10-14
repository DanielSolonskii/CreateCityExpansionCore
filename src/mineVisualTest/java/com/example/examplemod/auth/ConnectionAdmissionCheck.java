package com.example.examplemod.auth;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Headless precondition checks, not a substitute for NeoForge lifecycle acceptance. */
public final class ConnectionAdmissionCheck {
    private static final class Connection { volatile boolean alive = true; boolean closeWorks = true; }
    private static final AtomicLong time = new AtomicLong();
    private static ConnectionAdmissionRegistry<Connection> registry(int cap) {
        return new ConnectionAdmissionRegistry<>(new ConnectionAdmissionRegistry.Transport<>() {
            public boolean alive(Connection c) { return c.alive; }
            public void disconnect(Connection c) { if (c.closeWorks) c.alive = false; }
        }, time::get, cap, Duration.ofSeconds(120), Duration.ofSeconds(30));
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        check(AuthenticationBootstrap.accountDisplayName("drannch1k"), "account display name rejected");
        check(AuthenticationBootstrap.accountDisplayName("Engineer_123"), "literal account name rejected");
        for (String invalid : new String[]{"", "ab", "name with space", "<admin>", "a\noperator", "seventeen_chars_1", "§cadmin"})
            check(!AuthenticationBootstrap.accountDisplayName(invalid), "unsafe display name accepted");
        time.set(0);
        var registry = registry(2);
        var uuid = UUID.randomUUID();
        var old = new Connection();
        var first = registry.reserve(uuid, old);
        check(first != null && registry.reserve(uuid, new Connection()) == null && old.alive, "duplicate kicked OLD");
        check(registry.reserve(uuid, old).equals(first), "idempotent reserve");
        check(registry.challenge(first) && !registry.challenge(first), "challenge replay");
        check(registry.beginConsume(first) && !registry.beginConsume(first), "concurrent consume");
        var identity = new ConnectionAdmissionRegistry.Verified(UUID.randomUUID(), uuid, "cs12345678901234", "Engineer");
        check(!registry.complete(first, new ConnectionAdmissionRegistry.Verified(UUID.randomUUID(), UUID.randomUUID(), identity.joinName(), identity.displayName())), "UUID spoof");
        check(registry.complete(first, identity), "trusted completion rejected");
        check(registry.identity(uuid,old).displayName().equals("Engineer") && registry.identity(uuid,old).joinName().equals("cs12345678901234"), "display overwrote technical identity");
        time.set(Duration.ofHours(3).toNanos()); registry.sweep();
        check(registry.identity(uuid, old) == identity && old.alive, "verified gameplay expired");
        old.alive = false; registry.disconnected(first);
        var replacement = new Connection(); var second = registry.reserve(uuid, replacement);
        registry.disconnected(first); registry.reject(first);
        check(second != null && replacement.alive && !registry.complete(first, identity), "stale callback affected replacement");
        registry.challenge(second); registry.beginConsume(second);
        time.addAndGet(Duration.ofSeconds(30).toNanos());
        check(!registry.complete(second, identity) && !replacement.alive && registry.size() == 0, "deadline equality/late callback");
        var slowClose = new Connection(); slowClose.closeWorks = false;
        registry.reserve(uuid, slowClose); time.addAndGet(Duration.ofSeconds(120).toNanos()); registry.sweep();
        check(registry.size() == 1 && registry.reserve(uuid, new Connection()) == null, "released live expired owner");
        slowClose.closeWorks = true; registry.sweep(); check(registry.size() == 0, "timeout cleanup");
        var cap = registry(1); cap.reserve(UUID.randomUUID(), new Connection());
        check(cap.reserve(UUID.randomUUID(), new Connection()) == null, "unbounded registry");
        try (var executor = Executors.newFixedThreadPool(2)) {
            var concurrent = registry(2); var start = new CountDownLatch(1);
            var id = UUID.randomUUID();
            var a = executor.submit(() -> { start.await(); return concurrent.reserve(id, new Connection()); });
            var b = executor.submit(() -> { start.await(); return concurrent.reserve(id, new Connection()); });
            start.countDown(); check((a.get() == null) != (b.get() == null), "two reservations won");
        }
        System.out.println("Connection admission preconditions passed: duplicate, replay, UUID, deadline, stale callback, capacity, concurrency");
    }
}
