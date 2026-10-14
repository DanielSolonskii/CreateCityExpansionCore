package dev.createserver.backend.api;
import dev.createserver.backend.bridge.BridgeService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequestMapping("/api/internal")
public class BridgeController {
    private final BridgeService service;
    public BridgeController(BridgeService service){this.service=service;}
    @PostMapping("/game/telemetry") public Map<String,Object> receive(@RequestBody BridgeService.Batch batch){return service.receive(batch);}
    @GetMapping("/bridge/status") public BridgeService.Status status(){return service.status();}
    @GetMapping("/bridge/admin") public BridgeService.Admin admin(@RequestParam long telegramUserId){return service.admin(telegramUserId);}
}
