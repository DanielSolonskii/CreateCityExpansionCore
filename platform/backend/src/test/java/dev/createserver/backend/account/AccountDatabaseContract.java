package dev.createserver.backend.account;

import com.jayway.jsonpath.JsonPath;
import dev.createserver.backend.identity.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

abstract class AccountDatabaseContract {
    static final String SECRET = "test-only-service-secret-at-least-32-characters";
    static final String PASSWORD = "test-password-only-1234";
    @Autowired AccountService service;
    @Autowired AccountCredentialsRepository credentials;
    @Autowired PlatformUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired AlphaKeyService alphaKeys;
    @Autowired PhoneVerificationService phone;
    @Autowired DeviceService devices;
    @Autowired AccountSettings settings;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @LocalServerPort int port;
    final List<UUID> fixtures = new ArrayList<>();
    final List<UUID> keyFixtures = new ArrayList<>();

    @AfterEach
    void cleanup() {
        transactions.executeWithoutResult(tx -> {
            for (UUID key : keyFixtures) jdbc.update("DELETE FROM alpha_access_key WHERE id = ?", key);
            for (UUID user : fixtures) {
                jdbc.update("DELETE FROM registration_session WHERE platform_user_id = ?", user);
                jdbc.update("DELETE FROM telegram_identity WHERE platform_user_id = ?", user);
                jdbc.update("DELETE FROM account_credentials WHERE platform_user_id = ?", user);
                users.deleteById(user);
            }
            users.flush();
        });
    }

