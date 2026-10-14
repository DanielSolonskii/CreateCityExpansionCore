package dev.createserver.backend.report;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class ReportSettings {
    private final Set<Long> admins;
    public ReportSettings(@Value("${REPORT_ADMIN_TELEGRAM_IDS:}") String ids) { admins=parse(ids); }
    public Set<Long> admins(){ return admins; }
    public static Set<Long> parse(String ids){
        if(ids==null || ids.isBlank())return Set.of();
        Set<Long> result=new TreeSet<>();
        try { for(String id:ids.split(",",-1)) { if(!id.trim().matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException();result.add(Long.parseLong(id.trim())); } }
        catch(RuntimeException invalid){throw new IllegalArgumentException("Invalid REPORT_ADMIN_TELEGRAM_IDS");}
        if(result.size()>20)throw new IllegalArgumentException("At most 20 report admins supported");
        return Collections.unmodifiableSet(result);
    }
    public void require(long id){if(!admins.contains(id))throw new dev.createserver.backend.account.AccountFailure(403,"ADMIN_REQUIRED");}
}
