package dev.createserver.backend.account;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "registration_session")
public class RegistrationSession {
    public enum State { PENDING, COMPLETED, EXPIRED }
    @Id private UUID id;
    @Column(name = "platform_user_id", nullable = false, unique = true) private UUID platformUserId;
    @Column(name = "verification_hash", nullable = false, unique = true, length = 64) private String verificationHash;
    @Column(name = "polling_hash", nullable = false, unique = true, length = 64) private String pollingHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private State state;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "code_hash", unique = true, length = 64) private String codeHash;
    @Column(name = "restart_count", nullable = false) private int restartCount;
    protected RegistrationSession() {}
    RegistrationSession(UUID userId, String verificationHash, String pollingHash, Instant now, Instant expiry) {
        id = UUID.randomUUID();
        platformUserId = userId;
        this.verificationHash = verificationHash;
        this.pollingHash = pollingHash;
        state = State.PENDING;
        createdAt = now;
        expiresAt = expiry;
    }
    void expire(Instant now) {
        if (state == State.PENDING && !now.isBefore(expiresAt)) { state = State.EXPIRED; }
    }
    void complete(Instant now) { state = State.COMPLETED; completedAt = now; }
    void setCodeHash(String hash) { codeHash = hash; }
    String getVerificationHash() { return verificationHash; }
    String getCodeHash() { return codeHash; }
    void restart(String verification, String polling, String code, Instant now, Instant expiry) {
        if (state == State.COMPLETED || restartCount >= 3 || !now.isBefore(createdAt.plusSeconds(86400))) {
            throw new AccountFailure(409, "REGISTRATION_UNAVAILABLE");
        }
        verificationHash = verification; pollingHash = polling; codeHash = code;
        expiresAt = expiry; state = State.PENDING; restartCount++;
    }
    public UUID getId() { return id; }
    UUID getPlatformUserId() { return platformUserId; }
    String getPollingHash() { return pollingHash; }
    State getState() { return state; }
    Instant getExpiresAt() { return expiresAt; }
}
