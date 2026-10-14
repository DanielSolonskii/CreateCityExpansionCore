package dev.createserver.backend.account;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Launcher-only bearer credential; never a Minecraft admission or general API access token. */
@Service
public class DeviceService {
    private final JdbcTemplate jdbc;
    private final AccountService accounts;
    private final AccountSettings settings;
    private final Clock clock;
    public DeviceService(JdbcTemplate jdbc, AccountService accounts, AccountSettings settings, Clock clock) {
        this.jdbc=jdbc; this.accounts=accounts; this.settings=settings; this.clock=clock;
    }
    @Transactional
    public Started begin(String username, String password, String label) {
        if (label == null || !label.matches("[\\p{L}\\p{N} ._-]{1,64}")) throw new AccountFailure(400,"INVALID_DEVICE");
        String bot=settings.telegramBotUsername();
        if (bot == null || !bot.matches("[A-Za-z0-9_]{5,32}") || settings.botBackendSecret()==null || settings.botBackendSecret().length()<32)
            throw new AccountFailure(503,"VERIFICATION_NOT_CONFIGURED");
        var account=accounts.login(username,password);
        jdbc.queryForObject("SELECT id FROM platform_user WHERE id = ? FOR UPDATE", UUID.class,account.platformUserId());
        Instant now=clock.instant();
        Long count=jdbc.queryForObject("SELECT COUNT(*) FROM trusted_device WHERE platform_user_id = ? AND " +
            "((state = 'PENDING' AND confirmation_expires_at > ?) OR (state = 'TRUSTED' AND trusted_expires_at > ?))",Long.class,
            account.platformUserId(),ts(now),ts(now));
        if (count >= 20) throw new AccountFailure(429,"DEVICE_LIMIT");
        UUID id=UUID.randomUUID();
        String credential=Secrets.token(),poll=Secrets.token(),proof=Secrets.token();
        Instant expiry=now.plusSeconds(900);
        jdbc.update("INSERT INTO trusted_device (id,platform_user_id,label,state,credential_hash,polling_hash,verification_hash," +
            "created_at,confirmation_expires_at,trusted_expires_at) VALUES (?,?,?,'PENDING',?,?,?,?,?,?)", id,
            account.platformUserId(),label,hash("credential",credential),hash("poll",poll),hash("proof",proof),ts(now),ts(expiry),ts(now.plus(Duration.ofDays(180))));
        return new Started(id,poll,credential,"https://t.me/"+bot+"?start=d_"+proof,expiry);
    }
    @Transactional(readOnly=true)
    public DeviceView poll(UUID id,String pollingToken) {
        Device row=find(id,false);
        if (!valid("poll",pollingToken,row.poll())) throw new AccountFailure(404,"DEVICE_NOT_FOUND");
        if (row.state().equals("PENDING") && !clock.instant().isBefore(row.confirmationExpiry()))
            throw new AccountFailure(410,"DEVICE_EXPIRED");
        return view(row);
    }
    @Transactional(readOnly=true)
    public Preview preview(String proof,long telegramId) {
        if (!Secrets.isToken(proof)) throw new AccountFailure(400,"INVALID_VERIFICATION");
        var ids=jdbc.query("SELECT id FROM trusted_device WHERE verification_hash = ?",(rs,n)->rs.getObject(1,UUID.class),hash("proof",proof));
        if (ids.isEmpty()) throw new AccountFailure(400,"INVALID_VERIFICATION");
        Device row=find(ids.getFirst(),false); owned(row,telegramId); pending(row);
        return new Preview(row.id(),username(row.userId()),row.label(),row.confirmationExpiry());
    }
    @Transactional
    public void approve(UUID id,long telegramId) {
        Device row=find(id,true); owned(row,telegramId); pending(row);
        jdbc.update("UPDATE trusted_device SET state='TRUSTED', approved_at=? WHERE id=?",ts(clock.instant()),id);
    }
    @Transactional
    public AccountService.LoggedIn authenticate(UUID id,String credential) {
        Device row=find(id,true);
        if (!valid("credential",credential,row.credential()) || !row.state().equals("TRUSTED") ||
            !clock.instant().isBefore(row.trustExpiry()) || !active(row.userId())) throw new AccountFailure(401,"INVALID_DEVICE");
        jdbc.update("UPDATE trusted_device SET last_used_at=? WHERE id=?",ts(clock.instant()),id);
        return new AccountService.LoggedIn(row.userId(),username(row.userId()),"AUTHENTICATED");
    }
    @Transactional(readOnly=true)
    public List<DeviceView> list(long telegramId) {
        UUID user=owner(telegramId);
        return jdbc.query("SELECT id FROM trusted_device WHERE platform_user_id=? AND state <> 'REVOKED' ORDER BY created_at DESC",
            (rs,n)->rs.getObject(1,UUID.class),user).stream().map(id->find(id,false))
            .filter(row->clock.instant().isBefore(row.state().equals("PENDING") ? row.confirmationExpiry() : row.trustExpiry()))
            .map(this::view).toList();
    }
    @Transactional
    public void revokeByTelegram(UUID id,long telegramId) { Device row=find(id,true); owned(row,telegramId); revoke(row); }
    @Transactional
    public void revokeSelf(UUID id,String credential) {
        Device row=find(id,true);
        if (!valid("credential",credential,row.credential())) throw new AccountFailure(401,"INVALID_DEVICE");
        revoke(row);
    }
    private void revoke(Device row) {
        if (!row.state().equals("REVOKED")) jdbc.update("UPDATE trusted_device SET state='REVOKED',revoked_at=? WHERE id=?",ts(clock.instant()),row.id());
    }
    private void pending(Device row) {
        if (!row.state().equals("PENDING") || !clock.instant().isBefore(row.confirmationExpiry()) || !active(row.userId()))
            throw new AccountFailure(400,"INVALID_VERIFICATION");
    }
    private boolean active(UUID user) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*) > 0 FROM platform_user u JOIN telegram_identity t ON t.platform_user_id=u.id " +
            "WHERE u.id=? AND u.status='ACTIVE'",Boolean.class,user));
    }
    private UUID owner(long id) {
        if (id<=0) throw new AccountFailure(404,"DEVICE_NOT_FOUND");
        var rows=jdbc.query("SELECT platform_user_id FROM telegram_identity WHERE telegram_user_id=?",(rs,n)->rs.getObject(1,UUID.class),id);
        if (rows.isEmpty()) throw new AccountFailure(404,"DEVICE_NOT_FOUND");
        return rows.getFirst();
    }
    private void owned(Device row,long id) {
        if (!owner(id).equals(row.userId())) throw new AccountFailure(404,"DEVICE_NOT_FOUND");
    }
    private String username(UUID user) { return jdbc.queryForObject("SELECT username FROM account_credentials WHERE platform_user_id=?",String.class,user); }
    private Device find(UUID id,boolean lock) {
        var rows=jdbc.query("SELECT * FROM trusted_device WHERE id=?"+(lock ? " FOR UPDATE" : ""),(rs,n)->new Device(
            rs.getObject("id",UUID.class),rs.getObject("platform_user_id",UUID.class),rs.getString("label"),rs.getString("state"),
            rs.getString("credential_hash"),rs.getString("polling_hash"),rs.getTimestamp("confirmation_expires_at").toInstant(),
            rs.getTimestamp("trusted_expires_at").toInstant(),rs.getTimestamp("created_at").toInstant()),id);
        if (rows.isEmpty()) throw new AccountFailure(404,"DEVICE_NOT_FOUND");
        return rows.getFirst();
    }
    private DeviceView view(Device row) { return new DeviceView(row.id(),row.label(),row.state(),row.created(),row.trustExpiry()); }
    private static Timestamp ts(Instant instant) { return Timestamp.from(instant); }
    private static String hash(String purpose,String token) { return Secrets.hash("trusted-device-v1:"+purpose+":"+token); }
    private static boolean valid(String purpose,String token,String stored) { return Secrets.isToken(token) && Secrets.equal(stored,hash(purpose,token)); }
    private record Device(UUID id,UUID userId,String label,String state,String credential,String poll,Instant confirmationExpiry,Instant trustExpiry,Instant created) {}
    public record Started(UUID deviceId,String pollingToken,String deviceCredential,String telegramDeepLink,Instant expiresAt) {
        @Override public String toString() { return "DeviceStarted[redacted]"; }
    }
    public record DeviceView(UUID deviceId,String label,String state,Instant createdAt,Instant expiresAt) {}
    public record Preview(UUID deviceId,String username,String label,Instant expiresAt) {}
}
