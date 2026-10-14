package dev.createserver.bot;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** UI only. Backend reauthorizes every request and owns reports/actions/outbox. */
public final class ReportAdminPanel {
    private final Set<Long> admins; private final BackendClient backend;private final TelegramClient telegram;
    public ReportAdminPanel(Set<Long> admins,BackendClient backend,TelegramClient telegram){this.admins=Set.copyOf(admins);this.backend=backend;this.telegram=telegram;}
    public boolean admin(long id){return admins.contains(id);}
    public static Set<Long> configured(Map<String,String> env){
        String value=env.getOrDefault("REPORT_ADMIN_TELEGRAM_IDS","");if(value.isBlank())return Set.of();Set<Long> ids=new TreeSet<>();
        try{for(String id:value.split(",",-1)){if(!id.trim().matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException();ids.add(Long.parseLong(id.trim()));}if(ids.size()>20)throw new IllegalArgumentException();}
        catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid REPORT_ADMIN_TELEGRAM_IDS");}return Set.copyOf(ids);
    }
    public BotPage page(long user,String data,String callbackId)throws IOException,InterruptedException{
        if(!admin(user))throw new IOException("Admin required");
        if(data.equals("admin:home")) {
            String count;
            try{var summary=backend.deviceRequest("/api/internal/reports/summary?telegramUserId="+user,null);
                if(!summary.path("openReports").isIntegralNumber() || summary.path("openReports").asLong()<0)throw new IOException("Invalid report count");
                count=Long.toString(summary.path("openReports").asLong());
            }catch(IOException unavailable){count="временно недоступно";}
            String status;
            try{status=ServerPanel.summary(backend);}catch(IOException unavailable){status="⚪ Статус сервера временно недоступен";}
            return new BotPage("🛠 Администрирование\n\n"+status+"\nОткрытых репортов: "+count,List.of(
                List.of(list("🚨 Репорты",0)),
                List.of(ServerPanel.adminButton("👥 Игроки","players"),ServerPanel.adminButton("📊 Статистика","stats")),
                List.of(ServerPanel.adminButton("↻ Обновить","home"),BotPage.button("← Главное меню","home"))));
        }
        if(data.equals("admin:stats"))return ServerPanel.page(backend,user);
        if(data.equals("admin:players"))return ServerPanel.players(backend,user);
        try{
            if(data.matches("admin:list:[0-9]{1,5}")){
                int offset=Integer.parseInt(data.substring(11));if(offset>10000)throw new IllegalArgumentException();
                var rows=backend.deviceRequest("/api/internal/reports?telegramUserId="+user+"&offset="+offset,null);
                if(!rows.isArray() || rows.size()>10)throw new IOException("Invalid report list");
                List<List<Map<String,String>>> keys=new ArrayList<>();
                for(var row:rows){String id=uuid(row.path("id"));keys.add(List.of(button(row.path("username").asText()+" · "+row.path("status").asText(),"report",id)));}
                if(offset>0)keys.add(List.of(list("← Назад",Math.max(0,offset-10))));
                if(rows.size()==10 && offset<10000)keys.add(List.of(list("Далее →",offset+10)));
                keys.add(List.of(ServerPanel.adminButton("← Администрирование","home")));
                return new BotPage("🚨 Репорты\n\n"+(rows.isEmpty()?"Открытых репортов нет.":"Выберите репорт · страница "+(offset/10+1)),keys);
            }
            String[] parts=data.split(":",3);if(parts.length!=3 || !parts[0].equals("admin"))throw new IllegalArgumentException();String id=UUID.fromString(parts[2]).toString();
            if(parts[1].equals("player")){
                var p=backend.deviceRequest("/api/internal/reports/"+id+"/player?telegramUserId="+user,null);
                return new BotPage("👤 Игрок\n\nНик: "+p.path("username").asText()+"\nСтатус: "+p.path("status").asText()+"\nPlatform UUID: "+uuid(p.path("platformUserId"))+"\nMinecraft UUID: "+uuid(p.path("minecraftUuid"))+"\nTelegram: "+(p.path("telegramBound").asBoolean()?"привязан":"не привязан"),List.of(List.of(button("← Репорт","report",id))));
            }
            JsonNode report;
            if(Set.of("take","close","reject").contains(parts[1])){
                UUID command=UUID.nameUUIDFromBytes(("report-action-v1:"+callbackId).getBytes(StandardCharsets.UTF_8));
                String action=switch(parts[1]){case "take"->"TAKE";case "close"->"CLOSE";default->"REJECT";};
                report=backend.deviceRequest("/api/internal/reports/"+id+"/actions",Map.of("commandId",command,"telegramUserId",user,"action",action));
            }else if(parts[1].equals("report"))report=backend.deviceRequest("/api/internal/reports/"+id+"?telegramUserId="+user,null);
            else throw new IllegalArgumentException();
            return detail(report);
        }catch(IllegalArgumentException invalid){return new BotPage("Кнопка устарела. Обновите список репортов.",List.of(List.of(list("↻ Репорты",0))));}
    }
    static BotPage detail(JsonNode r)throws IOException{
        String id=uuid(r.path("id"));String text=r.path("text").asText();if(text.length()>1000)throw new IOException("Invalid report");
        var keys=new ArrayList<List<Map<String,String>>>();
        if(Set.of("OPEN","IN_PROGRESS").contains(r.path("status").asText())){
            if(r.path("status").asText().equals("OPEN"))keys.add(List.of(button("Взять","take",id)));
            keys.add(List.of(button("Закрыть","close",id),button("Отклонить","reject",id)));
        }
        keys.add(List.of(button("Игрок","player",id),list("← Репорты",0)));
        String state=switch(r.path("status").asText()){case "OPEN"->"Открыт";case "IN_PROGRESS"->"В работе";case "CLOSED"->"Закрыт";case "REJECTED"->"Отклонён";default->"Обновите репорт";};
        String time=r.path("createdAt").asText();
        try{time=java.time.format.DateTimeFormatter.ofPattern("dd.MM HH:mm 'UTC'").withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.parse(time));}catch(java.time.DateTimeException ignored){}
        return new BotPage("🚨 Репорт #"+id.substring(0,8)+"\n\n"+r.path("username").asText()+" · "+state+"\n"+time+"\n"+r.path("dimension").asText()+" · "+r.path("x").asText()+", "+r.path("y").asText()+", "+r.path("z").asText()+"\n\n"+text,keys);
    }
    public void notifyPending()throws InterruptedException{
        for(long admin:admins){
            try{
                var deliveries=backend.deviceRequest("/api/internal/reports/notifications/claim",Map.of("telegramUserId",admin));
                if(!deliveries.isArray() || deliveries.size()>5)throw new IOException("Invalid notifications");
                for(var d:deliveries){String id=uuid(d.path("reportId")),token=uuid(d.path("leaseToken"));
                    telegram.sendPage(admin,detail(d.path("report")));
                    backend.deviceRequest("/api/internal/reports/notifications/"+id+"/ack",Map.of("telegramUserId",admin,"leaseToken",token));
                }
            }catch(IOException | RuntimeException failure){System.err.println("Report notification deferred; retrying later.");}
        }
    }
    private static String uuid(JsonNode n)throws IOException{try{return UUID.fromString(n.asText()).toString();}catch(RuntimeException e){throw new IOException("Invalid report identity");}}
    private static Map<String,String> button(String text,String action,String id){return Map.of("text",text,"callback_data","admin:"+action+":"+id);}
    private static Map<String,String> list(String text,int offset){return Map.of("text",text,"callback_data","admin:list:"+offset);}
}
