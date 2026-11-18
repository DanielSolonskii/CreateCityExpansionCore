package dev.createserver.bot;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class NoticeDeliveryTest {
    private final JsonMapper json = JsonMapper.builder().build();

    @ParameterizedTest(name = "{0} resumes after a lost acknowledgement")
    @ValueSource(strings = {"community", "companion"})
    void resumesRemainingNoticesAfterLostAcknowledgement(String queue) throws Exception {
        boolean companion = queue.equals("companion");
        String endpoint = companion ? "/api/internal/companion/notices" : "/api/internal/community";
        List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        List<UUID> tokens = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        try (var server = new MockWebServer()) {
            server.start(InetAddress.getByName("127.0.0.1"), 0);
            URI base = URI.create("http://127.0.0.1:" + server.getPort() + "/");
            var backend = new BackendClient(base, "test-secret", HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build());
            var telegram = new TelegramClient(base, new OkHttpClient());
            for (int i = 0; i < ids.size(); i++) {
                var claim = Map.of(companion ? "actionId" : "id", ids.get(i), "attemptToken", tokens.get(i),
                        "telegramUserId", 200 + i, "text", "Notice " + i, "kind", "MESSAGE", "peer", "Friend");
                server.enqueue(new MockResponse().setBody(json.writeValueAsString(List.of(claim))));
                server.enqueue(new MockResponse().setBody("{\"ok\":true,\"result\":{\"message_id\":7}}"));
                server.enqueue(i == 0 ? new MockResponse().setResponseCode(503) : new MockResponse().setBody("{\"accepted\":true}"));
            }
            server.enqueue(new MockResponse().setBody("[]"));

            for (int pass = 0; pass < 2; pass++) {
                if (companion) CompanionNotifications.deliver(Set.of(111L), backend, telegram);
                else CommunityBot.deliver(backend, telegram);
                // The first call must stop at the failed ACK. The next call drains both remaining notices.
                assertThat(server.getRequestCount()).isEqualTo(pass == 0 ? 3 : 10);
            }
            for (int i = 0; i < ids.size(); i++) {
                assertThat(server.takeRequest().getPath()).isEqualTo(endpoint + "/claim");
                var send = server.takeRequest();
                assertThat(send.getPath()).isEqualTo("/sendMessage");
                var message = json.readTree(send.getBody().readUtf8());
                assertThat(message.path("chat_id").asLong()).isEqualTo(200 + i);
                assertThat(message.path("text").asText()).contains("Notice " + i);
                var ack = server.takeRequest();
                assertThat(ack.getPath()).isEqualTo(endpoint + (companion ? "/" + ids.get(i) : "") + "/ack");
                var receipt = json.readTree(ack.getBody().readUtf8());
                assertThat(receipt.path("attemptToken").asText()).isEqualTo(tokens.get(i).toString());
                assertThat(receipt.path("outcome").asText()).isEqualTo("SENT");
                if (!companion) assertThat(receipt.path("id").asText()).isEqualTo(ids.get(i).toString());
            }
            assertThat(server.takeRequest().getPath()).isEqualTo(endpoint + "/claim");
        }
    }
}
