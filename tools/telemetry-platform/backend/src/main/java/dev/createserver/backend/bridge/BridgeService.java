package dev.createserver.backend.bridge;

import dev.createserver.backend.account.*;
import dev.createserver.backend.game.GameSettings;
import dev.createserver.backend.report.ReportSettings;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BridgeService {
    public static final Set<String> TYPES=Set.of("SESSION_START","SESSION_END","SESSION_CHECKPOINT","PLAYER_JOIN","PLAYER_LEAVE","CITY_CREATED","CITY_JOIN","CITY_LEAVE","TERRITORY_CHANGED","PROJECT_ADDED","PROJECT_REMOVED","ECONOMY_ACTION","RAILWAY_ACTION");
    public record Event(UUID id,UUID minecraftUuid,String type,UUID subjectId,String detail,Instant occurredAt,long durationSeconds){}
    public record Batch(UUID id,String serverId,UUID bootId,Instant startedAt,long revision,Instant observedAt,boolean online,long uptimeSeconds,List<UUID> players,int maxPlayers,String minecraftVersion,String ccecVersion,String serverVersion,long droppedEvents,List<Event> events){}
    public record Status(String state,long uptimeSeconds,int playerCount,int maxPlayers,String minecraftVersion,String ccecVersion,String serverVersion,Instant observedAt,long droppedEvents){}
    public record Player(UUID minecraftUuid,UUID platformUserId,String username){}
    public record Admin(Status status,List<Player> players,long events24h,long sessions,long playtimeSeconds){}
    private final JdbcTemplate db;private final Clock clock;private final GameSettings game;private final ReportSettings admins;
    // Local serializer used only for UUID snapshots, not for credentials.
    public BridgeService(JdbcTemplate db,Clock clock,GameSettings game,ReportSettings admins){this.db=db;this.clock=clock;this.game=game;this.admins=admins;}
    private static Timestamp ts(Instant i){return Timestamp.from(i);}
    private static AccountFailure bad(){return new AccountFailure(400,"INVALID_TELEMETRY");}
    private static boolean version(String s,int max){return s!=null&&!s.isBlank()&&s.length()<=max&&s.chars().noneMatch(Character::isISOControl);}
    private String encode(List<UUID> value){return value.stream().map(UUID::toString).collect(java.util.stream.Collectors.joining(","));}
    private List<UUID> players(String value){return value.isEmpty()?List.of():Arrays.stream(value.split(",")).map(UUID::fromString).toList();}
    private static Instant instant(Object value){return value instanceof Timestamp t?t.toInstant():((OffsetDateTime)value).toInstant();}
    private UUID platform(UUID mc){var ids=db.query("SELECT platform_user_id FROM minecraft_identity WHERE minecraft_uuid=?",(r,n)->r.getObject(1,UUID.class),mc);return ids.isEmpty()?null:ids.getFirst();}
    @Transactional public Map<String,Object> receive(Batch b){
        Instant now=clock.instant();
        if(b==null||b.id()==null||b.bootId()==null||!game.serverId().equals(b.serverId())||b.startedAt()==null||b.observedAt()==null||b.revision()<0||b.uptimeSeconds()<0||b.uptimeSeconds()>31536000||b.droppedEvents()<0
            ||b.startedAt().isAfter(now.plusSeconds(60))||b.observedAt().isBefore(b.startedAt())||b.observedAt().isAfter(now.plusSeconds(60))
            ||b.players()==null||b.players().size()>100||b.players().stream().anyMatch(Objects::isNull)||new HashSet<>(b.players()).size()!=b.players().size()||b.maxPlayers()<1||b.maxPlayers()>1000||(!b.online()&&!b.players().isEmpty())
            ||!version(b.minecraftVersion(),64)||!version(b.ccecVersion(),64)||!version(b.serverVersion(),128)||b.events()==null||b.events().size()>50)throw bad();
        for(Event e:b.events())if(e==null||e.id()==null||e.minecraftUuid()==null||e.type()==null||!TYPES.contains(e.type())||e.detail()==null||!e.detail().matches("[A-Za-z0-9_.:-]{0,64}")||e.occurredAt()==null||e.occurredAt().isBefore(b.startedAt())||e.occurredAt().isAfter(now.plusSeconds(60))||e.durationSeconds()<0||e.durationSeconds()>31536000||(e.type().startsWith("SESSION_")&&e.subjectId()==null))throw bad();
        String hash=Secrets.hash(b.toString());
        var boots=db.query("SELECT server_id,started_at FROM server_bridge_boot WHERE boot_id=? FOR UPDATE",(r,n)->Map.entry(r.getString(1),r.getTimestamp(2).toInstant()),b.bootId());
        if(boots.isEmpty()){
            db.update("INSERT INTO server_bridge_boot(boot_id,server_id,started_at,revision,observed_at,received_at,online,uptime_seconds,players,max_players,minecraft_version,ccec_version,server_version,dropped_events) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",b.bootId(),b.serverId(),ts(b.startedAt()),b.revision(),ts(b.observedAt()),ts(now),b.online(),b.uptimeSeconds(),encode(b.players()),b.maxPlayers(),b.minecraftVersion(),b.ccecVersion(),b.serverVersion(),b.droppedEvents());
        }else {
            if(!boots.getFirst().getKey().equals(b.serverId())||!boots.getFirst().getValue().equals(b.startedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS)))throw bad();
            var prior=db.query("SELECT request_hash FROM telemetry_batch WHERE id=?",(r,n)->r.getString(1),b.id());
            if(!prior.isEmpty()){if(!prior.getFirst().equals(hash))throw new AccountFailure(409,"TELEMETRY_CONFLICT");return Map.of("id",b.id());}
            long revision=db.queryForObject("SELECT revision FROM server_bridge_boot WHERE boot_id=?",Long.class,b.bootId());
            if(b.revision()<=revision)throw new AccountFailure(409,"STALE_BRIDGE_REVISION");
            var previous=db.queryForObject("SELECT observed_at FROM server_bridge_boot WHERE boot_id=?",Timestamp.class,b.bootId()).toInstant();
            if(b.observedAt().isBefore(previous)||b.uptimeSeconds()<db.queryForObject("SELECT uptime_seconds FROM server_bridge_boot WHERE boot_id=?",Long.class,b.bootId()))throw bad();
            db.update("UPDATE server_bridge_boot SET revision=?,observed_at=?,received_at=?,online=?,uptime_seconds=?,players=?,max_players=?,minecraft_version=?,ccec_version=?,server_version=?,dropped_events=? WHERE boot_id=?",b.revision(),ts(b.observedAt()),ts(now),b.online(),b.uptimeSeconds(),encode(b.players()),b.maxPlayers(),b.minecraftVersion(),b.ccecVersion(),b.serverVersion(),b.droppedEvents(),b.bootId());
        }
        for(Event e:b.events()){
            String eh=Secrets.hash(e.toString());var existing=db.query("SELECT request_hash,boot_id FROM telemetry_event WHERE id=?",(r,n)->Map.entry(r.getString(1),r.getObject(2,UUID.class)),e.id());
            if(!existing.isEmpty()){if(!existing.getFirst().equals(Map.entry(eh,b.bootId())))throw new AccountFailure(409,"TELEMETRY_CONFLICT");continue;}
            UUID user=platform(e.minecraftUuid());
            db.update("INSERT INTO telemetry_event(id,boot_id,platform_user_id,minecraft_uuid,type,subject_id,detail,occurred_at,received_at,duration_seconds,request_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?)",e.id(),b.bootId(),user,e.minecraftUuid(),e.type(),e.subjectId(),e.detail(),ts(e.occurredAt()),ts(now),e.durationSeconds(),eh);
            if(e.type().startsWith("SESSION_")){
                var session=db.query("SELECT minecraft_uuid,boot_id FROM telemetry_session WHERE id=? FOR UPDATE",(r,n)->Map.entry(r.getObject(1,UUID.class),r.getObject(2,UUID.class)),e.subjectId());
                if(session.isEmpty())db.update("INSERT INTO telemetry_session(id,boot_id,platform_user_id,minecraft_uuid,started_at,ended_at,duration_seconds) VALUES(?,?,?,?,?,?,?)",e.subjectId(),b.bootId(),user,e.minecraftUuid(),ts(e.occurredAt().minusSeconds(e.durationSeconds())),e.type().equals("SESSION_END")?ts(e.occurredAt()):null,e.durationSeconds());
                else {
                    if(!session.getFirst().equals(Map.entry(e.minecraftUuid(),b.bootId())))throw new AccountFailure(409,"SESSION_CONFLICT");
                    db.update("UPDATE telemetry_session SET duration_seconds=CASE WHEN duration_seconds<? THEN ? ELSE duration_seconds END,ended_at=CASE WHEN ? THEN ? ELSE ended_at END WHERE id=?",e.durationSeconds(),e.durationSeconds(),e.type().equals("SESSION_END"),ts(e.occurredAt()),e.subjectId());
                }
            }
        }
        db.update("INSERT INTO telemetry_batch(id,boot_id,request_hash,received_at) VALUES(?,?,?,?)",b.id(),b.bootId(),hash,ts(now));
        return Map.of("id",b.id());
    }
    private Map<String,Object> latest(){var rows=db.queryForList("SELECT * FROM server_bridge_boot WHERE server_id=? ORDER BY started_at DESC,boot_id DESC LIMIT 1",game.serverId());return rows.isEmpty()?null:rows.getFirst();}
    private Status status(Map<String,Object> row){
        if(row==null)return new Status("UNKNOWN",0,0,0,"","","",null,0);
        Instant received=instant(row.get("received_at"));
        String state=!(Boolean)row.get("online")?"OFFLINE":instant(row.get("observed_at")).isBefore(clock.instant().minusSeconds(90))?"UNKNOWN":"ONLINE";
        return new Status(state,((Number)row.get("uptime_seconds")).longValue(),state.equals("ONLINE")?players((String)row.get("players")).size():0,((Number)row.get("max_players")).intValue(),(String)row.get("minecraft_version"),(String)row.get("ccec_version"),(String)row.get("server_version"),instant(row.get("observed_at")),((Number)row.get("dropped_events")).longValue());
    }
    public Status status(){return status(latest());}
    public Admin admin(long id){admins.require(id);var row=latest();var status=status(row);List<Player> current=new ArrayList<>();
        if(status.state().equals("ONLINE"))for(UUID mc:players((String)row.get("players"))){UUID user=platform(mc);String name=user==null?"legacy":db.queryForObject("SELECT username FROM account_credentials WHERE platform_user_id=?",String.class,user);current.add(new Player(mc,user,name));}
        return new Admin(status,current,db.queryForObject("SELECT COUNT(*) FROM telemetry_event WHERE occurred_at>?",Long.class,ts(clock.instant().minusSeconds(86400))),db.queryForObject("SELECT COUNT(*) FROM telemetry_session",Long.class),db.queryForObject("SELECT COALESCE(SUM(duration_seconds),0) FROM telemetry_session",Long.class));
    }
}
