package dev.createserver.backend.identity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;

// Test-only creation/cleanup helper; never included in the production JAR or HTTP API.
final class IdentityFixtures implements AutoCloseable {
    private final PlatformUserRepository users;
    private final MinecraftIdentityRepository identities;
    private final TransactionTemplate transactions;
    private final List<UUID> createdUsers = new ArrayList<>();

    IdentityFixtures(PlatformUserRepository users, MinecraftIdentityRepository identities,
                     TransactionTemplate transactions) {
        this.users = users;
        this.identities = identities;
        this.transactions = transactions;
    }

    PlatformUser user() {
        PlatformUser user = transactions.execute(tx -> users.saveAndFlush(PlatformUser.create()));
        createdUsers.add(user.getId());
        return user;
    }

    MinecraftIdentity minecraft(PlatformUser user, UUID uuid, String name) {
        return transactions.execute(tx -> identities.saveAndFlush(
                MinecraftIdentity.attach(users.getReferenceById(user.getId()), uuid, name)));
    }

    @Override
    public void close() {
        transactions.executeWithoutResult(tx -> {
            for (UUID id : createdUsers) {
                identities.findByPlatformUser_Id(id).ifPresent(identities::delete);
            }
            identities.flush();
            createdUsers.forEach(users::deleteById);
            users.flush();
        });
    }
}
