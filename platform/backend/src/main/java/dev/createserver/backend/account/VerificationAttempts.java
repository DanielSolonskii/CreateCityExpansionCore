package dev.createserver.backend.account;

import java.sql.Timestamp;
import java.time.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Limits commit independently of a rejected verification, and survive process restart. */
@Service
public class VerificationAttempts {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final AccountSettings settings;
    public VerificationAttempts(JdbcTemplate jdbc, PlatformTransactionManager manager, Clock clock, AccountSettings settings) {
        this.jdbc = jdbc; this.clock = clock; this.settings = settings;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    public void check(long id, boolean code) {
        if (id <= 0) throw new AccountFailure(400, "INVALID_VERIFICATION");
        if (!allow("verification-global", 200, 60) ||
            !allow((code ? "code:" : "qr:") + id, code ? 5 : 10, code ? 900 : 60)) {
            throw new AccountFailure(429, "RATE_LIMITED");
        }
    }
    private boolean allow(String key, int maximum, int seconds) {
        String hash = Secrets.keyedHash("verification-rate-v1", key, settings.botBackendSecret());
        for (int retry = 0; retry < 3; retry++) {
            try {
                return Boolean.TRUE.equals(transaction.execute(tx -> {
                    Instant now = clock.instant();
                    var rows = jdbc.query("SELECT expires_at, attempts FROM verification_rate_bucket WHERE bucket_key = ? FOR UPDATE",
                        (rs, n) -> new Bucket(rs.getTimestamp(1).toInstant(), rs.getInt(2)), hash);
                    if (rows.isEmpty()) {
                        // Bounded storage: only expired buckets can be removed.
                        jdbc.update("DELETE FROM verification_rate_bucket WHERE expires_at <= ?", Timestamp.from(now));
                        if (jdbc.queryForObject("SELECT COUNT(*) FROM verification_rate_bucket", Long.class) >= 10000) return false;
                        jdbc.update("INSERT INTO verification_rate_bucket VALUES (?, ?, 1)", hash, Timestamp.from(now.plusSeconds(seconds)));
                        return true;
                    }
                    Bucket bucket = rows.getFirst();
                    if (!now.isBefore(bucket.expiry())) {
                        jdbc.update("UPDATE verification_rate_bucket SET expires_at = ?, attempts = 1 WHERE bucket_key = ?",
                            Timestamp.from(now.plusSeconds(seconds)), hash);
                        return true;
                    }
                    if (bucket.attempts() >= maximum) return false;
                    jdbc.update("UPDATE verification_rate_bucket SET attempts = attempts + 1 WHERE bucket_key = ?", hash);
                    return true;
                }));
            } catch (DuplicateKeyException race) { /* Retry in a fresh transaction. */ }
        }
        return false;
    }
    private record Bucket(Instant expiry, int attempts) {}
}
