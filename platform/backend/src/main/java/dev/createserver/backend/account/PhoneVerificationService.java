package dev.createserver.backend.account;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PhoneVerificationService {
    private final RegistrationSessionRepository registrations;
    private final AccountService accounts;
    private final AccountCredentialsRepository credentials;
    private final AccountSettings settings;
    private final VerificationAttempts attempts;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final TransactionTemplate transaction;
    public PhoneVerificationService(RegistrationSessionRepository registrations, AccountService accounts,
            AccountCredentialsRepository credentials, AccountSettings settings, VerificationAttempts attempts,
            JdbcTemplate jdbc, Clock clock, PlatformTransactionManager manager) {
        this.registrations = registrations; this.accounts = accounts; this.credentials = credentials;
        this.settings = settings; this.attempts = attempts; this.jdbc = jdbc; this.clock = clock;
        this.transaction = new TransactionTemplate(manager);
    }
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Preview prepare(String token, String code, long telegramId) {
        // Commit abuse counters BEFORE acquiring the session transaction/connection.
        // Nested REQUIRES_NEW while holding a session connection can exhaust a small pool.
        attempts.check(telegramId, code != null);
        return transaction.execute(tx -> prepareInTransaction(token, code, telegramId));
    }
    private Preview prepareInTransaction(String token, String code, long telegramId) {
        boolean usingCode = code != null;
        if ((token == null) == (code == null)) throw invalid();
        Optional<RegistrationSession> found;
        if (usingCode) {
            String normalized = Secrets.normalizeCode(code);
            if (normalized == null) throw invalid();
            found = registrations.lockByCodeHash(Secrets.keyedHash("registration-code-v1", normalized, settings.botBackendSecret()));
        } else {
            if (!Secrets.isToken(token)) throw invalid();
            found = registrations.lockByVerificationHash(Secrets.hash(token));
        }
        RegistrationSession session = found.orElseThrow(PhoneVerificationService::invalid);
        Instant now = clock.instant();
        pending(session, now);
        // A new preview for this sender supersedes their older buttons; other senders cannot use this ticket.
        jdbc.update("DELETE FROM telegram_confirmation WHERE expires_at <= ? OR (registration_id = ? AND telegram_user_id = ?)",
            Timestamp.from(now), session.getId(), telegramId);
        if (jdbc.queryForObject("SELECT COUNT(*) FROM telegram_confirmation WHERE registration_id = ?", Integer.class, session.getId()) >= 32) {
            throw new AccountFailure(429, "RATE_LIMITED");
        }
        String ticket = Secrets.token();
        Instant expiry = now.plusSeconds(300).isBefore(session.getExpiresAt()) ? now.plusSeconds(300) : session.getExpiresAt();
        jdbc.update("INSERT INTO telegram_confirmation VALUES (?, ?, ?, ?, ?)",
            Secrets.hash(ticket), session.getId(), session.getVerificationHash(), telegramId, Timestamp.from(expiry));
        String username = credentials.findById(session.getPlatformUserId()).orElseThrow().getUsername();
        return new Preview(ticket, username, expiry);
    }
    @Transactional
    public AccountService.Confirmed confirm(String ticket, long telegramId, String username) {
        if (!Secrets.isToken(ticket) || telegramId <= 0 || (username != null && username.length() > 64)) throw invalid();
        var rows = jdbc.query("SELECT registration_id, verification_hash, telegram_user_id, expires_at FROM telegram_confirmation WHERE token_hash = ?",
            (rs, n) -> new Ticket(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3), rs.getTimestamp(4).toInstant()),
            Secrets.hash(ticket));
        if (rows.isEmpty()) throw invalid();
        Ticket proof = rows.getFirst();
        RegistrationSession session = registrations.lockById(proof.registration()).orElseThrow(PhoneVerificationService::invalid);
        Instant now = clock.instant();
        if (proof.telegramId() != telegramId || !now.isBefore(proof.expiry()) ||
                !Secrets.equal(session.getVerificationHash(), proof.verificationHash())) throw invalid();
        pending(session, now);
        var result = accounts.confirmSession(session, telegramId, username);
        jdbc.update("DELETE FROM telegram_confirmation WHERE registration_id = ?", session.getId());
        return result;
    }
    @Transactional
    public AccountService.Started restart(UUID id, String pollingToken) {
        if (settings.telegramBotUsername() == null || !settings.telegramBotUsername().matches("[A-Za-z0-9_]{5,32}")) {
            throw new AccountFailure(503, "VERIFICATION_NOT_CONFIGURED");
        }
        if (!Secrets.isToken(pollingToken)) throw new AccountFailure(404, "REGISTRATION_NOT_FOUND");
        RegistrationSession session = registrations.lockById(id)
            .orElseThrow(() -> new AccountFailure(404, "REGISTRATION_NOT_FOUND"));
        if (!Secrets.equal(session.getPollingHash(), Secrets.hash(pollingToken))) throw new AccountFailure(404, "REGISTRATION_NOT_FOUND");
        String token = Secrets.token(), code = Secrets.code();
        Instant now = clock.instant();
        session.restart(Secrets.hash(token), session.getPollingHash(),
            Secrets.keyedHash("registration-code-v1", Secrets.normalizeCode(code), settings.botBackendSecret()),
            now, now.plusSeconds(settings.registrationTtlSeconds()));
        jdbc.update("DELETE FROM telegram_confirmation WHERE registration_id = ?", id);
        registrations.flush();
        return new AccountService.Started(id, pollingToken, "https://t.me/" + settings.telegramBotUsername() + "?start=" + token,
            session.getExpiresAt(), code);
    }
    private static void pending(RegistrationSession session, Instant now) {
        if (session.getState() != RegistrationSession.State.PENDING || !now.isBefore(session.getExpiresAt())) throw invalid();
    }
    private static AccountFailure invalid() { return new AccountFailure(400, "INVALID_VERIFICATION"); }
    public record Preview(String confirmationToken, String username, Instant expiresAt) {
        @Override public String toString() { return "Preview[redacted]"; }
    }
    private record Ticket(UUID registration, String verificationHash, long telegramId, Instant expiry) {}
}
