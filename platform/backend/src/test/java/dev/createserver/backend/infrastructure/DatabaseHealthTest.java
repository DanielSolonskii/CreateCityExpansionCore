package dev.createserver.backend.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DatabaseHealthTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final DatabaseHealth health = new DatabaseHealth(jdbc);

    @Test
    void acceptsMigratedDatabase() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        assertThat(health.isAvailable()).isTrue();
    }

    @Test
    void missingMarkerIsNotHealthy() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class))).thenReturn(null);
        assertThat(health.isAvailable()).isFalse();
    }

    @Test
    void databaseFailureIsNotHealthy() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class)))
                .thenThrow(new DataAccessResourceFailureException("private connection details"));
        assertThat(health.isAvailable()).isFalse();
    }
}
