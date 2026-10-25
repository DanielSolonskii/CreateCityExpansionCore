package dev.createserver.backend.launcher;
import dev.createserver.backend.account.*;
import dev.createserver.backend.bridge.BridgeService;
import dev.createserver.backend.game.GameSettings;
import dev.createserver.backend.miniapp.CompanionService;
import java.time.*;import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.json.JsonMapper;
/** Device-owner projection. Never accepts a target player or exposes social/private data. */
@RestController public class LauncherProfileController {
    public record Profile(UUID minecraftUuid,String username,Instant registeredAt,String city,String role,String cityState,Instant cityObservedAt,Long playTicks,Instant statsObservedAt,String skinUrl){}
    private final DeviceService devices;private final JdbcTemplate db;private final GameSettings game;private final BridgeService bridge;private final Clock clock;
    public LauncherProfileController(DeviceService devices,JdbcTemplate db,GameSettings game,BridgeService bridge,Clock clock){this.devices=devices;this.db=db;this.game=game;this.bridge=bridge;this.clock=clock;}
    @ExceptionHandler(AccountFailure.class)
    public ResponseEntity<Map<String,String>> denied(AccountFailure failure){return ResponseEntity.status(failure.status()).cacheControl(CacheControl.noStore()).body(Map.of("code",failure.getMessage()));}
    @GetMapping("/api/auth/devices/{id}/profile") @Transactional
    public ResponseEntity<Profile> profile(@PathVariable UUID id,@RequestHeader(value="Authorization",defaultValue="")String auth){
        var account=devices.authenticate(id,auth.startsWith("Bearer ")?auth.substring(7):"");UUID owner=account.platformUserId();
        var identities=db.query("SELECT minecraft_uuid,current_name FROM minecraft_identity WHERE platform_user_id=?",(r,i)->new Object[]{r.getObject(1,UUID.class),r.getString(2)},owner);
        UUID mc=identities.isEmpty()?null:(UUID)identities.getFirst()[0];String username=identities.isEmpty()?account.username():(String)identities.getFirst()[1];
        Instant registered=db.queryForObject("SELECT created_at FROM platform_user WHERE id=?",(r,i)->r.getTimestamp(1).toInstant(),owner);
        String city="",role="",state="UNKNOWN";Instant observed=null;
        var snapshots=db.query("SELECT payload FROM companion_snapshot WHERE server_id=?",(r,i)->r.getString(1),game.serverId());
        if(!snapshots.isEmpty()){
            var s=JsonMapper.builder().build().readValue(snapshots.getFirst(),CompanionService.Snapshot.class);observed=s.observedAt();
            var boots=db.query("SELECT started_at FROM server_bridge_boot WHERE server_id=? ORDER BY started_at DESC,boot_id DESC LIMIT 1",(r,i)->r.getTimestamp(1).toInstant(),game.serverId());
            state=bridge.status().state().equals("ONLINE")&&!observed.isBefore(clock.instant().minusSeconds(120))&&!boots.isEmpty()&&!observed.isBefore(boots.getFirst())?"CURRENT":"STALE";
            for(var c:s.cities())for(var m:c.members())if(m.minecraftUuid().equals(mc)){city=c.name();role=m.role();}
        }
        var stats=mc==null?List.<Object[]>of():db.query("SELECT play_ticks,stats_observed_at,skin_url FROM companion_profile WHERE minecraft_uuid=?",(r,i)->new Object[]{r.getObject(1,Long.class),r.getTimestamp(2)==null?null:r.getTimestamp(2).toInstant(),r.getString(3)},mc);
        var row=stats.isEmpty()?new Object[]{null,null,null}:stats.getFirst();String skin=(String)row[2];if(skin!=null&&!skin.matches("https://textures\\.minecraft\\.net/texture/[0-9a-f]{32,64}"))skin=null;
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Profile(mc,username,registered,city,role,state,observed,(Long)row[0],(Instant)row[1],skin));
    }
}
