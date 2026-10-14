package dev.createserver.backend.account;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TelegramIdentityRepository extends JpaRepository<TelegramIdentity, Long> {
    boolean existsByPlatformUserId(UUID platformUserId);
}
