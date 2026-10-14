package dev.createserver.backend.account;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "account_credentials")
public class AccountCredentials {
    @Id @Column(name = "platform_user_id") private UUID platformUserId;
    @Column(nullable = false, unique = true, length = 32) private String username;
    @Column(name = "password_hash", nullable = false, length = 100) private String passwordHash;
    protected AccountCredentials() {}
    AccountCredentials(UUID userId, String username, String passwordHash) {
        this.platformUserId = userId;
        this.username = username;
        this.passwordHash = passwordHash;
    }
    public UUID getPlatformUserId() { return platformUserId; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
}
