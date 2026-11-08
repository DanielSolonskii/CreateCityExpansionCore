package dev.createserver.backend.miniapp;
import dev.createserver.backend.bridge.BridgeService;
import dev.createserver.backend.broadcast.LauncherNews;
import dev.createserver.backend.game.GameSettings;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/mini-app")
public class MiniAppController {
    public record Device(String label,String state,Instant createdAt,Instant expiresAt){}
    public record Profile(String username,String status,Instant createdAt,long playtimeSeconds,List<Device> devices){}
    public record Server(String state,long uptimeSeconds,int playerCount,int maxPlayers,String minecraftVersion,String ccecVersion,Instant observedAt,List<String> players){}
    public record Dashboard(Profile profile,Server server,List<LauncherNews.Item> news){}
    private final JdbcTemplate db;private final BridgeService bridge;private final LauncherNews news;private final GameSettings game;private final Clock clock;
    public MiniAppController(JdbcTemplate db,BridgeService bridge,LauncherNews news,GameSettings game,Clock clock){this.db=db;this.bridge=bridge;this.news=news;this.game=game;this.clock=clock;}
    @GetMapping("/dashboard") @Transactional(readOnly=true)
    public Dashboard dashboard(@RequestAttribute(MiniAppGuard.ACTOR) long telegramId){
        var profiles=db.query("SELECT u.id,c.username,u.status,u.created_at FROM telegram_identity t JOIN platform_user u ON u.id=t.platform_user_id JOIN account_credentials c ON c.platform_user_id=u.id WHERE t.telegram_user_id=?",(r,n)->{
            UUID id=r.getObject(1,UUID.class);String status=r.getString(3);
            List<Device> devices=status.equals("ACTIVE")?db.query("SELECT label,state,created_at,trusted_expires_at FROM trusted_device WHERE platform_user_id=? AND state='TRUSTED' AND trusted_expires_at>? ORDER BY created_at DESC LIMIT 20",(d,i)->new Device(d.getString(1),d.getString(2),d.getTimestamp(3).toInstant(),d.getTimestamp(4).toInstant()),id,java.sql.Timestamp.from(clock.instant())):List.of();
            long time=status.equals("ACTIVE")?db.queryForObject("SELECT COALESCE(SUM(duration_seconds),0) FROM telemetry_session WHERE platform_user_id=?",Long.class,id):0;
            return new Profile(r.getString(2),status,r.getTimestamp(4).toInstant(),time,devices);
        },telegramId);
        var s=bridge.status();List<String> players=new ArrayList<>();
        if(s.state().equals("ONLINE")){
            var snapshots=db.query("SELECT players FROM server_bridge_boot WHERE server_id=? ORDER BY started_at DESC,boot_id DESC LIMIT 1",(r,n)->r.getString(1),game.serverId());
            if(!snapshots.isEmpty() && !snapshots.getFirst().isBlank())for(String mc:snapshots.getFirst().split(",")){
                var names=db.query("SELECT c.username FROM minecraft_identity m JOIN account_credentials c ON c.platform_user_id=m.platform_user_id WHERE m.minecraft_uuid=?",(r,n)->r.getString(1),UUID.fromString(mc));
                players.add(names.isEmpty()?"Игрок":names.getFirst());
            }
        }
        return new Dashboard(profiles.isEmpty()?null:profiles.getFirst(),new Server(s.state(),s.uptimeSeconds(),s.playerCount(),s.maxPlayers(),s.minecraftVersion(),s.ccecVersion(),s.observedAt(),players),news.list());
    }
}
