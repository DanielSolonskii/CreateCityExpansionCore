package dev.createserver.backend.game;
import dev.createserver.backend.account.Secrets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record GameSettings(String serverId,String serviceSecret) {
    public GameSettings(@Value("${platform.game.server-id:${GAME_SERVER_ID:}}") String serverId,
                        @Value("${platform.game.service-secret:${CCEC_BACKEND_SECRET:}}") String serviceSecret) {
        this.serverId=serverId;this.serviceSecret=serviceSecret;
    }
    public boolean configured(){return serverId!=null && serverId.matches("[a-z0-9_-]{1,64}") && serviceSecret!=null && serviceSecret.length()>=32 && serviceSecret.length()<=512;}
    public boolean permits(String supplied){return configured() && Secrets.equal(serviceSecret,supplied);}
    @Override public String toString(){return "GameSettings[redacted]";}
}
