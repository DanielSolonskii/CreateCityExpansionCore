-- Technical marker only: verifies migration and database infrastructure.
CREATE TABLE platform_infrastructure (
    id SMALLINT PRIMARY KEY CHECK (id = 1),
    schema_version INTEGER NOT NULL CHECK (schema_version >= 1),
    initialized_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO platform_infrastructure (id, schema_version) VALUES (1, 1);
