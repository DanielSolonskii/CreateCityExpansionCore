package dev.createserver.backend.launcher;
import dev.createserver.backend.account.*;import java.util.*;import java.time.*;import java.sql.Timestamp;
import org.junit.jupiter.api.*;import org.springframework.beans.factory.annotation.Autowired;import org.springframework.boot.test.context.SpringBootTest;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"GAME_SERVER_ID=launcher-test","spring.datasource.url=jdbc:h2:mem:launcher-profile;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"})
@Transactional class LauncherProfileTest {
 @Autowired LauncherProfileController controller;@Autowired JdbcTemplate db;
 UUID owner=UUID.randomUUID(),mc=UUID.randomUUID(),device=UUID.randomUUID();String secret="a".repeat(43);
 @BeforeEach void fixture(){var now=Timestamp.from(Instant.now());db.update("INSERT INTO platform_user(id,status,created_at,updated_at) VALUES(?,'ACTIVE',?,?)",owner,now,now);
 db.update("INSERT INTO account_credentials(platform_user_id,username,password_hash) VALUES(?,'launcherplayer',?)",owner,"t".repeat(60));
 db.update("INSERT INTO minecraft_identity(minecraft_uuid,platform_user_id,current_name) VALUES(?,?,'LauncherPlayer')",mc,owner);
 db.update("INSERT INTO telegram_identity(telegram_user_id,platform_user_id,verified_at) VALUES(12345678,?,?)",owner,now);
 db.update("INSERT INTO trusted_device(id,platform_user_id,label,state,credential_hash,polling_hash,verification_hash,created_at,confirmation_expires_at,trusted_expires_at,approved_at) VALUES(?,?,?,'TRUSTED',?,?,?,?,?,?,?)",device,owner,"Test",Secrets.hash("trusted-device-v1:credential:"+secret),"b".repeat(64),"c".repeat(64),now,Timestamp.from(Instant.now().plusSeconds(60)),Timestamp.from(Instant.now().plusSeconds(900)),now);}
 @Test void ownProfileUsesMinecraftIdentityAndExplicitUnavailableState(){var response=controller.profile(device,"Bearer "+secret);assertThat(response.getHeaders().getCacheControl()).contains("no-store");var p=response.getBody();assertThat(p.minecraftUuid()).isEqualTo(mc);assertThat(p.minecraftUuid()).isNotEqualTo(owner);assertThat(p.username()).isEqualTo("LauncherPlayer");assertThat(p.cityState()).isEqualTo("UNKNOWN");assertThat(p.playTicks()).isNull();}
 @Test void invalidExpiredRevokedAndForeignDeviceCannotRead(){assertThatThrownBy(()->controller.profile(device,"Bearer "+"z".repeat(43))).isInstanceOf(AccountFailure.class);assertThatThrownBy(()->controller.profile(UUID.randomUUID(),"Bearer "+secret)).isInstanceOf(AccountFailure.class);
 db.update("UPDATE trusted_device SET state='REVOKED',revoked_at=? WHERE id=?",Timestamp.from(Instant.now()),device);assertThatThrownBy(()->controller.profile(device,"Bearer "+secret)).isInstanceOf(AccountFailure.class);}
 @Test void expiredAndDisabledDevicesCannotRead(){db.update("UPDATE trusted_device SET created_at=?,confirmation_expires_at=?,approved_at=?,trusted_expires_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(3600)),Timestamp.from(Instant.now().minusSeconds(3500)),Timestamp.from(Instant.now().minusSeconds(3400)),Timestamp.from(Instant.now().minusSeconds(1)),device);assertThatThrownBy(()->controller.profile(device,"Bearer "+secret)).isInstanceOf(AccountFailure.class);}
 @Test void storedStatsAndSkinAreOwnerScoped(){db.update("INSERT INTO companion_profile(minecraft_uuid,about,observed_at,play_ticks,skin_url,stats_observed_at) VALUES(?,?,?,24000,?,?)",mc,"",Timestamp.from(Instant.now()),"https://textures.minecraft.net/texture/"+"a".repeat(64),Timestamp.from(Instant.now()));var p=controller.profile(device,"Bearer "+secret).getBody();assertThat(p.playTicks()).isEqualTo(24000);assertThat(p.skinUrl()).startsWith("https://textures.minecraft.net/texture/");}
 @Test void httpAuthFailureIs401RatherThan500()throws Exception{
 var mvc=org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
 mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/devices/"+device+"/profile").header("Authorization","Bearer "+"z".repeat(43)))
 .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized())
 .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("INVALID_DEVICE"));
 }
 @Test void independentCompanionBootIsFreshAfterCurrentServerStart(){
 var now=Instant.now();var t=Timestamp.from(now);
 db.update("INSERT INTO server_bridge_boot(boot_id,server_id,started_at,revision,observed_at,received_at,online,uptime_seconds,players,max_players,minecraft_version,ccec_version,server_version,dropped_events) VALUES(?,'launcher-test',?,1,?,?,TRUE,60,'',15,'1.21.1','1.0.1','test',0)",UUID.randomUUID(),Timestamp.from(now.minusSeconds(60)),t,t);
 var snapshot=new dev.createserver.backend.miniapp.CompanionService.Snapshot("launcher-test",UUID.randomUUID(),now,List.of(),List.of());
 db.update("INSERT INTO companion_snapshot(server_id,boot_id,observed_at,payload) VALUES('launcher-test',?,?,?)",snapshot.bootId(),t,tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(snapshot));
 assertThat(controller.profile(device,"Bearer "+secret).getBody().cityState()).isEqualTo("CURRENT");
 }
}
