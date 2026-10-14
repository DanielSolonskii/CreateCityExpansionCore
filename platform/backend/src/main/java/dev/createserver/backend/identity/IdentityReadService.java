package dev.createserver.backend.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IdentityReadService {
    private final PlatformUserRepository users;
    private final MinecraftIdentityRepository minecraftIdentities;

    public IdentityReadService(PlatformUserRepository users, MinecraftIdentityRepository minecraftIdentities) {
        this.users = users;
        this.minecraftIdentities = minecraftIdentities;
    }

    public Optional<PlatformUserView> findUser(UUID platformUserId) {
        return users.findById(platformUserId).map(user -> new PlatformUserView(
                user.getId(), user.getStatus(), user.getCreatedAt(), user.getUpdatedAt(),
                minecraftIdentities.findByPlatformUser_Id(platformUserId)
                        .map(this::view).orElse(null)));
    }

    public Optional<MinecraftIdentityView> findMinecraftIdentity(UUID minecraftUuid) {
        return minecraftIdentities.findById(minecraftUuid).map(this::view);
    }

    private MinecraftIdentityView view(MinecraftIdentity identity) {
        return new MinecraftIdentityView(identity.getPlatformUserId(),
                identity.getMinecraftUuid(), identity.getCurrentName());
    }

    public record PlatformUserView(UUID id, UserStatus status, Instant createdAt, Instant updatedAt,
                                   MinecraftIdentityView minecraftIdentity) {}
    public record MinecraftIdentityView(UUID platformUserId, UUID minecraftUuid, String currentName) {}
}
