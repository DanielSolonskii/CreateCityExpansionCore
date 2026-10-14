package dev.createserver.backend.broadcast;
import dev.createserver.backend.account.AccountFailure;
import java.net.*;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

abstract class BroadcastContract {
    @Autowired BroadcastService service;@Autowired JdbcTemplate db;@LocalServerPort int port;
    List<UUID> users=new ArrayList<>();long recipient;
    @BeforeEach void setup(){
        recipient=1_000_000_000L+Math.floorMod(UUID.randomUUID().getLeastSignificantBits(),1_000_000_000L);
        fixture(recipient,"ACTIVE");fixture(recipient+1,"DISABLED");fixture(recipient+2,"PENDING_VERIFICATION");
    }
    void fixture(long telegram,String status){UUID id=UUID.randomUUID();users.add(id);var now=Timestamp.from(Instant.now());db.update("INSERT INTO platform_user(id,status,created_at,updated_at) VALUES(?,?,?,?)",id,status,now,now);db.update("INSERT INTO telegram_identity(telegram_user_id,platform_user_id,verified_at) VALUES(?,?,?)",telegram,id,now);}
    @AfterEach void clean(){
        db.update("DELETE FROM broadcast_delivery");db.update("DELETE FROM broadcast_post");
        for(UUID id:users){db.update("DELETE FROM telegram_identity WHERE platform_user_id=?",id);db.update("DELETE FROM platform_user WHERE id=?",id);}
    }
    BroadcastService.Post draft(){return service.create(new BroadcastService.Draft(UUID.randomUUID(),111,"Новости <alpha>","Photo_123"));}
    void failure(int status,Runnable fn){assertThatThrownBy(fn::run).isInstanceOfSatisfying(AccountFailure.class,e->assertThat(e.status()).isEqualTo(status));}
    @Test void validationAndNoSendBeforeExplicitConfirmation(){var p=draft();assertThat(p.status()).isEqualTo("DRAFT");assertThat(service.claim(111)).isEmpty();assertThat(p.total()).isZero();for(var req:List.of(new BroadcastService.Draft(UUID.randomUUID(),111,"",null),new BroadcastService.Draft(UUID.randomUUID(),111,"x".repeat(1025),"photo"),new BroadcastService.Draft(UUID.randomUUID(),111,"text","https://unsafe/photo"),new BroadcastService.Draft(UUID.randomUUID(),111,"bad\u0000",null)))failure(400,()->service.create(req));}
    @Test void draftReplayImmutableAndCancel(){UUID id=UUID.randomUUID();var req=new BroadcastService.Draft(id,111,"Новости",null);assertThat(service.create(req)).isEqualTo(service.create(req));failure(409,()->service.create(new BroadcastService.Draft(id,111,"Changed",null)));service.cancel(111,id);failure(409,()->service.publish(111,id));assertThat(service.claim(111)).isEmpty();}
    @Test void publishOnlyActiveLinkedSnapshotAndRepeatedConfirmationSafe(){var p=draft();service.publish(111,p.id());service.publish(222,p.id());assertThat(db.queryForObject("SELECT COUNT(*) FROM broadcast_delivery WHERE post_id=? AND telegram_user_id IN (?,?,?)",Long.class,p.id(),recipient,recipient+1,recipient+2)).isEqualTo(1);failure(409,()->service.cancel(111,p.id()));assertThat(service.get(111,p.id()).total()).isEqualTo(service.get(111,p.id()).eligible());}
    @Test void concurrentConfirmAndClaimDoNotDuplicate()throws Exception{
        UUID id=draft().id();try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->service.publish(111,id));var b=pool.submit(()->service.publish(222,id));assertThat(a.get(10,TimeUnit.SECONDS).total()).isEqualTo(b.get(10,TimeUnit.SECONDS).total());
            var ca=pool.submit(()->service.claim(111));var cb=pool.submit(()->service.claim(222));var first=ca.get(10,TimeUnit.SECONDS);var second=cb.get(10,TimeUnit.SECONDS);Set<Long> seen=new HashSet<>();for(var d:first)assertThat(seen.add(d.telegramUserId())).isTrue();for(var d:second)assertThat(seen.add(d.telegramUserId())).isTrue();}
        assertThat(db.queryForObject("SELECT COUNT(*) FROM broadcast_delivery WHERE post_id=? AND telegram_user_id=?",Long.class,id,recipient)).isEqualTo(1);
    }
    @Test void ackReplayTokenIsolationRetryAndCompletion(){
        UUID id=draft().id();service.publish(111,id);var deliveries=service.claim(111);var d=deliveries.stream().filter(x->x.telegramUserId()==recipient).findFirst().orElseThrow();
        failure(409,()->service.acknowledge(id,new BroadcastService.Ack(111,recipient,UUID.randomUUID(),"SENT",9,0)));
        var retry=new BroadcastService.Ack(111,recipient,d.attemptToken(),"RETRY",0,60);service.acknowledge(id,retry);service.acknowledge(id,retry);assertThat(service.claim(111)).noneMatch(x->x.telegramUserId()==recipient);
        db.update("UPDATE broadcast_delivery SET next_attempt_at=? WHERE post_id=?",Timestamp.from(Instant.now().minusSeconds(1)),id);
        var next=service.claim(111).stream().filter(x->x.telegramUserId()==recipient).findFirst().orElseThrow();assertThat(next.attemptToken()).isNotEqualTo(d.attemptToken());failure(409,()->service.acknowledge(id,retry));
        var sent=new BroadcastService.Ack(111,recipient,next.attemptToken(),"SENT",99,0);service.acknowledge(id,sent);service.acknowledge(id,sent);assertThat(service.get(111,id).sent()).isEqualTo(1);
        db.update("UPDATE broadcast_delivery SET state='FAILED' WHERE post_id=? AND telegram_user_id<>?",id,recipient);service.claim(111);assertThat(service.get(111,id).status()).isEqualTo("COMPLETED");
    }
    @Test void crashExpiredLeaseIsUncertainNeverRetried(){UUID id=draft().id();service.publish(111,id);service.claim(111);db.update("UPDATE broadcast_delivery SET started_at=? WHERE post_id=?",Timestamp.from(Instant.now().minusSeconds(121)),id);assertThat(service.claim(111)).isEmpty();assertThat(service.get(111,id).uncertain()).isPositive();assertThat(service.get(111,id).status()).isEqualTo("COMPLETED");}
    @Test void ordinaryUserDeniedAndSchemaConstraints(){var p=draft();failure(403,()->service.list(333));failure(403,()->service.get(333,p.id()));failure(403,()->service.publish(333,p.id()));failure(403,()->service.cancel(333,p.id()));failure(403,()->service.claim(333));failure(403,()->service.create(new BroadcastService.Draft(UUID.randomUUID(),333,"text",null)));failure(403,()->service.acknowledge(p.id(),new BroadcastService.Ack(333,recipient,UUID.randomUUID(),"SENT",2,0)));assertThatThrownBy(()->db.update("UPDATE broadcast_post SET status='BAD' WHERE id=?",p.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
    @Test void serviceAuthenticationAndNumericAdminRequiredOverHttp()throws Exception{
        var http=HttpClient.newHttpClient();String url="http://127.0.0.1:"+port+"/api/internal/broadcasts?telegramUserId=";
        assertThat(http.send(HttpRequest.newBuilder(URI.create(url+111)).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);
        assertThat(http.send(HttpRequest.newBuilder(URI.create(url+333)).header("X-Bot-Service-Secret","test-only-bot-service-secret-at-least-32").GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
        assertThat(http.send(HttpRequest.newBuilder(URI.create(url+111)).header("X-Bot-Service-Secret","test-only-bot-service-secret-at-least-32").GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
    }
}
