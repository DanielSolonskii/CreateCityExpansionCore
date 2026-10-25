package dev.createserver.backend.broadcast;

import dev.createserver.backend.account.AccountFailure;
import dev.createserver.backend.report.ReportSettings;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Serializes short DB mutations only; no Telegram I/O in a transaction. */
@Service
@Transactional
public class BroadcastService {
    private final JdbcTemplate db; private final Clock clock; private final ReportSettings admins;
    public BroadcastService(JdbcTemplate db, Clock clock, ReportSettings admins){this.db=db;this.clock=clock;this.admins=admins;}
    public record Draft(UUID commandId,long telegramUserId,String text,String photoFileId){}
    public record Post(UUID id,long creatorAdmin,String text,String photoFileId,String status,Instant createdAt,
        long eligible,long total,long sent,long failed,long uncertain,long pending){}
    public record Delivery(UUID postId,long telegramUserId,UUID attemptToken,String text,String photoFileId){}
    public record Ack(long telegramUserId,long recipientId,UUID attemptToken,String outcome,long messageId,int retryAfter){}
    private void lock(long admin){admins.require(admin);db.queryForObject("SELECT id FROM broadcast_lock WHERE id=1 FOR UPDATE",Integer.class);}
    private Timestamp now(){return Timestamp.from(clock.instant());}
    private AccountFailure fail(int status,String code){return new AccountFailure(status,code);}
    public Post create(Draft draft){
        if(draft==null)throw fail(400,"INVALID_POST");lock(draft.telegramUserId());
        String text=draft.text(),photo=draft.photoFileId();
        if(draft.commandId()==null || text==null || text.length()>(photo==null?4000:1024) ||
            text.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t') ||
            (photo==null&&text.isBlank()) || (photo!=null&&!photo.matches("[A-Za-z0-9_-]{1,512}")))throw fail(400,"INVALID_POST");
        var existing=db.queryForList("SELECT id FROM broadcast_post WHERE id=?",draft.commandId());
        if(!existing.isEmpty()){
            Post p=read(draft.commandId());if(p.creatorAdmin()!=draft.telegramUserId() || !p.text().equals(text)||!Objects.equals(p.photoFileId(),photo))throw fail(409,"POST_CONFLICT");return p;
        }
        if(db.queryForObject("SELECT COUNT(*) FROM broadcast_post WHERE creator_admin=? AND created_at>?",Long.class,draft.telegramUserId(),Timestamp.from(clock.instant().minusSeconds(3600)))>=10)throw fail(429,"POST_LIMIT");
        db.update("INSERT INTO broadcast_post(id,creator_admin,text,photo_file_id,status,created_at) VALUES(?,?,?,?,'DRAFT',?)",draft.commandId(),draft.telegramUserId(),text,photo,now());
        return read(draft.commandId());
    }
    public Post publish(long admin,UUID id){
        lock(admin);Post p=read(id);
        if(p.status().equals("QUEUED")||p.status().equals("COMPLETED"))return p;
        if(!p.status().equals("DRAFT"))throw fail(409,"POST_NOT_DRAFT");
        boolean maintenance=p.text().strip().toLowerCase(Locale.ROOT).startsWith("#maintenance")||p.text().strip().toLowerCase(Locale.ROOT).startsWith("технические работы");
        db.update("INSERT INTO broadcast_delivery(post_id,telegram_user_id,state,next_attempt_at) SELECT ?,t.telegram_user_id,'PENDING',? FROM telegram_identity t JOIN platform_user u ON u.id=t.platform_user_id LEFT JOIN companion_preferences pref ON pref.platform_user_id=u.id WHERE u.status='ACTIVE' AND COALESCE("+(maintenance?"pref.maintenance":"pref.news")+",TRUE)=TRUE",id,now());
        db.update("UPDATE broadcast_post SET status='QUEUED',confirmed_at=? WHERE id=?",now(),id);complete();return read(id);
    }
    public Post cancel(long admin,UUID id){lock(admin);Post p=read(id);if(!Set.of("DRAFT","CANCELLED").contains(p.status()))throw fail(409,"POST_NOT_DRAFT");db.update("UPDATE broadcast_post SET status='CANCELLED' WHERE id=?",id);return read(id);}
    public Post get(long admin,UUID id){admins.require(admin);return read(id);}
    public List<Post> list(long admin){admins.require(admin);return db.query("SELECT id FROM broadcast_post ORDER BY created_at DESC LIMIT 10",(rs,n)->rs.getObject(1,UUID.class)).stream().map(this::read).toList();}
    private long count(UUID id,String state){return db.queryForObject("SELECT COUNT(*) FROM broadcast_delivery WHERE post_id=?"+(state==null?"":" AND state=?"),Long.class,state==null?new Object[]{id}:new Object[]{id,state});}
    private Post read(UUID id){
        var rows=db.query("SELECT * FROM broadcast_post WHERE id=?",(rs,n)->new Post(rs.getObject("id",UUID.class),rs.getLong("creator_admin"),rs.getString("text"),rs.getString("photo_file_id"),rs.getString("status"),rs.getTimestamp("created_at").toInstant(),0,0,0,0,0,0),id);
        if(rows.isEmpty())throw fail(404,"POST_NOT_FOUND");Post p=rows.getFirst();
        long eligible=db.queryForObject("SELECT COUNT(*) FROM telegram_identity t JOIN platform_user u ON u.id=t.platform_user_id WHERE u.status='ACTIVE'",Long.class);
        return new Post(p.id(),p.creatorAdmin(),p.text(),p.photoFileId(),p.status(),p.createdAt(),eligible,count(id,null),count(id,"SENT"),count(id,"FAILED"),count(id,"UNCERTAIN"),count(id,"PENDING")+count(id,"SENDING"));
    }
    private void complete(){db.update("UPDATE broadcast_post SET status='COMPLETED' WHERE status='QUEUED' AND NOT EXISTS (SELECT 1 FROM broadcast_delivery d WHERE d.post_id=broadcast_post.id AND d.state IN ('PENDING','SENDING'))");}
    public List<Delivery> claim(long admin){
        lock(admin);
        // A lost Telegram response cannot safely be retried: Bot API has no idempotency key.
        db.update("UPDATE broadcast_delivery SET state='UNCERTAIN',failure_code='LEASE_EXPIRED' WHERE state='SENDING' AND started_at<?",Timestamp.from(clock.instant().minusSeconds(120)));
        complete();
        var rows=db.query("SELECT d.post_id,d.telegram_user_id,p.text,p.photo_file_id FROM broadcast_delivery d JOIN broadcast_post p ON p.id=d.post_id WHERE p.status='QUEUED' AND d.state='PENDING' AND d.next_attempt_at<=? ORDER BY p.created_at,d.telegram_user_id LIMIT 3",(rs,n)->new Delivery(rs.getObject(1,UUID.class),rs.getLong(2),UUID.randomUUID(),rs.getString(3),rs.getString(4)),now());
        for(var d:rows)db.update("UPDATE broadcast_delivery SET state='SENDING',attempt_token=?,started_at=? WHERE post_id=? AND telegram_user_id=?",d.attemptToken(),now(),d.postId(),d.telegramUserId());
        return rows;
    }
    public void acknowledge(UUID id,Ack ack){
        if(ack==null)throw fail(400,"INVALID_ACK");lock(ack.telegramUserId());
        if(ack.attemptToken()==null||!Set.of("SENT","FAILED","UNCERTAIN","RETRY").contains(Objects.toString(ack.outcome(),""))||
            (ack.outcome().equals("SENT")&&ack.messageId()<=0)||ack.retryAfter()<0||ack.retryAfter()>86400)throw fail(400,"INVALID_ACK");
        var rows=db.queryForList("SELECT state,attempt_token FROM broadcast_delivery WHERE post_id=? AND telegram_user_id=?",id,ack.recipientId());
        if(rows.isEmpty()||!ack.attemptToken().equals(rows.getFirst().get("attempt_token")))throw fail(409,"DELIVERY_CONFLICT");
        String state=rows.getFirst().get("state").toString();
        if(!state.equals("SENDING")){
            if(state.equals(ack.outcome()) || (state.equals("PENDING")&&ack.outcome().equals("RETRY")))return;
            throw fail(409,"DELIVERY_CONFLICT");
        }
        db.update("UPDATE broadcast_delivery SET state=?,telegram_message_id=?,failure_code=?,next_attempt_at=? WHERE post_id=? AND telegram_user_id=?",
            ack.outcome().equals("RETRY")?"PENDING":ack.outcome(),ack.outcome().equals("SENT")?ack.messageId():null,
            ack.outcome().equals("SENT")?null:ack.outcome(),Timestamp.from(clock.instant().plusSeconds(Math.max(1,ack.retryAfter()))),id,ack.recipientId());complete();
    }
}
