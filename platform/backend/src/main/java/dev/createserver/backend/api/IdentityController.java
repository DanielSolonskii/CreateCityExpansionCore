package dev.createserver.backend.api;

import dev.createserver.backend.identity.IdentityReadService;
import dev.createserver.backend.identity.IdentityReadService.MinecraftIdentityView;
import dev.createserver.backend.identity.IdentityReadService.PlatformUserView;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class IdentityController {
    private final IdentityReadService identities;

    public IdentityController(IdentityReadService identities) {
        this.identities = identities;
    }

    @GetMapping("/users/{platformUserId}")
    public ResponseEntity<PlatformUserView> user(@PathVariable String platformUserId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(identities.findUser(parseUuid(platformUserId))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    @GetMapping("/identities/minecraft/{minecraftUuid}")
    public ResponseEntity<MinecraftIdentityView> minecraft(@PathVariable String minecraftUuid) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(identities.findMinecraftIdentity(parseUuid(minecraftUuid))
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND)));
    }

    private UUID parseUuid(String value) {
        try {
            UUID uuid = UUID.fromString(value);
            if (!uuid.toString().equalsIgnoreCase(value)) {
                throw new IllegalArgumentException();
            }
            return uuid;
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
    }
}
