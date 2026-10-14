package dev.createserver.backend.report;

import dev.createserver.backend.account.*;
import dev.createserver.backend.game.GameSettings;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {
    private final JdbcTemplate db; private final Clock clock; private final ReportSettings settings; private final GameSettings game;
    public ReportService(JdbcTemplate db,Clock clock,ReportSettings settings,GameSettings game){this.db=db;this.clock=clock;this.settings=settings;this.game=game;}
    public record Submission(UUID commandId,UUID minecraftUuid,String serverId,String text,String dimension,double x,double y,double z,Instant occurredAt) {}
    public record Report(UUID id,UUID platformUserId,UUID minecraftUuid,String username,String serverId,String text,String dimension,double x,double y,double z,Instant occurredAt,Instant createdAt,String status,Long assignedAdmin) {}
    public record Action(UUID commandId,long telegramUserId,String action) {}
    public record PlayerCard(UUID platformUserId,UUID minecraftUuid,String username,String status,String joinName,boolean telegramBound) {}
    public record Delivery(UUID reportId,UUID leaseToken,Report report) {}
    private static Timestamp ts(Instant t){return Timestamp.from(t);}
    private AccountFailure fail(int status,String code){return new AccountFailure(status,code);}
    private Report map(ResultSet r,int index)throws SQLException{return new Report(r.getObject("id",UUID.class),r.getObject("platform_user_id",UUID.class),r.getObject("minecraft_uuid",UUID.class),r.getString("username"),r.getString("server_id"),r.getString("text"),r.getString("dimension"),r.getDouble("x"),r.getDouble("y"),r.getDouble("z"),r.getTimestamp("occurred_at").toInstant(),r.getTimestamp("created_at").toInstant(),r.getString("status"),(Long)r.getObject("assigned_admin"));}
    private Report report(UUID id,boolean lock){
        var rows=db.query("SELECT * FROM player_report WHERE id=?"+(lock?" FOR UPDATE":""),this::map,id);
        if(rows.isEmpty())throw fail(404,"REPORT_NOT_FOUND");return rows.getFirst();
    }
    @Transactional public Report submit(Submission s){
        if(s==null || s.commandId()==null || s.minecraftUuid()==null || s.occurredAt()==null || !game.serverId().equals(s.serverId())
            || s.text()==null || s.text().codePointCount(0,s.text().length())<5 || s.text().length()>1000 || !s.text().equals(s.text().strip())
            || s.text().chars().anyMatch(c->Character.isISOControl(c) && c!='\n') || s.dimension()==null || !s.dimension().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || s.dimension().length()>128
            || !Double.isFinite(s.x()) || !Double.isFinite(s.y()) || !Double.isFinite(s.z()) || Math.abs(s.x())>30_000_000 || Math.abs(s.z())>30_000_000 || Math.abs(s.y())>30_000_000)
            throw fail(400,"INVALID_REPORT");
        String hash=Secrets.hash(s.toString());
        // Lock the platform actor, serializing cooldown and duplicate submissions across instances.
        var actors=db.query("SELECT u.id,a.username FROM platform_user u JOIN minecraft_identity m ON m.platform_user_id=u.id JOIN account_credentials a ON a.platform_user_id=u.id WHERE m.minecraft_uuid=? AND u.status='ACTIVE' FOR UPDATE",(r,n)->Map.entry(r.getObject(1,UUID.class),r.getString(2)),s.minecraftUuid());
        if(actors.isEmpty())throw fail(403,"REPORT_IDENTITY_UNAVAILABLE");var actor=actors.getFirst();
        var old=db.query("SELECT request_hash FROM player_report WHERE id=?",(r,n)->r.getString(1),s.commandId());
        if(!old.isEmpty()){if(!old.getFirst().equals(hash))throw fail(409,"REPORT_COMMAND_CONFLICT");return report(s.commandId(),false);}
        Instant now=clock.instant();
        if(s.occurredAt().isBefore(now.minusSeconds(600)) || s.occurredAt().isAfter(now.plusSeconds(60)))throw fail(400,"INVALID_REPORT_TIME");
        int recent=db.queryForObject("SELECT COUNT(*) FROM player_report WHERE platform_user_id=? AND created_at>?",Integer.class,actor.getKey(),ts(now.minusSeconds(60)));
        int hourly=db.queryForObject("SELECT COUNT(*) FROM player_report WHERE platform_user_id=? AND created_at>?",Integer.class,actor.getKey(),ts(now.minusSeconds(3600)));
        if(recent>0 || hourly>=10)throw fail(429,"REPORT_COOLDOWN");
        db.update("INSERT INTO player_report(id,platform_user_id,minecraft_uuid,username,server_id,text,dimension,x,y,z,occurred_at,created_at,updated_at,request_hash,status) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,'OPEN')",
            s.commandId(),actor.getKey(),s.minecraftUuid(),actor.getValue(),s.serverId(),s.text(),s.dimension(),s.x(),s.y(),s.z(),ts(s.occurredAt()),ts(now),ts(now),hash);
        for(long admin:settings.admins())db.update("INSERT INTO report_notification(report_id,admin_telegram_id,created_at) VALUES(?,?,?)",s.commandId(),admin,ts(now));
        return report(s.commandId(),false);
    }
    public record Summary(long openReports){}
    public Summary summary(long admin){settings.require(admin);return new Summary(db.queryForObject("SELECT COUNT(*) FROM player_report WHERE status IN ('OPEN','IN_PROGRESS')",Long.class));}
    public List<Report> open(long admin,int offset){settings.require(admin);if(offset<0 || offset>10000)throw fail(400,"INVALID_PAGE");return db.query("SELECT * FROM player_report WHERE status IN ('OPEN','IN_PROGRESS') ORDER BY created_at,id LIMIT 10 OFFSET ?",this::map,offset);}
    public Report get(long admin,UUID id){settings.require(admin);return report(id,false);}
    @Transactional public Report action(UUID id,Action a){
        if(a==null)throw fail(400,"INVALID_ACTION");settings.require(a.telegramUserId());
        if(a.commandId()==null || a.action()==null || !Set.of("TAKE","CLOSE","REJECT").contains(a.action()))throw fail(400,"INVALID_ACTION");
        Report r=report(id,true);
        var prior=db.query("SELECT report_id,admin_telegram_id,action FROM report_admin_action WHERE id=?",(rs,n)->List.of(rs.getObject(1,UUID.class),rs.getLong(2),rs.getString(3)),a.commandId());
        if(!prior.isEmpty()){if(!prior.getFirst().equals(List.of(id,a.telegramUserId(),a.action())))throw fail(409,"ACTION_CONFLICT");return r;}
        if(!Set.of("OPEN","IN_PROGRESS").contains(r.status()) || (r.assignedAdmin()!=null && r.assignedAdmin()!=a.telegramUserId()))throw fail(409,"REPORT_STATE_CONFLICT");
        if(a.action().equals("TAKE") && !r.status().equals("OPEN"))throw fail(409,"REPORT_STATE_CONFLICT");
        String next=switch(a.action()){case "TAKE"->"IN_PROGRESS";case "CLOSE"->"CLOSED";default->"REJECTED";};Instant now=clock.instant();
        db.update("UPDATE player_report SET status=?,assigned_admin=?,updated_at=? WHERE id=?",next,a.telegramUserId(),ts(now),id);
        db.update("INSERT INTO report_admin_action(id,report_id,admin_telegram_id,action,previous_status,resulting_status,created_at) VALUES(?,?,?,?,?,?,?)",a.commandId(),id,a.telegramUserId(),a.action(),r.status(),next,ts(now));
        return report(id,false);
    }
    public PlayerCard player(long admin,UUID id){settings.require(admin);Report r=report(id,false);return db.queryForObject("SELECT u.status,a.username,m.join_name,EXISTS(SELECT 1 FROM telegram_identity t WHERE t.platform_user_id=u.id) AS bound FROM platform_user u JOIN account_credentials a ON a.platform_user_id=u.id JOIN minecraft_identity m ON m.platform_user_id=u.id WHERE u.id=?",(rs,n)->new PlayerCard(r.platformUserId(),r.minecraftUuid(),rs.getString("username"),rs.getString("status"),rs.getString("join_name"),rs.getBoolean("bound")),r.platformUserId());}
    @Transactional public List<Delivery> claim(long admin){
        settings.require(admin);Instant now=clock.instant();
        var ids=db.query("SELECT report_id FROM report_notification WHERE admin_telegram_id=? AND delivered_at IS NULL AND (lease_until IS NULL OR lease_until<=?) ORDER BY created_at,report_id LIMIT 5 FOR UPDATE",(r,n)->r.getObject(1,UUID.class),admin,ts(now));
        List<Delivery> deliveries=new ArrayList<>();
        for(UUID id:ids){UUID token=UUID.randomUUID();db.update("UPDATE report_notification SET lease_token=?,lease_until=? WHERE report_id=? AND admin_telegram_id=?",token,ts(now.plusSeconds(120)),id,admin);deliveries.add(new Delivery(id,token,report(id,false)));}
        return deliveries;
    }
    @Transactional public void acknowledge(long admin,UUID id,UUID token){
        settings.require(admin);if(token==null)throw fail(400,"INVALID_DELIVERY");
        int count=db.update("UPDATE report_notification SET delivered_at=? WHERE report_id=? AND admin_telegram_id=? AND lease_token=? AND delivered_at IS NULL AND lease_until>?",ts(clock.instant()),id,admin,token,ts(clock.instant()));
        if(count==0 && db.queryForObject("SELECT COUNT(*) FROM report_notification WHERE report_id=? AND admin_telegram_id=? AND lease_token=? AND delivered_at IS NOT NULL",Integer.class,id,admin,token)==0)throw fail(409,"DELIVERY_CONFLICT");
    }
}
