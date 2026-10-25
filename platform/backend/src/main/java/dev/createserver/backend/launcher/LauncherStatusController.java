package dev.createserver.backend.launcher;

import dev.createserver.backend.bridge.BridgeService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Public aggregate only: no names, UUIDs, private data or internal diagnostics. */
@RestController
public class LauncherStatusController {
    public record Status(String state, Integer playersOnline, Integer playersMax) {}
    private final BridgeService bridge;
    public LauncherStatusController(BridgeService bridge) { this.bridge=bridge; }
    @GetMapping("/api/launcher/server-status")
    public ResponseEntity<Status> status() {
        var value=bridge.status();
        boolean online="ONLINE".equals(value.state());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Status(
            value.state(), online ? value.playerCount() : null,
            online && value.maxPlayers()>0 ? value.maxPlayers() : null));
    }
}
