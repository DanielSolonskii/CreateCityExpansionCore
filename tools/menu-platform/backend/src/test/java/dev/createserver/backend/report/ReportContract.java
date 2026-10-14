package dev.createserver.backend.report;
import dev.createserver.backend.account.AccountFailure;
import java.net.*;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

abstract class ReportContract {
    static final String GAME="test-only-game-service-independent-secret-32",BOT="test-only-bot-service-secret-at-least-32";
    @Autowired ReportService service;@Autowired JdbcTemplate db;@LocalServerPort int port;
    UUID user,mc;ReportService.Submission request;
    @BeforeEach void setup(){
        user=UUID.randomUUID();mc=UUID.randomUUID();Instant now=Instant.now();String username="r"+user.toString().substring(0,8);
        db.update("INSERT INTO platform_user(id,status,created_at,updated_at) VALUES(?,'ACTIVE',?,?)",user,Timestamp.from(now),Timestamp.from(now));
        db.update("INSERT INTO account_credentials(platform_user_id,username,password_hash) VALUES(?,?,?)",user,username,"x".repeat(60));
        db.update("INSERT INTO minecraft_identity(minecraft_uuid,platform_user_id,current_name,join_name) VALUES(?,?,? ,?)",mc,user,username,"cs"+user.toString().replace("-","").substring(0,14));
        request=sub(UUID.randomUUID(),"Сломался поезд");
    }
    ReportService.Submission sub(UUID id,String text){return new ReportService.Submission(id,mc,"test-world",text,"minecraft:overworld",12.5,70,-8.5,Instant.now());}
    @AfterEach void cleanup(){
        db.update("DELETE FROM report_notification WHERE report_id IN (SELECT id FROM player_report WHERE platform_user_id=?)",user);
        db.update("DELETE FROM report_admin_action WHERE report_id IN (SELECT id FROM player_report WHERE platform_user_id=?)",user);
        db.update("DELETE FROM player_report WHERE platform_user_id=?",user);
        db.update("DELETE FROM minecraft_identity WHERE platform_user_id=?",user);db.update("DELETE FROM account_credentials WHERE platform_user_id=?",user);db.update("DELETE FROM platform_user WHERE id=?",user);
    }
    @Test void persistsAuthoritativeIdentityContextAndOutbox(){var r=service.submit(request);assertThat(r.platformUserId()).isEqualTo(user);assertThat(r.minecraftUuid()).isEqualTo(mc);assertThat(r.text()).isEqualTo(request.text());assertThat(r.status()).isEqualTo("OPEN");assertThat(r.dimension()).isEqualTo("minecraft:overworld");assertThat(r.x()).isEqualTo(12.5);assertThat(db.queryForObject("SELECT COUNT(*) FROM report_notification WHERE report_id=?",Integer.class,r.id())).isEqualTo(2);}
    @Test void idempotentReplayAndChangedPayloadConflict(){assertThat(service.submit(request)).isEqualTo(service.submit(request));assertFailure(409,()->service.submit(sub(request.commandId(),"Другой текст")));assertThat(db.queryForObject("SELECT COUNT(*) FROM player_report WHERE platform_user_id=?",Integer.class,user)).isEqualTo(1);}
    @Test void cooldownAndHourlyLimitPersistInDatabase(){service.submit(request);assertFailure(429,()->service.submit(sub(UUID.randomUUID(),"Ещё один репорт")));for(int i=1;i<10;i++){db.update("UPDATE player_report SET created_at=? WHERE platform_user_id=?",Timestamp.from(Instant.now().minusSeconds(70)),user);service.submit(sub(UUID.randomUUID(),"Репорт номер "+i));}db.update("UPDATE player_report SET created_at=? WHERE platform_user_id=?",Timestamp.from(Instant.now().minusSeconds(70)),user);assertFailure(429,()->service.submit(sub(UUID.randomUUID(),"Одиннадцатый репорт")));}
    @Test void concurrentReportsHaveOneWinner()throws Exception{var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->{start.await();return attempt(()->service.submit(sub(UUID.randomUUID(),"Репорт первый")));});var b=pool.submit(()->{start.await();return attempt(()->service.submit(sub(UUID.randomUUID(),"Репорт второй")));});start.countDown();assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,429);}}
    @Test void concurrentDuplicateDeliveryCreatesOneReport()throws Exception{var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->{start.await();return service.submit(request).id();});var b=pool.submit(()->{start.await();return service.submit(request).id();});start.countDown();assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(b.get(10,TimeUnit.SECONDS));}assertThat(db.queryForObject("SELECT COUNT(*) FROM report_notification WHERE report_id=?",Integer.class,request.commandId())).isEqualTo(2);}
    @Test void malformedTextCoordinatesAndTimeRejected(){for(String text:List.of("abcd","a".repeat(1001)," text ","test\u0000"))assertFailure(400,()->service.submit(sub(UUID.randomUUID(),text)));var bad=new ReportService.Submission(UUID.randomUUID(),mc,"test-world","valid text","minecraft:overworld",Double.NaN,0,0,Instant.now());assertFailure(400,()->service.submit(bad));assertFailure(400,()->service.submit(new ReportService.Submission(UUID.randomUUID(),mc,"test-world","valid text","minecraft:overworld",0,0,0,Instant.now().minusSeconds(601))));}
    @Test void inactiveUnknownIdentityAndWrongDomainDenied(){db.update("UPDATE platform_user SET status='DISABLED' WHERE id=?",user);assertFailure(403,()->service.submit(request));assertFailure(400,()->service.submit(new ReportService.Submission(UUID.randomUUID(),mc,"other-world","valid text","minecraft:overworld",0,0,0,Instant.now())));}
    @Test void takeThenCloseAuditedReplaySafeAndOwnershipProtected(){var r=service.submit(request);var take=new ReportService.Action(UUID.randomUUID(),111,"TAKE");assertThat(service.action(r.id(),take).status()).isEqualTo("IN_PROGRESS");service.action(r.id(),take);assertFailure(409,()->service.action(r.id(),new ReportService.Action(UUID.randomUUID(),222,"CLOSE")));assertThat(service.action(r.id(),new ReportService.Action(UUID.randomUUID(),111,"CLOSE")).status()).isEqualTo("CLOSED");assertFailure(409,()->service.action(r.id(),new ReportService.Action(UUID.randomUUID(),111,"TAKE")));assertThat(db.queryForObject("SELECT COUNT(*) FROM report_admin_action WHERE report_id=?",Integer.class,r.id())).isEqualTo(2);assertThat(service.open(111,0)).noneMatch(x->x.id().equals(r.id()));}
    @Test void concurrentAdminsCannotBothTake()throws Exception{var r=service.submit(request);var start=new CountDownLatch(1);try(var pool=Executors.newFixedThreadPool(2)){var a=pool.submit(()->{start.await();return attempt(()->service.action(r.id(),new ReportService.Action(UUID.randomUUID(),111,"TAKE")));});var b=pool.submit(()->{start.await();return attempt(()->service.action(r.id(),new ReportService.Action(UUID.randomUUID(),222,"TAKE")));});start.countDown();assertThat(List.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);}}
    @Test void rejectAndPlayerCardContainNoCredentials(){var r=service.submit(request);var p=service.player(111,r.id());assertThat(p.platformUserId()).isEqualTo(user);assertThat(p.toString()).doesNotContain("password","credential");assertThat(service.action(r.id(),new ReportService.Action(UUID.randomUUID(),222,"REJECT")).status()).isEqualTo("REJECTED");}
    @Test void ordinaryUserDeniedOnEveryOperation(){var r=service.submit(request);assertFailure(403,()->service.open(333,0));assertFailure(403,()->service.get(333,r.id()));assertFailure(403,()->service.player(333,r.id()));assertFailure(403,()->service.action(r.id(),new ReportService.Action(UUID.randomUUID(),333,"CLOSE")));assertFailure(403,()->service.claim(333));assertFailure(403,()->service.acknowledge(333,r.id(),UUID.randomUUID()));}
    @Test void summaryCountsOpenAndAssignedButNotFinalStates(){
        long before=service.summary(111).openReports();
        var r=service.submit(request);assertThat(service.summary(111).openReports()).isEqualTo(before+1);
        service.action(r.id(),new ReportService.Action(UUID.randomUUID(),111,"TAKE"));assertThat(service.summary(111).openReports()).isEqualTo(before+1);
        service.action(r.id(),new ReportService.Action(UUID.randomUUID(),111,"CLOSE"));assertThat(service.summary(111).openReports()).isEqualTo(before);
        assertFailure(403,()->service.summary(333));
    }
    @Test void summaryHttpRequiresBotSecretAndNumericAdmin()throws Exception{
        String path="/api/internal/reports/summary?telegramUserId=";
        assertThat(http(path+111,"GET","X-Game-Service-Secret",GAME,null).statusCode()).isEqualTo(401);
        assertThat(http(path+333,"GET","X-Bot-Service-Secret",BOT,null).statusCode()).isEqualTo(403);
        var response=http(path+111,"GET","X-Bot-Service-Secret",BOT,null);
        assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).contains("openReports").doesNotContain("username","password");
    }
    @Test void summaryIsNotLimitedToFirstPage(){
        long before=service.summary(111).openReports();
        for(int i=0;i<11;i++){
            db.update("UPDATE player_report SET created_at=? WHERE platform_user_id=?",Timestamp.from(Instant.now().minusSeconds(3601)),user);
            service.submit(sub(UUID.randomUUID(),"Summary report "+i));
        }
        assertThat(service.open(111,0)).hasSize(10);
        assertThat(service.summary(111).openReports()).isEqualTo(before+11);
    }
    @Test void durableLeaseRetryAckAndAdminIsolation(){var r=service.submit(request);var first=service.claim(111).stream().filter(d->d.reportId().equals(r.id())).findFirst().orElseThrow();assertThat(service.claim(111)).noneMatch(d->d.reportId().equals(r.id()));assertFailure(409,()->service.acknowledge(222,r.id(),first.leaseToken()));db.update("UPDATE report_notification SET lease_until=? WHERE report_id=? AND admin_telegram_id=111",Timestamp.from(Instant.now().minusSeconds(1)),r.id());var retry=service.claim(111).stream().filter(d->d.reportId().equals(r.id())).findFirst().orElseThrow();assertThat(retry.leaseToken()).isNotEqualTo(first.leaseToken());assertFailure(409,()->service.acknowledge(111,r.id(),first.leaseToken()));service.acknowledge(111,r.id(),retry.leaseToken());service.acknowledge(111,r.id(),retry.leaseToken());assertThat(service.claim(111)).noneMatch(d->d.reportId().equals(r.id()));assertThat(service.claim(222)).anyMatch(d->d.reportId().equals(r.id()));}
    @Test void schemaRejectsInvalidStatusAndAssignedConstraint(){var r=service.submit(request);assertThatThrownBy(()->db.update("UPDATE player_report SET status='BOGUS' WHERE id=?",r.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThatThrownBy(()->db.update("UPDATE player_report SET status='IN_PROGRESS',assigned_admin=NULL WHERE id=?",r.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
    @Test void httpServiceCredentialsAndAdminChecks()throws Exception{
        String ingest="/api/internal/game/reports";String body="{\"commandId\":\""+request.commandId()+"\",\"minecraftUuid\":\""+mc+"\",\"serverId\":\"test-world\",\"text\":\"HTTP report\",\"dimension\":\"minecraft:overworld\",\"x\":1,\"y\":2,\"z\":3,\"occurredAt\":\""+Instant.now()+"\"}";
        assertThat(http(ingest,"POST","X-Bot-Service-Secret",BOT,body).statusCode()).isEqualTo(401);
        assertThat(http(ingest,"POST","X-Game-Service-Secret",GAME,body).statusCode()).isEqualTo(200);
        assertThat(http("/api/internal/reports?telegramUserId=111","GET","X-Game-Service-Secret",GAME,null).statusCode()).isEqualTo(401);
        assertThat(http("/api/internal/reports?telegramUserId=333","GET","X-Bot-Service-Secret",BOT,null).statusCode()).isEqualTo(403);
        var response=http("/api/internal/reports?telegramUserId=111","GET","X-Bot-Service-Secret",BOT,null);assertThat(response.statusCode()).isEqualTo(200);assertThat(response.body()).contains("HTTP report").doesNotContain("password_hash");
    }
    HttpResponse<String> http(String path,String method,String header,String secret,String body)throws Exception{return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header(header,secret).header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
    int attempt(Runnable r){try{r.run();return 200;}catch(AccountFailure e){return e.status();}}
    void assertFailure(int status,Runnable r){assertThatThrownBy(r::run).isInstanceOfSatisfying(AccountFailure.class,e->assertThat(e.status()).isEqualTo(status));}
}