    @Test
    void createsPendingAccountWithSaltedPasswordAndIndependentHashedSecrets() {
        String username = username();
        var started = register(username);
        var credential = credentials.findByUsername(username).orElseThrow();
        assertThat(credential.getPasswordHash()).startsWith("{bcrypt}$2a$12$").doesNotContain(PASSWORD);
        assertThat(encoder.matches(PASSWORD, credential.getPasswordHash())).isTrue();
        assertThat(encoder.encode(PASSWORD)).isNotEqualTo(credential.getPasswordHash());
        assertThat(users.findById(credential.getPlatformUserId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.PENDING_VERIFICATION);
        String stored = jdbc.queryForObject("SELECT verification_hash FROM registration_session WHERE id = ?",
                String.class, started.registrationId());
        assertThat(stored).isEqualTo(Secrets.hash(token(started))).isNotEqualTo(token(started));
        assertThat(started.pollingToken()).isNotEqualTo(token(started));
        assertThat(service.status(started.registrationId(), started.pollingToken()).state())
                .isEqualTo(RegistrationSession.State.PENDING);
        assertThat(started.toString()).doesNotContain(started.pollingToken(), token(started));
    }

    @Test
    void duplicateUsernameIsCaseInsensitiveAndRollsBack() {
        String username = username();
        register(username);
        fails(409, () -> service.register(username.toUpperCase(Locale.ROOT), PASSWORD, key()));
    }

    @Test
    void rejectsInvalidUsernameAndPasswordLimits() {
        fails(400, () -> service.register("bad name", PASSWORD, key()));
        fails(400, () -> service.register("ab", PASSWORD, key()));
        fails(400, () -> service.register(username(), "short", key()));
        fails(400, () -> service.register(username(), "я".repeat(37), key()));
        fails(400, () -> service.register(username(), "x".repeat(73), key()));
    }

    @Test
    void onlyTelegramVerifiedActiveAccountCanLoginAndWrongPasswordIsGeneric() {
        String username = username();
        var started = register(username);
        fails(401, () -> service.login(username, PASSWORD));
        long telegram = telegramId();
        var confirmed = service.confirm(token(started), telegram, "DisplayOnly");
        assertThat(users.findById(confirmed.platformUserId()).orElseThrow().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(service.login(username.toUpperCase(Locale.ROOT), PASSWORD).platformUserId())
                .isEqualTo(confirmed.platformUserId());
        fails(401, () -> service.login(username, "wrong-password-123"));
        assertThat(service.linkedUser(telegram).platformUserId()).isEqualTo(confirmed.platformUserId());
        var status = service.status(started.registrationId(), started.pollingToken());
        assertThat(status.state()).isEqualTo(RegistrationSession.State.COMPLETED);
        assertThat(status.platformUserId()).isEqualTo(confirmed.platformUserId());
    }

    @Test
    void disabledAccountAndActiveWithoutTelegramCannotLogin() {
        String username = username();
        var started = register(username);
        UUID id = credentials.findByUsername(username).orElseThrow().getPlatformUserId();
        transactions.executeWithoutResult(tx -> users.findById(id).orElseThrow().changeStatus(UserStatus.ACTIVE));
        fails(401, () -> service.login(username, PASSWORD));
        transactions.executeWithoutResult(tx -> users.findById(id).orElseThrow().changeStatus(UserStatus.PENDING_VERIFICATION));
        service.confirm(token(started), telegramId(), null);
        transactions.executeWithoutResult(tx -> users.findById(id).orElseThrow().changeStatus(UserStatus.DISABLED));
        fails(401, () -> service.login(username, PASSWORD));
    }

    @Test
    void invalidExpiredAndReusedVerificationTokensAreRejected() {
        var expired = register(username());
        fails(400, () -> service.confirm("invalid", telegramId(), null));
        fails(400, () -> service.confirm(Secrets.token(), telegramId(), null));
        jdbc.update("UPDATE registration_session SET created_at = CURRENT_TIMESTAMP - INTERVAL '2' HOUR, " +
                "expires_at = CURRENT_TIMESTAMP - INTERVAL '1' HOUR WHERE id = ?", expired.registrationId());
        fails(400, () -> service.confirm(token(expired), telegramId(), null));
        assertThat(service.status(expired.registrationId(), expired.pollingToken()).state())
                .isEqualTo(RegistrationSession.State.EXPIRED);
        var complete = register(username());
        service.confirm(token(complete), telegramId(), null);
        fails(400, () -> service.confirm(token(complete), telegramId(), null));
        jdbc.update("UPDATE registration_session SET created_at = CURRENT_TIMESTAMP - INTERVAL '2' HOUR, " +
                "expires_at = CURRENT_TIMESTAMP - INTERVAL '1' HOUR WHERE id = ?", complete.registrationId());
        fails(410, () -> service.status(complete.registrationId(), complete.pollingToken()));
    }

    @Test
    void pollingSecretCannotConfirmAndVerificationSecretCannotPollSomeoneElse() {
        var first = register(username());
        var second = register(username());
        fails(404, () -> service.status(first.registrationId(), token(first)));
        fails(404, () -> service.status(second.registrationId(), first.pollingToken()));
        fails(404, () -> service.status(first.registrationId(), "invalid"));
        fails(400, () -> service.confirm(first.pollingToken(), telegramId(), null));
        assertThat(service.status(first.registrationId(), first.pollingToken()).state())
                .isEqualTo(RegistrationSession.State.PENDING);
    }

    @Test
    void telegramIdCannotActivateTwoAccountsAndRollbackLeavesSecondPending() {
        var first = register(username());
        var second = register(username());
        long id = telegramId();
        service.confirm(token(first), id, null);
        fails(409, () -> service.confirm(token(second), id, "SameName"));
        assertThat(service.status(second.registrationId(), second.pollingToken()).state())
                .isEqualTo(RegistrationSession.State.PENDING);
        service.confirm(token(second), telegramId(), "SameName");
    }

    @Test
    void concurrentConfirmationOfOneTokenHasExactlyOneWinner() throws Exception {
        var started = register(username());
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> attempt = () -> {
                try { service.confirm(token(started), telegramId(), null); return true; }
                catch (AccountFailure failure) { assertThat(failure.status()).isEqualTo(400); return false; }
            };
            var results = pool.invokeAll(List.of(attempt, attempt));
            int winners = 0;
            for (var result : results) { if (result.get()) { winners++; } }
            assertThat(winners).isEqualTo(1);
        }
    }

    @Test
    void databaseConstraintsProtectUsernameTelegramAndRegistrationLifecycle() {
        var first = register(username());
        var second = register(username());
        UUID user1 = jdbc.queryForObject("SELECT platform_user_id FROM registration_session WHERE id = ?",
                UUID.class, first.registrationId());
        UUID user2 = jdbc.queryForObject("SELECT platform_user_id FROM registration_session WHERE id = ?",
                UUID.class, second.registrationId());
        reject(() -> jdbc.update("UPDATE account_credentials SET username = " +
                "(SELECT username FROM account_credentials WHERE platform_user_id = ?) WHERE platform_user_id = ?",
                user1, user2));
        reject(() -> jdbc.update("UPDATE account_credentials SET password_hash = 'plaintext' WHERE platform_user_id = ?", user1));
        reject(() -> jdbc.update("UPDATE registration_session SET state = 'COMPLETED' WHERE id = ?", first.registrationId()));
        reject(() -> jdbc.update("UPDATE registration_session SET expires_at = created_at WHERE id = ?", first.registrationId()));
        reject(() -> jdbc.update("UPDATE registration_session SET polling_hash = verification_hash WHERE id = ?", second.registrationId()));
        long telegram = telegramId();
        service.confirm(token(first), telegram, null);
        reject(() -> jdbc.update("INSERT INTO telegram_identity (telegram_user_id, platform_user_id, verified_at) " +
                "VALUES (?, ?, CURRENT_TIMESTAMP)", telegram, user2));
        reject(() -> jdbc.update("INSERT INTO telegram_identity (telegram_user_id, platform_user_id, verified_at) " +
                "VALUES (?, ?, CURRENT_TIMESTAMP)", telegramId(), user1));
        reject(() -> jdbc.update("INSERT INTO telegram_identity (telegram_user_id, platform_user_id, verified_at) " +
                "VALUES (0, ?, CURRENT_TIMESTAMP)", user2));
    }

    @Test
    void httpRegistrationStatusConfirmationLoginAndServiceAuthentication() throws Exception {
        String username = username();
        var response = http("POST", "/api/registrations",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\",\"alphaKey\":\"" + key() + "\"}", null, null);
        assertThat(response.statusCode()).isEqualTo(201);
        UUID user = credentials.findByUsername(username).orElseThrow().getPlatformUserId();
        fixtures.add(user);
        String session = JsonPath.read(response.body(), "$.registrationId");
        String polling = JsonPath.read(response.body(), "$.pollingToken");
        String link = JsonPath.read(response.body(), "$.telegramDeepLink");
        String verification = URI.create(link).getQuery().substring(6);
        String confirmBody = "{\"registrationToken\":\"" + verification + "\",\"telegramUserId\":" + telegramId() + "}";
        assertThat(http("POST", "/api/internal/telegram/confirm", confirmBody, null, null).statusCode()).isEqualTo(401);
        assertThat(http("POST", "/api/internal/telegram/confirm", confirmBody, null, "wrong").statusCode()).isEqualTo(401);
        assertThat(http("GET", "/api/registrations/" + session, null, "Bearer " + verification, null).statusCode()).isEqualTo(404);
        assertThat(http("GET", "/api/registrations/" + session, null, "Bearer " + polling, null).body()).contains("PENDING");
        assertThat(http("POST", "/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null, null).statusCode()).isEqualTo(401);
        assertThat(http("POST", "/api/internal/telegram/confirm", confirmBody, null, SECRET).statusCode()).isEqualTo(400);
        var preview = http("POST", "/api/internal/telegram/prepare", confirmBody, null, SECRET);
        String ticket = JsonPath.read(preview.body(), "$.confirmationToken");
        long author = ((Number) JsonPath.read(confirmBody, "$.telegramUserId")).longValue();
        assertThat(http("POST", "/api/internal/telegram/accept",
            "{\"confirmationToken\":\"" + ticket + "\",\"telegramUserId\":" + author + "}", null, SECRET).statusCode()).isEqualTo(200);
        assertThat(http("GET", "/api/registrations/" + session, null, "Bearer " + polling, null).body()).contains("COMPLETED");
        var login = http("POST", "/api/auth/login",
                "{\"username\":\"" + username + "\",\"password\":\"" + PASSWORD + "\"}", null, null);
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("AUTHENTICATED").doesNotContain("token", "password", "hash");
        assertThat(login.headers().firstValue("Cache-Control")).contains("no-store");
    }

    @Test
    void loginBruteForceIsBoundedPerUsername() {
        String username = username();
        for (int i = 0; i < 5; i++) { fails(401, () -> service.login(username, PASSWORD)); }
        fails(429, () -> service.login(username, PASSWORD));
    }

    @Test
    void alphaGenerationStoresOnlyUniqueHashesAndValidatesCountAndExpiry() {
        var generated = alphaKeys.generate(3, java.time.Instant.now().plusSeconds(3600));
        generated.forEach(k -> keyFixtures.add(k.id()));
        assertThat(generated.stream().map(AlphaKeyService.Generated::plaintext).distinct().count()).isEqualTo(3);
        for (var item : generated) {
            assertThat(Secrets.isToken(item.plaintext())).isTrue();
            assertThat(item.toString()).doesNotContain(item.plaintext());
            assertThat(jdbc.queryForObject("SELECT key_hash FROM alpha_access_key WHERE id = ?", String.class, item.id()))
                    .isEqualTo(Secrets.hash(item.plaintext())).isNotEqualTo(item.plaintext());
        }
        assertThatThrownBy(() -> alphaKeys.generate(0, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> alphaKeys.generate(10001, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> alphaKeys.generate(1, java.time.Instant.now().minusSeconds(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyActiveAccountWithNoAlphaKeyStillLogsIn() {
        String name = username();
        UUID user = transactions.execute(tx -> {
            var existing = users.saveAndFlush(PlatformUser.create());
            credentials.saveAndFlush(new AccountCredentials(existing.getId(), name, encoder.encode(PASSWORD)));
            jdbc.update("INSERT INTO telegram_identity (telegram_user_id, platform_user_id, verified_at) VALUES (?, ?, CURRENT_TIMESTAMP)",
                    telegramId(), existing.getId());
            return existing.getId();
        });
        fixtures.add(user);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alpha_access_key WHERE platform_user_id = ?", Integer.class, user)).isZero();
        assertThat(service.login(name, PASSWORD).platformUserId()).isEqualTo(user);
    }

    @Test
    void usedAlphaKeyIsNotReturnedWhenVerificationExpires() {
        String key = key();
        String username = username();
        var started = service.register(username, PASSWORD, key);
        UUID user = credentials.findByUsername(username).orElseThrow().getPlatformUserId();
        fixtures.add(user);
        assertThat(jdbc.queryForObject("SELECT platform_user_id FROM alpha_access_key WHERE key_hash = ?", UUID.class, Secrets.hash(key)))
                .isEqualTo(user);
        assertThat(jdbc.queryForObject("SELECT state FROM alpha_access_key WHERE key_hash = ?", String.class, Secrets.hash(key)))
                .isEqualTo("USED");
        jdbc.update("UPDATE registration_session SET created_at = CURRENT_TIMESTAMP - INTERVAL '2' HOUR, " +
                "expires_at = CURRENT_TIMESTAMP - INTERVAL '1' HOUR WHERE id = ?", started.registrationId());
        assertThat(service.status(started.registrationId(), started.pollingToken()).state()).isEqualTo(RegistrationSession.State.EXPIRED);
        fails(400, () -> service.register(username(), PASSWORD, key));
    }

    @Test
    void invalidRevokedExpiredKeysAllHaveTheSameFailureAndCreateNothing() {
        String revoked = key();
        String expired = key();
        jdbc.update("UPDATE alpha_access_key SET state = 'REVOKED' WHERE key_hash = ?", Secrets.hash(revoked));
        jdbc.update("UPDATE alpha_access_key SET created_at = CURRENT_TIMESTAMP - INTERVAL '2' HOUR, " +
                "expires_at = CURRENT_TIMESTAMP - INTERVAL '1' HOUR WHERE key_hash = ?", Secrets.hash(expired));
        for (String value : Arrays.asList(null, "", "wrong", Secrets.token(), revoked, expired)) {
            String name = username();
            assertThatThrownBy(() -> service.register(name, PASSWORD, value)).isInstanceOfSatisfying(AccountFailure.class, e -> {
                assertThat(e.status()).isEqualTo(400);
                assertThat(e.getMessage()).isEqualTo("INVALID_ALPHA_KEY");
            });
            assertThat(credentials.findByUsername(name)).isEmpty();
        }
    }

    @Test
    void registrationAndConsumedKeyRollbackTogether() {
        String key = key();
        String name = username();
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> {
            service.register(name, PASSWORD, key);
            throw new IllegalStateException("test rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(credentials.findByUsername(name)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT state FROM alpha_access_key WHERE key_hash = ?", String.class, Secrets.hash(key)))
                .isEqualTo("UNUSED");
        service.register(name, PASSWORD, key);
        fixtures.add(credentials.findByUsername(name).orElseThrow().getPlatformUserId());
    }

    @Test
    void simultaneousUseOfOneAlphaKeyHasExactlyOneAccount() throws Exception {
        String key = key();
        List<String> names = List.of(username(), username());
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<Boolean>> results = new ArrayList<>();
            for (String name : names) results.add(pool.submit(() -> {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test start timeout");
                try { service.register(name, PASSWORD, key); return true; }
                catch (AccountFailure e) { assertThat(e.getMessage()).isEqualTo("INVALID_ALPHA_KEY"); return false; }
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            int winners = 0;
            try {
                for (var result : results) if (result.get(30, TimeUnit.SECONDS)) winners++;
            } finally {
                for (String name : names) credentials.findByUsername(name).ifPresent(c -> fixtures.add(c.getPlatformUserId()));
            }
            assertThat(winners).isEqualTo(1);
            assertThat(fixtures).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM registration_session WHERE platform_user_id = ?",
                    Integer.class, fixtures.getFirst())).isEqualTo(1);
        }
    }

    @Test
    void alphaDatabaseConstraintsProtectHashStateUsageAndAccountUniqueness() {
        String first = key();
        String second = key();
        reject(() -> jdbc.update("UPDATE alpha_access_key SET key_hash = ? WHERE key_hash = ?", Secrets.hash(first), Secrets.hash(second)));
        reject(() -> jdbc.update("UPDATE alpha_access_key SET state = 'USED' WHERE key_hash = ?", Secrets.hash(first)));
        reject(() -> jdbc.update("UPDATE alpha_access_key SET state = 'INVALID' WHERE key_hash = ?", Secrets.hash(first)));
        reject(() -> jdbc.update("UPDATE alpha_access_key SET expires_at = created_at WHERE key_hash = ?", Secrets.hash(first)));
        reject(() -> jdbc.update("UPDATE alpha_access_key SET key_hash = 'plaintext' WHERE key_hash = ?", Secrets.hash(first)));
        String name = username();
        service.register(name, PASSWORD, first);
        UUID user = credentials.findByUsername(name).orElseThrow().getPlatformUserId();
        fixtures.add(user);
        reject(() -> jdbc.update("UPDATE alpha_access_key SET state = 'USED', used_at = CURRENT_TIMESTAMP, platform_user_id = ? WHERE key_hash = ?",
                user, Secrets.hash(second)));
        reject(() -> jdbc.update("UPDATE alpha_access_key SET state = 'UNUSED' WHERE key_hash = ?", Secrets.hash(first)));
    }

    @Test
    void httpRequiresAlphaKeyAndReturnsNoDetailsForDuplicateUse() throws Exception {
        String key = key();
        String name = username();
        service.register(name, PASSWORD, key);
        fixtures.add(credentials.findByUsername(name).orElseThrow().getPlatformUserId());
        for (String suffix : List.of("", ",\"alphaKey\":\"wrong\"", ",\"alphaKey\":\"" + key + "\"")) {
            String body = "{\"username\":\"" + username() + "\",\"password\":\"" + PASSWORD + "\"" + suffix + "}";
            var response = http("POST", "/api/registrations", body, null, null);
            assertThat(response.statusCode()).isEqualTo(400);
            assertThat(response.body()).isEqualTo("{\"code\":\"INVALID_ALPHA_KEY\"}");
            assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
            assertThat(response.body()).doesNotContain(key, "USED", "hash", name);
        }
    }

    @Test
    void phoneCodeAndQrOnlyPrepareExplicitSenderBoundConfirmation() {
        var registration = register(username());
        long id = telegramId();
        assertThat(registration.verificationCode()).matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5}-[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5}");
        String hash = jdbc.queryForObject("SELECT code_hash FROM registration_session WHERE id = ?", String.class, registration.registrationId());
        assertThat(hash).isEqualTo(Secrets.keyedHash("registration-code-v1", Secrets.normalizeCode(registration.verificationCode()), SECRET))
            .isNotEqualTo(Secrets.hash(registration.verificationCode())).doesNotContain(registration.verificationCode());
        var preview = phone.prepare(null, registration.verificationCode().toLowerCase(Locale.ROOT).replace("-", ""), id);
        assertThat(service.status(registration.registrationId(), registration.pollingToken()).state()).isEqualTo(RegistrationSession.State.PENDING);
        assertThat(preview.toString()).doesNotContain(preview.confirmationToken());
        assertThat(jdbc.queryForObject("SELECT token_hash FROM telegram_confirmation WHERE registration_id = ?", String.class,
            registration.registrationId())).isEqualTo(Secrets.hash(preview.confirmationToken()));
        fails(400, () -> phone.confirm(preview.confirmationToken(), id + 1, null));
        phone.confirm(preview.confirmationToken(), id, null);
        assertThat(service.status(registration.registrationId(), registration.pollingToken()).state()).isEqualTo(RegistrationSession.State.COMPLETED);
        fails(400, () -> phone.confirm(preview.confirmationToken(), id, null));
        fails(400, () -> phone.prepare(null, registration.verificationCode(), id));
        fails(400, () -> phone.prepare(token(registration), null, id));
    }

    @Test
    void phoneExpiryAndPollingIsolationAndTicketExpiry() {
        var registration = register(username());
        long id = telegramId();
        fails(400, () -> phone.prepare(registration.pollingToken(), null, id));
        var preview = phone.prepare(token(registration), null, id);
        fails(404, () -> service.status(registration.registrationId(), preview.confirmationToken()));
        fails(404, () -> service.status(registration.registrationId(), token(registration)));
        jdbc.update("UPDATE telegram_confirmation SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1' SECOND WHERE registration_id = ?", registration.registrationId());
        fails(400, () -> phone.confirm(preview.confirmationToken(), id, null));
        jdbc.update("UPDATE registration_session SET created_at = CURRENT_TIMESTAMP - INTERVAL '2' HOUR, expires_at = CURRENT_TIMESTAMP - INTERVAL '1' HOUR WHERE id = ?", registration.registrationId());
        fails(400, () -> phone.prepare(null, registration.verificationCode(), id));
        fails(400, () -> phone.prepare(token(registration), null, id));
    }

    @Test
    void codeRateLimitsPersistDespiteRejectedTransactionsAndAcrossLimiterInstances() {
        long id = telegramId();
        for (int i = 0; i < 5; i++) fails(400, () -> phone.prepare(null, "23456-23456", id));
        fails(429, () -> phone.prepare(null, "23456-23456", id));
        var hash = Secrets.keyedHash("verification-rate-v1", "code:" + id, SECRET);
        assertThat(jdbc.queryForObject("SELECT attempts FROM verification_rate_bucket WHERE bucket_key = ?", Integer.class, hash)).isEqualTo(5);
        fails(429, () -> new VerificationAttempts(jdbc, transactionManager, java.time.Clock.systemUTC(), settings).check(id, true));
        jdbc.update("UPDATE verification_rate_bucket SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1' SECOND WHERE bucket_key = ?", hash);
        fails(400, () -> phone.prepare(null, "incorrect", id));
        assertThat(jdbc.queryForObject("SELECT attempts FROM verification_rate_bucket WHERE bucket_key = ?", Integer.class, hash)).isEqualTo(1);
    }

    @Test
    void restartRotatesAllProofsWithoutNewAccountOrRefundAndIsBounded() {
        var first = register(username());
        long id = telegramId();
        var oldButton = phone.prepare(token(first), null, id);
        fails(404, () -> phone.restart(first.registrationId(), token(first)));
        jdbc.update("UPDATE registration_session SET created_at = CURRENT_TIMESTAMP - INTERVAL '2' HOUR, expires_at = CURRENT_TIMESTAMP - INTERVAL '1' HOUR WHERE id = ?", first.registrationId());
        assertThat(service.status(first.registrationId(), first.pollingToken()).state()).isEqualTo(RegistrationSession.State.EXPIRED);
        var next = phone.restart(first.registrationId(), first.pollingToken());
        assertThat(next.registrationId()).isEqualTo(first.registrationId());
        assertThat(next.pollingToken()).isEqualTo(first.pollingToken());
        assertThat(service.status(first.registrationId(), first.pollingToken()).state()).isEqualTo(RegistrationSession.State.PENDING);
        fails(400, () -> phone.prepare(token(first), null, id));
        fails(400, () -> phone.prepare(null, first.verificationCode(), id));
        fails(400, () -> phone.confirm(oldButton.confirmationToken(), id, null));
        var third = phone.restart(next.registrationId(), next.pollingToken());
        var fourth = phone.restart(third.registrationId(), third.pollingToken());
        fails(409, () -> phone.restart(fourth.registrationId(), fourth.pollingToken()));
        assertThat(jdbc.queryForObject("SELECT state FROM alpha_access_key WHERE platform_user_id = ?", String.class, fixtures.getLast())).isEqualTo("USED");
        phone.confirm(phone.prepare(null, fourth.verificationCode(), id).confirmationToken(), id, null);
        fails(409, () -> phone.restart(fourth.registrationId(), fourth.pollingToken()));
    }

    @Test
    void duplicateTelegramAndConcurrentPhoneConfirmationRemainAtomic() throws Exception {
        var first = register(username()); var second = register(username());
        long id = telegramId();
        var one = phone.prepare(null, first.verificationCode(), id);
        var two = phone.prepare(token(second), null, id);
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = List.of(one, two).stream().map(proof -> executor.submit(() -> {
                gate.await();
                try { phone.confirm(proof.confirmationToken(), id, null); return true; }
                catch (AccountFailure | DataIntegrityViolationException expected) { return false; }
            })).toList();
            gate.countDown();
            int success = 0;
            for (var future : futures) if (future.get(30, TimeUnit.SECONDS)) success++;
            assertThat(success).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM telegram_identity WHERE telegram_user_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(List.of(service.status(first.registrationId(), first.pollingToken()).state(),
            service.status(second.registrationId(), second.pollingToken()).state())).containsExactlyInAnyOrder(
                RegistrationSession.State.COMPLETED, RegistrationSession.State.PENDING);
    }

    @Test
    void concurrentSameRegistrationDifferentPhoneButtonsOnlyOneSucceeds() throws Exception {
        var registration = register(username());
        long first = telegramId(), second = telegramId();
        var one = phone.prepare(token(registration), null, first);
        var two = phone.prepare(null, registration.verificationCode(), second);
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = List.of(executor.submit(() -> { gate.await(); try { phone.confirm(one.confirmationToken(), first, null); return true; } catch (AccountFailure e) { return false; } }),
                executor.submit(() -> { gate.await(); try { phone.confirm(two.confirmationToken(), second, null); return true; } catch (AccountFailure e) { return false; } }));
            gate.countDown();
            assertThat(results.get(0).get(30, TimeUnit.SECONDS) ^ results.get(1).get(30, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentReplayOfTheSameButtonHasExactlyOneWinner() throws Exception {
        var registration = register(username());
        long id = telegramId();
        var proof = phone.prepare(null, registration.verificationCode(), id);
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = java.util.stream.IntStream.range(0, 2).mapToObj(n -> executor.submit(() -> {
                gate.await();
                try { phone.confirm(proof.confirmationToken(), id, null); return true; }
                catch (AccountFailure e) { return false; }
            })).toList();
            gate.countDown();
            assertThat(results.get(0).get(30, TimeUnit.SECONDS) ^ results.get(1).get(30, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(service.status(registration.registrationId(), registration.pollingToken()).state()).isEqualTo(RegistrationSession.State.COMPLETED);
    }

    @Test
    void phoneDbConstraintsAndLegacyPendingSessionCompatibility() {
        var first = register(username()); var second = register(username());
        String hash = jdbc.queryForObject("SELECT code_hash FROM registration_session WHERE id = ?", String.class, first.registrationId());
        reject(() -> jdbc.update("UPDATE registration_session SET code_hash = ? WHERE id = ?", hash, second.registrationId()));
        reject(() -> jdbc.update("UPDATE registration_session SET code_hash = 'plaintext' WHERE id = ?", first.registrationId()));
        reject(() -> jdbc.update("UPDATE registration_session SET restart_count = 4 WHERE id = ?", first.registrationId()));
        jdbc.update("UPDATE registration_session SET code_hash = NULL WHERE id = ?", first.registrationId());
        var legacy = phone.prepare(token(first), null, telegramId());
        assertThat(legacy.username()).isNotBlank();
        var updated = phone.restart(first.registrationId(), first.pollingToken());
        assertThat(updated.verificationCode()).isNotBlank();
        var preview = phone.prepare(null, second.verificationCode(), telegramId());
        reject(() -> jdbc.update("UPDATE telegram_confirmation SET telegram_user_id = 0 WHERE token_hash = ?", Secrets.hash(preview.confirmationToken())));
    }

    @Test
    void legacyConfirmIsRestrictedToPrePhoneSessionsAndRecoveryHasAnAbsoluteDeadline() {
        var current = register(username());
        fails(400, () -> service.confirmLegacy(token(current), telegramId(), null));
        jdbc.update("UPDATE registration_session SET code_hash = NULL WHERE id = ?", current.registrationId());
        service.confirmLegacy(token(current), telegramId(), null);
        assertThat(service.status(current.registrationId(), current.pollingToken()).state()).isEqualTo(RegistrationSession.State.COMPLETED);
        var old = register(username());
        jdbc.update("UPDATE registration_session SET created_at = CURRENT_TIMESTAMP - INTERVAL '2' DAY, expires_at = CURRENT_TIMESTAMP - INTERVAL '1' DAY WHERE id = ?", old.registrationId());
        fails(409, () -> phone.restart(old.registrationId(), old.pollingToken()));
    }

    @Test
    void concurrentWrongCodesCannotExceedDurableAttemptLimit() throws Exception {
        long id = telegramId();
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var results = java.util.stream.IntStream.range(0, 10).mapToObj(n -> executor.submit(() -> {
                gate.await();
                try { phone.prepare(null, "invalid", id); return 200; }
                catch (AccountFailure e) { return e.status(); }
            })).toList();
            gate.countDown();
            List<Integer> codes = new ArrayList<>();
            for (var future : results) codes.add(future.get(30, TimeUnit.SECONDS));
            assertThat(codes.stream().filter(code -> code == 400).count()).isEqualTo(5);
            assertThat(codes.stream().filter(code -> code == 429).count()).isEqualTo(5);
        }
    }

    @Test
    void phoneHttpEndpointsRequireBotAuthenticationAndPollingCapability() throws Exception {
        var registration = register(username());
        long id = telegramId();
        String body = "{\"verificationCode\":\"" + registration.verificationCode() + "\",\"telegramUserId\":" + id + "}";
        assertThat(http("POST", "/api/internal/telegram/prepare", body, null, null).statusCode()).isEqualTo(401);
        var preview = http("POST", "/api/internal/telegram/prepare", body, null, SECRET);
        assertThat(preview.statusCode()).isEqualTo(200);
        assertThat(preview.headers().firstValue("Cache-Control")).contains("no-store");
        String ticket = JsonPath.read(preview.body(), "$.confirmationToken");
        assertThat(http("POST", "/api/registrations/" + registration.registrationId() + "/restart", null, "Bearer " + token(registration), null).statusCode()).isEqualTo(404);
        var result = http("POST", "/api/internal/telegram/accept", "{\"confirmationToken\":\"" + ticket +
            "\",\"telegramUserId\":" + id + "}", null, SECRET);
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(http("POST", "/api/internal/telegram/accept", "{\"confirmationToken\":\"" + ticket +
            "\",\"telegramUserId\":" + id + "}", null, SECRET).statusCode()).isEqualTo(400);
    }

    @Test
    void telegramAccountReadUsesNumericBindingAndReturnsOnlySafeFields() throws Exception {
        String username = username();
        var started = register(username);
        long id = telegramId();
        fails(404, () -> service.linkedUser(id));
        fails(400, () -> service.linkedUser(0));
        var confirmed = service.confirm(token(started), id, "NotThePlatformUsername");
        var linked = service.linkedUser(id);
        assertThat(linked.platformUserId()).isEqualTo(confirmed.platformUserId());
        assertThat(linked.username()).isEqualTo(username);
        assertThat(linked.status()).isEqualTo(UserStatus.ACTIVE);
        String path = "/api/internal/telegram/users/" + id;
        assertThat(http("GET", path, null, null, null).statusCode()).isEqualTo(401);
        assertThat(http("GET", path, null, null, "wrong").statusCode()).isEqualTo(401);
        var response = http("GET", path, null, null, SECRET);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        Map<String, Object> body = JsonPath.read(response.body(), "$");
        assertThat(body).containsOnlyKeys("platformUserId", "username", "status");
        assertThat(body.get("username")).isEqualTo(username);
        assertThat(response.body()).doesNotContain(PASSWORD, "passwordHash", "NotThePlatformUsername");
        transactions.executeWithoutResult(tx -> users.findById(confirmed.platformUserId()).orElseThrow().changeStatus(UserStatus.DISABLED));
        assertThat(service.linkedUser(id).status()).isEqualTo(UserStatus.DISABLED);
        assertThat(http("GET", "/api/internal/telegram/users/" + telegramId(), null, null, SECRET).statusCode()).isEqualTo(404);
    }

    @Test
    void trustedDeviceRequiresOwnersExplicitApprovalAndAllSecretsAreIsolated() throws Exception {
        String name=username(); var registration=register(name); long telegram=telegramId();
        service.confirm(token(registration),telegram,null);
        var started=devices.begin(name,PASSWORD,"Test Windows PC");
        assertThat(started.toString()).doesNotContain(started.deviceCredential(),started.pollingToken());
        String proof=URI.create(started.telegramDeepLink()).getQuery().substring(8);
        assertThat(devices.poll(started.deviceId(),started.pollingToken()).state()).isEqualTo("PENDING");
        fails(401,()->devices.authenticate(started.deviceId(),started.deviceCredential()));
        fails(404,()->devices.poll(started.deviceId(),started.deviceCredential()));
        fails(404,()->devices.preview(proof,telegramId()));
        fails(404,()->devices.approve(started.deviceId(),telegramId()));
        assertThat(devices.preview(proof,telegram).username()).isEqualTo(name);
        var stored=jdbc.queryForMap("SELECT credential_hash,polling_hash,verification_hash FROM trusted_device WHERE id=?",started.deviceId());
        assertThat(stored.values()).doesNotContain(started.deviceCredential(),started.pollingToken(),proof);
        devices.approve(started.deviceId(),telegram);
        assertThat(devices.poll(started.deviceId(),started.pollingToken()).state()).isEqualTo("TRUSTED");
        assertThat(devices.authenticate(started.deviceId(),started.deviceCredential()).username()).isEqualTo(name);
        fails(401,()->devices.authenticate(started.deviceId(),started.pollingToken()));
        fails(400,()->devices.approve(started.deviceId(),telegram));
        assertThat(devices.list(telegram)).hasSize(1);
        fails(404,()->devices.revokeByTelegram(started.deviceId(),telegramId()));
        devices.revokeByTelegram(started.deviceId(),telegram);
        fails(401,()->devices.authenticate(started.deviceId(),started.deviceCredential()));
        assertThat(devices.list(telegram)).isEmpty();
        // Existing password login and registration completion were not modified.
        assertThat(service.login(name,PASSWORD).username()).isEqualTo(name);
        assertThat(service.status(registration.registrationId(),registration.pollingToken()).state()).isEqualTo(RegistrationSession.State.COMPLETED);
    }

    @Test
    void deviceExpiryDisabledAccountAndLogoutAreFailClosed() {
        String name=username(); var registration=register(name); long telegram=telegramId();
        var confirmed=service.confirm(token(registration),telegram,null);
        var expired=devices.begin(name,PASSWORD,"Expired PC");
        jdbc.update("UPDATE trusted_device SET created_at=CURRENT_TIMESTAMP-INTERVAL '2' HOUR,confirmation_expires_at=CURRENT_TIMESTAMP-INTERVAL '1' HOUR WHERE id=?",expired.deviceId());
        fails(410,()->devices.poll(expired.deviceId(),expired.pollingToken()));
        fails(400,()->devices.approve(expired.deviceId(),telegram));
        var active=devices.begin(name,PASSWORD,"Active PC"); devices.approve(active.deviceId(),telegram);
        transactions.executeWithoutResult(tx->users.findById(confirmed.platformUserId()).orElseThrow().changeStatus(UserStatus.DISABLED));
        fails(401,()->devices.authenticate(active.deviceId(),active.deviceCredential()));
        transactions.executeWithoutResult(tx->users.findById(confirmed.platformUserId()).orElseThrow().changeStatus(UserStatus.ACTIVE));
        devices.revokeSelf(active.deviceId(),active.deviceCredential());
        devices.revokeSelf(active.deviceId(),active.deviceCredential());
        fails(401,()->devices.authenticate(active.deviceId(),active.deviceCredential()));
        var aged=devices.begin(name,PASSWORD,"Aged PC"); devices.approve(aged.deviceId(),telegram);
        jdbc.update("UPDATE trusted_device SET created_at=CURRENT_TIMESTAMP-INTERVAL '3' DAY,confirmation_expires_at=CURRENT_TIMESTAMP-INTERVAL '2' DAY,trusted_expires_at=CURRENT_TIMESTAMP-INTERVAL '1' DAY WHERE id=?",aged.deviceId());
        fails(401,()->devices.authenticate(aged.deviceId(),aged.deviceCredential()));
    }

    @Test
    void concurrentDeviceApprovalHasOneWinnerAndRevokeIsTerminal() throws Exception {
        String name=username(); var registration=register(name); long telegram=telegramId(); service.confirm(token(registration),telegram,null);
        var started=devices.begin(name,PASSWORD,"Race PC");
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<Boolean> approve=()->{ try { devices.approve(started.deviceId(),telegram); return true; } catch(AccountFailure failure) { return false; } };
            var outcomes=pool.invokeAll(List.of(approve,approve));
            assertThat(outcomes.stream().filter(result->{try{return result.get();}catch(Exception exception){throw new AssertionError(exception);}}).count()).isEqualTo(1);
        }
        devices.revokeByTelegram(started.deviceId(),telegram);
        fails(400,()->devices.approve(started.deviceId(),telegram));
        fails(401,()->devices.authenticate(started.deviceId(),started.deviceCredential()));
    }

    @Test
    void deviceHttpFlowRequiresPasswordNumericOwnerAndIndependentCapabilities() throws Exception {
        String name=username(); var registration=register(name); long telegram=telegramId(); service.confirm(token(registration),telegram,null);
        String body="{\"username\":\""+name+"\",\"password\":\""+PASSWORD+"\",\"label\":\"HTTP PC\"}";
        var result=http("POST","/api/auth/devices",body,null,null); assertThat(result.statusCode()).isEqualTo(201);
        UUID id=UUID.fromString(JsonPath.read(result.body(),"$.deviceId"));
        String poll=JsonPath.read(result.body(),"$.pollingToken"),credential=JsonPath.read(result.body(),"$.deviceCredential");
        assertThat(result.headers().firstValue("Cache-Control")).contains("no-store");
        String path="/api/auth/devices/"+id;
        var badPassword=http("POST","/api/auth/devices",body.replace(PASSWORD,"incorrect-password-test"),null,null);
        assertThat(badPassword.statusCode()).isEqualTo(401);
        assertThat(badPassword.body()).contains("INVALID_LOGIN").doesNotContain("password","hash","stackTrace");
        assertThat(http("GET",path,null,null,null).statusCode()).isEqualTo(404);
        assertThat(http("POST",path+"/authenticate",null,"Bearer "+credential,null).statusCode()).isEqualTo(401);
        String owner="{\"telegramUserId\":"+telegram+"}";
        assertThat(http("POST","/api/internal/devices/"+id+"/approve",owner,null,null).statusCode()).isEqualTo(401);
        assertThat(http("POST","/api/internal/devices/"+id+"/approve",owner,null,SECRET).statusCode()).isEqualTo(204);
        assertThat(http("GET",path,null,"Bearer "+poll,null).body()).contains("TRUSTED").doesNotContain(credential,poll,"hash");
        assertThat(http("POST",path+"/authenticate",null,"Bearer "+credential,null).statusCode()).isEqualTo(200);
        assertThat(http("POST",path+"/revoke",null,"Bearer "+poll,null).statusCode()).isEqualTo(401);
        assertThat(http("POST",path+"/revoke",null,"Bearer "+credential,null).statusCode()).isEqualTo(204);
        assertThat(http("POST",path+"/authenticate",null,"Bearer "+credential,null).statusCode()).isEqualTo(401);
    }

    @Test void concurrentDeviceApproveAndRevokeCannotResurrectTrust() throws Exception {
        String name=username(); var registration=register(name); long telegram=telegramId(); service.confirm(token(registration),telegram,null);
        var started=devices.begin(name,PASSWORD,"Race revoke PC");
        var gate=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var approve=pool.submit(()->{ gate.await(); try { devices.approve(started.deviceId(),telegram); } catch(AccountFailure failure) { assertThat(failure.status()).isEqualTo(400); } return true; });
            var revoke=pool.submit(()->{ gate.await(); devices.revokeByTelegram(started.deviceId(),telegram); return true; });
            gate.countDown(); assertThat(approve.get(10,TimeUnit.SECONDS)).isTrue(); assertThat(revoke.get(10,TimeUnit.SECONDS)).isTrue();
        }
        assertThat(devices.poll(started.deviceId(),started.pollingToken()).state()).isEqualTo("REVOKED");
        fails(401,()->devices.authenticate(started.deviceId(),started.deviceCredential()));
    }
    @Test void deviceDatabaseConstraintsAndLiveLimitAreEnforced() {
        String name=username(); var registration=register(name); long telegram=telegramId(); service.confirm(token(registration),telegram,null);
        var first=devices.begin(name,PASSWORD,"Constraint PC");
        var second=devices.begin(name,PASSWORD,"Second PC");
        String hash=jdbc.queryForObject("SELECT credential_hash FROM trusted_device WHERE id=?",String.class,first.deviceId());
        assertThatThrownBy(()->jdbc.update("UPDATE trusted_device SET credential_hash=? WHERE id=?",hash,second.deviceId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE trusted_device SET state='TRUSTED' WHERE id=?",first.deviceId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE trusted_device SET state='UNKNOWN' WHERE id=?",first.deviceId())).isInstanceOf(DataIntegrityViolationException.class);
        for(int n=0;n<18;n++) {
            UUID copy=UUID.randomUUID();
            jdbc.update("INSERT INTO trusted_device (id,platform_user_id,label,state,credential_hash,polling_hash,verification_hash,created_at,confirmation_expires_at,trusted_expires_at) "+
                "SELECT ?,platform_user_id,label,state,?,?,?,created_at,confirmation_expires_at,trusted_expires_at FROM trusted_device WHERE id=?",
                copy,Secrets.hash(copy+"c"),Secrets.hash(copy+"p"),Secrets.hash(copy+"v"),first.deviceId());
        }
        fails(429,()->devices.begin(name,PASSWORD,"Beyond limit"));
        assertThat(devices.list(telegram)).hasSize(20);
    }

    private AccountService.Started register(String username) {
        var result = service.register(username, PASSWORD, key());
        fixtures.add(credentials.findByUsername(username.toLowerCase(Locale.ROOT)).orElseThrow().getPlatformUserId());
        return result;
    }
    private String key() {
        var generated = alphaKeys.generate(1, null).getFirst();
        keyFixtures.add(generated.id());
        return generated.plaintext();
    }
    private String username() { return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 30); }
    private long telegramId() { return 1 + (UUID.randomUUID().getLeastSignificantBits() & 0x0000FFFFFFFFFFFFL); }
    private String token(AccountService.Started registration) { return URI.create(registration.telegramDeepLink()).getQuery().substring(6); }
    private void fails(int status, Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(AccountFailure.class,
                failure -> assertThat(failure.status()).isEqualTo(status));
    }
    private void reject(Runnable action) {
        assertThatThrownBy(() -> transactions.executeWithoutResult(tx -> action.run()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    private HttpResponse<String> http(String method, String path, String body, String auth, String secret) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .header("Content-Type", "application/json");
            if (auth != null) { request.header("Authorization", auth); }
            if (secret != null) { request.header("X-Bot-Service-Secret", secret); }
            return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() :
                    HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
