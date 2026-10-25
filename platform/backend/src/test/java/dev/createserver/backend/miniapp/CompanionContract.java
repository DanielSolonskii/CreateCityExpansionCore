package dev.createserver.backend.miniapp;
import dev.createserver.backend.account.*;
import dev.createserver.backend.report.ReportService;
import dev.createserver.backend.broadcast.BroadcastService;
import java.net.*;import java.net.http.*;import java.sql.Timestamp;import java.time.*;import java.util.*;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

abstract class CompanionContract extends MiniAppContract {
    @Autowired CompanionService companion;@Autowired DeviceService devices;@Autowired ReportService reports;
    @Autowired CompanionNoticeController notices;@Autowired BroadcastService broadcasts;
    final JsonMapper json=JsonMapper.builder().build();
    @AfterEach void removeCompanion(){
        db.update("DELETE FROM companion_notice WHERE action_id IN (SELECT a.id FROM report_admin_action a JOIN player_report r ON r.id=a.report_id WHERE r.platform_user_id IN (?,?))",owner,other);
        db.update("DELETE FROM report_notification WHERE report_id IN (SELECT id FROM player_report WHERE platform_user_id IN (?,?))",owner,other);
        db.update("DELETE FROM report_admin_action WHERE report_id IN (SELECT id FROM player_report WHERE platform_user_id IN (?,?))",owner,other);
        db.update("DELETE FROM player_report WHERE platform_user_id IN (?,?)",owner,other);
        db.update("DELETE FROM companion_preferences WHERE platform_user_id IN (?,?)",owner,other);
        db.update("DELETE FROM trusted_device WHERE platform_user_id IN (?,?)",owner,other);
        db.update("DELETE FROM companion_snapshot WHERE server_id='mini-test'");db.update("DELETE FROM companion_profile WHERE minecraft_uuid=?",mc);
        db.update("DELETE FROM telegram_identity WHERE platform_user_id=?",other);
    }
    CompanionService.Snapshot snapshot(Instant time,List<CompanionService.City> cities){return new CompanionService.Snapshot("mini-test",boot,time,cities,List.of(new CompanionService.About(mc,"Инженер железных дорог")));}
    @Test void citySnapshotProfileAndReportsAreOwnerScoped()throws Exception{
        companion.receive(snapshot(Instant.now(),List.of(new CompanionService.City(UUID.randomUUID(),"Мастерская",2,3,7500,List.of(new CompanionService.Member(mc,"OWNER"))))));
        reports.submit(new ReportService.Submission(UUID.randomUUID(),mc,"mini-test","Мой репорт","minecraft:overworld",1,2,3,Instant.now()));
        var response=request("/api/mini-app/account?platformUserId="+other,proof(telegram));assertThat(response.statusCode()).isEqualTo(200);
        var body=json.readTree(response.body());assertThat(body.path("cities").get(0).path("role").asText()).isEqualTo("OWNER");
        assertThat(body.path("cities").get(0).path("members").get(0).asText()).isEqualTo(username);
        assertThat(body.path("about").asText()).contains("Инженер");assertThat(body.path("reports").size()).isOne();
        assertThat(response.body()).doesNotContain("password","credential",other.toString(),mc.toString());
        assertThat(request("/api/mini-app/account",proof(telegram+1)).statusCode()).isEqualTo(403);
    }
    @Test void oldSnapshotCannotRestoreDeletedCityAndStaleIsExplicit(){
        Instant now=Instant.now();var city=new CompanionService.City(UUID.randomUUID(),"Город",1,1,2500,List.of(new CompanionService.Member(mc,"OWNER")));
        companion.receive(snapshot(now.minusSeconds(2),List.of(city)));companion.receive(snapshot(now,List.of()));companion.receive(snapshot(now.minusSeconds(1),List.of(city)));
        assertThat(companion.account(telegram).cities()).isEmpty();assertThat(companion.account(telegram).cityState()).isEqualTo("CURRENT");
        db.update("UPDATE server_bridge_boot SET observed_at=? WHERE boot_id=?",Timestamp.from(now.minusSeconds(150)),boot);
        assertThat(companion.account(telegram).cityState()).isEqualTo("STALE");assertThat(companion.account(telegram).online()).isFalse();
        assertThatThrownBy(()->companion.receive(snapshot(now.minusSeconds(121),List.of()))).isInstanceOf(AccountFailure.class);
    }
    @Test void revokeRequiresOwnerAndPreferencesPersist()throws Exception{
        UUID device=UUID.randomUUID();Timestamp now=Timestamp.from(Instant.now());
        db.update("INSERT INTO trusted_device(id,platform_user_id,label,state,credential_hash,polling_hash,verification_hash,created_at,confirmation_expires_at,trusted_expires_at,approved_at) VALUES(?,?,?,'TRUSTED',?,?,?,?,?,?,?)",device,other,"Другой ПК","a".repeat(64),"b".repeat(64),"c".repeat(64),now,Timestamp.from(Instant.now().plusSeconds(60)),Timestamp.from(Instant.now().plusSeconds(900)),now);
        assertThatThrownBy(()->companion.revoke(telegram,device)).isInstanceOf(AccountFailure.class);
        db.update("UPDATE trusted_device SET platform_user_id=? WHERE id=?",owner,device);
        companion.revoke(telegram,device);companion.revoke(telegram,device);
        assertThat(db.queryForObject("SELECT state FROM trusted_device WHERE id=?",String.class,device)).isEqualTo("REVOKED");
        var preferences=new CompanionService.Preferences(false,true,false);companion.preferences(telegram,preferences);assertThat(companion.account(telegram).preferences()).isEqualTo(preferences);
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/mini-app/preferences")).header("Authorization","tma "+proof(telegram)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"news\":true,\"maintenance\":false,\"reports\":true}")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);assertThat(companion.account(telegram).preferences().maintenance()).isFalse();
        db.update("UPDATE platform_user SET status='DISABLED' WHERE id=?",owner);assertThat(request("/api/mini-app/account",proof(telegram)).statusCode()).isEqualTo(403);
    }
    @Test void notificationsRespectPreferencesAndClaimsAreOnce(){
        var r=reports.submit(new ReportService.Submission(UUID.randomUUID(),mc,"mini-test","Мой репорт","minecraft:overworld",1,2,3,Instant.now()));
        reports.action(r.id(),new ReportService.Action(UUID.randomUUID(),111,"TAKE"));
        var claimed=notices.claim(new CompanionNoticeController.Actor(111));assertThat(claimed).hasSize(1);assertThat(notices.claim(new CompanionNoticeController.Actor(111))).isEmpty();
        var n=claimed.getFirst();notices.ack(n.actionId(),new CompanionNoticeController.Ack(111,n.attemptToken(),"SENT"));
        companion.preferences(telegram,new CompanionService.Preferences(false,true,false));reports.action(r.id(),new ReportService.Action(UUID.randomUUID(),111,"CLOSE"));assertThat(notices.claim(new CompanionNoticeController.Actor(111))).isEmpty();
        for(String text:List.of("Новости","#maintenance\nПерезапуск")){
            UUID post=UUID.randomUUID();try{broadcasts.create(new BroadcastService.Draft(post,111,text,null));broadcasts.publish(111,post);
                assertThat(db.queryForObject("SELECT COUNT(*) FROM broadcast_delivery WHERE post_id=? AND telegram_user_id=?",Integer.class,post,telegram)).isEqualTo(text.startsWith("#maintenance")?1:0);
            }finally{db.update("DELETE FROM broadcast_delivery WHERE post_id=?",post);db.update("DELETE FROM broadcast_post WHERE id=?",post);}
        }
    }
    @Test void internalSnapshotNeverAcceptsMiniAppProof()throws Exception{
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/internal/game/companion")).header("Authorization","tma "+proof(telegram)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(401);
    }
    @Test void concurrentPreferencesAreSerializedWithoutDuplicateRows()throws Exception{
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var start=new java.util.concurrent.CountDownLatch(1);
            var a=pool.submit(()->{start.await();return companion.preferences(telegram,new CompanionService.Preferences(true,false,true));});
            var b=pool.submit(()->{start.await();return companion.preferences(telegram,new CompanionService.Preferences(false,true,false));});start.countDown();
            a.get(10,java.util.concurrent.TimeUnit.SECONDS);b.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(db.queryForObject("SELECT COUNT(*) FROM companion_preferences WHERE platform_user_id=?",Integer.class,owner)).isOne();
        }
    }
}
