package dev.createserver.backend.account;

import dev.createserver.backend.identity.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final PlatformUserRepository users;
    private final AccountCredentialsRepository credentials;
    private final TelegramIdentityRepository telegram;
    private final RegistrationSessionRepository registrations;
    private final PasswordEncoder passwords;
    private final AccountSettings settings;
    private final Clock clock;
    private final RateLimits limits;
    private final AlphaAccessKeyRepository alphaKeys;
    private final String dummyHash;

    public AccountService(PlatformUserRepository users, AccountCredentialsRepository credentials,
                          TelegramIdentityRepository telegram, RegistrationSessionRepository registrations,
                          PasswordEncoder passwords, AccountSettings settings, Clock clock, RateLimits limits,
                          AlphaAccessKeyRepository alphaKeys) {
        this.users = users; this.credentials = credentials; this.telegram = telegram;
        this.registrations = registrations; this.passwords = passwords; this.settings = settings;
        this.clock = clock; this.limits = limits;
        this.alphaKeys = alphaKeys;
        dummyHash = passwords.encode(Secrets.token());
    }

    @Transactional
    public Started register(String username, String password, String alphaKey) {
        String login = normalize(username);
        validatePassword(password);
        String bot = settings.telegramBotUsername();
        if (bot == null || !bot.matches("[A-Za-z0-9_]{5,32}") ||
                settings.botBackendSecret() == null || settings.botBackendSecret().length() < 32) {
            throw new AccountFailure(503, "VERIFICATION_NOT_CONFIGURED");
        }
        if (credentials.findByUsername(login).isPresent()) { throw new AccountFailure(409, "USERNAME_UNAVAILABLE"); }
        if (!Secrets.isToken(alphaKey)) throw new AccountFailure(400, "INVALID_ALPHA_KEY");
        AlphaAccessKey key = alphaKeys.lockByHash(Secrets.hash(alphaKey))
                .orElseThrow(() -> new AccountFailure(400, "INVALID_ALPHA_KEY"));
        if (!key.available(clock.instant())) throw new AccountFailure(400, "INVALID_ALPHA_KEY");
        PlatformUser user = PlatformUser.create();
        user.changeStatus(UserStatus.PENDING_VERIFICATION);
        user = users.saveAndFlush(user);
        credentials.saveAndFlush(new AccountCredentials(user.getId(), login, passwords.encode(password)));
        String verification = Secrets.token();
        String polling = Secrets.token();
        String code = Secrets.code();
        Instant now = clock.instant();
        RegistrationSession session = registrations.saveAndFlush(new RegistrationSession(
                user.getId(), Secrets.hash(verification), Secrets.hash(polling), now,
                now.plusSeconds(settings.registrationTtlSeconds())));
        key.consume(user.getId(), clock.instant());
        session.setCodeHash(Secrets.keyedHash("registration-code-v1", Secrets.normalizeCode(code), settings.botBackendSecret()));
        alphaKeys.flush();
        return new Started(session.getId(), polling, "https://t.me/" + bot + "?start=" + verification,
                session.getExpiresAt(), code);
    }

    @Transactional
    public Status status(UUID sessionId, String pollingToken) {
        if (!Secrets.isToken(pollingToken)) { throw new AccountFailure(404, "REGISTRATION_NOT_FOUND"); }
        RegistrationSession session = registrations.lockById(sessionId)
                .orElseThrow(() -> new AccountFailure(404, "REGISTRATION_NOT_FOUND"));
        if (!Secrets.equal(session.getPollingHash(), Secrets.hash(pollingToken))) {
            throw new AccountFailure(404, "REGISTRATION_NOT_FOUND");
        }
        if (session.getState() == RegistrationSession.State.COMPLETED && !clock.instant().isBefore(session.getExpiresAt())) {
            throw new AccountFailure(410, "REGISTRATION_EXPIRED");
        }
        session.expire(clock.instant());
        return new Status(session.getState(), session.getExpiresAt(),
                session.getState() == RegistrationSession.State.COMPLETED ? session.getPlatformUserId() : null);
    }

    @Transactional
    public Confirmed confirm(String token, long telegramUserId, String telegramUsername) {
        if (!Secrets.isToken(token) || telegramUserId <= 0 ||
                (telegramUsername != null && telegramUsername.length() > 64)) {
            throw new AccountFailure(400, "INVALID_VERIFICATION");
        }
        RegistrationSession session = registrations.lockByVerificationHash(Secrets.hash(token))
                .orElseThrow(() -> new AccountFailure(400, "INVALID_VERIFICATION"));
        return confirmSession(session, telegramUserId, telegramUsername);
    }

    /** Compatibility for sessions issued before V5, never an auto-confirm path for new sessions. */
    @Transactional
    public Confirmed confirmLegacy(String token, long telegramUserId, String telegramUsername) {
        if (!Secrets.isToken(token) || telegramUserId <= 0 || (telegramUsername != null && telegramUsername.length() > 64)) {
            throw new AccountFailure(400, "INVALID_VERIFICATION");
        }
        RegistrationSession session = registrations.lockByVerificationHash(Secrets.hash(token))
            .orElseThrow(() -> new AccountFailure(400, "INVALID_VERIFICATION"));
        if (session.getCodeHash() != null) throw new AccountFailure(400, "INVALID_VERIFICATION");
        return confirmSession(session, telegramUserId, telegramUsername);
    }

    Confirmed confirmSession(RegistrationSession session, long telegramUserId, String telegramUsername) {
        Instant now = clock.instant();
        // Failed confirmations do not change state, but expiry is checked on every access.
        if (session.getState() != RegistrationSession.State.PENDING || !now.isBefore(session.getExpiresAt())) {
            throw new AccountFailure(400, "INVALID_VERIFICATION");
        }
        PlatformUser user = users.findById(session.getPlatformUserId()).orElseThrow();
        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new AccountFailure(409, "REGISTRATION_UNAVAILABLE");
        }
        if (telegram.existsById(telegramUserId)) { throw new AccountFailure(409, "TELEGRAM_ALREADY_LINKED"); }
        telegram.saveAndFlush(new TelegramIdentity(telegramUserId, user.getId(), telegramUsername, now));
        user.changeStatus(UserStatus.ACTIVE);
        session.complete(now);
        return new Confirmed(user.getId());
    }

    @Transactional(readOnly = true)
    public LoggedIn login(String username, String password) {
        String login;
        try { login = normalize(username); validatePassword(password); }
        catch (AccountFailure failure) { throw new AccountFailure(401, "INVALID_LOGIN"); }
        if (!limits.allow("login-user:" + Secrets.hash(login), 5, Duration.ofMinutes(5))) {
            throw new AccountFailure(429, "RATE_LIMITED");
        }
        Optional<AccountCredentials> credential = credentials.findByUsername(login);
        boolean matches = passwords.matches(password, credential.map(AccountCredentials::getPasswordHash).orElse(dummyHash));
        if (!matches || credential.isEmpty()) { throw new AccountFailure(401, "INVALID_LOGIN"); }
        UUID userId = credential.get().getPlatformUserId();
        PlatformUser user = users.findById(userId).orElseThrow();
        if (user.getStatus() != UserStatus.ACTIVE || !telegram.existsByPlatformUserId(userId)) {
            throw new AccountFailure(401, "INVALID_LOGIN");
        }
        return new LoggedIn(userId, credential.get().getUsername(), "AUTHENTICATED");
    }

    @Transactional(readOnly = true)
    public LinkedAccount linkedUser(long telegramUserId) {
        if (telegramUserId <= 0) { throw new AccountFailure(400, "INVALID_TELEGRAM_ID"); }
        UUID id = telegram.findById(telegramUserId).map(TelegramIdentity::getPlatformUserId)
                .orElseThrow(() -> new AccountFailure(404, "USER_NOT_FOUND"));
        PlatformUser user = users.findById(id).orElseThrow();
        String username = credentials.findById(id).map(AccountCredentials::getUsername).orElse(null);
        // Explicit read DTO: never serialize the credentials or identity entities.
        return new LinkedAccount(id, username, user.getStatus());
    }

    private String normalize(String username) {
        if (username == null || !username.matches("[A-Za-z][A-Za-z0-9_]{2,31}")) {
            throw new AccountFailure(400, "INVALID_USERNAME");
        }
        return username.toLowerCase(Locale.ROOT);
    }

    private void validatePassword(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 12 ||
                password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AccountFailure(400, "INVALID_PASSWORD");
        }
    }

    public record Started(UUID registrationId, String pollingToken, String telegramDeepLink, Instant expiresAt, String verificationCode) {
        @Override public String toString() { return "Started[redacted]"; }
    }
    public record Status(RegistrationSession.State state, Instant expiresAt, UUID platformUserId) {}
    public record Confirmed(UUID platformUserId) {}
    public record LinkedAccount(UUID platformUserId, String username, UserStatus status) {}
    public record LoggedIn(UUID platformUserId, String username, String status) {}
}
