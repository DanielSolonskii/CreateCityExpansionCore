package dev.createserver.backend.community;
import dev.createserver.backend.account.AccountFailure;
import dev.createserver.backend.game.GameSettings;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
/** Owner-bound notifications and commands only. No public message-history/friends export. */
@RestController
public class CommunityRelayController {
    public record Notice(UUID id,UUID owner,UUID peer,String name,String kind,String text,UUID city,UUID token,long created,long after){}
    public record Ack(UUID id,String result){}
    public record Policy(UUID owner,boolean enabled,boolean preview,boolean offlineOnly,boolean online){}
    public record Exchange(String serverId,List<Notice> notices,List<Ack> results,List<Policy> policies){public Exchange(String serverId,List<Notice> notices,List<Ack> results){this(serverId,notices,results,List.of());}}
    public record Command(UUID id,UUID actor,UUID target,String kind,String text,UUID token){}
    public record ExchangeResult(List<UUID> accepted,List<Command> commands){}
    public record Delivery(UUID id,long telegramUserId,UUID attemptToken,String peer,String kind,String text,boolean invitation){}
    public record DeliveryAck(UUID id,UUID attemptToken,String outcome){}
    public record BotAction(long telegramUserId,UUID notice,String action,String text,UUID request){}
    private final JdbcTemplate db;private final GameSettings game;private final Clock clock;
    public CommunityRelayController(JdbcTemplate db,GameSettings game,Clock clock){this.db=db;this.game=game;this.clock=clock;}
    private Timestamp ts(Instant v){return Timestamp.from(v);}private void lock(){db.queryForObject("SELECT id FROM broadcast_lock WHERE id=1 FOR UPDATE",Integer.class);}
    @PostMapping("/api/internal/game/community") @Transactional
    public ExchangeResult exchange(@RequestBody Exchange e){
        if(e==null||!game.serverId().equals(e.serverId())||e.notices()==null||e.notices().size()>16||e.results()==null||e.results().size()>16||e.policies()==null||e.policies().size()>1000)throw new AccountFailure(400,"INVALID_COMMUNITY_BATCH");lock();Instant now=clock.instant();
        db.update("DELETE FROM community_notice WHERE created_at<?",ts(now.minusSeconds(7*86400)));db.update("DELETE FROM community_command WHERE created_at<?",ts(now.minusSeconds(7*86400)));
        db.update("DELETE FROM community_event_receipt WHERE created_at<?",ts(now.minusSeconds(7*86400)));
        for(var p:e.policies()){if(p==null||p.owner()==null)throw new AccountFailure(400,"INVALID_COMMUNITY_POLICY");db.update("DELETE FROM community_policy WHERE owner_uuid=?",p.owner());db.update("INSERT INTO community_policy(owner_uuid,enabled,preview,offline_only,online,updated_at) VALUES(?,?,?,?,?,?)",p.owner(),p.enabled(),p.preview(),p.offlineOnly(),p.online(),ts(now));if(!p.enabled())db.update("UPDATE community_notice SET state='SUPPRESSED' WHERE owner_uuid=? AND state='PENDING'",p.owner());}
        var accepted=new ArrayList<UUID>();for(var n:e.notices()){
            if(n==null||n.id()==null||n.owner()==null||n.peer()==null||n.name()==null||n.name().length()>64||n.text()==null||n.text().length()>500||!Set.of("MESSAGE","INVITE").contains(Objects.toString(n.kind(),""))||n.created()>now.toEpochMilli()+30000||n.created()<now.minusSeconds(7*86400).toEpochMilli()||n.after()<n.created()||n.after()>now.plusSeconds(86400).toEpochMilli())throw new AccountFailure(400,"INVALID_COMMUNITY_NOTICE");
            if(db.queryForObject("SELECT COUNT(*) FROM community_event_receipt WHERE id=?",Integer.class,n.id())>0){accepted.add(n.id());continue;}
            var recipients=db.query("SELECT t.telegram_user_id FROM minecraft_identity m JOIN telegram_identity t ON t.platform_user_id=m.platform_user_id JOIN platform_user u ON u.id=m.platform_user_id WHERE m.minecraft_uuid=? AND u.status='ACTIVE'",(r,i)->r.getLong(1),n.owner());
            if(!recipients.isEmpty()&&db.queryForObject("SELECT COUNT(*) FROM community_notice WHERE id=?",Integer.class,n.id())==0){if(db.queryForObject("SELECT COUNT(*) FROM community_notice WHERE state='PENDING'",Integer.class)>=10000)throw new AccountFailure(429,"COMMUNITY_QUEUE_FULL");
                int combined=n.kind().equals("MESSAGE")?db.update("UPDATE community_notice SET text=?,peer_name=?,available_at=? WHERE owner_uuid=? AND peer_uuid=? AND kind='MESSAGE' AND state='PENDING' AND created_at>?",n.text(),n.name(),ts(Instant.ofEpochMilli(n.after())),n.owner(),n.peer(),ts(now.minusSeconds(10))):0;
                if(combined==0)db.update("INSERT INTO community_notice(id,owner_uuid,telegram_user_id,peer_uuid,peer_name,kind,text,city_uuid,invite_token,created_at,available_at,state) VALUES(?,?,?,?,?,?,?,?,?,?,?,'PENDING')",n.id(),n.owner(),recipients.getFirst(),n.peer(),n.name(),n.kind(),n.text(),n.city(),n.token(),ts(Instant.ofEpochMilli(n.created())),ts(Instant.ofEpochMilli(n.after())));
            }db.update("INSERT INTO community_event_receipt(id,created_at) VALUES(?,?)",n.id(),ts(now));accepted.add(n.id());
        }
        var results=new ArrayList<>(e.results());db.query("SELECT id FROM community_command WHERE state='PENDING' AND created_at<? ORDER BY created_at,id LIMIT 16",(r,i)->r.getObject(1,UUID.class),ts(now.minusSeconds(300))).forEach(id->results.add(new Ack(id,"stale")));
        for(var a:results){if(a==null||a.id()==null||a.result()==null||!Set.of("ok","duplicate","permission","stale","state","membership","invalid","unknown","full","self").contains(a.result()))throw new AccountFailure(400,"INVALID_COMMUNITY_ACK");int changed=db.update("UPDATE community_command SET state='DONE',result=? WHERE id=? AND state='PENDING'",a.result(),a.id());
            if(changed==1){var completed=db.query("SELECT c.actor_uuid,c.target_uuid,t.telegram_user_id FROM community_command c JOIN minecraft_identity m ON m.minecraft_uuid=c.actor_uuid JOIN telegram_identity t ON t.platform_user_id=m.platform_user_id JOIN platform_user u ON u.id=m.platform_user_id WHERE c.id=? AND u.status='ACTIVE'",(r,i)->new Object[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getLong(3)},a.id());
                if(!completed.isEmpty()&&db.queryForObject("SELECT COUNT(*) FROM community_notice WHERE id=?",Integer.class,a.id())==0){var row=completed.getFirst();String text=Set.of("ok","duplicate").contains(a.result())?"Игровой сервер подтвердил действие.":"Игровой сервер отклонил действие: "+a.result()+". Проверьте дружбу, членство и актуальность приглашения в игре.";
                    db.update("INSERT INTO community_notice(id,owner_uuid,telegram_user_id,peer_uuid,peer_name,kind,text,created_at,available_at,state) VALUES(?,?,?,?,?,'RESULT',?,?,?,'PENDING')",a.id(),row[0],row[2],row[1],"",text,ts(now),ts(now));}
            }}

        var commands=db.query("SELECT c.id,c.actor_uuid,c.target_uuid,c.kind,c.text,c.invite_token FROM community_command c JOIN minecraft_identity m ON m.minecraft_uuid=c.actor_uuid JOIN platform_user u ON u.id=m.platform_user_id JOIN telegram_identity t ON t.platform_user_id=u.id AND t.telegram_user_id=c.telegram_user_id WHERE c.state='PENDING' AND u.status='ACTIVE' ORDER BY c.created_at,c.id LIMIT 16",(r,i)->new Command(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getString(4),r.getString(5),r.getObject(6,UUID.class)));
        return new ExchangeResult(accepted,commands);
    }
    @PostMapping("/api/internal/community/claim") @Transactional
    public List<Delivery> claim(){lock();Instant now=clock.instant();db.update("UPDATE community_notice SET state='UNCERTAIN' WHERE state='SENDING' AND started_at<?",ts(now.minusSeconds(120)));
        var rows=db.query("SELECT n.id,n.telegram_user_id,n.peer_name,n.kind,n.text,n.city_uuid,n.invite_token,p.preview FROM community_notice n JOIN community_policy p ON p.owner_uuid=n.owner_uuid JOIN minecraft_identity m ON m.minecraft_uuid=n.owner_uuid JOIN platform_user u ON u.id=m.platform_user_id JOIN telegram_identity t ON t.platform_user_id=u.id AND t.telegram_user_id=n.telegram_user_id WHERE n.state='PENDING' AND n.available_at<=? AND p.enabled=TRUE AND p.updated_at>? AND (p.offline_only=FALSE OR p.online=FALSE OR n.kind='RESULT') AND u.status='ACTIVE' ORDER BY n.created_at,n.id LIMIT 3",(r,i)->new Delivery(r.getObject(1,UUID.class),r.getLong(2),UUID.randomUUID(),r.getString(3),r.getString(4),r.getBoolean(8)||!r.getString(4).equals("MESSAGE")?r.getString(5):"",r.getObject(6)!=null&&r.getObject(7)!=null),ts(now),ts(now.minusSeconds(90)));
        for(var n:rows)db.update("UPDATE community_notice SET state='SENDING',attempt_token=?,started_at=? WHERE id=?",n.attemptToken(),ts(now),n.id());return rows;
    }
    @PostMapping("/api/internal/community/ack") @Transactional
    public Map<String,Boolean> ack(@RequestBody DeliveryAck a){if(a==null||a.id()==null||a.attemptToken()==null||!Set.of("SENT","FAILED","UNCERTAIN").contains(Objects.toString(a.outcome(),"")))throw new AccountFailure(400,"INVALID_ACK");
        int n=db.update("UPDATE community_notice SET state=? WHERE id=? AND attempt_token=? AND state='SENDING'",a.outcome(),a.id(),a.attemptToken());if(n==0&&db.queryForObject("SELECT COUNT(*) FROM community_notice WHERE id=? AND attempt_token=? AND state=?",Integer.class,a.id(),a.attemptToken(),a.outcome())==0)throw new AccountFailure(409,"DELIVERY_CONFLICT");return Map.of("accepted",true);}
    @PostMapping("/api/internal/community/action") @Transactional
    public Map<String,String> action(@RequestBody BotAction a){if(a==null||a.notice()==null||a.request()==null||a.text()==null||a.text().length()>500||!Set.of("REPLY","ACCEPT","DECLINE","MUTE").contains(Objects.toString(a.action(),"")))throw new AccountFailure(400,"INVALID_COMMUNITY_ACTION");lock();
        var rows=db.query("SELECT n.owner_uuid,n.peer_uuid,n.kind,n.city_uuid,n.invite_token FROM community_notice n JOIN minecraft_identity m ON m.minecraft_uuid=n.owner_uuid JOIN platform_user u ON u.id=m.platform_user_id JOIN telegram_identity t ON t.platform_user_id=u.id AND t.telegram_user_id=n.telegram_user_id WHERE n.id=? AND n.telegram_user_id=? AND u.status='ACTIVE' AND n.created_at>?",(r,i)->new Object[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getObject(4,UUID.class),r.getObject(5,UUID.class)},a.notice(),a.telegramUserId(),ts(clock.instant().minusSeconds(7*86400)));
        if(rows.isEmpty())throw new AccountFailure(403,"COMMUNITY_OWNER_REQUIRED");var row=rows.getFirst();UUID actor=(UUID)row[0],target=(UUID)row[1],token=null;String kind=a.action();
        if(kind.equals("REPLY")&&!row[2].equals("MESSAGE")||Set.of("ACCEPT","DECLINE").contains(kind)&&(!row[2].equals("INVITE")||row[3]==null||row[4]==null))throw new AccountFailure(409,"COMMUNITY_ACTION_UNAVAILABLE");
        if(kind.equals("REPLY")&&(a.text().isBlank()||a.text().chars().anyMatch(c->Character.isISOControl(c)||c==167)))throw new AccountFailure(400,"INVALID_COMMUNITY_TEXT");
        if(kind.equals("ACCEPT")||kind.equals("DECLINE")){target=(UUID)row[3];token=(UUID)row[4];}
        var existing=db.query("SELECT result FROM community_command WHERE id=? AND actor_uuid=?",(r,i)->r.getString(1),a.request(),actor);if(!existing.isEmpty())return Map.of("status",existing.getFirst().isBlank()?"QUEUED":existing.getFirst());
        if(db.queryForObject("SELECT COUNT(*) FROM community_command WHERE actor_uuid=? AND created_at>?",Integer.class,actor,ts(clock.instant().minusSeconds(60)))>=10)throw new AccountFailure(429,"COMMUNITY_RATE_LIMIT");
        db.update("INSERT INTO community_command(id,actor_uuid,telegram_user_id,target_uuid,kind,text,invite_token,created_at,state,result) VALUES(?,?,?,?,?,?,?,?,'PENDING','')",a.request(),actor,a.telegramUserId(),target,kind,a.text(),token,ts(clock.instant()));return Map.of("status","QUEUED");
    }
}
