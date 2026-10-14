package dev.createserver.backend.identity;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

abstract class IdentityDatabaseContract {
    @Autowired PlatformUserRepository users;
    @Autowired MinecraftIdentityRepository identities;
    @Autowired IdentityReadService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired Flyway flyway;
    @LocalServerPort int port;
    IdentityFixtures fixtures;

    @BeforeEach
    void fixtures() {
        fixtures = new IdentityFixtures(users, identities, transactions);
    }

    @AfterEach
    void cleanup() {
        fixtures.close();
    }

    @Test
    void migrationV3AndJpaValidationComplete() {
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("10");
        assertThat(jdbc.queryForObject(
                "SELECT schema_version FROM platform_infrastructure WHERE id = 1", Integer.class))
                .isEqualTo(10);
    }

    @Test
    void userHasIndependentUuidStatusAndPersistentTimestamps() {
        PlatformUser user = fixtures.user();
        var view = service.findUser(user.getId()).orElseThrow();
        assertThat(view.id()).isEqualTo(user.getId());
        assertThat(view.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(view.createdAt()).isNotNull();
        assertThat(view.updatedAt()).isEqualTo(view.createdAt());
        assertThat(view.minecraftIdentity()).isNull();

        transactions.executeWithoutResult(tx -> {
            PlatformUser managed = users.findById(user.getId()).orElseThrow();
            managed.changeStatus(UserStatus.DISABLED);
            users.flush();
        });
        var changed = service.findUser(user.getId()).orElseThrow();
        assertThat(changed.status()).isEqualTo(UserStatus.DISABLED);
        assertThat(changed.createdAt()).isEqualTo(view.createdAt());
        assertThat(changed.updatedAt()).isAfterOrEqualTo(view.updatedAt());
    }

    @Test
    void repositoriesAndReadServiceFindIdentityByStableUuidAfterRename() {
        PlatformUser user = fixtures.user();
        UUID minecraft = UUID.randomUUID();
        fixtures.minecraft(user, minecraft, "InitialName");
        transactions.executeWithoutResult(tx -> {
            MinecraftIdentity identity = identities.findById(minecraft).orElseThrow();
            identity.rename("UpdatedName");
            identities.flush();
        });
        var identity = service.findMinecraftIdentity(minecraft).orElseThrow();
        assertThat(identity.platformUserId()).isEqualTo(user.getId());
        assertThat(identity.minecraftUuid()).isEqualTo(minecraft);
        assertThat(identity.currentName()).isEqualTo("UpdatedName");
        assertThat(service.findUser(user.getId()).orElseThrow().minecraftIdentity()).isEqualTo(identity);
    }

    @Test
    void displayNamesAreNotUniqueIdentifiers() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        fixtures.minecraft(fixtures.user(), first, "SameName");
        fixtures.minecraft(fixtures.user(), second, "SameName");
        assertThat(service.findMinecraftIdentity(first)).isPresent();
        assertThat(service.findMinecraftIdentity(second)).isPresent();
        assertThat(service.findUser(UUID.randomUUID())).isEmpty();
        assertThat(service.findMinecraftIdentity(UUID.randomUUID())).isEmpty();
    }

    @Test
    void databaseRejectsDuplicateMinecraftUuidAcrossUsers() {
        PlatformUser first = fixtures.user();
        PlatformUser second = fixtures.user();
        UUID minecraft = UUID.randomUUID();
        fixtures.minecraft(first, minecraft, "First");
        reject(() -> jdbc.update(
                "INSERT INTO minecraft_identity (minecraft_uuid, platform_user_id, current_name) VALUES (?, ?, ?)",
                minecraft, second.getId(), "Second"));
    }

    @Test
    void databaseRejectsSecondMinecraftIdentityForOneUser() {
        PlatformUser user = fixtures.user();
        fixtures.minecraft(user, UUID.randomUUID(), "First");
        reject(() -> jdbc.update(
                "INSERT INTO minecraft_identity (minecraft_uuid, platform_user_id, current_name) VALUES (?, ?, ?)",
                UUID.randomUUID(), user.getId(), "Second"));
    }

