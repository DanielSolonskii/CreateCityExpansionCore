package dev.createserver.backend.miniapp;
import dev.createserver.backend.account.AccountFailure;
import dev.createserver.backend.report.ReportSettings;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Small durable report-update outbox. Ambiguous sends are not automatically repeated. */
@RestController @RequestMapping("/api/internal/companion/notices")
public class CompanionNoticeController {
    private final JdbcTemplate db;private final ReportSettings admins;private final Clock clock;
    public CompanionNoticeController(JdbcTemplate db,ReportSettings admins,Clock clock){this.db=db;this.admins=admins;this.clock=clock;}
    public record Actor(long telegramUserId){}
    public record Ack(long telegramUserId,UUID attemptToken,String outcome){}
    public record Notice(UUID actionId,long telegramUserId,UUID attemptToken,String text){}
    private Timestamp ts(Instant t){return Timestamp.from(t);}
    @PostMapping("/claim") @Transactional public List<Notice> claim(@RequestBody Actor actor){
        admins.require(actor.telegramUserId());db.queryForObject("SELECT id FROM broadcast_lock WHERE id=1 FOR UPDATE",Integer.class);
        db.update("UPDATE companion_notice SET state='UNCERTAIN' WHERE state='SENDING' AND started_at<?",ts(clock.instant().minusSeconds(120)));
        var rows=db.query("SELECT a.id,t.telegram_user_id,r.id,a.resulting_status FROM report_admin_action a JOIN player_report r ON r.id=a.report_id JOIN telegram_identity t ON t.platform_user_id=r.platform_user_id JOIN platform_user u ON u.id=r.platform_user_id LEFT JOIN companion_preferences p ON p.platform_user_id=u.id WHERE u.status='ACTIVE' AND COALESCE(p.reports,TRUE)=TRUE AND a.created_at>=(SELECT started_at FROM companion_notice_start WHERE id=1) AND a.created_at>? AND NOT EXISTS(SELECT 1 FROM companion_notice n WHERE n.action_id=a.id) ORDER BY a.created_at,a.id LIMIT 3",(r,n)->new Notice(r.getObject(1,UUID.class),r.getLong(2),UUID.randomUUID(),"RE:CREATED · Обращение "+r.getObject(3,UUID.class).toString().substring(0,8)+"\n"+switch(r.getString(4)){case "IN_PROGRESS"->"Взято в работу";case "CLOSED"->"Закрыто";default->"Отклонено";}),ts(clock.instant().minusSeconds(86400)));
        for(Notice n:rows)db.update("INSERT INTO companion_notice(action_id,telegram_user_id,text,state,attempt_token,started_at) VALUES(?,?,?,'SENDING',?,?)",n.actionId(),n.telegramUserId(),n.text(),n.attemptToken(),ts(clock.instant()));
        return rows;
    }
    @PostMapping("/{id}/ack") @Transactional public Map<String,Boolean> ack(@PathVariable UUID id,@RequestBody Ack a){
        admins.require(a.telegramUserId());if(a.attemptToken()==null||!Set.of("SENT","FAILED","UNCERTAIN").contains(Objects.toString(a.outcome(),"")))throw new AccountFailure(400,"INVALID_ACK");
        int changed=db.update("UPDATE companion_notice SET state=? WHERE action_id=? AND attempt_token=? AND state='SENDING'",a.outcome(),id,a.attemptToken());
        if(changed==0&&db.queryForObject("SELECT COUNT(*) FROM companion_notice WHERE action_id=? AND attempt_token=? AND state=?",Integer.class,id,a.attemptToken(),a.outcome())==0)throw new AccountFailure(409,"DELIVERY_CONFLICT");return Map.of("accepted",true);
    }
}
