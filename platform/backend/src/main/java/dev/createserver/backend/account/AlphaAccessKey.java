package dev.createserver.backend.account;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "alpha_access_key")
public class AlphaAccessKey {
    public enum State { UNUSED, USED, REVOKED }
    @Id private UUID id;
    @Column(name = "key_hash", nullable = false, unique = true, length = 64) private String keyHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private State state;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at") private Instant expiresAt;
    @Column(name = "used_at") private Instant usedAt;
    @Column(name = "platform_user_id", unique = true) private UUID platformUserId;
    protected AlphaAccessKey() {}
    AlphaAccessKey(String hash, Instant now, Instant expiry) {
        id = UUID.randomUUID(); keyHash = hash; createdAt = now; expiresAt = expiry; state = State.UNUSED;
    }
    boolean available(Instant now) { return state == State.UNUSED && (expiresAt == null || now.isBefore(expiresAt)); }
    void consume(UUID userId, Instant now) {
        if (!available(now)) throw new AccountFailure(400, "INVALID_ALPHA_KEY");
        state = State.USED; platformUserId = userId; usedAt = now;
    }
    public UUID getId() { return id; }
    public State getState() { return state; }
    @Override public String toString() { return "AlphaAccessKey[redacted]"; }
}
