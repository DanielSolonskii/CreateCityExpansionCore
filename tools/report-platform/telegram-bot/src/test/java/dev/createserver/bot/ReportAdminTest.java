package dev.createserver.bot;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class ReportAdminTest {
    final JsonMapper json=JsonMapper.builder().build();HttpServer be,tg;BackendClient backend;TelegramClient telegram;
    final List<String> calls=new ArrayList<>(),messages=new ArrayList<>(),edits=new ArrayList<>();
    int backendStatus=200,sendStatus=200;boolean notification=true;
    final String id="12345678-1234-1234-1234-123456789abc",lease="12345678-1234-1234-1234-123456789abd";
    Map<String,Object> row(){return Map.of("id",id,"username","engineer","status","OPEN","text","<admin> broken train","dimension","minecraft:overworld","createdAt","2026-10-04T00:00:00Z","x",1,"y",2,"z",3);}
    @BeforeEach void setup()throws Exception{
        be=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        be.createContext("/",e->{
            assertThat(e.getRequestHeaders().getFirst("X-Bot-Service-Secret")).isEqualTo("fake-service-secret");
            String path=e.getRequestURI().toString(),body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);calls.add(path+" "+body);
            Object result=path.contains("/claim")?(notification?List.of(Map.of("reportId",id,"leaseToken",lease,"report",row())):List.of())
              :path.contains("/player")?Map.of("platformUserId",id,"minecraftUuid",lease,"username","engineer","status","ACTIVE","telegramBound",true)
              :path.contains("/reports?")?List.of(row()):path.contains("/users/")?Map.of("platformUserId",id,"username","engineer","status","ACTIVE"):row();
            byte[] bytes=json.writeValueAsBytes(result);boolean ack=path.endsWith("/ack");
            if(ack && backendStatus==200){notification=false;e.sendResponseHeaders(204,-1);}else{e.sendResponseHeaders(backendStatus,bytes.length);e.getResponseBody().write(bytes);}e.close();
        });be.start();backend=new BackendClient(URI.create("http://127.0.0.1:"+be.getAddress().getPort()),"fake-service-secret",HttpClient.newHttpClient());
        tg=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        tg.createContext("/",e->{String body=new String(e.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);String path=e.getRequestURI().getPath();if(path.equals("/sendMessage"))messages.add(body);if(path.equals("/editMessageText"))edits.add(body);int status=path.equals("/sendMessage")?sendStatus:200;
            byte[] bytes=json.writeValueAsBytes(Map.of("ok",status==200,"result",Map.of("message_id",1)));e.sendResponseHeaders(status,bytes.length);e.getResponseBody().write(bytes);e.close();});tg.start();telegram=new TelegramClient(URI.create("http://127.0.0.1:"+tg.getAddress().getPort()+"/"),new okhttp3.OkHttpClient());
    }
    @AfterEach void stop(){be.stop(0);tg.stop(0);}
    BotHandler handler(){return new BotHandler("recreating_bot",null,backend,telegram,Set.of(111L));}
    tools.jackson.databind.JsonNode update(long user,String data)throws Exception{return json.readTree(json.writeValueAsString(Map.of("callback_query",Map.of("id","callback-id","data",data,"from",Map.of("id",user),"message",Map.of("message_id",1,"chat",Map.of("id",user,"type","private"))))));}
    tools.jackson.databind.JsonNode command(long user,String text)throws Exception{return json.readTree(json.writeValueAsString(Map.of("message",Map.of("text",text,"from",Map.of("id",user),"chat",Map.of("id",user,"type","private")))));}
    @Test void ordinaryUserHasNoAdminMenuAndForgedCallbackNeverReachesBackend()throws Exception{var h=handler();h.handle(command(222,"/start"));assertThat(messages.getFirst()).doesNotContain("Админ","admin:");calls.clear();h.handle(update(222,"admin:close:"+id));assertThat(calls).isEmpty();}
    @Test void adminMenuListAndDetailEditSameMessage()throws Exception{var h=handler();h.handle(command(111,"/start"));assertThat(messages.getFirst()).contains("Админ");h.handle(update(111,"admin:list:0"));h.handle(update(111,"admin:report:"+id));assertThat(messages).hasSize(1);assertThat(edits).hasSize(2);assertThat(edits.getLast()).contains("Взять","Закрыть","Отклонить","Игрок","broken train").doesNotContain("parse_mode");}
    @Test void actionUsesNumericSenderAndStableCommandIdOnReplay()throws Exception{var h=handler();h.handle(update(111,"admin:take:"+id));h.handle(update(111,"admin:take:"+id));assertThat(calls).hasSize(2);assertThat(calls.getFirst()).isEqualTo(calls.getLast()).contains("\"telegramUserId\":111","\"action\":\"TAKE\"");}
    @Test void playerCardNoSecretsAndStaleCallbackSafe()throws Exception{var h=handler();h.handle(update(111,"admin:player:"+id));assertThat(edits.getLast()).contains("engineer","Minecraft UUID").doesNotContain("password","credential");calls.clear();h.handle(update(111,"admin:close:invalid"));assertThat(calls).isEmpty();assertThat(edits.getLast()).contains("устарела");}
    @Test void backendFailureShowsRetryWithoutFakeSuccess()throws Exception{backendStatus=503;handler().handle(update(111,"admin:close:"+id));assertThat(edits.getLast()).contains("недоступен").doesNotContain("CLOSED");}
    @Test void notificationsOnlyAdminsAndAckAfterTelegramSuccess()throws Exception{handler().notifyReports();assertThat(messages).hasSize(1);assertThat(messages.getFirst()).contains("\"chat_id\":111");assertThat(calls.getFirst()).contains("claim");assertThat(calls.getLast()).contains("/ack",lease);}
    @Test void telegramFailureDoesNotAcknowledgeDelivery()throws Exception{sendStatus=500;handler().notifyReports();assertThat(calls).hasSize(1);assertThat(calls.getFirst()).contains("claim");assertThat(notification).isTrue();}
    @Test void emptyAdminsNeverClaimsAndStrictConfiguration()throws Exception{new BotHandler("recreating_bot",null,backend,telegram).notifyReports();assertThat(calls).isEmpty();assertThat(ReportAdminPanel.configured(Map.of("REPORT_ADMIN_TELEGRAM_IDS","111,222"))).containsExactlyInAnyOrder(111L,222L);for(String value:List.of("0","username","111,","-123"))assertThatThrownBy(()->ReportAdminPanel.configured(Map.of("REPORT_ADMIN_TELEGRAM_IDS",value))).isInstanceOf(IllegalArgumentException.class);}
}
