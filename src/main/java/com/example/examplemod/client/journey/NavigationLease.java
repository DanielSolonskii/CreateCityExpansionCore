package com.example.examplemod.client.journey;

import com.example.examplemod.journey.NavigationLookup;
import java.util.UUID;

/** At most one outstanding read per objective. Silence expires the observation, not the user's intent. */
public final class NavigationLease {
    private final NavigationLookup.Kind kind;
    private final UUID id;
    private long nextRead, expires, sent;
    private String pending = "";
    private int missed;

    public NavigationLease(NavigationLookup.Kind kind, UUID id, long now) {
        this.kind = kind;
        this.id = id;
        nextRead = now;
        expires = now;
    }

    public boolean confirmed(long now) { return now < expires; }

    public String poll(long now, long sequence) {
        if (now < nextRead) return "";
        if (!pending.isEmpty()) missed = Math.min(3, missed + 1);
        sent = now;
        nextRead = now + (5_000L << missed);
        pending = new NavigationLookup(kind, id, sequence).token();
        return pending;
    }

    public boolean accept(String token, long now, boolean available) {
        if (pending.isEmpty() || !pending.equals(token)) return false;
        pending = "";
        if (available && now - sent >= 15_000) return false;
        missed = 0;
        nextRead = now + 5_000;
        expires = available ? sent + 15_000 : now;
        return true;
    }
}
