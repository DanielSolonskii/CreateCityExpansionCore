package dev.createserver.backend.account;

import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SecretsAndRateLimitsTest {
    @Test
    void tokensAreIndependentStrongOpaqueValues() {
        String first = Secrets.token();
        String second = Secrets.token();
        assertThat(first).hasSize(43).isNotEqualTo(second);
        assertThat(Secrets.hash(first)).hasSize(64).isNotEqualTo(first);
        assertThat(Secrets.equal(Secrets.hash(first), Secrets.hash(first))).isTrue();
        assertThat(Secrets.equal(Secrets.hash(first), Secrets.hash(second))).isFalse();
    }

    @Test
    void rateLimitEnforcesWindowAndResetsWithoutRedis() {
        MutableClock clock = new MutableClock();
        RateLimits limits = new RateLimits(clock);
        assertThat(limits.allow("key", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(limits.allow("key", 2, Duration.ofMinutes(1))).isTrue();
        assertThat(limits.allow("key", 2, Duration.ofMinutes(1))).isFalse();
        clock.now = clock.now.plusSeconds(61);
        assertThat(limits.allow("key", 2, Duration.ofMinutes(1))).isTrue();
    }

    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-03T00:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
}
