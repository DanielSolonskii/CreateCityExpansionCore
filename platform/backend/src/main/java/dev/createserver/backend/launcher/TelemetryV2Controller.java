package dev.createserver.backend.launcher;
import dev.createserver.backend.bridge.TelemetryV2Service;
import dev.createserver.backend.account.AccountFailure;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import java.util.*;
@RestController public class TelemetryV2Controller {
 private final TelemetryV2Service service;
 public TelemetryV2Controller(TelemetryV2Service service){this.service=service;}
 @PostMapping("/api/auth/devices/{id}/telemetry") public ResponseEntity<Map<String,Object>> receive(@PathVariable UUID id,@RequestHeader(value="Authorization",defaultValue="")String auth,@RequestBody TelemetryV2Service.Batch batch){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.receive(id,auth.startsWith("Bearer ")?auth.substring(7):"",batch));}
 @GetMapping("/api/internal/bridge/analytics") public Map<String,Object> analytics(@RequestParam long telegramUserId,@RequestParam(defaultValue="online")String section,@RequestParam(required=false)UUID session){return service.analytics(telegramUserId,section,session);}
 @ExceptionHandler(AccountFailure.class) public ResponseEntity<Map<String,String>> denied(AccountFailure e){return ResponseEntity.status(e.status()).cacheControl(CacheControl.noStore()).body(Map.of("code",e.getMessage()));}
}
