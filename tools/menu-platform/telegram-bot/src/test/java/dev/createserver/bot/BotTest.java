package dev.createserver.bot;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class BotTest {
    private HttpServer backend;
    private HttpServer telegram;
    private final List<String> backendBodies = new ArrayList<>();
    private final List<String> messages = new ArrayList<>();
    private final List<String> edits = new ArrayList<>();
    private final List<String> answers = new ArrayList<>();
    private int lookupStatus = 404;
    private String accountStatus = "ACTIVE";
    private String accountBody;
    private int editStatus = 200;
    private String editError = "message to edit not found";
    private final JsonMapper json = JsonMapper.builder().build();
    private final String token = "a".repeat(43);
    private int prepareStatus = 200;
    private int acceptStatus = 200;
    private int deviceStatus = 200;
    private final String deviceId = "12345678-1234-1234-1234-123456789abc";

    @BeforeEach
    void startFakes() throws Exception {
        backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        backend.createContext("/api/internal/bridge/status", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Bot-Service-Secret")).isEqualTo("fake-service-secret");
            byte[] body="{\"state\":\"UNKNOWN\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });
        backend.createContext("/api/internal/devices", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Bot-Service-Secret")).isEqualTo("fake-service-secret");
            String path = exchange.getRequestURI().getPath();
            backendBodies.add(path + " " + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            Object result = path.endsWith("/preview") ? Map.of("deviceId",deviceId,"username","engineer","label","Windows PC")
                : List.of(Map.of("deviceId",deviceId,"label","Windows PC","state","TRUSTED"));
            boolean mutation = path.endsWith("/approve") || path.endsWith("/revoke");
            if (mutation && deviceStatus == 200) exchange.sendResponseHeaders(204,-1);
            else { byte[] body=json.writeValueAsBytes(result); exchange.sendResponseHeaders(deviceStatus,body.length); exchange.getResponseBody().write(body); }
            exchange.close();
        });
        backend.createContext("/api/internal/telegram/accept", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Bot-Service-Secret")).isEqualTo("fake-service-secret");
            backendBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(acceptStatus, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        backend.createContext("/api/internal/telegram/prepare", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Bot-Service-Secret")).isEqualTo("fake-service-secret");
            backendBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = json.writeValueAsBytes(Map.of("confirmationToken", token, "username", "engineer"));
            exchange.sendResponseHeaders(prepareStatus, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        backend.createContext("/api/internal/telegram/users/123456789012", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Bot-Service-Secret")).isEqualTo("fake-service-secret");
            byte[] body = (accountBody == null ? "{\"platformUserId\":\"12345678-1234-1234-1234-123456789012\",\"username\":\"engineer\",\"status\":\"" + accountStatus + "\"}" : accountBody).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(lookupStatus, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        telegram = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        telegram.createContext("/sendMessage", exchange -> {
            messages.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = ("{\"ok\":true,\"result\":{\"message_id\":" + messages.size() + "}}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        telegram.createContext("/editMessageText", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            edits.add(request);
            byte[] body = json.writeValueAsBytes(editStatus == 200 ? Map.of("ok", true, "result", Map.of())
                : Map.of("ok", false, "description", editError));
            exchange.sendResponseHeaders(editStatus, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        telegram.createContext("/getUpdates", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(request).contains("\"offset\":42", "\"timeout\":30", "message", "callback_query");
            byte[] body = "{\"ok\":true,\"result\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        telegram.createContext("/answerCallbackQuery", exchange -> {
            answers.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"ok\":true,\"result\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        backend.start(); telegram.start();
    }

    @AfterEach
    void stopFakes() { backend.stop(0); telegram.stop(0); }

    private BackendClient backendClient() {
        return new BackendClient(URI.create("http://127.0.0.1:" + backend.getAddress().getPort()),
                "fake-service-secret", HttpClient.newHttpClient());
    }
    private TelegramClient telegramClient() {
        return new TelegramClient(URI.create("http://127.0.0.1:" + telegram.getAddress().getPort() + "/"),
                BotHttpClients.telegram(ProxyConfiguration.fromEnvironment(Map.of())));
    }
    private BotHandler handler() {
        return new BotHandler("create_test_bot", "https://example.com/launcher", backendClient(), telegramClient());
    }
    private String update(String command, String type) {
        return "{\"update_id\":1,\"message\":{\"chat\":{\"id\":123456789012,\"type\":\"" + type +
                "\"},\"from\":{\"id\":123456789012,\"is_bot\":false},\"text\":\"" + command + "\"}}";
    }

    @Test
    void parsesOnlyStartAndCorrectBotWithValidDeepLinkToken() {
        assertThat(StartCommand.parse("/start", "create_test_bot").orElseThrow().registrationToken()).isNull();
        assertThat(StartCommand.parse("/start@create_test_bot " + token, "create_test_bot")).isPresent();
        assertThat(StartCommand.parse("/start@other_bot " + token, "create_test_bot")).isEmpty();
        assertThat(StartCommand.parse("/start invalid token", "create_test_bot")).isEmpty();
        assertThat(StartCommand.parse("/help", "create_test_bot")).isEmpty();
        assertThat(new StartCommand(token).toString()).doesNotContain(token);
    }
    private String deviceCallback(String action, long sender) {
        return "{\"callback_query\":{\"id\":\"device-test\",\"data\":\""+action+":"+deviceId+
            "\",\"from\":{\"id\":"+sender+",\"is_bot\":false},\"message\":{\"message_id\":1,\"chat\":{\"id\":123456789012,\"type\":\"private\"}}}}";
    }
    @Test void deviceQrNeedsExplicitNumericOwnerConfirmation() throws Exception {
        handler().handle(json.readTree(update("/start d_"+token,"private")));
        assertThat(backendBodies.getFirst()).contains("/preview","\"telegramUserId\":123456789012",token).doesNotContain("/approve");
        assertThat(messages.getLast()).contains("Подтвердить устройство","engineer","Windows PC").doesNotContain(token);
        handler().handle(json.readTree(deviceCallback("trust",42)));
        assertThat(backendBodies).hasSize(1);
        handler().handle(json.readTree(deviceCallback("trust",123456789012L)));
        assertThat(backendBodies.getLast()).contains("/approve","\"telegramUserId\":123456789012");
        assertThat(edits.getLast()).contains("Устройство подтверждено");
    }
    @Test void deviceRevocationHasConfirmationAndBackendFailureIsSafe() throws Exception {
        handler().handle(json.readTree(deviceCallback("drop",123456789012L)));
        assertThat(backendBodies.getLast()).contains("/telegram/123456789012");
        assertThat(edits.getLast()).contains("Отозвать устройство");
        handler().handle(json.readTree(deviceCallback("revoke",123456789012L)));
        assertThat(backendBodies.getLast()).contains("/revoke");
        assertThat(edits.getLast()).contains("Доверие устройству отозвано");
        deviceStatus=404;
        handler().handle(json.readTree(update("/start d_"+token,"private")));
        assertThat(messages.getLast()).contains("недоступно").doesNotContain(token,"404","fake-service-secret");
    }

    @Test
    void welcomeContainsServerDescriptionAndLauncherButtonWithoutBackendMutation() throws Exception {
        handler().handle(json.readTree(update("/start", "private")));
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst()).contains("RE:CREATED", "📥 Скачать Launcher", "🔑 Ввести код", "inline_keyboard");
        assertThat(backendBodies).isEmpty();
    }

    @Test
    void verificationUsesNumericUpdateAuthorAndNullableUsernameViaAuthenticatedBackendHttp() throws Exception {
        handler().handle(json.readTree(update("/start " + token, "private")));
        assertThat(backendBodies).hasSize(1);
        var sent = json.readTree(backendBodies.getFirst());
        assertThat(sent.path("telegramUserId").asLong()).isEqualTo(123456789012L);
        assertThat(sent.path("telegramUsername").isMissingNode()).isTrue();
        assertThat(sent.path("registrationToken").asText()).isEqualTo(token);
        assertThat(messages.getFirst()).contains("Подтвердить регистрацию", "verify:", "engineer").doesNotContain("регистрация завершена");
    }

    @Test
    void phoneCodePreparesAndButtonAloneConfirmsWithNumericAuthor() throws Exception {
        handler().handle(json.readTree(update("/code ABCDE-FGHJK", "private")));
        var prepared = json.readTree(backendBodies.getFirst());
        assertThat(prepared.path("verificationCode").asText()).isEqualTo("ABCDE-FGHJK");
        assertThat(prepared.path("registrationToken").isNull()).isTrue();
        handler().handle(json.readTree("{\"callback_query\":{\"id\":\"callback-1\",\"data\":\"verify:" + token +
            "\",\"from\":{\"id\":123456789012,\"is_bot\":false},\"message\":{\"chat\":{\"id\":123456789012,\"type\":\"private\"}}}}"));
        var accepted = json.readTree(backendBodies.getLast());
        assertThat(accepted.path("telegramUserId").asLong()).isEqualTo(123456789012L);
        assertThat(accepted.path("telegramUsername").isNull()).isTrue();
        assertThat(accepted.path("confirmationToken").asText()).isEqualTo(token);
        assertThat(messages.getLast()).contains("Можно вернуться в Launcher");
    }

    @Test
    void rateLimitAndExpiredCodeAreSafeRepliesWithoutRetryException() throws Exception {
        prepareStatus = 429;
        handler().handle(json.readTree(update("/code ABCDE-FGHJK", "private")));
        assertThat(messages.getLast()).contains("Слишком много попыток").doesNotContain("ABCDE-FGHJK");
        prepareStatus = 400;
        handler().handle(json.readTree(update("/code ABCDE-FGHJK", "private")));
        assertThat(messages.getLast()).contains("недействительны").doesNotContain("ABCDE-FGHJK");
    }
    @Test
    void callbackReplayAndDuplicateIdentityHaveSafePhoneReplies() throws Exception {
        String callback = "{\"callback_query\":{\"id\":\"callback-1\",\"data\":\"verify:" + token +
            "\",\"from\":{\"id\":123456789012,\"is_bot\":false},\"message\":{\"chat\":{\"id\":123456789012,\"type\":\"private\"}}}}";
        acceptStatus = 400;
        handler().handle(json.readTree(callback));
        assertThat(messages.getLast()).contains("недействительно").doesNotContain(token);
        acceptStatus = 409;
        handler().handle(json.readTree(callback));
        assertThat(messages.getLast()).contains("недоступно").doesNotContain(token);
        assertThat(messages).noneMatch(message -> message.contains("Можно вернуться"));
    }
    @Test
    void forwardedMessagesAndMismatchedCallbackSenderCannotMutateAccount() throws Exception {
        String forwarded = update("/start " + token, "private").replace("\"text\":", "\"forward_origin\":{},\"text\":");
        handler().handle(json.readTree(forwarded));
        handler().handle(json.readTree("{\"callback_query\":{\"id\":\"callback-1\",\"data\":\"verify:" + token +
            "\",\"from\":{\"id\":987654321,\"is_bot\":false},\"message\":{\"chat\":{\"id\":123456789012,\"type\":\"private\"}}}}"));
        assertThat(backendBodies).isEmpty();
    }
    @Test
    void bareCodeAndPromptDoNotRequireDesktopOrBotConversationState() throws Exception {
        handler().handle(json.readTree(update("/code", "private")));
        assertThat(backendBodies).isEmpty();
        handler().handle(json.readTree(update("abcde-fghjk", "private")));
        assertThat(json.readTree(backendBodies.getFirst()).path("verificationCode").asText()).isEqualTo("abcde-fghjk");
        assertThat(messages.getLast()).contains("Подтвердить регистрацию");
    }

    @Test
    void groupAndBotSendersCannotConfirm() throws Exception {
        handler().handle(json.readTree(update("/start " + token, "group")));
        handler().handle(json.readTree(update("/start " + token, "private").replace("\"is_bot\":false", "\"is_bot\":true")));
        assertThat(backendBodies).isEmpty();
    }

    @Test
    void pollingAndLinkedUserLookupWorkAgainstFakeHttp() throws Exception {
        lookupStatus = 200;
        assertThat(telegramClient().updates(42).isArray()).isTrue();
        assertThat(backendClient().linkedUser(123456789012L)).isEqualTo(200);
    }

    private void navigate(BotHandler handler, String data, long messageId) throws Exception {
        handler.handle(json.readTree("{\"callback_query\":{\"id\":\"nav-1\",\"data\":\"" + data +
            "\",\"from\":{\"id\":123456789012,\"is_bot\":false},\"message\":{\"message_id\":" + messageId +
            ",\"chat\":{\"id\":123456789012,\"type\":\"private\"}}}}"));
    }
    @Test void devicePreviewAndRegistrationPreviewDoNotCreateMessageChains()throws Exception {
        var handler=handler();handler.handle(json.readTree(update("/start","private")));
        handler.handle(json.readTree(update("/start d_"+token,"private")));
        assertThat(edits.getLast()).contains("Подтвердить устройство");
        handler.handle(json.readTree(update("/code ABCDE-FGHJK","private")));
        assertThat(edits.getLast()).contains("Подтвердить регистрацию");
        assertThat(messages).hasSize(1);assertThat(edits).hasSize(2);
    }
    @Test void htmlFormattingNeverInterpretsDynamicMarkup(){
        var page=new BotPage("RE:CREATED\n\n<a href='bad'>player</a> & <b>report</b>",List.of());
        assertThat(page.html()).startsWith("<b>RE:CREATED</b>").contains("&lt;a","&amp;","&lt;b&gt;report").doesNotContain("<a ","<b>report");
    }

    @Test
    void linkedMenuAndAllNavigationEditOneMessageWithoutMutations() throws Exception {
        lookupStatus = 200;
        var handler = handler();
        handler.handle(json.readTree(update("/start", "private")));
        assertThat(messages.getFirst()).contains("👤 Аккаунт", "🎮 Сервер", "🔔 Уведомления", "📥 Launcher", "❓ Помощь").doesNotContain("Ввести код");
        navigate(handler, "menu:v1:account", 1);
        assertThat(edits.getLast()).contains("Имя: engineer", "Статус: Активен", "Telegram: привязан").doesNotContain("password", "platformUserId");
        navigate(handler, "menu:v1:server", 1);
        assertThat(edits.getLast()).contains("Нет свежих данных").doesNotContain("25565", "Игроков: 0", "онлайн");
        navigate(handler, "menu:v1:notifications", 1);
        assertThat(edits.getLast()).contains("подписка не включена");
        navigate(handler, "menu:v1:launcher", 1);
        assertThat(edits.getLast()).contains("https://example.com/launcher", "← Главное меню");
        navigate(handler, "menu:v1:help", 1);
        assertThat(edits.getLast()).contains("Отсканируйте QR телефоном");
        navigate(handler, "menu:v1:home", 1);
        handler.handle(json.readTree(update("/start", "private")));
        assertThat(messages).hasSize(1);
        assertThat(edits).hasSize(7).allMatch(edit -> json.readTree(edit).path("message_id").asLong() == 1);
        assertThat(backendBodies).isEmpty();
    }

    @Test
    void unlinkedNavigationCodeAndUnknownCommandsRemainCompact() throws Exception {
        var handler = handler();
        handler.handle(json.readTree(update("/start", "private")));
        navigate(handler, "menu:v1:code", 1);
        assertThat(edits.getLast()).contains("/code XXXXX-XXXXX");
        navigate(handler, "menu:v1:account", 1);
        assertThat(edits.getLast()).doesNotContain("Имя: engineer", "Аккаунт").contains("Создайте аккаунт");
        handler.handle(json.readTree(update("/unknown secret", "private")));
        assertThat(edits.getLast()).contains("Не понял сообщение").doesNotContain("secret");
        assertThat(messages).hasSize(1);
        assertThat(backendBodies).isEmpty();
    }

    @Test
    void inactiveBindingAndLookupErrorsNeverImplyActiveOrUnlinked() throws Exception {
        lookupStatus = 200; accountStatus = "DISABLED";
        var handler = handler();
        handler.handle(json.readTree(update("/start", "private")));
        assertThat(messages.getLast()).contains("Telegram привязан, но аккаунт пока не активен").doesNotContain("👤 Аккаунт");
        for (int status : List.of(401, 403, 429, 503)) {
            lookupStatus = status;
            handler.handle(json.readTree(update("/start", "private")));
            assertThat(edits.getLast()).contains("временно недоступен").doesNotContain("Создайте аккаунт", "engineer", "503", "fake-service-secret");
        }
        navigate(handler, "menu:v1:help", 1);
        assertThat(edits.getLast()).contains("❓ Помощь");
        navigate(handler, "menu:v1:launcher", 1);
        assertThat(edits.getLast()).contains("https://example.com/launcher");
        lookupStatus = 200; accountBody = "{}";
        handler.handle(json.readTree(update("/start", "private")));
        assertThat(edits.getLast()).contains("временно недоступен");
    }

    @Test
    void staleAndUnknownCallbacksRecoverWithoutVerificationMutation() throws Exception {
        var handler = handler();
        handler.handle(json.readTree(update("/start", "private")));
        navigate(handler, "menu:v0:account", 1);
        assertThat(answers.getLast()).contains("устарела");
        navigate(handler, "menu:v1:help", 99);
        assertThat(json.readTree(edits.getLast()).path("message_id").asLong()).isEqualTo(1);
        assertThat(edits.getLast()).contains("Добро пожаловать");
        navigate(handler, "verify:invalid", 1);
        assertThat(backendBodies).isEmpty();
        assertThat(messages).hasSize(1);
    }

    @Test
    void deletedMenuIsReplacedOnceAndUnchangedEditDoesNotCreateMessages() throws Exception {
        var handler = handler();
        handler.handle(json.readTree(update("/start", "private")));
        editStatus = 400; editError = "Bad Request: message is not modified";
        handler.handle(json.readTree(update("/start", "private")));
        assertThat(messages).hasSize(1);
        editError = "Bad Request: message to edit not found";
        handler.handle(json.readTree(update("/start", "private")));
        assertThat(messages).hasSize(2);
        editStatus = 200;
        navigate(handler, "menu:v1:help", 2);
        assertThat(json.readTree(edits.getLast()).path("message_id").asLong()).isEqualTo(2);
        editStatus = 500;
        assertThatThrownBy(() -> handler.handle(json.readTree(update("/start", "private"))))
            .isInstanceOf(java.io.IOException.class).hasMessage("Telegram communication failed");
        assertThat(messages).hasSize(2);
    }

    @Test
    void confirmationAndRepeatedStartReuseCanonicalMenuAndRemoveProof() throws Exception {
        var handler = handler();
        handler.handle(json.readTree(update("/start", "private")));
        handler.handle(json.readTree(update("/code ABCDE-FGHJK", "private")));
        navigate(handler, "verify:" + token, 2);
        assertThat(edits.getLast()).contains("Можно вернуться в Launcher").doesNotContain(token);
        assertThat(json.readTree(edits.getLast()).path("message_id").asLong()).isEqualTo(1);
        handler.handle(json.readTree(update("/start", "private")));
        assertThat(json.readTree(edits.getLast()).path("message_id").asLong()).isEqualTo(1);
        assertThat(messages).hasSize(1);
    }

    @Test
    void unavailableRegistrationKeepsRetryProofAndDoesNotLeakSecrets() throws Exception {
        var handler = handler();
        prepareStatus = 503;
        handler.handle(json.readTree(update("/start " + token, "private")));
        assertThat(messages.getLast()).contains("сейчас недоступно").doesNotContain(token);
        acceptStatus = 503;
        navigate(handler, "verify:" + token, 3);
        assertThat(edits.getLast()).contains("Повторить подтверждение");
        assertThat(json.readTree(edits.getLast()).path("text").asText()).doesNotContain(token, "fake-service-secret");
        acceptStatus = 200;
        navigate(handler, "verify:" + token, 3);
        assertThat(edits.getLast()).contains("Можно вернуться").doesNotContain(token);
    }

    @Test
    void launcherDownloadHasNoPlaceholderDefaultAndRejectsUnsafeUrls() throws Exception {
        assertThat(BotMain.launcherUrl(Map.of())).isNull();
        assertThat(BotMain.launcherUrl(Map.of("LAUNCHER_DOWNLOAD_URL", "https://downloads.example.org/setup.exe")))
            .isEqualTo("https://downloads.example.org/setup.exe");
        for (String url : List.of("http://example.com", "https://user:secret@example.com/x", "https:///missing", "https://example.com/#fragment")) {
            assertThatThrownBy(() -> BotMain.launcherUrl(Map.of("LAUNCHER_DOWNLOAD_URL", url)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("secret");
        }
        var handler = new BotHandler("create_test_bot", null, backendClient(), telegramClient());
        handler.handle(json.readTree(update("/start", "private")));
        navigate(handler, "menu:v1:launcher", 1);
        assertThat(edits.getLast()).contains("Ссылка на скачивание пока не настроена").doesNotContain("\"url\"");
    }

    @Test
    void connectionFailureHasSafeReplyAndNavigationRecoversAfterProcessRestart() throws Exception {
        var handler = handler();
        backend.stop(0);
        handler.handle(json.readTree(update("/start", "private")));
        assertThat(messages.getLast()).contains("временно недоступен").doesNotContain("127.0.0.1", "fake-service-secret");
        // New process has no menu cache but can adopt the message supplied by a trusted callback update.
        navigate(handler(), "menu:v1:help", 1);
        assertThat(edits.getLast()).contains("❓ Помощь");
        assertThat(messages).hasSize(1);
    }

    @Test
    void freshAccountLookupPreventsStaleActiveMenuAfterDisableAndCrossUserNavigation() throws Exception {
        lookupStatus = 200;
        var handler = handler();
        handler.handle(json.readTree(update("/start", "private")));
        accountStatus = "DISABLED";
        navigate(handler, "menu:v1:account", 1);
        assertThat(edits.getLast()).contains("аккаунт пока не активен").doesNotContain("Имя: engineer");
        int count = edits.size();
        handler.handle(json.readTree("{\"callback_query\":{\"id\":\"cross-user\",\"data\":\"menu:v1:account\",\"from\":{\"id\":999,\"is_bot\":false},\"message\":{\"message_id\":1,\"chat\":{\"id\":123456789012,\"type\":\"private\"}}}}"));
        assertThat(edits).hasSize(count);
        assertThat(backendBodies).isEmpty();
    }
}
