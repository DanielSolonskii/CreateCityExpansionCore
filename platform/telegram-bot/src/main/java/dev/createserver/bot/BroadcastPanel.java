package dev.createserver.bot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** Admin UI only; immutable posts, authorization and delivery state live in Backend. */
public final class BroadcastPanel {
    private static final String API="/api/internal/broadcasts";
    private final Set<Long> admins;private final BackendClient backend;private final TelegramClient telegram;
    // Only the next-message composer is ephemeral. A draft itself is durable/idempotent.
    private final Set<Long> composing=new HashSet<>();
    public BroadcastPanel(Set<Long> admins,BackendClient backend,TelegramClient telegram){this.admins=Set.copyOf(admins);this.backend=backend;this.telegram=telegram;}
    public boolean admin(long id){return admins.contains(id);}
    public void leave(long id){composing.remove(id);}
    private static Map<String,String> button(String text,String action){return Map.of("text",text,"callback_data","broadcast:"+action);}
    private BotPage home(long user)throws IOException,InterruptedException{
        var rows=backend.deviceRequest(API+"?telegramUserId="+user,null);
        if(!rows.isArray()||rows.size()>10)throw new IOException("Invalid broadcast list");
        List<List<Map<String,String>>> keys=new ArrayList<>();keys.add(List.of(button("✏️ Новый пост","new")));
        for(var row:rows){String id=UUID.fromString(row.path("id").asText()).toString();keys.add(List.of(button(label(row.path("status").asText())+" · "+id.substring(0,8),"view:"+id)));}
        keys.add(List.of(Map.of("text","← Администрирование","callback_data","admin:home")));
        return new BotPage("📣 Рассылки\n\nПосты для всех привязанных активных игроков. Отправка только после предпросмотра и подтверждения.\n\nПоследние посты:",keys);
    }
    public BotPage page(long user,String data)throws IOException,InterruptedException {
        if(!admin(user))throw new IOException("Admin required");
        composing.remove(user);
        if(data.equals("broadcast:new")){
            composing.add(user);return new BotPage("✏️ Новый пост\n\nОтправьте текст (до 4000 символов) или одну фотографию с подписью (до 1024).\n\nПосле подтверждения пост появится и в новостях Launcher. Для changelog первая строка: #update 1.0.18\nНиже — список изменений. Обычный пост без маркера останется новостью.\n\nСледующий шаг — предпросмотр, не рассылка. Для отмены: /start.",List.of(List.of(button("Отмена","home"))));
        }
        if(data.equals("broadcast:home"))return home(user);
        try {
            String[] parts=data.split(":",3);
            if(parts.length!=3||!Set.of("view","send","cancel").contains(parts[1]))throw new IllegalArgumentException();
            String id=UUID.fromString(parts[2]).toString();
            JsonNode post=parts[1].equals("view")?backend.deviceRequest(API+"/"+id+"?telegramUserId="+user,null)
                :backend.deviceRequest(API+"/"+id+(parts[1].equals("send")?"/publish":"/cancel"),Map.of("telegramUserId",user));
            return detail(post);
        }catch(IllegalArgumentException invalid){return new BotPage("Эта кнопка устарела. Откройте список постов.",List.of(List.of(button("📣 Рассылки","home"))));}
    }
    public BotPage message(long user,long updateId,JsonNode message)throws IOException,InterruptedException {
        if(!admin(user)||!composing.contains(user))return null;
        String text=message.path("text").asText();
        if(text.startsWith("/")){leave(user);return null;}
        String photo=null;
        if(message.has("photo")){
            var photos=message.path("photo");if(!photos.isArray()||photos.isEmpty())return inputError();
            photo=photos.get(photos.size()-1).path("file_id").asText();text=message.path("caption").asText();
            if(!photo.matches("[A-Za-z0-9_-]{1,512}"))return inputError();
        }
        if(message.has("media_group_id"))return new BotPage("Пришлите одну фотографию, не альбом. Рассылка ещё не создана.",List.of(List.of(button("Отмена","home"))));
        if(updateId<=0 || (photo==null&&text.isBlank()) || text.length()>(photo==null?4000:1024) || text.codePoints().anyMatch(c->Character.isISOControl(c)&&c!='\n'&&c!='\t'))return inputError();
        UUID id=UUID.nameUUIDFromBytes(("broadcast-draft-v1:"+user+":"+updateId).getBytes(StandardCharsets.UTF_8));
        Map<String,Object> body=new LinkedHashMap<>();body.put("telegramUserId",user);body.put("commandId",id);body.put("text",text);body.put("photoFileId",photo);
        var post=backend.deviceRequest(API,body);
        // No recipients are contacted here. The photo preview is intentionally separate from the editable menu.
        leave(user);
        telegram.sendPost(user,text,photo);
        return detail(post);
    }
    private BotPage inputError(){return new BotPage("Нужен текст до 4000 символов или одна фотография с подписью до 1024. Попробуйте ещё раз.",List.of(List.of(button("Отмена","home"))));}
    private BotPage detail(JsonNode p){
        String id=UUID.fromString(p.path("id").asText()).toString(),status=p.path("status").asText();
        List<List<Map<String,String>>> keys=new ArrayList<>();String content="📣 Пост · "+id.substring(0,8)+"\n\n"+label(status);
        if(status.equals("DRAFT")){
            String full=p.path("text").asText();String excerpt=full.codePoints().limit(500).collect(StringBuilder::new,StringBuilder::appendCodePoint,StringBuilder::append).toString();
            content+="\n"+(p.path("photoFileId").isTextual()?"🖼 Фото с подписью":"Текстовый пост")+"\n\n"+excerpt+(excerpt.length()<full.length()?"…\nПолный текст в предпросмотре выше.":"")+"\n\nАдресатов сейчас: "+p.path("eligible").asLong()+". Подтвердить отправку всем?";
            // Keep menu under Telegram's 4096-char limit; full long text is in the preview message.
            if(content.length()>3800)content="📣 Пост · "+id.substring(0,8)+"\n\nПредпросмотр отправлен выше. Адресатов сейчас: "+p.path("eligible").asLong()+". Подтвердить отправку всем?";
            keys.add(List.of(button("✅ Отправить всем","send:"+id),button("Отмена","cancel:"+id)));
        }else{
            content+="\n\nВсего: "+p.path("total").asLong()+"\nДоставлено: "+p.path("sent").asLong()+"\nНе доставлено: "+p.path("failed").asLong()+"\nНеопределённый результат: "+p.path("uncertain").asLong()+"\nВ очереди: "+p.path("pending").asLong();
            if(p.path("uncertain").asLong()>0)content+="\nНеопределённые отправки не повторяются автоматически, чтобы не создавать дубли.";
            keys.add(List.of(button("↻ Обновить","view:"+id)));
        }
        keys.add(List.of(button("← Рассылки","home")));return new BotPage(content,keys);
    }
    private static String label(String status){return switch(status){case "DRAFT"->"Черновик";case "QUEUED"->"Отправляется";case "COMPLETED"->"Рассылка завершена";case "CANCELLED"->"Отменено";default->"Недоступно";};}
    /** Dedicated worker; never modifies menu/composer state and never blindly retries a send. */
    public void deliver()throws InterruptedException {
        if(admins.isEmpty())return;long admin=Collections.min(admins);
        try {
            var rows=backend.deviceRequest(API+"/claim",Map.of("telegramUserId",admin));
            if(!rows.isArray()||rows.size()>3)throw new IOException("Invalid delivery batch");
            for(var d:rows){
                UUID post=UUID.fromString(d.path("postId").asText()),token=UUID.fromString(d.path("attemptToken").asText());
                long recipient=d.path("telegramUserId").asLong();if(recipient<=0)throw new IOException("Invalid recipient");
                String photo=d.path("photoFileId").isTextual()?d.path("photoFileId").asText():null;
                String outcome="UNCERTAIN";long messageId=0;int retry=0;
                try {messageId=telegram.sendPost(recipient,d.path("text").asText(),photo);outcome="SENT";}
                catch(TelegramClient.Rejected failure){
                    if(failure.status()==429){outcome="RETRY";retry=failure.retryAfter();}
                    else if(failure.status()>=400&&failure.status()<500)outcome="FAILED";
                }catch(IOException uncertain){/* Durable uncertain result: no automatic resend. */}
                backend.deviceRequest(API+"/"+post+"/ack",Map.of("telegramUserId",admin,"recipientId",recipient,"attemptToken",token,"outcome",outcome,"messageId",messageId,"retryAfter",retry));
                Thread.sleep(500);
            }
        }catch(IOException|IllegalArgumentException failure){System.err.println("Broadcast delivery deferred; check admin delivery report.");}
    }
}
