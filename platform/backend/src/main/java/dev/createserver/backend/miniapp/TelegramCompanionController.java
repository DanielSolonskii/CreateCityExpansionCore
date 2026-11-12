package dev.createserver.backend.miniapp;

import dev.createserver.backend.account.AccountFailure;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** AccountGuard requires the bot service secret; actor is the numeric update sender. */
@RestController
@RequestMapping("/api/internal/telegram/companion/{telegramId}")
public class TelegramCompanionController {
    public record Preference(String field,Boolean enabled) {}
    private final TelegramCompanionService service;
    public TelegramCompanionController(TelegramCompanionService service){this.service=service;}
    @GetMapping public TelegramCompanionService.Profile profile(@PathVariable long telegramId){return service.profile(telegramId);}
    @GetMapping("/preferences") public CompanionService.Preferences preferences(@PathVariable long telegramId){return service.preferences(telegramId);}
    @PostMapping("/preferences") public CompanionService.Preferences preference(@PathVariable long telegramId,@RequestBody Preference value){
        return service.setPreference(telegramId,value==null?null:value.field(),value==null?null:value.enabled());
    }
    @ExceptionHandler(AccountFailure.class)
    public ResponseEntity<Map<String,String>> failure(AccountFailure failure){
        return ResponseEntity.status(failure.status()).cacheControl(CacheControl.noStore()).body(Map.of("code",failure.getMessage()));
    }
}
