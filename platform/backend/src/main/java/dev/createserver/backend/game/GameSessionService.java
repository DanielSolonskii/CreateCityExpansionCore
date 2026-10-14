package dev.createserver.backend.game;

import dev.createserver.backend.account.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL authority; matches the existing DeviceService transaction/JDBC convention. */
@Service
public class GameSessionService {
    private final JdbcTemplate jdbc;private final DeviceService devices;private final GameSettings settings;private final Clock clock;
    private static final SecureRandom RANDOM=new SecureRandom();
    public GameSessionService(JdbcTemplate jdbc,DeviceService devices,GameSettings settings,Clock clock){this.jdbc=jdbc;this.devices=devices;this.settings=settings;this.clock=clock;}
    @Transactional
    public Capability start(UUID deviceId,String credential){
        configured();
        var account=devices.authenticate(deviceId,credential); // locks trusted_device before platform/session rows
        lockUser(account.platformUserId());
        var profile=profile(account.platformUserId(),account.username());
        Instant now=clock.instant();
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM launcher_session WHERE platform_user_id=? AND revoked_at IS NULL AND expires_at>?",Long.class,account.platformUserId(),ts(now));
        if(count>=20)throw new AccountFailure(429,"GAME_RATE_LIMITED");
        UUID id=UUID.randomUUID();String secret=Secrets.token();Instant expiry=now.plusSeconds(600);
        jdbc.update("INSERT INTO launcher_session(id,platform_user_id,device_id,secret_hash,created_at,expires_at) VALUES(?,?,?,?,?,?)",id,account.platformUserId(),deviceId,hash("launcher",secret),ts(now),ts(expiry));
        return new Capability(id+"."+secret,expiry,account.username(),profile);
    }
    @Transactional
    public void revoke(String bearer){
        Session session=session(bearer);
        jdbc.update("UPDATE launcher_session SET revoked_at=? WHERE id=? AND revoked_at IS NULL",ts(clock.instant()),session.id());
    }
    @Transactional
    public Ticket issue(String bearer,Challenge challenge){
        configured();validate(challenge);
        Session session=session(bearer);boot(challenge.serverId(),challenge.bootId());
        Profile identity=readProfile(session.user());
        Instant now=clock.instant();
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM game_session WHERE platform_user_id=? AND created_at>?",Long.class,session.user(),ts(now.minusSeconds(60)));
        if(count>=10)throw new AccountFailure(429,"GAME_RATE_LIMITED");
        UUID id=UUID.randomUUID();String secret=Secrets.token();Instant expiry=now.plusSeconds(120);
        jdbc.update("INSERT INTO game_session(id,launcher_session_id,platform_user_id,minecraft_uuid,server_id,boot_id,connection_id,nonce_hash,secret_hash,state,created_at,expires_at) VALUES(?,?,?,?,?,?,?,?,?,'ISSUED',?,?)",
                id,session.id(),session.user(),identity.minecraftUuid(),challenge.serverId(),challenge.bootId(),challenge.connectionId(),hash("nonce",challenge.nonce()),hash("game",secret),ts(now),ts(expiry));
        return new Ticket(id,secret,expiry);
    }
    @Transactional
    public Boot registerBoot(String serverId,UUID bootId){
        configured();if(!settings.serverId().equals(serverId) || bootId==null)throw denied();
        var current=jdbc.query("SELECT boot_id FROM game_server_boot WHERE server_id=? FOR UPDATE",(rs,n)->rs.getObject(1,UUID.class),serverId);
        if(current.isEmpty())jdbc.update("INSERT INTO game_server_boot(server_id,boot_id,registered_at) VALUES(?,?,?)",serverId,bootId,ts(clock.instant()));
        else if(!current.getFirst().equals(bootId))jdbc.update("UPDATE game_server_boot SET boot_id=?,registered_at=? WHERE server_id=?",bootId,ts(clock.instant()),serverId);
        return new Boot(bootId);
    }
    @Transactional
    public Decision consume(Consume proof){
        configured();
        if(proof==null || proof.gameSessionId()==null || !Secrets.isToken(proof.secret()) || proof.minecraftUuid()==null || proof.joinName()==null)throw denied();
        validate(new Challenge(proof.serverId(),proof.bootId(),proof.connectionId(),proof.nonce()));
        var ids=jdbc.query("SELECT launcher_session_id FROM game_session WHERE id=?",(rs,n)->rs.getObject(1,UUID.class),proof.gameSessionId());
        if(ids.isEmpty())throw denied();
        Session session=lockedSession(ids.getFirst());
        boot(proof.serverId(),proof.bootId());
        var tickets=jdbc.query("SELECT * FROM game_session WHERE id=? FOR UPDATE",(rs,n)->new Row(rs.getObject("platform_user_id",UUID.class),rs.getObject("minecraft_uuid",UUID.class),rs.getString("server_id"),
                rs.getObject("boot_id",UUID.class),rs.getObject("connection_id",UUID.class),rs.getString("nonce_hash"),rs.getString("secret_hash"),rs.getString("state"),rs.getTimestamp("expires_at").toInstant(),rs.getString("consumed_display_name")),proof.gameSessionId());
        if(tickets.isEmpty())throw denied();Row row=tickets.getFirst();Profile identity=readProfile(session.user());
        if(!row.user().equals(session.user()) || !row.mc().equals(proof.minecraftUuid()) || !identity.minecraftUuid().equals(proof.minecraftUuid()) || !identity.joinName().equals(proof.joinName())
                || !row.server().equals(proof.serverId()) || !row.boot().equals(proof.bootId()) || !row.connection().equals(proof.connectionId())
                || !Secrets.equal(row.nonce(),hash("nonce",proof.nonce())) || !Secrets.equal(row.secret(),hash("game",proof.secret())) || !clock.instant().isBefore(row.expiry())
                || !(row.state().equals("ISSUED") || row.state().equals("CONSUMED")))throw denied();
        String display=row.state().equals("CONSUMED") ? row.display() : identity.currentName();
        if(row.state().equals("ISSUED"))jdbc.update("UPDATE game_session SET state='CONSUMED',consumed_at=?,consumed_display_name=? WHERE id=?",ts(clock.instant()),display,proof.gameSessionId());
        return new Decision(session.user(),row.mc(),identity.joinName(),display);
    }
    private Profile profile(UUID user,String username){
        var rows=jdbc.query("SELECT minecraft_uuid,join_name,current_name FROM minecraft_identity WHERE platform_user_id=?",(rs,n)->new Profile(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)),user);
        if(!rows.isEmpty())return validateProfile(rows.getFirst());
        StringBuilder alias=new StringBuilder("cs");String alphabet="abcdefghijklmnopqrstuvwxyz0123456789";
        for(int i=0;i<14;i++)alias.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        String join=alias.toString();UUID mc=offlineUuid(join);String display=username.substring(0,Math.min(16,username.length()));
        jdbc.update("INSERT INTO minecraft_identity(minecraft_uuid,platform_user_id,current_name,join_name) VALUES(?,?,?,?)",mc,user,display,join);
        return new Profile(mc,join,display);
    }
    private Profile readProfile(UUID user){
        var rows=jdbc.query("SELECT minecraft_uuid,join_name,current_name FROM minecraft_identity WHERE platform_user_id=?",(rs,n)->new Profile(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)),user);
        if(rows.isEmpty())throw denied();return validateProfile(rows.getFirst());
    }
    private Profile validateProfile(Profile p){
        if(p.joinName()==null || !p.joinName().matches("cs[a-z0-9]{14}") || !offlineUuid(p.joinName()).equals(p.minecraftUuid()))throw new AccountFailure(409,"GAME_IDENTITY_NOT_PROVISIONED");
        return p;
    }
    private Session session(String bearer){
        if(bearer==null || !bearer.matches("[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}"))throw denied();
        UUID id;try{id=UUID.fromString(bearer.substring(0,36));}catch(IllegalArgumentException e){throw denied();}
        Session session=lockedSession(id);
        String stored=jdbc.queryForObject("SELECT secret_hash FROM launcher_session WHERE id=?",String.class,id);
        if(!Secrets.equal(stored,hash("launcher",bearer.substring(37))))throw denied();return session;
    }
    /** Global order: device -> user -> launcher session -> boot -> game session. */
    private Session lockedSession(UUID id){
        var rows=jdbc.query("SELECT platform_user_id,device_id FROM launcher_session WHERE id=?",(rs,n)->new Session(id,rs.getObject(1,UUID.class),rs.getObject(2,UUID.class)),id);
        if(rows.isEmpty())throw denied();Session session=rows.getFirst();
        var valid=jdbc.query("SELECT state,trusted_expires_at,platform_user_id FROM trusted_device WHERE id=? FOR UPDATE",(rs,n)->rs.getString(1).equals("TRUSTED") && clock.instant().isBefore(rs.getTimestamp(2).toInstant()) && rs.getObject(3,UUID.class).equals(session.user()),session.device());
        if(valid.isEmpty() || !valid.getFirst())throw denied();lockUser(session.user());
        var allowed=jdbc.query("SELECT expires_at,revoked_at FROM launcher_session WHERE id=? FOR UPDATE",(rs,n)->clock.instant().isBefore(rs.getTimestamp(1).toInstant()) && rs.getTimestamp(2)==null,id);
        if(allowed.isEmpty() || !allowed.getFirst())throw denied();return session;
    }
    private void lockUser(UUID user){
        String status=jdbc.queryForObject("SELECT status FROM platform_user WHERE id=? FOR UPDATE",String.class,user);
        Long telegram=jdbc.queryForObject("SELECT COUNT(*) FROM telegram_identity WHERE platform_user_id=?",Long.class,user);
        if(!"ACTIVE".equals(status) || telegram!=1)throw denied();
    }
    private void boot(String server,UUID boot){
        if(!settings.serverId().equals(server))throw denied();
        var rows=jdbc.query("SELECT boot_id FROM game_server_boot WHERE server_id=? FOR UPDATE",(rs,n)->rs.getObject(1,UUID.class),server);
        if(rows.isEmpty() || !rows.getFirst().equals(boot))throw denied();
    }
    private void configured(){if(!settings.configured())throw new AccountFailure(503,"GAME_AUTH_NOT_CONFIGURED");}
    private static void validate(Challenge c){if(c==null || c.serverId()==null || !c.serverId().matches("[a-z0-9_-]{1,64}") || c.bootId()==null || c.connectionId()==null || !Secrets.isToken(c.nonce()))throw denied();}
    private static AccountFailure denied(){return new AccountFailure(401,"GAME_AUTH_DENIED");}
    private static String hash(String purpose,String value){return Secrets.hash("game-session-v1:"+purpose+":"+value);}
    public static UUID offlineUuid(String name){return UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));}
    private static Timestamp ts(Instant i){return Timestamp.from(i);}
    private record Session(UUID id,UUID user,UUID device){}
    private record Row(UUID user,UUID mc,String server,UUID boot,UUID connection,String nonce,String secret,String state,Instant expiry,String display){}
    public record Profile(UUID minecraftUuid,String joinName,String currentName){}
    public record Capability(String bearer,Instant expiresAt,String username,Profile profile){@Override public String toString(){return "LauncherCapability[redacted]";}}
    public record Ticket(UUID gameSessionId,String secret,Instant expiresAt){@Override public String toString(){return "GameTicket[redacted]";}}
    public record Challenge(String serverId,UUID bootId,UUID connectionId,String nonce){}
    public record Consume(UUID gameSessionId,String secret,String serverId,UUID bootId,UUID connectionId,String nonce,UUID minecraftUuid,String joinName){@Override public String toString(){return "GameConsume[redacted]";}}
    public record Decision(UUID platformUserId,UUID minecraftUuid,String joinName,String currentName){}
    public record Boot(UUID bootId){}
}
