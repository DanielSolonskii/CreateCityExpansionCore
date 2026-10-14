package dev.createserver.backend.account;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "telegram_identity")
public class TelegramIdentity {
    @Id @Column(name = "telegram_user_id") private Long telegramUserId;
    @Column(name = "platform_user_id", nullable = false, unique = true) private UUID platformUserId;
    @Column(name = "current_username", length = 64) private String currentUsername;
    @Column(name = "verified_at", nullable = false) private Instant verifiedAt;
    protected TelegramIdentity() {}
    TelegramIdentity(long telegramId, UUID userId, String username, Instant at) {
        telegramUserId = telegramId;
        platformUserId = userId;
        currentUsername = username;
        verifiedAt = at;
    }
    public UUID getPlatformUserId() { return platformUserId; }
}
