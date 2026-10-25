package dev.createserver.backend.launcher;
import dev.createserver.backend.account.*;
import dev.createserver.backend.bridge.TelemetryV2Service;
import java.util.*;import java.time.*;import java.sql.Timestamp;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(properties={"REPORT_ADMIN_TELEGRAM_IDS=1","spring.datasource.url=jdbc:h2:mem:telemetry-v2;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"})
@Transactional class TelemetryV2Test {
 @Autowired TelemetryV2Service service;@Autowired JdbcTemplate db;
 UUID owner=UUID.randomUUID(),device=UUID.randomUUID(),attempt=UUID.randomUUID();String secret="a".repeat(43);
 @BeforeEach void fixture(){var now=Timestamp.from(Instant.now());db.update("INSERT INTO platform_user(id,status,created_at,updated_at) VALUES(?,'ACTIVE',?,?)",owner,now,now);
 db.update("INSERT INTO account_credentials(platform_user_id,username,password_hash) VALUES(?,'telemetryplayer',?)",owner,"t".repeat(60));
 db.update("INSERT INTO telegram_identity(telegram_user_id,platform_user_id,verified_at) VALUES(12345678,?,?)",owner,now);
 db.update("INSERT INTO trusted_device(id,platform_user_id,label,state,credential_hash,polling_hash,verification_hash,created_at,confirmation_expires_at,trusted_expires_at,approved_at) VALUES(?,?,?,'TRUSTED',?,?,?,?,?,?,?)",device,owner,"Test",Secrets.hash("trusted-device-v1:credential:"+secret),"b".repeat(64),"c".repeat(64),now,Timestamp.from(Instant.now().plusSeconds(60)),Timestamp.from(Instant.now().plusSeconds(900)),now);}
 @Test void ownerAuthenticationDedupeAndTypedFields(){var event=new TelemetryV2Service.Event(UUID.randomUUID(),attempt,"LAUNCH_CLICK","OK",0,0,System.currentTimeMillis(),"0.1.7");var batch=new TelemetryV2Service.Batch(List.of(event));
 assertThatThrownBy(()->service.receive(device,"z".repeat(43),batch)).isInstanceOf(AccountFailure.class);
 service.receive(device,secret,batch);service.receive(device,secret,batch);
 assertThat(db.queryForObject("SELECT COUNT(*) FROM launcher_telemetry_event",Integer.class)).isEqualTo(1);
 assertThat(db.queryForObject("SELECT platform_user_id FROM launcher_telemetry_event",UUID.class)).isEqualTo(owner);
 assertThatThrownBy(()->service.receive(device,secret,new TelemetryV2Service.Batch(List.of(new TelemetryV2Service.Event(UUID.randomUUID(),attempt,"SURVEY_REASON","free private text",0,0,System.currentTimeMillis(),"0.1.7"))))).isInstanceOf(AccountFailure.class);
 }
 @Test void adminPanelsMigrateAndRemainRestricted(){assertThatThrownBy(()->service.analytics(2,"online",null)).isInstanceOf(AccountFailure.class);
 for(String section:List.of("online","funnel","problems","retention"))assertThat(service.analytics(1,section,null)).containsKeys("title","lines");
 assertThat(db.queryForObject("SELECT schema_version FROM platform_infrastructure WHERE id=1",Integer.class)).isEqualTo(14);
 }
}
