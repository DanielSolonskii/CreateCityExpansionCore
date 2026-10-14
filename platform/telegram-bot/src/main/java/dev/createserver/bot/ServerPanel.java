package dev.createserver.bot;
import java.io.IOException;
import tools.jackson.databind.JsonNode;
import java.util.*;
public final class ServerPanel {
    private ServerPanel(){}
    public static String summary(BackendClient backend)throws IOException,InterruptedException {
        var status=backend.deviceRequest("/api/internal/bridge/status",null);
        return switch(status.path("state").asText()) {
            case "ONLINE" -> "🟢 Сервер онлайн · "+status.path("playerCount").asInt()+" / "+status.path("maxPlayers").asInt()+" игроков";
            case "OFFLINE" -> "⚫ Сервер остановлен";
            default -> "⚪ Нет свежих данных о сервере";
        };
    }
    static Map<String,String> adminButton(String text,String action){return Map.of("text",text,"callback_data","admin:"+action);}
    public static BotPage players(BackendClient backend,long admin)throws IOException,InterruptedException {
        var data=backend.deviceRequest("/api/internal/bridge/admin?telegramUserId="+admin,null);
        var rows=data.path("players");if(!rows.isArray()||rows.size()>100)throw new IOException("Invalid bridge players");
        String text="👥 Игроки RE:CREATED\n\n";
        if(!data.path("status").path("state").asText().equals("ONLINE"))text+="Нет свежего online-списка.";
        else if(rows.isEmpty())text+="Сейчас игроков нет.";
        else {int shown=0;for(var row:rows){if(shown++==30){text+="…";break;}String name=row.path("username").asText();if(name.length()>32)throw new IOException("Invalid bridge player");text+="• "+name+"\n";}}
        return new BotPage(text,List.of(List.of(adminButton("↻ Обновить","players"),adminButton("📊 Статистика","stats")),List.of(adminButton("← Администрирование","home"))));
    }
    public static BotPage page(BackendClient backend,Long admin)throws IOException,InterruptedException {
        JsonNode data=backend.deviceRequest(admin==null?"/api/internal/bridge/status":"/api/internal/bridge/admin?telegramUserId="+admin,null);
        JsonNode status=admin==null?data:data.path("status");
        String state=status.path("state").asText();
        String text="🎮 Сервер RE:CREATED\n\n"+switch(state){case "ONLINE"->"🟢 Онлайн";case "OFFLINE"->"⚫ Остановлен";default->"⚪ Нет свежих данных";};
        if(state.equals("ONLINE"))text+="\nИгроков: "+status.path("playerCount").asInt()+" / "+status.path("maxPlayers").asInt()+"\nUptime: "+status.path("uptimeSeconds").asLong()/60+" мин";
        if(!status.path("minecraftVersion").asText().isBlank())text+="\nMinecraft: "+status.path("minecraftVersion").asText()+"\nCCEC: "+status.path("ccecVersion").asText()+"\nСервер: "+status.path("serverVersion").asText();
        if(admin!=null){
            text+="\n\n📊 События за 24 ч: "+data.path("events24h").asLong()+"\nСессии: "+data.path("sessions").asLong()+"\nУчтено игрового времени: "+data.path("playtimeSeconds").asLong()/60+" мин\nПропущено событий в текущем boot: "+status.path("droppedEvents").asLong()+"\n\nТекущие игроки:";
            var players=data.path("players");if(!players.isArray()||players.size()>100)throw new IOException("Invalid bridge players");
            if(players.isEmpty())text+="\n—";
            int shown=0;for(var player:players){if(shown++==30){text+="\n…";break;}String name=player.path("username").asText();if(name.length()>32)throw new IOException("Invalid bridge player");text+="\n• "+name;}
        }
        return new BotPage(text,admin==null ? List.of(List.of(BotPage.button("↻ Обновить","server")),List.of(BotPage.button("← Главное меню","home")))
            : List.of(List.of(adminButton("↻ Обновить","stats")),List.of(adminButton("← Администрирование","home"))));
    }
}
