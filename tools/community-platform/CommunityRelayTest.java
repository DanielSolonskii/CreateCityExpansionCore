package dev.createserver.backend.community;
import java.sql.Timestamp;
import java.time.Instant;
import java.net.*;import java.net.http.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"GAME_SERVER_ID=community-test","CCEC_BACKEND_SECRET=community-game-test-only-secret-long-32","BOT_BACKEND_SECRET=community-bot-test-only-secret-long-32","spring.datasource.url=jdbc:h2:mem:community;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.username=sa","spring.datasource.password=","spring.datasource.driver-class-name=org.h2.Driver"})
@Transactional
class CommunityRelayTest {
    @Autowired CommunityRelayController service;@Autowired JdbcTemplate db;@LocalServerPort int port;
    UUID owner=UUID.randomUUID(),minecraft=UUID.randomUUID(),peer=UUID.randomUUID();long telegram=8987654321L;
    @BeforeEach void fixture(){var now=Timestamp.from(Instant.now());db.update("INSERT INTO platform_user(id,status,created_at,updated_at) VALUES(?,'ACTIVE',?,?)",owner,now,now);db.update("INSERT INTO minecraft_identity(minecraft_uuid,platform_user_id,current_name) VALUES(?,?,'Friend')",minecraft,owner);db.update("INSERT INTO telegram_identity(telegram_user_id,platform_user_id,verified_at) VALUES(?,?,?)",telegram,owner,now);}
    CommunityRelayController.Notice notice(String kind){long now=System.currentTimeMillis();return new CommunityRelayController.Notice(UUID.randomUUID(),minecraft,peer,"Friend",kind,"",kind.equals("INVITE")?UUID.randomUUID():null,kind.equals("INVITE")?UUID.randomUUID():null,now,now);}
    void enqueue(CommunityRelayController.Notice n){service.exchange(new CommunityRelayController.Exchange("community-test",List.of(n),List.of(),List.of(new CommunityRelayController.Policy(minecraft,true,false,true,false))));}
    @Test void privateOwnerAndIdempotentDelivery(){var n=notice("MESSAGE");enqueue(n);enqueue(n);assertThat(db.queryForObject("SELECT COUNT(*) FROM community_notice WHERE id=?",Integer.class,n.id())).isOne();
        var deliveries=service.claim();assertThat(deliveries).hasSize(1);assertThat(service.claim()).isEmpty();var d=deliveries.getFirst();assertThat(d.telegramUserId()).isEqualTo(telegram);
        assertThatThrownBy(()->service.action(new CommunityRelayController.BotAction(telegram+1,n.id(),"REPLY","secret",UUID.randomUUID()))).isInstanceOf(RuntimeException.class);
        service.ack(new CommunityRelayController.DeliveryAck(d.id(),d.attemptToken(),"SENT"));service.ack(new CommunityRelayController.DeliveryAck(d.id(),d.attemptToken(),"SENT"));
        assertThatThrownBy(()->service.ack(new CommunityRelayController.DeliveryAck(d.id(),UUID.randomUUID(),"SENT"))).isInstanceOf(RuntimeException.class);
    }
    @Test void repliesUseBoundMinecraftIdentityAndPersistNonce(){var n=notice("MESSAGE");enqueue(n);UUID nonce=UUID.randomUUID();var request=new CommunityRelayController.BotAction(telegram,n.id(),"REPLY","Hello",nonce);service.action(request);service.action(request);
        var result=service.exchange(new CommunityRelayController.Exchange("community-test",List.of(),List.of()));assertThat(result.commands()).hasSize(1);var command=result.commands().getFirst();assertThat(command.actor()).isEqualTo(minecraft);assertThat(command.target()).isEqualTo(peer);assertThat(command.id()).isEqualTo(nonce);
        service.exchange(new CommunityRelayController.Exchange("community-test",List.of(),List.of(new CommunityRelayController.Ack(nonce,"ok"))));assertThat(service.exchange(new CommunityRelayController.Exchange("community-test",List.of(),List.of())).commands()).isEmpty();
        assertThat(db.queryForObject("SELECT COUNT(*) FROM community_notice WHERE id=?",Integer.class,nonce)).isOne();
    }
    @Test void revokedAccountAndInvalidKindsCannotAct(){var n=notice("INVITE");enqueue(n);db.update("UPDATE platform_user SET status='DISABLED' WHERE id=?",owner);assertThat(service.claim()).isEmpty();assertThatThrownBy(()->service.action(new CommunityRelayController.BotAction(telegram,n.id(),"ACCEPT","",UUID.randomUUID()))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->service.exchange(new CommunityRelayController.Exchange("other-server",List.of(n),List.of()))).isInstanceOf(RuntimeException.class);
    }
    @Test void invitationCarriesServerTokenAndCommandsAreNotBlindlyReplayed(){var n=notice("INVITE");enqueue(n);UUID id=UUID.randomUUID();service.action(new CommunityRelayController.BotAction(telegram,n.id(),"ACCEPT","",id));var c=service.exchange(new CommunityRelayController.Exchange("community-test",List.of(),List.of())).commands().getFirst();assertThat(c.target()).isEqualTo(n.city());assertThat(c.token()).isEqualTo(n.token());
        db.update("UPDATE community_command SET created_at=? WHERE id=?",Timestamp.from(Instant.now().minusSeconds(301)),id);assertThat(service.exchange(new CommunityRelayController.Exchange("community-test",List.of(),List.of())).commands()).isEmpty();
    }
    @Test void publicCallsCannotReachGameOrBotRelay()throws Exception{var http=HttpClient.newHttpClient();for(String path:List.of("/api/internal/community/claim","/api/internal/game/community")){
        var response=http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).isIn(401,503);
    }}
}
