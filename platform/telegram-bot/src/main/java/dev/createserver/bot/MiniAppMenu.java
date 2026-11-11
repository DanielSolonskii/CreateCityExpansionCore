package dev.createserver.bot;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import okhttp3.*;
import tools.jackson.databind.json.JsonMapper;
/** Optional global Telegram menu button. Existing messages/verification callbacks stay intact. */
final class MiniAppMenu {
    static String url(Map<String,String> env){
        String value=env.get("MINI_APP_URL");if(value==null || value.isBlank())return null;
        try{URI uri=URI.create(value);if(!uri.getScheme().equals("https") || uri.getHost()==null || uri.getUserInfo()!=null || uri.getFragment()!=null || uri.getQuery()!=null)throw new IllegalArgumentException();return value;}
        catch(RuntimeException invalid){throw new IllegalArgumentException("MINI_APP_URL must be a public HTTPS URL without credentials/query");}
    }
    static void configure(Map<String,String> env,String token,OkHttpClient http) throws IOException {
        String url=url(env);if(url==null)return;
        send(URI.create("https://api.telegram.org/bot"+token+"/setChatMenuButton"),url,http);
    }
    static void send(URI endpoint,String url,OkHttpClient http) throws IOException {
        var json=JsonMapper.builder().build();
        var payload=Map.of("menu_button",Map.of("type","web_app","text","Открыть RE:CREATED","web_app",Map.of("url",url)));
        try(var response=http.newCall(new Request.Builder().url(endpoint.toString()).post(RequestBody.create(json.writeValueAsString(payload),MediaType.get("application/json"))).build()).execute()){
            if(response.code()!=200 || response.body()==null || !json.readTree(response.body().byteStream().readNBytes(65536)).path("ok").asBoolean())throw new IOException();
        }catch(IOException | RuntimeException failed){throw new IOException("Mini App menu configuration unavailable");}
    }
}
