package dev.createserver.backend.game;

import dev.createserver.backend.account.*;
import java.net.URI;
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

abstract class GameSessionContract {
    static final String GAME_SECRET="test-only-game-service-independent-secret-32";
    @Autowired GameSessionService game;
    @Autowired JdbcTemplate jdbc;
    @Autowired DeviceService devices;
    @Autowired org.springframework.security.crypto.password.PasswordEncoder encoder;
    @LocalServerPort int port;
    UUID user,device,boot;
    String credential,username;
    GameSessionService.Capability capability;
    @BeforeEach void setup(){
        user=UUID.randomUUID();device=UUID.randomUUID();boot=UUID.randomUUID();credential=Secrets.token();username="u"+user.toString().replace("-","").substring(0,16);
        Instant now=Instant.now();
        jdbc.update("INSERT INTO platform_user(id,status,created_at,updated_at) VALUES(?,'ACTIVE',?,?)",user,ts(now),ts(now));
        jdbc.update("INSERT INTO account_credentials(platform_user_id,username,password_hash) VALUES(?,?,?)",user,username,encoder.encode("test-only-password-1234"));
        jdbc.update("INSERT INTO telegram_identity(telegram_user_id,platform_user_id,verified_at) VALUES(?,?,?)",Math.abs(user.getMostSignificantBits()>>>1),user,ts(now));
        jdbc.update("INSERT INTO trusted_device(id,platform_user_id,label,state,credential_hash,polling_hash,verification_hash,created_at,confirmation_expires_at,trusted_expires_at,approved_at) VALUES(?,?,'fixture','TRUSTED',?,?,?,?,?,?,?)",
                device,user,Secrets.hash("trusted-device-v1:credential:"+credential),Secrets.hash(Secrets.token()),Secrets.hash(Secrets.token()),ts(now),ts(now.plusSeconds(900)),ts(now.plusSeconds(3600)),ts(now));
        game.registerBoot("test-world",boot);capability=game.start(device,credential);
    }
    @AfterEach void cleanup(){
        jdbc.update("DELETE FROM game_session WHERE platform_user_id=?",user);
        jdbc.update("DELETE FROM launcher_session WHERE platform_user_id=?",user);
        jdbc.update("DELETE FROM minecraft_identity WHERE platform_user_id=?",user);
        jdbc.update("DELETE FROM trusted_device WHERE platform_user_id=?",user);
        jdbc.update("DELETE FROM telegram_identity WHERE platform_user_id=?",user);
        jdbc.update("DELETE FROM account_credentials WHERE platform_user_id=?",user);
        jdbc.update("DELETE FROM platform_user WHERE id=?",user);
    }
    @Test void provisionsStableAliasNotAccountUsernameAndHashesOnly(){
        assertThat(capability.username()).isEqualTo(username);
        var p=capability.profile();assertThat(p.joinName()).matches("cs[a-z0-9]{14}").isNotEqualTo(username);
        assertThat(p.minecraftUuid()).isEqualTo(GameSessionService.offlineUuid(p.joinName()));
        assertThat(game.start(device,credential).profile()).isEqualTo(p);
        assertThat(jdbc.queryForObject("SELECT secret_hash FROM launcher_session WHERE platform_user_id=? ORDER BY created_at LIMIT 1",String.class,user)).hasSize(64).doesNotContain(capability.bearer());
        assertThat(capability.toString()).doesNotContain(capability.bearer());
    }
    @Test void legacyIdentityIsNotSilentlyRewritten(){
        UUID original=capability.profile().minecraftUuid();
        jdbc.update("UPDATE minecraft_identity SET join_name=NULL WHERE platform_user_id=?",user);
        assertThatThrownBy(()->game.start(device,credential)).isInstanceOfSatisfying(AccountFailure.class,e->assertThat(e.status()).isEqualTo(409));
        assertThat(jdbc.queryForObject("SELECT minecraft_uuid FROM minecraft_identity WHERE platform_user_id=?",UUID.class,user)).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT join_name FROM minecraft_identity WHERE platform_user_id=?",String.class,user)).isNull();
    }
    @Test void databaseRejectsDuplicateAliasInvalidAliasAndInvalidExpiry(){
        UUID other=UUID.randomUUID();Instant now=Instant.now();
        jdbc.update("INSERT INTO platform_user(id,status,created_at,updated_at) VALUES(?,'ACTIVE',?,?)",other,ts(now),ts(now));
        try {
            assertThatThrownBy(()->jdbc.update("INSERT INTO minecraft_identity(minecraft_uuid,platform_user_id,current_name,join_name) VALUES(?,?,?,?)",UUID.randomUUID(),other,"Fixture",capability.profile().joinName())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(()->jdbc.update("INSERT INTO minecraft_identity(minecraft_uuid,platform_user_id,current_name,join_name) VALUES(?,?,?,?)",UUID.randomUUID(),other,"Fixture","username")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(()->jdbc.update("UPDATE launcher_session SET expires_at=created_at WHERE platform_user_id=?",user)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        } finally {jdbc.update("DELETE FROM minecraft_identity WHERE platform_user_id=?",other);jdbc.update("DELETE FROM platform_user WHERE id=?",other);}
    }
    @Test void concurrentProvisioningKeepsOneStableMinecraftIdentity()throws Exception{
        jdbc.update("DELETE FROM launcher_session WHERE platform_user_id=?",user);jdbc.update("DELETE FROM minecraft_identity WHERE platform_user_id=?",user);
        var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{start.await();return game.start(device,credential).profile();});
            var b=pool.submit(()->{start.await();return game.start(device,credential).profile();});start.countDown();
            assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(b.get(10,TimeUnit.SECONDS));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM minecraft_identity WHERE platform_user_id=?",Integer.class,user)).isEqualTo(1);
        }
    }
    @Test void singleUseDifferentTupleDeniedAndSameAttemptRetryIdempotent(){
        var c=challenge();var t=game.issue(capability.bearer(),c);var proof=proof(t,c);
        assertThat(game.consume(proof)).isEqualTo(game.consume(proof));
        denied(()->game.consume(new GameSessionService.Consume(t.gameSessionId(),t.secret(),c.serverId(),c.bootId(),UUID.randomUUID(),c.nonce(),capability.profile().minecraftUuid(),capability.profile().joinName())));
        assertThat(jdbc.queryForObject("SELECT state FROM game_session WHERE id=?",String.class,t.gameSessionId())).isEqualTo("CONSUMED");
        assertThat(t.toString()).doesNotContain(t.secret());assertThat(proof.toString()).doesNotContain(t.secret());
    }
    @Test void spoofedNameUuidNonceAndSecretCannotConsume(){
        var c=challenge();var t=game.issue(capability.bearer(),c);var p=capability.profile();
        denied(()->game.consume(new GameSessionService.Consume(t.gameSessionId(),t.secret(),c.serverId(),boot,c.connectionId(),c.nonce(),p.minecraftUuid(),username)));
        denied(()->game.consume(new GameSessionService.Consume(t.gameSessionId(),t.secret(),c.serverId(),boot,c.connectionId(),c.nonce(),UUID.randomUUID(),p.joinName())));
        denied(()->game.consume(new GameSessionService.Consume(t.gameSessionId(),t.secret(),c.serverId(),boot,c.connectionId(),Secrets.token(),p.minecraftUuid(),p.joinName())));
        denied(()->game.consume(new GameSessionService.Consume(t.gameSessionId(),Secrets.token(),c.serverId(),boot,c.connectionId(),c.nonce(),p.minecraftUuid(),p.joinName())));
        assertThat(game.consume(proof(t,c)).platformUserId()).isEqualTo(user);
    }
    @Test void bootRestartExpiresPreviousProof(){
        var c=challenge();var t=game.issue(capability.bearer(),c);game.registerBoot("test-world",UUID.randomUUID());
        denied(()->game.consume(proof(t,c)));denied(()->game.issue(capability.bearer(),c));
    }
    @Test void deviceRevocationBlocksOutstandingCapabilityAndTicket(){
        var c=challenge();var t=game.issue(capability.bearer(),c);devices.revokeSelf(device,credential);
        denied(()->game.issue(capability.bearer(),challenge()));denied(()->game.consume(proof(t,c)));
    }
    @Test void disabledOrUnlinkedAccountFailsClosed(){
        var c=challenge();var t=game.issue(capability.bearer(),c);
        jdbc.update("UPDATE platform_user SET status='DISABLED' WHERE id=?",user);denied(()->game.consume(proof(t,c)));
        jdbc.update("UPDATE platform_user SET status='ACTIVE' WHERE id=?",user);jdbc.update("DELETE FROM telegram_identity WHERE platform_user_id=?",user);denied(()->game.issue(capability.bearer(),c));
    }
    @Test void expiredTicketAndLauncherCapabilityDenied(){
        var c=challenge();var t=game.issue(capability.bearer(),c);Instant now=Instant.now();
        jdbc.update("UPDATE game_session SET created_at=?,expires_at=? WHERE id=?",ts(now.minusSeconds(200)),ts(now.minusSeconds(1)),t.gameSessionId());denied(()->game.consume(proof(t,c)));
        jdbc.update("UPDATE launcher_session SET created_at=?,expires_at=? WHERE platform_user_id=?",ts(now.minusSeconds(700)),ts(now.minusSeconds(1)),user);denied(()->game.issue(capability.bearer(),c));
    }
    @Test void revokeAndCredentialIsolation(){
        denied(()->game.issue(credential,challenge()));denied(()->game.issue(capability.bearer().substring(0,37)+Secrets.token(),challenge()));
        game.revoke(capability.bearer());denied(()->game.issue(capability.bearer(),challenge()));
    }
    @Test void concurrentDifferentAttemptOnlyValidTupleWins() throws Exception{
        var c=challenge();var t=game.issue(capability.bearer(),c);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var valid=pool.submit(()->{start.await();return game.consume(proof(t,c));});
            var invalid=pool.submit(()->{start.await();try{game.consume(new GameSessionService.Consume(t.gameSessionId(),t.secret(),c.serverId(),boot,UUID.randomUUID(),c.nonce(),capability.profile().minecraftUuid(),capability.profile().joinName()));return false;}catch(AccountFailure failure){return failure.status()==401;}});
            start.countDown();assertThat(valid.get(10,TimeUnit.SECONDS).platformUserId()).isEqualTo(user);assertThat(invalid.get(10,TimeUnit.SECONDS)).isTrue();
        }
    }
    @Test void concurrentSameAttemptOnlyOneTransitionAndSameDecision()throws Exception{
        var c=challenge();var t=game.issue(capability.bearer(),c);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var a=pool.submit(()->{start.await();return game.consume(proof(t,c));});var b=pool.submit(()->{start.await();return game.consume(proof(t,c));});start.countDown();assertThat(a.get(10,TimeUnit.SECONDS)).isEqualTo(b.get(10,TimeUnit.SECONDS));
        }
    }
    @Test void httpPublicCapabilityIssueAndSeparatedServiceCredentials()throws Exception{
        String body="{\"serverId\":\"test-world\",\"bootId\":\""+boot+"\"}";
        assertThat(post("/api/internal/game/boot","X-Bot-Service-Secret","test-only-bot-service-secret-at-least-32",body).statusCode()).isEqualTo(401);
        assertThat(post("/api/internal/game/boot","X-Game-Service-Secret",GAME_SECRET,body).statusCode()).isEqualTo(200);
        assertThat(post("/api/internal/devices/preview","X-Game-Service-Secret",GAME_SECRET,"{}").statusCode()).isEqualTo(401);
        var response=post("/api/auth/devices/"+device+"/game-session-capability","Authorization","Bearer "+credential,"{}");
        assertThat(response.statusCode()).isEqualTo(200);assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(post("/api/game-sessions","Authorization","Bearer "+credential,"{}").statusCode()).isEqualTo(401);
    }
    private HttpResponse<String> post(String path,String header,String value,String body)throws Exception{return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header(header,value).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
    private GameSessionService.Challenge challenge(){return new GameSessionService.Challenge("test-world",boot,UUID.randomUUID(),Secrets.token());}
    private GameSessionService.Consume proof(GameSessionService.Ticket t,GameSessionService.Challenge c){return new GameSessionService.Consume(t.gameSessionId(),t.secret(),c.serverId(),c.bootId(),c.connectionId(),c.nonce(),capability.profile().minecraftUuid(),capability.profile().joinName());}
    private static void denied(Runnable call){assertThatThrownBy(call::run).isInstanceOfSatisfying(AccountFailure.class,e->assertThat(e.status()).isEqualTo(401));}
    private static Timestamp ts(Instant value){return Timestamp.from(value);}
}
