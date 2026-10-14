package dev.createserver.backend.api;

import dev.createserver.backend.account.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AccountController {
    private final AccountService accounts;
    private final PhoneVerificationService phone;
    public AccountController(AccountService accounts, PhoneVerificationService phone) { this.accounts = accounts; this.phone = phone; }

    @PostMapping("/registrations/{registrationId}/restart")
    public ResponseEntity<AccountService.Started> restart(@PathVariable UUID registrationId,
            @RequestHeader(value = "Authorization", defaultValue = "") String header) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(phone.restart(registrationId, header.startsWith("Bearer ") ? header.substring(7) : ""));
    }
    @PostMapping("/internal/telegram/prepare")
    public ResponseEntity<PhoneVerificationService.Preview> prepare(@RequestBody PhoneProof request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(phone.prepare(request.registrationToken(), request.verificationCode(), request.telegramUserId()));
    }
    @PostMapping("/internal/telegram/accept")
    public ResponseEntity<AccountService.Confirmed> accept(@RequestBody PhoneProof request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(phone.confirm(request.confirmationToken(), request.telegramUserId(), request.telegramUsername()));
    }
    public record PhoneProof(String registrationToken, String verificationCode, String confirmationToken,
            long telegramUserId, String telegramUsername) {
        @Override public String toString() { return "PhoneProof[redacted]"; }
    }

    @PostMapping("/registrations")
    public ResponseEntity<AccountService.Started> register(@RequestBody Registration request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(accounts.register(request.username(), request.password(), request.alphaKey()));
    }

    @GetMapping("/registrations/{registrationId}")
    public ResponseEntity<AccountService.Status> status(@PathVariable UUID registrationId,
                                                      @RequestHeader(value = "Authorization", defaultValue = "") String header) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accounts.status(registrationId, header.startsWith("Bearer ") ? header.substring(7) : ""));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AccountService.LoggedIn> login(@RequestBody Credentials request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accounts.login(request.username(), request.password()));
    }

    @PostMapping("/internal/telegram/confirm")
    public ResponseEntity<AccountService.Confirmed> confirm(@RequestBody TelegramConfirmation request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(accounts.confirmLegacy(request.registrationToken(), request.telegramUserId(), request.telegramUsername()));
    }

    @GetMapping("/internal/telegram/users/{telegramUserId}")
    public ResponseEntity<AccountService.LinkedAccount> linkedUser(@PathVariable long telegramUserId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.linkedUser(telegramUserId));
    }

    public record Credentials(String username, String password) {
        @Override public String toString() { return "Credentials[redacted]"; }
    }
    public record Registration(String username, String password, String alphaKey) {
        @Override public String toString() { return "Registration[redacted]"; }
    }
    public record TelegramConfirmation(String registrationToken, long telegramUserId, String telegramUsername) {
        @Override public String toString() { return "TelegramConfirmation[redacted]"; }
    }
}
