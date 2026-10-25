package dev.createserver.backend.miniapp;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
public class CompanionController {
    private final CompanionService service;
    public CompanionController(CompanionService service){this.service=service;}
    @GetMapping("/api/mini-app/account") public CompanionService.Account account(@RequestAttribute(MiniAppGuard.ACTOR) long actor){return service.account(actor);}
    @PostMapping("/api/mini-app/preferences") public CompanionService.Preferences preferences(@RequestAttribute(MiniAppGuard.ACTOR) long actor,@RequestBody CompanionService.Preferences value){return service.preferences(actor,value);}
    @PostMapping("/api/mini-app/devices/{id}/revoke") public Map<String,Boolean> revoke(@RequestAttribute(MiniAppGuard.ACTOR) long actor,@PathVariable UUID id){service.revoke(actor,id);return Map.of("revoked",true);}
    @PostMapping("/api/internal/game/companion") public Map<String,Boolean> snapshot(@RequestBody CompanionService.Snapshot value){return service.receive(value);}
}
