package dev.createserver.backend.identity;

import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "minecraft_identity")
public class MinecraftIdentity {
    @Id
    @Column(name = "minecraft_uuid")
    private UUID minecraftUuid;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "platform_user_id", nullable = false, unique = true)
    private PlatformUser platformUser;

    @Column(name = "current_name", nullable = false, length = 16)
    private String currentName;

    protected MinecraftIdentity() {}

    // Internal persistence primitive. This does not verify ownership of a Minecraft account.
    public static MinecraftIdentity attach(PlatformUser user, UUID minecraftUuid, String currentName) {
        MinecraftIdentity identity = new MinecraftIdentity();
        identity.platformUser = Objects.requireNonNull(user);
        identity.minecraftUuid = Objects.requireNonNull(minecraftUuid);
        identity.rename(currentName);
        return identity;
    }

    public void rename(String name) {
        if (name == null || name.isBlank() || name.length() > 16 || !name.equals(name.trim())) {
            throw new IllegalArgumentException("Minecraft display name must contain 1 to 16 characters without surrounding whitespace");
        }
        currentName = name;
    }

    public UUID getMinecraftUuid() { return minecraftUuid; }
    public UUID getPlatformUserId() { return platformUser.getId(); }
    public String getCurrentName() { return currentName; }
}
