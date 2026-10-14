package dev.createserver.backend;

import dev.createserver.backend.infrastructure.DatabaseHealth;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("postgres")
@SpringBootTest
class PostgresIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired DatabaseHealth health;

    @Test
    void realPostgresStartsWithMigrationAndJpa() {
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("10");
        assertThat(health.isAvailable()).isTrue();
    }
}
