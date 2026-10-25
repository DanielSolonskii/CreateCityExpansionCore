package dev.createserver.backend.bridge;

import dev.createserver.backend.account.*;
import dev.createserver.backend.report.ReportSettings;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Typed launcher diagnostics; existing telemetry_session remains the sole playtime source. */
@Service public class TelemetryV2Service {
 public record Event(UUID id,UUID attemptId,String type,String code,long value,long elapsedMs,long occurredAtMs,String version){}
 public record Batch(List<Event> events){}
 private static final Set<String> TYPES=Set.of("LAUNCH_CLICK","UPDATE_READY","AUTH_READY","PROCESS_START","PROCESS_EXIT","LAUNCH_FAILED","SURVEY_REASON","SURVEY_INTEREST");
 private static final Set<String> CODES=Set.of("OK","FAILED","ABNORMAL","UNKNOWN","GOOD","LAGS","CONFUSING","NO_GOAL","OTHER","BUILD","CITY","INDUSTRY","TRADE","RAIL","SOCIAL");
 private final JdbcTemplate db;private final Clock clock;private final DeviceService devices;private final ReportSettings admins;
 public TelemetryV2Service(JdbcTemplate db,Clock clock,DeviceService devices,ReportSettings admins){this.db=db;this.clock=clock;this.devices=devices;this.admins=admins;}
 private static AccountFailure bad(){return new AccountFailure(400,"INVALID_TELEMETRY");}
 @Transactional public Map<String,Object> receive(UUID device,String secret,Batch batch){
  var owner=devices.authenticate(device,secret);long now=clock.millis();
  if(batch==null||batch.events()==null||batch.events().size()>32)throw bad();
  int recent=db.queryForObject("SELECT COUNT(*) FROM launcher_telemetry_event WHERE device_id=? AND received_at>?",Integer.class,device,Timestamp.from(clock.instant().minusSeconds(60)));
  if(recent>240)throw new AccountFailure(429,"RATE_LIMITED");
  for(var e:batch.events()){
   if(e==null||e.id()==null||e.attemptId()==null||!TYPES.contains(e.type())||!CODES.contains(e.code())||e.elapsedMs()<0||e.elapsedMs()>604800000L||e.occurredAtMs()<now-2592000000L||e.occurredAtMs()>now+300000||e.version()==null||!e.version().matches("[A-Za-z0-9_.-]{1,40}")||e.value()<Integer.MIN_VALUE||e.value()>Integer.MAX_VALUE)throw bad();
   String hash=Secrets.hash(e.toString());var prior=db.query("SELECT platform_user_id,request_hash FROM launcher_telemetry_event WHERE id=?",(r,n)->Map.entry(r.getObject(1,UUID.class),r.getString(2)),e.id());
   if(!prior.isEmpty()){if(!prior.getFirst().equals(Map.entry(owner.platformUserId(),hash)))throw new AccountFailure(409,"TELEMETRY_CONFLICT");continue;}
   db.update("INSERT INTO launcher_telemetry_event(id,platform_user_id,device_id,attempt_id,type,code,numeric_value,elapsed_ms,occurred_at,received_at,version,request_hash) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",e.id(),owner.platformUserId(),device,e.attemptId(),e.type(),e.code(),e.value(),e.elapsedMs(),new Timestamp(e.occurredAtMs()),Timestamp.from(clock.instant()),e.version(),hash);
  }
  return Map.of("accepted",batch.events().size());
 }
 public Map<String,Object> analytics(long admin,String section,UUID session){
  admins.require(admin);List<String> lines=new ArrayList<>();List<Map<String,Object>> links=new ArrayList<>();
  Instant cutoff=clock.instant().minusSeconds(30L*86400);String title;
  switch(section){
   case "online"->{title="Онлайн и сессии";
    var rows=db.queryForList("SELECT s.id,s.minecraft_uuid,s.started_at,s.ended_at,s.duration_seconds,c.username,b.ccec_version FROM telemetry_session s LEFT JOIN account_credentials c ON c.platform_user_id=s.platform_user_id JOIN server_bridge_boot b ON b.boot_id=s.boot_id ORDER BY s.started_at DESC LIMIT 12");
    for(var row:rows){String name=Objects.toString(row.get("username"),"legacy");String state=row.get("ended_at")==null?"нет финального события":"завершена";
     lines.add(name+" · "+((Number)row.get("duration_seconds")).longValue()/60+" мин · "+state+" · "+row.get("ccec_version"));links.add(Map.of("id",row.get("id"),"name",name));}
    lines.add("Текущий онлайн и свежесть — в разделе «Игроки». Отсутствие SESSION_END не доказывает краш.");
   }
   case "funnel"->{title="Запуск → вход в мир · 30 дней";
    for(String kind:List.of("LAUNCH_CLICK","UPDATE_READY","AUTH_READY","PROCESS_START","LAUNCH_FAILED","PROCESS_EXIT")){
     Long n=db.queryForObject("SELECT COUNT(DISTINCT attempt_id) FROM launcher_telemetry_event WHERE type=? AND occurred_at>?",Long.class,kind,Timestamp.from(cutoff));lines.add(kind+": "+n);}
    lines.add("Вход в мир, связанный с попыткой: "+db.queryForObject("SELECT COUNT(DISTINCT e.subject_id) FROM telemetry_event e JOIN launcher_telemetry_event l ON CAST(l.attempt_id AS VARCHAR(36))=e.detail AND l.platform_user_id=e.platform_user_id WHERE e.type='CLIENT_CONTEXT' AND e.occurred_at>?",Long.class,Timestamp.from(cutoff)));
    for(var row:db.queryForList("SELECT type,AVG(elapsed_ms) AS mean_ms FROM launcher_telemetry_event WHERE occurred_at>? AND type IN ('UPDATE_READY','AUTH_READY','PROCESS_START') GROUP BY type",Timestamp.from(cutoff)))lines.add(row.get("type")+" среднее: "+Math.round(((Number)row.get("mean_ms")).doubleValue()/1000)+" с от нажатия");
    lines.add("Клиентские данные и native-выход процесса — разные источники. ABNORMAL ≠ подтверждённый краш.");
   }
   case "problems"->{title="Качество и отказы · 30 дней";
    for(var row:db.queryForList("SELECT type,detail,COUNT(*) AS total FROM telemetry_event WHERE occurred_at>? AND type IN ('PROJECT_ACTION','UI_ACTION','SESSION_REASON') GROUP BY type,detail ORDER BY total DESC LIMIT 14",Timestamp.from(cutoff)))lines.add(row.get("type")+" · "+row.get("detail")+": "+row.get("total"));
    for(var row:db.queryForList("SELECT type,detail FROM telemetry_event WHERE occurred_at>? AND type IN ('CLIENT_QUALITY','MAP_QUALITY','SERVER_QUALITY') ORDER BY occurred_at DESC LIMIT 8",Timestamp.from(cutoff)))lines.add(row.get("type")+" · "+row.get("detail"));
    for(var row:db.queryForList("SELECT version,type,code,COUNT(*) AS total FROM launcher_telemetry_event WHERE occurred_at>? AND type IN ('LAUNCH_FAILED','SURVEY_REASON') GROUP BY version,type,code ORDER BY total DESC LIMIT 8",Timestamp.from(cutoff)))lines.add(row.get("version")+" · "+row.get("type")+" · "+row.get("code")+": "+row.get("total"));
    lines.add("CLIENT_* — измерения клиента. Совпадение ошибки и ухода не доказывает причинность.");
   }
   case "retention"->{title="Возвращаемость и вехи";
    var rows=db.queryForList("SELECT platform_user_id,started_at FROM telemetry_session WHERE platform_user_id IS NOT NULL ORDER BY started_at LIMIT 50001");
    if(rows.size()>50000){lines.add("Выборка слишком велика: показатели возвращаемости временно недоступны.");}
    else{
     Map<UUID,TreeSet<LocalDate>> days=new HashMap<>();
     for(var row:rows){UUID id=(UUID)row.get("platform_user_id");Object raw=row.get("started_at");Instant at=raw instanceof Timestamp t?t.toInstant():((OffsetDateTime)raw).toInstant();days.computeIfAbsent(id,k->new TreeSet<>()).add(at.atZone(ZoneOffset.UTC).toLocalDate());}
     LocalDate today=clock.instant().atZone(ZoneOffset.UTC).toLocalDate();
     for(int day:new int[]{1,7,30}){int eligible=0,returned=0;for(var visits:days.values()){LocalDate target=visits.first().plusDays(day);if(target.isBefore(today)){eligible++;if(visits.contains(target))returned++;}}lines.add("D"+day+": "+returned+" / "+eligible+(eligible==0?" · ещё нет зрелой когорты":" · "+Math.round(returned*100d/eligible)+"%"));}
    }
    for(var row:db.queryForList("SELECT type,COUNT(DISTINCT minecraft_uuid) AS total FROM telemetry_event WHERE occurred_at>? AND type IN ('CITY_CREATED','CITY_JOIN','PROJECT_ACTION','ECONOMY_ACTION','RAILWAY_ACTION','FRIEND_ACCEPTED') GROUP BY type",Timestamp.from(cutoff)))lines.add(row.get("type")+" · игроков за 30 дней: "+row.get("total"));
    for(var row:db.queryForList("SELECT code,COUNT(*) AS total FROM launcher_telemetry_event WHERE type='SURVEY_INTEREST' AND occurred_at>? GROUP BY code",Timestamp.from(cutoff)))lines.add("Хочет продолжить · "+row.get("code")+": "+row.get("total"));
    lines.add("D1/D7/D30 — конкретный UTC-день после первой измеренной игровой сессии. Небольшие выборки не доказывают влияние механики.");
   }
   case "session"->{title="Временная линия сессии";if(session==null)throw bad();
    var records=db.queryForList("SELECT minecraft_uuid,started_at,COALESCE(ended_at,CURRENT_TIMESTAMP) AS until_at FROM telemetry_session WHERE id=?",session);
    if(records.isEmpty())throw new AccountFailure(404,"SESSION_NOT_FOUND");var row=records.getFirst();
    for(var e:db.queryForList("SELECT type,detail,occurred_at FROM telemetry_event WHERE minecraft_uuid=? AND occurred_at>=? AND occurred_at<=? ORDER BY occurred_at LIMIT 24",row.get("minecraft_uuid"),row.get("started_at"),row.get("until_at")))lines.add(e.get("occurred_at")+" · "+e.get("type")+" · "+e.get("detail"));
   }
   default->throw bad();
  }
  if(lines.isEmpty())lines.add("Данных пока нет.");return Map.of("title",title,"lines",lines,"sessions",links,"schema",2);
 }
}
