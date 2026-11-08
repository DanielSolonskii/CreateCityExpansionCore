package dev.createserver.backend.miniapp;
import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
abstract class MiniAppContract {
    @Autowired JdbcTemplate db;@LocalServerPort int port;
    final UUID owner=UUID.randomUUID(),other=UUID.randomUUID(),boot=UUID.randomUUID(),mc=UUID.randomUUID();
    final long telegram=(UUID.randomUUID().getMostSignificantBits()&0x7fffffff)+10000000000L;
    final String username="mini"+owner.toString().replace("-","").substring(0,10);
    @BeforeEach void fixture(){
        Timestamp now=Timestamp.from(Instant.now());
        for(UUID id:List.of(owner,other)){
            db.update("INSERT INTO platform_user(id,status,created_at,updated_at) VALUES(?,'ACTIVE',?,?)",id,now,now);
            db.update("INSERT INTO account_credentials(platform_user_id,username,password_hash) VALUES(?,?,?)",id,id.equals(owner)?username:"peer"+id.toString().substring(0,8),"private-password-hash".repeat(4));
        }
        db.update("INSERT INTO telegram_identity(telegram_user_id,platform_user_id,verified_at) VALUES(?,?,?)",telegram,owner,now);
        db.update("INSERT INTO minecraft_identity(minecraft_uuid,platform_user_id,current_name) VALUES(?,?,'csstablealias')",mc,owner);
        db.update("INSERT INTO server_bridge_boot(boot_id,server_id,started_at,revision,observed_at,received_at,online,uptime_seconds,players,max_players,minecraft_version,ccec_version,server_version,dropped_events) VALUES(?,'mini-test',?,1,?,?,TRUE,60,?,15,'1.21.1','1.0.1','test',0)",boot,now,now,now,mc.toString());
        db.update("INSERT INTO telemetry_session(id,boot_id,platform_user_id,minecraft_uuid,started_at,duration_seconds) VALUES(?,?,?,?,?,120)",UUID.randomUUID(),boot,owner,mc,now);
        db.update("INSERT INTO telemetry_session(id,boot_id,platform_user_id,minecraft_uuid,started_at,duration_seconds) VALUES(?,?,?,?,?,9999)",UUID.randomUUID(),boot,other,UUID.randomUUID(),now);
    }
    @AfterEach void cleanup(){
        db.update("DELETE FROM telemetry_session WHERE boot_id=?",boot);db.update("DELETE FROM server_bridge_boot WHERE boot_id=?",boot);
        db.update("DELETE FROM minecraft_identity WHERE platform_user_id=?",owner);db.update("DELETE FROM telegram_identity WHERE platform_user_id=?",owner);
        for(UUID id:List.of(owner,other)){db.update("DELETE FROM account_credentials WHERE platform_user_id=?",id);db.update("DELETE FROM platform_user WHERE id=?",id);}
    }
    HttpResponse<String> request(String path,String proof)throws Exception{
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET();if(proof!=null)builder.header("Authorization","tma "+proof);
        return HttpClient.newHttpClient().send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    String proof(long id){return TelegramProofTest.proof(id,Instant.now().getEpochSecond());}
    @Test void linkedProfileIsIsolatedAndSnapshotUsesDisplayNames()throws Exception{
        var response=request("/api/mini-app/dashboard?telegramUserId="+(telegram+1),proof(telegram));
        assertThat(response.statusCode()).isEqualTo(200);assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        var data=JsonMapper.builder().build().readTree(response.body());
        assertThat(data.path("profile").path("username").asText()).isEqualTo(username);
        assertThat(data.path("profile").path("playtimeSeconds").asLong()).isEqualTo(120);
        assertThat(data.path("server").path("state").asText()).isEqualTo("ONLINE");assertThat(data.path("server").path("players").toString()).contains(username).doesNotContain("csstablealias");
        assertThat(response.body()).doesNotContain("password","credential","polling","botBackendSecret",owner.toString(),mc.toString());
    }
    @Test void unlinkedAndStaleSnapshotNeverInventOnline()throws Exception{
        db.update("UPDATE server_bridge_boot SET observed_at=? WHERE boot_id=?",Timestamp.from(Instant.now().minusSeconds(150)),boot);
        var data=JsonMapper.builder().build().readTree(request("/api/mini-app/dashboard",proof(telegram+1)).body());
        assertThat(data.path("profile").isNull()).isTrue();assertThat(data.path("server").path("state").asText()).isEqualTo("UNKNOWN");assertThat(data.path("server").path("players").size()).isZero();
    }
    @Test void proofCannotAccessInternalOrGameApisAndExpires()throws Exception{
        assertThat(request("/api/mini-app/dashboard",null).statusCode()).isEqualTo(401);
        assertThat(request("/api/mini-app/dashboard",TelegramProofTest.proof(telegram,Instant.now().minusSeconds(601).getEpochSecond())).statusCode()).isEqualTo(401);
        assertThat(request("/api/internal/bridge/admin",proof(telegram)).statusCode()).isIn(401,503);
        assertThat(request("/api/mini-app/admin",proof(telegram)).statusCode()).isEqualTo(404);
    }
    @Test void perIdentityRateLimitIsEnforced()throws Exception{
        String proof=proof(telegram+2);HttpResponse<String> response=null;
        for(int i=0;i<61;i++)response=request("/api/mini-app/dashboard",proof);
        assertThat(response.statusCode()).isEqualTo(429);assertThat(response.headers().firstValue("Retry-After")).contains("60");
    }
}
