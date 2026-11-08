package dev.createserver.bot;
import java.io.IOException;
import java.util.*;
public final class TelemetryPanel {
 private TelemetryPanel(){}
 public static BotPage page(BackendClient backend,long admin,String data)throws IOException,InterruptedException{
  String section=data.startsWith("admin:timeline:")?"session":data.substring("admin:telemetry:".length());
  if(!Set.of("online","funnel","problems","retention","session").contains(section))throw new IOException("Invalid telemetry page");
  String query="/api/internal/bridge/analytics?telegramUserId="+admin+"&section="+section;
  if(section.equals("session"))query+="&session="+UUID.fromString(data.substring("admin:timeline:".length()));
  var result=backend.deviceRequest(query,null);var lines=result.path("lines");if(!lines.isArray()||lines.size()>80)throw new IOException("Invalid analytics");
  StringBuilder text=new StringBuilder("📊 ").append(result.path("title").asText()).append("\n\n");
  if(section.equals("online"))text.append(ServerPanel.summary(backend)).append("\n\n");
  for(var line:lines){String value=line.asText();if(text.length()+value.length()>3500)break;text.append(value).append("\n");}
  var keys=new ArrayList<List<Map<String,String>>>();
  if(section.equals("online"))for(var session:result.path("sessions")){if(keys.size()>=6)break;String id=UUID.fromString(session.path("id").asText()).toString();keys.add(List.of(Map.of("text","Сессия · "+session.path("name").asText(),"callback_data","admin:timeline:"+id)));}
  keys.add(List.of(button("Онлайн","online"),button("Вход","funnel")));
  keys.add(List.of(button("Проблемы","problems"),button("Возвращаемость","retention")));
  keys.add(List.of(ServerPanel.adminButton("← Администрирование","home")));
  return new BotPage(text.toString(),keys);
 }
 private static Map<String,String> button(String name,String section){return Map.of("text",name,"callback_data","admin:telemetry:"+section);}
}
