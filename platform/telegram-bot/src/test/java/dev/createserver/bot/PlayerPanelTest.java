package dev.createserver.bot;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class PlayerPanelTest {
    private final JsonMapper json=JsonMapper.builder().build();
    private HttpServer server;
    private BackendClient backend;
    private Map<String,Object> account,profile;
    private Map<String,Boolean> preferences;
    private final List<String> requests=new ArrayList<>();
    private final List<Map<String,Object>> cities=new ArrayList<>();
    private int status=200;
    @BeforeEach void setup()throws Exception {
        preferences=new LinkedHashMap<>(Map.of("news",true,"maintenance",true,"reports",true));
        account=new LinkedHashMap<>(Map.of("username","engineer","about","<b>Инженер</b> & строитель","cityState","CURRENT",
            "cityObservedAt","2026-11-11T18:00:00Z","cities",cities,"reports",List.of(),"devices",List.of()));
        profile=new LinkedHashMap<>(Map.of("account",account,"onlineState","ONLINE","minecraftLinked",true,
            "playtimeSeconds",3661,"statsObservedAt","2026-11-11T18:00:00Z"));
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",e->{
            assertThat(e.getRequestHeaders().getFirst("X-Bot-Service-Secret")).isEqualTo("test-only-service-secret");
            String path=e.getRequestURI().getPath(),body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
            requests.add(e.getRequestMethod()+" "+path+" "+body);
            if(e.getRequestMethod().equals("POST")){
                var patch=json.readTree(body);preferences.put(patch.path("field").asText(),patch.path("enabled").asBoolean());
            }
            byte[] result=json.writeValueAsBytes(path.endsWith("/preferences")?preferences:profile);
            e.sendResponseHeaders(status,result.length);e.getResponseBody().write(result);e.close();
        });server.start();
        backend=new BackendClient(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"test-only-service-secret",HttpClient.newHttpClient());
    }
    @AfterEach void cleanup(){server.stop(0);}
    private Map<String,Object> city(int i){return Map.of("name","Город "+i,"role","ENGINEER","level",2,"plots",3,"blocks",7500,
        "members",List.of("engineer","builder","mayor","player4","player5","player6"));}
    @Test void profileUsesActualStatisticsEscapesMarkupAndDistinguishesUnknown()throws Exception {
        var page=PlayerPanel.page(backend,123,"account");
        assertThat(page.text()).contains("В игре","1 ч 1 мин","engineer","21:00 UTC+3","Последняя сессия: нет данных");
        assertThat(page.html()).contains("&lt;b&gt;Инженер&lt;/b&gt; &amp;").doesNotContain("<b>Инженер");
        profile.put("onlineState","UNKNOWN");profile.put("playtimeSeconds",null);
        assertThat(PlayerPanel.page(backend,123,"account").text()).contains("Нет свежих данных","статистика ещё не получена").doesNotContain("Не в игре","0 ч");
        profile.put("minecraftLinked",false);
        assertThat(PlayerPanel.page(backend,123,"account").text()).contains("Ещё нет игрового профиля");
        assertThat(requests).allMatch(r->r.startsWith("GET /api/internal/telegram/companion/123 "));
    }
    @Test void citiesArePagedWithRolesAndBoundedMembers()throws Exception {
        for(int i=0;i<11;i++)cities.add(city(i));
        var first=PlayerPanel.page(backend,123,"cities");
        assertThat(first.text()).contains("Город 0","Город 4","Инженер","Участков: 3","Блоков территории: 7500","ещё 1").doesNotContain("Город 5","player6");
        assertThat(first.keyboard().toString()).contains("menu:v1:cities:5");
        var second=PlayerPanel.page(backend,123,"cities:5");
        assertThat(second.text()).contains("Город 5","Город 9").doesNotContain("Город 0","Город 10");
        assertThat(second.keyboard().toString()).contains("cities:0","cities:10");
        assertThat(PlayerPanel.page(backend,123,"cities:999").text()).contains("Город 10");
        assertThat(first.text().length()).isLessThan(3800);
    }
    @Test void staleEmptyAndMissingCitiesAreHonest()throws Exception {
        assertThat(PlayerPanel.page(backend,123,"cities").text()).contains("пока не состоите");
        account.put("cityState","STALE");
        assertThat(PlayerPanel.page(backend,123,"cities").text()).contains("Снимок устарел","В последнем снимке").doesNotContain("пока не состоите");
        cities.add(city(0));assertThat(PlayerPanel.page(backend,123,"cities").text()).contains("Город 0","Снимок устарел");
        account.put("cityState","UNKNOWN");
        assertThat(PlayerPanel.page(backend,123,"cities").text()).contains("ещё не получены").doesNotContain("Город 0");
        profile.put("minecraftLinked",false);
        assertThat(PlayerPanel.page(backend,123,"cities").text()).contains("Войдите на сервер");
    }
    @Test void preferencesSetDesiredValueAndPreserveOtherChannels()throws Exception {
        PlayerPanel.page(backend,123,"pref:news:off");
        var page=PlayerPanel.page(backend,123,"pref:news:off");
        assertThat(preferences).containsEntry("news",false).containsEntry("maintenance",true).containsEntry("reports",true);
        assertThat(page.text()).contains("общие с Mini App","очередь");
        assertThat(page.keyboard().toString()).contains("pref:news:on","pref:maintenance:off","pref:reports:off");
        assertThat(requests).hasSize(2).allMatch(r->r.startsWith("POST /api/internal/telegram/companion/123/preferences ")&&r.contains("\"enabled\":false"));
        requests.clear();PlayerPanel.page(backend,123,"notifications");assertThat(requests.getFirst()).startsWith("GET ");
    }
    @Test void reportsHaveHumanStatusesAndPagination()throws Exception {
        var reports=new ArrayList<Map<String,Object>>();
        for(int i=0;i<6;i++)reports.add(Map.of("text","Мой репорт "+i,"status",i==5?"CLOSED":"IN_PROGRESS","createdAt","2026-11-11T18:00:00Z"));
        account.put("reports",reports);
        assertThat(PlayerPanel.page(backend,123,"reports").text()).contains("В работе","Мой репорт 4").doesNotContain("Мой репорт 5");
        assertThat(PlayerPanel.page(backend,123,"reports:5").text()).contains("Закрыто","Мой репорт 5");
    }
    @Test void malformedBackendAndHttpErrorsNeverBecomeFalseEmptyOrDisabledStates()throws Exception {
        account.put("cities",Map.of());
        assertThatThrownBy(()->PlayerPanel.page(backend,123,"cities")).isInstanceOf(java.io.IOException.class);
        profile.put("onlineState","invented");
        assertThatThrownBy(()->PlayerPanel.page(backend,123,"account")).isInstanceOf(java.io.IOException.class);
        preferences.remove("reports");
        assertThatThrownBy(()->PlayerPanel.page(backend,123,"preferences")).isInstanceOf(java.io.IOException.class);
        status=503;assertThatThrownBy(()->PlayerPanel.page(backend,123,"preferences")).isInstanceOf(java.io.IOException.class);
        for(String action:List.of("pref:news:toggle","pref:news:off:999","pref:unknown:on","cities:-1","reports:1000"))assertThat(PlayerPanel.handles(action)).isFalse();
    }
}
