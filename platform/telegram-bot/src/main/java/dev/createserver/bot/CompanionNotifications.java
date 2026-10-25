package dev.createserver.bot;
import java.io.IOException;
import java.util.*;
final class CompanionNotifications {
    static void deliver(Set<Long> admins,BackendClient backend,TelegramClient telegram)throws InterruptedException{
        if(admins.isEmpty())return;long admin=Collections.min(admins);
        try{
            var rows=backend.deviceRequest("/api/internal/companion/notices/claim",Map.of("telegramUserId",admin));
            if(!rows.isArray()||rows.size()>3)throw new IOException("Invalid notice batch");
            for(var n:rows){
                UUID id=UUID.fromString(n.path("actionId").asText()),token=UUID.fromString(n.path("attemptToken").asText());
                long user=n.path("telegramUserId").asLong();String text=n.path("text").asText();if(user<=0||text.length()>500)throw new IOException("Invalid notice");
                String outcome="UNCERTAIN";
                try{telegram.sendPost(user,text,null);outcome="SENT";}catch(TelegramClient.Rejected rejected){outcome="FAILED";}catch(IOException uncertain){/* No blind retry. */}
                backend.deviceRequest("/api/internal/companion/notices/"+id+"/ack",Map.of("telegramUserId",admin,"attemptToken",token,"outcome",outcome));
            }
        }catch(IOException|IllegalArgumentException unavailable){System.err.println("Companion notifications deferred.");}
    }
}
