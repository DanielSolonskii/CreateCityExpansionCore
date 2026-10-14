package dev.createserver.bot;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;

public final class BotHandler {
    private final String botUsername, launcherUrl;
    private final BackendClient backend;
    private final TelegramClient telegram;
    private final ReportAdminPanel reports;
    private final BroadcastPanel broadcasts;
    // Bounded presentation state only; never cache verification proofs or account data.
    private final Map<Long, Long> menus = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Long> entry) { return size() > 10_000; }
    };
    // A new user message needs a new anchor: editing cannot move a Telegram message.
    private final Map<Long, Boolean> freshMenus = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<Long, Boolean> entry) { return size() > 10_000; }
    };
    private static final Set<String> ACTIONS = Set.of("home", "account", "server", "launcher", "notifications", "help", "code", "devices");
    public BotHandler(String username, String launcherUrl, BackendClient backend, TelegramClient telegram) {
        this(username,launcherUrl,backend,telegram,Set.of());
    }
    public BotHandler(String username,String launcherUrl,BackendClient backend,TelegramClient telegram,Set<Long> admins) {
        this.botUsername = username; this.launcherUrl = launcherUrl; this.backend = backend; this.telegram = telegram;
        this.reports=new ReportAdminPanel(admins,backend,telegram);
        this.broadcasts=new BroadcastPanel(admins,backend,telegram);
    }
    public void notifyReports() throws InterruptedException { reports.notifyPending(); }
    public void notifyBroadcasts() throws InterruptedException { broadcasts.deliver(); }
    public void handle(JsonNode update) throws IOException, InterruptedException {
        boolean callback = update.has("callback_query");
        JsonNode event = callback ? update.path("callback_query") : update.path("message");
        JsonNode message = callback ? event.path("message") : event;
        JsonNode sender = event.path("from");
        long userId = sender.path("id").asLong(), chatId = message.path("chat").path("id").asLong();
        if (!message.path("chat").path("type").asText().equals("private") || sender.path("is_bot").asBoolean() ||
                userId <= 0 || chatId != userId || message.has("forward_origin")) return;
        if (callback) {
            String data = event.path("data").asText();
            long messageId = message.path("message_id").asLong();
            if(data.startsWith("broadcast:")){
                telegram.answer(event.path("id").asText(),broadcasts.admin(userId)?"":"Нет доступа.");
                if(!broadcasts.admin(userId)||messageId<=0)return;
                BotPage page;
                try{page=broadcasts.page(userId,data);}
                catch(IOException failure){page=new BotPage("Рассылки временно недоступны или действие уже изменено. Обновите список, прежде чем создавать новый пост.",List.of(List.of(Map.of("text","📣 Рассылки","callback_data","broadcast:home"))));}
                renderConfirmation(chatId,messageId,page);return;
            }
            broadcasts.leave(userId);
            if(data.startsWith("admin:")) {
                telegram.answer(event.path("id").asText(),reports.admin(userId)?"":"Нет доступа.");
                if(!reports.admin(userId) || messageId<=0)return;
                BotPage page;
                try {page=reports.page(userId,data,event.path("id").asText());}
                catch(IOException failure){page=new BotPage("Репорт недоступен, уже изменён другим админом или сервис временно не отвечает.",List.of(List.of(Map.of("text","↻ Репорты","callback_data","admin:list:0"))));}
                // An older report alert is also refreshed after mutation, without creating a new state message.
                Long current=menus.get(chatId);
                if(current!=null && current!=messageId && data.matches("admin:(?:take|close|reject):.*"))telegram.editPage(chatId,messageId,page);
                renderConfirmation(chatId,messageId,page);return;
            }
            if (data.matches("(?:trust|drop|revoke):[0-9a-fA-F-]{36}")) {
                telegram.answer(event.path("id").asText());
                if(messageId<=0) return;
                String id;
                try { id=UUID.fromString(data.substring(data.indexOf(':')+1)).toString(); }
                catch(IllegalArgumentException exception) { return; }
                BotPage page;
                try {
                    if(data.startsWith("drop:")) {
                        // Re-read the numeric owner's list before asking for explicit revocation.
                        var rows=backend.deviceRequest("/api/internal/devices/telegram/"+userId,null);
                        boolean owned=false;
                        for(var row:rows) if(row.path("deviceId").asText().equals(id)) owned=true;
                        if(!owned) throw new IOException("Device unavailable");
                        page=new BotPage("Отозвать доверие этому устройству? Следующий запуск Launcher потребует пароль и Telegram.",List.of(
                            List.of(Map.of("text","Отозвать устройство","callback_data","revoke:"+id)),
                            List.of(BotPage.button("Отмена","devices"))));
                    } else {
                        backend.deviceRequest("/api/internal/devices/"+id+(data.startsWith("trust:") ? "/approve" : "/revoke"),Map.of("telegramUserId",userId));
                        page=BotPage.notice(data.startsWith("trust:") ? "Устройство подтверждено. Вернитесь в Launcher." : "Доверие устройству отозвано.");
                    }
                } catch(IOException exception) { page=BotPage.notice("Действие недоступно или запрос истёк. Проверьте Launcher и обновите список устройств."); }
                renderConfirmation(chatId,messageId,page); return;
            }
            if (data.matches("verify:[A-Za-z0-9_-]{43}")) {
                telegram.answer(event.path("id").asText());
                String username = sender.path("username").isTextual() ? sender.path("username").asText() : null;
                String reply;
                try {
                    int status = backend.accept(data.substring(7), userId, username);
                    checkService(status);
                    reply = status == 200 ? "Регистрация подтверждена. Можно вернуться в Launcher."
                        : status == 409 ? "Подтверждение недоступно или Telegram уже привязан. Проверьте Launcher."
                        : "Подтверждение недействительно или истекло. Снова откройте QR или введите код. Если срок истёк, обновите QR/код в Launcher.";
                } catch (IOException exception) {
                    // Preserve the same sender-bound proof button during Backend downtime.
                    reply = "Подтверждение сейчас недоступно. Попробуйте ещё раз или проверьте Launcher.";
                    renderConfirmation(chatId, messageId, new BotPage(reply, List.of(List.of(
                        Map.of("text", "Повторить подтверждение", "callback_data", data)))));
                    return;
                }
                renderConfirmation(chatId, messageId, BotPage.notice(reply));
                return;
            }
            String action = data.startsWith("menu:v1:") ? data.substring(8) : "";
            if (action.equals("here")) {
                telegram.answer(event.path("id").asText());
                if (messageId <= 0) return;
                freshMenus.put(chatId, true);
                show(chatId, "home", null);
                return;
            }
            boolean known = ACTIONS.contains(action);
            if (known) telegram.answer(event.path("id").asText());
            else telegram.answer(event.path("id").asText(), "Эта кнопка устарела. Открываю главное меню.");
            if (messageId <= 0) return;
            Long current = menus.get(chatId);
            if (current != null && current != messageId) action = "home";
            else menus.put(chatId, messageId);
            show(chatId, known ? action : "home", null);
            return;
        }
        if (message.path("message_id").asLong() > 0) freshMenus.put(chatId, true);
        String text = message.path("text").asText().trim();
        try {
            BotPage draft=broadcasts.message(userId,update.path("update_id").asLong(),message);
            if(draft!=null){showPage(chatId,draft);return;}
        }catch(IOException failure){showPage(chatId,new BotPage("Не удалось подготовить предпросмотр. Проверьте список черновиков: пост мог быть сохранён. Игрокам ничего не отправлено без подтверждения.",List.of(List.of(Map.of("text","📣 Рассылки","callback_data","broadcast:home")))));return;}
        if(text.matches("(?i)/admin(?:@"+Pattern.quote(botUsername)+")?")) {
            if(!reports.admin(userId)){showNotice(chatId,"Нет доступа.");return;}
            try{showPage(chatId,reports.page(userId,"admin:home","command"));}
            catch(IOException failure){showNotice(chatId,"Админ-раздел временно недоступен.");}return;
        }
        if(text.matches("(?i)/start(?:@"+Pattern.quote(botUsername)+")?\\s+d_[A-Za-z0-9_-]{43}")) {
            try {
                var preview=backend.deviceRequest("/api/internal/devices/preview",Map.of("verificationToken",text.split("\\s+",2)[1].substring(2),"telegramUserId",userId));
                String id=UUID.fromString(preview.path("deviceId").asText()).toString();
                String username=preview.path("username").asText(),label=preview.path("label").asText();
                if(!username.matches("[a-z][a-z0-9_]{2,31}") || label.length()>64) throw new IOException("Invalid preview");
                showPage(chatId,new BotPage("RE:CREATED\n\nРазрешить вход в аккаунт "+username+" с устройства «"+label+"»?\nПодтверждайте только свой запрос.",List.of(
                    List.of(Map.of("text","Подтвердить устройство","callback_data","trust:"+id)))));
            } catch(IOException | IllegalArgumentException exception) { showNotice(chatId,"Подтверждение устройства недоступно. Откройте свежий QR в Launcher своего аккаунта."); }
            return;
        }
        var start = StartCommand.parse(text, botUsername);
        String token = start.isPresent() ? start.get().registrationToken() : null;
        String code = null;
        String command = "(?i)/code(?:@" + Pattern.quote(botUsername) + ")?";
        if (text.matches(command)) { show(chatId, "code", null); return; }
        if (text.matches(command + "\\s+.*")) code = text.split("\\s+", 2)[1].trim();
        else if (text.matches("(?i)[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5}-?[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{5}")) code = text;
        if (token == null && code == null) {
            if (text.matches("(?i)/help(?:@" + Pattern.quote(botUsername) + ")?")) show(chatId, "help", null);
            else if (text.matches("(?i)/menu(?:@" + Pattern.quote(botUsername) + ")?")) show(chatId, "home", null);
            else show(chatId, "home", start.isPresent() ? null : "Не понял сообщение. Выберите раздел меню или отправьте /code и код из Launcher.");
            return;
        }
        BackendClient.Preview preview;
        try { preview = backend.prepare(token, code, userId); checkService(preview.status()); }
        catch (IOException exception) { showNotice(chatId, "Подтверждение сейчас недоступно. Попробуйте позже. Пароль и альфа-ключ боту не нужны."); return; }
        if (preview.status() == 200) showPage(chatId,new BotPage("RE:CREATED · Регистрация\n\nАккаунт: "+preview.username()+"\nПодтверждайте только свой запрос. После подтверждения вернитесь в Launcher.",List.of(
            List.of(Map.of("text","Подтвердить регистрацию","callback_data","verify:"+preview.ticket())),
            List.of(BotPage.button("← Главное меню","home")))));
        else showNotice(chatId, preview.status() == 429 ? "Слишком много попыток. Подождите 15 минут и повторите."
            : "Код или ссылка недействительны, истекли или уже использованы. Проверьте Launcher.");
    }
    private void show(long chatId, String action, String notice) throws IOException, InterruptedException {
        BackendClient.Account account;
        try { account = backend.account(chatId); }
        catch (IOException exception) {
            if (Set.of("help", "launcher", "code").contains(action)) {
                showPage(chatId, BotPage.render(action, null, launcherUrl));
            } else {
                showPage(chatId, new BotPage("RE:CREATED\n\nСервис аккаунтов временно недоступен. Попробуйте позже.", List.of(
                    List.of(BotPage.button("↻ Повторить", "home")),
                    List.of(BotPage.button("📥 Launcher", "launcher"), BotPage.button("❓ Помощь", "help")))));
            }
            return;
        }
        BotPage page = BotPage.render(action, account, launcherUrl);
        boolean home=!Set.of("account","server","notifications","help","launcher","code","devices").contains(action)
            || (Set.of("account","server","notifications").contains(action) && (account==null || !account.active()));
        if(home) {
            String summary;
            try{summary=ServerPanel.summary(backend);}catch(IOException unavailable){summary="⚪ Статус сервера временно недоступен";}
            var keys=new ArrayList<>(page.keyboard());
            if(reports.admin(chatId))keys.add(List.of(Map.of("text","🛠 Администрирование","callback_data","admin:home")));
            page=new BotPage(page.text()+"\n\n"+summary,keys);
        }
        if(action.equals("server") && account!=null && account.active()) {
            try{page=ServerPanel.page(backend,null);}catch(IOException unavailable){page=BotPage.notice("🎮 Сервер\n\nДанные временно недоступны. Попробуйте позже.");}
        }
        if(action.equals("devices") && account!=null && account.active()) {
            try {
                var rows=backend.deviceRequest("/api/internal/devices/telegram/"+chatId,null);
                if(!rows.isArray() || rows.size()>20) throw new IOException("Invalid device list");
                List<List<Map<String,String>>> keyboard=new ArrayList<>();
                StringBuilder content=new StringBuilder("💻 Устройства\n\n");
                for(var row:rows) {
                    String id=UUID.fromString(row.path("deviceId").asText()).toString();
                    String label=row.path("label").asText();
                    if(label.length()>64) throw new IOException("Invalid label");
                    content.append(label).append(row.path("state").asText().equals("TRUSTED") ? " — доверенное\n" : " — ожидает подтверждения\n");
                    keyboard.add(List.of(Map.of("text","Отозвать: "+label,"callback_data","drop:"+id)));
                }
                if(rows.isEmpty()) content.append("Доверенных устройств пока нет.");
                keyboard.add(List.of(BotPage.button("← Аккаунт","account")));
                page=new BotPage(content.toString(),keyboard);
            } catch(IOException | IllegalArgumentException exception) { page=BotPage.notice("Список устройств временно недоступен. Попробуйте позже."); }
        }
        if (notice != null) page = new BotPage(notice + "\n\n" + page.text(), page.keyboard());
        showPage(chatId, page);
    }
    private void showNotice(long chatId, String text) throws IOException, InterruptedException { showPage(chatId, BotPage.notice(text)); }
    private void showPage(long chatId, BotPage page) throws IOException, InterruptedException {
        Long id = menus.get(chatId);
        var keyboard = new ArrayList<>(page.keyboard());
        keyboard.add(List.of(BotPage.button("↓ Меню вниз", "here")));
        page = new BotPage(page.text(), keyboard);
        if (id == null || freshMenus.containsKey(chatId) || !telegram.editPage(chatId, id, page)) {
            // Replace the anchor only after Telegram accepts the new message.
            long next = telegram.sendPage(chatId, page);
            menus.put(chatId, next);
            freshMenus.remove(chatId);
            if (id != null && id != next) {
                try { telegram.clearKeyboard(chatId, id); }
                catch (IOException unavailable) { /* Old history must not prevent navigation. */ }
            }
        }
    }
    private void renderConfirmation(long chatId, long id, BotPage page) throws IOException, InterruptedException {
        if(id>0)menus.putIfAbsent(chatId,id);
        showPage(chatId,page);
    }
    private static void checkService(int status) throws IOException {
        if (status >= 500) throw new IOException("Backend temporarily unavailable");
        if (status == 401 || status == 403) throw new IOException("Backend service authentication failed");
    }
}
