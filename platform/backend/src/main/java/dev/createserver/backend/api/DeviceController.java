package dev.createserver.backend.api;

import dev.createserver.backend.account.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DeviceController {
    private final DeviceService devices;
    public DeviceController(DeviceService devices) { this.devices=devices; }
    @PostMapping("/auth/devices")
    public ResponseEntity<DeviceService.Started> begin(@RequestBody LoginProof body) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(devices.begin(body.username(),body.password(),body.label()));
    }
    @GetMapping("/auth/devices/{id}")
    public DeviceService.DeviceView poll(@PathVariable UUID id,@RequestHeader(value="Authorization",defaultValue="") String auth) {
        return devices.poll(id,bearer(auth));
    }
    @PostMapping("/auth/devices/{id}/authenticate")
    public AccountService.LoggedIn authenticate(@PathVariable UUID id,@RequestHeader(value="Authorization",defaultValue="") String auth) {
        return devices.authenticate(id,bearer(auth));
    }
    @PostMapping("/auth/devices/{id}/revoke")
    public ResponseEntity<Void> logout(@PathVariable UUID id,@RequestHeader(value="Authorization",defaultValue="") String auth) {
        devices.revokeSelf(id,bearer(auth)); return ResponseEntity.noContent().build();
    }
    @PostMapping("/internal/devices/preview")
    public DeviceService.Preview preview(@RequestBody Proof body) { return devices.preview(body.verificationToken(),body.telegramUserId()); }
    @PostMapping("/internal/devices/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id,@RequestBody Proof body) {
        devices.approve(id,body.telegramUserId()); return ResponseEntity.noContent().build();
    }
    @GetMapping("/internal/devices/telegram/{telegramId}")
    public List<DeviceService.DeviceView> list(@PathVariable long telegramId) { return devices.list(telegramId); }
    @PostMapping("/internal/devices/{id}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable UUID id,@RequestBody Proof body) {
        devices.revokeByTelegram(id,body.telegramUserId()); return ResponseEntity.noContent().build();
    }
    private String bearer(String value) { return value.startsWith("Bearer ") ? value.substring(7) : ""; }
    public record LoginProof(String username,String password,String label) {
        @Override public String toString() { return "DeviceLogin[redacted]"; }
    }
    public record Proof(String username,String password,String label,String verificationToken,long telegramUserId) {
        @Override public String toString() { return "DeviceProof[redacted]"; }
    }
}
