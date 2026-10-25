package dev.createserver.backend.miniapp;

import dev.createserver.backend.account.*;
import dev.createserver.backend.bridge.BridgeService;
import dev.createserver.backend.game.GameSettings;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class CompanionService {
    public record Preferences(boolean news,boolean maintenance,boolean reports){}
    public record Member(UUID minecraftUuid,String role){}
    public record City(UUID id,String name,int level,int plots,long blocks,List<Member> members){}
    public record About(UUID minecraftUuid,String about,Long playTicks,String skinUrl){
        public About(UUID minecraftUuid,String about){this(minecraftUuid,about,null,null);}
    }
    public record Snapshot(String serverId,UUID bootId,Instant observedAt,List<City> cities,List<About> profiles){}
    public record CityView(String name,String role,int level,int plots,long blocks,List<String> members){}
    public record ReportView(UUID id,String text,String status,Instant createdAt,Instant updatedAt){}
    public record Account(String username,boolean online,Instant lastSeen,String about,Instant profileObservedAt,
        String cityState,Instant cityObservedAt,List<CityView> cities,List<ReportView> reports,List<DeviceService.DeviceView> devices,Preferences preferences){}
    private final JdbcTemplate db;private final DeviceService devices;private final GameSettings game;
    private final Clock clock;private final BridgeService bridge;private final JsonMapper json=JsonMapper.builder().build();
    public CompanionService(JdbcTemplate db,DeviceService devices,GameSettings game,Clock clock,BridgeService bridge){this.db=db;this.devices=devices;this.game=game;this.clock=clock;this.bridge=bridge;}
    private Timestamp ts(Instant i){return Timestamp.from(i);}
    UUID owner(long telegram){
        var ids=db.query("SELECT u.id FROM telegram_identity t JOIN platform_user u ON u.id=t.platform_user_id WHERE t.telegram_user_id=? AND u.status='ACTIVE'",(r,n)->r.getObject(1,UUID.class),telegram);
        if(ids.isEmpty())throw new AccountFailure(403,"ACCOUNT_UNAVAILABLE");return ids.getFirst();
    }
    private Preferences prefs(UUID id){var rows=db.query("SELECT news,maintenance,reports FROM companion_preferences WHERE platform_user_id=?",(r,n)->new Preferences(r.getBoolean(1),r.getBoolean(2),r.getBoolean(3)),id);return rows.isEmpty()?new Preferences(true,true,true):rows.getFirst();}
    @Transactional public Preferences preferences(long telegram,Preferences value){
        UUID id=owner(telegram);db.queryForObject("SELECT id FROM platform_user WHERE id=? FOR UPDATE",UUID.class,id);
        if(value==null)throw new AccountFailure(400,"INVALID_PREFERENCES");
        db.update("DELETE FROM companion_preferences WHERE platform_user_id=?",id);
        db.update("INSERT INTO companion_preferences(platform_user_id,news,maintenance,reports,updated_at) VALUES(?,?,?,?,?)",id,value.news(),value.maintenance(),value.reports(),ts(clock.instant()));return value;
    }
    @Transactional public void revoke(long telegram,UUID device){owner(telegram);devices.revokeByTelegram(device,telegram);}
    @Transactional(readOnly=true) public Account account(long telegram){
        UUID id=owner(telegram);
        String username=db.queryForObject("SELECT username FROM account_credentials WHERE platform_user_id=?",String.class,id);
        var identities=db.query("SELECT minecraft_uuid FROM minecraft_identity WHERE platform_user_id=?",(r,n)->r.getObject(1,UUID.class),id);
        UUID mc=identities.isEmpty()?null:identities.getFirst();
        var last=db.query("SELECT COALESCE(ended_at,started_at) FROM telemetry_session WHERE platform_user_id=? ORDER BY COALESCE(ended_at,started_at) DESC LIMIT 1",(r,n)->r.getTimestamp(1).toInstant(),id);
        boolean online=false;
        if(mc!=null&&bridge.status().state().equals("ONLINE")){
            var rows=db.query("SELECT players FROM server_bridge_boot WHERE server_id=? ORDER BY started_at DESC,boot_id DESC LIMIT 1",(r,n)->r.getString(1),game.serverId());
            online=!rows.isEmpty()&&Arrays.asList(rows.getFirst().split(",")).contains(mc.toString());
        }
        var snapshots=db.query("SELECT payload FROM companion_snapshot WHERE server_id=?",(r,n)->r.getString(1),game.serverId());
        Snapshot s=snapshots.isEmpty()?null:json.readValue(snapshots.getFirst(),Snapshot.class);
        String state="UNKNOWN";Instant observed=s==null?null:s.observedAt();List<CityView> cities=new ArrayList<>();
        if(s!=null){
            var latest=db.query("SELECT boot_id FROM server_bridge_boot WHERE server_id=? ORDER BY started_at DESC,boot_id DESC LIMIT 1",(r,n)->r.getObject(1,UUID.class),game.serverId());
            boolean fresh=!s.observedAt().isBefore(clock.instant().minusSeconds(120))&&!latest.isEmpty()&&bridge.status().state().equals("ONLINE");
            state=fresh?"CURRENT":"STALE";
            for(City c:s.cities())for(Member m:c.members())if(m.minecraftUuid().equals(mc)){
                List<String> names=new ArrayList<>();for(Member member:c.members()){
                    var mapped=db.query("SELECT a.username FROM minecraft_identity i JOIN account_credentials a ON a.platform_user_id=i.platform_user_id WHERE i.minecraft_uuid=?",(r,n)->r.getString(1),member.minecraftUuid());
                    names.add(mapped.isEmpty()?"Игрок":mapped.getFirst());
                }
                cities.add(new CityView(c.name(),m.role(),c.level(),c.plots(),c.blocks(),names));
            }
        }
        var about=mc==null?List.<AboutRow>of():db.query("SELECT about,observed_at FROM companion_profile WHERE minecraft_uuid=?",(r,n)->new AboutRow(r.getString(1),r.getTimestamp(2).toInstant()),mc);
        var reports=db.query("SELECT id,text,status,created_at,updated_at FROM player_report WHERE platform_user_id=? ORDER BY created_at DESC,id DESC LIMIT 30",(r,n)->new ReportView(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getTimestamp(4).toInstant(),r.getTimestamp(5).toInstant()),id);
        return new Account(username,online,last.isEmpty()?null:last.getFirst(),about.isEmpty()?"":about.getFirst().text(),about.isEmpty()?null:about.getFirst().time(),state,observed,cities,reports,devices.list(telegram),prefs(id));
    }
    private record AboutRow(String text,Instant time){}
    @Transactional public Map<String,Boolean> receive(Snapshot s){
        Instant now=clock.instant();
        if(s==null||!game.serverId().equals(s.serverId())||s.bootId()==null||s.observedAt()==null||s.observedAt().isBefore(now.minusSeconds(120))||s.observedAt().isAfter(now.plusSeconds(30))||s.cities()==null||s.cities().size()>1000||s.profiles()==null||s.profiles().size()>1000)throw new AccountFailure(400,"INVALID_COMPANION_SNAPSHOT");
        Set<UUID> ids=new HashSet<>();
        for(City c:s.cities()){
            if(c==null||c.id()==null||!ids.add(c.id())||c.name()==null||c.name().isBlank()||c.name().length()>128||c.name().chars().anyMatch(Character::isISOControl)||c.level()<0||c.plots()<0||c.blocks()<0||c.members()==null||c.members().size()>1000)throw new AccountFailure(400,"INVALID_COMPANION_SNAPSHOT");
            Set<UUID> members=new HashSet<>();for(Member m:c.members())if(m==null||m.minecraftUuid()==null||!members.add(m.minecraftUuid())||!Set.of("OWNER","ADMIN","BUILDER","RESIDENT","ENGINEER","TREASURER").contains(Objects.toString(m.role(),"")))throw new AccountFailure(400,"INVALID_COMPANION_SNAPSHOT");
        }
        for(About p:s.profiles())if(p!=null&&(p.playTicks()!=null&&(p.playTicks()<0||p.playTicks()>Integer.MAX_VALUE)||p.skinUrl()!=null&&!p.skinUrl().isEmpty()&&!p.skinUrl().matches("https://textures\\.minecraft\\.net/texture/[0-9a-f]{32,64}")))throw new AccountFailure(400,"INVALID_PROFILE_PRESENTATION");
        Set<UUID> profiles=new HashSet<>();for(About p:s.profiles())if(p==null||p.minecraftUuid()==null||!profiles.add(p.minecraftUuid())||p.about()==null||p.about().length()>180||p.about().chars().anyMatch(Character::isISOControl))throw new AccountFailure(400,"INVALID_COMPANION_SNAPSHOT");
        // Monotonic observation time prevents delayed requests from overwriting newer state.
        db.queryForObject("SELECT id FROM broadcast_lock WHERE id=1 FOR UPDATE",Integer.class);
        var old=db.query("SELECT observed_at FROM companion_snapshot WHERE server_id=?",(r,n)->r.getTimestamp(1).toInstant(),s.serverId());
        if(!old.isEmpty()&&!s.observedAt().isAfter(old.getFirst()))return Map.of("accepted",true);
        db.update("DELETE FROM companion_snapshot WHERE server_id=?",s.serverId());
        db.update("INSERT INTO companion_snapshot(server_id,boot_id,observed_at,payload) VALUES(?,?,?,?)",s.serverId(),s.bootId(),ts(s.observedAt()),json.writeValueAsString(s));
        for(About p:s.profiles()){
            int updated=db.update("UPDATE companion_profile SET about=?,observed_at=?,play_ticks=COALESCE(?,play_ticks),skin_url=COALESCE(?,skin_url),stats_observed_at=COALESCE(?,stats_observed_at) WHERE minecraft_uuid=?",p.about(),ts(s.observedAt()),p.playTicks(),p.skinUrl(),p.playTicks()==null?null:ts(s.observedAt()),p.minecraftUuid());
            if(updated==0)db.update("INSERT INTO companion_profile(minecraft_uuid,about,observed_at,play_ticks,skin_url,stats_observed_at) VALUES(?,?,?,?,?,?)",p.minecraftUuid(),p.about(),ts(s.observedAt()),p.playTicks(),p.skinUrl(),p.playTicks()==null?null:ts(s.observedAt()));
        }
        return Map.of("accepted",true);
    }
}
