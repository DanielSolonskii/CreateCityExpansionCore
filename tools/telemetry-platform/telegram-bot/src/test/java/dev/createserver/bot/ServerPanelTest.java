package dev.createserver.bot;
import com.sun.net.httpserver.HttpServer;
import java.net.*;import java.net.http.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.io.IOException;
import org.junit.jupiter.api.*;import static org.assertj.core.api.Assertions.*;
class ServerPanelTest {
    HttpServer server;BackendClient backend;String response;int code=200;List<String> calls=new ArrayList<>();
    @BeforeEach void setup()throws Exception{
        response="{\"state\":\"ONLINE\",\"playerCount\":2,\"maxPlayers\":15,\"uptimeSeconds\":120,\"minecraftVersion\":\"1.21.1\",\"ccecVersion\":\"1.0.1\",\"serverVersion\":\"NeoForge 21.1.251\"}";
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/",exchange->{calls.add(exchange.getRequestURI().toString());byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(code,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();});server.start();
        backend=new BackendClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"fake-service-secret",HttpClient.newHttpClient());
    }
    @AfterEach void stop(){server.stop(0);}
    @Test void ordinaryServerPageContainsRealCountsNotPlayerNames()throws Exception{var page=ServerPanel.page(backend,null);assertThat(page.text()).contains("2 / 15","1.21.1","NeoForge 21.1.251").doesNotContain("Текущие игроки");assertThat(calls).containsExactly("/api/internal/bridge/status");}
    @Test void unknownDoesNotDisplayStaleCounts()throws Exception{response="{\"state\":\"UNKNOWN\",\"playerCount\":9,\"maxPlayers\":15}";assertThat(ServerPanel.page(backend,null).text()).contains("Нет свежих данных").doesNotContain("9 / 15");}
    @Test void adminStatsAndPlayersUseProtectedRoute()throws Exception{response="{\"status\":{\"state\":\"ONLINE\",\"playerCount\":1,\"maxPlayers\":15},\"players\":[{\"username\":\"engineer\"}],\"events24h\":6,\"sessions\":2,\"playtimeSeconds\":180}";var page=ServerPanel.page(backend,111L);assertThat(page.text()).contains("engineer","24 ч: 6","Сессии: 2");assertThat(calls.getFirst()).contains("telegramUserId=111");}
    @Test void forgedAdminStatsCallbackNeverCallsBackend()throws Exception{var panel=new ReportAdminPanel(Set.of(111L),backend,null);assertThatThrownBy(()->panel.page(222,"admin:stats","callback")).isInstanceOf(IOException.class);assertThat(calls).isEmpty();}
    @Test void backendFailureIsNotReportedAsOffline(){code=503;assertThatThrownBy(()->ServerPanel.page(backend,null)).isInstanceOf(IOException.class);}
    @Test void separatePlayerScreenAndAdminDenial()throws Exception{response="{\"status\":{\"state\":\"ONLINE\"},\"players\":[{\"username\":\"engineer\"}]}";assertThat(ServerPanel.players(backend,111).text()).contains("👥 Игроки","engineer");calls.clear();assertThatThrownBy(()->new ReportAdminPanel(Set.of(111L),backend,null).page(222,"admin:players","callback")).isInstanceOf(IOException.class);assertThat(calls).isEmpty();}
}
