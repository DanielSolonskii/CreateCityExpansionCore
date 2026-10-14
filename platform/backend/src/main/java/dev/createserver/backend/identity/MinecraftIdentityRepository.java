package dev.createserver.backend.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MinecraftIdentityRepository extends JpaRepository<MinecraftIdentity, UUID> {
    Optional<MinecraftIdentity> findByPlatformUser_Id(UUID platformUserId);
}
