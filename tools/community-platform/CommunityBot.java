package dev.createserver.bot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.JsonNode;
/** Private-chat interactions; Backend binds each notice to the verified Telegram owner. */
final class CommunityBot {
    private CommunityBot(){}
    private record Pending(UUID notice,long expires){}
    private static final Map<Long,Pending> replies=new LinkedHashMap<>(){protected boolean removeEldestEntry(Map.Entry<Long,Pending> e){return size()>1000;}};
    static boolean handle(JsonNode update,long actor,BackendClient backend,TelegramClient telegram)throws IOException,InterruptedException {
        boolean callback=update.has("callback_query");var event=callback?update.path("callback_query"):update.path("message");String data=event.path("data").asText();
        if(callback&&data.startsWith("community:")){
            telegram.answer(event.path("id").asText());String[] parts=data.split(":");if(parts.length!=3)return true;UUID notice;try{notice=UUID.fromString(parts[2]);}catch(IllegalArgumentException invalid){return true;}
            String action=parts[1];if(action.equals("reply")){replies.put(actor,new Pending(notice,System.currentTimeMillis()+300000));telegram.send(actor,"Напишите ответ одним сообщением (до 500 символов). /cancel — отмена. Права и дружбу проверит игровой сервер.",null);return true;}
            if(!Set.of("accept","decline","mute").contains(action))return true;
            submit(update,actor,notice,action.toUpperCase(Locale.ROOT),"",backend,telegram);return true;
        }
        if(!callback&&replies.containsKey(actor)){
            var pending=replies.get(actor);String text=event.path("text").asText();if(pending.expires()<System.currentTimeMillis()){replies.remove(actor);return false;}
            if(text.equals("/cancel")){replies.remove(actor);telegram.send(actor,"Ответ отменён.",null);return true;}
            if(text.startsWith("/"))return false;
            if(text.isBlank()||text.length()>500){telegram.send(actor,"Нужно текстовое сообщение до 500 символов. /cancel — отмена.",null);return true;}
            submit(update,actor,pending.notice(),"REPLY",text,backend,telegram);replies.remove(actor);return true;
        }return false;
    }
    private static void submit(JsonNode update,long actor,UUID notice,String action,String text,BackendClient backend,TelegramClient telegram)throws InterruptedException,IOException {
        UUID request=UUID.nameUUIDFromBytes(("community:"+actor+":"+update.path("update_id").asLong()+":"+notice).getBytes(StandardCharsets.UTF_8));
        try{var result=backend.deviceRequest("/api/internal/community/action",Map.of("telegramUserId",actor,"notice",notice,"action",action,"text",text,"request",request));telegram.send(actor,"QUEUED".equals(result.path("status").asText())?"Команда передана игровому серверу. Результат придёт отдельным уведомлением.":"Состояние команды: "+result.path("status").asText(),null);}
        catch(IOException unavailable){telegram.send(actor,"Действие недоступно, устарело или аккаунт не привязан. Откройте «Сообщество» в игре и проверьте состояние.",null);}
    }
    static void deliver(BackendClient backend,TelegramClient telegram)throws InterruptedException {
        try{var rows=backend.deviceRequest("/api/internal/community/claim",Map.of());if(!rows.isArray()||rows.size()>3)throw new IOException();
            for(var n:rows){UUID id=UUID.fromString(n.path("id").asText()),token=UUID.fromString(n.path("attemptToken").asText());long actor=n.path("telegramUserId").asLong();String kind=n.path("kind").asText(),peer=n.path("peer").asText(),body=n.path("text").asText();if(actor<=0||body.length()>500||peer.length()>64)throw new IOException();
                var buttons=new ArrayList<List<Map<String,String>>>();String title;
                if(kind.equals("MESSAGE")){title="RE:CREATED · Сообщение от "+peer;buttons.add(List.of(Map.of("text","Ответить","callback_data","community:reply:"+id),Map.of("text","Отключить диалог","callback_data","community:mute:"+id)));}
                else if(kind.equals("INVITE")){title="RE:CREATED · Приглашение в город от "+peer;if(n.path("invitation").asBoolean())buttons.add(List.of(Map.of("text","Принять","callback_data","community:accept:"+id),Map.of("text","Отклонить","callback_data","community:decline:"+id)));}
                else title="RE:CREATED · Результат действия";
                String outcome="UNCERTAIN";try{telegram.sendPage(actor,new BotPage(title+(body.isBlank()?"\nПодробности доступны в «Сообществе» в игре.":"\n"+body),buttons));outcome="SENT";}catch(TelegramClient.Rejected failure){outcome="FAILED";}catch(IOException uncertain){}
                backend.deviceRequest("/api/internal/community/ack",Map.of("id",id,"attemptToken",token,"outcome",outcome));
            }
        }catch(IOException|IllegalArgumentException unavailable){System.err.println("Community notifications deferred.");}
    }
}
