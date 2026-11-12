package dev.createserver.backend.miniapp;

import dev.createserver.backend.account.AccountFailure;
import dev.createserver.backend.bridge.BridgeService;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner-scoped bot views over the same data and preferences as the Mini App. */
@Service
public class TelegramCompanionService {
    public record Profile(CompanionService.Account account, String onlineState, Instant onlineObservedAt,
                          boolean minecraftLinked, Long playtimeSeconds, Instant statsObservedAt) {}
    private record Stats(Long ticks, Instant observedAt) {}
    private final CompanionService companion;
    private final JdbcTemplate db;
    private final BridgeService bridge;
    public TelegramCompanionService(CompanionService companion, JdbcTemplate db, BridgeService bridge) {
        this.companion=companion; this.db=db; this.bridge=bridge;
    }
    @Transactional(readOnly=true)
    public Profile profile(long telegram) {
        UUID owner=companion.owner(telegram);
        var account=companion.account(telegram);
        var ids=db.query("SELECT minecraft_uuid FROM minecraft_identity WHERE platform_user_id=?",
            (r,n)->r.getObject(1,UUID.class),owner);
        UUID mc=ids.isEmpty()?null:ids.getFirst();
        var status=bridge.status();
        String online=mc==null||status.state().equals("UNKNOWN")?"UNKNOWN":account.online()?"ONLINE":"OFFLINE";
        var stats=mc==null?List.<Stats>of():db.query("SELECT play_ticks,stats_observed_at FROM companion_profile WHERE minecraft_uuid=?",
            (r,n)->new Stats(r.getObject(1,Long.class),r.getTimestamp(2)==null?null:r.getTimestamp(2).toInstant()),mc);
        Stats value=stats.isEmpty()?null:stats.getFirst();
        return new Profile(account,online,status.observedAt(),mc!=null,
            value==null||value.ticks()==null?null:value.ticks()/20,value==null?null:value.observedAt());
    }
    private CompanionService.Preferences readPreferences(UUID owner) {
        var rows=db.query("SELECT news,maintenance,reports FROM companion_preferences WHERE platform_user_id=?",
            (r,n)->new CompanionService.Preferences(r.getBoolean(1),r.getBoolean(2),r.getBoolean(3)),owner);
        return rows.isEmpty()?new CompanionService.Preferences(true,true,true):rows.getFirst();
    }
    @Transactional(readOnly=true)
    public CompanionService.Preferences preferences(long telegram) {
        return readPreferences(companion.owner(telegram));
    }
    @Transactional
    public CompanionService.Preferences setPreference(long telegram,String field,Boolean enabled) {
        if(field==null||!Set.of("news","maintenance","reports").contains(field)||enabled==null)
            throw new AccountFailure(400,"INVALID_PREFERENCES");
        UUID owner=companion.owner(telegram);
        // Same lock as Mini App writes; independent buttons cannot overwrite each other.
        db.queryForObject("SELECT id FROM platform_user WHERE id=? FOR UPDATE",UUID.class,owner);
        var previous=readPreferences(owner);
        return companion.preferences(telegram,new CompanionService.Preferences(
            field.equals("news")?enabled:previous.news(),
            field.equals("maintenance")?enabled:previous.maintenance(),
            field.equals("reports")?enabled:previous.reports()));
    }
}
