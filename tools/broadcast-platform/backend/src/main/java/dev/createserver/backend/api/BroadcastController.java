package dev.createserver.backend.api;
import dev.createserver.backend.broadcast.BroadcastService;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/internal/broadcasts")
public class BroadcastController {
    private final BroadcastService service;
    public BroadcastController(BroadcastService service){this.service=service;}
    public record Admin(long telegramUserId){}
    @GetMapping public List<BroadcastService.Post> list(@RequestParam long telegramUserId){return service.list(telegramUserId);}
    @PostMapping public BroadcastService.Post create(@RequestBody BroadcastService.Draft body){return service.create(body);}
    @GetMapping("/{id}") public BroadcastService.Post get(@PathVariable UUID id,@RequestParam long telegramUserId){return service.get(telegramUserId,id);}
    @PostMapping("/{id}/publish") public BroadcastService.Post publish(@PathVariable UUID id,@RequestBody Admin body){return service.publish(body.telegramUserId(),id);}
    @PostMapping("/{id}/cancel") public BroadcastService.Post cancel(@PathVariable UUID id,@RequestBody Admin body){return service.cancel(body.telegramUserId(),id);}
    @PostMapping("/claim") public List<BroadcastService.Delivery> claim(@RequestBody Admin body){return service.claim(body.telegramUserId());}
    @PostMapping("/{id}/ack") public ResponseEntity<Void> ack(@PathVariable UUID id,@RequestBody BroadcastService.Ack body){service.acknowledge(id,body);return ResponseEntity.noContent().build();}
}
