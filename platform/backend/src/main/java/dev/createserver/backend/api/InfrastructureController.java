package dev.createserver.backend.api;

import dev.createserver.backend.infrastructure.DatabaseHealth;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InfrastructureController {
    private final DatabaseHealth databaseHealth;
    private final BuildProperties build;

    public InfrastructureController(DatabaseHealth databaseHealth, BuildProperties build) {
        this.databaseHealth = databaseHealth;
        this.build = build;
    }

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        boolean available = databaseHealth.isAvailable();
        String status = available ? "UP" : "DOWN";
        return ResponseEntity.status(available ? 200 : 503)
                .cacheControl(CacheControl.noStore())
                .body(new HealthResponse(status, new Checks(status)));
    }

    @GetMapping("/version")
    public ResponseEntity<VersionResponse> version() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new VersionResponse(build.getName(), build.getVersion()));
    }

    public record HealthResponse(String status, Checks checks) {}
    public record Checks(String postgres) {}
    public record VersionResponse(String name, String version) {}
}
