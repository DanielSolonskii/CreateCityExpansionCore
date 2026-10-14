package dev.createserver.backend;

import dev.createserver.backend.infrastructure.DatabaseHealth;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:infrastructure;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class InfrastructureIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired DatabaseHealth health;
    @LocalServerPort int port;

    @Test
    void contextStartsAndFlywayCreatesTechnicalMarker() {
        assertThat(flyway.info().current().getVersion().toString()).isEqualTo("9");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_infrastructure", Integer.class))
                .isEqualTo(1);
        assertThat(health.isAvailable()).isTrue();
    }

    @Test
    void httpEndpointsUseRealSpringConfiguration() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = get(client, "/api/health");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"status\":\"UP\"", "\"postgres\":\"UP\"");
            HttpResponse<String> version = get(client, "/api/version");
            assertThat(version.statusCode()).isEqualTo(200);
            assertThat(version.body()).contains("create-server-backend", "0.1.0-SNAPSHOT");

            jdbc.update("DELETE FROM platform_infrastructure WHERE id = 1");
            try {
                HttpResponse<String> failed = get(client, "/api/health");
                assertThat(failed.statusCode()).isEqualTo(503);
                assertThat(failed.body()).contains("\"postgres\":\"DOWN\"");
            } finally {
                jdbc.update("INSERT INTO platform_infrastructure (id, schema_version) VALUES (1, 4)");
            }
        }
    }

    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
