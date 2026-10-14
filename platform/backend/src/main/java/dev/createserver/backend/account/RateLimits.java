package dev.createserver.backend.account;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class RateLimits {
    private record Bucket(Instant expires, int count) {}
    private final Map<String, Bucket> buckets = new HashMap<>();
    private final Clock clock;
    public RateLimits(Clock clock) { this.clock = clock; }

    public synchronized boolean allow(String key, int limit, Duration window) {
        Instant now = clock.instant();
        buckets.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expires()));
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            if (buckets.size() >= 10000) { return false; }
            bucket = new Bucket(now.plus(window), 0);
        }
        if (bucket.count() >= limit) { return false; }
        buckets.put(key, new Bucket(bucket.expires(), bucket.count() + 1));
        return true;
    }
}
