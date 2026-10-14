package dev.createserver.bot;

import java.io.IOException;
import java.net.URI;
import okhttp3.*;
import java.util.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class TelegramClient {
    private final URI apiRoot;
    private final OkHttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    public TelegramClient(String token, OkHttpClient http) {
        this(URI.create("https://api.telegram.org/bot" + token + "/"), http);
    }
    // Injectable local fake API for tests; production always uses the official HTTPS endpoint.
    TelegramClient(URI apiRoot, OkHttpClient http) { this.apiRoot = apiRoot; this.http = http; }

    public JsonNode updates(long offset) throws IOException, InterruptedException {
        return post("getUpdates", Map.of("offset", offset, "timeout", 30, "allowed_updates", List.of("message", "callback_query")));
    }
    public void send(long chatId, String message, String launcherUrl) throws IOException, InterruptedException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId); body.put("text", message);
        if (launcherUrl != null) {
            body.put("reply_markup", Map.of("inline_keyboard", List.of(List.of(
                    Map.of("text", "Скачать launcher", "url", launcherUrl)))));
        }
        post("sendMessage", body);
    }
    public long sendPage(long chatId, BotPage page) throws IOException, InterruptedException {
        long id = post("sendMessage", pageBody(chatId, page)).path("message_id").asLong();
        if (id <= 0) throw new IOException("Invalid Telegram message response");
        return id;
    }
    /** Plain text/caption: admin input never becomes Telegram markup or a remote URL. */
    public long sendPost(long chatId,String text,String photoFileId) throws IOException,InterruptedException {
        Map<String,Object> body=new LinkedHashMap<>();body.put("chat_id",chatId);
        if(photoFileId==null)body.put("text",text);
        else {body.put("photo",photoFileId);body.put("caption",text);}
        long id=post(photoFileId==null?"sendMessage":"sendPhoto",body).path("message_id").asLong();
        if(id<=0)throw new IOException("Invalid Telegram message response");return id;
    }
    public static final class Rejected extends IOException {
        private final int status,retryAfter;
        Rejected(int status,int retryAfter){super("Telegram communication failed");this.status=status;this.retryAfter=retryAfter;}
        public int status(){return status;}public int retryAfter(){return retryAfter;}
    }
    /** False only for an explicitly missing/uneditable message, never for transport failures. */
    public boolean editPage(long chatId, long messageId, BotPage page) throws IOException, InterruptedException {
        Map<String, Object> body = pageBody(chatId, page);
        body.put("message_id", messageId);
        try { post("editMessageText", body); return true; }
        catch (EditRejected exception) { return exception.unchanged; }
    }
    /** Retire only the old menu's buttons; never delete conversation history. */
    public void clearKeyboard(long chatId, long messageId) throws IOException, InterruptedException {
        post("editMessageReplyMarkup", Map.of("chat_id", chatId, "message_id", messageId,
                "reply_markup", Map.of("inline_keyboard", List.of())));
    }
    private Map<String, Object> pageBody(long chatId, BotPage page) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId); body.put("text", page.html());
        body.put("parse_mode", "HTML");
        body.put("reply_markup", Map.of("inline_keyboard", page.keyboard()));
        body.put("link_preview_options", Map.of("is_disabled", true));
        return body;
    }
    private JsonNode post(String method, Object body) throws IOException, InterruptedException {
        Request request = new Request.Builder().url(apiRoot.resolve(method).toString())
                .post(RequestBody.create(json.writeValueAsString(body), MediaType.get("application/json"))).build();
        try (Response response = http.newCall(request).execute()) {
            if (response.body() == null) { throw new IOException("Telegram request failed"); }
            JsonNode result;
            try { result = json.readTree(response.body().string()); }
            catch (RuntimeException exception) { throw new IOException("Invalid Telegram response"); }
            if (response.code() == 400 && method.equals("editMessageText")) {
                String description = result.path("description").asText().toLowerCase(Locale.ROOT);
                if (description.contains("message is not modified")) throw new EditRejected(true);
                if (description.contains("message to edit not found") || description.contains("message can't be edited"))
                    throw new EditRejected(false);
            }
            if (response.code() != 200 || !result.path("ok").asBoolean()) {
                throw new Rejected(response.code(),Math.min(86400,Math.max(1,result.path("parameters").path("retry_after").asInt(30))));
            }
            return result.path("result");
        } catch (EditRejected exception) {
            throw exception;
        } catch (Rejected exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            // URLs, proxy credentials and response bodies never escape into diagnostics.
            throw new IOException("Telegram communication failed");
        }
    }
    private static final class EditRejected extends IOException {
        private final boolean unchanged;
        EditRejected(boolean unchanged) { super("Telegram message edit unavailable"); this.unchanged = unchanged; }
    }
    public void confirmation(long chatId, String username, String ticket) throws IOException, InterruptedException {
        post("sendMessage", Map.of("chat_id", chatId,
            "text", "Вы подтверждаете регистрацию RE:CREATED для аккаунта " + username +
                ". Подтверждайте только свой аккаунт. Если это не ваш запрос, не нажимайте кнопку.",
            "reply_markup", Map.of("inline_keyboard", List.of(List.of(Map.of(
                "text", "Подтвердить регистрацию", "callback_data", "verify:" + ticket))))));
    }
    public void answer(String callbackId) throws IOException, InterruptedException {
        post("answerCallbackQuery", Map.of("callback_query_id", callbackId));
    }
    public void answer(String callbackId, String text) throws IOException, InterruptedException {
        post("answerCallbackQuery", Map.of("callback_query_id", callbackId, "text", text));
    }
}
