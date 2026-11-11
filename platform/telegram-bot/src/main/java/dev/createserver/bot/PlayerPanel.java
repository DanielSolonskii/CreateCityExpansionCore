package dev.createserver.bot;

import java.io.IOException;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import tools.jackson.databind.JsonNode;

/** Fresh owner data only. Callback values carry desired preferences, never owner IDs. */
final class PlayerPanel {
    private static final DateTimeFormatter TIME=DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm 'UTC+3'").withZone(ZoneOffset.ofHours(3));
    private PlayerPanel(){}
    static boolean handles(String action){
        return Set.of("account","cities","preferences","notifications","reports").contains(action)
            ||action.matches("(?:cities|reports):[0-9]{1,3}")||action.matches("pref:(?:news|maintenance|reports):(?:on|off)");
    }
    static BotPage page(BackendClient backend,long actor,String action)throws IOException,InterruptedException {
        String api="/api/internal/telegram/companion/"+actor;
        if(action.equals("preferences")||action.equals("notifications")||action.startsWith("pref:")){
            JsonNode value;
            if(action.startsWith("pref:")){
                String[] parts=action.split(":");
                value=backend.deviceRequest(api+"/preferences",Map.of("field",parts[1],"enabled",parts[2].equals("on")));
            }else value=backend.deviceRequest(api+"/preferences",null);
            return preferences(value);
        }
        var profile=backend.deviceRequest(api,null);
        var account=profile.path("account");
        if(!account.isObject())throw invalid();
        if(action.startsWith("cities"))return cities(profile,account,offset(action));
        if(action.startsWith("reports"))return reports(account,offset(action));
        return profile(profile,account);
    }
    private static BotPage profile(JsonNode data,JsonNode account)throws IOException {
        String name=text(account,"username",32),about=text(account,"about",180);
        String state=text(data,"onlineState",10);
        if(!Set.of("ONLINE","OFFLINE","UNKNOWN").contains(state))throw invalid();
        boolean linked=bool(data,"minecraftLinked");
        String online=!linked?"Ещё нет игрового профиля":switch(state){case "ONLINE"->"🟢 В игре";case "OFFLINE"->"⚪ Не в игре";default->"🟡 Нет свежих данных";};
        var reports=array(account,"reports",30);var devices=array(account,"devices",20);
        long open=0,trusted=0;
        for(var report:reports)if(Set.of("OPEN","IN_PROGRESS").contains(report.path("status").asText()))open++;
        for(var device:devices)if(device.path("state").asText().equals("TRUSTED"))trusted++;
        StringBuilder text=new StringBuilder("👤 Профиль игрока\n\nИмя: ").append(name)
            .append("\nСтатус: Активен\nTelegram: привязан\n\n").append(online)
            .append("\nПоследняя сессия: ").append(time(account,"lastSeen"));
        var play=data.path("playtimeSeconds");
        if(!play.isNull()&&!play.isMissingNode()){
            if(!play.isIntegralNumber()||play.asLong()<0)throw invalid();
            long minutes=play.asLong()/60;
            text.append("\nВремя в игре: ").append(minutes/60).append(" ч ").append(minutes%60).append(" мин")
                .append("\nСнимок статистики: ").append(time(data,"statsObservedAt"));
        }else text.append("\nИгровая статистика ещё не получена.");
        if(!about.isBlank())text.append("\n\nО себе\n").append(about).append("\nОбновлено: ").append(time(account,"profileObservedAt"));
        text.append("\n\nДоверенных устройств: ").append(trusted).append("\nОткрытых обращений: ").append(open)
            .append("\n(среди последних ").append(reports.size()).append(")");
        return new BotPage(text.toString(),List.of(
            List.of(BotPage.button("🏙 Мои города","cities"),BotPage.button("📬 Обращения","reports")),
            List.of(BotPage.button("💻 Устройства","devices"),BotPage.button("🔔 Уведомления","preferences")),
            List.of(BotPage.button("↻ Обновить","account"),BotPage.button("← Главное меню","home"))));
    }
    private static BotPage cities(JsonNode profile,JsonNode account,int requested)throws IOException {
        var rows=array(account,"cities",1000);String state=text(account,"cityState",10);
        if(!Set.of("CURRENT","STALE","UNKNOWN").contains(state))throw invalid();
        StringBuilder content=new StringBuilder("🏙 Мои города\n\n");
        boolean linked=bool(profile,"minecraftLinked");
        if(!linked)content.append("Игровой профиль ещё не связан. Войдите на сервер через Launcher.");
        else if(state.equals("UNKNOWN"))content.append("Данные о городах ещё не получены. Зайдите в игру и обновите страницу позже.");
        else {
            if(!state.equals("CURRENT"))content.append("🟡 Снимок устарел. Состав и роли могли измениться.\n");
            content.append("Обновлено: ").append(time(account,"cityObservedAt")).append("\n");
            if(rows.isEmpty())content.append(state.equals("CURRENT")?"\nВы пока не состоите в городе. Создать город или вступить в него можно в игре.":"\nВ последнем снимке городов нет. Обновите данные после входа на сервер.");
        }
        int start=pageStart(requested,rows.size());
        if(linked&&!state.equals("UNKNOWN"))for(int i=start;i<Math.min(start+5,rows.size());i++){
            var city=rows.get(i);String name=text(city,"name",128),role=text(city,"role",32);
            var members=array(city,"members",1000);
            content.append("\n\n").append(i+1).append(". ").append(name).append("\nВаша роль: ").append(role(role))
                .append("\nУровень: ").append(number(city,"level")).append(" · Участков: ").append(number(city,"plots"))
                .append("\nБлоков территории: ").append(number(city,"blocks")).append("\nЖителей: ").append(members.size());
            for(int j=0;j<Math.min(5,members.size());j++){
                if(!members.get(j).isTextual()||members.get(j).asText().length()>32)throw invalid();
                content.append(j==0?"\n":" · ").append(members.get(j).asText());
            }
            if(members.size()>5)content.append(" · ещё ").append(members.size()-5);
        }
        return paged(content.toString(),"cities",start,linked&&!state.equals("UNKNOWN")?rows.size():0);
    }
    private static BotPage reports(JsonNode account,int requested)throws IOException {
        var rows=array(account,"reports",30);int start=pageStart(requested,rows.size());
        StringBuilder text=new StringBuilder("📬 Мои обращения\n\nПоследние обращения, отправленные из игры.");
        if(rows.isEmpty())text.append("\n\nОбращений пока нет.");
        for(int i=start;i<Math.min(start+5,rows.size());i++){
            var row=rows.get(i);String body=text(row,"text",1000);
            text.append("\n\n").append(i+1).append(". ").append(reportState(text(row,"status",20)))
                .append(" · ").append(time(row,"createdAt")).append("\n").append(body.length()>180?body.substring(0,180)+"…":body);
        }
        return paged(text.toString(),"reports",start,rows.size());
    }
    private static BotPage preferences(JsonNode data)throws IOException {
        boolean news=bool(data,"news"),maintenance=bool(data,"maintenance"),reports=bool(data,"reports");
        return new BotPage("🔔 Настройки уведомлений\n\nВыберите, что получать в этом чате. Настройки общие с Mini App.\n\n"+
            flag(news)+" Новости и обновления\n"+flag(maintenance)+" Технические работы\n"+flag(reports)+" Ответы по обращениям\n\n"+
            "Изменения действуют для новых уведомлений. Уже поставленные в очередь могут ещё прийти. Подтверждения входа и сообщения сообщества управляются отдельно.",List.of(
                List.of(preference("Новости", "news",news)),List.of(preference("Технические работы","maintenance",maintenance)),
                List.of(preference("Ответы по обращениям","reports",reports)),
                List.of(BotPage.button("↻ Обновить","preferences"),BotPage.button("← Главное меню","home"))));
    }
    private static Map<String,String> preference(String label,String field,boolean enabled){
        return BotPage.button((enabled?"Выключить: ":"Включить: ")+label,"pref:"+field+":"+(enabled?"off":"on"));
    }
    private static String flag(boolean enabled){return enabled?"✅":"➖";}
    private static BotPage paged(String text,String action,int start,int total){
        var keyboard=new ArrayList<List<Map<String,String>>>();var pages=new ArrayList<Map<String,String>>();
        if(start>0)pages.add(BotPage.button("← Назад",action+":"+(start-5)));
        if(start+5<total)pages.add(BotPage.button("Далее →",action+":"+(start+5)));
        if(!pages.isEmpty())keyboard.add(pages);
        keyboard.add(List.of(BotPage.button("↻ Обновить",action+":"+start),BotPage.button("← Профиль","account")));
        keyboard.add(List.of(BotPage.button("Главное меню","home")));
        return new BotPage(text,keyboard);
    }
    private static int offset(String action){return action.contains(":")?Integer.parseInt(action.split(":")[1]):0;}
    private static int pageStart(int offset,int size){return size==0?0:Math.min(offset/5*5,(size-1)/5*5);}
    private static String role(String value){return switch(value){case "OWNER"->"Глава города";case "ADMIN"->"Администратор";case "BUILDER"->"Строитель";case "RESIDENT"->"Житель";case "ENGINEER"->"Инженер";case "TREASURER"->"Казначей";default->"Неизвестная роль";};}
    private static String reportState(String value){return switch(value){case "OPEN"->"Получено";case "IN_PROGRESS"->"В работе";case "CLOSED"->"Закрыто";case "REJECTED"->"Отклонено";default->"Статус недоступен";};}
    private static String time(JsonNode object,String field)throws IOException {
        var value=object.path(field);if(value.isNull()||value.isMissingNode())return "нет данных";
        try{return TIME.format(Instant.parse(value.asText()));}catch(DateTimeException invalid){throw invalid();}
    }
    private static String text(JsonNode object,String field,int max)throws IOException {
        var value=object.path(field);if(!value.isTextual()||value.asText().length()>max)throw invalid();return value.asText();
    }
    private static long number(JsonNode object,String field)throws IOException {
        var value=object.path(field);if(!value.isIntegralNumber()||value.asLong()<0)throw invalid();return value.asLong();
    }
    private static boolean bool(JsonNode object,String field)throws IOException {
        if(!object.path(field).isBoolean())throw invalid();return object.path(field).asBoolean();
    }
    private static JsonNode array(JsonNode object,String field,int max)throws IOException {
        var value=object.path(field);if(!value.isArray()||value.size()>max)throw invalid();return value;
    }
    private static IOException invalid(){return new IOException("Player data unavailable");}
}
