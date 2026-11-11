CREATE TABLE launcher_telemetry_event (
 id UUID PRIMARY KEY, platform_user_id UUID NOT NULL REFERENCES platform_user(id), device_id UUID NOT NULL,
 attempt_id UUID NOT NULL, type VARCHAR(40) NOT NULL, code VARCHAR(32) NOT NULL,
 numeric_value BIGINT NOT NULL, elapsed_ms BIGINT NOT NULL, occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
 received_at TIMESTAMP WITH TIME ZONE NOT NULL, version VARCHAR(40) NOT NULL, request_hash VARCHAR(64) NOT NULL
);
CREATE INDEX ix_launcher_telemetry_attempt ON launcher_telemetry_event(platform_user_id,attempt_id);
CREATE INDEX ix_launcher_telemetry_time ON launcher_telemetry_event(occurred_at,type);
CREATE INDEX ix_launcher_telemetry_device ON launcher_telemetry_event(device_id,received_at);
UPDATE platform_infrastructure SET schema_version=14 WHERE id=1;
