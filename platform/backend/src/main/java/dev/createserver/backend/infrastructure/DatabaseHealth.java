package dev.createserver.backend.infrastructure;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseHealth {
    private final JdbcTemplate jdbc;

    public DatabaseHealth(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isAvailable() {
        try {
            Integer version = jdbc.queryForObject(
                    "SELECT schema_version FROM platform_infrastructure WHERE id = 1",
                    Integer.class);
            return version != null && version >= 1;
        } catch (DataAccessException exception) {
            return false;
        }
    }
}
