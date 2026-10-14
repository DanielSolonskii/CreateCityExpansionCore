package dev.createserver.backend.account;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlphaKeyService {
    private final AlphaAccessKeyRepository keys;
    private final Clock clock;
    public AlphaKeyService(AlphaAccessKeyRepository keys, Clock clock) { this.keys = keys; this.clock = clock; }

    // No HTTP endpoint. The administrative CLI prints these only after the transaction commits.
    @Transactional
    public List<Generated> generate(int count, Instant expiresAt) {
        Instant now = clock.instant();
        if (count < 1 || count > 10000 || (expiresAt != null && !expiresAt.isAfter(now))) {
            throw new IllegalArgumentException("Count must be 1..10000 and expiry must be in the future");
        }
        List<Generated> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String plaintext = Secrets.token();
            AlphaAccessKey key = keys.save(new AlphaAccessKey(Secrets.hash(plaintext), now, expiresAt));
            result.add(new Generated(key.getId(), plaintext));
        }
        keys.flush();
        return result;
    }
    public record Generated(UUID id, String plaintext) {
        @Override public String toString() { return "Generated[redacted]"; }
    }
}
