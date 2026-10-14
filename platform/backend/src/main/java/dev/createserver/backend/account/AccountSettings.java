package dev.createserver.backend.account;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("platform.accounts")
public record AccountSettings(String telegramBotUsername, String botBackendSecret, long registrationTtlSeconds) {
    public AccountSettings {
        if (registrationTtlSeconds < 60 || registrationTtlSeconds > 3600) {
            throw new IllegalArgumentException("Registration TTL must be between 60 and 3600 seconds");
        }
    }
    @Override public String toString() { return "AccountSettings[redacted]"; }
}
