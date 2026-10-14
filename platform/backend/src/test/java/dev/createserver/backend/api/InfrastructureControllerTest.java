package dev.createserver.backend.api;

import dev.createserver.backend.infrastructure.DatabaseHealth;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class InfrastructureControllerTest {
    private final DatabaseHealth database = mock(DatabaseHealth.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        Properties properties = new Properties();
        properties.setProperty("name", "create-server-backend");
        properties.setProperty("version", "0.1.0-SNAPSHOT");
        mvc = MockMvcBuilders.standaloneSetup(
                new InfrastructureController(database, new BuildProperties(properties))).build();
    }

    @Test
    void healthyDatabaseReturns200() throws Exception {
        when(database.isAvailable()).thenReturn(true);
        mvc.perform(get("/api/health")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.checks.postgres").value("UP"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void unavailableDatabaseReturns503WithoutDetails() throws Exception {
        when(database.isAvailable()).thenReturn(false);
        mvc.perform(get("/api/health")).andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\",\"checks\":{\"postgres\":\"DOWN\"}}"));
    }

    @Test
    void versionUsesBuildMetadataWithoutDatabaseAccess() throws Exception {
        mvc.perform(get("/api/version")).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("create-server-backend"))
                .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"));
        verifyNoInteractions(database);
    }
}
