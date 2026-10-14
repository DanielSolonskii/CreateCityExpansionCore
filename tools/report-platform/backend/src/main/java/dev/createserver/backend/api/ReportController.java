package dev.createserver.backend.api;
import dev.createserver.backend.report.ReportService;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/internal")
public class ReportController {
    private final ReportService service;
    public ReportController(ReportService service){this.service=service;}
    @PostMapping("/game/reports") public ReportService.Report submit(@RequestBody ReportService.Submission body){return service.submit(body);}
    @GetMapping("/reports") public List<ReportService.Report> open(@RequestParam long telegramUserId,@RequestParam(defaultValue="0")int offset){return service.open(telegramUserId,offset);}
    @GetMapping("/reports/{id}") public ReportService.Report get(@PathVariable UUID id,@RequestParam long telegramUserId){return service.get(telegramUserId,id);}
    @GetMapping("/reports/{id}/player") public ReportService.PlayerCard player(@PathVariable UUID id,@RequestParam long telegramUserId){return service.player(telegramUserId,id);}
    @PostMapping("/reports/{id}/actions") public ReportService.Report action(@PathVariable UUID id,@RequestBody ReportService.Action body){return service.action(id,body);}
    public record Admin(long telegramUserId){}
    public record Ack(long telegramUserId,UUID leaseToken){}
    @PostMapping("/reports/notifications/claim") public List<ReportService.Delivery> claim(@RequestBody Admin body){return service.claim(body.telegramUserId());}
    @PostMapping("/reports/notifications/{id}/ack") public ResponseEntity<Void> ack(@PathVariable UUID id,@RequestBody Ack body){service.acknowledge(body.telegramUserId(),id,body.leaseToken());return ResponseEntity.noContent().build();}
}