    @Test
    void repositoryRejectsSecondIdentityAndRetainsOriginalAfterRollback() {
        PlatformUser user = fixtures.user();
        UUID original = UUID.randomUUID();
        fixtures.minecraft(user, original, "First");
        assertThatThrownBy(() -> fixtures.minecraft(user, UUID.randomUUID(), "Second"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.findUser(user.getId()).orElseThrow().minecraftIdentity().minecraftUuid())
                .isEqualTo(original);
    }

    @Test
    void databaseRejectsOrphanAndMissingUserReference() {
        reject(() -> jdbc.update(
                "INSERT INTO minecraft_identity (minecraft_uuid, platform_user_id, current_name) VALUES (?, ?, ?)",
                UUID.randomUUID(), UUID.randomUUID(), "Orphan"));
        reject(() -> jdbc.update(
                "INSERT INTO minecraft_identity (minecraft_uuid, platform_user_id, current_name) VALUES (?, NULL, ?)",
                UUID.randomUUID(), "Orphan"));
    }

    @Test
    void databaseRejectsInvalidStatusMissingTimestampsAndEmptyName() {
        PlatformUser user = fixtures.user();
        reject(() -> jdbc.update("UPDATE platform_user SET status = 'UNKNOWN' WHERE id = ?", user.getId()));
        reject(() -> jdbc.update("UPDATE platform_user SET status = NULL WHERE id = ?", user.getId()));
        reject(() -> jdbc.update("UPDATE platform_user SET created_at = NULL WHERE id = ?", user.getId()));
        reject(() -> jdbc.update("UPDATE platform_user SET updated_at = NULL WHERE id = ?", user.getId()));
        reject(() -> jdbc.update("UPDATE platform_user SET updated_at = created_at - INTERVAL '1' SECOND WHERE id = ?",
                user.getId()));
        reject(() -> jdbc.update(
                "INSERT INTO minecraft_identity (minecraft_uuid, platform_user_id, current_name) VALUES (?, ?, ?)",
                UUID.randomUUID(), user.getId(), "   "));
        reject(() -> jdbc.update(
                "INSERT INTO minecraft_identity (minecraft_uuid, platform_user_id, current_name) VALUES (?, ?, NULL)",
                UUID.randomUUID(), user.getId()));
    }

    @Test
    void userHttpResponseContainsIdentityAndIsoTimestamps() throws Exception {
        PlatformUser user = fixtures.user();
        UUID minecraft = UUID.randomUUID();
        fixtures.minecraft(user, minecraft, "ReadOnly");
        HttpResponse<String> response = request("GET", "/api/users/" + user.getId());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat((String) JsonPath.read(response.body(), "$.id")).isEqualTo(user.getId().toString());
        assertThat((String) JsonPath.read(response.body(), "$.status")).isEqualTo("ACTIVE");
        assertThat((String) JsonPath.read(response.body(), "$.minecraftIdentity.minecraftUuid"))
                .isEqualTo(minecraft.toString());
        assertThat(Instant.parse(JsonPath.read(response.body(), "$.createdAt"))).isNotNull();
        assertThat(Instant.parse(JsonPath.read(response.body(), "$.updatedAt"))).isNotNull();
    }

    @Test
    void userWithoutMinecraftIdentityIsReadable() throws Exception {
        PlatformUser user = fixtures.user();
        HttpResponse<String> response = request("GET", "/api/users/" + user.getId());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((Object) JsonPath.read(response.body(), "$.minecraftIdentity")).isNull();
    }

    @Test
    void minecraftHttpLookupUsesUuidAndReflectsRename() throws Exception {
        PlatformUser user = fixtures.user();
        UUID minecraft = UUID.randomUUID();
        fixtures.minecraft(user, minecraft, "Before");
        transactions.executeWithoutResult(tx -> identities.findById(minecraft).orElseThrow().rename("After"));
        HttpResponse<String> response = request("GET", "/api/identities/minecraft/" + minecraft);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.platformUserId")).isEqualTo(user.getId().toString());
        assertThat((String) JsonPath.read(response.body(), "$.minecraftUuid")).isEqualTo(minecraft.toString());
        assertThat((String) JsonPath.read(response.body(), "$.currentName")).isEqualTo("After");
    }

    @Test
    void missingRecordsReturn404AndInvalidUuidReturns400() throws Exception {
        assertThat(request("GET", "/api/users/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(request("GET", "/api/identities/minecraft/" + UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(request("GET", "/api/users/not-a-uuid").statusCode()).isEqualTo(400);
        assertThat(request("GET", "/api/identities/minecraft/1-1-1-1-1").statusCode()).isEqualTo(400);
    }

    @Test
    void noPublicCreationEndpointsExist() throws Exception {
        assertThat(request("POST", "/api/users").statusCode()).isEqualTo(404);
        assertThat(request("POST", "/api/users/" + UUID.randomUUID()).statusCode()).isEqualTo(405);
        assertThat(request("POST", "/api/identities/minecraft/" + UUID.randomUUID()).statusCode()).isEqualTo(405);
    }

    private void reject(Runnable mutation) {
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> mutation.run()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private HttpResponse<String> request(String method, String path) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("X-Bot-Service-Secret", "test-only-service-secret-at-least-32-characters")
                    .method(method, HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
