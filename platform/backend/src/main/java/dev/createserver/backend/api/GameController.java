package dev.createserver.backend.api;
import dev.createserver.backend.game.GameSessionService;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class GameController {
    private final GameSessionService service;
    public GameController(GameSessionService service){this.service=service;}
    @PostMapping("/auth/devices/{id}/game-session-capability")
    public GameSessionService.Capability capability(@PathVariable UUID id,@RequestHeader(value="Authorization",defaultValue="")String auth){return service.start(id,bearer(auth));}
    @PostMapping("/game-sessions")
    public GameSessionService.Ticket issue(@RequestHeader(value="Authorization",defaultValue="")String auth,@RequestBody GameSessionService.Challenge body){return service.issue(bearer(auth),body);}
    @DeleteMapping("/launcher/sessions/current")
    public ResponseEntity<Void> revoke(@RequestHeader(value="Authorization",defaultValue="")String auth){service.revoke(bearer(auth));return ResponseEntity.noContent().build();}
    @PostMapping("/internal/game/boot")
    public GameSessionService.Boot boot(@RequestBody Boot body){return service.registerBoot(body.serverId(),body.bootId());}
    @PostMapping("/internal/game/sessions/consume")
    public GameSessionService.Decision consume(@RequestBody GameSessionService.Consume body){return service.consume(body);}
    private String bearer(String value){return value.startsWith("Bearer ")?value.substring(7):"";}
    public record Boot(String serverId,UUID bootId){}
}
