package dev.createserver.bot;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class BroadcastTest {
    final JsonMapper json=JsonMapper.builder().build();HttpServer be,tg;
    BackendClient backend;TelegramClient telegram;List<String> requests=new ArrayList<>(),sends=new ArrayList<>();
    int backendStatus=200,sendStatus=200;String status="DRAFT";
    String id="12345678-1234-1234-1234-123456789abc",token="12345678-1234-1234-1234-123456789abd";
    Map<String,Object> post(){return Map.of("id",id,"status",status,"text","Новости <тест>","photoFileId","Photo_123","eligible",2,"total",2,"sent",1,"failed",1,"pending",0);}
    @BeforeEach void setup()throws Exception {
        be=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        be.createContext("/",e->{
            assertThat(e.getRequestHeaders().getFirst("X-Bot-Service-Secret")).isEqualTo("fake-service-secret");
            String path=e.getRequestURI().toString(),body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);requests.add(path+" "+body);
            if(path.endsWith("/publish"))status="QUEUED";
            Object result=path.endsWith("/claim")?List.of(Map.of("postId",id,"telegramUserId",222,"attemptToken",token,"text","News","photoFileId","Photo_123"))
                :path.contains("broadcasts?")?List.of(post()):post();
            byte[] bytes=json.writeValueAsBytes(result);e.sendResponseHeaders(backendStatus,bytes.length);e.getResponseBody().write(bytes);e.close();
        });be.start();backend=new BackendClient(URI.create("http://127.0.0.1:"+be.getAddress().getPort()),"fake-service-secret",HttpClient.newHttpClient());
        tg=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        tg.createContext("/",e->{String path=e.getRequestURI().getPath(),body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);boolean post=path.equals("/sendPhoto")||path.equals("/sendMessage");if(post)sends.add(path+" "+body);int code=post?sendStatus:200;
            byte[] bytes=json.writeValueAsBytes(Map.of("ok",code==200,"result",Map.of("message_id",7),"parameters",Map.of("retry_after",90),"description","SECRET description must never escape"));e.sendResponseHeaders(code,bytes.length);e.getResponseBody().write(bytes);e.close();});tg.start();telegram=new TelegramClient(URI.create("http://127.0.0.1:"+tg.getAddress().getPort()+"/"),new okhttp3.OkHttpClient());
    }
    @AfterEach void stop(){be.stop(0);tg.stop(0);}
    BroadcastPanel panel(){return new BroadcastPanel(Set.of(111L),backend,telegram);}
    tools.jackson.databind.JsonNode photo(String caption)throws Exception{return json.readTree(json.writeValueAsString(Map.of("photo",List.of(Map.of("file_id","small"),Map.of("file_id","Photo_123")),"caption",caption)));}
    @Test void photoDraftHasPrivatePreviewAndRequiresExplicitConfirmation()throws Exception{
        var p=panel();p.page(111,"broadcast:new");var page=p.message(111,123,photo("Новости <тест>"));assertThat(page.text()).contains("Подтвердить","2");assertThat(requests).hasSize(1);assertThat(requests.getFirst()).contains("Photo_123","telegramUserId","111").doesNotContain("/publish");assertThat(sends).hasSize(1);assertThat(sends.getFirst()).contains("sendPhoto","\"chat_id\":111","Photo_123").doesNotContain("parse_mode");p.page(111,"broadcast:send:"+id);assertThat(requests.getLast()).contains("/publish");
    }
    @Test void replayDraftUsesSameCommandAndNoSecondBroadcast()throws Exception{var p=panel();p.page(111,"broadcast:new");p.message(111,123,photo("Новости"));p.page(111,"broadcast:new");p.message(111,123,photo("Новости"));assertThat(requests.getFirst()).isEqualTo(requests.getLast());assertThat(requests).allMatch(x->!x.contains("/publish"));}
    @Test void ordinaryUserCannotComposeOrSendForgedCallback()throws Exception{var p=panel();assertThatThrownBy(()->p.page(222,"broadcast:new")).isInstanceOf(java.io.IOException.class);assertThat(p.message(222,1,photo("News"))).isNull();var h=new BotHandler("recreating_bot",null,backend,telegram,Set.of(111L));h.handle(json.readTree("{\"callback_query\":{\"id\":\"cb\",\"data\":\"broadcast:send:"+id+"\",\"from\":{\"id\":222},\"message\":{\"message_id\":1,\"chat\":{\"id\":222,\"type\":\"private\"}}}}"));assertThat(requests).isEmpty();assertThat(sends).isEmpty();}
    @Test void invalidCaptionAndAlbumNeverCreatePost()throws Exception{var p=panel();p.page(111,"broadcast:new");assertThat(p.message(111,1,photo("x".repeat(1025))).text()).contains("1024");var album=photo("News");((tools.jackson.databind.node.ObjectNode)album).put("media_group_id","album");assertThat(p.message(111,2,album).text()).contains("не альбом");assertThat(requests).isEmpty();assertThat(sends).isEmpty();p.message(111,3,json.readTree("{\"text\":\"/start\"}"));assertThat(p.message(111,4,photo("News"))).isNull();}
    @Test void backendFailureDoesNotPublishOrSendAndKeepsComposer()throws Exception{var p=panel();p.page(111,"broadcast:new");backendStatus=503;assertThatThrownBy(()->p.message(111,1,photo("News"))).isInstanceOf(java.io.IOException.class);assertThat(sends).isEmpty();backendStatus=200;assertThat(p.message(111,1,photo("News"))).isNotNull();assertThat(requests).noneMatch(x->x.contains("/publish"));}
    @Test void blockedRecipientCountedFailedAndRateLimitDeferred()throws Exception{sendStatus=403;panel().deliver();assertThat(requests.getLast()).contains("\"outcome\":\"FAILED\"");requests.clear();sendStatus=429;panel().deliver();assertThat(requests.getLast()).contains("\"outcome\":\"RETRY\"","\"retryAfter\":90");}
    @Test void ambiguousServerErrorIsNotRetriedAndNoSecretsInException()throws Exception{sendStatus=500;panel().deliver();assertThat(requests.getLast()).contains("\"outcome\":\"UNCERTAIN\"");assertThat(sends).hasSize(1);assertThatThrownBy(()->telegram.sendPost(111,"News",null)).hasMessage("Telegram communication failed").hasMessageNotContaining("SECRET");}
    @Test void successfulDeliveryStoresTelegramMessageIdAndEmptyAdminNeverClaims()throws Exception{panel().deliver();assertThat(requests.getLast()).contains("\"outcome\":\"SENT\"","\"messageId\":7","\"recipientId\":222");requests.clear();new BroadcastPanel(Set.of(),backend,telegram).deliver();assertThat(requests).isEmpty();}
    @Test void reportAndStaleCallbacksRemainSafe()throws Exception{var p=panel();assertThat(p.page(111,"broadcast:send:bad").text()).contains("устарела");assertThat(requests).isEmpty();status="COMPLETED";var page=p.page(111,"broadcast:view:"+id);assertThat(page.text()).contains("Доставлено: 1","Не доставлено: 1");assertThat(page.keyboard().toString()).doesNotContain("broadcast:send");}
}
